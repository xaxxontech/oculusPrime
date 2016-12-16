package developer;

import java.util.Timer;
import java.util.TimerTask;

import oculusPrime.Application;
import oculusPrime.AutoDock;
import oculusPrime.Observer;
import oculusPrime.Settings;
import oculusPrime.State;
import oculusPrime.Util;
import oculusPrime.State.values;

public class NavWatchdog implements Observer {
	
	private static final long DELAY = 10000; 
	
	Settings settings = Settings.getReference();	
	State state = State.getReference();
	Application application = null;
	String pointslist;
	String estimatedmeters;
	int estimatedseconds; 
	long time;
	
	
	NavWatchdog(){ 
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
				pointslist = "none";	
				estimatedmeters = "0";
				estimatedseconds = 0;
			}
		}
		
		if(key.equals(values.dockstatus.name())){
			if(state.equals(values.dockstatus, AutoDock.DOCKED)){
				estimatedmeters = "0";
				estimatedseconds = 0;
				time = 0;
			}
		}

		/*
		if(key.equals(values.rosgoalcancel.name())){
			if(state.exists(values.rosgoalcancel)){
				Util.log("------------------- "+ state.get(values.rosgoalcancel), this);	
			}
		}
		*/
	}

	private class Task extends TimerTask {
		public void run(){
			if(estimatedseconds > 0){
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
