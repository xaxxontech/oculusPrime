package oculusPrime.commport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TooManyListenersException;
import java.util.UUID;

import developer.depth.Mapper;
import developer.depth.ScanUtils;
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

	public enum direction { stop, right, left, forward, backward, unknown };
	public enum cameramove { stop, up, down, horiz, upabit, downabit, rearstop, reverse };
	public enum speeds { slow, med, fast };  
	public enum mode { on, off };

	public static final long DEAD_TIME_OUT = 60000;
	public static final int WATCHDOG_DELAY = 8000;	
	public static final long RESET_DELAY = 14400000; // 4 hrs
	public static final long DOCKING_DELAY = 1000;
	public static final int SETUP = 4000;
	public static final int BAUD = 115200;
	
	
	public static final byte STOP = 's';
	public static final byte FORWARD = 'f';
	public static final byte BACKWARD = 'b';
	public static final byte LEFT = 'l';
	public static final byte RIGHT = 'r';
	public static final byte LEFTTIMED = 'z';
	public static final byte RIGHTTIMED = 'e';
	public static final byte HARD_STOP = 'h'; // TODO: unused
	
	public static final byte FLOOD_LIGHT_LEVEL = 'o'; 
	public static final byte SPOT_LIGHT_LEVEL = 'p';
	public static final byte FWDFLOOD_LIGHT_LEVEL = 'q';
		
	public static final byte CAM = 'v';
	public static final byte CAMRELEASE = 'w';
	public static final byte CAMHORIZSET = 'm';
	public static final byte GET_PRODUCT = 'x';
	public static final byte GET_VERSION = 'y';
	public static final byte ODOMETRY_START = 'i';
	public static final byte ODOMETRY_STOP_AND_REPORT = 'j';
	public static final byte ODOMETRY_REPORT = 'k';
	public static final byte PING = 'c';
		
	public static final int CAM_NUDGE = 3; // degrees
	public static final long CAM_SMOOTH_DELAY = 50;
	public static final long CAM_RELEASE_DELAY = 500;
//	public static final int CAM_EXTRA_FOR_CALIBRATE = 90; // degrees
	public static final String FIRMWARE_ID = "malg";

	private static final long STROBEFLASH_MAX = 5000; //strobe timeout
	private static final int ACCEL_DELAY = 75;
	private static final int COMP_DELAY = 500;

	protected long lastSent = System.currentTimeMillis();
	protected long lastRead = System.currentTimeMillis();
	protected long lastReset = System.currentTimeMillis();
	protected static State state = State.getReference();
	protected Application application = null;
	protected static SerialPort serialPort = null;	
	protected static OutputStream out = null; 
	protected static InputStream in = null;
	protected String version = null;
	
	protected static Settings settings = Settings.getReference();
	
	// data buffer 
	protected byte[] buffer = new byte[32];
	protected int buffSize = 0;
	
	// thread safety 
	protected volatile boolean isconnected = false;
//	public volatile boolean sliding = false;
	protected volatile UUID currentMoveID;
	protected volatile UUID currentCamMoveID;
	
//	private boolean invertswap = false;
	
	// tracking motor moves 
	private static Timer cameraTimer = null;
	
	// take from settings 
	private static final double clicknudgemomentummult = 0.25;	
	public int maxclicknudgedelay = settings.getInteger(GUISettings.maxclicknudgedelay);
	public int speedslow = settings.getInteger(GUISettings.speedslow);
	public int speedmed = settings.getInteger(GUISettings.speedmed);
	public int nudgedelay = settings.getInteger(GUISettings.nudgedelay);
	public int maxclickcam = settings.getInteger(GUISettings.maxclickcam);
	public int fullrotationdelay = settings.getInteger(GUISettings.fullrotationdelay);
	public int onemeterdelay = settings.getInteger(GUISettings.onemeterdelay);
	public int steeringcomp = 0;
	
	public static int CAM_HORIZ = settings.getInteger(GUISettings.camhoriz); 
	public static int CAM_REVERSE = settings.getInteger(GUISettings.camreverse);;
	public static int CAM_MAX; 
	public static int CAM_MIN;
	
	public String portname = settings.readSetting(ManualSettings.motorport);
	
    private static final int TURNBOOST = 25; 
	public static final int speedfast = 255;
//	public static final int turnspeed = 255;
//	private static final double GYROCOMP = 1.09;
		
	public ArduinoPrime(Application app) {	
		
		application = app;	
		state.put(State.values.motorspeed, speedfast);
		state.put(State.values.movingforward, false);
		state.put(State.values.moving, false);
		state.put(State.values.motionenabled, true);
		
		state.put(State.values.floodlightlevel, 0);
		state.put(State.values.spotlightbrightness, 0);
		
		state.put(State.values.dockstatus, AutoDock.UNKNOWN);
		state.put(State.values.batterylife, AutoDock.UNKNOWN);
		state.put(State.values.motorport, portname);
		setSteeringComp(settings.readSetting(GUISettings.steeringcomp));
//		state.put(State.values.wheeldiamm,  settings.readSetting(ManualSettings.wheeldiameter));
		state.put(State.values.direction, direction.stop.toString());
//		state.put(State.values.gyrocomp, GYROCOMP);
		
		setCameraStops(CAM_HORIZ, CAM_REVERSE);
		
		if(motorsReady()){
			
			Util.log("attempting to connect to port"+portname, this);
			
				if (!isconnected) {
					connect();
					Util.delay(SETUP);
				}
				if(isconnected){
					
					Util.log("Connected to port: " + state.get(State.values.motorport), this);
					
					initialize();

				}
			
		}
		
		new WatchDog().start();

	}
	
	public void initialize() {
		
		Util.debug("initialize", this);
		
		registerListeners();
		cameraToPosition(CAM_HORIZ);
		setSpotLightBrightness(0);
		floodLight(0);
		
		lastRead = System.currentTimeMillis();
		lastReset = lastRead;		
	}
	
	/** inner class to check if getting responses in timely manor */
	public class WatchDog extends Thread {
		oculusPrime.State state = oculusPrime.State.getReference();
		
		public WatchDog() {
			this.setDaemon(true);
		}

		public void run() {
			
			
			Util.delay(SETUP);
			
			while (true) {
				long now = System.currentTimeMillis();
				
				if (now - lastReset > RESET_DELAY && isconnected) Util.debug(FIRMWARE_ID+" PCB past reset delay", this);
				
				if (now - lastReset > RESET_DELAY && !state.getBoolean(oculusPrime.State.values.autodocking) && 
						state.get(oculusPrime.State.values.driver) == null && isconnected &&
						!state.getBoolean(oculusPrime.State.values.moving)) {
					Util.log("motors board periodic reset", this);
					reset();
				}

//				if (now - lastRead > DEAD_TIME_OUT && isconnected) {
//					sendCommand(PING); 
//					long delay = 10L;
//					Util.delay(delay);
//					if (now + delay - lastRead > delay && isconnected) { // no response!
//						application.message(FIRMWARE_ID+" PCB timeout, attempting reset", null, null);
//						reset();
//					}
//				}
				
				if (now - lastRead > DEAD_TIME_OUT && isconnected) {
					application.message(FIRMWARE_ID+" PCB timeout, attempting reset", null, null);
					reset();
				}
				
//				if (now - lastSent > WATCHDOG_DELAY && isconnected)  sendCommand(PING);			
				sendCommand(PING);

				Util.delay(WATCHDOG_DELAY);
			}		
		}
	}
	
	public static boolean motorsReady (){
		final String motors = state.get(State.values.motorport); 
		if(motors == null) return false; 
		if(motors.equals(Discovery.params.disabled.name())) return false;
		if(motors.equals(Discovery.params.discovery.name())) return false;
		
		return true;
	}

	public void floodLight(int target) {
		state.set(State.values.floodlightlevel, target);

		target = target * 255 / 100;
		sendCommand(new byte[]{FLOOD_LIGHT_LEVEL, (byte)target});
	}
	
	public void fwdflood(int target) {
		state.set(State.values.fwdfloodlevel, target);

		target = target * 255 / 100;
		sendCommand(new byte[]{FWDFLOOD_LIGHT_LEVEL, (byte)target});
	}
	
	public void setSpotLightBrightness(int target){
		state.set(State.values.spotlightbrightness, target);
		
		target = target * 255 / 100;
		sendCommand(new byte[]{SPOT_LIGHT_LEVEL, (byte)target});

	}
	
	public void strobeflash(String mode, long d, int i) {
		if (d==0) d=STROBEFLASH_MAX;
		final long duration = d;
		if (i==0) i=255;
		final int intensity = i * 255 / 100;
		if (mode.equalsIgnoreCase(ArduinoPrime.mode.on.toString())) {
			state.set(State.values.strobeflashon, true);
			final long strobestarted = System.currentTimeMillis();
			new Thread(new Runnable() {
				public void run() {
					try {
						while (state.getBoolean(State.values.strobeflashon)) {
							if (System.currentTimeMillis() - strobestarted > STROBEFLASH_MAX || 
									System.currentTimeMillis() - strobestarted > duration) {
								state.set(State.values.strobeflashon, false);
							}
							sendCommand(new byte[]{SPOT_LIGHT_LEVEL, (byte)0});
							sendCommand(new byte[]{FLOOD_LIGHT_LEVEL, (byte)intensity});
							Thread.sleep(50);
							sendCommand(new byte[]{SPOT_LIGHT_LEVEL, (byte)intensity});
							sendCommand(new byte[]{FLOOD_LIGHT_LEVEL, (byte)0});
							Thread.sleep(50);
							
						}
						Thread.sleep(50);
						setSpotLightBrightness(state.getInteger(State.values.spotlightbrightness));
						floodLight(state.getInteger(State.values.floodlightlevel));
					} catch (Exception e) { } }
			}).start();
		}
		if (mode.equalsIgnoreCase(ArduinoPrime.mode.off.toString())) {
			state.set(State.values.strobeflashon, false);
		}
	}

	/** respond to feedback from the device  */	
	public void execute() {
		String response = "";
		for (int i = 0; i < buffSize; i++)
			response += (char) buffer[i];
		
//		Util.debug("serial in: " + response, this);
		
		if(response.equals("reset")) {
			version = null;
			sendCommand(GET_VERSION);  
			Util.debug(FIRMWARE_ID+" "+response, this);
		} 
		
		if(response.startsWith("version:")) {
			version = response.substring(response.indexOf("version:") + 8, response.length());
			application.message("malg board firmware version: " + version, null, null);		
		} 
	
		String[] s = response.split(" ");

		if (s[0].equals("moved")) {
			int d = (int) (Double.parseDouble(s[1]) * Math.PI * settings.getInteger(ManualSettings.wheeldiameter));
			double a = Double.parseDouble(s[2]);
//			a *= state.getDouble(State.values.gyrocomp.toString()); // apply comp
			a *= settings.getDouble(ManualSettings.gyrocomp.toString());
			
			state.set(State.values.distanceangle, d +" "+a);
			
			// TODO: testing only ----------------
			if (Application.openNIRead.depthCamGenerating) {
				if (!state.exists(State.values.distanceanglettl.toString())) {
					state.set(State.values.distanceanglettl, "0 0");
				}
				
				int dttl = Integer.parseInt(state.get(State.values.distanceanglettl).split(" ")[0]);
				double attl = Double.parseDouble(state.get(State.values.distanceanglettl).split(" ")[1]);
				dttl += d;
				attl += a;
				String dattl = dttl+" "+attl;
				state.set(State.values.distanceanglettl,dattl);
			}
			// end of testing only ----------------
		}
		else if (s[0].equals("stop") && state.getBoolean(State.values.stopbetweenmoves)) 
			state.set(State.values.direction, direction.stop.toString());
		else if (s[0].equals("stopdetectfail")) {
			application.message("FIRMWARE STOP DETECT FAIL", null, null);
			if (state.getBoolean(State.values.stopbetweenmoves)) 
				state.set(State.values.direction, direction.stop.toString());
		}

	}

	private void connect() {
		try {

			Util.debug("attempting connect to "+portname, this);
			serialPort = (SerialPort) CommPortIdentifier.getPortIdentifier(portname).open(ArduinoPrime.class.getName(), SETUP);
			serialPort.setSerialPortParams(BAUD, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);

			// open streams
			out = serialPort.getOutputStream();
			in = serialPort.getInputStream();

			isconnected = true;

		} catch (Exception e) {
			
			Util.log("can't connect to port: " + e.getMessage(), this);
			
		}
	}
	
	protected void registerListeners() {
		if (serialPort != null) {
			try {
				serialPort.addEventListener(this);
			} catch (TooManyListenersException e) {
				e.printStackTrace();
			}
			serialPort.notifyOnDataAvailable(true);
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

	
//	public void setEcho(boolean update) {
//		if (update) sendCommand(ECHO_ON);
//		else sendCommand(ECHO_OFF);
//	}

	
	public void reset() {
//		if (isconnected) {
			new Thread(new Runnable() {
				public void run() {
					Util.log("resetting MALG board", this);
					disconnect();
					connect();
					Util.delay(SETUP);
					initialize();
				}
			}).start();
//		}
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
		
		if (state.getBoolean(State.values.controlsinverted)) {
			switch (cmd[0]) {
			case ArduinoPrime.FORWARD: cmd[0]=ArduinoPrime.BACKWARD; break;
			case ArduinoPrime.BACKWARD: cmd[0]=ArduinoPrime.FORWARD; break;
			case ArduinoPrime.LEFT: cmd[0]=ArduinoPrime.RIGHT; break;
			case ArduinoPrime.RIGHT: cmd[0]=ArduinoPrime.LEFT; 
			}
		}
		
//		if(settings.getBoolean(ManualSettings.debugenabled)) {
//			String text = "sendCommand(): " + (char)cmd[0] + " ";
//			for(int i = 1 ; i < cmd.length ; i++) 
//				text += ((byte)cmd[i] & 0xFF) + " ";  // & 0xFF converts to unsigned byte
//			
//			Util.log("DEBUG: "+ text, this);
//		}
		
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
		
		final UUID moveID = UUID.randomUUID(); 
		currentMoveID = moveID;
		
		if (state.getBoolean(State.values.stopbetweenmoves)) {
				
			if ( !state.get(State.values.direction).equals(direction.stop.toString()) ) {
			
				stopGoing();
				currentMoveID = moveID;

				new Thread(new Runnable() {public void run() {
					long stopwaiting = System.currentTimeMillis()+1000;
					while(!state.get(State.values.direction).equals(direction.stop.toString()) &&
							System.currentTimeMillis() < stopwaiting) {} // wait
					if (currentMoveID.equals(moveID))  goForward();
					
				} }).start();
				
				return;
			}
		}

		state.put(State.values.moving, true);
		state.put(State.values.movingforward, true);
		if (settings.getBoolean(GUISettings.muteonrovmove))  application.muteROVMic();
		
		int speed1 = (int) voltsComp((double) speedslow);
		if (speed1 > 255) { speed1 = 255; }
		
		int speed2= state.getInteger(State.values.motorspeed);
	
		if (speed2<speed1) { 		// voltcomp on slow speed only
			speed2 = (int) voltsComp((double) speed2);
			if (speed2 > 255) { speed2 = 255; }
		}
		
		// no full speed when on dock voltage
		if (state.get(State.values.dockstatus).equals(AutoDock.DOCKED) && speed2==speedfast) {
			speed2 = speedmed;
		}
		
//		application.gyroport.sendCommand(FORWARD);

		if (state.get(State.values.direction).equals(direction.forward.toString()) ) {
			int[] comp = applyComp(speed2); 
			int L, R;
//			if (!state.getBoolean(State.values.controlsinverted)) {
				L = comp[0];
				R = comp[1];
//			}
//			else {
//				R = comp[1];
//				L = comp[0];
//			}
			sendCommand(new byte[] { FORWARD, (byte) R, (byte) L});
			return;
		}
		
		// slow, un-comped 
		sendCommand(new byte[] { FORWARD, (byte) speed1, (byte) speed1});

		final int spd = speed2;
		
		if (speed2 > speed1) { 
			new Thread(new Runnable() {
				public void run() {
					
					Util.delay(ACCEL_DELAY);
					
					if (!currentMoveID.equals(moveID))  return;

					// actual speed, un-comped
					sendCommand(new byte[] { FORWARD, (byte) spd, (byte) spd});

				} 
			}).start();
		}
		
		if (steeringcomp != 0) {  
			new Thread(new Runnable() {
				public void run() {
					Util.delay(COMP_DELAY); 

					if (!currentMoveID.equals(moveID))  return;
					
					int[] comp = applyComp(spd); // actual speed, comped
					int L,R;
//					if (!state.getBoolean(State.values.controlsinverted)) {
						L = comp[0];
						R = comp[1];
//					}
//					else {
//						R = comp[1];
//						L = comp[0];
//					}
					sendCommand(new byte[] { FORWARD, (byte) R, (byte) L});
					
				} 
			}).start();
		}
		
		state.set(State.values.direction, direction.forward.toString());
	}
	
	private int[] applyComp(int spd) {
		int A = spd;
		int B = spd;
		int comp = (int) ((double) steeringcomp * Math.pow((double) spd/(double) speedfast, 2.0));
		
		if (state.getBoolean(State.values.controlsinverted)) {
			if (steeringcomp < 0) A += comp; // left motor reduced
			else if (steeringcomp > 0) B -= comp; // right motor reduced
		}
		else {
			if (steeringcomp < 0) B += comp; // right motor reduced
			else if (steeringcomp > 0) A -= comp; // left motor reduced
		}
		return new int[] {A, B};  // reverse L&R for backwards
	}

	public void goBackward() {
	
		final UUID moveID = UUID.randomUUID(); 
		currentMoveID = moveID;

		if (state.getBoolean(State.values.stopbetweenmoves)) {
			
			if ( !state.get(State.values.direction).equals(direction.stop.toString())  ) {
			
				stopGoing();
				currentMoveID = moveID;
				
				new Thread(new Runnable() {public void run() {
					long stopwaiting = System.currentTimeMillis()+1000;
					while(!state.get(State.values.direction).equals(direction.stop.toString()) &&
							System.currentTimeMillis() < stopwaiting) {} // wait
					if (currentMoveID.equals(moveID))  goBackward();
					
				} }).start();
				
				return;
			}
		}

		state.put(State.values.moving, true);
		state.put(State.values.movingforward, false);
		if (settings.getBoolean(GUISettings.muteonrovmove))  application.muteROVMic();
		
		
		int speed1 = (int) voltsComp((double) speedslow);
		if (speed1 > 255) { speed1 = 255; }
		
		int speed2= state.getInteger(State.values.motorspeed);
	
		if (speed2<speed1) { 		// voltcomp on slow speed only
			speed2 = (int) voltsComp((double) speed2);
			if (speed2 > 255) { speed2 = 255; }
		}
		
		// no full speed when on dock voltage
		if (state.get(State.values.dockstatus).equals(AutoDock.DOCKED) && speed2==speedfast) {
			speed2 = speedmed;
		}
		
//		application.gyroport.sendCommand(BACKWARD);

		if (state.get(State.values.direction).equals(direction.backward.toString()) ) { 
			int[] comp = applyComp(speed2); // apply comp now that up to speed
			int L = comp[1];
			int R = comp[0];
			sendCommand(new byte[] { BACKWARD, (byte) R, (byte) L});
			return;
		}
		
		// send un-comped forward command to get wheels moving, helps drive straighter
		sendCommand(new byte[] { BACKWARD, (byte) speed1, (byte) speed1});
		final int spd = speed2;

		if (speed2 > speed1) { 
			new Thread(new Runnable() {
				public void run() {
					
					Util.delay(ACCEL_DELAY);
					
					if (!currentMoveID.equals(moveID))  return;

					// actual speed, un-comped
					sendCommand(new byte[] { BACKWARD, (byte) spd, (byte) spd});

				} 
			}).start();
		}
		
		if (steeringcomp != 0) {  
			new Thread(new Runnable() {
				public void run() {
					Util.delay(COMP_DELAY); 

					if (!currentMoveID.equals(moveID))  return;
					
					int[] comp = applyComp(spd); // apply comp now that up to speed
					int L = comp[1];
					int R = comp[0];
					sendCommand(new byte[] { BACKWARD, (byte) R, (byte) L});
					
				} 
			}).start();
		}
		state.set(State.values.direction, direction.backward.toString()); // now go
	}

	public void turnRight() {
		turnRight(0);
	}
	
	public void turnRight(int delay) {
		final UUID moveID = UUID.randomUUID(); 
		currentMoveID = moveID;

		if (state.getBoolean(State.values.stopbetweenmoves)) {
			
			if ( !(state.get(State.values.direction).equals(direction.right.toString()) ||
				state.get(State.values.direction).equals(direction.stop.toString()) ) ) {
			
				stopGoing();
				currentMoveID = moveID;

				new Thread(new Runnable() {public void run() {
					
					long stopwaiting = System.currentTimeMillis()+1000;
					while(!state.get(State.values.direction).equals(direction.stop.toString()) &&
							System.currentTimeMillis() < stopwaiting) {} // wait
					if (currentMoveID.equals(moveID))  turnRight();
					
				} }).start();
				
				return;
			}
		}

		state.set(State.values.direction, direction.right.toString()); // now go
		
//		int tmpspeed = turnspeed;
//		int boost = TURNBOOST;
//		int speed = state.getInteger(State.values.motorspeed);
//		if (speed < turnspeed && (speed + boost) < speedfast)
//			tmpspeed = speed + boost;
		
		int tmpspeed = state.getInteger(State.values.motorspeed) + TURNBOOST;
		if (tmpspeed > 255) tmpspeed = 255;
		
		if (delay==0) {
			sendCommand(new byte[] { RIGHT, (byte) tmpspeed, (byte) tmpspeed });
			state.put(State.values.moving, true);
			if (settings.getBoolean(GUISettings.muteonrovmove))  application.muteROVMic();
		}
		else {
        	byte d1 = (byte) ((delay >> 8) & 0xff);
			byte d2 = (byte) (delay & 0xff);
			sendCommand(new byte[] { RIGHTTIMED, (byte) tmpspeed, (byte) tmpspeed, (byte) d1, (byte) d2});
		}
	}

	public void turnLeft() {
		turnLeft(0);
	}
	
	public void turnLeft(int delay) {
		final UUID moveID = UUID.randomUUID(); 
		currentMoveID = moveID;
		
		if (state.getBoolean(State.values.stopbetweenmoves)) {
			
			if ( !(state.get(State.values.direction).equals(direction.left.toString()) ||
				state.get(State.values.direction).equals(direction.stop.toString()) ) ) {
			
				stopGoing();
				currentMoveID = moveID;

				new Thread(new Runnable() {public void run() {
					
					long stopwaiting = System.currentTimeMillis()+1000;
					while(!state.get(State.values.direction).equals(direction.stop.toString()) &&
							System.currentTimeMillis() < stopwaiting) {} // wait
					if (currentMoveID.equals(moveID))  turnLeft();
					
				} }).start();
				
				return;
			}
		}	
		
		state.set(State.values.direction, direction.left.toString()); // now go

//		int tmpspeed = turnspeed;
//		int boost = TURNBOOST;
//		int speed = state.getInteger(State.values.motorspeed);
//		if (speed < turnspeed && (speed + boost) < speedfast)
//			tmpspeed = speed + boost;
		
		int tmpspeed = state.getInteger(State.values.motorspeed) + TURNBOOST;
		if (tmpspeed > 255) tmpspeed = 255;
		
		if (delay==0) {
			sendCommand(new byte[] { LEFT, (byte) tmpspeed, (byte) tmpspeed });
			state.put(State.values.moving, true);
			if (settings.getBoolean(GUISettings.muteonrovmove))  application.muteROVMic();
		}
		else {
        	byte d1 = (byte) ((delay >> 8) & 0xff);
			byte d2 = (byte) (delay & 0xff);
			sendCommand(new byte[] { LEFTTIMED, (byte) tmpspeed, (byte) tmpspeed, (byte) d1, (byte) d2});
		}

	}
	
	private class cameraUpTask extends TimerTask {
		@Override
		public void run(){
			
			state.set(State.values.cameratilt, state.getInteger(State.values.cameratilt) - CAM_NUDGE);
			
			if (state.getInteger(State.values.cameratilt) <= CAM_MAX) {
				cameraTimer.cancel();
//				sendCommand(new byte[] { HOME_TILT_REAR, (byte) CAM_MAX }); //calibrate
				state.set(State.values.cameratilt, CAM_MAX);
				return;
			}
		
			sendCommand(new byte[] { CAM, (byte) state.getInteger(State.values.cameratilt) });
		}
	}
	
	private class cameraDownTask extends TimerTask {
		@Override
		public void run(){
			
			state.set(State.values.cameratilt, state.getInteger(State.values.cameratilt) + CAM_NUDGE);
			
			if (state.getInteger(State.values.cameratilt) >= CAM_MIN) {
				cameraTimer.cancel();
//				application.gyroport.sendCommand(new byte[] { HOME_TILT_FRONT }); // calibrate
				state.set(State.values.cameratilt, CAM_MIN);
				return;
			}
	
			sendCommand(new byte[] { CAM, (byte) state.getInteger(State.values.cameratilt) });
		}
	}
	
	public void camCommand(cameramove move){ 

		if (state.getBoolean(State.values.autodocking)) {
			application.messageplayer("command dropped, autodocking", null, null);
			return;
		}
		
		// no camera moves in reverse except horiz, which cancels reverse mode
		if (state.getBoolean(State.values.controlsinverted) && !move.equals(cameramove.horiz)) {
			return;
		}
		
		int position;
		
		UUID camMoveID = UUID.randomUUID(); 
		
		switch (move) {
		
			case stop: 
				if(cameraTimer != null) cameraTimer.cancel();
				currentCamMoveID = camMoveID;
				camRelease(camMoveID);
				cameraTimer = null;
				return;
				
			case up:  
				cameraTimer = new java.util.Timer();  
				cameraTimer.scheduleAtFixedRate(new cameraUpTask(), 0, CAM_SMOOTH_DELAY);
				return;
			
			case down:  
				cameraTimer = new java.util.Timer();  
				cameraTimer.scheduleAtFixedRate(new cameraDownTask(), 0, CAM_SMOOTH_DELAY);
				return;
			
			case horiz: 
				sendCommand(new byte[] { CAM, (byte) CAM_HORIZ });
				currentCamMoveID = camMoveID;
				camRelease(camMoveID);
				state.set(State.values.cameratilt, CAM_HORIZ);
				if (state.getBoolean(State.values.controlsinverted)) {
					state.set(State.values.controlsinverted, false);
				}
				return;
			
			case downabit: 
				position= state.getInteger(State.values.cameratilt) + CAM_NUDGE*3;
				if (position >= CAM_MIN) { 
					position = CAM_MIN;
//					sendCommand(new byte[] { HOME_TILT_FRONT }); //calibrate 
				}
				sendCommand(new byte[] { CAM, (byte) position }); 
				currentCamMoveID = camMoveID;
				camRelease(camMoveID);				 
				state.set(State.values.cameratilt, position);
				return; 
			
			case upabit: 
				position = state.getInteger(State.values.cameratilt) - CAM_NUDGE*3;
				if (position <= CAM_MAX) { 
					position = CAM_MAX;
//					sendCommand(new byte[] { HOME_TILT_REAR, (byte) CAM_MAX }); //calibrate
				}
				sendCommand(new byte[] { CAM, (byte) position }); 
				currentCamMoveID = camMoveID;
				camRelease(camMoveID);
				
				state.set(State.values.cameratilt, position);
				return;
				
//			case frontstop:  
//				sendCommand(new byte[] { HOME_TILT_FRONT });
//				state.set(State.values.cameratilt, CAM_MIN);
//				return;
//				
			case rearstop:   // legacy compatibility, same as reverse
				
			case reverse:
				sendCommand(new byte[] { CAM, (byte) CAM_REVERSE });
				currentCamMoveID = camMoveID;
				camRelease(camMoveID);
				state.set(State.values.cameratilt, CAM_REVERSE);
				state.set(State.values.controlsinverted, true);
				return;
		}
	
	}
	
	public void cameraToPosition(int position) {
		sendCommand(new byte[] { CAM, (byte) position} );
		UUID camMoveID = UUID.randomUUID(); 
		currentCamMoveID = camMoveID;
		camRelease(camMoveID);
		state.set(State.values.cameratilt, position);
		return;
	}
	
	private void camRelease(final UUID camMoveID) {
		new Thread(new Runnable() {
			public void run() {				
				Util.delay(CAM_RELEASE_DELAY);
				if (camMoveID.equals(currentCamMoveID)) {
					application.messageplayer(null, "cameratilt", state.get(State.values.cameratilt));
					sendCommand(CAMRELEASE);  
				}
			}
				
		}).start();
	}

	public void speedset(String str) { // final speeds update
		
	    try { // check for integer
	        Integer.parseInt(str); 
	        state.put(State.values.motorspeed, Integer.parseInt(str));
	    } catch(NumberFormatException e) {  // not integer
			final speeds update = speeds.valueOf(str);
			
			switch (update) {
				case slow: state.put(State.values.motorspeed, speedslow); break;
				case med: state.put(State.values.motorspeed, speedmed); break;
				case fast: state.put(State.values.motorspeed, speedfast); break;
			}
	    }

	}

	public void nudge(final direction dir) {
		
		if (settings.getBoolean(ManualSettings.developer.name())) {
			if (Application.openNIRead.depthCamGenerating || Application.stereo.stereoCamerasOn ) {
				if (Application.stereo.generating) return; // (assuming wont be using openni in this case)
				switch (dir) {
				case right:
				case left:  rotate(dir, 15); break;
				case forward: 
				case backward: movedistance(dir, 0.4); break;
				}
				return;
			}
		}
		
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
				
				Util.delay((int) voltsComp(n));

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
				
				double n = fullrotationdelay * degrees / 360;
				
				if (settings.getBoolean(ManualSettings.developer.name())) {
					if (Application.openNIRead.depthCamGenerating) { // openni
						short[] depthFrameBefore = Application.openNIRead.readFullFrame();						
						if (Mapper.map.length==0)  Mapper.addMove(depthFrameBefore, 0, 0); 
						if (!state.getBoolean(State.values.odometry)) odometryStart();
						state.delete(State.values.distanceanglettl);
					}
					
					if (Application.stereo.stereoCamerasOn) {
						if (Mapper.map.length==0) {
							short cells[][] = Application.stereo.captureTopViewShort(Mapper.mapSingleHeight);
							Mapper.addArcPath(cells, 0, 0);
						}
						if (!state.getBoolean(State.values.odometry)) odometryStart();
						state.delete(State.values.distanceanglettl);
					}
				}
				
				switch (dir) {
					case right: turnRight(); break;
					case left: turnLeft(); 
				}
				
				Util.delay((int) voltsComp(n));

				stopGoing();
				
				String msg = "";
				if (settings.getBoolean(ManualSettings.developer.name())) {

					if (Application.openNIRead.depthCamGenerating) { // openni 
						Util.delay(500); // allow for slow to stop
						short[] depthFrameAfter = Application.openNIRead.readFullFrame();
	
						while (!state.exists(State.values.distanceanglettl.toString())) { } //wait TODO: add timer
						double angle = Double.parseDouble(state.get(State.values.distanceanglettl).split(" ")[1]); 
						Mapper.addMove(depthFrameAfter, 0, angle);
						msg += "angle moved via gyro: "+angle;
					}
					
					if (Application.stereo.stereoCamerasOn) {
						Util.delay(700); // allow extra 200ms for latest frame 	
	
						while (!state.exists(State.values.distanceanglettl.toString())) { } //wait TODO: add timer
						double angle = Double.parseDouble(state.get(State.values.distanceanglettl).split(" ")[1]); 
						msg += "angle moved via gyro: "+angle;
						
						short cells[][] = Application.stereo.captureTopViewShort(Mapper.mapSingleHeight);
						Mapper.addArcPath(cells, 0, angle);
					}
				}
				
				state.put(State.values.motorspeed, tempspeed);
				application.message(msg, "motion", "stopped");
				
			}
		}).start();
	}
	
	public void movedistance(final direction dir, final double meters) {
		new Thread(new Runnable() {
			public void run() {
				
				final int tempspeed = state.getInteger(State.values.motorspeed);
				state.put(State.values.motorspeed, speedfast);
				
				double n = onemeterdelay * meters;
				
				// openni
				short[] depthFrameBefore = null;
				short[] depthFrameAfter = null;
				
				// stereo
				short[][] cellsBefore = null;
				short[][] cellsAfter = null;
				
				
				switch (dir) {
					case forward:
						
						if (settings.getBoolean(ManualSettings.developer.name())) {
							if (Application.openNIRead.depthCamGenerating) {   // openni
								depthFrameBefore = Application.openNIRead.readFullFrame();	
								if (Mapper.map.length==0)  Mapper.addMove(depthFrameBefore, 0, 0);
	        					if (!state.getBoolean(State.values.odometry)) odometryStart();
	        					state.delete(State.values.distanceanglettl);
							}
							
	                        if (Application.stereo.stereoCamerasOn) { // stereo
	        					if (Mapper.map.length==0) {
	                                cellsBefore = Application.stereo.captureTopViewShort(Mapper.mapSingleHeight);
	        						Mapper.addArcPath(cellsBefore, 0, 0);
	        					}
	        					if (!state.getBoolean(State.values.odometry)) odometryStart();
	        					state.delete(State.values.distanceanglettl);
	                        }
						}

                        goForward(); 
						break;
						
					case backward: 
						if (settings.getBoolean(ManualSettings.developer.name())) {
							if (Application.openNIRead.depthCamGenerating) {  // openni
								depthFrameAfter = Application.openNIRead.readFullFrame();						
								if (Mapper.map.length==0)  Mapper.addMove(depthFrameAfter, 0, 0);
	        					if (!state.getBoolean(State.values.odometry)) odometryStart();
	        					state.delete(State.values.distanceanglettl);
							}
							
	                        if (Application.stereo.stereoCamerasOn) { // stereo
	        					if (Mapper.map.length==0) {
	        						cellsAfter = Application.stereo.captureTopViewShort(Mapper.mapSingleHeight);
	        						Mapper.addArcPath(cellsAfter, 0, 0);
	        					}
	        					if (!state.getBoolean(State.values.odometry)) odometryStart();
	        					state.delete(State.values.distanceanglettl);
	    					}
						}
                        
						goBackward(); 
				}
				
				Util.delay((int) voltsComp(n));

				stopGoing();
				
				String msg = null;
				
				if (settings.getBoolean(ManualSettings.developer.name())) {

					if (depthFrameBefore != null) { // went forward, openni
						Util.delay(750); // allow for slow to stop
	
						while (!state.exists(State.values.distanceanglettl.toString())) { } //wait TODO: add timer
						double angle = Double.parseDouble(state.get(State.values.distanceanglettl).split(" ")[1]); 
						
						depthFrameAfter = Application.openNIRead.readFullFrame();
						
	                    int distance = Integer.parseInt(state.get(State.values.distanceanglettl).split(" ")[0]);
						msg = "distance moved d: "+distance+", angle:"+angle;
						Mapper.addMove(depthFrameAfter, distance, angle);
					}
					else if (depthFrameAfter != null) { // went backward, openni
						Util.delay(750);
						
						while (!state.exists(State.values.distanceanglettl.toString())) { } //wait TODO: add timer
						double angle = Double.parseDouble(state.get(State.values.distanceanglettl).split(" ")[1]); 
						
						depthFrameBefore = Application.openNIRead.readFullFrame();
						
	                    int distance = Integer.parseInt(state.get(State.values.distanceanglettl).split(" ")[0]);
						msg = "distance moved d: "+distance+", angle:"+angle;
						Mapper.addMove(depthFrameBefore, distance, angle);
					}
	
	                else if (Application.stereo.stereoCamerasOn) {
	                    Util.delay(750); // might need bit extra to get latest frame?
	
						while (!state.exists(State.values.distanceanglettl.toString())) { } //wait TODO: add timer
						double angle = Double.parseDouble(state.get(State.values.distanceanglettl).split(" ")[1]); 
	                    int distance = Integer.parseInt(state.get(State.values.distanceanglettl).split(" ")[0]);
	                    
	                    if (dir.equals(direction.forward)) { // went forward, stereo
	                        cellsAfter = Application.stereo.captureTopViewShort(Mapper.mapSingleHeight);
	    					Mapper.addArcPath(cellsAfter, distance, angle);
	    					msg = "moved forward: "+distance+"mm, "+angle+"deg";
	                    }
	                    else { // went backward, stereo
	                        cellsBefore = Application.stereo.captureTopViewShort(Mapper.mapSingleHeight);
	    					Mapper.addArcPath(cellsBefore, distance, -angle);
	    					msg = "moved backward: "+distance+"mm, -"+angle+"deg";
	                    }
	
	                }
				}
				
				state.put(State.values.motorspeed, tempspeed);
				application.message(msg, "motion", "stopped");
				
			}
		}).start();
	}
	
	/**
	 * compensates timer for drooping system voltage
	 * @param n original milliseconds
	 * @return modified (typically extended) milliseconds
	 */
	private double voltsComp(double n) {
		double volts = 12.0;
		final double nominalvolts = 12.0;
		final double exponent = 1.6;

		if (state.exists(State.values.battvolts.toString())) {
			volts = Double.parseDouble(state.get(State.values.battvolts));
		}
		
		n = n * Math.pow(nominalvolts/volts, exponent);
		return n;
	}
	
	public void delayWithVoltsComp(int n) {
		int delay = (int) voltsComp((double) n);
		Util.delay(delay);
	}

	public void clickSteer(final int x, int y) {
		
		clickCam(y);
		clickNudge(x);
	}

	private void clickNudge(final Integer x) {
		
		final double mult = Math.pow(((320.0 - (Math.abs(x))) / 320.0), 3)* clicknudgemomentummult + 1.0;
		final int clicknudgedelay = (int) ((maxclicknudgedelay * (Math.abs(x)) / 320) * mult);
		
		new Thread(new Runnable() {	
			public void run() {
				try {
					
					final int tempspeed = state.getInteger(State.values.motorspeed);
					state.put(State.values.motorspeed, speedfast);
					
					if (x > 0) turnRight();
					else turnLeft();
					
					Util.delay((int) voltsComp(clicknudgedelay));
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
		final UUID moveID = UUID.randomUUID();
		currentMoveID = moveID;

		state.put(State.values.moving, false);
		state.put(State.values.movingforward, false);
		if (settings.getBoolean(GUISettings.muteonrovmove) && state.getBoolean(State.values.moving)) application.unmuteROVMic();

		// needs deaccel!
//		if (state.getBoolean(State.values.stopbetweenmoves)) sendCommand(HARD_STOP);
//		else sendCommand(STOP);
		
		sendCommand(STOP);
		
		if (!state.getBoolean(State.values.stopbetweenmoves)) { // firmware can call stop when odometry running
			state.set(State.values.direction, direction.stop.toString());
		}
		else {
//			state.set(State.values.direction, direction.unknown.toString()); // TODO: try omitting
			new Thread(new Runnable() {public void run() {
				Util.delay(1000);
				if (currentMoveID.equals(moveID))  {
					state.set(State.values.direction, direction.stop.toString());
				}
				
			} }).start();
		}
	}
	
	private void clickCam(final Integer y) {
		if (state.getBoolean(State.values.autodocking)) return;
		
		int n = maxclickcam * y / 240;
		n = state.getInteger(State.values.cameratilt) +n;

		if (n > CAM_MIN) { n= CAM_MIN; }
		if (n < CAM_MAX) { n= CAM_MAX; }
		if (n == 13 || n== 10) { n=12; }
		sendCommand(new byte[] { CAM, (byte) n });
		UUID camMoveID = UUID.randomUUID(); 
		currentCamMoveID = camMoveID;
		camRelease(camMoveID);
		state.set(State.values.cameratilt, n);

	}
	
	public void setSteeringComp(String str) {
		if (str.contains("L")) { // left is negative
			steeringcomp = Integer.parseInt(str.replaceAll("\\D", "")) * -1;
		}
		else { steeringcomp = Integer.parseInt(str.replaceAll("\\D", "")); }
		steeringcomp = (int) ((double) steeringcomp * 255/100);
	}
	
	public void odometryStart() {
		sendCommand(ODOMETRY_START);
		state.delete(State.values.distanceangle);
		state.set(State.values.odometry, true);
	}
	public void odometryStop() {
		sendCommand(ODOMETRY_STOP_AND_REPORT);
		state.set(State.values.odometry, false);
	}
	public void odometryReport() {
		sendCommand(ODOMETRY_REPORT);
	}

	public void setCameraStops(int h, int r) {
		if (h != CAM_HORIZ) {
			settings.writeSettings(GUISettings.camhoriz.name(), h);
			sendCommand(new byte[] { CAMHORIZSET,  (byte) h }); // writes to eeprom for horiz-on-reset
			CAM_HORIZ = h;
		}
		if (r != CAM_REVERSE) {
			settings.writeSettings(GUISettings.camreverse.name(), r);
			CAM_REVERSE = r;
		}
		
//		CAM_MAX = CAM_HORIZ - 50; // 20; 
//		CAM_MIN = CAM_HORIZ + 30; // 100;
//		CAM_REVERSE = CAM_HORIZ + 68; // + 82 for bradz bot!
		
		double servoResolutionComp =  (CAM_REVERSE - CAM_HORIZ)/68.0;
		CAM_MAX = (int) (CAM_HORIZ - 45 * servoResolutionComp); 
		CAM_MIN = (int) (CAM_HORIZ + 25 * servoResolutionComp); // 100;
	}
	

}

