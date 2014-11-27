package oculusPrime;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import oculusPrime.Settings;
import oculusPrime.State;
import oculusPrime.Util;
import oculusPrime.commport.ArduinoPower;
import oculusPrime.commport.ArduinoPrime;

public class SystemWatchdog {
	
	private final Settings settings = Settings.getReference();
	protected Application application = null;
	
	// check every ten minutes
	private static final long DELAY = 10000;
	private static final long AUTODOCKTIMEOUT= 360000; // 6 min

	// when is the system stale and need reboot
	private static final long STALE = Util.ONE_DAY * 2; 
	
	// shared state variables
	private State state = State.getReference();
	
    /** Constructor */
	SystemWatchdog(Application a){ 
		application = a;
		Timer timer = new Timer();
		timer.scheduleAtFixedRate(new Task(), DELAY, DELAY);
	}
	
	private class Task extends TimerTask {
		public void run() {
			
			// check for redock command from battery firmware
			if (state.getBoolean(State.values.redock)) {
				state.set(State.values.redock, false);
				redock();
			}
			
			// saftey: check for force_undock command from battery firmware
			if (state.getBoolean(State.values.forceundock) && state.get(State.values.dockstatus).equals(AutoDock.DOCKED)) {
				forceundock();
			}
			
			// regular reboot if set 
			if ((state.getUpTime() > STALE) && !state.getBoolean(State.values.driver) && 
					!state.exists(State.values.powererror.toString()) &&
					(settings.getBoolean(GUISettings.reboot))){ 
				
				String boot = new Date(state.getLong(State.values.boottime.name())).toString();				
				Util.log("rebooting, last boot was: " + boot, this);
				Util.reboot();
			}

		}
	}
	
	private void redock() {
		new Thread(new Runnable() { public void run() {
			long start; 
			String subject = "Oculus Prime Unable to Dock";
			String body = "Un-docked, battery draining";
			
			// camera on
			application.driverCallServer(PlayerCommands.streamsettingsset, "high");
			application.driverCallServer(PlayerCommands.publish, Application.streamstate.camera.toString());
			// go forward momentarily
			application.driverCallServer(PlayerCommands.speed, ArduinoPrime.speeds.med.toString());
			state.set(State.values.motionenabled, true);
			state.set(State.values.controlsinverted, false); 
			application.driverCallServer(PlayerCommands.move, ArduinoPrime.direction.forward.toString());
			Util.delay(800); 
			application.driverCallServer(PlayerCommands.move, ArduinoPrime.direction.stop.toString());
			// reverse tilt
			application.driverCallServer(PlayerCommands.cameracommand, ArduinoPrime.cameramove.reverse.toString());
			// docklight on, spotlight off
			application.driverCallServer(PlayerCommands.floodlight, Integer.toString(AutoDock.FLLOW));
			application.driverCallServer(PlayerCommands.spotlight, "0");
			Util.delay(2000); // allow for motion stop and light to settle

			// rotate and dock detect
			int rot = 0;
			String res = "";
			while (true) {
				if (rot == 32) { // failure give up
					callForHelp(subject, body);
					application.driverCallServer(PlayerCommands.publish, Application.streamstate.stop.toString());
					application.driverCallServer(PlayerCommands.floodlight, "0");
					return;
				}
				
				if (rot == 16) { 
					res = "highres";
					application.driverCallServer(PlayerCommands.floodlight, Integer.toString(AutoDock.FLHIGH));
				}
				
				application.driverCallServer(PlayerCommands.dockgrab, res);
				while (!state.exists(State.values.dockfound.toString()) ) {} // wait

				if (state.getBoolean(State.values.dockfound)) break; // great, onwards
				else { // rotate a bit
					application.driverCallServer(PlayerCommands.left, "25");
					while(!state.get(State.values.direction).equals(ArduinoPrime.direction.stop.toString()) ) {} // wait
					Util.delay(ArduinoPrime.TURNING_STOP_DELAY);
				}
				rot ++;
			}

			application.driverCallServer(PlayerCommands.autodock, "go"); // attempt dock
			// wait while autodocking does its thing 
			start = System.currentTimeMillis();
			while (state.getBoolean(State.values.autodocking) && System.currentTimeMillis() - start < AUTODOCKTIMEOUT)  
				Util.delay(100); 
				
			if (!state.get(State.values.dockstatus).equals(AutoDock.DOCKED)) {

				callForHelp(subject, body);
				application.driverCallServer(PlayerCommands.publish, Application.streamstate.stop.toString());
				application.driverCallServer(PlayerCommands.floodlight, "0");
			}
		
		}  }).start();
	}
	
	private void callForHelp(String subject, String body) {
		application.driverCallServer(PlayerCommands.messageclients, body);
		String emailto = settings.readSetting(ManualSettings.email_to_address);
		if (!emailto.equals(Settings.DISABLED))
			application.driverCallServer(PlayerCommands.email, emailto+" ["+subject+"] "+body);
		application.driverCallServer(PlayerCommands.rssadd, "["+subject+"] "+body );
	}
	
	private void forceundock() {
		// go forward momentarily
		application.driverCallServer(PlayerCommands.speed, ArduinoPrime.speeds.med.toString());
		state.set(State.values.motionenabled, true);
		state.set(State.values.controlsinverted, false); 
		application.driverCallServer(PlayerCommands.move, ArduinoPrime.direction.forward.toString());
		Util.delay(800);
		application.driverCallServer(PlayerCommands.move, ArduinoPrime.direction.stop.toString());
		// 
		String subject = "Oculus Prime Power ERROR, Forced Un-Dock";
		String body = "Oculus Prime Power ERROR, Forced Un-Dock";
		callForHelp(subject, body);
	}

}
