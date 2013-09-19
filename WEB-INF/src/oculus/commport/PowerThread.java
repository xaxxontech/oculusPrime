package oculus.commport;

import java.util.Timer;
import java.util.TimerTask;
import oculus.Util;

public class PowerThread { 
	
	//  implements Observer {
	//  private oculus.State state = oculus.State.getReference();
	
	private static Timer watchdogTimer = null; 
	private ArduinoPort serialPort = null;

	PowerThread(ArduinoPort port) {
		serialPort = port;
		watchdogTimer = new java.util.Timer(); 
		watchdogTimer.scheduleAtFixedRate(new watchdogTask(), ArduinoPort.SETUP, ArduinoPort.WATCHDOG_DELAY);
		
		// state.addObserver(this);
	}

	private class watchdogTask extends TimerTask {
		@Override
		public void run(){
			
			serialPort.updateBatteryLevel(); 
			
			/** check for time outs  */
			if (serialPort.getReadDelta() > ArduinoPort.DEAD_TIME_OUT) {
						
				Util.log("arduino watchdog time out, may be no hardware attached", this);
				
				try { // hopeless.. die 
					if(watchdogTimer != null) watchdogTimer.cancel();
				} catch (Exception e) {
					Util.log(e.getLocalizedMessage(), this);
				}
			}
		}
	}

	/** respond to dock state changes 
	@Override
	public void updated(final String key) {
		
		if(key.equals(State.values.dockstatus.name())){ 
		
			Util.log("___ key: " + key, this);
			
			if(state.get(key).equals(AutoDock.DOCKING)){
			//handled elsewhere
//				serialPort.setSpotLightBrightness(0);
//				serialPort.floodLightOff();
				
			}
			
			if(state.get(key).equals(AutoDock.DOCKED)){
				//handled elsewhere
//				serialPort.floodLightOff();
//				serialPort.setSpotLightBrightness(0);
				
				// poll at different rate ??
				
				//watchdogTimer = new java.util.Timer();
				//watchdogTimer.scheduleAtFixedRate(new watchdogTask(), ArduinoPort.SETUP, ArduinoPort.WATCHDOG_DELAY);
			}
		}
	}*/
}

