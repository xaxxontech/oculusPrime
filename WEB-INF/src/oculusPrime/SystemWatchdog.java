package oculusPrime;

import oculusPrime.AutoDock.autodockmodes;
import oculusPrime.State.values;
import oculusPrime.commport.ArduinoPower;
import oculusPrime.commport.ArduinoPrime;
import oculusPrime.commport.PowerLogger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Timer;
import java.util.TimerTask;

// TODO: more responsive to be a state observer and use timer as a watchdog
public class SystemWatchdog {
	
	private final Settings settings = Settings.getReference();
	protected Application application = null;
	
	private static final long DELAY = 10000; // 10 sec 
	public static final long AUTODOCKTIMEOUT= 360000; // 6 min
	private static final long ABANDONDEDLOGIN= 30*Util.ONE_MINUTE; 
	public static final String NOFORWARD = "noforward";

	// stale system reboot frequency 
	private static final long STALE = Util.ONE_DAY * 2; 
	
	private State state = State.getReference();
	
	public String lastpowererrornotify = null; // this gets set to null on client login 
	public boolean powererrorwarningonly = true;
	public boolean redocking = false;
	private boolean lowbattredock = false;
	
	SystemWatchdog(Application a){ 
		application = a;
		Timer timer = new Timer();
		timer.scheduleAtFixedRate(new Task(), DELAY, DELAY);
	}
	
	// top -bn 2 -d 0.1 | grep '^%Cpu' | tail -n 1 | awk '{print $2+$4+$6}'
	// http://askubuntu.com/questions/274349/getting-cpu-usage-realtime
	private void getCPU(){
		try {

			String[] cmd = { "/bin/sh", "-c", "top -bn 2 -d 0.01 | grep '^%Cpu' | tail -n 1 | awk \'{print $2+$4+$6}\'" };
			Process proc = Runtime.getRuntime().exec(cmd);
			BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			String line = procReader.readLine();
			// Util.log("cpu : " + Util.formatFloat(line, 0));
			state.set(values.cpu, Util.formatFloat(line, 0)); 
		
		} catch (Exception e) {
			Util.debug("getCPU(): " + e.getMessage(), this);
		}
	}
	
	private class Task extends TimerTask {
		public void run() {
			
			getCPU(); // TODO: build up functionality 
			if(state.getDouble(values.cpu.name()) > 70) {
				Util.log("cpu too high?? " + state.get(values.cpu), this);
				// settings.writeSettings(ManualSettings.debugenabled, "false");
			}

			// safety: check for force_undock command from battery firmware
			if (state.getBoolean(State.values.forceundock) && state.get(State.values.dockstatus).equals(AutoDock.DOCKED)) {
				Util.log("System WatchDog, force undock", this);
				PowerLogger.append("System WatchDog, force undock", this);
				forceundock();
			}
			
			// notify clients of power errors
			if (state.exists(State.values.powererror.toString())) {
				if (lastpowererrornotify == null) notifyPowerError();
				else if ( ! lastpowererrornotify.equals(state.get(State.values.powererror))) notifyPowerError();
			}
			
			// regular reboot if set 
			if (System.currentTimeMillis() - state.getLong(values.linuxboot) > STALE
					&& !state.exists(State.values.driver.toString()) &&
					!state.exists(State.values.powererror.toString()) && // why..? 
					state.get(State.values.dockstatus).equals(AutoDock.DOCKED) &&
					state.getInteger(State.values.telnetusers) == 0 &&
					(settings.getBoolean(GUISettings.reboot))){
				
				// String boot = new Date(state.getLong(State.values.javastartup.name())).toString();				
				Util.log("regular reboot", this);
				application.driverCallServer(PlayerCommands.reboot, null);
			}
			
			// deal with abandoned logins, driver still connected
			if (state.exists(State.values.driver.toString()) && 
					System.currentTimeMillis() - state.getLong(State.values.lastusercommand) > ABANDONDEDLOGIN ) {

				application.driverCallServer(PlayerCommands.disconnectotherconnections, null);
				application.driverCallServer(PlayerCommands.driverexit, null);
				if (state.get(State.values.dockstatus).equals(AutoDock.UNDOCKED) && 
						settings.getBoolean(GUISettings.redock)) {
					Util.log("abandoned logins, driver still connected, attempt redock", this);
					PowerLogger.append("abandoned logins, driver still connected, attempt redock", this);
					redock(NOFORWARD);
				}
			}
			
			// deal with abandonded, undocked, low battery, not redocking, not already attempted redock
			if (!state.exists(State.values.driver.toString()) && 
					System.currentTimeMillis() - state.getLong(State.values.lastusercommand) > ABANDONDEDLOGIN && 
					redocking == false && lowbattredock == false &&
					Integer.parseInt(state.get(State.values.batterylife).replaceAll("[^0-9]", "")) <= 10 &&
	//				state.getInteger(State.values.batterylife) <= 10 && // FAIL! (returns -1, error)
					state.get(State.values.dockstatus).equals(AutoDock.UNDOCKED) && 
					settings.getBoolean(GUISettings.redock)
					){
				lowbattredock = true;
				Util.log("abandonded, undocked, low battery, not redocking", this);
				PowerLogger.append("abandonded, undocked, low battery, not redocking", this);
				redock(null);
			}
			else  lowbattredock = false; 

		}
	}
	
	private void notifyPowerError() {
		PowerLogger.append("notifyPowerError()", this);
		lastpowererrornotify = state.get(State.values.powererror);
		boolean warningonly = true;
		String longerror = "";
		boolean commlost = false;
		String code[] = lastpowererrornotify.split(",");
		for (int i=0; i < code.length; i++) {
			int c = Integer.parseInt(code[i]);
			if (c > ArduinoPower.WARNING_ONLY_BELOW ) { 
				warningonly = false;
				longerror += "<span style='color: red'>";
			}
			if (c != 0) longerror += ArduinoPower.pwrerr.get(c).replaceFirst("ERROR_", "") + "<br>";
			if (!warningonly) longerror += "</span>";
			if (c == ArduinoPower.COMM_LOST) commlost = true;
		}
		
		if (state.exists(State.values.driver.toString())) {
			String msg = "";
			if (warningonly && !commlost) msg += "POWER WARNING<br>History:<br><br>";
			else msg += "POWER SYSTEM ERROR<br>History:<br><br>";
			
			msg += longerror + "<br>";
			
			if (warningonly && !commlost) msg += "OK to clear warnings?<br><br>";
			else if (warningonly && commlost) msg += "Try: restart application, reboot, check USB cable<br><br>"; // commlost
			else msg += "Please UNPLUG BATTERY and contact technical support<br><br>";
		
			msg += "<a href='javascript: acknowledgeerror(&quot;true&quot;);'>";
		    msg += "<span class='cancelbox'>&#x2714;</span> OK</a> &nbsp; &nbsp; ";

			if (warningonly) { powererrorwarningonly = true;
			    msg += "<a href='javascript: acknowledgeerror(&quot;cancel&quot;);'>";
			    msg += "<span class='cancelbox'><b>X</b></span> IGNORE</a><br>";
			}
			else powererrorwarningonly = false;
			
			application.sendplayerfunction("acknowledgeerror", msg);
		}
		else if (!warningonly) callForHelp("Oculus Prime POWER ERROR","Please UNPLUG BATTERY and contact technical support");
		else if (warningonly && commlost) callForHelp("Oculus Prime POWER ERROR","Power PCB Communication Lost");
	}
	
	public void redock(String str) {
		if (redocking) return;
		
		if (str == null) str = "";
		final String option = str;
		new Thread(new Runnable() { public void run() {
			redocking = true;
			long start;
			String subject = "Oculus Prime Unable to Dock";
			String body = "Un-docked, battery draining";
			
			// warn
//			application.driverCallServer(PlayerCommands.speech, "warning, moving, in, 5, seconds");
			application.driverCallServer(PlayerCommands.strobeflash, "on 500 50");
			Util.delay(1000); // including 500 delay 
			application.driverCallServer(PlayerCommands.strobeflash, "on 500 50");
			Util.delay(1000); // including 500 delay 
			application.driverCallServer(PlayerCommands.strobeflash, "on 500 50");
			Util.delay(6000); // allow reaction

			if (!redocking) return;

			// camera on
			application.driverCallServer(PlayerCommands.streamsettingsset, Application.camquality.high.toString());
			application.driverCallServer(PlayerCommands.publish, Application.streamstate.camera.toString());
			// go forward momentarily
			application.driverCallServer(PlayerCommands.speed, ArduinoPrime.speeds.med.toString());
			state.set(State.values.motionenabled, true);
			state.set(State.values.controlsinverted, false);

			if (!option.equals(NOFORWARD)) {
				application.driverCallServer(PlayerCommands.move, ArduinoPrime.direction.forward.toString());
				Util.delay(800); 
				application.driverCallServer(PlayerCommands.move, ArduinoPrime.direction.stop.toString());
			}

			if (!redocking) return;

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

				if (!redocking) return;

				if (rot == 32) { // failure give up
					callForHelp(subject, body);
					application.driverCallServer(PlayerCommands.publish, Application.streamstate.stop.toString());
					application.driverCallServer(PlayerCommands.floodlight, "0");
					redocking = false;
					return;
				}
				
				if (rot == 16) { 
					res = AutoDock.HIGHRES;
					application.driverCallServer(PlayerCommands.floodlight, Integer.toString(AutoDock.FLHIGH));
				}
				
				application.driverCallServer(PlayerCommands.dockgrab, res);
				Util.delay(10); // thread safe
				start = System.currentTimeMillis();
				while (!state.exists(State.values.dockfound.toString()) && System.currentTimeMillis() - start < 10000) { Util.delay(10); } // wait

				if (state.getBoolean(State.values.dockfound)) break; // great, onwards
				else { // rotate a bit
					application.driverCallServer(PlayerCommands.left, "25");
					Util.delay(10); // thread safe
					start = System.currentTimeMillis();
					while(!state.get(State.values.direction).equals(ArduinoPrime.direction.stop.toString())
							&& System.currentTimeMillis() - start < 5000) {  Util.delay(10); } // wait
					Util.delay(ArduinoPrime.TURNING_STOP_DELAY);
				}
				rot ++;
			}

			if (!redocking) return;

			application.driverCallServer(PlayerCommands.autodock, autodockmodes.go.toString()); // attempt dock
			// wait while autodocking does its thing 
			start = System.currentTimeMillis();
			while (state.getBoolean(State.values.autodocking) && System.currentTimeMillis() - start < AUTODOCKTIMEOUT)  
				Util.delay(100);

			if (!redocking) return;

			application.driverCallServer(PlayerCommands.publish, Application.streamstate.stop.toString());
			application.driverCallServer(PlayerCommands.floodlight, "0");

			if (!state.get(State.values.dockstatus).equals(AutoDock.DOCKED)) {
				if (!state.exists(State.values.driver.toString()))  callForHelp(subject, body);
			}

			redocking = false;
		}  }).start();

	}
	
	private void callForHelp(String subject, String body) {
		application.driverCallServer(PlayerCommands.messageclients, body);
		Util.log("callForHelp() " + subject + " " + body, this);
		PowerLogger.append("callForHelp() " + subject + " " + body, this);

		if (!settings.getBoolean(ManualSettings.alertsenabled)) return;

		body += "\nhttp://"+state.get(State.values.externaladdress)+":"+
				settings.readRed5Setting("http.port")+"/oculusPrime/";
		String emailto = settings.readSetting(ManualSettings.email_to_address);
		if (!emailto.equals(Settings.DISABLED))
			application.driverCallServer(PlayerCommands.email, emailto+" ["+subject+"] "+body);
		application.driverCallServer(PlayerCommands.rssadd, "["+subject+"] "+body );
	}
	
	private void forceundock() {
		application.driverCallServer(PlayerCommands.messageclients, "Power ERROR, Forced Un-Dock");
		// go forward momentarily
		application.driverCallServer(PlayerCommands.speed, ArduinoPrime.speeds.med.toString());
		state.set(State.values.motionenabled, true);
		state.set(State.values.controlsinverted, false); 
		application.driverCallServer(PlayerCommands.move, ArduinoPrime.direction.forward.toString());
		Util.delay(800);
		application.driverCallServer(PlayerCommands.move, ArduinoPrime.direction.stop.toString());
		 
//		String subject = "Oculus Prime Power ERROR, Forced Un-Dock";
//		String body = "Oculus Prime Power ERROR, Forced Un-Dock";
//		callForHelp(subject, body);
	}

}
