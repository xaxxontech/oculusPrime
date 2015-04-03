package developer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

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
	public static final long NAVSTARTTIMEOUT = Util.ONE_MINUTE*2;

   /** Constructor */
	public Navigation(Application a){ 
		app = a;
	}	
	
	public void gotoWaypoint(final String str) {
		if (state.getBoolean(State.values.autodocking)) {
			app.driverCallServer(PlayerCommands.messageclients, "command dropped, autodocking");
			return;
		}
		
		new Thread(new Runnable() { public void run() {

			if (!waitForNavSystem()) {
				app.driverCallServer(PlayerCommands.messageclients, "Unable to start navigation");
				return;
			}
			
			// undock if necessary
			if (state.get(State.values.dockstatus).equals(AutoDock.DOCKED)) {
				state.set(State.values.motionenabled, true);
				app.driverCallServer(PlayerCommands.forward, "0.7");
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
		
		if (state.get(State.values.dockstatus).equals(AutoDock.DOCKED))
			state.set(State.values.rosinitialpose, "0_0_0");
		
		return true;
	}
	
	public void startNavigation() {
		state.set(State.values.navigationenabled, false); // set true by ROS node when ready
//		app.driverCallServer(PlayerCommands.streamsettingsset, "med");  // do in ros python node instead
		
		new Thread(new Runnable() { public void run() {
			app.driverCallServer(PlayerCommands.messageclients, "starting navigation, please wait");
			Util.systemCall("pkill roslaunch");
			Util.delay(3000);
			Ros.launch(Ros.REMOTE_NAV);

			// wait
			long start = System.currentTimeMillis();
			while (!state.getBoolean(State.values.navigationenabled) 
					&& System.currentTimeMillis() - start < NAVSTARTTIMEOUT) { Util.delay(50);  } // wait

			if (state.getBoolean(State.values.navigationenabled)) return; // success
			
			// try again if needed, just once
			Util.systemCall("pkill roslaunch");
			Util.delay(3000);
			Ros.launch(Ros.REMOTE_NAV);
			Util.log("navigation start attempt #2",this);
				
		}  }).start();

	}
	
	public void dock() {
		if (state.getBoolean(State.values.autodocking) || state.get(State.values.dockstatus).equals(AutoDock.DOCKED)) {
			app.driverCallServer(PlayerCommands.messageclients, "command dropped");
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
					&& System.currentTimeMillis() - start < Util.TEN_MINUTES) { Util.delay(100);  } // wait
		
			if (state.exists(State.values.roscurrentgoal) || !state.exists(State.values.rosgoalstatus)) { // this will probably never happen
				app.driverCallServer(PlayerCommands.messageclients, "failed to reach dock");
				return;
			}
			
			if (!state.get(State.values.rosgoalstatus).equals(Ros.ROSGOALSTATUS_SUCCEEDED)) {
				app.driverCallServer(PlayerCommands.messageclients, "failed to reach dock");
				return;
			}
			
			// success, should be pointing at dock, shut down nav
			Util.systemCall("pkill roslaunch");
			Util.delay(5000);
			
			// dock
			app.driverCallServer(PlayerCommands.streamsettingsset, "high");
			app.driverCallServer(PlayerCommands.publish, Application.streamstate.camera.toString());
			app.driverCallServer(PlayerCommands.spotlight, "0");
			app.driverCallServer(PlayerCommands.cameracommand, ArduinoPrime.cameramove.reverse.toString());
			Util.delay(25);
			app.driverCallServer(PlayerCommands.floodlight, Integer.toString(AutoDock.FLHIGH));
			Util.delay(25);
			app.driverCallServer(PlayerCommands.left, "180");
			Util.delay(app.comport.fullrotationdelay/2 + 2000);
			
			// make sure dock in view before calling autodock go
			// dock detect, rotate if necessary
			int rot = 0;
			while (true) {
				
				app.driverCallServer(PlayerCommands.dockgrab, AutoDock.HIGHRES);
				Util.delay(10); // thread safe
				while (!state.exists(State.values.dockfound.toString()) ) {} // wait

				if (state.getBoolean(State.values.dockfound)) break; // great, onwards
				else { // rotate a bit
					app.driverCallServer(PlayerCommands.left, "25");
					Util.delay(10); // thread safe
					while(!state.get(State.values.direction).equals(ArduinoPrime.direction.stop.toString()) ) {} // wait
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
				
			if (state.get(State.values.dockstatus).equals(AutoDock.DOCKED)) 
				Util.delay(2000);
				app.driverCallServer(PlayerCommands.publish, Application.streamstate.stop.toString());
			
			
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
			Util.debug("no navroutes file found");
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return result;
	}
	
	public void saveRoute(String str) {
		try {
			FileWriter fw = new FileWriter(navroutesfile);
			fw.append(str);
			fw.close();
			app.driverCallServer(PlayerCommands.messageclients, "routes saved");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void runRoute(final String name) {
		// build error checking into this (ignore duplicate waypoints, etc)
		// assume goto dock at the end, whether or not dock is a waypoint
		
		if (state.getBoolean(State.values.autodocking)) {
			app.driverCallServer(PlayerCommands.messageclients, "command dropped, autodocking");
			return;
		}
		
		// check for route name
		Document document = Util.loadXMLFromString(routesLoad());
		NodeList routes = document.getDocumentElement().getChildNodes();
		Element route = null;
		for (int i = 0; i< routes.getLength(); i++) {
    		String rname = ((Element) routes.item(i)).getElementsByTagName("rname").item(0).getTextContent();  
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
		app.driverCallServer(PlayerCommands.messageclients, "activating route: "+name);
		
		new Thread(new Runnable() { public void run() {

			if (!waitForNavSystem()) {
				app.driverCallServer(PlayerCommands.messageclients, "Unable to start navigation");
				state.delete(State.values.navigationroute);
				return;
			}
			
			// undock if necessary
			if (state.get(State.values.dockstatus).equals(AutoDock.DOCKED)) {
				state.set(State.values.motionenabled, true);
				app.driverCallServer(PlayerCommands.forward, "0.7");
				Util.delay(1000);
			}
			
	    	// go to each waypoint
	    	NodeList waypoints = navroute.getElementsByTagName("waypoint");	    	
	    	int wpnum = 0;
	    	while (wpnum < waypoints.getLength()) {
		    	if (!state.exists(State.values.navigationroute)) return;
		    	if (!state.get(State.values.navigationroute).equals(name)) return;

	    		String wpname = 
	    				((Element) waypoints.item(wpnum)).getElementsByTagName("wpname").item(0).getTextContent();

				wpnum ++;
	    		
	    		if (wpname.equals(DOCK))  break;
	    		
	    		if (!Ros.setWaypointAsGoal(wpname)) { // can't set waypoint, try the next one
					app.driverCallServer(PlayerCommands.messageclients, "route "+name+" unable to set waypoint");
					continue;
	    		}

	    		state.set(State.values.roscurrentgoal, "pending");
	    		
	    		// wait to reach wayypoint
				long start = System.currentTimeMillis();
				while (state.exists(State.values.roscurrentgoal) 
						&& System.currentTimeMillis() - start < WAYPOINTTIMEOUT) { Util.delay(100);  } 
			
				if (state.exists(State.values.roscurrentgoal) || !state.exists(State.values.rosgoalstatus)) { // this will probably never happen
					app.driverCallServer(PlayerCommands.messageclients, "route "+name+" failed to reach waypoint");
					continue;
				}
				
				if (!state.get(State.values.rosgoalstatus).equals(Ros.ROSGOALSTATUS_SUCCEEDED)) {
					app.driverCallServer(PlayerCommands.messageclients, "route "+name+" failed to reach waypoint");
					continue; 
				}
	    		
				Util.delay(1000);
				
	    	}
	    	
	    	if (!state.exists(State.values.navigationroute)) return;
	    	if (!state.get(State.values.navigationroute).equals(name)) return;
	    	
			dock();
			
			// wait while autodocking does its thing 
			long start = System.currentTimeMillis();
			while (System.currentTimeMillis() - start < SystemWatchdog.AUTODOCKTIMEOUT + WAYPOINTTIMEOUT) {
				if (!state.exists(State.values.navigationroute)) return;
				if (!state.get(State.values.navigationroute).equals(name)) return;
				if (state.get(State.values.dockstatus).equals(AutoDock.DOCKED)) break; // success
				Util.delay(100); 
			}
				
			if (!state.get(State.values.dockstatus).equals(AutoDock.DOCKED)) {
				state.delete(State.values.navigationroute);
				return; 
			}
			
			// delay to next route
			String min = navroute.getElementsByTagName("minbetween").item(0).getTextContent();
	    	long timebetween = Long.parseLong(min) * 1000 * 60;
			app.driverCallServer(PlayerCommands.messageclients, min+" min until next route: "+name);
	    	start = System.currentTimeMillis();
			while (System.currentTimeMillis() - start < timebetween) {
				if (!state.exists(State.values.navigationroute)) return;
				if (!state.get(State.values.navigationroute).equals(name)) return;
				Util.delay(100); 
			}
			
			runRoute(name); // keep going forever
		
		}  }).start();
		
	}
}
