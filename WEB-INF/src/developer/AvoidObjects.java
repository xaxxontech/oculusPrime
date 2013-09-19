package developer;

import oculus.Application;
import oculus.Observer;
import oculus.State;
import oculus.Util;

public class AvoidObjects implements Observer {

	private static final Integer TOO_CLOSE = 400;
	private static final Integer THRESHOLD = 5;
	private State state = State.getReference();
	private Application app = null;
	private int center = 0;
	
	/** */
	public AvoidObjects(Application a){
		Util.log("..starting up", this);
		state.addObserver(this);
		app = a;
	}
	
	

	/* private static AvoidObjects singleton;
	public static AvoidObjects getReference() {
		if(singleton==null) singleton = new AvoidObjects();
		return singleton;
	} */
	
	@Override
	public void updated(String key) {
		
	//	Util.log("state changed: " + key + " value: " + state.get(key), this);
		
		if(key.equals(State.values.centerpoint.name())){
			
			Integer distance = state.getInteger(key);
			
			if(distance==0) return;
			
			if(distance > TOO_CLOSE){
				
				// Util.log("point now: " + distance + " delta: " + Math.abs(center - distance), this);
					
				// threshold 
				if(Math.abs(center - distance) > THRESHOLD){
					
					String dir;
					if(center>distance) dir = " <b>closer</b>";
					else dir = " backing up";
					app.message("center point: " + distance + " delta: " + Math.abs(center - distance) + dir, null, null); 
					
					//move("right");
				
				
					center = distance;
				}
				
			} else {
				
				app.message("center point: TOO CLOSE", null, null); 
				
			}
			
			
			
		}
		
	}
	
	
	
	

}
