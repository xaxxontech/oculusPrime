package oculusPrime.commport;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortException;
import jssc.SerialPortList;
import oculusPrime.Application;
import oculusPrime.AutoDock;
import oculusPrime.GUISettings;
import oculusPrime.ManualSettings;
import oculusPrime.Settings;
import oculusPrime.State;
import oculusPrime.Util;
import developer.depth.Mapper;

/**
 *  Communicate with the MALG board 
 */
public class ArduinoPrime  implements jssc.SerialPortEventListener {

	public enum direction { stop, right, left, forward, backward, unknown };
	public enum cameramove { stop, up, down, horiz, upabit, downabit, rearstop, reverse };
	public enum speeds { slow, med, fast };  
	public enum mode { on, off };

	public static final long DEAD_TIME_OUT = 20000;
	public static final int WATCHDOG_DELAY = 8000;	
	public static final long RESET_DELAY = (long) (Util.ONE_HOUR*4.5); // 4 hrs
	public static final long DOCKING_DELAY = 1000;
	public static final int DEVICEHANDSHAKEDELAY = 2000;
	public static final int BAUD = 115200;
	public static final long ALLOW_FOR_RESET = 10000;
	
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
	public static final int LINEAR_STOP_DELAY = 750;
	public static final int TURNING_STOP_DELAY = 500;

	private static final long STROBEFLASH_MAX = 5000; //strobe timeout
	private static final int ACCEL_DELAY = 75;
	private static final int COMP_DELAY = 500;

	protected long lastSent = System.currentTimeMillis();
	protected long lastRead = System.currentTimeMillis();
	protected long lastReset = System.currentTimeMillis();
	protected static State state = State.getReference();
	protected Application application = null;
	
	protected static Settings settings = Settings.getReference();
	
	protected static SerialPort serialPort = null;
	// data buffer 
	protected byte[] buffer = new byte[256];
	protected int buffSize = 0;
	
	// thread safety 
	protected volatile boolean isconnected = false;
//	public volatile boolean sliding = false;
	protected volatile long currentMoveID;
	protected volatile long currentCamMoveID;
	private boolean camLimitOverride = false;
	
//	private boolean invertswap = false;
	
	// tracking motor moves 
	private Timer cameraTimer = null;
	private double lastodomangle = 0; // degrees
	private int lastodomlinear = 0; // mm
	
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
	
    private static final int TURNBOOST = 25; 
	public static final int speedfast = 255;

	private volatile List<Byte> commandList = new ArrayList<>();

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

		setSteeringComp(settings.readSetting(GUISettings.steeringcomp));
		state.put(State.values.direction, direction.stop.name()); // .toString());

		state.put(State.values.odomturnpwm, speedmed);
		state.put(State.values.odomlinearpwm, speedmed);
		state.set(State.values.odomupdated, false);
		
		setCameraStops(CAM_HORIZ, CAM_REVERSE);

		new CommandSender().start();

		if(!settings.readSetting(ManualSettings.motorport).equals(Settings.DISABLED)) connect();
		initialize();
		camCommand(ArduinoPrime.cameramove.horiz); // in case board hasn't reset
		new WatchDog().start();

	}
	
	public void initialize() {
		
		Util.debug("initialize", this);
		
//		cameraToPosition(CAM_HORIZ);
//		setSpotLightBrightness(0);
//		floodLight(0);
		
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
			
//			Util.delay(Util.ONE_MINUTE);
			
			while (true) {
				long now = System.currentTimeMillis();
				
//				if (now - lastReset > RESET_DELAY && isconnected) Util.debug(FIRMWARE_ID+" PCB past reset delay", this);
				
				if (now - lastReset > RESET_DELAY && !state.getBoolean(oculusPrime.State.values.autodocking) && 
						state.get(oculusPrime.State.values.driver) == null && isconnected &&
						state.getInteger(oculusPrime.State.values.telnetusers) == 0 &&
						!state.getBoolean(oculusPrime.State.values.moving)) {
					Util.log(FIRMWARE_ID+" PCB periodic reset", this);
					lastReset = now;
					reset();
				}
				
				if (now - lastRead > DEAD_TIME_OUT && isconnected) {
					application.message(FIRMWARE_ID+" PCB timeout, attempting reset", null, null);
					Util.log(FIRMWARE_ID + " PCB timeout, attempting reset", this);
					lastRead = now;
					reset();
				}
				
//				if (now - lastSent > WATCHDOG_DELAY && isconnected)  sendCommand(PING);			
				sendCommand(PING); // expect "" response

				Util.delay(WATCHDOG_DELAY);
			}		
		}
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
		if (i==0) i=100;
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

		if (!state.getBoolean(State.values.odometry)) Util.debug("serial in: " + response, this);
		
		if(response.equals("reset")) {
			sendCommand(GET_VERSION);  
			Util.debug(FIRMWARE_ID+" "+response, this);
		} 
		
		if(response.startsWith("version:")) {
			String version = response.substring(response.indexOf("version:") + 8, response.length());
			application.message("malg board firmware version: " + version, null, null);		
		} 
	
		String[] s = response.split(" ");

		if (s[0].equals("moved")) {
			lastodomlinear = (int) (Double.parseDouble(s[1]) * Math.PI * settings.getInteger(ManualSettings.wheeldiameter));
			lastodomangle = Double.parseDouble(s[2]);
//			a *= state.getDouble(State.values.gyrocomp.toString()); // apply comp
			lastodomangle *= settings.getDouble(ManualSettings.gyrocomp.toString());
			
			state.set(State.values.distanceangle, lastodomlinear +" "+lastodomangle); //millimeters, degrees
			state.set(State.values.odomupdated, true);
			
			// TODO: testing only ----------------
			if (settings.getBoolean(ManualSettings.developer.name())) {
				if (Application.openNIRead.depthCamGenerating) {
					if (!state.exists(State.values.distanceanglettl.toString())) {
						state.set(State.values.distanceanglettl, "0 0");
					}
					
					int dttl = Integer.parseInt(state.get(State.values.distanceanglettl).split(" ")[0]);
					double attl = Double.parseDouble(state.get(State.values.distanceanglettl).split(" ")[1]);
					dttl += lastodomlinear;
					attl += lastodomangle;
					String dattl = dttl+" "+attl;
					state.set(State.values.distanceanglettl,dattl);
				}
			}
			// end of testing only ----------------
		}
		else if (s[0].equals("stop") && state.getBoolean(State.values.stopbetweenmoves)) 
			state.set(State.values.direction, direction.stop.toString());
		else if (s[0].equals("stopdetectfail")) {
//			if (settings.getBoolean(ManualSettings.debugenabled))
				application.message("FIRMWARE STOP DETECT FAIL", null, null);
//			Util.debug("FIRMWARE STOP DETECT FAIL", this);
			if (state.getBoolean(State.values.stopbetweenmoves)) 
				state.set(State.values.direction, direction.stop.toString());
		}

	}

	/**
	 * port query and connect 
	 */
	private void connect() {
		isconnected = false;
		
		try {

	    	String[] portNames = SerialPortList.getPortNames();
	        if (portNames.length == 0) return;
	        
	        String otherdevice = "";
	        if (state.exists(State.values.powerport)) 
	        	otherdevice = state.get(State.values.powerport);
	        
	        for (int i=0; i<portNames.length; i++) {
        		if (portNames[i].matches("/dev/ttyUSB.+") && !portNames[i].equals(otherdevice)) {
        			
        			Util.log("querying port "+portNames[i], this);
        			
        			serialPort = new SerialPort(portNames[i]);
        			serialPort.openPort();
        			serialPort.setParams(BAUD, 8, 1, 0);
        			Thread.sleep(DEVICEHANDSHAKEDELAY);
        			byte[] buffer = new byte[99];
        			buffer = serialPort.readBytes(); // clear serial buffer
        			
        			serialPort.writeBytes(new byte[] { GET_PRODUCT, 13 }); // query device
        			Thread.sleep(100); // some delay is required
        			buffer = serialPort.readBytes();
        			
        			if (buffer == null) break;
        			
        			String device = new String();
        			for (int n=0; n<buffer.length; n++) {
        				if((int)buffer[n] == 13 || (int)buffer[n] == 10) { break; }
        				if(Character.isLetter((char) buffer[n]))
        					device += (char) buffer[n];
        			}
        			
        			if (device.length() == 0) break;
        			Util.debug(device+" "+portNames[i], this);
    				if (device.trim().startsWith("id")) device = device.substring(2, device.length());
    				Util.debug(device+" "+portNames[i], this);
    				
    				if (device.equals(FIRMWARE_ID)) {
    					
    					Util.log("malg connected to "+portNames[i], this);
    					
    					isconnected = true;
    					state.set(State.values.motorport, portNames[i]);
    		            serialPort.addEventListener(this, SerialPort.MASK_RXCHAR);//Add SerialPortEventListener
    					break; // don't read any more ports, time consuming
    				}
    				serialPort.closePort();
	        	}
	        }

		} catch (Exception e) {	
			Util.log("can't connect to port: " + e.getMessage(), this);
		}
			
	}

	public boolean isConnected() {
		return isconnected;
	}

	/** utility for macros requiring movement
	 *  BLOCKING
	 * 	return true if connected, if not, wait for up to 10 seconds
	 * @return   boolean isconnected
	 */
	public boolean checkisConnectedBlocking() {
		if (isconnected) return true;
		Util.log("malg not connected, waiting for reset", this);
		long start = System.currentTimeMillis();
		while (!isconnected && System.currentTimeMillis() - start < ALLOW_FOR_RESET)
			Util.delay(50);
		if (isconnected) return true;
		Util.log("malg not connected", this);
		return false;
	}

	public void serialEvent(SerialPortEvent event) {
		if (!event.isRXCHAR())  return;

		try {
			byte[] input = new byte[32];
			
			input = serialPort.readBytes();
			for (int j = 0; j < input.length; j++) {
				if ((input[j] == '>') || (input[j] == 13) || (input[j] == 10)) {
					if (buffSize > 0) execute();
					buffSize = 0; // reset
					lastRead = System.currentTimeMillis(); 	// last command from board

				}else if (input[j] == '<') {  // start of message
					buffSize = 0;
				} else {
					buffer[buffSize++] = input[j];   // buffer until ready to parse
				}
			}
			
		} catch (SerialPortException e) {
			e.printStackTrace();
		}
		
	}

	public void reset() {
		new Thread(new Runnable() {
			public void run() {
			Util.log("resetting MALG board", this);
			disconnect();
			connect();
			initialize();
			}
		}).start();
	}

	/** shutdown serial port */
	protected void disconnect() {
		try {
			isconnected = false;
			serialPort.closePort();
			state.delete(State.values.motorport);
		} catch (Exception e) {
			Util.log("error in disconnect(): " + e.getMessage(), this);
		}
	}

	/**
	 * Send a multiple byte command to send the device
	 * 
	 * @param cmd
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
		
		/*
		if(settings.getBoolean(ManualSettings.debugenabled)) {
			String text = "sendCommand(): " + (char)cmd[0] + " ";
			for(int i = 1 ; i < cmd.length ; i++) 
				text += ((byte)cmd[i] & 0xFF) + " ";  // & 0xFF converts to unsigned byte
			
			Util.log("DEBUG: "+ text, this);
		}   // */

		for (byte b : cmd)   commandList.add(b);
		commandList.add((byte) 13); // EOF

	}
	
	
	public void sendCommand(final byte cmd){
		sendCommand(new byte[]{cmd});
	}


	/** inner class to send commands to port in sequential order */
	public class CommandSender extends Thread {
		oculusPrime.State state = oculusPrime.State.getReference();

		public CommandSender() {
			this.setDaemon(true);
		}

		public void run() {

			while (true) {
				if (commandList.size() > 0 && isconnected) {
					if (commandList.get(commandList.size()-1) == (byte) 13) {

						if (commandList.size() > 15) {
							commandList.clear();
							Util.log("error, command stack up, all dropped", this);
							continue;
						}

						Byte[] commands = new Byte[commandList.size()];
						commandList.toArray(commands);
						commandList.clear();

						try {

							byte c[] = new byte[commands.length];
							for (int i = 0; i < commands.length; i++) c[i]=commands[i];

							// track last write
							lastSent = System.currentTimeMillis();

							serialPort.writeBytes(c); // writing as array ensures goes at baud rate?


						} catch (Exception e) {
							for (int i = commands.length-1; i>=0; i--) commandList.add(0, commands[i]); //save command for after reset
							Util.log("sendCommand() error, attempting reset", this);
							Util.printError(e);
							lastReset = System.currentTimeMillis();
							reset(); // this will (help) resets happen quicker, not just on watchdog intervals
						}

					}
					else Util.log("error, warning no EOF char", this);
				}

				Util.delay(1);


			}
		}
	}

	public void goForward() {
		
		final long moveID = System.nanoTime();
		currentMoveID = moveID;
		
		if (state.getBoolean(State.values.stopbetweenmoves)) {
				
			if ( !state.get(State.values.direction).equals(direction.stop.toString()) ) {
			
				stopGoing();
				currentMoveID = moveID;

				new Thread(new Runnable() {public void run() {
					long stopwaiting = System.currentTimeMillis()+1000;
					while(!state.get(State.values.direction).equals(direction.stop.toString()) &&
							System.currentTimeMillis() < stopwaiting) { Util.delay(1); } // wait
					if (currentMoveID == moveID)  goForward();
					
				} }).start();
				
				return;
			}
		}

		state.put(State.values.moving, true);
		state.put(State.values.movingforward, true);
		
		int speed1 = (int) voltsComp((double) speedslow);
		if (speed1 > 255) { speed1 = 255; }
		
//		int speed2= state.getInteger(State.values.motorspeed);
		
		int speed2;
		if (state.exists(State.values.odomlinearmpms.toString())) {
			speed2 = state.getInteger(State.values.odomlinearpwm); 
			tracklinearrate(moveID);
		}
		else speed2= state.getInteger(State.values.motorspeed);
		
		
	
		if (speed2<speed1) { 		// voltcomp on slow speed only
			speed2 = (int) voltsComp((double) speed2);
			if (speed2 > 255) { speed2 = 255; }
		}
		
		// no full speed when on dock voltage
		if (state.get(State.values.dockstatus).equals(AutoDock.DOCKED) && speed2==speedfast) {
			speed2 = speedmed;
		}
		
		// if already moving forward, go full speed
		if (state.get(State.values.direction).equals(direction.forward.toString()) ) {
			int[] comp = applyComp(speed2); 
			int L, R;
			L = comp[0];
			R = comp[1];

			sendCommand(new byte[] { FORWARD, (byte) R, (byte) L});
			return;
		}
		
		// start slow, un-comped 
		sendCommand(new byte[] { FORWARD, (byte) speed1, (byte) speed1});

		final int spd = speed2;
		
		if (speed2 > speed1) { 
			new Thread(new Runnable() {
				public void run() {
					
					Util.delay(ACCEL_DELAY);
					
					if (currentMoveID != moveID)  return;

		// actual speed, un-comped
					sendCommand(new byte[] { FORWARD, (byte) spd, (byte) spd});

				} 
			}).start();
		}
		
		if (steeringcomp != 0) {  
			new Thread(new Runnable() {
				public void run() {
					Util.delay(COMP_DELAY); 

					if (currentMoveID != moveID)  return;
					
					int[] comp = applyComp(spd); // actual speed, comped
					int L,R;
					L = comp[0];
					R = comp[1];
		// actual speed, comped
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
	
		final long moveID = System.nanoTime();
		currentMoveID = moveID;

		if (state.getBoolean(State.values.stopbetweenmoves)) {
			
			if ( !state.get(State.values.direction).equals(direction.stop.toString())  ) {
			
				stopGoing();
				currentMoveID = moveID;
				
				new Thread(new Runnable() {public void run() {
					long stopwaiting = System.currentTimeMillis()+1000;
					while(!state.get(State.values.direction).equals(direction.stop.toString()) &&
							System.currentTimeMillis() < stopwaiting) { Util.delay(1); } // wait
					if (currentMoveID == moveID)  goBackward();
					
				} }).start();
				
				return;
			}
		}

		state.put(State.values.moving, true);
		state.put(State.values.movingforward, false);
//		if (settings.getBoolean(GUISettings.muteonrovmove))  application.muteROVMic();
		
		
		int speed1 = (int) voltsComp((double) speedslow);
		if (speed1 > 255) { speed1 = 255; }
		
//		int speed2= state.getInteger(State.values.motorspeed);
		int speed2;
		if (state.exists(State.values.odomlinearmpms.toString())) {
			speed2 = state.getInteger(State.values.odomlinearpwm); 
			tracklinearrate(moveID);
		}
		else speed2= state.getInteger(State.values.motorspeed);
	
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
					
					if (currentMoveID != moveID)  return;

					// actual speed, un-comped
					sendCommand(new byte[] { BACKWARD, (byte) spd, (byte) spd});

				} 
			}).start();
		}
		
		if (steeringcomp != 0) {  
			new Thread(new Runnable() {
				public void run() {
					Util.delay(COMP_DELAY); 

					if (currentMoveID != moveID)  return;
					
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
		final long moveID = System.nanoTime();
		currentMoveID = moveID;

		if (state.getBoolean(State.values.stopbetweenmoves)) {
			
			if ( !(state.get(State.values.direction).equals(direction.right.toString()) ||
				state.get(State.values.direction).equals(direction.stop.toString()) ) ) {
			
				stopGoing();
				currentMoveID = moveID;

				new Thread(new Runnable() {public void run() {
					
					long stopwaiting = System.currentTimeMillis()+1000;
					while(!state.get(State.values.direction).equals(direction.stop.toString()) &&
							System.currentTimeMillis() < stopwaiting) { Util.delay(1); } // wait
					if (currentMoveID == moveID)  turnRight();
					
				} }).start();
				
				return;
			}
		}

		state.set(State.values.direction, direction.right.toString()); // now go
		
		int tmpspeed;
		if (state.exists(State.values.odomturndpms.toString())) {
			tmpspeed = state.getInteger(State.values.odomturnpwm); 
			trackturnrate(moveID);
		}
		else tmpspeed = state.getInteger(State.values.motorspeed) + TURNBOOST;
		
		if (tmpspeed > 255) tmpspeed = 255;
		
		if (delay==0) {
			sendCommand(new byte[] { RIGHT, (byte) tmpspeed, (byte) tmpspeed });
			state.put(State.values.moving, true);
//			if (settings.getBoolean(GUISettings.muteonrovmove))  application.muteROVMic();
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
	
	/**
	 * Turn Left
	 * @param delay milliseconds, then stop (timed directly by firmware). If 0, continuous movement
	 */
	public void turnLeft(int delay) {
		final long moveID = System.nanoTime();
		currentMoveID = moveID;
		
		if (state.getBoolean(State.values.stopbetweenmoves)) {
			
			if ( !(state.get(State.values.direction).equals(direction.left.toString()) ||
				state.get(State.values.direction).equals(direction.stop.toString()) ) ) {
			
				stopGoing();
				currentMoveID = moveID;

				new Thread(new Runnable() {public void run() {
					
					long stopwaiting = System.currentTimeMillis()+1000;
					while(!state.get(State.values.direction).equals(direction.stop.toString()) &&
							System.currentTimeMillis() < stopwaiting) { Util.delay(1); } // wait
					if (currentMoveID == moveID)  turnLeft();
					
				} }).start();
				
				return;
			}
		}	
		
		state.set(State.values.direction, direction.left.toString()); // now go
		
		int tmpspeed;
		if (state.exists(State.values.odomturndpms.toString())) {
			tmpspeed = state.getInteger(State.values.odomturnpwm);
			trackturnrate(moveID);
		}
		else tmpspeed = state.getInteger(State.values.motorspeed) + TURNBOOST;
		
		if (tmpspeed > 255) tmpspeed = 255;
		
		if (delay==0) {
			sendCommand(new byte[] { LEFT, (byte) tmpspeed, (byte) tmpspeed });
			state.put(State.values.moving, true);
//			if (settings.getBoolean(GUISettings.muteonrovmove))  application.muteROVMic();
		}
		else {
        	byte d1 = (byte) ((delay >> 8) & 0xff);
			byte d2 = (byte) (delay & 0xff);
			sendCommand(new byte[] { LEFTTIMED, (byte) tmpspeed, (byte) tmpspeed, (byte) d1, (byte) d2});
		}

	}
	
	// secondspertwopi=4.2
	// secondsper 360 deg = 4.2
	// degrees per second = 360/4.2 = 85.714
	// degrees per ms = 0.0857 state odomturndpms 0.0857   state odomturnpwm 150
	// state odometrybroadcast 250      odometrystart
	
	/*
	 * to start:
	 * state odomturndpms 0.0857
	 * state odometrybroadcast 250
	 * odometrystart
	 * 
	 * monitor with:
	 * state odomturnpwm 
	 * state odomlinearpwm
	 * 
	 * state odomlinearmpms
	 * state odomturndpms
	 */
	private void trackturnrate(final long moveID) {
		
		new Thread(new Runnable() {public void run() {
			final long turnstart = System.currentTimeMillis();
			long start = turnstart;
			final double tolerance = state.getDouble(State.values.odomturndpms.toString())*0.08;
			final int pwmincr = 5;
			final int accel = 500;
			final double targetrate = state.getDouble(State.values.odomturndpms.toString());
			
			while (currentMoveID == moveID)  {

				if (state.getBoolean(State.values.odomupdated)) {
					state.set(State.values.odomupdated, false);
					
					long now = System.currentTimeMillis();
					if (now - turnstart < accel) {
						start = now;
						continue; // throw away 1st during accel, assuming broadcast interval is around 250ms
					}
					
					double rate = Math.abs(lastodomangle)/(now - start);

					int currentpwm = state.getInteger(State.values.odomturnpwm);
					int newpwm = currentpwm;

					if (rate > targetrate + tolerance) {
						newpwm = currentpwm - pwmincr;
						if (newpwm < speedslow) newpwm = speedslow;
					}
					else if (rate < targetrate - tolerance) {
						newpwm = currentpwm + pwmincr;
						if (newpwm > 255) newpwm = 255;
					}
					else // within tolerance, kill thread to save cpu
						break;
					
					// modify speed
					state.set(State.values.odomturnpwm, newpwm);
					if ( state.get(State.values.direction).equals(direction.left.toString()))
						sendCommand(new byte[] { LEFT, (byte) newpwm, (byte) newpwm });
					else 
						sendCommand(new byte[] { RIGHT, (byte) newpwm, (byte) newpwm });
					
					start = now;
					
				}
				Util.delay(1);
				
			}
		} }).start();
		
	}
	
	// example target rate = 3.2m/s = 0.00032 m/ms
	/*
	 * to start:
	 * state odomlinearmpms 0.00032
	 * state odometrybroadcast 250
	 * odometrystart
	 * 
	 * monitor with:
	 * state odomlinearpwm
	 */
	private void tracklinearrate(final long moveID) {
		
		new Thread(new Runnable() {public void run() {
			final long linearstart = System.currentTimeMillis();
			long start = linearstart;
			final double tolerance = state.getDouble(State.values.odomlinearmpms.toString()) * 0.08; //0.000015625; //  meters per ms --- 5% of target speed 0.32m/s (0.00032
			final int pwmincr = 5;
			final int accel = 480;
			final double targetrate = state.getDouble(State.values.odomlinearmpms.toString());
			
			while (currentMoveID == moveID)  {

				if (state.getBoolean(State.values.odomupdated)) {
					state.set(State.values.odomupdated, false);
					
					long now = System.currentTimeMillis();
					if (now - linearstart < accel) {
						start = now;
						continue; // throw away 1st during accel, assuming broadcast interval is around 250ms
					}
					
					double meters = lastodomlinear/1000.0;
					double rate = Math.abs(meters)/(now - start); // m per ms

					int currentpwm = state.getInteger(State.values.odomlinearpwm);
					int newpwm = currentpwm;

					if (rate > targetrate + tolerance) {
						newpwm = currentpwm - pwmincr;
						if (newpwm < speedslow) newpwm = speedslow;
					}
					else if (rate < targetrate - tolerance) {
						newpwm = currentpwm + pwmincr;
						if (newpwm > 255) newpwm = 255;
					}
					else // within tolerance, kill thread (save cpu)
						break;
					
					//modify speed
					state.set(State.values.odomlinearpwm, newpwm);

					int[] comp = applyComp(newpwm); // steering comp
					int L,R;
					byte dir;

					
					if ( state.get(State.values.direction).equals(direction.forward.toString())) {
						dir = FORWARD;
						L = comp[0];
						R = comp[1];
					} else  {
						dir = BACKWARD;
						L = comp[1];
						R = comp[0];
					}
					
					sendCommand(new byte[] { dir, (byte) R, (byte) L});
					
					start = now;
				}

				Util.delay(1);

			}
		} }).start();
		
	}
	
	private class cameraUpTask extends TimerTask {
		@Override
		public void run(){
			
			state.set(State.values.cameratilt, state.getInteger(State.values.cameratilt) - CAM_NUDGE);
			
			if (state.getInteger(State.values.cameratilt) <= CAM_MAX && !camLimitOverride) {
				cameraTimer.cancel();
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
			
			if (state.getInteger(State.values.cameratilt) >= CAM_MIN && !camLimitOverride) {
				cameraTimer.cancel();
				state.set(State.values.cameratilt, CAM_MIN);
				return;
			}
	
			sendCommand(new byte[] { CAM, (byte) state.getInteger(State.values.cameratilt) });
		}
	}
	
	public void camCommand(cameramove move){ 

// 		moved condition to application comman dswithch
//		if (state.getBoolean(State.values.autodocking)) {
//			application.messageplayer("command dropped, autodocking", null, null);
//			return;
//		}
		
		// no camera moves in reverse except horiz, which cancels reverse mode
		if (state.getBoolean(State.values.controlsinverted) && !move.equals(cameramove.horiz)) {
			return;
		}
		
		int position;
		
//		UUID camMoveID = UUID.randomUUID();
		long camMoveID = System.nanoTime();
		
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
				cameraSlowToPosition(CAM_HORIZ);
//				sendCommand(new byte[] { CAM, (byte) CAM_HORIZ });
//				currentCamMoveID = camMoveID;
//				camRelease(camMoveID);
//				state.set(State.values.cameratilt, CAM_HORIZ);
//				if (state.getBoolean(State.values.controlsinverted)) {
//					state.set(State.values.controlsinverted, false);
//				}
				return;
			
			case downabit: 
				position= state.getInteger(State.values.cameratilt) + CAM_NUDGE*3;
				if (position >= CAM_MIN) { 
					position = CAM_MIN;
				}
				cameraSlowToPosition(position);
//				sendCommand(new byte[] { CAM, (byte) position }); 
//				currentCamMoveID = camMoveID;
//				camRelease(camMoveID);				 
//				state.set(State.values.cameratilt, position);
				return; 
			
			case upabit: 
				position = state.getInteger(State.values.cameratilt) - CAM_NUDGE*3;
				if (position <= CAM_MAX) { 
					position = CAM_MAX;
				}
				cameraSlowToPosition(position);
//				sendCommand(new byte[] { CAM, (byte) position }); 
//				currentCamMoveID = camMoveID;
//				camRelease(camMoveID);
//				state.set(State.values.cameratilt, position);
				return;
				
//			case frontstop:  
//				sendCommand(new byte[] { HOME_TILT_FRONT });
//				state.set(State.values.cameratilt, CAM_MIN);
//				return;
//				
			case rearstop:   // deprecated, same as reverse
				
			case reverse:
				cameraSlowToPosition(CAM_REVERSE);
//				sendCommand(new byte[] { CAM, (byte) CAM_REVERSE });
//				currentCamMoveID = camMoveID;
//				camRelease(camMoveID);
//				state.set(State.values.cameratilt, CAM_REVERSE);
//				state.set(State.values.controlsinverted, true);
				return;
		}
	
	}
	
	public void cameraToPosition(int position) { // max movement speed
		sendCommand(new byte[] { CAM, (byte) position} );
//		UUID camMoveID = UUID.randomUUID();
		long camMoveID = System.nanoTime();
		currentCamMoveID = camMoveID;
		camRelease(camMoveID);
		state.set(State.values.cameratilt, position);
		return;
	}
	
	public void cameraSlowToPosition(final int position) { // slow movement speed
		if (state.getInteger(State.values.cameratilt) == position) return;
		
//		final UUID camMoveID = UUID.randomUUID();
		final long camMoveID = System.nanoTime();
		currentCamMoveID = camMoveID;
		camLimitOverride = true;

		if (cameraTimer != null) cameraTimer.cancel();
		cameraTimer = new java.util.Timer();

		boolean temp = false;
//		final int timercode = cameraTimer.hashCode();
		if (state.getInteger(State.values.cameratilt) > position)  { // up
			temp = true; 
			cameraTimer.scheduleAtFixedRate(new cameraUpTask(), 0, CAM_SMOOTH_DELAY);
		}
		else { 
			cameraTimer.scheduleAtFixedRate(new cameraDownTask(), 0, CAM_SMOOTH_DELAY); 
		} // down
		final boolean up = temp;
		
		new Thread(new Runnable() {
			public void run() {				
				
				while (camMoveID == currentCamMoveID) {
					int currentpos = state.getInteger(State.values.cameratilt);
					if ( (up && currentpos <= position) || (!up && currentpos >= position) ) { // position reached, stop
						if(cameraTimer != null) cameraTimer.cancel();
						camRelease(camMoveID);
						cameraTimer = null;
						break;
					}
					
					if (Math.abs(currentpos - CAM_REVERSE) < 5 ) {
						if (!state.getBoolean(State.values.controlsinverted)) 
								state.set(State.values.controlsinverted, true);
					}
					else {
						if (state.getBoolean(State.values.controlsinverted)) 
								state.set(State.values.controlsinverted, false);
					}
					Util.delay(1);
				}
				
				if (camMoveID == currentCamMoveID) {
					application.messageplayer(null, "cameratilt", state.get(State.values.cameratilt));
//					if (cameraTimer.hashCode() == timercode) cameraTimer.cancel(); // cancel this if another cam command took over
//					cameraTimer.cancel();
					camLimitOverride = false;
				}
//				else if (cameraTimer != null) {
//					if (cameraTimer.hashCode() == timercode) cameraTimer.cancel(); // cancel this if another cam command took over
//				}
				
//				camLimitOverride = false;
			}
				
		}).start();
		
	}
	
	private void camRelease(final long camMoveID) {
		new Thread(new Runnable() {
			public void run() {				
				Util.delay(CAM_RELEASE_DELAY);
				if (camMoveID == currentCamMoveID) {
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

	@SuppressWarnings("incomplete-switch")
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
				boolean movingforward = state.getBoolean(State.values.movingforward);
				
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
				
				if (!state.exists(State.values.odomturndpms.toString())) { // normal
					Util.delay((int) voltsComp(n));
				} 
				else { // continuous comp using gyro
					if (movingforward && (dir.equals(direction.right) || dir.equals(direction.left))) {
						long stopwaiting = System.currentTimeMillis()+LINEAR_STOP_DELAY;
						while( System.currentTimeMillis() < stopwaiting &&
								state.get(State.values.direction).equals(direction.forward.toString())  ) {  Util.delay(1);  } // wait for stop
					}
					Util.delay((long) (12.5 / state.getDouble(State.values.odomturndpms.toString())) );
					if (movingforward)  state.set(State.values.movingforward, true);
				}

				if (movingforward) goForward();
				else stopGoing();
				
			}
		}).start();
	}

	public void rotate(final direction dir, final int degrees) {
		new Thread(new Runnable() {
			@SuppressWarnings("incomplete-switch")
			public void run() {
				
				final int tempspeed = state.getInteger(State.values.motorspeed);
				state.put(State.values.motorspeed, speedfast);
				
				
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
				
				if (!state.exists(State.values.odomturndpms.toString())) { // normal
					double n = fullrotationdelay * degrees / 360;
					Util.delay((int) voltsComp(n));
				} 
				else { // continuous comp using gyro
					Util.delay((long) (degrees / state.getDouble(State.values.odomturndpms.toString())) );
				}

				stopGoing();
				
				String msg = "";
				if (settings.getBoolean(ManualSettings.developer.name())) {

					if (Application.openNIRead.depthCamGenerating) { // openni 
						Util.delay(500); // allow for slow to stop
						short[] depthFrameAfter = Application.openNIRead.readFullFrame();
	
						while (!state.exists(State.values.distanceanglettl.toString())) {  Util.delay(1); } //wait TODO: add timer
						double angle = Double.parseDouble(state.get(State.values.distanceanglettl).split(" ")[1]); 
						Mapper.addMove(depthFrameAfter, 0, angle);
						msg += "angle moved via gyro: "+angle;
					}
					
					if (Application.stereo.stereoCamerasOn) {
						Util.delay(700); // allow extra 200ms for latest frame 	
	
						while (!state.exists(State.values.distanceanglettl.toString())) {  Util.delay(1); } //wait TODO: add timer
						double angle = Double.parseDouble(state.get(State.values.distanceanglettl).split(" ")[1]); 
						msg += "angle moved via gyro: "+angle;
						
						short cells[][] = Application.stereo.captureTopViewShort(Mapper.mapSingleHeight);
						Mapper.addArcPath(cells, 0, angle);
					}
				}
				
				if (msg.equals("")) msg = null;
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
				case left:
					break;
				case right:
					break;
				case stop:
					break;
				case unknown:
					break;
				default:
					break; 
				}
				
				if (!state.exists(State.values.odomlinearmpms.toString())) { // normal
					double n = onemeterdelay * meters;
					Util.delay((int) voltsComp(n));
				} 
				else { // continuous comp using gyro
					Util.delay((long) (meters / state.getDouble(State.values.odomlinearmpms.toString())) );
				}

				stopGoing();
				
				String msg = null;
				
				if (settings.getBoolean(ManualSettings.developer.name())) {

					if (depthFrameBefore != null) { // went forward, openni
						Util.delay(750); // allow for slow to stop
	
						while (!state.exists(State.values.distanceanglettl.toString())) {  Util.delay(1); } //wait TODO: add timer
						double angle = Double.parseDouble(state.get(State.values.distanceanglettl).split(" ")[1]); 
						
						depthFrameAfter = Application.openNIRead.readFullFrame();
						
	                    int distance = Integer.parseInt(state.get(State.values.distanceanglettl).split(" ")[0]);
						msg = "distance moved d: "+distance+", angle:"+angle;
						Mapper.addMove(depthFrameAfter, distance, angle);
					}
					else if (depthFrameAfter != null) { // went backward, openni
						Util.delay(750);
						
						while (!state.exists(State.values.distanceanglettl.toString())) {  Util.delay(10); } //wait TODO: add timer
						double angle = Double.parseDouble(state.get(State.values.distanceanglettl).split(" ")[1]); 
						
						depthFrameBefore = Application.openNIRead.readFullFrame();
						
	                    int distance = Integer.parseInt(state.get(State.values.distanceanglettl).split(" ")[0]);
						msg = "distance moved d: "+distance+", angle:"+angle;
						Mapper.addMove(depthFrameBefore, distance, angle);
					}
	
	                else if (Application.stereo.stereoCamerasOn) {
	                    Util.delay(750); // might need bit extra to get latest frame?
	
						while (!state.exists(State.values.distanceanglettl.toString())) {  Util.delay(10); } //wait TODO: add timer
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
		double volts = 12.0; // default
		final double nominalvolts = 12.0;
		final double exponent = 1.6;

		if (state.exists(State.values.battvolts.toString())) {
			if (Math.abs(state.getDouble(State.values.battvolts.toString()) - volts) > 2) // sanity check
				Util.log("error state:battvolts beyond expected range! "+state.get(State.values.battvolts), this);
			else  volts = Double.parseDouble(state.get(State.values.battvolts));
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
		final long moveID = System.nanoTime();
		currentMoveID = moveID;

		state.put(State.values.moving, false);
		state.put(State.values.movingforward, false);
//		if (settings.getBoolean(GUISettings.muteonrovmove) && state.getBoolean(State.values.moving)) application.unmuteROVMic();

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
				if (currentMoveID == moveID)  {
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
//		UUID camMoveID = UUID.randomUUID();
		long camMoveID = System.nanoTime();
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
		state.set(State.values.stopbetweenmoves, true);
		
		if (state.exists(State.values.odometrybroadcast.toString())) { // broadcast
			new Thread(new Runnable() {public void run() {
				
				while (state.exists(State.values.odometrybroadcast.toString()) && 
							state.exists(State.values.odometry.toString())) {
					if (state.getBoolean(State.values.odometry) && 
							state.getLong(State.values.odometrybroadcast) > 0 ) {
						Util.delay(state.getLong(State.values.odometrybroadcast));
						odometryReport();
					}
					else  break;
				}
				
			} }).start();
		}
	}
	
	public void odometryStop() {
		sendCommand(ODOMETRY_STOP_AND_REPORT);
		state.set(State.values.odometry, false);
		state.set(State.values.stopbetweenmoves, false);
		state.delete(State.values.odomturndpms);
		state.delete(State.values.odomlinearmpms);
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
		
		double servoResolutionComp =  (CAM_REVERSE - CAM_HORIZ)/68.0;
		CAM_MAX = (int) (CAM_HORIZ - 45 * servoResolutionComp); 
		CAM_MIN = (int) (CAM_HORIZ + 25 * servoResolutionComp); // 100;
	}
	

}

