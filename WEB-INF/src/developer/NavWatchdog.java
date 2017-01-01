package developer;

import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import oculusPrime.Application;
import oculusPrime.AutoDock;
import oculusPrime.Observer;
import oculusPrime.PlayerCommands;
import oculusPrime.Settings;
import oculusPrime.State;
import oculusPrime.Util;
import oculusPrime.State.values;

public class NavWatchdog implements Observer {
	
	static final long DELAY = 10000; 
	
	static Settings settings = Settings.getReference();	
	static State state = State.getReference();
	Application application = null;
	static String pointslist;
	static String estimatedmeters;
	static	int estimatedseconds; 
	static long time;

	static int rotations = 0;
	static int routemillimeters;

	static public Vector<String> waypoints;
	
	
	public NavWatchdog(Application application){ 
		this.application = application; 
		new Timer().scheduleAtFixedRate(new Task(), DELAY, DELAY);
	//	new Timer().scheduleAtFixedRate(new SlowTask(), DELAY*2, DELAY*2);

		state.addObserver(this);
	}

	@Override
	public void updated(String key) {
		
		// only read from file on change 
		if(key.equals(values.navigationroute.name())){
			if(state.exists(values.navigationroute)){
				estimatedmeters = NavigationUtilities.getRouteDistanceEstimateString(state.get(values.navigationroute));
				estimatedseconds = NavigationUtilities.getRouteTimeEstimate(state.get(values.navigationroute));
				pointslist = NavigationUtilities.getWaypointsForRoute(state.get(values.navigationroute)).toString();
			} else {
				
				Util.log("------------ canceled, exit? ------- ", this);	

				pointslist = "none";	
				estimatedmeters = "0";
				estimatedseconds = 0;
			}
		}
		
		if(key.equals(values.dockstatus.name())){
			if(state.equals(values.dockstatus, AutoDock.DOCKED)){
				Util.log("------------ docked ------- ", this);	

				estimatedmeters = "0";
				estimatedseconds = 0;
				time = 0;
		/*		
		//		Util.delay(5000);
		//		state.block(values.stream, "stop", (int)Util.TWO_MINUTES);
				Util.delay(5000);
				application.playerCallServer(PlayerCommands.publish, "camera");
				Util.delay(5000);
				if(Navigation.turnLightOnIfDark()) Util.log("light was turned on because was dark", this);
				Util.delay(5000);
				application.playerCallServer(PlayerCommands.motiondetect, null);
			*
			*/	

				/*new Thread(new Runnable() { public void run(){
					app.driverCallServer(PlayerCommands.publish, "camera");
					Util.delay(4000); // TODO: BETTER WAY TO KNOW IF CAMERA IS ON?
					if(Navigation.turnLightOnIfDark()) Util.log("light was turned on because was dark", this);
				}}).start();*/
				
			}
		}

		if(key.equals(values.navsystemstatus.name())){
			if(state.equals(values.navsystemstatus, "starting")){
				
				Util.log("------------starting up------- ", this);	
				estimatedmeters = "0";
				estimatedseconds = 0;
				time = 0;
			//	application.playerCallServer(PlayerCommands.motiondetectcancel, null);

			//	application.driverCallServer(PlayerCommands.publish, "stop");
			//	application.driverCallServer(PlayerCommands.spotlight, "0");
			}
			
		}
	
		if(key.equals(values.rosgoalcancel.name())){
			if(state.exists(values.rosgoalcancel)){
				Util.log("------------------- "+ state.get(values.rosgoalcancel), this);	
			}
		}

		if(key.equals(values.distanceangle.name())){
			try {
				int mm = Integer.parseInt(state.get(values.distanceangle).split(" ")[0]);
				if(mm > 0) routemillimeters += mm;
			} catch (Exception e){}
		}
		
		if(key.equals(values.recoveryrotation.name())){
			if(state.getBoolean(values.recoveryrotation)) rotations++; 
			Util.log("rotations: " + rotations, this);
		}
		
		if(key.equals(values.motiondetect.name())){
			
			if(state.exists(values.motiondetect)){
				Util.log("updated() motion waiting..", this);
			} else {
				Util.log("updated() waypoint motion deleted..", this);
			}
	
			/*
			if(waypoints == null) return;
			
			Util.log("updated() waypoint = " + state.get(values.roswaypoint) + " " + waypoints, this);
			Util.log("updated() waypoint index = " + waypoints.indexOf(state.get(values.roswaypoint)), this);
			
			
//			if(waypoints.indexOf(state.get(values.roswaypoint)) == -1){
//				Util.log("updated() waypoint index = " + waypoints.indexOf(state.get(values.roswaypoint)), this);
//				failed = true;
//			}

			for(int i = 0 ; i < waypoints.size() ; i++){
				if(waypoints.get(i).equals(values.roswaypoint.name())){
					
					Util.log("...updated() waypoint last index = " + i, this);

				}
			}*/
		}	
		
		/*
		if(key.equals(values.navigationroute.name())){
			Util.log("updated() navigationroute = " + state.get(values.navigationroute), this);
			if( ! state.exists(values.navigationroute)){
				
				Util.log("updated() deleted navigationroute: failed or canceled?? ", this);
		
			}
		}
		*/
		
		if(key.equals(values.dockstatus.name())){
			if(state.equals(values.dockstatus, AutoDock.DOCKED)){
				
				Util.log("updated() dockstatus, docked reset...", this);	
				state.delete(values.routeoverdue);
				state.delete(values.recoveryrotation);			
				state.delete(values.waypointbusy);
				state.delete(values.rosgoalcancel);
			
				boolean routevisiting = false;	
				boolean failed = false;
				rotations = 0;

			//  never do these here 
			//	waypoints = null;
			//  never do these here! 
			//	routemillimeters = 0;  
			//	routestarttime = 0;
				
			}
		}
		
		/*
		if(key.equals(values.wallpower.name())){
			if(state.exists(values.wallpower)){
				if(state.equals(values.wallpower, "true")){

					Util.log("updated() dockstatus, wallpower reset...", this);	

				//	routemillimeters = 0;  
					routestarttime = System.currentTimeMillis();
				
				}
			}
		}*/
		
		if(key.equals(values.navigationroute.name())){
			if(state.exists(values.navigationroute)){
		

				waypoints = NavigationUtilities.getWaypointsForRoute(state.get(values.navigationroute));
				Util.log("updated(): waypoints: " + waypoints, this);

			}
		}
	}

	private class Task extends TimerTask {
		public void run(){
			if( ! state.exists(values.navigationroute)){
				time = 0; 
			}
			if(estimatedseconds > Util.ONE_MINUTE){
				time = ((System.currentTimeMillis() - Navigation.routestarttime)/1000);
				if(time > estimatedseconds +10000){
				
					// NavigationLog.newItem("Route: " + state.exists(values.navigationroute) + " over due: " + time);
					
					Util.log("run(): " + "Route: " + state.exists(values.navigationroute) + " over due: " + time, this);
					state.set(values.routeoverdue, "true");
					
				}
			}
		}
	}
	
	/*
	private class SlowTask extends TimerTask {
		public void run(){
				
			
			Util.log("run(): Slow fire.. ", this);

			
			
		}
	}
	*/
	
}
