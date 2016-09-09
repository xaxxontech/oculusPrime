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
	String rname; //state.get(values.navigationroute);	
	long start;// = System.currentTimeMillis();
	int estsec; //estimatedtime;
	int i = 0;
	boolean docked = false;
	
	WatchOverdue(){
    	rname = state.get(values.navigationroute);	
    	start = System.currentTimeMillis();
    	// estsec = estimatedtime;
		docked = false;
		i = 0;
		watch();
	}
	
    public void watch(){
   
    	if(rname == null) return;
    	if(estsec == 0){
    		 Util.log("skipped, no estimate available: " + state.get(values.navigationroute), this);	
    		 return;
    	}
    	
		state.addObserver(this);
		Util.log("waiting: [" + state.get(values.navigationroute) + "] seconds: "+estsec, this);
		
		// Util.delay(estsec*500); // TODO: make a setting? in seconds 
		
		while(System.currentTimeMillis() < (start + (estsec*1000))){
	
			Util.delay(9000);
			Util.log("waiting... "+i++, this);
/*
			if(failed){
	    		Util.log("watching (exit, failed): " +rname + " seconds: "+estsec, this);
				return;
			}
		
			if(aborted){
	    		Util.log("watching (exit, aborted): " + rname + " seconds: "+estsec, this);
				return;
			}	
*/	
			if(docked){
	    		Util.log("watching (exit, docked): " +rname + " seconds: "+estsec, this);
				return;
			}
   		}
		
		Util.log("watching (exited): [" + state.get(values.navigationroute) + "] seconds: "+estsec, this);
    }

	@Override
	public void updated(String key) {
		
		if(key.equals(values.dockstatus.name())){
			if(state.equals(values.dockstatus, AutoDock.DOCKED)){
				Util.log("watching (exit, docked): " + rname + " seconds: "+estsec, this);
				 docked = true;
			}
		}
			
		if(key.equals(values.roswaypoint.name())){
						
			// if( ! state.exists(key)) aborted = true;
			
			Util.log(" ---*-*--- sate change:" + key + " = " + state.get(key), this);

		}
	}
}