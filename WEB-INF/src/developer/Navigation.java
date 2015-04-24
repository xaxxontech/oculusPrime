package developer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

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
	public static final int RESTARTAFTERCONSECUTIVEROUTES = 555;
	public static final long ROSSHUTDOWNDELAY = 20000;
	private final Settings settings = Settings.getReference();

	/** Constructor */
	public Navigation(Application a){ 
		app = a;
		Ros.loadwaypoints();
	}	
	
	public void gotoWaypoint(final String str) {
		if (state.getBoolean(State.values.autodocking)) {
			app.driverCallServer(PlayerCommands.messageclients, "command dropped, autodocking");
			return;
		}

		if (state.get(State.values.dockstatus).equals(AutoDock.UNDOCKED) &&
				!state.exists(State.values.navigationenabled))  {
			app.driverCallServer(PlayerCommands.messageclients,
					"Can't navigate, location unknown");
			return;
		}
		
		new Thread(new Runnable() { public void run() {

			if (!waitForNavSystem()) {
				app.driverCallServer(PlayerCommands.messageclients, "Unable to start navigation");
				return;
			}
			
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
		if (!state.exists(State.values.navigationenabled)) startNavigation();
		else if (!state.getBoolean(State.values.navigationenabled)) startNavigation();
		
		long start = System.currentTimeMillis();
		while (!state.getBoolean(State.values.navigationenabled) 
				&& System.currentTimeMillis() - start < NAVSTARTTIMEOUT*3) { Util.delay(50);  } // wait

		if (!state.getBoolean(State.values.navigationenabled)) {
			app.driverCallServer(PlayerCommands.messageclients, "navigation start failure");
			return false;
		}
		
		if (!state.get(State.values.dockstatus).equals(AutoDock.UNDOCKED))
			state.set(State.values.rosinitialpose, "0_0_0");
		
		return true;
	}
	
	public void startNavigation() {
		if (state.exists(State.values.navigationenabled)) {
			Util.log("can't start navigation, already running", this);
			return;
		}

		new Thread(new Runnable() { public void run() {
			app.driverCallServer(PlayerCommands.messageclients, "starting navigation, please wait");
//			stopNavigation();
			state.set(State.values.navigationenabled, false); // false=pending, set true by ROS node when ready
//			Util.delay(3000);
			Ros.launch(Ros.REMOTE_NAV);

			// wait
			long start = System.currentTimeMillis();
			while (!state.getBoolean(State.values.navigationenabled) 
					&& System.currentTimeMillis() - start < NAVSTARTTIMEOUT) { Util.delay(50);  } // wait

			app.driverCallServer(PlayerCommands.streamsettingsset, Application.camquality.med.toString());

			if (state.getBoolean(State.values.navigationenabled)) return; // success
			
			// ========try again if needed, just once======
			if (!state.exists(State.values.navigationenabled)) return; // in case cancelled

			Util.log("navigation start attempt #2", this);
			stopNavigation(); // ros script deletes state navigationenabled after couple secs
			state.set(State.values.navigationenabled, false); // false=pending, set true by ROS node when ready
			Util.delay(ROSSHUTDOWNDELAY);
			Ros.launch(Ros.REMOTE_NAV);

			// wait
			start = System.currentTimeMillis();
			while (!state.getBoolean(State.values.navigationenabled)
					&& System.currentTimeMillis() - start < NAVSTARTTIMEOUT) { Util.delay(50);  } // wait, again

			if (state.getBoolean(State.values.navigationenabled)) return; // success
			else  stopNavigation();

		}  }).start();

	}
	
	public void stopNavigation() {
		state.delete(State.values.navigationenabled);
//		if (state.exists(State.values.navigationroute)) cancelRoute(); // don't do this
		Util.systemCall("pkill roslaunch");
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
		else if (!state.exists(State.values.navigationenabled)) {
				app.driverCallServer(PlayerCommands.messageclients, "navigation not running");
			return;
		}
		else if (!state.getBoolean(State.values.navigationenabled)) {
			app.driverCallServer(PlayerCommands.messageclients, "navigation not running");
			return;
		}
		
		Ros.setWaypointAsGoal(DOCK);
		state.set(State.values.roscurrentgoal, "pending");
		
		new Thread(new Runnable() { public void run() {
			
			long start = System.currentTimeMillis();
			while (state.exists(State.values.roscurrentgoal) 
					&& System.currentTimeMillis() - start < WAYPOINTTIMEOUT) { Util.delay(100);  } // wait
		
			if ( !state.exists(State.values.rosgoalstatus)) { //
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
			Util.delay(7000); // 5000 too low, massive cpu sometimes here
			app.comport.checkisConnectedBlocking(); // just in case
			app.driverCallServer(PlayerCommands.odometrystop, null); // just in case, odo messes up docking if ros not killed
			
			// dock
			app.driverCallServer(PlayerCommands.videosoundmode, Application.VIDEOSOUNDMODELOW); // saves CPU
			app.driverCallServer(PlayerCommands.streamsettingsset, Application.camquality.high.toString());
			app.driverCallServer(PlayerCommands.publish, Application.streamstate.camera.toString());
			app.driverCallServer(PlayerCommands.spotlight, "0");
			app.driverCallServer(PlayerCommands.cameracommand, ArduinoPrime.cameramove.reverse.toString());
//			Util.delay(25);
			app.driverCallServer(PlayerCommands.floodlight, Integer.toString(AutoDock.FLHIGH));
//			Util.delay(25);
			app.driverCallServer(PlayerCommands.left, "180");
			Util.delay(app.comport.fullrotationdelay/2 + 2000);
			
			// make sure dock in view before calling autodock go
			// dock detect, rotate if necessary
			int rot = 0;
			while (true) {
				
				app.driverCallServer(PlayerCommands.dockgrab, AutoDock.HIGHRES);
				start = System.currentTimeMillis();
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
				
				if (rot == 16) { // failure give up
//					callForHelp(subject, body);
					app.driverCallServer(PlayerCommands.publish, Application.streamstate.stop.toString());
					app.driverCallServer(PlayerCommands.floodlight, "0");
					app.driverCallServer(PlayerCommands.messageclients, "failed to find dock");
					return;
				}
			}
			
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
				!state.exists(State.values.navigationenabled))  {
			app.driverCallServer(PlayerCommands.messageclients,
					"Can't start route, location unknown");
			return;
		}
		
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
			int consecutiveroute = 1;
			
			while (true) {

				state.delete(State.values.nextroutetime);

				if (state.get(State.values.dockstatus).equals(AutoDock.UNDOCKED) &&
						!state.exists(State.values.navigationenabled))  {
					app.driverCallServer(PlayerCommands.messageclients,
							"Can't navigate route, location unknown");
					cancelRoute();
					return;
				}

				if (!waitForNavSystem()) {
					app.driverCallServer(PlayerCommands.messageclients, "Unable to start navigation");
					cancelRoute();
					return;
				}

				// check if cancelled while waiting
				if (!state.exists(State.values.navigationroute)) return;
				if (!state.get(State.values.navigationrouteid).equals(id)) return;

				// undock if necessary
				if (!state.get(State.values.dockstatus).equals(AutoDock.UNDOCKED)) {
					state.set(State.values.motionenabled, true);
					app.comport.checkisConnectedBlocking(); // just in case
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

					Util.log("setting waypoint: "+wpname, this);
		    		if (!Ros.setWaypointAsGoal(wpname)) { // can't set waypoint, try the next one
						app.driverCallServer(PlayerCommands.messageclients, "route "+name+" unable to set waypoint");
						wpnum ++;
						continue;
		    		}
	
		    		state.set(State.values.roscurrentgoal, "pending");
		    		
		    		// wait to reach wayypoint
					long start = System.currentTimeMillis();
					while (state.exists(State.values.roscurrentgoal) 
							&& System.currentTimeMillis() - start < WAYPOINTTIMEOUT) { Util.delay(100);  }
					
					if (!state.exists(State.values.navigationroute)) return;
			    	if (!state.get(State.values.navigationrouteid).equals(id)) return;
	
					if (!state.exists(State.values.rosgoalstatus)) {
						Util.log("error, state rosgoalstatus null", this);
						cancelRoute();
						return;
					}
					
					// failed, try next waypoint
					if (!state.get(State.values.rosgoalstatus).equals(Ros.ROSGOALSTATUS_SUCCEEDED)) {
						app.driverCallServer(PlayerCommands.messageclients, "route "+name+" failed to reach waypoint");
						wpnum ++;
						continue; 
					}

					// send actions and duration delay to processRouteActions()
					NodeList actions = ((Element) waypoints.item(wpnum)).getElementsByTagName("action");
					long duration = Long.parseLong(
							((Element) waypoints.item(wpnum)).getElementsByTagName("duration").item(0).getTextContent());
					if (duration > 0)  processWayPointActions(actions, duration * 1000, wpname, name, id);
		    		
					Util.delay(1000);

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
					cancelRoute();
					// try docking one more time, sending alert if fail
					Util.log("calling redock()", this);
					app.driverCallServer(PlayerCommands.redock, SystemWatchdog.NOFORWARD);
					return; 
				}

				consecutiveroute ++;

				String msg = " min until next route: "+name+", run #"+consecutiveroute;
				if (consecutiveroute > RESTARTAFTERCONSECUTIVEROUTES) {
					msg = " min until app restart, max consecutive routes: "+RESTARTAFTERCONSECUTIVEROUTES+ " reached";
				}

					// delay to next route
				String min = navroute.getElementsByTagName("minbetween").item(0).getTextContent();
		    	long timebetween = Long.parseLong(min) * 1000 * 60;
				state.set(State.values.nextroutetime, System.currentTimeMillis()+timebetween);
				app.driverCallServer(PlayerCommands.messageclients, min +  msg);
		    	start = System.currentTimeMillis();
				while (System.currentTimeMillis() - start < timebetween) {
					if (!state.exists(State.values.navigationroute)) return;
			    	if (!state.get(State.values.navigationrouteid).equals(id)) return;
					Util.delay(1000);
				}

				state.delete(State.values.nextroutetime);

				if (consecutiveroute > RESTARTAFTERCONSECUTIVEROUTES)  {
					app.restart();
					return;
				}

			}
		
		}  }).start();
		
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
		// var navrouteavailableactions = ["rotate", "email", "rss", "motion", "not motion", "sound", "not sound" ];
		/*
		 * rotate only works with motion & notmotion (ie., camera) ignore otherwise
		 *     -rotate ~30 deg increments, fixed duration. start-stop
		 *     -minimum full rotation, or more if <duration> allows 
		 * <duration> -- cancel all actions and move to next waypoint (let actions complete)
		 * alerts: rss or email: send on detection from "motion", "not motion", "sound", "not sound" 
		 *      -once only from single waypoint, max 2 per route (on 1st detection, then summary at end)
		 * if no alerts, log only
		 */
    	// setstreamactivitythreshold 30 0  -- tested fairly sensitive, video, run camera first
    	// takes 5-10 seconds to init if mic is on (mic only, or mic + camera)

		boolean rotate = false;
		boolean email = false;
		boolean rss = false;
		boolean motion = false;
		boolean notmotion = false;
		boolean sound = false;
		boolean notsound = false;
		
		boolean camera = false;
		boolean mic = false;
		
    	for (int i=0; i< actions.getLength(); i++) {
    		String action = ((Element) actions.item(i)).getTextContent();
    		switch (action) {
			case "rotate": rotate = true; break;
			case "email": email = true; break;
			case "rss": rss = true; break;
			case "motion": motion = true;
				camera = true;
				break;
			case "notmotion": notmotion = true; 
				camera = true;
				break;
			case "sound": sound = true; 
				mic = true;
				break;
			case "notsound": notsound = true; 
				mic = true;
				break;
      		}	
    	}
    	
    	if (!camera) rotate = false; // if no camera, what's the point in rotating

    	// VIDEOSOUNDMODELOW required for flash stream activity function to work, saves cpu for camera
    	String previousvideosoundmode = state.get(State.values.videosoundmode);
    	if (mic || camera) app.driverCallServer(PlayerCommands.videosoundmode, Application.VIDEOSOUNDMODELOW);

		// setup camera
		if (camera) {
    		app.driverCallServer(PlayerCommands.streamsettingsset, Application.camquality.high.toString());
    		app.driverCallServer(PlayerCommands.cameracommand, ArduinoPrime.cameramove.upabit.toString());
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
			
			// start stream(s)
			if (camera && mic) {
				if (turnLightOnIfDark())  Util.delay(4000); // allow cam to adjust
//				app.driverCallServer(PlayerCommands.motiondetectgo, settings.readSetting(ManualSettings.motionthreshold));
				app.driverCallServer(PlayerCommands.motiondetectgo, null);
				app.driverCallServer(PlayerCommands.setstreamactivitythreshold,
						"0 "+settings.readSetting(ManualSettings.soundthreshold));
				Util.delay(2000); // mic takes a while to start up
			} else if (camera && !mic) {
				if (turnLightOnIfDark())  Util.delay(4000); // allow cam to adjust
//				app.driverCallServer(PlayerCommands.motiondetectgo, settings.readSetting(ManualSettings.motionthreshold));
				app.driverCallServer(PlayerCommands.motiondetectgo, null);
			} else if (!camera && mic) {
				app.driverCallServer(PlayerCommands.setstreamactivitythreshold,
						"0 "+settings.readSetting(ManualSettings.soundthreshold));
				Util.delay(2000); // mic takes a while to start up
			}

			// WAIT
			long start = System.currentTimeMillis();
			while (!state.exists(State.values.streamactivity) && System.currentTimeMillis() - start < delay) { Util.delay(10); }

			// ALERT
			if (state.exists(State.values.streamactivity)) {

				String streamactivity =  state.get(State.values.streamactivity);
				String msg = "detected "+Util.getTime()+", level " + streamactivity.replaceAll("\\D","") + ", at waypoint: " + wpname + ", route: " + name;
				Util.log(msg+" "+streamactivity, this);

				if (email || rss) { // && settings.getBoolean(ManualSettings.alertsenabled) ) {

					if (streamactivity.contains("video")) {
						msg = "[Oculus Prime Motion Detection] Motion " + msg;
						msg += "\nimage link:\n" + FrameGrabHTTP.saveToFile("?mode=processedImg");
						Util.delay(3000); // allow time for download thread to capture image before turning off camera
					}
					else if (streamactivity.contains("audio")) {
						msg = "[Oculus Prime Sound Detection] Sound " + msg;
					}

					if (email) {
						String emailto = settings.readSetting(ManualSettings.email_to_address);
						if (!emailto.equals(Settings.DISABLED))
							app.driverCallServer(PlayerCommands.email, emailto + " " + msg);
					}
					if (rss)	app.driverCallServer(PlayerCommands.rssadd, msg);
				}

				if (state.exists(State.values.streamactivityenabled))
					app.driverCallServer(PlayerCommands.setstreamactivitythreshold, "0 0");
				if (state.exists(State.values.motiondetectwatching))
					app.driverCallServer(PlayerCommands.motiondetectcancel, null);

				break; // if rotating, stop
			}
			else  {
				if (mic)   app.driverCallServer(PlayerCommands.setstreamactivitythreshold, "0 0");
				if (camera) app.driverCallServer(PlayerCommands.motiondetectcancel, null);
			}

			if (rotate) {
				if (camera) Util.delay(500); // TODO: need this due to motiondetect cpu 100%?
				app.driverCallServer(PlayerCommands.left, "45");

				long stopwaiting = System.currentTimeMillis()+5000; // timeout if error
				while(!state.get(State.values.direction).equals(ArduinoPrime.direction.stop.toString()) &&
						System.currentTimeMillis() < stopwaiting) { Util.delay(10); } // wait for stop

				Util.delay(3000); // allow cam to normalize
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
		long start = System.currentTimeMillis();
		while (!state.exists(State.values.lightlevel) && System.currentTimeMillis() - start < 2000) {
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

	public void cancelRoute() {
		state.delete(State.values.navigationroute); // this eventually stops currently running route
		goalCancel();
		state.delete(State.values.nextroutetime);

		// check for route name
		Document document = Util.loadXMLFromString(routesLoad());
		NodeList routes = document.getDocumentElement().getChildNodes();

		for (int i = 0; i< routes.getLength(); i++) {
			((Element) routes.item(i)).getElementsByTagName("active").item(0).setTextContent("false");
		}

		String xmlString = Util.XMLtoString(document);
		saveRoute(xmlString);

		app.driverCallServer(PlayerCommands.messageclients, "all routes cancelled");
	}

}
