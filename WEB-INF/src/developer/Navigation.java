package developer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;

import oculusPrime.commport.PowerLogger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import developer.image.OpenCVObjectDetect;
import oculusPrime.Application;
import oculusPrime.AutoDock;
import oculusPrime.AutoDock.autodockmodes;
import oculusPrime.State.values;
import oculusPrime.FrameGrabHTTP;
import oculusPrime.GUISettings;
import oculusPrime.ManualSettings;
import oculusPrime.Observer;
import oculusPrime.PlayerCommands;
import oculusPrime.Settings;
import oculusPrime.State;
import oculusPrime.SystemWatchdog;
import oculusPrime.Util;
import oculusPrime.commport.ArduinoPrime;

public class Navigation implements Observer {

    public enum lidarstate { enabled, disabled }

    protected static Application app = null;
	private static State state = State.getReference();
	public static final String DOCK = "dock"; // waypoint name
	private static final String redhome = System.getenv("RED5_HOME");
	public static final File navroutesfile = new File(redhome+"/conf/navigationroutes.xml");
	public static final long WAYPOINTTIMEOUT = Util.TEN_MINUTES;
	public static final long NAVSTARTTIMEOUT = Util.TWO_MINUTES;
	public static final int RESTARTAFTERCONSECUTIVEROUTES = 15;
	private final static Settings settings = Settings.getReference();
	public volatile boolean navdockactive = false;
	public static int consecutiveroute = 1;
	public static long routemillimeters = 0;
	public static long routestarttime = 0;
	public NavigationLog navlog;
	int batteryskips = 0;
	
	
	/** Constructor */
	public Navigation(Application a) {
		state.set(State.values.navsystemstatus, Ros.navsystemstate.stopped.toString());
		Ros.loadwaypoints();
		Ros.rospackagedir = Ros.getRosPackageDir(); // required for map saving
		navlog = new NavigationLog();
		state.addObserver(this);
		app = a;
        if (settings.getBoolean(ManualSettings.lidar))
            state.set(values.lidar, true);
	}	
	
	@Override
	public void updated(String key) {
		if(key.equals(values.distanceangle.name())){
			try {
				int mm = Integer.parseInt(state.get(values.distanceangle).split(" ")[0]);
				if(mm > 0) routemillimeters += mm;
			} catch (Exception e){}
		}
	}

	public static String getRouteMeters() {
		return Util.formatFloat(routemillimeters / 1000, 0);
	}
	
	public void gotoWaypoint(final String str) {
		if (state.getBoolean(State.values.autodocking)) {
			app.driverCallServer(PlayerCommands.messageclients, "command dropped, autodocking");
			return;
		}

		if (state.get(State.values.dockstatus).equals(AutoDock.UNDOCKED) &&
				!state.get(State.values.navsystemstatus).equals(Ros.navsystemstate.running.toString()))  {
			app.driverCallServer(PlayerCommands.messageclients, "Can't navigate, location unknown");
			return;
		}
		
		new Thread(new Runnable() { public void run() {

			if (!waitForNavSystem()) return;
			
			// undock if necessary
			if (!state.get(State.values.dockstatus).equals(AutoDock.UNDOCKED)) {
				undockandlocalize();
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

		String launchfile = Ros.MAKE_MAP;
        if (settings.getBoolean(ManualSettings.lidar)) launchfile = Ros.MAKE_MAP_LIDAR;

        if (!Ros.launch(launchfile)) {
			app.driverCallServer(PlayerCommands.messageclients, "roslaunch already running, aborting mapping start");
			return;
		}

		app.driverCallServer(PlayerCommands.messageclients, "starting mapping, please wait");
		state.set(State.values.navsystemstatus, Ros.navsystemstate.starting.toString()); // set running by ROS node when ready
//		app.driverCallServer(PlayerCommands.streamsettingsset, Application.camquality.med.toString());

	}

	public void startNavigation() {
		if (!state.equals(State.values.navsystemstatus, Ros.navsystemstate.stopped)) return;

		new Thread(new Runnable() { public void run() {

            String launchfile = Ros.REMOTE_NAV;
            if (settings.getBoolean(ManualSettings.lidar)) launchfile = Ros.REMOTE_NAV_LIDAR;

            app.driverCallServer(PlayerCommands.messageclients, "starting navigation, please wait");
			if (!Ros.launch(launchfile)) {
				app.driverCallServer(PlayerCommands.messageclients, "roslaunch already running, abort");
				return;
			}
			state.set(State.values.navsystemstatus, Ros.navsystemstate.starting.toString()); // set running by ROS node when ready

			// wait
			long start = System.currentTimeMillis();
			while (!state.get(State.values.navsystemstatus).equals(Ros.navsystemstate.running.toString())
					&& System.currentTimeMillis() - start < NAVSTARTTIMEOUT) { Util.delay(50);  } // wait

			if (state.equals(State.values.navsystemstatus, Ros.navsystemstate.running)){
//				if (settings.getBoolean(ManualSettings.useflash))
//					app.driverCallServer(PlayerCommands.streamsettingsset, Application.camquality.med.toString()); // reduce cpu
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
			while (!state.equals(State.values.navsystemstatus, Ros.navsystemstate.stopped)) Util.delay(10);

			if (!Ros.launch(launchfile)) {
				app.driverCallServer(PlayerCommands.messageclients, "roslaunch already running, abort");
				return;
			}

			start = System.currentTimeMillis(); // wait
			while (!state.equals(State.values.navsystemstatus, Ros.navsystemstate.running)
					&& System.currentTimeMillis() - start < NAVSTARTTIMEOUT) Util.delay(50);

			// check if running
			if (state.equals(State.values.navsystemstatus, Ros.navsystemstate.running)) {
				if (settings.getBoolean(ManualSettings.useflash))
					app.driverCallServer(PlayerCommands.streamsettingsset, Application.camquality.med.toString()); // reduce cpu
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

			// wait to reach waypoint
			start = System.currentTimeMillis();
			while (System.currentTimeMillis() - start < WAYPOINTTIMEOUT && state.exists(State.values.roscurrentgoal)) {
				try {
					if (!state.get(State.values.roscurrentgoal).equals(goalcoords))
						return; // waypoint changed while waiting
				} catch (Exception e) {}
				Util.delay(10);
			}

			if ( !state.exists(State.values.rosgoalstatus)) { //this is (harmlessly) thrown normally nav goal cancelled (by driver stop command?)
				Util.log("error, rosgoalstatus null, setting to empty string", this); // TODO: testing
				state.set(State.values.rosgoalstatus, "");
			}

			if (!state.get(State.values.rosgoalstatus).equals(Ros.ROSGOALSTATUS_SUCCEEDED)) {
				app.driverCallServer(PlayerCommands.messageclients, "Navigation.dock() failed to reach dock");
				return;
			}

			navdockactive = true;
			Util.delay(1000);

			// success, should be pointing at dock, shut down nav
			stopNavigation();
//			Util.delay(Ros.ROSSHUTDOWNDELAY/2); // 5000 too low, massive cpu sometimes here

			Util.delay(5000); // 5000 too low, massive cpu sometimes here

			if (!navdockactive) return;

			SystemWatchdog.waitForCpu();
			app.comport.checkisConnectedBlocking(); // just in case

			//start gyro again
			state.set(values.odometrybroadcast, ArduinoPrime.ODOMBROADCASTDEFAULT);
			state.set(values.rotatetolerance, ArduinoPrime.ROTATETOLERANCE);
			app.driverCallServer(PlayerCommands.odometrystart, null);

			// highres camera on
			app.driverCallServer(PlayerCommands.streamsettingsset, Application.camquality.high.toString());
			// only switch mode if camera not running, to avoid interruption of feed
			if (state.get(State.values.stream).equals(Application.streamstate.stop.toString()) ||
					state.get(State.values.stream).equals(Application.streamstate.mic.toString())) {
				app.driverCallServer(PlayerCommands.videosoundmode, Application.VIDEOSOUNDMODELOW); // saves CPU
				app.driverCallServer(PlayerCommands.publish, Application.streamstate.camera.toString());
			}

			app.driverCallServer(PlayerCommands.spotlight, "0");

			// reverse cam
			app.driverCallServer(PlayerCommands.cameracommand, ArduinoPrime.cameramove.reverse.toString());
			state.set(State.values.controlsinverted, true); // preempt so doesn't reverse in the middle of doing 180
															// must be set after cameracommand to work

			// do 180 deg turn
			app.driverCallServer(PlayerCommands.rotate, "180"); // odom controlled
			long timeout = System.currentTimeMillis() + 5000;
			while(!state.get(values.odomrotating).equals(Settings.FALSE) && System.currentTimeMillis() < timeout)
				Util.delay(1);

			Util.debug("navdock: onwards", this);
			app.driverCallServer(PlayerCommands.odometrystop, null);

			app.driverCallServer(PlayerCommands.floodlight, Integer.toString(AutoDock.FLHIGH));

			Util.delay(4000); // wait for video to stabilize

			if (!navdockactive) return;

			SystemWatchdog.waitForCpu(30, 20000); // stricter 30% check, lots of missed dock grabs here

			// make sure dock in view before calling autodock go
			if (!finddock(AutoDock.HIGHRES, false)) { // single highres dock search, no rotate to start
				if (!finddock(AutoDock.LOWRES, true)) { // lowres dock search with rotate (lowres much faster)
					Util.log("error, finddock() needs to try 2nd time", this);
					Util.delay(20000); // allow cam shutdown, system settle
					app.killGrabber(); // force chrome restart
					Util.delay(Application.GRABBERRESPAWN + 4000); // allow time for grabber respawn
					// camera, lights (in case malg had dropped commands)
					app.driverCallServer(PlayerCommands.spotlight, "0");
					app.driverCallServer(PlayerCommands.cameracommand, ArduinoPrime.cameramove.reverse.toString());
					app.driverCallServer(PlayerCommands.floodlight, Integer.toString(AutoDock.FLHIGH));
					app.driverCallServer(PlayerCommands.publish, Application.streamstate.camera.toString());
					Util.delay(4000); // wait for cam startup, light adjust
					app.comport.checkisConnectedBlocking(); // just in case
					if (!navdockactive) return;
					if (!finddock(AutoDock.HIGHRES, true)) return; // highres dock search with rotate (slow)
				}
			}

			// onwards
			SystemWatchdog.waitForCpu();
			app.driverCallServer(PlayerCommands.autodock, autodockmodes.go.toString());

			// wait while autodocking does its thing
			start = System.currentTimeMillis();
			while (state.getBoolean(State.values.autodocking) &&
					System.currentTimeMillis() - start < SystemWatchdog.AUTODOCKTIMEOUT)
				Util.delay(100);

			if (!navdockactive) return;

			if (state.get(State.values.dockstatus).equals(AutoDock.DOCKED)) {
				Util.delay(2000);
				app.driverCallServer(PlayerCommands.publish, Application.streamstate.stop.toString());
			} else  Util.log("dock() - unable to dock", this);


		}  }).start();

		navdockactive = false;
	}

	// dock detect, rotate if necessary
	private boolean finddock(String resolution, boolean rotate) {
		int rot = 0;

		while (navdockactive) {
			SystemWatchdog.waitForCpu();

			app.driverCallServer(PlayerCommands.dockgrab, resolution);
			long start = System.currentTimeMillis();
			while (!state.exists(State.values.dockfound.toString()) && System.currentTimeMillis() - start < Util.ONE_MINUTE)
				Util.delay(10);  // wait

			if (state.getBoolean(State.values.dockfound)) break; // great, onwards
			else if (!rotate) return false;
			else { // rotate a bit
				app.comport.checkisConnectedBlocking(); // just in case
				app.driverCallServer(PlayerCommands.right, "25");
				Util.delay(10); // thread safe

				start = System.currentTimeMillis();
				while(!state.get(State.values.direction).equals(ArduinoPrime.direction.stop.toString())
						&& System.currentTimeMillis() - start < 5000) { Util.delay(10); } // wait
				Util.delay(ArduinoPrime.TURNING_STOP_DELAY);
			}
			rot ++;

			if (rot == 1) Util.log("error, rotation required", this);

			if (rot == 21) { // failure give up
//					callForHelp(subject, body);
				app.driverCallServer(PlayerCommands.publish, Application.streamstate.stop.toString());
				app.driverCallServer(PlayerCommands.floodlight, "0");
				app.driverCallServer(PlayerCommands.messageclients, "Navigation.finddock() failed to find dock");
				return false;
			}
		}
		if (!navdockactive) return false;
		return true;
	}

	public static void goalCancel() {
		state.set(State.values.rosgoalcancel, true); // pass info to ros node
		state.delete(State.values.roswaypoint);
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
	
	/** only used before starting a route, ignored if un-docked */
	public static boolean batteryTooLow() {

		if (state.get(values.batterylife).matches(".*\\d+.*")) {  // make sure batterylife != 'TIMEOUT', throws error
			if ( Integer.parseInt(state.get(State.values.batterylife).replaceAll("[^0-9]", ""))
							< settings.getInteger(ManualSettings.lowbattery) &&
					state.get(State.values.dockstatus).equals(AutoDock.DOCKED))
			{
                app.driverCallServer(PlayerCommands.messageclients, "skipping route, battery too low");
                return true;
			}
		}

        return false;
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
		if (state.exists(values.roscurrentgoal))   goalCancel();  // override any active goal

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

			// get schedule info, map days to numbers
			NodeList days = navroute.getElementsByTagName("day");
			if (days.getLength() == 0) {
				app.driverCallServer(PlayerCommands.messageclients, "Can't schedule route, no days specified");
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

				// skip route if battery low (settings.txt)  
				if(batteryTooLow()){			
					batteryskips++;
					Util.log("battery too low: " + state.get(values.batterylife) + " skips: " + batteryskips, this);
					if(batteryskips == 1){	// only log once !
						navlog.newItem(NavigationLog.ALERTSTATUS, "Battery too low to start: " + state.get(values.batterylife), 0, null, name, consecutiveroute, 0);	
					} else {
						if( ! state.get(values.batterylife).contains("_charging")) {
							Util.log("batteryTooLow(): not charging, powerreset: "+ state.get(values.batterylife), "Navigation.runRoute()");
							app.driverCallServer(PlayerCommands.powerreset, null);
						}
					}
					if( ! delayToNextRoute(navroute, name, id)) return; 
					continue;
				} else { batteryskips = 0; }
				
				// start ros nav system
				if (!waitForNavSystem()) {
					// check if cancelled while waiting
					if (!state.exists(State.values.navigationroute)) return;
					if (!state.get(State.values.navigationrouteid).equals(id)) return;

					navlog.newItem(NavigationLog.ERRORSTATUS, "unable to start navigation system", routestarttime, null, name, consecutiveroute, 0);

					if (state.getUpTime() > Util.TEN_MINUTES) {
						app.driverCallServer(PlayerCommands.reboot, null);
						return;
					}

					if (!delayToNextRoute(navroute, name, id)) return;
					continue;
				}

				// check if cancelled while waiting
				if (!state.exists(State.values.navigationroute)) return;
				if (!state.get(State.values.navigationrouteid).equals(id)) return;

				// GO!
				routestarttime = System.currentTimeMillis();
				state.set(State.values.lastusercommand, routestarttime);  // avoid watchdog abandoned
				routemillimeters = 0l;
				
				// undock if necessary
				if (!state.get(State.values.dockstatus).equals(AutoDock.UNDOCKED)) {
					SystemWatchdog.waitForCpu();
					undockandlocalize();
				}
				app.driverCallServer(PlayerCommands.cameracommand, ArduinoPrime.cameramove.horiz.toString());

		    	// go to each waypoint
		    	NodeList waypoints = navroute.getElementsByTagName("waypoint");	    	
		    	int wpnum = 0;
		    	while (wpnum < waypoints.getLength()) {

					state.set(State.values.lastusercommand, System.currentTimeMillis()); // avoid watchdog abandoned

					// check if cancelled
			    	if (!state.exists(State.values.navigationroute)) return;
			    	if (!state.get(State.values.navigationrouteid).equals(id)) return;

		    		String wpname = ((Element) waypoints.item(wpnum)).getElementsByTagName("wpname").item(0).getTextContent();
					wpname = wpname.trim();

					app.comport.checkisConnectedBlocking(); // just in case

		    		if (wpname.equals(DOCK))  break;

					SystemWatchdog.waitForCpu();

					if (state.exists(values.roscurrentgoal)) { // current route override TODO: add waypoint name to log msg
						navlog.newItem(NavigationLog.ERRORSTATUS, "current route override prior to set waypoint: "+wpname,
								routestarttime, null, name, consecutiveroute, 0);
						app.driverCallServer(PlayerCommands.messageclients, "current route override prior to set waypoint: "+wpname);
						NavigationUtilities.routeFailed(state.get(values.navigationroute));
						break;
					}

					Util.log("setting waypoint: "+wpname, this);
		    		if (!Ros.setWaypointAsGoal(wpname)) { // can't set waypoint, try the next one
						navlog.newItem(NavigationLog.ERRORSTATUS, "unable to set waypoint", routestarttime,
								wpname, name, consecutiveroute, 0);
						app.driverCallServer(PlayerCommands.messageclients, "route "+name+" unable to set waypoint");
						wpnum ++;
						continue;
		    		}
	
		    		state.set(State.values.roscurrentgoal, "pending");
		    		
		    		// wait to reach wayypoint
					long start = System.currentTimeMillis();
					boolean oktocontinue = true;
					while (state.exists(State.values.roscurrentgoal) && System.currentTimeMillis() - start < WAYPOINTTIMEOUT) {
						Util.delay(10);
						if (!state.equals(values.roswaypoint, wpname)) { // current route override
							navlog.newItem(NavigationLog.ERRORSTATUS, "current route override on way to waypoint: "+wpname,
									routestarttime, wpname, name, consecutiveroute, 0);
							app.driverCallServer(PlayerCommands.messageclients, "current route override on way to waypoint: "+wpname);
							oktocontinue = false;
							break;
						}
					}
					if (!oktocontinue) break;
					
					if (!state.exists(State.values.navigationroute)) return;
			    	if (!state.get(State.values.navigationrouteid).equals(id)) return;

					if (!state.exists(State.values.rosgoalstatus)) { // this is (harmlessly) thrown normally nav goal cancelled (by driver stop command?)
						Util.log("error, state rosgoalstatus null", this);
						state.set(State.values.rosgoalstatus, "error");
					}
					
					// failed, try next waypoint
					if (!state.get(State.values.rosgoalstatus).equals(Ros.ROSGOALSTATUS_SUCCEEDED)) {
						navlog.newItem(NavigationLog.ERRORSTATUS, "Failed to reach waypoint: "+wpname,
								routestarttime, wpname, name, consecutiveroute, 0);
						app.driverCallServer(PlayerCommands.messageclients, "route "+name+" failed to reach waypoint");
						wpnum ++;
						continue; 
					}

					// send actions and duration delay to processRouteActions()
					NodeList actions = ((Element) waypoints.item(wpnum)).getElementsByTagName("action");
					long duration = Long.parseLong(
						((Element) waypoints.item(wpnum)).getElementsByTagName("duration").item(0).getTextContent());
					if (duration > 0)  processWayPointActions(actions, duration * 1000, wpname, name, id);
					wpnum ++;
				}
		    	
		    	if (!state.exists(State.values.navigationroute)) return;
		    	if (!state.get(State.values.navigationrouteid).equals(id)) return;

				if (!state.exists(values.roscurrentgoal)) { // current route override check

					dock();

					// wait while autodocking does its thing
					final long start = System.currentTimeMillis();
					while (System.currentTimeMillis() - start < SystemWatchdog.AUTODOCKTIMEOUT + WAYPOINTTIMEOUT) {
						if (!state.exists(State.values.navigationroute)) return;
						if (!state.get(State.values.navigationrouteid).equals(id)) return;
						if (state.get(State.values.dockstatus).equals(AutoDock.DOCKED) && !state.getBoolean(State.values.autodocking))
							break;
						Util.delay(100); // success
					}

					if (!state.get(State.values.dockstatus).equals(AutoDock.DOCKED)) {

						navlog.newItem(NavigationLog.ERRORSTATUS, "Unable to dock", routestarttime, null, name, consecutiveroute, 0);

						// cancelRoute(id);
						// try docking one more time, sending alert if fail
						Util.log("calling redock()", this);
						stopNavigation();
						Util.delay(Ros.ROSSHUTDOWNDELAY / 2); // 5000 too low, massive cpu sometimes here
						app.driverCallServer(PlayerCommands.redock, SystemWatchdog.NOFORWARD);

						if (!delayToNextRoute(navroute, name, id)) return;
						continue;
					}

					navlog.newItem(NavigationLog.COMPLETEDSTATUS, null, routestarttime, null, name, consecutiveroute, routemillimeters);

					// how long did docking take
					int timetodock = 0; // (int) ((System.currentTimeMillis() - start)/ 1000);
					// subtract from routes time
					int routetime = (int)(System.currentTimeMillis() - routestarttime)/1000 - timetodock;
					NavigationUtilities.routeCompleted(name, routetime, (int)routemillimeters/1000);

					consecutiveroute++;
					routemillimeters = 0;

				}

				if (!delayToNextRoute(navroute, name, id)) return;
			}
		}  }).start();
	}

	private void undockandlocalize() { // blocking
		state.set(State.values.motionenabled, true);
		double distance = settings.getDouble(ManualSettings.undockdistance);
		app.driverCallServer(PlayerCommands.forward, String.valueOf(distance));
		Util.delay((long) (distance / state.getDouble(values.odomlinearmpms.toString()))); // required for fast systems?!
		long start = System.currentTimeMillis();
		while(!state.get(values.direction).equals(ArduinoPrime.direction.stop.toString())
				&& System.currentTimeMillis() - start < 10000) { Util.delay(10); } // wait

        if (settings.getBoolean(ManualSettings.lidar))  return;

		Util.delay(ArduinoPrime.LINEAR_STOP_DELAY);

		/* rotate to localize */
		app.comport.checkisConnectedBlocking(); // pcb could reset changing from wall to battery
		app.driverCallServer(PlayerCommands.rotate, "360");
		Util.delay((long) (360 / state.getDouble(State.values.odomturndpms.toString())) + 1000);
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
		
		state.set(values.waypointbusy, "true");

		boolean rotate = false;
		boolean email = false;
		boolean rss = false;
		boolean motion = false;
		boolean notdetect = false;
		boolean sound = false;
		boolean human = false;
		boolean photo = false;
		boolean record = false;
		
		boolean camera = false;
		boolean mic = false;
		String notdetectedaction = "";

		boolean camAlreadyOn = false;
//		if (!state.get(values.stream).equals(Application.streamstate.stop.toString()))
//			camAlreadyOn = true;
		
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

				case "record video":
					record = true;
					camera = true;
					mic = true;
					break;
			}
    	}

		// if no camera, what's the point in rotating
    	if (!camera && rotate) {
			rotate = false;
			app.driverCallServer(PlayerCommands.messageclients, "rotate action ignored, camera unused");
		}

    	// VIDEOSOUNDMODELOW required for flash stream activity function to work, saves cpu for camera
    	String previousvideosoundmode = state.get(State.values.videosoundmode);
    	if (mic || camera) app.driverCallServer(PlayerCommands.videosoundmode, Application.VIDEOSOUNDMODELOW);

		// setup camera mode and position
		if (camera) {
			if (!camAlreadyOn) {
				if (human)
					app.driverCallServer(PlayerCommands.streamsettingsset, Application.camquality.med.toString());
				else if (motion)
					app.driverCallServer(PlayerCommands.streamsettingsset, Application.camquality.high.toString());
				else if (photo)
					app.driverCallServer(PlayerCommands.streamsettingscustom, "1280_720_8_85");
				else // record
					app.driverCallServer(PlayerCommands.streamsettingsset, Application.camquality.high.toString());
			}

			if (photo)
				app.driverCallServer(PlayerCommands.camtilt, String.valueOf(ArduinoPrime.CAM_HORIZ - ArduinoPrime.CAM_NUDGE * 2));
			else
				app.driverCallServer(PlayerCommands.camtilt, String.valueOf(ArduinoPrime.CAM_HORIZ-ArduinoPrime.CAM_NUDGE*3));
		}

        if (mic) {
            if (state.exists(values.lidar)) {
                state.set(values.lidar, lidarstate.disabled.toString());
            }
        }

		// turn on cam and or mic, allow delay for normalize
		if (!camAlreadyOn) {
			if (camera && mic) {
				app.driverCallServer(PlayerCommands.publish, Application.streamstate.camandmic.toString());
				Util.delay(5000);
				if (!settings.getBoolean(ManualSettings.useflash)) Util.delay(5000); // takes a while for 2 streams
			} else if (camera && !mic) {
				app.driverCallServer(PlayerCommands.publish, Application.streamstate.camera.toString());
				Util.delay(5000);
			} else if (!camera && mic) {
				app.driverCallServer(PlayerCommands.publish, Application.streamstate.mic.toString());
				Util.delay(5000);
			}
		}

		String recordlink = null;
		if (record)  recordlink = app.video.record(Settings.TRUE); // start recording

		long waypointstart = System.currentTimeMillis();
		long delay = 10000;
 		if (duration < delay) duration = delay;
		int turns = 0;
		int maxturns = 8;
		if (!rotate) {
			delay = duration;
			turns = maxturns;
		}

		// remain at waypoint rotating and/or waiting, detection running if enabled
		while (System.currentTimeMillis() - waypointstart < duration || turns < maxturns) {

			if (!state.exists(State.values.navigationroute)) return;
	    	if (!state.get(State.values.navigationrouteid).equals(id)) return;

			state.delete(State.values.streamactivity);

			// enable sound detection
			if (sound) {
				if (!settings.getBoolean(ManualSettings.useflash))   app.video.sounddetect(Settings.TRUE);
				else   app.driverCallServer(PlayerCommands.setstreamactivitythreshold,
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
				if (!settings.getBoolean(ManualSettings.useflash))  SystemWatchdog.waitForCpu();

				String link = FrameGrabHTTP.saveToFile(null);

				Util.delay(2000); // allow time for framgrabusy flag to be set true
				long timeout = System.currentTimeMillis() + 10000;
				while (state.getBoolean(values.framegrabbusy) && System.currentTimeMillis() < timeout) Util.delay(10);
				Util.delay(3000); // allow time to download

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
						state.get(State.values.navigationroute), consecutiveroute, 0);
			}

			// ALERT
			if (state.exists(State.values.streamactivity) && ! notdetect) {

				String streamactivity =  state.get(State.values.streamactivity);
				String msg = "Detected: "+streamactivity+", time: "+
						Util.getTime()+", at waypoint: " + wpname + ", route: " + name;
				Util.log(msg + " " + streamactivity, this);

				String navlogmsg = "Detected: ";

				if (streamactivity.contains("video")) {
					navlogmsg += "motion";
				}
				else if (streamactivity.contains("audio")) {
					navlogmsg += "sound";
				}
				else navlogmsg += streamactivity;


				String link = "";
				if (streamactivity.contains("video") || streamactivity.contains(OpenCVObjectDetect.HUMAN)) {
					link = FrameGrabHTTP.saveToFile("?mode=processedImgJPG");
					navlogmsg += "<br><a href='" + link + "' target='_blank'>image link</a>";
				}

				if (email || rss) {

					if (streamactivity.contains(OpenCVObjectDetect.HUMAN)) {
						msg = "[Oculus Prime Detected "+streamactivity+"] " + msg;
						msg += "\nimage link: " + link + "\n";
						Util.delay(3000); // allow time for download thread to capture image before turning off camera
					}
					if (streamactivity.contains("video")) {
						msg = "[Oculus Prime Detected Motion] " + msg;
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
						state.get(State.values.navigationroute), consecutiveroute, 0);

				// shut down sensing
				if (state.exists(State.values.motiondetect))
					app.driverCallServer(PlayerCommands.motiondetectcancel, null);
				if (state.exists(State.values.objectdetect))
					app.driverCallServer(PlayerCommands.objectdetectcancel, null);
				if (sound) {
					if (!settings.getBoolean(ManualSettings.useflash))   // app.video.sounddetect(Settings.FALSE);
						app.driverCallServer(PlayerCommands.sounddetect, Settings.FALSE);
					else   app.driverCallServer(PlayerCommands.setstreamactivitythreshold, "0 0");
				}

				break; // go to next waypoint, stop if rotating
			}

			// nothing detected, shut down sensing
			if (state.exists(State.values.motiondetect))
				app.driverCallServer(PlayerCommands.motiondetectcancel, null);
			if (state.exists(State.values.objectdetect))
				app.driverCallServer(PlayerCommands.objectdetectcancel, null);
			if (sound) {
				if (!settings.getBoolean(ManualSettings.useflash))   // app.video.sounddetect(Settings.FALSE);
					app.driverCallServer(PlayerCommands.sounddetect, Settings.FALSE);
				else   app.driverCallServer(PlayerCommands.setstreamactivitythreshold, "0 0");
			}

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
						state.get(State.values.navigationroute), consecutiveroute, 0);
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

		// END RECORD
		if (record && recordlink != null) {

			String navlogmsg = "<a href='" + recordlink + "_video.flv' target='_blank'>Video</a>";
			if (!settings.getBoolean(ManualSettings.useflash))
				navlogmsg += "<br><a href='" + recordlink + "_audio.flv' target='_blank'>Audio</a>";
			String msg = "[Oculus Prime Video] ";
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
			navlog.newItem(NavigationLog.VIDEOSTATUS, navlogmsg, routestarttime, wpname,
					state.get(State.values.navigationroute), consecutiveroute, 0);
			app.video.record(Settings.FALSE); // stop recording
		}

		if (!camAlreadyOn)
			app.driverCallServer(PlayerCommands.publish, Application.streamstate.stop.toString());
		if (camera) {
			app.driverCallServer(PlayerCommands.spotlight, "0");
			app.driverCallServer(PlayerCommands.cameracommand, ArduinoPrime.cameramove.horiz.toString());
		}
		
		if (mic) {
		    app.driverCallServer(PlayerCommands.videosoundmode, previousvideosoundmode);
            if (state.exists(values.lidar)) {
                state.set(values.lidar, lidarstate.enabled.toString());
                Util.delay(5000);
            }
        }

        state.set(values.waypointbusy, "false");

	}

	public static boolean turnLightOnIfDark() {

		if (state.getInteger(values.spotlightbrightness) == 100) return false; // already on

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
		batteryskips = 0;
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
