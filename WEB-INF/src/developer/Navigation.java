package developer;

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
//	private static final int linearspeed = 150;
//	private static final long mspermeter = 3200; // calibration, automate? (do in java, faster)
	private static final String DOCK = "dock"; // waypoint name


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
//				app.driverCallServer(PlayerCommands.speed, Integer.toString(linearspeed));
//				app.driverCallServer(PlayerCommands.move, ArduinoPrime.direction.forward.toString());
//				Util.delay((long) (mspermeter*0.7));
//				app.driverCallServer(PlayerCommands.move, ArduinoPrime.direction.stop.toString());
				app.driverCallServer(PlayerCommands.forward, "0.7");
		
				Util.delay(1000);
			}
			
			if (!Ros.setWaypointAsGoal(str))
				app.driverCallServer(PlayerCommands.messageclients, "unable to set waypoint");

		
		}  }).start();
	}
	
	private boolean waitForNavSystem() { // blocking
		if (!state.exists(Ros.NAVIGATIONENABLED)) startNavigation();
		else if (!state.getBoolean(Ros.NAVIGATIONENABLED)) startNavigation();
		
		long start = System.currentTimeMillis();
		while (!state.getBoolean(Ros.NAVIGATIONENABLED) 
				&& System.currentTimeMillis() - start < Util.ONE_MINUTE*2) { Util.delay(50);  } // wait
		
		if (!state.getBoolean(Ros.NAVIGATIONENABLED)) {
			app.driverCallServer(PlayerCommands.messageclients, "navigation start failure");
			return false;
		}
		
		return true;
	}
	
	public void startNavigation() {
		state.set(Ros.NAVIGATIONENABLED, false); // set true by ROS node when ready
//		app.driverCallServer(PlayerCommands.streamsettingsset, "med");  // do in ros python node instead
		
		new Thread(new Runnable() { public void run() {
			app.driverCallServer(PlayerCommands.messageclients, "starting navigation, please wait");
			Util.systemCall("pkill roslaunch");
			Util.delay(3000);
			Ros.launch(Ros.REMOTE_NAV);
		
		}  }).start();

	}
	
	public void dock() {
		if (state.getBoolean(State.values.autodocking) || state.get(State.values.dockstatus).equals(AutoDock.DOCKED)) {
			app.driverCallServer(PlayerCommands.messageclients, "command dropped");
			return;
		}
		else if (!state.exists(Ros.NAVIGATIONENABLED)) {
				app.driverCallServer(PlayerCommands.messageclients, "navigation not running");
			return;
		}
		else if (!state.getBoolean(Ros.NAVIGATIONENABLED)) {
			app.driverCallServer(PlayerCommands.messageclients, "navigation not running");
			return;
		}
		
		Ros.setWaypointAsGoal(DOCK);
		state.set(Ros.ROSCURRENTGOAL, "pending");
		
		new Thread(new Runnable() { public void run() {
			
			long start = System.currentTimeMillis();
			while (state.exists(Ros.ROSCURRENTGOAL) 
					&& System.currentTimeMillis() - start < Util.TEN_MINUTES) { Util.delay(100);  } // wait
		
			if (state.exists(Ros.ROSCURRENTGOAL) || !state.exists(Ros.ROSGOALSTATUS)) { // this will probably never happen
				app.driverCallServer(PlayerCommands.messageclients, "failed to reach dock");
				return;
			}
			
			if (!state.get(Ros.ROSGOALSTATUS).equals(Ros.ROSGOALSTATUS_SUCCEEDED)) {
				app.driverCallServer(PlayerCommands.messageclients, "failed to reach dock");
				return;
			}
			
			// success, shut down nav
			Util.systemCall("pkill roslaunch");
			Util.delay(3000);
			
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
		state.set(Ros.ROSGOALCANCEL, true);
	}
}
