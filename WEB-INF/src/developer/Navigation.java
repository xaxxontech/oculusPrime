package developer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;

import developer.image.OpenCVMotionDetect;
import developer.image.OpenCVObjectDetect;
import oculusPrime.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import oculusPrime.Application;
import oculusPrime.AutoDock;
import oculusPrime.PlayerCommands; 
import oculusPrime.State;
import oculusPrime.SystemWatchdog;
import oculusPrime.Util;
import oculusPrime.AutoDock.autodockmodes;
import oculusPrime.commport.ArduinoPrime;

public class Navigation {
	protected Application app = null;
	private static State state = State.getReference();
	private static final String DOCK = "dock"; // waypoint name
	private static final String redhome = System.getenv("RED5_HOME");
	private static final File navroutesfile = new File(redhome+"/conf/navigationroutes.xml");
	public static final long WAYPOINTTIMEOUT = Util.FIVE_MINUTES;
	public static final long NAVSTARTTIMEOUT = Util.TWO_MINUTES;
	public static final int RESTARTAFTERCONSECUTIVEROUTES = 15; // TODO: set to 15 in production
	private final Settings settings = Settings.getReference();
	public long routestarttime;
	public NavigationLog navlog;
	public int consecutiveroute = 1;

	/** Constructor */
	public Navigation(Application a){
		state.set(State.values.navsystemstatus, Ros.navsystemstate.stopped.toString());
		app = a;
		Ros.loadwaypoints();
		Ros.rospackagedir = Ros.getRosPackageDir(); // required for map saving
		navlog = new NavigationLog();
	}	
	
	public void gotoWaypoint(final String str) {
		if (state.getBoolean(State.values.autodocking)) {
			app.driverCallServer(PlayerCommands.messageclients, "command dropped, autodocking");
			return;
		}

		if (state.get(State.values.dockstatus).equals(AutoDock.UNDOCKED) &&
				!state.get(State.values.navsystemstatus).equals(Ros.navsystemstate.running.toString()))  {
			app.driverCallServer(PlayerCommands.messageclients,
					"Can't navigate, location unknown");
			return;
		}
		
		new Thread(new Runnable() { public void run() {

			if (!waitForNavSystem()) return;
			
			// undock if necessary
			if (!state.get(State.values.dockstatus).equals(AutoDock.UNDOCKED)) {
				state.set(State.values.motionenabled, true);
				app.driverCallServer(PlayerCommands.forward, "0.7");
				Util.delay(3000);

				// rotate to localize
				app.driverCallServer(PlayerCommands.left, "360");
				Util.delay((long) (360 / state.getDouble(State.values.odomturndpms.toString())));
				Util.delay(1000);

			}

			if (!Ros.setWaypointAsGoal(str))
				app.driverCallServer(PlayerCommands.messageclients, "unable to set waypoint");

		
		}  }).start();
	}
	
	private boolean waitForNavSystem() { // blocking

		if (state.get(State.values.navsystemstatus).equals(Ros.navsystemstate.mapping.toString()) ||
				state.get(State.values.navsystemstatus).equals(Ros.navsystemstate.stopping.toString())) {
			app.driverCallServer(PlayerCommands.messageclients, "Navigation.waitForNavSystem(): can't start navigation");
			return false;
		}

		startNavigation();

		long start = System.currentTimeMillis();
		while (!state.get(State.values.navsystemstatus).equals(Ros.navsystemstate.running.toString())
				&& System.currentTimeMillis() - start < NAVSTARTTIMEOUT*3) { Util.delay(50);  } // wait

		if (!state.get(State.values.navsystemstatus).equals(Ros.navsystemstate.running.toString())) {
			app.driverCallServer(PlayerCommands.messageclients, "Navigation.waitForNavSystem(): navigation start failure");
			return false;
		}

		return true;
	}


	public void startMapping() {
		if (!state.get(State.values.navsystemstatus).equals(Ros.navsystemstate.stopped.toString())) {
			app.driverCallServer(PlayerCommands.messageclients, "Navigation.startMapping(): unable to start mapping, system already running");
			return;
		}

		app.driverCallServer(PlayerCommands.messageclients, "starting mapping, please wait");
		state.set(State.values.navsystemstatus, Ros.navsystemstate.starting.toString()); // set running by ROS node when ready
		app.driverCallServer(PlayerCommands.streamsettingsset, Application.camquality.med.toString());

		Ros.launch(Ros.MAKE_MAP);

	}

	public void startNavigation() {
		if (!state.get(State.values.navsystemstatus).equals(Ros.navsystemstate.stopped.toString())) {
			return;
		}

		new Thread(new Runnable() { public void run() {
			app.driverCallServer(PlayerCommands.messageclients, "starting navigation, please wait");
			state.set(State.values.navsystemstatus, Ros.navsystemstate.starting.toString()); // set running by ROS node when ready
			Ros.launch(Ros.REMOTE_NAV);

			// wait
			long start = System.currentTimeMillis();
			while (!state.get(State.values.navsystemstatus).equals(Ros.navsystemstate.running.toString())
					&& System.currentTimeMillis() - start < NAVSTARTTIMEOUT) { Util.delay(50);  } // wait

			if (state.get(State.values.navsystemstatus).equals(Ros.navsystemstate.running.toString()) ) {
				app.driverCallServer(PlayerCommands.streamsettingsset, Application.camquality.med.toString());
				if (!state.get(State.values.dockstatus).equals(AutoDock.UNDOCKED))
					state.set(State.values.rosinitialpose, "0_0_0");
				Util.log("navigation running", this);
				return; // success
			}

			// ========try again if needed, just once======

			if (state.get(State.values.navsystemstatus).equals(Ros.navsystemstate.stopping.toString()) ||
					state.get(State.values.navsystemstatus).equals(Ros.navsystemstate.stopped.toString()))
				return; // in case cancelled

			Util.log("navigation start attempt #2", this);
			stopNavigation();
			while (!state.get(State.values.navsystemstatus).equals(Ros.navsystemstate.stopped.toString())) // wait
				Util.delay(10);
			Ros.launch(Ros.REMOTE_NAV);

			// wait
			start = System.currentTimeMillis();
			while (!state.get(State.values.navsystemstatus).equals(Ros.navsystemstate.running.toString())
					&& System.currentTimeMillis() - start < NAVSTARTTIMEOUT) { Util.delay(50);  } // wait, again

			// check if running
			if (state.get(State.values.navsystemstatus).equals(Ros.navsystemstate.running.toString()) ) {
				app.driverCallServer(PlayerCommands.streamsettingsset, Application.camquality.med.toString());
				if (!state.get(State.values.dockstatus).equals(AutoDock.UNDOCKED))
					state.set(State.values.rosinitialpose, "0_0_0");
				Util.log("navigation running", this);
				return; // success
			}
			else  {
				stopNavigation(); // give up
//				Util.delay(5000);
//				Util.systemCall("pkill roscore");  // full reset
			}

		}  }).start();
	}

	public void stopNavigation() {
//		if (state.get(State.values.navsystemstatus).equals(Ros.navsystemstate.stopped.toString()))
//			return;

		Util.log("stopping navigation", this);
		Util.systemCall("pkill roslaunch");

		if (state.get(State.values.navsystemstatus).equals(Ros.navsystemstate.stopped.toString()))
			return;

		state.set(State.values.navsystemstatus, Ros.navsystemstate.stopping.toString());
		new Thread(new Runnable() { public void run() {
			Util.delay(Ros.ROSSHUTDOWNDELAY);
			state.set(State.values.navsystemstatus, Ros.navsystemstate.stopped.toString());
		}  }).start();

	}

	public void dock() {
		if (state.getBoolean(State.values.autodocking)  ) {
			app.driverCallServer(PlayerCommands.messageclients, "autodocking in progress, command dropped");
			return;
		}
		else if (state.get(State.values.dockstatus).equals(AutoDock.DOCKED)) {
			app.driverCallServer(PlayerCommands.messageclients, "already docked, command dropped");
			return;
		}
		else if (!state.get(State.values.navsystemstatus).equals(Ros.navsystemstate.running.toString())) {
			app.driverCallServer(PlayerCommands.messageclients, "navigation not running");
			return;
		}

		SystemWatchdog.waitForCpu();

		Ros.setWaypointAsGoal(DOCK);
		state.set(State.values.roscurrentgoal, "pending");

		new Thread(new Runnable() { public void run() {

			long start = System.currentTimeMillis();

			// store goal coords
			while (state.get(State.values.roscurrentgoal).equals("pending") && System.currentTimeMillis() - start < 1000) Util.delay(10);
			if (!state.exists(State.values.roscurrentgoal)) return; // avoid null pointer
			String goalcoords = state.get(State.values.roscurrentgoal);

			start = System.currentTimeMillis();
			while (state.exists(State.values.roscurrentgoal)
					&& System.currentTimeMillis() - start < WAYPOINTTIMEOUT) {
				if (!state.get(State.values.roscurrentgoal).equals(goalcoords)) return; // waypoint changed while waiting
				Util.delay(10);
			} // wait

			if ( !state.exists(State.values.rosgoalstatus)) { //this is (harmlessly) thrown normally nav goal cancelled (by driver stop command?)
				Util.log("error, state rosgoalstatus null", this);
				return;
			}

			if (!state.get(State.values.rosgoalstatus).equals(Ros.ROSGOALSTATUS_SUCCEEDED)) {
				app.driverCallServer(PlayerCommands.messageclients, "Navigation.dock() failed to reach dock");
				return;
			}

			Util.delay(1000);

			// success, should be pointing at dock, shut down nav
			stopNavigation();
//			Util.delay(Ros.ROSSHUTDOWNDELAY/2); // 5000 too low, massive cpu sometimes here

			Util.delay(5000); // 5000 too low, massive cpu sometimes here
			SystemWatchdog.waitForCpu();
			app.comport.checkisConnectedBlocking(); // just in case
			app.driverCallServer(PlayerCommands.odometrystop, null); // just in case, odo messes up docking if ros not killed

			// camera, lights
			app.driverCallServer(PlayerCommands.videosoundmode, Application.VIDEOSOUNDMODELOW); // saves CPU
			app.driverCallServer(PlayerCommands.streamsettingsset, Application.camquality.high.toString());
			app.driverCallServer(PlayerCommands.publish, Application.streamstate.camera.toString());
			app.driverCallServer(PlayerCommands.spotlight, "0");
			app.driverCallServer(PlayerCommands.cameracommand, ArduinoPrime.cameramove.reverse.toString());
			app.driverCallServer(PlayerCommands.floodlight, Integer.toString(AutoDock.FLHIGH));
			// do 180 deg turn
			app.driverCallServer(PlayerCommands.left, "180");
			Util.delay(app.comport.fullrotationdelay/2 + 2000);

			// make sure dock in view before calling autodock go
			if (!finddock()) { // something wrong with camera capture, try again?
				Util.delay(5000); // allow camera time to shutdown
				app.killGrabber(); // force chrome restart
				Util.delay(app.GRABBERRESPAWN + 4000); // allow time for grabber respawn
				// camera, lights
				app.driverCallServer(PlayerCommands.floodlight, Integer.toString(AutoDock.FLHIGH));
				app.driverCallServer(PlayerCommands.publish, Application.streamstate.camera.toString());
				Util.delay(4000); // wait for cam startup, light adjust
				app.comport.checkisConnectedBlocking(); // just in case
				if (!finddock()) return; // give up
			}

			// onwards
			SystemWatchdog.waitForCpu();
			app.driverCallServer(PlayerCommands.autodock, autodockmodes.go.toString());

			// wait while autodocking does its thing
			start = System.currentTimeMillis();
			while (state.getBoolean(State.values.autodocking) &&
					System.currentTimeMillis() - start < SystemWatchdog.AUTODOCKTIMEOUT)
				Util.delay(100);

			if (state.get(State.values.dockstatus).equals(AutoDock.DOCKED)) {
				Util.delay(2000);
				app.driverCallServer(PlayerCommands.publish, Application.streamstate.stop.toString());
			} else  Util.log("dock() - unable to dock", this);


		}  }).start();
	}

	// dock detect, rotate if necessary
	private boolean finddock() {
		int rot = 0;
		while (true) {
			SystemWatchdog.waitForCpu();

			app.driverCallServer(PlayerCommands.dockgrab, AutoDock.HIGHRES);
			long start = System.currentTimeMillis();
			while (!state.exists(State.values.dockfound.toString()) && System.currentTimeMillis() - start < 10000)
				Util.delay(10);  // wait

			if (state.getBoolean(State.values.dockfound)) break; // great, onwards
			else { // rotate a bit
				app.comport.checkisConnectedBlocking(); // just in case
				app.driverCallServer(PlayerCommands.left, "25");
				Util.delay(10); // thread safe

				start = System.currentTimeMillis();
				while(!state.get(State.values.direction).equals(ArduinoPrime.direction.stop.toString())
						&& System.currentTimeMillis() - start < 5000) { Util.delay(10); } // wait
				Util.delay(ArduinoPrime.TURNING_STOP_DELAY);
			}
			rot ++;

			if (rot == 1) Util.log("error, rotation required", this);

			if (rot == 16) { // failure give up
//					callForHelp(subject, body);
				app.driverCallServer(PlayerCommands.publish, Application.streamstate.stop.toString());
				app.driverCallServer(PlayerCommands.floodlight, "0");
				app.driverCallServer(PlayerCommands.messageclients, "Navigation.finddock() failed to find dock");
				return false;
			}
		}

		return true;
	}


	public static void goalCancel() {
		state.set(State.values.rosgoalcancel, true); // pass info to ros node
	}

	public static String routesLoad() {
		String result = "";

		BufferedReader reader;
		try {
			reader = new BufferedReader(new FileReader(navroutesfile));
			String line = "";
			while ((line = reader.readLine()) != null) 	result += line;
			reader.close();

		} catch (FileNotFoundException e) {
			return "<routeslist></routeslist>";
		} catch (IOException e) {
			return "<routeslist></routeslist>";
		}

		return result;
	}

	public void saveRoute(String str) {
		try {
			FileWriter fw = new FileWriter(navroutesfile);
			fw.append(str);
			fw.close();
		} catch (IOException e) {
			Util.printError(e);
		}
	}

	public void runAnyActiveRoute() {
		Document document = Util.loadXMLFromString(routesLoad());
		NodeList routes = document.getDocumentElement().getChildNodes();
		for (int i = 0; i< routes.getLength(); i++) {
			String rname = ((Element) routes.item(i)).getElementsByTagName("rname").item(0).getTextContent();
			String isactive = ((Element) routes.item(i)).getElementsByTagName("active").item(0).getTextContent();
			if (isactive.equals("true")) {
				runRoute(rname);
				Util.log("Auto-starting nav route: "+rname, this);
				break;
			}
		}

	}

	public void runRoute(final String name) {
		// build error checking into this (ignore duplicate waypoints, etc)
		// assume goto dock at the end, whether or not dock is a waypoint

		if (state.getBoolean(State.values.autodocking)) {
			app.driverCallServer(PlayerCommands.messageclients, "command dropped, autodocking");
			return;
		}

		if (state.get(State.values.dockstatus).equals(AutoDock.UNDOCKED) &&
				!state.get(State.values.navsystemstatus).equals(Ros.navsystemstate.running.toString()))  {
			app.driverCallServer(PlayerCommands.messageclients,
					"Can't start route, location unknown, command dropped");
			cancelAllRoutes();
			return;
		}

		if (state.exists(State.values.navigationroute))  cancelAllRoutes(); // if another route running

		// check for route name
		Document document = Util.loadXMLFromString(routesLoad());
		NodeList routes = document.getDocumentElement().getChildNodes();
		Element route = null;
		for (int i = 0; i< routes.getLength(); i++) {
    		String rname = ((Element) routes.item(i)).getElementsByTagName("rname").item(0).getTextContent();
			// unflag any currently active routes. New active route gets flagged just below:
			((Element) routes.item(i)).getElementsByTagName("active").item(0).setTextContent("false");
			if (rname.equals(name)) {
    			route = (Element) routes.item(i);
    			break;
    		}
		}

		if (route == null) { // name not found
			app.driverCallServer(PlayerCommands.messageclients, "route: "+name+" not found");
			return;
		}

		// start route
		final Element navroute = route;
		state.set(State.values.navigationroute, name);
		final String id = String.valueOf(System.nanoTime());
		state.set(State.values.navigationrouteid, id);

		// flag route active, save to xml file
		route.getElementsByTagName("active").item(0).setTextContent("true");
		String xmlstring = Util.XMLtoString(document);
		saveRoute(xmlstring);

		app.driverCallServer(PlayerCommands.messageclients, "activating route: " + name);

		new Thread(new Runnable() { public void run() {

			// get schedule info
			// map days to numbers
			NodeList days = navroute.getElementsByTagName("day");
			if (days.getLength() == 0) {
				app.driverCallServer(PlayerCommands.messageclients,
						"Can't schedule route, no days specified");
				cancelRoute(id);
				return;
			}
			int[] daynums = new int[days.getLength()];
			String[] availabledays = new String[]{"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
			for (int d=0; d<days.getLength(); d++) {
				for (int ad = 0; ad<availabledays.length; ad++) {
					if (days.item(d).getTextContent().equalsIgnoreCase(availabledays[ad]))   daynums[d]=ad+1;
				}
			}
			// more schedule info
			int starthour = Integer.parseInt(navroute.getElementsByTagName("starthour").item(0).getTextContent());
			int startmin = Integer.parseInt(navroute.getElementsByTagName("startmin").item(0).getTextContent());
			int routedurationhours = Integer.parseInt(navroute.getElementsByTagName("routeduration").item(0).getTextContent());

			// repeat route schedule forever until cancelled
			while (true) {

				state.delete(State.values.nextroutetime);

				// determine next scheduled route time, wait if necessary
				while (state.exists(State.values.navigationroute)) {
					if (!state.get(State.values.navigationrouteid).equals(id)) return;

					// add xml: starthour, startmin, routeduration, day
					Calendar calendarnow = Calendar.getInstance();
					calendarnow.setTime(new Date());
					int daynow = calendarnow.get(Calendar.DAY_OF_WEEK); // 1-7 (friday is 6)

					// parse new xml: starthour, startmin, routeduration (hours), day (1-7)
					// determine if now is within day + range, if not determine time to next range and wait

					boolean startroute = false;
					int nextdayindex = 99;

					for (int i=0; i<daynums.length; i++) {

						// check if need to start run right away
						if (daynums[i] == daynow -1 || daynums[i] == daynow || (daynums[i]==7 && daynow == 1)) { // yesterday or today
							Calendar testday = Calendar.getInstance();
							if (daynums[i] == daynow -1 || (daynums[i]==7 && daynow == 1)) { // yesterday
								testday.set(calendarnow.get(Calendar.YEAR), calendarnow.get(Calendar.MONTH),
										calendarnow.get(Calendar.DATE) - 1, starthour, startmin);
							}
							else { // today
								testday.set(calendarnow.get(Calendar.YEAR), calendarnow.get(Calendar.MONTH),
										calendarnow.get(Calendar.DATE), starthour, startmin);
							}
							if (calendarnow.getTimeInMillis() >= testday.getTimeInMillis() && calendarnow.getTimeInMillis() <
									testday.getTimeInMillis() + (routedurationhours * 60 * 60 * 1000)) {
								startroute = true;
								break;
							}

						}

						if (daynow == daynums[i]) nextdayindex = i;
						else if (daynow > daynums[i]) nextdayindex = i+1;
					}

					if (startroute) break;

					// determine seconds to next route
					if (!state.exists(State.values.nextroutetime)) { // only set once

						int adddays = 0;
						if (nextdayindex >= daynums.length ) { //wrap around
							nextdayindex = 0;
							adddays = 7-daynow + daynums[0];
						}
						else adddays = daynums[nextdayindex] - daynow;

						Calendar testday = Calendar.getInstance();
						testday.set(calendarnow.get(Calendar.YEAR), calendarnow.get(Calendar.MONTH),
								calendarnow.get(Calendar.DATE) + adddays, starthour, startmin);

						if (testday.getTimeInMillis() < System.currentTimeMillis()) { // same day, past route
							nextdayindex ++;
							if (nextdayindex >= daynums.length ) { //wrap around
								adddays = 7-daynow + daynums[0];
							}
							else  adddays = daynums[nextdayindex] - daynow;
							testday.set(calendarnow.get(Calendar.YEAR), calendarnow.get(Calendar.MONTH),
									calendarnow.get(Calendar.DATE) + adddays, starthour, startmin);
						}
						else if (testday.getTimeInMillis() - System.currentTimeMillis() > Util.ONE_DAY*7) //wrap
							testday.setTimeInMillis(testday.getTimeInMillis()-Util.ONE_DAY*7);

						state.set(State.values.nextroutetime, testday.getTimeInMillis());
					}

					Util.delay(1000);
				}

				// check if cancelled while waiting
				if (!state.exists(State.values.navigationroute)) return;
				if (!state.get(State.values.navigationrouteid).equals(id)) return;

				if (state.get(State.values.dockstatus).equals(AutoDock.UNDOCKED) &&
						!state.get(State.values.navsystemstatus).equals(Ros.navsystemstate.running.toString())) {
					app.driverCallServer(PlayerCommands.messageclients, "Can't navigate route, location unknown");
					cancelRoute(id);
					return;
				}

				if (!waitForNavSystem()) {
					navlog.newItem(NavigationLog.ERRORSTATUS, "unable to start navigation system", routestarttime,
							null, name, consecutiveroute);

					// check if cancelled while waiting
					if (!state.exists(State.values.navigationroute)) return;
					if (!state.get(State.values.navigationrouteid).equals(id)) return;

					if (state.getUpTime() > Util.TEN_MINUTES) {
						app.driverCallServer(PlayerCommands.reboot, null);
						return;
					}

					if (!delayToNextRoute(navroute, name, id)) return;
					continue;
				}

				// start!
				routestarttime = System.currentTimeMillis();

				// undock if necessary
				if (!state.get(State.values.dockstatus).equals(AutoDock.UNDOCKED)) {
					state.set(State.values.motionenabled, true);
					app.comport.checkisConnectedBlocking(); // just in case

					SystemWatchdog.waitForCpu();

					app.driverCallServer(PlayerCommands.forward, "0.7");

					Util.delay(3000);

					// rotate to localize
					app.comport.checkisConnectedBlocking(); // pcb could reset changing from wall to battery
					app.driverCallServer(PlayerCommands.left, "360");
					Util.delay((long) (360 / state.getDouble(State.values.odomturndpms.toString())));
					Util.delay(1000);
					app.driverCallServer(PlayerCommands.cameracommand, ArduinoPrime.cameramove.horiz.toString());
				}
				
		    	// go to each waypoint
		    	NodeList waypoints = navroute.getElementsByTagName("waypoint");	    	
		    	int wpnum = 0;
		    	while (wpnum < waypoints.getLength()) {

					// check if cancelled
			    	if (!state.exists(State.values.navigationroute)) return;
			    	if (!state.get(State.values.navigationrouteid).equals(id)) return;

		    		String wpname = 
	    				((Element) waypoints.item(wpnum)).getElementsByTagName("wpname").item(0).getTextContent();

					app.comport.checkisConnectedBlocking(); // just in case

		    		if (wpname.equals(DOCK))  break;

					SystemWatchdog.waitForCpu();

					Util.log("setting waypoint: "+wpname, this);
		    		if (!Ros.setWaypointAsGoal(wpname)) { // can't set waypoint, try the next one
						navlog.newItem(NavigationLog.ERRORSTATUS, "unable to set waypoint", routestarttime,
								wpname, name, consecutiveroute);
						app.driverCallServer(PlayerCommands.messageclients, "route "+name+" unable to set waypoint");
						wpnum ++;
						continue;
		    		}
	
		    		state.set(State.values.roscurrentgoal, "pending");
		    		
		    		// wait to reach wayypoint
					long start = System.currentTimeMillis();
					while (state.exists(State.values.roscurrentgoal) 
							&& System.currentTimeMillis() - start < WAYPOINTTIMEOUT) {
						Util.delay(100);
						long t = System.currentTimeMillis();
						if (state.getLong(State.values.lastodomreceived) - t > 1000) // malg timeout?
							Util.log("error, lastodomreceived: "+
									String.valueOf(state.getLong(State.values.lastodomreceived)-t), this);
					}
					
					if (!state.exists(State.values.navigationroute)) return;
			    	if (!state.get(State.values.navigationrouteid).equals(id)) return;
	
					if (!state.exists(State.values.rosgoalstatus)) { // this is (harmlessly) thrown normally nav goal cancelled (by driver stop command?)

						Util.log("error, state rosgoalstatus null", this);
						cancelRoute(id);
						return;
					}
					
					// failed, try next waypoint
					if (!state.get(State.values.rosgoalstatus).equals(Ros.ROSGOALSTATUS_SUCCEEDED)) {
						navlog.newItem(NavigationLog.ERRORSTATUS, "Failed to reach waypoint: "+wpname,
								routestarttime, wpname, name, consecutiveroute);
						app.driverCallServer(PlayerCommands.messageclients, "route "+name+" failed to reach waypoint");
						wpnum ++;
						continue; 
					}

					// send actions and duration delay to processRouteActions()
					NodeList actions = ((Element) waypoints.item(wpnum)).getElementsByTagName("action");
					long duration = Long.parseLong(
							((Element) waypoints.item(wpnum)).getElementsByTagName("duration").item(0).getTextContent());
					if (duration > 0)  processWayPointActions(actions, duration * 1000, wpname, name, id);
		    		
//					Util.delay(1000);
					wpnum ++;
				}
		    	
		    	if (!state.exists(State.values.navigationroute)) return;
		    	if (!state.get(State.values.navigationrouteid).equals(id)) return;
		    	
				dock();
				
				// wait while autodocking does its thing 
				long start = System.currentTimeMillis();
				while (System.currentTimeMillis() - start < SystemWatchdog.AUTODOCKTIMEOUT + WAYPOINTTIMEOUT) {
					if (!state.exists(State.values.navigationroute)) return;
			    	if (!state.get(State.values.navigationrouteid).equals(id)) return;
					if (state.get(State.values.dockstatus).equals(AutoDock.DOCKED) &&
							!state.getBoolean(State.values.autodocking)) break; // success
					Util.delay(100);
				}
					
				if (!state.get(State.values.dockstatus).equals(AutoDock.DOCKED)) {
					// TODO: send alert?
					navlog.newItem(NavigationLog.ERRORSTATUS, "Unable to dock",
							routestarttime, null, name, consecutiveroute);
//					cancelRoute(id);
					// try docking one more time, sending alert if fail
					Util.log("calling redock()", this);
					stopNavigation();
					Util.delay(Ros.ROSSHUTDOWNDELAY / 2); // 5000 too low, massive cpu sometimes here
					app.driverCallServer(PlayerCommands.redock, SystemWatchdog.NOFORWARD);

//					return;
					if (!delayToNextRoute(navroute, name, id)) return;
					continue;
				}

				navlog.newItem(NavigationLog.COMPLETEDSTATUS, null, routestarttime, null,
						name, consecutiveroute);
				consecutiveroute ++;

				if (!delayToNextRoute(navroute, name, id)) return;

			}
		
		}  }).start();
		
	}


	private boolean delayToNextRoute(Element navroute, String name, String id) {
		// delay to next route

		String msg = " min until next route: "+name+", run #"+consecutiveroute;
		if (consecutiveroute > RESTARTAFTERCONSECUTIVEROUTES) {
			msg = " min until reboot, max consecutive routes: "+RESTARTAFTERCONSECUTIVEROUTES+ " reached";
		}

		String min = navroute.getElementsByTagName("minbetween").item(0).getTextContent();
		long timebetween = Long.parseLong(min) * 1000 * 60;
		state.set(State.values.nextroutetime, System.currentTimeMillis()+timebetween);
		app.driverCallServer(PlayerCommands.messageclients, min +  msg);
		long start = System.currentTimeMillis();
		while (System.currentTimeMillis() - start < timebetween) {
			if (!state.exists(State.values.navigationroute)) {
				state.delete(State.values.nextroutetime);
				return false;
			}
			if (!state.get(State.values.navigationrouteid).equals(id)) {
				state.delete(State.values.nextroutetime);
				return false;
			}
			Util.delay(1000);
		}

		if (consecutiveroute > RESTARTAFTERCONSECUTIVEROUTES &&
				state.getUpTime() > Util.TEN_MINUTES)  { // prevent runaway reboots
			Util.log("rebooting, max consecutive routes reached", this);
			app.driverCallServer(PlayerCommands.reboot, null);
			return false;
		}
		return true;
	}

	/**
	 * process actions for single waypoint 
	 * 
	 * @param actions
	 * @param duration
	 */
	private void processWayPointActions(NodeList actions, long duration, String wpname, String name, String id) {
		
		// TODO: actions here
		//  <action>  
		// var navrouteavailableactions = ["rotate", "email", "rss", "motion", "sound", "human", "not detect" ];
		/*
		 * rotate only works with motion & human (ie., camera) ignore otherwise
		 *     -rotate ~30 deg increments, fixed duration. start-stop
		 *     -minimum full rotation, or more if <duration> allows 
		 * <duration> -- cancel all actions and move to next waypoint (let actions complete)
		 * alerts: rss or email: send on detection (or not) from "motion", "human", "sound"
		 *      -once only from single waypoint, max 2 per route (on 1st detection, then summary at end)
		 * if no alerts, log only
		 */
    	// takes 5-10 seconds to init if mic is on (mic only, or mic + camera)

		boolean rotate = false;
		boolean email = false;
		boolean rss = false;
		boolean motion = false;
		boolean notdetect = false;
		boolean sound = false;
		boolean human = false;
		boolean photo = false;
		
		boolean camera = false;
		boolean mic = false;
		String notdetectedaction = "";
		
    	for (int i=0; i< actions.getLength(); i++) {
    		String action = ((Element) actions.item(i)).getTextContent();
    		switch (action) {
			case "rotate": rotate = true; break;
			case "email": email = true; break;
			case "rss": rss = true; break;
			case "motion":
				motion = true;
				camera = true;
				notdetectedaction = action;
				break;
			case "not detect":
				notdetect = true;
				break;
			case "sound":
				sound = true;
				mic = true;
				notdetectedaction = action;
				break;
			case "human":
				human = true;
				camera = true;
				notdetectedaction = action;
				break;
			case "photo":
				photo = true;
				camera = true;
				break;
      		}	
    	}

		// if no camera, what's the point in rotating
    	if (!camera) {
			rotate = false;
			app.driverCallServer(PlayerCommands.messageclients, "rotate action ignored, camera unused");
		}

    	// VIDEOSOUNDMODELOW required for flash stream activity function to work, saves cpu for camera
    	String previousvideosoundmode = state.get(State.values.videosoundmode);
    	if (mic || camera) app.driverCallServer(PlayerCommands.videosoundmode, Application.VIDEOSOUNDMODELOW);

		// setup camera mode and position
		if (camera) {
			if (human)
				app.driverCallServer(PlayerCommands.streamsettingsset, Application.camquality.med.toString());
			else if (motion)
    			app.driverCallServer(PlayerCommands.streamsettingsset, Application.camquality.high.toString());
			else // photo
			{
				app.driverCallServer(PlayerCommands.streamsettingscustom, "1280_720_8_85");
//				app.driverCallServer(PlayerCommands.streamsettingsset, Application.camquality.custom.toString());
			}

			int pos = state.getInteger(State.values.cameratilt);
			if (photo)
				app.driverCallServer(PlayerCommands.camtilt, String.valueOf(ArduinoPrime.CAM_HORIZ-ArduinoPrime.CAM_NUDGE*2));
			else
				app.driverCallServer(PlayerCommands.camtilt, String.valueOf(ArduinoPrime.CAM_HORIZ-ArduinoPrime.CAM_NUDGE*5));

		}

		// turn on cam and or mic, allow delay for normalize
		if (camera && mic) {
			app.driverCallServer(PlayerCommands.publish, Application.streamstate.camandmic.toString());
			Util.delay(5000);
		} else if (camera && !mic) {
			app.driverCallServer(PlayerCommands.publish, Application.streamstate.camera.toString());
			Util.delay(5000);
		} else if (!camera && mic) {
			app.driverCallServer(PlayerCommands.publish, Application.streamstate.mic.toString());
			Util.delay(5000);
		}

		long waypointstart = System.currentTimeMillis();
		long delay = 10000;
 		if (duration < delay) duration = delay;
		int turns = 0;
		int maxturns = 8;
		if (!rotate) {
			delay = duration;
			turns = maxturns;
		}

		// remain at waypoint looping and/or waiting, detection running if enabled
		while (System.currentTimeMillis() - waypointstart < duration || turns < maxturns) {


			if (!state.exists(State.values.navigationroute)) return;
	    	if (!state.get(State.values.navigationrouteid).equals(id)) return;

			// enable sound detection
			if (sound) {
				app.driverCallServer(PlayerCommands.setstreamactivitythreshold,
						"0 " + settings.readSetting(ManualSettings.soundthreshold));
			}

			// lights on if needed
			boolean lightondelay = false;
			if (camera) {
				if (turnLightOnIfDark()) {
					Util.delay(4000); // allow cam to adjust
					lightondelay = true;
				}
			}

			// enable human or motion detection
			if (human)
				app.driverCallServer(PlayerCommands.objectdetect, OpenCVObjectDetect.HUMAN);
			else if (motion)
				app.driverCallServer(PlayerCommands.motiondetect, null);

			// mic takes a while to start up
			if (sound && !lightondelay) Util.delay(2000);

			// ALL SENSES ENABLED, NOW WAIT
			long start = System.currentTimeMillis();
			while (!state.exists(State.values.streamactivity) && System.currentTimeMillis() - start < delay
					&& state.get(State.values.navigationrouteid).equals(id)) { Util.delay(10); }


			// PHOTO
			if (photo) {
				String link = FrameGrabHTTP.saveToFile("");
				Util.delay(2000); // wait while downloads
				String navlogmsg = "<a href='" + link + "' target='_blank'>Photo</a>";
				String msg = "[Oculus Prime Photo] ";
				msg += navlogmsg+", time: "+
						Util.getTime()+", at waypoint: " + wpname + ", route: " + name;

				if (email) {
					String emailto = settings.readSetting(GUISettings.email_to_address);
					if (!emailto.equals(Settings.DISABLED)) {
						app.driverCallServer(PlayerCommands.email, emailto + " " + msg);
						navlogmsg += "<br> email sent ";
					}
				}
				if (rss) {
					app.driverCallServer(PlayerCommands.rssadd, msg);
					navlogmsg += "<br> new RSS item ";
				}

				navlog.newItem(NavigationLog.PHOTOSTATUS, navlogmsg, routestarttime, wpname,
						state.get(State.values.navigationroute), consecutiveroute);

			}

			// ALERT
			if (state.exists(State.values.streamactivity) && ! notdetect) {

				String streamactivity =  state.get(State.values.streamactivity);
				String msg = "Detected: "+streamactivity+", time: "+
						Util.getTime()+", at waypoint: " + wpname + ", route: " + name;
				Util.log(msg + " " + streamactivity, this);

				String navlogmsg = "Detected: "+streamactivity;

				String link = "";
				if (streamactivity.contains("video") || streamactivity.contains(OpenCVObjectDetect.HUMAN)) {
					link = FrameGrabHTTP.saveToFile("?mode=processedImg");
					navlogmsg += "<br><a href='" + link + "' target='_blank'>image link</a>";
				}

				if (email || rss) {

					if (streamactivity.contains("video") || streamactivity.contains(OpenCVObjectDetect.HUMAN)) {
						msg = "[Oculus Prime Detected "+streamactivity+"] " + msg;
						msg += "\nimage link: " + link + "\n";
						Util.delay(3000); // allow time for download thread to capture image before turning off camera
					}
					else if (streamactivity.contains("audio")) {
						msg = "[Oculus Prime Sound Detection] Sound " + msg;
					}

					if (email) {
						String emailto = settings.readSetting(GUISettings.email_to_address);
						if (!emailto.equals(Settings.DISABLED)) {
							app.driverCallServer(PlayerCommands.email, emailto + " " + msg);
							navlogmsg += "<br> email sent ";
						}
					}
					if (rss) {
						app.driverCallServer(PlayerCommands.rssadd, msg);
						navlogmsg += "<br> new RSS item ";
					}
				}

				navlog.newItem(NavigationLog.ALERTSTATUS, navlogmsg, routestarttime, wpname,
						state.get(State.values.navigationroute), consecutiveroute);

				// shut down sensing
				if (state.exists(State.values.motiondetect))
					app.driverCallServer(PlayerCommands.motiondetectcancel, null);
				if (state.exists(State.values.objectdetect))
					app.driverCallServer(PlayerCommands.objectdetectcancel, null);
				if (state.exists(State.values.streamactivityenabled))
					app.driverCallServer(PlayerCommands.setstreamactivitythreshold, "0 0");

				break; // go to next waypoint, stop if rotating
			}

			// nothing detected, shut down sensing
			if (state.exists(State.values.motiondetect))
				app.driverCallServer(PlayerCommands.motiondetectcancel, null);
			if (state.exists(State.values.objectdetect))
				app.driverCallServer(PlayerCommands.objectdetectcancel, null);
			if (state.exists(State.values.streamactivityenabled))
				app.driverCallServer(PlayerCommands.setstreamactivitythreshold, "0 0");

			// ALERT if not detect
			if (notdetect) {
				String navlogmsg = "NOT Detected: "+notdetectedaction;
				String msg = "";

				if (email || rss) {
					msg = "[Oculus Prime: "+notdetectedaction+" NOT detected] ";
					msg += "At waypoint: " + wpname + ", route: " + name + ", time: "+Util.getTime();
				}

				if (email) {
					String emailto = settings.readSetting(GUISettings.email_to_address);
					if (!emailto.equals(Settings.DISABLED)) {
						app.driverCallServer(PlayerCommands.email, emailto + " " + msg);
						navlogmsg += "<br> email sent ";
					}
				}
				if (rss) {
					app.driverCallServer(PlayerCommands.rssadd, msg);
					navlogmsg += "<br> new RSS item ";
				}

				navlog.newItem(NavigationLog.ALERTSTATUS, navlogmsg, routestarttime, wpname,
						state.get(State.values.navigationroute), consecutiveroute);
			}


			if (rotate) {

				Util.delay(2000);
				SystemWatchdog.waitForCpu(8000); // lots of missed stop commands, cpu timeouts here

				double degperms = state.getDouble(State.values.odomturndpms.toString());   // typically 0.0857;
				app.driverCallServer(PlayerCommands.move, ArduinoPrime.direction.left.toString());
				Util.delay((long) (50.0 / degperms));
				app.driverCallServer(PlayerCommands.move, ArduinoPrime.direction.stop.toString());

				long stopwaiting = System.currentTimeMillis()+750; // timeout if error
				while(!state.get(State.values.direction).equals(ArduinoPrime.direction.stop.toString()) &&
						System.currentTimeMillis() < stopwaiting) { Util.delay(1); } // wait for stop
				if (!state.get(State.values.direction).equals(ArduinoPrime.direction.stop.toString()))
					Util.log("error, missed turnstop within 750ms", this);

				Util.delay(4000); // 2000 if condition below enabled

				// nuke this condition, delay always required, esp. if turning towards bright light source (eg., window)
//				if (state.getInteger(State.values.spotlightbrightness) > 0)
//					Util.delay(2000); // allow cam to normalize

				turns ++;
			}

		}

		app.driverCallServer(PlayerCommands.publish, Application.streamstate.stop.toString());
		if (camera) {
			app.driverCallServer(PlayerCommands.spotlight, "0");
			app.driverCallServer(PlayerCommands.cameracommand, ArduinoPrime.cameramove.horiz.toString());
		}
		if (mic) app.driverCallServer(PlayerCommands.videosoundmode, previousvideosoundmode);

	}

	private boolean turnLightOnIfDark() {

		state.delete(State.values.lightlevel);
		app.driverCallServer(PlayerCommands.getlightlevel, null);
		long timeout = System.currentTimeMillis() + 5000;
		while (!state.exists(State.values.lightlevel) && System.currentTimeMillis() < timeout) {
			Util.delay(10);
		}
		if (state.exists(State.values.lightlevel)) {
			if (state.getInteger(State.values.lightlevel) < 25) {
				app.driverCallServer(PlayerCommands.spotlight, "100"); // light on
				return true;
			}
		}

		return false;
	}

	/**
	 * cancel all routes, only if id matches state
	 * @param id
	 */
	private void cancelRoute(String id) {
		if (id.equals(state.get(State.values.navigationrouteid))) cancelAllRoutes();
	}

	public void cancelAllRoutes() {
		state.delete(State.values.navigationroute); // this eventually stops currently running route
		goalCancel();
		state.delete(State.values.nextroutetime);

		Document document = Util.loadXMLFromString(routesLoad());
		NodeList routes = document.getDocumentElement().getChildNodes();

		// set all routes inactive
		for (int i = 0; i< routes.getLength(); i++) {
			((Element) routes.item(i)).getElementsByTagName("active").item(0).setTextContent("false");
		}

		String xmlString = Util.XMLtoString(document);
		saveRoute(xmlString);

		app.driverCallServer(PlayerCommands.messageclients, "all routes cancelled");
	}

	public void saveMap() {
		if (!state.get(State.values.navsystemstatus).equals(Ros.navsystemstate.mapping.toString())) {
			app.message("unable to save map, mapping not running", null, null);
			return;
		}
		new Thread(new Runnable() { public void run() {
			if (Ros.saveMap())  app.message("map saved to "+Ros.getMapFilePath(), null, null);
		}  }).start();

	}


}
