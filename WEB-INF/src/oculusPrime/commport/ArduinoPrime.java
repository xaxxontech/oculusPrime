package oculusPrime.commport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TooManyListenersException;

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

	// toggle to see bytes sent in log 
	public static final boolean DEBUGGING = true;
	
	public enum direction { stop, right, left, forward, backward };
	public enum cameramove { stop, up, down, horiz, upabit, downabit, frontstop, rearstop, frontstopblocking };
	public enum speeds { slow, med, fast }; // better motors, maybe add speeds? 
	public enum mode { on, off };

	public static final long DEAD_TIME_OUT = 30000;
	public static final int WATCHDOG_DELAY = 5000;	
	public static final long RESET_DELAY = 14400000; // 4 hrs
	public static final long DOCKING_DELAY = 1000;
	public static final int SETUP = 4000;
	public static final int BAUD = 115200;
	
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
	public static final String FIRMWARE_ID = "oculusPrime";

	private static final long STROBEFLASH_MAX = 5000; //stobe timeout

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
	protected final double nominalsysvolts = 12.0;
	
	// data buffer 
	protected byte[] buffer = new byte[32];
	protected int buffSize = 0;
	
	// thread safety 
	protected volatile boolean isconnected = false;
	public volatile boolean sliding = false;
	
//	private boolean invertswap = false;
	
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
	public int onemeterdelay = settings.getInteger(GUISettings.onemeterdelay);
	public int steeringcomp = 0;
	
	public String portname = settings.readSetting(ManualSettings.motorport);
	
    private static final int TURNBOOST = 25; 
	public int speedfast = 255;
	public int turnspeed = 255;
		
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
		setSpotLightBrightness(0);
		floodLight(0);
		sendCommand(FIND_HOME_TILT); 
		sendCommand(new byte[]{CAM, (byte) CAM_HORIZ});
		state.set(State.values.cameratilt, CAM_HORIZ);
		
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
				
//				if (isconnected) Util.debug("watchdog",this);
				if (now - lastReset > RESET_DELAY && isconnected) Util.debug("past reset delay", this); 

//				if (now - lastRead > DEAD_TIME_OUT) {
//					application.message("motors PCB timeout, attempting reset", null, null);
//					reset();
//				}
				
				if (now - lastReset > RESET_DELAY && !state.getBoolean(oculusPrime.State.values.autodocking) && 
						state.get(oculusPrime.State.values.driver) == null && isconnected &&
						!state.getBoolean(oculusPrime.State.values.moving)) {
//						state.getInteger(oculusPrime.State.values.telnetusers) < 1 && isconnected) { //TODO: this started barfing after commit 5ddbcd7
					
					// check for autodocking = false; driver = false; telnet = false;
					// application.message("battery board periodic reset", "battery", "resetting");
					Util.log("motors board periodic reset", this);
					reset();
				}
								
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

		Util.debug("floodlight: " + target, this);

		target = target * 255 / 100;
		sendCommand(new byte[]{FLOOD_LIGHT_LEVEL, (byte)target});
	}
	
	public void setSpotLightBrightness(int target){
		state.set(State.values.spotlightbrightness, target);
		Util.debug("setSpotLightBrightness: " + target, this);
		
		target = target * 255 / 100;
		sendCommand(new byte[]{SPOT_LIGHT_LEVEL, (byte)target});

	}
	
	public void strobeflash(String mode) {
		if (mode.equalsIgnoreCase(ArduinoPrime.mode.on.toString())) {
			state.set(State.values.strobeflashon, true);
			final long strobestarted = System.currentTimeMillis();
			new Thread(new Runnable() {
				public void run() {
					try {
						while (state.getBoolean(State.values.strobeflashon)) {
							if (System.currentTimeMillis() - strobestarted > STROBEFLASH_MAX) {
								state.set(State.values.strobeflashon, false);
							}
							sendCommand(new byte[]{SPOT_LIGHT_LEVEL, (byte)0});
							sendCommand(new byte[]{FLOOD_LIGHT_LEVEL, (byte)255});
							Thread.sleep(50);
							sendCommand(new byte[]{SPOT_LIGHT_LEVEL, (byte)255});
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
		
		Util.debug("serial in: " + response, this);
		
		if(response.equals("reset")) {
			version = null;
			sendCommand(GET_VERSION);  
		} 
		
		if(response.startsWith("version:")) {
			version = response.substring(response.indexOf("version:") + 8, response.length());
			application.message(this.getClass().getName() + " version: " + version, null, null);		
		} 
	
		
		if(response.startsWith("tiltpos")) {
			String position = response.split(" ")[1];
			state.set(State.values.cameratilt, position);
			application.messageplayer(null, "cameratilt", state.get(State.values.cameratilt));
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
					Util.delay(SETUP);
					initialize();
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
		
		int speed= state.getInteger(State.values.motorspeed);
		
		//comp for voltage on slow speed 
		if (speed==speedslow) {
			speed = (int) voltsComp((double) speed);
			if (speed > 255) { speed = 255; }
			Util.debug("speed (slow) = "+speed, this);
		}
		
		// no full speed when on dock voltage
		if (state.get(State.values.dockstatus).equals(AutoDock.DOCKED) && speed==speedfast) {
			speed = speedmed;
		}
		
		// send un-comped forward command to get wheels moving
		sendCommand(new byte[] { FORWARD, (byte) speed, (byte) speed});

		final int spd = speed;
		
		if (steeringcomp != 0) {
			new Thread(new Runnable() {
				public void run() {
					Util.delay(95); // needs to be less than docking moves
					
					int L = spd;
					int R = spd;
					
					int comp = (int) ((double) steeringcomp * Math.pow((double) spd/(double) speedfast, 2.0));
					
					if (state.getBoolean(State.values.controlsinverted)) {
						if (steeringcomp < 0) L += comp; // left motor reduced
						else if (steeringcomp > 0) R -= comp; // right motor reduced
					}
					else {
						if (steeringcomp < 0) R += comp; // right motor reduced
						else if (steeringcomp > 0) L -= comp; // left motor reduced
					}
					
					sendCommand(new byte[] { FORWARD, (byte) R, (byte) L});					}
			}).start();
		
		}

		state.put(State.values.moving, true);
		state.put(State.values.movingforward, true);
		if (state.getBoolean(State.values.muteOnROVmove)) application.muteROVMic();
	}

	public void goBackward() {

//		if (state.getBoolean(State.values.controlsinverted) &! invertswap) 
//			{ invertswap = true; goForward(); }
//		invertswap = false;
	
		int speed= state.getInteger(State.values.motorspeed);

		//comp for voltage on slow speed 
		if (speed==speedslow) {
			speed = (int) voltsComp((double) speed);
			if (speed > 255) { speed = 255; }
		}

		sendCommand(new byte[] { BACKWARD, (byte) speed, (byte) speed });
		
		final int spd = speed;
		
		if (steeringcomp != 0) {
			new Thread(new Runnable() {
				public void run() {
					Util.delay(95); //needs to be less than docking moves
					
					int L = spd;
					int R = spd;
					
					int comp = (int) ((double) steeringcomp * Math.pow((double) spd/(double) speedfast, 2.0));
					
					if (state.getBoolean(State.values.controlsinverted)) {
						if (steeringcomp < 0) R += comp; // right motor reduced
						else if (steeringcomp > 0) L -= comp; // left motor reduced
					}
					else {
						if (steeringcomp < 0) L += comp; // left motor reduced
						else if (steeringcomp > 0) R -= comp; // right motor reduced
					}	
					
					sendCommand(new byte[] { BACKWARD, (byte) R, (byte) L});		
				}
			}).start();
		
		}

		
		state.put(State.values.moving, true);
		state.put(State.values.movingforward, false);
		if (state.getBoolean(State.values.muteOnROVmove)) application.muteROVMic();
	}

	public void turnRight() {

//		if (state.getBoolean(State.values.controlsinverted) && !invertswap) 
//			{ invertswap = true; turnLeft(); }
//		invertswap = false;
		
		int tmpspeed = turnspeed;
		int boost = TURNBOOST;
		int speed = state.getInteger(State.values.motorspeed);
		if (speed < turnspeed && (speed + boost) < speedfast)
			tmpspeed = speed + boost;
		
		sendCommand(new byte[] { RIGHT,  (byte) tmpspeed, (byte) tmpspeed });
		state.put(State.values.moving, true);
		if (state.getBoolean(State.values.muteOnROVmove)) application.muteROVMic();
	}

	public void turnLeft() {
		
//		if (state.getBoolean(State.values.controlsinverted) && !invertswap) 
//			{ invertswap = true; turnRight(); }
//		invertswap = false;		
		
		int tmpspeed = turnspeed;
		int boost = TURNBOOST;
		int speed = state.getInteger(State.values.motorspeed);
		if (speed < turnspeed && (speed + boost) < speedfast)
			tmpspeed = speed + boost;
		
		sendCommand(new byte[] { LEFT, (byte) tmpspeed, (byte) tmpspeed });
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
			
			case frontstopblocking:
				sendCommand(FIND_HOME_TILT); 
				return;
		}
	
	}
	
	public void cameraToPosition(int position) {
		if (position < CAM_MIN) { position = CAM_MIN; }
		else if (position > CAM_MAX) { position = CAM_MAX; } 
		if (position == 13) { position = 12; } // can't send 13, firmware will be seen as end of command
		sendCommand(new byte[] { CAM, (byte) position} );
		state.set(State.values.cameratilt, position);
		return;
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

		if (Application.openNIRead.depthCamGenerating) {
			switch (dir) {
			case right:
			case left:  rotate(dir, 15); break;
			case forward: 
			case backward: movedistance(dir, 0.4); break;
			}
			return;
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
				
				short[] depthFrameBefore = null;
				if (Application.openNIRead.depthCamGenerating) {
					depthFrameBefore = Application.openNIRead.readFullFrame();						
					if (Mapper.map.length==0)  ScanUtils.addFrameToMap(depthFrameBefore, 0, 0); 
				}
				
				switch (dir) {
					case right: turnRight(); break;
					case left: turnLeft(); 
				}
				
				Util.delay((int) voltsComp(n));

				stopGoing();
				
				String msg = "";
				if (depthFrameBefore != null) { 
					Util.delay(500); // allow for slow to stop
					short[] depthFrameAfter = Application.openNIRead.readFullFrame();
					
//					double angle = ScanUtils.findAngle(depthFrameBefore, depthFrameAfter, degrees);
//					if (Math.abs(angle) > Math.abs(degrees) * 1.7 || Math.abs(angle) < Math.abs(degrees) * 0.3) {
//						msg += "findAngle found: "+angle+", trying findAngleTopView<br>";
//						angle = ScanUtils.findAngleTopView(depthFrameBefore, depthFrameAfter, (int) degrees); 
//					}
//					if (Math.abs(angle) > Math.abs(degrees) * 1.7 || Math.abs(angle) < Math.abs(degrees) * 0.3) {
//						angle = degrees;
//					}
					
					double guessedAngle = degrees;
					if (dir == direction.right) guessedAngle = -guessedAngle; 
					double angle = ScanUtils.findAngleTopView(depthFrameBefore, depthFrameAfter, (int) guessedAngle);
					if (angle == 9999 || Math.abs(angle - guessedAngle) >= Math.abs(guessedAngle/2) ||
							Math.abs(angle) < Math.abs(guessedAngle/6))  {
						angle=ScanUtils.findAngle(depthFrameBefore, depthFrameAfter, guessedAngle);
						msg += "no dice, trying findAngle... ";
					}
					if (angle == 9999 || Math.abs(angle) < Math.abs(guessedAngle/8))  angle = guessedAngle;
					
					msg += "angle moved: "+angle;
					ScanUtils.addFrameToMap(depthFrameAfter, 0, angle);
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
				
				short[] depthFrameBefore = null;
				short[] depthFrameAfter = null;
				
				switch (dir) {
					case forward:
						if (Application.openNIRead.depthCamGenerating) {   
							depthFrameBefore = Application.openNIRead.readFullFrame();	
							if (Mapper.map.length==0)  ScanUtils.addFrameToMap(depthFrameBefore, 0, 0); 
						}
						goForward(); 
						break;
					case backward: 
						if (Application.openNIRead.depthCamGenerating) {  
							depthFrameAfter = Application.openNIRead.readFullFrame();						
							if (Mapper.map.length==0)  ScanUtils.addFrameToMap(depthFrameBefore, 0, 0); 
						}
						goBackward(); 
				}
				
				Util.delay((int) voltsComp(n));

				stopGoing();
				
				String msg = null;
				
				if (depthFrameBefore != null) { // went forward
					Util.delay(750); // allow for slow to stop
					depthFrameAfter = Application.openNIRead.readFullFrame();
					double[] moved = ScanUtils.findDepth(depthFrameBefore, depthFrameAfter, (int)(meters*1000));
					msg = "distance moved d: "+(int) moved[0]+", angle:"+(int) moved[1] +
							", best avg: "+moved[2];
					ScanUtils.addFrameToMap(depthFrameAfter, (int) moved[0], moved[1]);
				}
				else if (depthFrameAfter != null) { // went backward
					Util.delay(750);
					depthFrameBefore = Application.openNIRead.readFullFrame();
					double[] moved = ScanUtils.findDepth(depthFrameBefore, depthFrameAfter, (int)(meters*1000));
					msg = "distance moved d: -"+(int) moved[0]+", angle: "+(int) moved[1] +
							", best avg: "+moved[2];	
					ScanUtils.addFrameToMap(depthFrameAfter, (int) moved[0], moved[1]);
				}
				
				state.put(State.values.motorspeed, tempspeed);
				application.message(msg, "motion", "stopped");
				
			}
		}).start();
	}
	
	private double voltsComp(double n) {
		double sysvolts = 12.0;
		if (state.exists(State.values.sysvolts.toString())) {
			sysvolts= Double.parseDouble(state.get(State.values.sysvolts));
		}
		
		n = n * Math.pow(nominalsysvolts/sysvolts, 2.5);
		return n;
	}
	
	public void delayWithVoltsComp(int n) {
		int delay = (int) voltsComp((double) n);
		Util.debug("delay = "+delay, this);
		Util.delay(delay);
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
		sendCommand(STOP);
		state.put(State.values.moving, false);
		state.put(State.values.movingforward, false);
		if (state.getBoolean(State.values.muteOnROVmove) && state.getBoolean(State.values.moving)) application.unmuteROVMic();
	}
	
	private void clickCam(final Integer y) {
		
		Util.debug("___clickCam(): " + "y: " + y, this);
		
		int n = maxclickcam * y / 240;
		n = state.getInteger(State.values.cameratilt) -n;
//		state.set(State.values.cameratilt, state.getInteger(State.values.cameratilt) - n);
		
		// range check 
//		if (state.getInteger(State.values.cameratilt) < CAM_MIN) state.set(State.values.cameratilt, CAM_MIN);
//		if (state.getInteger(State.values.cameratilt) > CAM_MAX) state.set(State.values.cameratilt, CAM_MAX);
		if (n < CAM_MIN) { n= CAM_MIN; }
		if (n > CAM_MAX) { n= CAM_MAX; }
		if (n == 13) { n=12; }
		sendCommand(new byte[] { CAM, (byte) n });	
		
		state.set(State.values.cameratilt, n);
		application.messageplayer(null, "cameratilt", state.get(State.values.cameratilt));

	}
	
	public void setSteeringComp(String str) {
		if (str.contains("L")) { // left is negative
			steeringcomp = Integer.parseInt(str.replaceAll("\\D", "")) * -1;
		}
		else { steeringcomp = Integer.parseInt(str.replaceAll("\\D", "")); }
		steeringcomp = (int) ((double) steeringcomp * 255/100);
	}

}

