package developer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Calendar;
import java.util.Date;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;

import developer.image.OpenCVObjectDetect;
import oculusPrime.Application;
import oculusPrime.AutoDock;
import oculusPrime.AutoDock.autodockmodes;
import oculusPrime.State;
import oculusPrime.State.values;
import oculusPrime.FrameGrabHTTP;
import oculusPrime.GUISettings;
import oculusPrime.ManualSettings;
import oculusPrime.Observer;
import oculusPrime.PlayerCommands;
import oculusPrime.Settings;
import oculusPrime.SystemWatchdog;
import oculusPrime.Util;
import oculusPrime.commport.ArduinoPrime;

public class Navigation implements Observer {
	
	public static final long WAYPOINTTIMEOUT = Util.FIVE_MINUTES;
	public static final long NAVSTARTTIMEOUT = Util.TWO_MINUTES;
	public static final int RESTARTAFTERCONSECUTIVEROUTES = 15; // TODO: set to 15 in production
	public static final String ESTIMATED_DISTANCE_TAG = "estimateddistance";
	public static final String ESTIMATED_TIME_TAG = "estimatedtime";
	public static final String ROUTE_COUNT_TAG = "routecount";
	public static final String ROUTE_FAIL_TAG = "routefail";
	
	private Application app = null;	
	private static Settings settings = Settings.getReference();
	private static State state = State.getReference();
	private static final String DOCK = "dock"; // waypoint name
	private static final String redhome = System.getenv("RED5_HOME");
	public static final File navroutesfile = new File(redhome+"/conf/navigationroutes.xml");
	
	// SHOULD ALL BE PUT IN STATE? 
	public volatile boolean navdockactive = false;
	public static int consecutiveroute = 1;   
	public static long routemillimeters = 0;  
	public static long routestarttime = 0;
	public static int estimatedmeters = 0;	      
	public static int estimatedtime = 0;
	public static int rotations = 0;
	private boolean failed = false;
	
	/** Constructor */

	public Navigation(Application a) {
		state.set(values.navsystemstatus, Ros.navsystemstate.stopped.name());
		Ros.loadwaypoints();
		Ros.rospackagedir = Ros.getRosPackageDir(); // required for map saving
		state.addObserver(this);
		app = a;
	}	
	
	@Override
	public void updated(String key) {
		if(key.equals(values.distanceangle.name())){
			try {
				int mm = Integer.parseInt(state.get(values.distanceangle).split(" ")[0]);
				if(mm > 0) routemillimeters += mm;
			} catch (Exception e){}
		}
		if(key.equals(values.recoveryrotation.name())){
			if(state.getBoolean(values.recoveryrotation)) rotations++; 
		}
	}

	public void gotoWaypoint(final String str) {
		if (state.getBoolean(values.autodocking)) {
			app.driverCallServer(PlayerCommands.messageclients, "command dropped, autodocking");
			return;
		}

		if (state.equals(values.dockstatus, AutoDock.UNDOCKED) &&
				!state.equals(values.navsystemstatus, Ros.navsystemstate.running.name()))  {
			app.driverCallServer(PlayerCommands.messageclients, "Can't navigate, location unknown");
			return;
		}
		
		new Thread(new Runnable() { public void run() {

			if (!waitForNavSystem()) return;
			
			// undock if necessary
			if (!state.equals(values.dockstatus, AutoDock.UNDOCKED)) undockandlocalize();
			
			if (!Ros.setWaypointAsGoal(str))
				app.driverCallServer(PlayerCommands.messageclients, "unable to set waypoint");

		}  }).start();
	}
	
	private boolean waitForNavSystem() { // blocking

		if (state.equals(values.navsystemstatus, Ros.navsystemstate.mapping.name()) ||
				state.equals(values.navsystemstatus, Ros.navsystemstate.stopping.name())) {
			app.driverCallServer(PlayerCommands.messageclients, "Navigation.waitForNavSystem(): can't start navigation");
			return false;
		}

		startNavigation();

		long start = System.currentTimeMillis();
		while (!state.get(values.navsystemstatus).equals(Ros.navsystemstate.running.name())
				&& System.currentTimeMillis() - start < NAVSTARTTIMEOUT*3) { Util.delay(50);  } // wait

		if (!state.equals(values.navsystemstatus, Ros.navsystemstate.running.name())) {
			app.driverCallServer(PlayerCommands.messageclients, "Navigation.waitForNavSystem(): navigation start failure");
			return false;
		}

		return true;
	}

	public void startMapping() {
		if (!state.equals(values.navsystemstatus, Ros.navsystemstate.stopped.name())) {
			app.driverCallServer(PlayerCommands.messageclients, "Navigation.startMapping(): unable to start mapping, system already running");
			return;
		}

		if (!Ros.launch(Ros.MAKE_MAP)) {
			app.driverCallServer(PlayerCommands.messageclients, "roslaunch already running, aborting mapping start");
			return;
		}

		app.driverCallServer(PlayerCommands.messageclients, "starting mapping, please wait");
		state.set(values.navsystemstatus, Ros.navsystemstate.starting.name()); // set running by ROS node when ready
		app.driverCallServer(PlayerCommands.streamsettingsset, Application.camquality.med.name());
		Ros.launch(Ros.MAKE_MAP);
	}

	public void startNavigation() {
		if (!state.equals(values.navsystemstatus, Ros.navsystemstate.stopped)) return;
		new Thread(new Runnable() { public void run() {
			app.driverCallServer(PlayerCommands.messageclients, "starting navigation, please wait");
			if (!Ros.launch(Ros.REMOTE_NAV)) {
				app.driverCallServer(PlayerCommands.messageclients, "roslaunch already running, abort");
				return;
			}
			state.set(values.navsystemstatus, Ros.navsystemstate.starting.name()); // set running by ROS node when ready

			// wait
			long start = System.currentTimeMillis();
			while (!state.equals(values.navsystemstatus, Ros.navsystemstate.running.name())
					&& System.currentTimeMillis() - start < NAVSTARTTIMEOUT) { Util.delay(50);  } // wait

			if (state.equals(values.navsystemstatus, Ros.navsystemstate.running)){
				if (settings.getBoolean(ManualSettings.useflash))
					app.driverCallServer(PlayerCommands.streamsettingsset, Application.camquality.med.name()); // reduce cpu
				if (!state.equals(values.dockstatus, AutoDock.UNDOCKED))
					state.set(values.rosinitialpose, "0_0_0");
				Util.log("navigation running", this);
				return; // success
			}

			// ========try again if needed, just once======
			
			// in case cancelled
			if (state.equals(values.navsystemstatus, Ros.navsystemstate.stopping.name()) ||
					state.equals(values.navsystemstatus, Ros.navsystemstate.stopped.name())) return; 
			
			Util.log("navigation start attempt #2", this);
			stopNavigation();
			while( ! state.equals(values.navsystemstatus, Ros.navsystemstate.stopped)) Util.delay(10);

			if( ! Ros.launch(Ros.REMOTE_NAV)) {
				app.driverCallServer(PlayerCommands.messageclients, "roslaunch already running, abort");
				return;
			}

			start = System.currentTimeMillis(); // wait
			while (!state.equals(values.navsystemstatus, Ros.navsystemstate.running)
					&& System.currentTimeMillis() - start < NAVSTARTTIMEOUT) Util.delay(50);

			// check if running
			if (state.equals(values.navsystemstatus, Ros.navsystemstate.running)) {
				if (settings.getBoolean(ManualSettings.useflash))
					app.driverCallServer(PlayerCommands.streamsettingsset, Application.camquality.med.name()); // reduce cpu
				if (!state.equals(values.dockstatus, AutoDock.UNDOCKED))
					state.set(values.rosinitialpose, "0_0_0");
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

		if (state.equals(values.navsystemstatus, Ros.navsystemstate.stopped)) return;

		state.set(values.navsystemstatus, Ros.navsystemstate.stopping.name());
		new Thread(new Runnable() { public void run() {
			Util.delay(Ros.ROSSHUTDOWNDELAY);
			state.set(values.navsystemstatus, Ros.navsystemstate.stopped.name());
		}}).start();
	}

	public void dock() {
		if (state.getBoolean(values.autodocking)  ) {
			app.driverCallServer(PlayerCommands.messageclients, "autodocking in progress, command dropped");
			return;
		}
		else if (state.equals(values.dockstatus, AutoDock.DOCKED)) {
			app.driverCallServer(PlayerCommands.messageclients, "already docked, command dropped");
			return;
		}
		else if (!state.equals(values.navsystemstatus, Ros.navsystemstate.running)) {
			app.driverCallServer(PlayerCommands.messageclients, "navigation not running");
			return;
		}

		SystemWatchdog.waitForCpu();

		Ros.setWaypointAsGoal(DOCK);
		state.set(values.roscurrentgoal, "pending");

		new Thread(new Runnable() { public void run() {

			long start = System.currentTimeMillis();

			// store goal coords
			while (state.equals(values.roscurrentgoal, "pending") && System.currentTimeMillis() - start < 1000) Util.delay(10);
			if (!state.exists(values.roscurrentgoal)) return; // avoid null pointer
			String goalcoords = state.get(values.roscurrentgoal);

			// wait to reach waypoint
			start = System.currentTimeMillis();
			while (System.currentTimeMillis() - start < WAYPOINTTIMEOUT && state.exists(values.roscurrentgoal)) {
				try {
					if(!state.equals(values.roscurrentgoal, goalcoords)) return; // waypoint changed while waiting
				} catch (Exception e) {Util.printError(e);}
				Util.delay(10);
			}

			if ( !state.exists(values.rosgoalstatus)) { 
				//this is (harmlessly) thrown normally nav goal cancelled (by driver stop command?)
				Util.log("error, rosgoalstatus null, setting to empty string", this); // TODO: testing
				state.set(values.rosgoalstatus, "");
			}

			if (!state.equals(values.rosgoalstatus, Ros.ROSGOALSTATUS_SUCCEEDED)) {
				app.driverCallServer(PlayerCommands.messageclients, "Navigation.dock() failed to reach dock");
				failed = true;
				return;
			}

			navdockactive = true;
			Util.delay(1000);

			// success, should be pointing at dock, shut down nav
			stopNavigation();
//			Util.delay(Ros.ROSSHUTDOWNDELAY/2); // 5000 too low, massive cpu sometimes here

			Util.delay(5000); // 5000 too low, massive cpu sometimes here
			SystemWatchdog.waitForCpu();
			
			if (!navdockactive) return;

			SystemWatchdog.waitForCpu();
			app.comport.checkisConnectedBlocking(); // just in case
			app.driverCallServer(PlayerCommands.odometrystop, null); // just in case, odo messes up docking if ros not killed

			// camera, lights

			// highres
			app.driverCallServer(PlayerCommands.streamsettingsset, Application.camquality.high.name());
			// only switch mode if camera not running, to avoid interruption of feed
			if (state.equals(values.stream, Application.streamstate.stop.name()) ||
					state.equals(values.stream, Application.streamstate.mic.name())) {
				app.driverCallServer(PlayerCommands.videosoundmode, Application.VIDEOSOUNDMODELOW); // saves CPU
				app.driverCallServer(PlayerCommands.publish, Application.streamstate.camera.name());
			}
			app.driverCallServer(PlayerCommands.spotlight, "0");
			app.driverCallServer(PlayerCommands.cameracommand, ArduinoPrime.cameramove.reverse.name());
			app.driverCallServer(PlayerCommands.floodlight, Integer.toString(AutoDock.FLHIGH));
			app.driverCallServer(PlayerCommands.left, "180");
			Util.delay(app.comport.fullrotationdelay/2 + 4000);  // tried changing this to 4000, didn't help

			if (!navdockactive) return;

			// TODO: debugging potential intermittent camera problem .. conincides with flash error 1009? checked, no 1009
//			state.delete(State.values.lightlevel);
//			app.driverCallServer(PlayerCommands.getlightlevel, null);
//			long timeout = System.currentTimeMillis() + 5000;
//			while (!state.exists(State.values.lightlevel) && System.currentTimeMillis() < timeout)  Util.delay(10);
//			if (state.exists(State.values.lightlevel)) Util.log("lightlevel: "+state.get(State.values.lightlevel), this);
//			else Util.log("error, lightlevel null", this);

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
					app.driverCallServer(PlayerCommands.cameracommand, ArduinoPrime.cameramove.reverse.name());
					app.driverCallServer(PlayerCommands.floodlight, Integer.toString(AutoDock.FLHIGH));
					app.driverCallServer(PlayerCommands.publish, Application.streamstate.camera.name());
					Util.delay(4000); // wait for cam startup, light adjust
					app.comport.checkisConnectedBlocking(); // just in case
					if (!navdockactive) return;
					if (!finddock(AutoDock.HIGHRES, true)) return; // highres dock search with rotate (slow)
				}
			}

			// onwards
			SystemWatchdog.waitForCpu();
			app.driverCallServer(PlayerCommands.autodock, autodockmodes.go.name());

			// wait while autodocking does its thing
			start = System.currentTimeMillis();
			while (state.getBoolean(values.autodocking) &&
					System.currentTimeMillis() - start < SystemWatchdog.AUTODOCKTIMEOUT) Util.delay(100);

			if (!navdockactive) return;

			if (state.equals(values.dockstatus, AutoDock.DOCKED)) {
				Util.delay(2000);
				app.driverCallServer(PlayerCommands.publish, Application.streamstate.stop.name());
			} else  Util.log("dock() - unable to dock", this);
		}}).start();

		navdockactive = false;
	}

	// dock detect, rotate if necessary
	private boolean finddock(String resolution, boolean rotate) {
		int rot = 0;
		while (navdockactive) {
			SystemWatchdog.waitForCpu(); // added stricter 40% check, lots of missed dock grabs here

			app.driverCallServer(PlayerCommands.dockgrab, resolution);
			long start = System.currentTimeMillis();
			while (!state.exists(values.dockfound.name()) && System.currentTimeMillis() - start < Util.ONE_MINUTE)
				Util.delay(10);  // wait

			if (state.getBoolean(values.dockfound)) break; // great, onwards
			else if (!rotate) return false;
			else { // rotate a bit
				app.comport.checkisConnectedBlocking(); // just in case
				app.driverCallServer(PlayerCommands.right, "25");
				Util.delay(10); // thread safe

				start = System.currentTimeMillis();
				while(!state.equals(values.direction, ArduinoPrime.direction.stop.name())
						&& System.currentTimeMillis() - start < 5000) { Util.delay(10); } // wait
				Util.delay(ArduinoPrime.TURNING_STOP_DELAY);
			}
			rot ++;

			if (rot == 1) Util.log("error, rotation required", this);

			if (rot == 21) { // failure give up
//					callForHelp(subject, body);
				app.driverCallServer(PlayerCommands.publish, Application.streamstate.stop.name());
				app.driverCallServer(PlayerCommands.floodlight, "0");
				app.driverCallServer(PlayerCommands.messageclients, "Navigation.finddock() failed to find dock");
				return false;
			}
		}
		if (!navdockactive) return false;
		return true;
	}

	public static void goalCancel() {
		state.set(values.rosgoalcancel, true); // pass info to ros node
		state.delete(values.roswaypoint);
	}

	public static String routesLoad() {
		String result = "";
		BufferedReader reader;
		try {
			reader = new BufferedReader(new FileReader(navroutesfile));
			String line = "";
			while ((line = reader.readLine()) != null) 	result += line;
			reader.close();
		} catch (Exception e) {
			return "<routeslist></routeslist>";
		}
		return result;
	}

	public static void saveRoute(String str) {
		try {
			FileWriter fw = new FileWriter(navroutesfile);
			fw.append(str);
			fw.close();
		} catch (Exception e){ Util.printError(e); }
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
	
	private boolean updateTimeToNextRoute(final Element navroute, final String id){ 
		
		// get schedule info, map days to numbers
		NodeList days = navroute.getElementsByTagName("day");
		if (days.getLength() == 0) {
			app.driverCallServer(PlayerCommands.messageclients, "Can't schedule route, no days specified");
			cancelRoute(id);
			return true;
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
		
		Calendar calendarnow = Calendar.getInstance();
		calendarnow.setTime(new Date());
		int daynow = calendarnow.get(Calendar.DAY_OF_WEEK); // 1-7 (friday is 6)
		boolean startroute = false;
		int nextdayindex = 99;
		for (int i=0; i<daynums.length; i++) {
			// check if need to start run right away
			if (daynums[i] == daynow -1 || daynums[i] == daynow || (daynums[i]==7 && daynow == 1)) { // yesterday or today
				Calendar testday = Calendar.getInstance();
				if (daynums[i] == daynow -1 || (daynums[i]==7 && daynow == 1)) { // yesterday
					testday.set(calendarnow.get(Calendar.YEAR), calendarnow.get(Calendar.MONTH),
							calendarnow.get(Calendar.DATE) - 1, starthour, startmin);
				} else { // today
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

		// determine seconds to next route
		if (!state.exists(values.nextroutetime)) { // only set once

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

			state.set(values.nextroutetime, testday.getTimeInMillis());	
		}
		return startroute;
	}
	
	public static void updateRouteEstimatess(final String name, final int seconds, final long mm){
		Document document = Util.loadXMLFromString(routesLoad());
		NodeList routes = document.getDocumentElement().getChildNodes();
		Element route = null;
		for (int i = 0; i < routes.getLength(); i++){
			String rname = ((Element) routes.item(i)).getElementsByTagName("rname").item(0).getTextContent();
			if (rname.equals(name)){
				route = (Element) routes.item(i);				
				try {	
					route.getElementsByTagName(ESTIMATED_DISTANCE_TAG).item(0).setTextContent(Util.formatFloat(mm / (double)1000, 0));
				} catch (Exception e) { // create if not there 
					Node dist = document.createElement(ESTIMATED_DISTANCE_TAG);
					dist.setTextContent(Util.formatFloat(mm / (double)1000, 1));
					route.appendChild(dist);
				}
				try {
					route.getElementsByTagName(ESTIMATED_TIME_TAG).item(0).setTextContent(Integer.toString(seconds));
				} catch (Exception e) { // create if not there 
					Node time = document.createElement(ESTIMATED_TIME_TAG);
					time.setTextContent(Integer.toString(seconds));
					route.appendChild(time);
				}
				saveRoute(Util.XMLtoString(document));
				break;
			}
		}
	}
	
	public static int getRouteFails(final String name){
		return Integer.parseInt(getRouteFailsString(name));
	}
	
	public static String getRouteFailsString(final String name){
		NodeList routes = Util.loadXMLFromString(routesLoad()).getDocumentElement().getChildNodes();
		for (int i = 0; i < routes.getLength(); i++){
			String rname = ((Element) routes.item(i)).getElementsByTagName("rname").item(0).getTextContent();
			if (rname.equals(name)){
				try {
					return ((Element) routes.item(i)).getElementsByTagName(ROUTE_FAIL_TAG).item(0).getTextContent(); 
				} catch (Exception e){}
				break;
			}
		}
		return "0";
	}
	
	public static int getRouteCount(final String name){
		return Integer.parseInt(getRouteCountString(name));
	}
	
	public static String getRouteCountString(final String name){
		NodeList routes = Util.loadXMLFromString(routesLoad()).getDocumentElement().getChildNodes();
		for (int i = 0; i < routes.getLength(); i++){
			String rname = ((Element) routes.item(i)).getElementsByTagName("rname").item(0).getTextContent();
			if (rname.equals(name)){
				try {	
					return ((Element) routes.item(i)).getElementsByTagName(ROUTE_COUNT_TAG).item(0).getTextContent(); 
				} catch (Exception e){break;}
			}
		}
		return "0";
	}
	
	public static String getRouteDistanceEstimate(final String name){
		NodeList routes = Util.loadXMLFromString(routesLoad()).getDocumentElement().getChildNodes();
		for (int i = 0; i < routes.getLength(); i++){
			String rname = ((Element) routes.item(i)).getElementsByTagName("rname").item(0).getTextContent();
			if (rname.equals(name)){
				try {
					return ((Element) routes.item(i)).getElementsByTagName(ESTIMATED_DISTANCE_TAG).item(0).getTextContent(); 
				} catch (Exception e){break;}
			}
		}
		return "0";
	}
	
	public static String getRouteTimeEstimate(final String name){
		NodeList routes = Util.loadXMLFromString(routesLoad()).getDocumentElement().getChildNodes();
		for (int i = 0; i < routes.getLength(); i++){
			String rname = ((Element) routes.item(i)).getElementsByTagName("rname").item(0).getTextContent();
			if (rname.equals(name)){
				try {
					return ((Element) routes.item(i)).getElementsByTagName(ESTIMATED_TIME_TAG).item(0).getTextContent(); 
				} catch (Exception e){break;}
			}
		}
		return "0";
	}
	
	public static void updateRouteStats(final String name, final int routecount, final int routefails){
		Document document = Util.loadXMLFromString(routesLoad());
		NodeList routes = document.getDocumentElement().getChildNodes();
		Element route = null;
		for (int i = 0; i < routes.getLength(); i++){
			String rname = ((Element) routes.item(i)).getElementsByTagName("rname").item(0).getTextContent();
			if (rname.equals(name)){
				route = (Element) routes.item(i);				
				try {
					route.getElementsByTagName(ROUTE_COUNT_TAG).item(0).setTextContent(Integer.toString(routecount));
				} catch (Exception e) { // create if not there 
					Node count = document.createElement(ROUTE_COUNT_TAG);
					count.setTextContent(Integer.toString(routecount));
					route.appendChild(count);
				}
				try {
					route.getElementsByTagName(ROUTE_FAIL_TAG).item(0).setTextContent(Integer.toString(routefails));
				} catch (Exception e) { // create if not there 
					Node fail = document.createElement(ROUTE_FAIL_TAG);
					fail.setTextContent(Integer.toString(routefails));
					route.appendChild(fail);
				}
				saveRoute(Util.XMLtoString(document));
				break;
			}
		}
	}
	
	public class watchOverdue implements Runnable {
	    public void run() {
	    	if (settings.getBoolean(ManualSettings.developer.name()) && estimatedtime > 0){
	    		
	    		Util.log("watching: " + state.get(values.navigationroute) + " meters: " + estimatedmeters + " seconds: "+estimatedtime, this);

				state.delete(values.routeoverdue);
				
				Util.delay(estimatedtime*1000); // TODO: make a setting? in seconds 
				
				// if missed the dock 
				if( ! (state.getBoolean(values.autodocking) || 
				   state.equals(values.roswaypoint, DOCK) ||
				   state.equals(values.dockstatus, AutoDock.DOCKING) || 
				   state.equals(values.dockstatus, AutoDock.DOCKED))){
					
					// condition 
					state.set(values.routeoverdue, true); // over due, cancel route, drive to dock.. 
					Util.log("Overdue on Route, estimated time: " + estimatedtime + " seconds", this);
		    		Util.log(state.get(values.navigationroute) + " meters: " + estimatedmeters + " seconds: "+estimatedtime, this);

					NavigationLog.newItem(NavigationLog.ERRORSTATUS, "Overdue, called back to dock after " + estimatedtime + " seconds");
				//	NavigationLog.newItem("Overdue: " + estimatedtime + " seconds");

				//	dock(); // set new target
					
				}
			}
	    }
	}
	
	public void runRoute(final String name) {
		
		// TODO: 
		// build error checking into this (ignore duplicate waypoints, etc)
		// assume goto dock at the end, whether or not dock is a waypoint

		if (state.getBoolean(values.autodocking)) {
			app.driverCallServer(PlayerCommands.messageclients, "command dropped, autodocking");
			return;
		}

		if (state.equals(values.dockstatus, AutoDock.UNDOCKED) &&
				!state.equals(values.navsystemstatus, Ros.navsystemstate.running.name())){
			app.driverCallServer(PlayerCommands.messageclients, "Can't start route, location unknown, command dropped");
			cancelAllRoutes();
			return;
		}

		if (state.exists(values.navigationroute)) cancelAllRoutes(); // if another route running	

		// check for route name and info.. 
		Document document = Util.loadXMLFromString(routesLoad());
		NodeList routes = document.getDocumentElement().getChildNodes();
		Element route = null;
		for (int i = 0; i < routes.getLength(); i++){
			// unflag any currently active routes. New active route gets flagged below..
    		String rname = ((Element) routes.item(i)).getElementsByTagName("rname").item(0).getTextContent();
    		((Element) routes.item(i)).getElementsByTagName("active").item(0).setTextContent("false");
			if(rname.equals(name)) route = (Element) routes.item(i); // break; }
		}

		if (route == null) { // name not found
			app.driverCallServer(PlayerCommands.messageclients, "route: "+name+" not found");
			return;
		}

		// start route
		final Element navroute = route;
		final String id = String.valueOf(System.nanoTime());
		state.set(values.navigationroute, name);
		state.set(values.navigationrouteid, id);
	
		// flag route active, save to xml file
		route.getElementsByTagName("active").item(0).setTextContent("true");
		String xmlstring = Util.XMLtoString(document);
		saveRoute(xmlstring);
		
		new Thread(new Runnable() { public void run() {
			
			app.driverCallServer(PlayerCommands.messageclients, "activating route: " + name);
			
			// repeat route schedule forever until cancelled
			while(true){
				
				// determine next scheduled route time, wait if necessary
				state.delete(values.nextroutetime);
				while (state.exists(values.navigationroute)){
					Util.delay(1000);
					if (!state.equals(values.navigationrouteid, id)) return;
					if (updateTimeToNextRoute(navroute, id)){
						state.delete(State.values.nextroutetime);
						break; // delete so not shown in gui 
					}
				}

				// check if cancelled while waiting
				if( ! state.exists(State.values.navigationroute)) return;
				if( ! state.equals(values.navigationrouteid, id)) return;

				if(state.equals(values.dockstatus, AutoDock.UNDOCKED) &&
						!state.equals(values.navsystemstatus, Ros.navsystemstate.running)) {
					app.driverCallServer(PlayerCommands.messageclients, "Can't navigate route, location unknown");
					cancelRoute(id);
					return;
				}

				if(!waitForNavSystem()){ 
					
					// not necessary ??
					// check if cancelled while waiting
					// if( ! state.exists(values.navigationroute)) return;
					// if( ! state.equals(values.navigationrouteid, id)) return;

					NavigationLog.newItem(NavigationLog.ERRORSTATUS, "unable to start navigation system");

					if(state.getUpTime() > Util.TEN_MINUTES) {
						app.driverCallServer(PlayerCommands.reboot, null);
						return;
					}

					if( ! delayToNextRoute(navroute, name, id)) return;
					continue;
				}

				// check if cancelled while waiting
				if( ! state.exists(values.navigationroute)) return;
				if( ! state.equals(values.navigationrouteid, id)) return;
					
				// undock if necessary
				if (!state.get(values.dockstatus).equals(AutoDock.UNDOCKED)) {
					SystemWatchdog.waitForCpu(); undockandlocalize();
				}
				
				app.driverCallServer(PlayerCommands.cameracommand, ArduinoPrime.cameramove.horiz.name());

				// start.. clear counters and flags  
				state.delete(values.recoveryrotation);
				state.delete(values.routeoverdue);
				estimatedmeters = (int) Long.parseLong(Util.formatFloat(getRouteDistanceEstimate(name), 0));
				estimatedtime = Integer.parseInt(getRouteTimeEstimate(name));
				routestarttime = System.currentTimeMillis();
				routemillimeters = 0;
				failed = false;
				rotations = 0;
				
				Util.log("starting.. estimate meters: " + estimatedmeters + " seconds: "+estimatedtime, this);	
				new Thread(new watchOverdue()).start(); 
				
		    	// go to each waypoint
		    	NodeList waypoints = navroute.getElementsByTagName("waypoint");	    	
		    	int wpnum = 0;
		    	while(wpnum < waypoints.getLength()) {

		    		// check if cancelled while waiting
					if( ! state.exists(values.navigationroute)) return;
					if( ! state.equals(values.navigationrouteid, id)) return;
					
		    		String wpname = ((Element) waypoints.item(wpnum)).getElementsByTagName("wpname").item(0).getTextContent();

					app.comport.checkisConnectedBlocking(); // just in case

		    		if(wpname.equals(DOCK)) break;

					SystemWatchdog.waitForCpu();
					Util.log("setting waypoint: "+wpname, this);
		    		if (!Ros.setWaypointAsGoal(wpname)) { // can't set waypoint, try the next one
						NavigationLog.newItem(NavigationLog.ERRORSTATUS, "unable to set waypoint", wpname, name);
						app.driverCallServer(PlayerCommands.messageclients, "route "+name+" unable to set waypoint");
						wpnum ++;
						continue;
		    		}
	
		    		state.set(values.roscurrentgoal, "pending");
		    		
		    		// wait to reach wayypoint
					long start = System.currentTimeMillis();
					while (state.exists(values.roscurrentgoal) && System.currentTimeMillis() - start < WAYPOINTTIMEOUT)Util.delay(100);
					
					// check if cancelled while waiting
					if( ! state.exists(values.navigationroute)) return;
					if( ! state.equals(values.navigationrouteid, id)) return;
					
					if( ! state.exists(values.rosgoalstatus)){ 
						// this is (harmlessly) thrown normally nav goal cancelled (by driver stop command?)
						Util.log("error, state rosgoalstatus null", this);
						state.set(values.rosgoalstatus, "error");
					}
					
					if( ! state.get(values.rosgoalstatus).equals(Ros.ROSGOALSTATUS_SUCCEEDED)) {
						
						NavigationLog.newItem(NavigationLog.ERRORSTATUS, "Failed to reach waypoint: "+wpname, wpname, name);
						app.driverCallServer(PlayerCommands.messageclients, "route "+name+" failed to reach waypoint");
						wpnum ++;
						// TODO: COUNT FAILURES BETTER ? 
						failed = true;
						continue; 
					}

					// send actions and duration delay to processRouteActions()
					NodeList actions = ((Element) waypoints.item(wpnum)).getElementsByTagName("action");
					long duration = Long.parseLong(
						((Element) waypoints.item(wpnum)).getElementsByTagName("duration").item(0).getTextContent());
					if (duration > 0)  processWayPointActions(actions, duration * 1000, wpname, name, id);
					
					//TODO: TESTING........
					if(settings.getBoolean(ManualSettings.developer.name())){
						// SystemWatchdog.waitForCpu();
						app.driverCallServer(PlayerCommands.left, "360");
						Util.delay((long) (360 / state.getDouble(values.odomturndpms.name())) + 1000);
						SystemWatchdog.waitForCpu();
					}
					
					wpnum ++;
				}
		    	
		    	// check if cancelled while waiting
				if( ! state.exists(values.navigationroute)) return;
				if( ! state.equals(values.navigationrouteid, id)) return;
				dock();
				
				// wait while autodocking does its thing 
				long start = System.currentTimeMillis();
				while (System.currentTimeMillis() - start < SystemWatchdog.AUTODOCKTIMEOUT + WAYPOINTTIMEOUT) {
					if( ! state.exists(values.navigationroute)) return;
					if( ! state.equals(values.navigationrouteid, id)) return;
					if(state.get(values.dockstatus).equals(AutoDock.DOCKED) && !state.getBoolean(values.autodocking)) break; 
					Util.delay(100); // success
				}
					
				if( ! state.get(values.dockstatus).equals(AutoDock.DOCKED)){
					
					// TODO: send alert?
					NavigationLog.newItem(NavigationLog.ERRORSTATUS, "Unable to dock");
					// try docking one more time, sending alert if fail
					stopNavigation();
					Util.log("navigation is off, trying redock()", this);
					Util.delay(Ros.ROSSHUTDOWNDELAY / 2); // 5000 too low, massive cpu sometimes here
					app.driverCallServer(PlayerCommands.redock, SystemWatchdog.NOFORWARD);
			
					if(!delayToNextRoute(navroute, name, id)) return;
					continue;
				}
			
				// TODO: flawless route? 
				if( /*!(overdue*/ rotations == 0 && !failed){
					
					int seconds = (int)((System.currentTimeMillis()-routestarttime)/1000);
				
					Util.log("["+ name + "] estimated: " + estimatedmeters + " meters: " +
							Util.formatFloat((double)routemillimeters/(double)1000) + 
							" delta: " + Math.abs(estimatedmeters - routemillimeters/1000) + " meters ", this);
					
					Util.log("["+ name + "] estimated: " + estimatedtime   + " seconds : " + seconds     
							+ " delta: " + Math.abs(estimatedtime - seconds) + " seconds ", this);
					
					if(estimatedtime == 0 && estimatedmeters > 0){
						// Util.log("route estimate is zero, meters = " + estimatedmeters, this);
						updateRouteEstimatess(name, seconds, ((estimatedmeters*1000 + routemillimeters/1000)/2));
					} 
					
					if(estimatedmeters == 0 && estimatedtime > 0){
						// Util.log("route estimated distance is zero, seconds = " + seconds, this);
						updateRouteEstimatess(name, ((estimatedtime + seconds)/2), routemillimeters);
					} 
					
					if(estimatedmeters > 0 && estimatedtime > 0){
						// Util.log("route distance and time greater zero.. compute average", this);
						updateRouteEstimatess(name, ((estimatedtime + seconds)/2),((estimatedmeters*1000 + routemillimeters)/2));
					} 
					
					if(estimatedmeters == 0 && estimatedtime == 0){
						// Util.log("route distance and time are zero, use these values", this);
						updateRouteEstimatess(name, seconds, estimatedmeters + routemillimeters);
					}						
				}
									
				if(failed) updateRouteStats(state.get(values.navigationroute), getRouteCount(name)+1, getRouteFails(name)+1);
				else updateRouteStats(state.get(values.navigationroute), getRouteCount(name)+1, getRouteFails(name));
				NavigationLog.newItem(NavigationLog.COMPLETEDSTATUS, null);

				// reset 	
				state.delete(values.recoveryrotation);
				state.delete(values.routeoverdue);
				consecutiveroute++;

				// wait 
				if(!delayToNextRoute(navroute, name, id)) return;
			}
		}}).start();
	}


	private void undockandlocalize() { // blocking
		state.set(values.motionenabled, true);
		double distance = 1.0;
		app.driverCallServer(PlayerCommands.forward, String.valueOf(distance));
		Util.delay((long) (distance / state.getDouble(values.odomlinearmpms.name())) + 2000);

		// rotate to localize
		app.comport.checkisConnectedBlocking(); // pcb could reset changing from wall to battery
		app.driverCallServer(PlayerCommands.left, "360");
		Util.delay((long)(360 / state.getDouble(values.odomturndpms.name())) + 2000);
	}

	private boolean delayToNextRoute(Element navroute, String name, String id) {
		String msg = " min until next route: "+name+", run #"+consecutiveroute;
		if (consecutiveroute > RESTARTAFTERCONSECUTIVEROUTES) {
			msg = " min until reboot, max consecutive routes: "+RESTARTAFTERCONSECUTIVEROUTES+ " reached";
		}

		String min = navroute.getElementsByTagName("minbetween").item(0).getTextContent();
		long timebetween = Long.parseLong(min) * 1000 * 60;
		state.set(values.nextroutetime, System.currentTimeMillis()+timebetween);
		app.driverCallServer(PlayerCommands.messageclients, min +  msg);
		long start = System.currentTimeMillis();
		while (System.currentTimeMillis() - start < timebetween) {
			if (!state.exists(values.navigationroute)) {
				state.delete(values.nextroutetime);
				return false;
			}
			if (!state.get(values.navigationrouteid).equals(id)) {
				state.delete(values.nextroutetime);
				return false;
			}
			Util.delay(1000);
		}

		if (consecutiveroute > RESTARTAFTERCONSECUTIVEROUTES && state.getUpTime() > Util.TEN_MINUTES){ 
			Util.log("rebooting, max consecutive routes reached", this); // prevent runaway reboots
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
		boolean record = false;
		
		boolean camera = false;
		boolean mic = false;
		String notdetectedaction = "";
		
    	for (int i=0; i < actions.getLength(); i++) {
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
    	String previousvideosoundmode = state.get(values.videosoundmode);
    	if (mic || camera) app.driverCallServer(PlayerCommands.videosoundmode, Application.VIDEOSOUNDMODELOW);
    	
		// setup camera mode and position
		if (camera) {
			if (human) app.driverCallServer(PlayerCommands.streamsettingsset, Application.camquality.med.name());
			else if (motion) app.driverCallServer(PlayerCommands.streamsettingsset, Application.camquality.high.name());
			else app.driverCallServer(PlayerCommands.streamsettingscustom, "1280_720_8_85");
			if (photo) app.driverCallServer(PlayerCommands.camtilt, String.valueOf(ArduinoPrime.CAM_HORIZ-ArduinoPrime.CAM_NUDGE*2));
			else app.driverCallServer(PlayerCommands.camtilt, String.valueOf(ArduinoPrime.CAM_HORIZ-ArduinoPrime.CAM_NUDGE*5));

			if (human)
				app.driverCallServer(PlayerCommands.streamsettingsset, Application.camquality.med.name());
			else if (motion)
    			app.driverCallServer(PlayerCommands.streamsettingsset, Application.camquality.high.name());
			else if (photo)
				app.driverCallServer(PlayerCommands.streamsettingscustom, "1280_720_8_85");
			else // record
				app.driverCallServer(PlayerCommands.streamsettingsset, Application.camquality.high.name());

			if (photo)app.driverCallServer(PlayerCommands.camtilt, String.valueOf(ArduinoPrime.CAM_HORIZ - ArduinoPrime.CAM_NUDGE * 2));
			else app.driverCallServer(PlayerCommands.camtilt, String.valueOf(ArduinoPrime.CAM_HORIZ-ArduinoPrime.CAM_NUDGE*3));
			if (human) app.driverCallServer(PlayerCommands.streamsettingsset, Application.camquality.med.name());
			else if (motion) app.driverCallServer(PlayerCommands.streamsettingsset, Application.camquality.high.name());
			else app.driverCallServer(PlayerCommands.streamsettingscustom, "1280_720_8_85");
			if (photo) app.driverCallServer(PlayerCommands.camtilt, String.valueOf(ArduinoPrime.CAM_HORIZ - ArduinoPrime.CAM_NUDGE * 2));
			else app.driverCallServer(PlayerCommands.camtilt, String.valueOf(ArduinoPrime.CAM_HORIZ-ArduinoPrime.CAM_NUDGE*5));
		}
																				
		// turn on cam and or mic, allow delay for normalize
		if (camera && mic) {
			app.driverCallServer(PlayerCommands.publish, Application.streamstate.camandmic.name());
			Util.delay(5000);
			if (!settings.getBoolean(ManualSettings.useflash)) Util.delay(5000); // takes a while for 2 streams
		} else if (camera && !mic) {
			app.driverCallServer(PlayerCommands.publish, Application.streamstate.camera.name());
			Util.delay(5000);
		} else if (!camera && mic) {
			app.driverCallServer(PlayerCommands.publish, Application.streamstate.mic.name());
			Util.delay(5000);
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

			if( ! state.exists(values.navigationroute)) return;
	    	if( ! state.equals(values.navigationrouteid, id)) return;

			state.delete(values.streamactivity);

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
			if (human) app.driverCallServer(PlayerCommands.objectdetect, OpenCVObjectDetect.HUMAN);
			else if (motion) app.driverCallServer(PlayerCommands.motiondetect, null);

			// mic takes a while to start up
			if (sound && !lightondelay) Util.delay(2000);

			// ALL SENSES ENABLED, NOW WAIT
			long start = System.currentTimeMillis();
			while (!state.exists(values.streamactivity) && System.currentTimeMillis() - start < delay
					&& state.get(values.navigationrouteid).equals(id)) { Util.delay(10); }

			// PHOTO
			if (photo) {
				if (!settings.getBoolean(ManualSettings.useflash))  SystemWatchdog.waitForCpu();

				String link = FrameGrabHTTP.saveToFile("");

				Util.delay(2000); // allow time for framgrabusy flag to be set true
				long timeout = System.currentTimeMillis() + 10000;
				while (state.getBoolean(values.framegrabbusy) && System.currentTimeMillis() < timeout) Util.delay(10);
				Util.delay(3000); // allow time to download

				String navlogmsg = "<a href='" + link + "' target='_blank'>Photo</a>";
				String msg = "[Oculus Prime Photo] ";
				msg += navlogmsg+", time: "+ Util.getTime()+", at waypoint: " + wpname + ", route: " + name;

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

				NavigationLog.newItem(NavigationLog.PHOTOSTATUS, navlogmsg, wpname, state.get(values.navigationroute));
			}

			// ALERT
			if (state.exists(values.streamactivity) && ! notdetect) {

				String streamactivity =  state.get(values.streamactivity);
				String msg = "Detected: "+streamactivity+", time: "+ Util.getTime()+", at waypoint: " + wpname + ", route: " + name;
				Util.log(msg + " " + streamactivity, this);
				String navlogmsg = "Detected: "+streamactivity;

				String link = "";
				if (streamactivity.contains("video") || streamactivity.contains(OpenCVObjectDetect.HUMAN)) {
					link = FrameGrabHTTP.saveToFile("?mode=processedImgJPG");
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

				NavigationLog.newItem(NavigationLog.ALERTSTATUS, navlogmsg);

				// shut down sensing
				if (state.exists(values.motiondetect))
					app.driverCallServer(PlayerCommands.motiondetectcancel, null);
				if (state.exists(values.objectdetect))
					app.driverCallServer(PlayerCommands.objectdetectcancel, null);
				if (sound) {
					if (!settings.getBoolean(ManualSettings.useflash))   // app.video.sounddetect(Settings.FALSE);
						app.driverCallServer(PlayerCommands.sounddetect, Settings.FALSE);
					else   app.driverCallServer(PlayerCommands.setstreamactivitythreshold, "0 0");
				}

				break; // go to next waypoint, stop if rotating
			}

			// nothing detected, shut down sensing
			if (state.exists(values.motiondetect))
				app.driverCallServer(PlayerCommands.motiondetectcancel, null);
			if (state.exists(values.objectdetect))
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

				NavigationLog.newItem(NavigationLog.ALERTSTATUS, navlogmsg);
			}

			if (rotate) {
				Util.delay(2000);
				SystemWatchdog.waitForCpu(8000); // lots of missed stop commands, cpu timeouts here
				double degperms = state.getDouble(values.odomturndpms.name());   // typically 0.0857;
				app.driverCallServer(PlayerCommands.move, ArduinoPrime.direction.left.name());
				Util.delay((long) (50.0 / degperms));
				app.driverCallServer(PlayerCommands.move, ArduinoPrime.direction.stop.name());

				long stopwaiting = System.currentTimeMillis()+750; // timeout if error
				while(!state.get(values.direction).equals(ArduinoPrime.direction.stop.name()) &&
						System.currentTimeMillis() < stopwaiting) { Util.delay(1); } // wait for stop
				if (!state.get(values.direction).equals(ArduinoPrime.direction.stop.name()))
					Util.log("error, missed turnstop within 750ms", this);

				Util.delay(4000); // 2000 if condition below enabled
				turns ++;
			}
		}

		// END RECORD
		
		if (record && recordlink != null) {

			String navlogmsg = "<a href='" + recordlink + "_video.flv' target='_blank'>Video</a>";
			if (!settings.getBoolean(ManualSettings.useflash))
				navlogmsg += "<br><a href='" + recordlink + "_audio.flv' target='_blank'>Audio</a>";
			String msg = "[Oculus Prime Video] ";
			msg += navlogmsg+", time: "+Util.getTime()+", at waypoint: " + wpname + ", route: " + name;

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
			NavigationLog.newItem(NavigationLog.VIDEOSTATUS, navlogmsg);
			app.record(Settings.FALSE); // stop recording
		}

		app.driverCallServer(PlayerCommands.publish, Application.streamstate.stop.name());
		if (camera) {
			app.driverCallServer(PlayerCommands.spotlight, "0");
			app.driverCallServer(PlayerCommands.cameracommand, ArduinoPrime.cameramove.horiz.name());
		}
		if (mic) app.driverCallServer(PlayerCommands.videosoundmode, previousvideosoundmode) ;
	}

	private boolean turnLightOnIfDark(){

		if (state.getInteger(values.spotlightbrightness) == 100) return false; // already on

		state.delete(values.lightlevel);
		app.driverCallServer(PlayerCommands.getlightlevel, null);
		long timeout = System.currentTimeMillis() + 5000;
		while (!state.exists(values.lightlevel) && System.currentTimeMillis() < timeout) {
			Util.delay(10);
		}

		if (state.exists(values.lightlevel)) {
			if (state.getInteger(values.lightlevel) < 25) {
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
		if (id.equals(state.get(values.navigationrouteid))) cancelAllRoutes();
	}

	public void cancelAllRoutes() {
		state.delete(values.navigationroute); // this eventually stops currently running route
		goalCancel();
		state.delete(values.nextroutetime);

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
		if (!state.get(values.navsystemstatus).equals(Ros.navsystemstate.mapping.name())) {
			app.message("unable to save map, mapping not running", null, null);
			return;
		}
		new Thread(new Runnable() { public void run() {
			if (Ros.saveMap())  app.message("map saved to "+Ros.getMapFilePath(), null, null);
		}  }).start();
	}
}
