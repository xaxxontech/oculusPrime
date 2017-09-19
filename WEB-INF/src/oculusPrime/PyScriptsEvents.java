package oculusPrime;

import java.io.File;

import oculusPrime.State.values;

public class PyScriptsEvents implements Observer {
	
	static State state = State.getReference();
	static PyScriptsEvents singleton = new PyScriptsEvents();
	private PyScriptsEvents(){ state.addObserver(this);	}
	static PyScriptsEvents getRefrence(){ return singleton; }
	
	@Override
	public void updated(String key) {	
		
		// don't do anything until system settled 
		if(State.getReference().getUpTime() < Util.FIVE_MINUTES) {
			// Util.log("launching python docking scripts, skipping: " + key);
			return;
		}
		
		if(key.equals(values.dockstatus.name())){	
			if(state.equals(values.dockstatus, AutoDock.DOCKED)){
				new Thread(new Runnable() { public void run() {
					
					File[] scripts = PyScripts.getScriptFiles("docked");
//					Util.log("launching python docking scripts: " + scripts.length);

					// TODO: wait for cam off, cam tilt horz or do this in the py script? 
					Util.delay(Util.ONE_MINUTE); 
					
					for(int i = 0 ; i < scripts.length ; i++){
//						Util.log("py launch: " + scripts[i].getName());
						Util.systemCall("python telnet_scripts/" + scripts[i].getName());	
					}
					
				}}).start();
			}
		}
	}
}