package oculus;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import oculus.Settings;
import oculus.State;
import oculus.Util;

public class SystemWatchdog {
	
	private final Settings settings = Settings.getReference();
	private final boolean reboot = settings.getBoolean(GUISettings.reboot);		
	
	// check every ten minutes
	public static final long DELAY = Util.TEN_MINUTES;

	// when is the system stale and need reboot
	public static final long STALE = Util.ONE_DAY * 2; 
	
	// shared state variables
	private State state = State.getReference();
	
    /** Constructor */
	public SystemWatchdog(){ 
		if (reboot){
			Timer timer = new Timer();
			timer.scheduleAtFixedRate(new Task(), Util.TEN_MINUTES, DELAY);
		}	
	}
	
	private class Task extends TimerTask {
		public void run() {
			if ((state.getUpTime() > STALE) && !state.getBoolean(State.values.driver)){ 
				
				String boot = new Date(state.getLong(State.values.boottime.name())).toString();				
				Util.log("rebooting, last boot was: " + boot, this);
				
				// reboot 
				if (Settings.os.equals("windows")) {
					Util.systemCall("shutdown -r -f -t 01");	
				} else {
					Util.systemCall("shutdown -r now");
				}
			}
		}
	}
}
