package oculusPrime.commport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Timer;
import java.util.TimerTask;

import oculusPrime.Application;
import oculusPrime.AutoDock;
import oculusPrime.GUISettings;
import oculusPrime.ManualSettings;
import oculusPrime.Settings;
import oculusPrime.State;
import oculusPrime.Util;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;

/**
 *  Communicate with the motors and lights on one arduino and battery board 
 */
public class ArduinoPrime  implements SerialPortEventListener {

	// toggle to see bytes sent in log 
	public static final boolean DEBUGGING = true;
	
	public enum direction { stop, right, left, forward, backward };
	public enum cameramove { stop, up, down, horiz, upabit, downabit, frontstop, rearstop };
	public enum speeds { slow, med, fast }; // better motors, maybe add speeds? 

	public static final long DEAD_TIME_OUT = 30000;
	public static final long WATCHDOG_DELAY = 5000;	
	public static final long DOCKING_DELAY = 1000;
	public static final int SETUP = 2000;
	
	public static final byte STOP = 's';
	public static final byte FORWARD = 'f';
	public static final byte BACKWARD = 'b';
	public static final byte LEFT = 'l';
	public static final byte RIGHT = 'r';
	public static final byte ECHO = 'e';
	
	public static final byte FLOOD_LIGHT_LEVEL = 'o'; 
	public static final byte SPOT_LIGHT_LEVEL = 'p';
		
	public static final byte CAM = 'v';
	public static final byte FIND_HOME_TILT = 'm';
	public static final byte HOME_TILT_FRONT = 't';
	public static final byte HOME_TILT_REAR = 'z';
	public static final byte GET_PRODUCT = 'x';
	public static final byte GET_VERSION = 'y';
	public static final byte[] ECHO_ON = { 'e', '1' };
	public static final byte[] ECHO_OFF = { 'e', '0' };
		
	public static final int CAM_HORIZ = 19; // degrees (CAD measures 19)
	public static final int CAM_MAX = 219; // degrees (CAD measures 211)
	public static final int CAM_MIN = 0; // degrees
	public static final int CAM_NUDGE = 5; // degrees
	public static final long CAM_NUDGE_DELAY = 100; 
	public static final int CAM_EXTRA_FOR_CALIBRATE = 90; // degrees

	private static final int MAX_ATTEMPTS = 5; // how many tries before giving up looking
	
	// check if board has replied with correct firmware. 
//	private boolean verified = false;

	protected long lastSent = System.currentTimeMillis();
	protected long lastRead = System.currentTimeMillis();
	protected static State state = State.getReference();
	protected Application application = null;
	protected SerialPort serialPort = null;	
	protected String version = null;
	protected OutputStream out = null;
	protected InputStream in = null;
	
	protected static Settings settings = Settings.getReference();
	protected final String portName = state.get(State.values.serialport);
//	protected final String firmware = settings.readSetting(ManualSettings.firmware);
	
	// data buffer 
	protected byte[] buffer = new byte[32];
	protected int buffSize = 0;
	
	// thread safety 
	protected volatile boolean isconnected = false;
	public volatile boolean sliding = false;
	
	// tracking motor moves 
	private static Timer cameraTimer = null;
	
	// take from settings 
	public double clicknudgemomentummult = settings.getDouble(GUISettings.clicknudgemomentummult);	
	public int maxclicknudgedelay = settings.getInteger(GUISettings.maxclicknudgedelay);
	public int speedslow = settings.getInteger(GUISettings.speedslow);
	public int speedmed = settings.getInteger(GUISettings.speedmed);
	public int nudgedelay = settings.getInteger(GUISettings.nudgedelay);
	public int maxclickcam = settings.getInteger(GUISettings.maxclickcam);
	public int fullrotationdelay = settings.getInteger(GUISettings.fullrotationdelay);
	
    private static final int TURNBOOST = 25; 
	public int speedfast = 255;
	public int turnspeed = 255;
		
	public ArduinoPrime(Application app) {	
		
		application = app;	
		state.put(State.values.motorspeed, speedmed);
		state.put(State.values.movingforward, false);
		state.put(State.values.moving, false);
		state.put(State.values.motionenabled, true);
		
		state.put(State.values.floodlightlevel, 0);
		state.put(State.values.spotlightbrightness, 0);
		
		state.put(State.values.dockstatus, AutoDock.UNKNOWN);
		state.put(State.values.batterylife, AutoDock.UNKNOWN);
		
		if(motorsAvailable()){
			
			Util.log("attempting to connect to port", this);
			
			new Thread(new Runnable() {
				public void run() {
					connect();
					Util.delay(SETUP);
					if(isconnected){
						
						Util.log("Connected to port: " + state.get(State.values.serialport), this);
						
						setSpotLightBrightness(0);
						floodLight(0);
						sendCommand(FIND_HOME_TILT); 
						sendCommand(new byte[]{CAM, (byte) CAM_HORIZ});
						state.set(State.values.cameratilt, CAM_HORIZ);
					}
				}
			}).start();
			
//			/* be sure */
//			new Thread(new Runnable() {
//				public void run() {
//					Util.delay(SETUP*3);
//					
//					if(isconnected){ 
//						Util.log(".....checking firmware is valid", this);
//						if( ! verified){
//							Util.log("WARN: firmware is not responding, restarting", this);
//							settings.writeSettings(ManualSettings.serialport.name(), Discovery.params.discovery.name());
//							settings.incrementSettings(ManualSettings.attempts);
//							application.restart();
//						}
//					} else {
//						Util.log("WARN: firmware failed to connect", this);
//						settings.writeSettings(ManualSettings.serialport.name(), Discovery.params.discovery.name());
//					}
//				}
//			}).start(); 
		}
	}
	
	public static boolean motorsAvailable(){
		final String motors = state.get(State.values.serialport); 
		if(motors == null) return false; 
		if(motors.equals(Discovery.params.disabled.name())) return false;
//		if(motors.equals(Discovery.params.discovery.name())) return false;
		
		return true;
	}

	public void floodLight(int target) {
//		sendCommand(new byte[]{ FLOOD_LIGHT_LEVEL, 0});
//		sendCommand(new byte[]{ FLOOD_LIGHT_LEVEL, (byte)55});
//		sendCommand(new byte[]{ FLOOD_LIGHT_LEVEL, (byte) 255});
		application.message("floodLight brightness set to "+target+"%", "light", Integer.toString(target));
		state.set(State.values.floodlightlevel, target);

		Util.debug("floodlight: " + target, this);

		target = target * 255 / 100;
		sendCommand(new byte[]{FLOOD_LIGHT_LEVEL, (byte)target});
	}
	
	public void setSpotLightBrightness(int target){
		application.message("spotlight brightness set to "+target+"%", "light", Integer.toString(target));		
		state.set(State.values.spotlightbrightness, target);
		Util.debug("setSpotLightBrightness: " + target, this);
		
		target = target * 255 / 100;
		sendCommand(new byte[]{SPOT_LIGHT_LEVEL, (byte)target});

	}

	/** respond to feedback from the device  */	
	public void execute() {
		String response = "";
		for (int i = 0; i < buffSize; i++)
			response += (char) buffer[i];
		
		if(ArduinoPrime.DEBUGGING) Util.debug("serial in: " + response, this);
		
		if(response.equals("reset")) {
//			isconnected = true;
			version = null;
//			sendCommand(GET_PRODUCT);
//			Util.delay(300);
			sendCommand(GET_VERSION);  
		} 
		
//		if(response.startsWith("id:")){ 
//			
//			String product = response.substring(response.lastIndexOf(":")+1).trim();
//			if(product.equals( firmware )){
//				
//				Util.debug("verified: " + response, this);
//				settings.writeSettings(ManualSettings.attempts.name(), "0");
//				verified = true;
//				
//			} else {
//				
//				Util.log("WARN: wrong firmware type in settings, restart needed", this);
//				
//				if(settings.getInteger(ManualSettings.attempts) > MAX_ATTEMPTS) {
//					settings.writeSettings(ManualSettings.serialport.name(), Discovery.params.disabled.name());
//				} else {
//					settings.writeSettings(ManualSettings.serialport.name(), Discovery.params.discovery.name());
//				}
//				
//				settings.incrementSettings(ManualSettings.attempts);
//				application.restart();
//			}
//		}
		
		if(response.startsWith("version:")) {
			version = response.substring(response.indexOf("version:") + 8, response.length());
			application.message(this.getClass().getName() + " version: " + version, null, null);		
		} 
	
		if(response.startsWith("battery")){
			String s = response.split(" ")[1];
			if (s.equals("docked")) {
				if (!state.get(State.values.dockstatus).equals(AutoDock.DOCKED)) {
					application.message(null, "dock", AutoDock.DOCKED);
					state.put(State.values.dockstatus, AutoDock.DOCKED);
					state.set(State.values.batterycharging, true);
				}
//				if (state.getBoolean(State.values.motionenabled)) state.set(State.values.motionenabled, false);
			}
			if (s.equals("undocked")) {
				if (!state.get(State.values.dockstatus).equals(AutoDock.UNDOCKED)) {
					state.put(State.values.dockstatus, AutoDock.UNDOCKED);
					application.message(null, "dock", AutoDock.UNDOCKED );
					state.set(State.values.batterycharging, false);
				}
//				if (!state.getBoolean(State.values.motionenabled)) state.set(State.values.motionenabled, true);
				
			}
			String battinfo = response.split(" ")[2];
			if (!state.get(State.values.batterylife).equals(battinfo)) {
				state.put(State.values.batterylife, battinfo); 
			}
		}
		
		if(response.startsWith("tiltpos")) {
			String position = response.split(" ")[1];
			state.set(State.values.cameratilt, position);
			application.messageplayer(null, "cameratilt", state.get(State.values.cameratilt));
		}
	}

	public void connect() {
		try {

			serialPort = (SerialPort) CommPortIdentifier.getPortIdentifier(portName).open(ArduinoPrime.class.getName(), SETUP);
			serialPort.setSerialPortParams(115200, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);

			// open streams
			out = serialPort.getOutputStream();
			in = serialPort.getInputStream();

			// register for serial events
			serialPort.addEventListener(this);
			serialPort.notifyOnDataAvailable(true);
			
			isconnected = true;

		} catch (Exception e) {
			
			Util.log("can't connect to port: " + e.getMessage(), this);
//			settings.writeSettings(ManualSettings.serialport.name(), Discovery.params.discovery.name());
//			settings.incrementSettings(ManualSettings.attempts);
//			application.restart();
			
		}
	}

	public boolean isConnected() {
		return isconnected;
	}

	/** */
	public void manageInput(){
		try {
			byte[] input = new byte[32];
			int read = in.read(input);
			for (int j = 0; j < read; j++) {
				// print() or println() from arduino code
				if ((input[j] == '>') || (input[j] == 13) || (input[j] == 10)) {
					// do what ever is in buffer
					if (buffSize > 0) execute();
					buffSize = 0; // reset
					// track input from arduino
					lastRead = System.currentTimeMillis();
				} else if (input[j] == '<') {
					// start of message
					buffSize = 0;
				} else {
					// buffer until ready to parse
					buffer[buffSize++] = input[j];
				}
			}
		} catch (IOException e) {
			System.out.println("event : " + e.getMessage());
		}
	}
		
	public void serialEvent(SerialPortEvent event) {
		if (event.getEventType() == SerialPortEvent.DATA_AVAILABLE) {
			manageInput();
		}
	}

	public long getWriteDelta() {
		return System.currentTimeMillis() - lastSent;
	}
	
	public String getVersion() {
		return version;
	}
	
	public long getReadDelta() {
		return System.currentTimeMillis() - lastRead;
	}

	
	public void setEcho(boolean update) {
		if (update) sendCommand(ECHO_ON);
		else sendCommand(ECHO_OFF);
	}

	
	public void reset() {
		if (isconnected) {
			new Thread(new Runnable() {
				public void run() {
					disconnect();
					connect();
				}
			}).start();
		}
	}

	/** shutdown serial port */
	protected void disconnect() {
		try {
			if(in!=null) in.close();
			if(out!=null) out.close();
			isconnected = false;
			version = null;
		} catch (Exception e) {
			Util.log("disconnect(): " + e.getMessage(), this);
		}
		if(serialPort!=null) serialPort.close();
	}

	/**
	 * Send a multiple byte command to send the device
	 * 
	 * @param command
	 *            is a byte array of messages to send
	 */
	public void sendCommand(byte[] cmd) {

		if (!isconnected) return;
		
		if(DEBUGGING) {
			String text = "sendCommand(): " + (char)cmd[0] + " ";
			for(int i = 1 ; i < cmd.length ; i++) 
				text += (byte)cmd[i] + " ";
			
			Util.debug(text, this);
		}
		
		if (state.getBoolean(State.values.controlsinverted)) {
			switch (cmd[0]) {
			case ArduinoPrime.FORWARD: cmd[0]=ArduinoPrime.BACKWARD; break;
			case ArduinoPrime.BACKWARD: cmd[0]=ArduinoPrime.FORWARD; break;
			case ArduinoPrime.LEFT: cmd[0]=ArduinoPrime.RIGHT; break;
			case ArduinoPrime.RIGHT: cmd[0]=ArduinoPrime.LEFT; 
			}
		}
		
		final byte[] command = cmd;
		new Thread(new Runnable() {
			public void run() {
				try {

					// send
					out.write(command);
		
					// end of command
					out.write(13);
		
				} catch (Exception e) {
					reset();
					Util.log("OCULUS: sendCommand(), " + e.getMessage(), this);
				}
			}
		}).start();
		
		// track last write
		lastSent = System.currentTimeMillis();
	}
	
	
	public void sendCommand(final byte cmd){
		sendCommand(new byte[]{cmd});
	}

	public void goForward() {
		sendCommand(new byte[] { FORWARD, (byte) state.getInteger(State.values.motorspeed) });
		state.put(State.values.moving, true);
		state.put(State.values.movingforward, true);
		if (state.getBoolean(State.values.muteOnROVmove)) application.muteROVMic();
	}

	public void goBackward() {
		sendCommand(new byte[] { BACKWARD, (byte) state.getInteger(State.values.motorspeed) });
		state.put(State.values.moving, true);
		state.put(State.values.movingforward, false);
		if (state.getBoolean(State.values.muteOnROVmove)) application.muteROVMic();
	}

	public void turnRight() {
		int tmpspeed = turnspeed;
		int boost = TURNBOOST;
		int speed = state.getInteger(State.values.motorspeed);
		if (speed < turnspeed && (speed + boost) < speedfast)
			tmpspeed = speed + boost;

		sendCommand(new byte[] { RIGHT, (byte) tmpspeed });
		state.put(State.values.moving, true);
		if (state.getBoolean(State.values.muteOnROVmove)) application.muteROVMic();
	}

	public void turnLeft() {
		int tmpspeed = turnspeed;
		int boost = TURNBOOST;
		int speed = state.getInteger(State.values.motorspeed);
		if (speed < turnspeed && (speed + boost) < speedfast)
			tmpspeed = speed + boost;

		sendCommand(new byte[] { LEFT, (byte) tmpspeed });
		state.put(State.values.moving, true);
		if (state.getBoolean(State.values.muteOnROVmove)) application.muteROVMic();
	}
	
	private class cameraUpTask extends TimerTask {
		@Override
		public void run(){
			
			state.set(State.values.cameratilt, state.getInteger(State.values.cameratilt) + CAM_NUDGE);
			
			if (state.getInteger(State.values.cameratilt) >= CAM_MAX) {
				cameraTimer.cancel();
				sendCommand(new byte[] { HOME_TILT_REAR, (byte) CAM_MAX }); //calibrate
				state.set(State.values.cameratilt, CAM_MAX);
				return;
			}
		
			sendCommand(new byte[] { CAM, (byte) state.getInteger(State.values.cameratilt) });
		}
	}
	
	private class cameraDownTask extends TimerTask {
		@Override
		public void run(){
			
			state.set(State.values.cameratilt, state.getInteger(State.values.cameratilt) - CAM_NUDGE);
			
			if (state.getInteger(State.values.cameratilt) <= CAM_MIN) {
				cameraTimer.cancel();
				sendCommand(new byte[] { HOME_TILT_FRONT }); // calibrate
				state.set(State.values.cameratilt, CAM_MIN);
				return;
			}
	
			sendCommand(new byte[] { CAM, (byte) state.getInteger(State.values.cameratilt) });
		}
	}
	
	public void camCommand(cameramove move){ 

		Util.debug("camCommand(): " + move, this);
		
		if (state.getBoolean(State.values.autodocking)) {
			application.messageplayer("command dropped, autodocking", null, null);
			return;
		}
		
		if (state.getBoolean(State.values.controlsinverted)) {
			switch (move) {
			case up: move=cameramove.down; break;
			case down: move=cameramove.up; break;
			case upabit: move=cameramove.downabit; break;
			case downabit: move=cameramove.upabit; break; 
			}
		}
		
		int position;
		
		switch (move) {
		
			case stop: 
				if(cameraTimer != null) cameraTimer.cancel();
				cameraTimer = null;
				return;
				
			case up:  
				cameraTimer = new java.util.Timer();  
				cameraTimer.scheduleAtFixedRate(new cameraUpTask(), 0, CAM_NUDGE_DELAY);
				return;
			
			case down:  
				cameraTimer = new java.util.Timer();  
				cameraTimer.scheduleAtFixedRate(new cameraDownTask(), 0, CAM_NUDGE_DELAY);
				return;
			
			case horiz: 
				sendCommand(new byte[] { CAM, (byte) CAM_HORIZ });
				state.set(State.values.cameratilt, CAM_HORIZ);
				return;
			
			case downabit: 
				position= state.getInteger(State.values.cameratilt) - CAM_NUDGE*2;
				if (position <= CAM_MIN) { 
					position = CAM_MIN;
					sendCommand(new byte[] { HOME_TILT_FRONT }); //calibrate 
				}
				else {  sendCommand(new byte[] { CAM, (byte) position }); }
				state.set(State.values.cameratilt, position);
				return; 
			
			case upabit: 
				position = state.getInteger(State.values.cameratilt) + CAM_NUDGE*2;
				if (position >= CAM_MAX) { 
					position = CAM_MAX;
					sendCommand(new byte[] { HOME_TILT_REAR, (byte) CAM_MAX }); //calibrate
				}
				else { sendCommand(new byte[] { CAM, (byte) position }); }
				state.set(State.values.cameratilt, position);
				return;
				
			case frontstop:  
				sendCommand(new byte[] { HOME_TILT_FRONT });
				state.set(State.values.cameratilt, CAM_MIN);
				return;
				
			case rearstop: 
				sendCommand(new byte[] { HOME_TILT_REAR, (byte) CAM_MAX });
				state.set(State.values.cameratilt, CAM_MAX);
				return;
		}
	
	}
	

	public void speedset(final speeds update) {
		
		Util.debug("speedset(): " + update, this);
		
		switch (update) {
		case slow: state.put(State.values.motorspeed, speedslow); break;
		case med: state.put(State.values.motorspeed, speedmed); break;
		case fast: state.put(State.values.motorspeed, speedfast); break;
		}
	}

	public void nudge(final direction dir) {
		
		Util.debug("nudge(): " + dir, this);
		
		new Thread(new Runnable() {
			public void run() {
				
				int n = nudgedelay;
				
				switch (dir) {
				case right: turnRight(); break;
				case left: turnLeft(); break;
				case forward: 
					goForward();
					state.put(State.values.movingforward, false);
					n *= 4; 
					break;
				case backward:
					goBackward();
					n *= 4;
				}
				
				Util.delay(n);

				if (state.getBoolean(State.values.movingforward)) goForward();
				else stopGoing();
				
			}
		}).start();
	}

	public void rotate(final direction dir, final int degrees) {
		new Thread(new Runnable() {
			public void run() {
				
				final int tempspeed = state.getInteger(State.values.motorspeed);
				state.put(State.values.motorspeed, speedfast);
				
				int n = fullrotationdelay * degrees / 360;
				
				switch (dir) {
					case right: turnRight(); break;
					case left: turnLeft(); 
				}
				
				Util.delay(n);

				stopGoing();
				state.put(State.values.motorspeed, tempspeed);
				application.message(null, "motion", "stopped");
				
			}
		}).start();
	}
	
	public void slide(final direction dir){
		
		Util.debug("slide(): " + dir, this);
		
		if (sliding == false) {
			sliding = true;
			
			new Thread(new Runnable() {
				public void run() {
					try {	
						
						final int tempspeed = state.getInteger(State.values.motorspeed);
						final int distance = 300;
						final int turntime = 500;
						
						state.put(State.values.motorspeed, speedfast);
						
						switch (dir) {
						case right: turnLeft(); break;
						case left: turnRight(); break;
						}
				
						Thread.sleep(turntime);
						
						if (sliding == true) {
							goBackward();
							Thread.sleep(distance);
							
							if (sliding == true) {
								
								switch (dir) {
								case right: turnRight();
								case left: turnLeft();
								}	
									
								Thread.sleep(turntime);
							 
								if (sliding == true) {
									goForward();
									Thread.sleep(distance);
									if (sliding == true) {
										stopGoing();
										sliding = false;
										state.put(State.values.motorspeed, tempspeed);
									}
								}
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}).start();
		}
	}

	public void slidecancel() {
		if (sliding == true) sliding = false;
	}

	public void clickSteer(final int x, int y) {
		
		Util.debug("__clickSteer(): " + x, this);
		
		if (state.getBoolean(State.values.controlsinverted)) {
			y=-y;
		}
		
		clickCam(y);
		clickNudge(x);
	}

	private void clickNudge(final Integer x) {
		
		Util.debug("__clickNudge(): " + "x: " + x, this);
		
		//TODO:  unsure of this 
		final double mult = Math.pow(((320.0 - (Math.abs(x))) / 320.0), 3)* clicknudgemomentummult + 1.0;
		final int clicknudgedelay = (int) ((maxclicknudgedelay * (Math.abs(x)) / 320) * mult);
		
		Util.debug("__clickNudge() n: "+clicknudgemomentummult+" mult: "+mult+" clicknudgedelay-after: "+clicknudgedelay, this);
		
		new Thread(new Runnable() {	
			public void run() {
				try {
					
					final int tempspeed = state.getInteger(State.values.motorspeed);
					state.put(State.values.motorspeed, speedfast);
					
					if (x > 0) turnRight();
					else turnLeft();
					
					Thread.sleep(clicknudgedelay);
					state.put(State.values.motorspeed, tempspeed);
					
					if (state.getBoolean(State.values.movingforward)) goForward();
					else stopGoing();
					
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();
	
	}
	public void stopGoing() {
		sendCommand(STOP);
		state.put(State.values.moving, false);
		state.put(State.values.movingforward, false);
		if (state.getBoolean(State.values.muteOnROVmove) && state.getBoolean(State.values.moving)) application.unmuteROVMic();
	}
	
	private void clickCam(final Integer y) {
		
		Util.debug("___clickCam(): " + "y: " + y, this);
		
		Integer n = maxclickcam * y / 240;  
		state.set(State.values.cameratilt, state.getInteger(State.values.cameratilt) - n);
		
		// range check 
		if (state.getInteger(State.values.cameratilt) < CAM_MIN) state.set(State.values.cameratilt, CAM_MIN);
		if (state.getInteger(State.values.cameratilt) > CAM_MAX) state.set(State.values.cameratilt, CAM_MAX);
		
		application.messageplayer(null, "cameratilt", state.get(State.values.cameratilt));
		sendCommand(new byte[] { CAM, (byte) state.getInteger(State.values.cameratilt) });	
		
	}

}

