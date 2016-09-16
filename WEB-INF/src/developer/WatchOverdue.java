package developer;

import oculusPrime.AutoDock;
import oculusPrime.Observer;
import oculusPrime.Settings;
import oculusPrime.State;
import oculusPrime.Util;
import oculusPrime.State.values;

public class WatchOverdue implements Observer {

	// private static Settings settings = Settings.getReference();

	private static State state = State.getReference();
	String rname = state.get(values.navigationroute);	
	long start = System.currentTimeMillis();
	int estsec;
	private static int i = 0;
	private int id = 0;

	boolean docked = false;
	
	WatchOverdue(){
    //	state.addObserver(this);

    	id = i++;
	}
	
    public void watch(){
    	
    	state.addObserver(this);
    	
    	if(rname == null) return;
    	if(estsec == 0){
    		 Util.log("skipped, no estimate available: " + state.get(values.navigationroute), this);	
    		 return;
    	}
    	
    	rname = state.get(values.navigationroute);	
    	start = System.currentTimeMillis();
    	estsec = NavigationUtilities.getEstimatedSeconds();
		
		Util.fine(id + " watch: [" + state.get(values.navigationroute) + "] seconds: "+estsec);
		
		int cnt = 0;
		while(System.currentTimeMillis() < (start + (estsec*5000))){
	
			Util.delay(5000);
			Util.fine("waiting... " + cnt++);
			
			if(Navigation.failed){
	    		Util.log("watching (exit, failed): " + rname + " seconds: "+estsec, this);
				break;
			}	
			
			if(docked){
	    		Util.log(id + " watching (exit, docked): " +rname + " seconds: "+estsec, this);
				break;
			}
   	
		}
		
		Util.log(id + " watching (exited): [" + rname + "] seconds: "+estsec, this);
		state.removeObserver(this);
    
    }

	@Override
	public void updated(String key) {
		
		if(key.equals(values.dockstatus.name())){
			if(state.equals(values.dockstatus, AutoDock.DOCKED)){
				Util.log(id + "updated watching (exit, docked): " + rname + " seconds: "+estsec, this);
				 docked = true;
			}
		}
			
		if(key.equals(values.roswaypoint.name())){
						
			// if( ! state.exists(key)) aborted = true;
			
			Util.log( id+ " --- sate change:" + key + " = " + state.get(key), this);

		}
	}
}