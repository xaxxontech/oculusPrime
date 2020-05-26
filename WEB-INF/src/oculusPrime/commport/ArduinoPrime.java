package oculusPrime.commport;

import java.util.ArrayList;
import java.util.List;

import developer.Ros;
import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortException;
import jssc.SerialPortList;
import oculusPrime.*;
import developer.depth.Mapper;

/**
 *  Communicate with the MALG board 
 */
public class ArduinoPrime  implements jssc.SerialPortEventListener {

	public enum direction { stop, right, left, forward, backward, unknown, arcright, arcleft };
	public enum cameramove { stop, up, down, horiz, upabit, downabit, rearstop, reverse };
	public enum speeds { slow, med, fast };  
	public enum mode { on, off };

	public static final double FIRMWARE_VERSION_REQUIRED = 0.129; // trailing zeros ignored!
	public static final String FIRMWARE_ID = "malg";
	public static final double MALGDB_FIRMWARE_VERSION_REQUIRED = 1.15; // trailing zeros ignored!
	public static final String MALGDB_FIRMWARE_ID = "malgdb";
	public static String boardid = "unknown";
	public static final long DEAD_TIME_OUT = 20000;
	public static final int WATCHDOG_DELAY = 8000;
	public static final long RESET_DELAY = (long) (Util.ONE_HOUR*4.5); // 4 hrs
	public static final long DOCKING_DELAY = 1000;
	public static final int DEVICEHANDSHAKEDELAY = 2000;
	public static final int BAUD = 115200;
	public static final long ALLOW_FOR_RESET = 10000;
	public static final int FIRMWARE_TIMED_OFFSET = 10;
	
	public static final byte STOP = 's';
	public static final byte FORWARD = 'f';
	public static final byte BACKWARD = 'b';
	public static final byte LEFT = 'l';
	public static final byte RIGHT = 'r';
	public static final byte LEFTTIMED = 'z';
	public static final byte RIGHTTIMED = 'e';
	public static final byte FORWARDTIMED = 't';
	public static final byte BACKWARDTIMED = 'u';
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
	public static final long CAMHOLD_RELEASE_DELAY = Util.FIVE_MINUTES;

	public static final int LINEAR_STOP_DELAY = 750;
	public static final int TURNING_STOP_DELAY = 500;

	private static final long STROBEFLASH_MAX = 5000; //strobe timeout
	public static final int ACCEL_DELAY = 75;
	private static final int COMP_DELAY = 500;

	protected long lastSent = System.currentTimeMillis();
	protected long lastRead = System.currentTimeMillis();
	protected long lastReset = System.currentTimeMillis();
	protected static State state = State.getReference();
	protected Application application = null;
	
	protected static Settings settings = Settings.getReference();
	
	protected SerialPort serialPort = null;
	// data buffer 
	protected byte[] buffer = new byte[256];
	protected int buffSize = 0;
	private double firmwareversion = 0;

	// thread safety 
	protected volatile boolean isconnected = false;
	private volatile long currentMoveID;
	protected volatile long currentCamMoveID;

//	private boolean invertswap = false;
	
	// tracking motor moves 
	private volatile double lastodomangle = 0; // degrees
	private volatile int lastodomlinear = 0; // mm
	private volatile double arcodomcomp = 1;
	private volatile Boolean odometryBroadCasting = false; // thread alive flag
    private volatile Boolean odomangleupdated = false;
    private volatile Boolean odomlinearupdated = false;
    private double expectedangle;
    private long rotatestoptime = 0;
    private volatile double finalangle;
    public static final double ROTATETOLERANCE = 3.0;
    public static final int ODOMBROADCASTDEFAULT = 250;
    private static final long STOPBETWEENMOVESWAIT = 0;

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
	public static final Double METERSPERSEC = 0.32;
	public static final Double DEGPERMS = 0.0857; // max that gyro can keep up with
	public static final int MAXDOCKEDPWM = 120;

	private volatile List<Byte> commandList = new ArrayList<>();
	private volatile boolean commandlock = false;
	private CommandSender cs;

	private int timeddelay;

	public ArduinoPrime(Application app) {	
		
		application = app;	
		state.set(State.values.motorspeed, speedfast);
		state.set(State.values.movingforward, false);
		state.set(State.values.moving, false);
		state.set(State.values.motionenabled, true);
		
		state.set(State.values.floodlightlevel, 0);
		state.set(State.values.spotlightbrightness, 0);
		
		setSteeringComp(settings.readSetting(GUISettings.steeringcomp));
		state.set(State.values.direction, direction.stop.name()); // .toString());

		state.set(State.values.odomturnpwm, settings.readSetting(ManualSettings.odomturnpwm.name()));
		state.set(State.values.odomlinearpwm, settings.readSetting(ManualSettings.odomlinearpwm.name()));

		setCameraStops(CAM_HORIZ, CAM_REVERSE);

		if(!settings.readSetting(ManualSettings.motorport).equals(Settings.DISABLED)) {
			connect();
			cs = new CommandSender();
			cs.start();
			checkFirmWareVersion();
		}
		initialize();
		camCommand(ArduinoPrime.cameramove.horiz); // in case board hasn't reset
		new WatchDog().start();
	}
	
	public void initialize() {
		Util.debug("initialize", this);
		lastRead = System.currentTimeMillis();
		lastReset = lastRead;		
		new Thread(new Runnable() {
			public void run() {
				Util.delay(10000);  // arduino takes 10 sec to reach full power?
				if(isconnected) strobeflash(ArduinoPrime.mode.on.toString(), 200, 30);
			}
		}).start();
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
				
				if (now - lastReset > RESET_DELAY &&
						state.get(oculusPrime.State.values.dockstatus).equals(AutoDock.DOCKED) &&
						!state.getBoolean(oculusPrime.State.values.autodocking) &&
						state.get(oculusPrime.State.values.driver) == null && isconnected &&
						state.getInteger(oculusPrime.State.values.telnetusers) == 0 &&
						commandList.size() == 0 &&
						!state.getBoolean(oculusPrime.State.values.moving)) {

					if (settings.getBoolean(GUISettings.navigation)) {
						if (state.exists(oculusPrime.State.values.navigationroute) && (
								state.get(oculusPrime.State.values.navsystemstatus).equals(Ros.navsystemstate.running.toString()) ||
										state.get(oculusPrime.State.values.navsystemstatus).equals(Ros.navsystemstate.starting.toString()))) {
							Util.delay(WATCHDOG_DELAY);
							continue;
						}
						if (state.exists(oculusPrime.State.values.nextroutetime)) {
							if (state.getLong(oculusPrime.State.values.nextroutetime.toString()) - System.currentTimeMillis() < Util.TWO_MINUTES) {
								Util.delay(WATCHDOG_DELAY);
								continue;
							}
						}
					}

					Util.log(boardid+" PCB periodic reset", this);
					Util.delay(10000); // allow time for camera to return to horiz, if reset right after docking
					lastReset = now;
					reset();
				}

				if (now - lastRead > DEAD_TIME_OUT && isconnected) {
					application.message(boardid+" PCB timeout, attempting reset", null, null);
					Util.log(boardid+ " PCB timeout, attempting reset", this);
					lastRead = now;
					reset();
				}
				
//				if (now - lastSent > WATCHDOG_DELAY && isconnected)  sendCommand(PING);			
				sendCommand(PING); // expect "" response

				Util.delay(WATCHDOG_DELAY);
			}		
		}
	}

	private void checkFirmWareVersion() {
		if (!isconnected) return;

		double version_required = FIRMWARE_VERSION_REQUIRED;

		if (boardid.equals(MALGDB_FIRMWARE_ID)) {
			version_required = MALGDB_FIRMWARE_VERSION_REQUIRED;
//			Util.log(boardid + ": firmware version check skipped", this);
//			return;
		}

		firmwareversion = 0;
		sendCommand(GET_VERSION);
		long start = System.currentTimeMillis();
		while(firmwareversion == 0 && System.currentTimeMillis() - start < 10000) { Util.delay(100);  }
		if (firmwareversion == 0) {
			String msg = "failed to determine current "+boardid+" firmware version";
			Util.log("error, "+msg, this);
			state.set(State.values.guinotify, msg);
			return;
		}
		if (firmwareversion != version_required) {

			if (state.get(State.values.osarch).equals(Application.ARM)) {// TODO: add ARM avrdude to package!
				String msg = "current "+boardid+" firmware: "+firmwareversion+
						" out of date! Update to: "+version_required;
//				state.set(State.values.guinotify, msg);
				Util.log(msg, this);
				return;
			}

			Util.log("Required "+boardid+" firmware version is "+version_required+", attempting update...", this);
			String port = state.get(State.values.motorport); // disconnect() nukes this state value
			disconnect();

			// TODO: do update here, blocking
			Updater.updateFirmware(boardid, version_required, port);

			connect();
			if (cs.isAlive())  Util.log("error, CommmandSender still alive", this);
			cs = new CommandSender();
			cs.start();

			// check if successful
			firmwareversion = 0;
			sendCommand(GET_VERSION);
			start = System.currentTimeMillis();
			while(firmwareversion == 0 && System.currentTimeMillis() - start < 10000)  { Util.delay(100); }
			if (firmwareversion != version_required && !settings.getBoolean(ManualSettings.debugenabled)) {
				String msg = "unable to update " + boardid + " firmware to version "+version_required;
				Util.log("error, "+msg, this);
				state.set(State.values.guinotify, msg);
			}
		}
	}

	public void floodLight(int target) {
		state.set(State.values.floodlightlevel, target);

		target = target * 255 / 100;
		sendCommand(new byte[]{FLOOD_LIGHT_LEVEL, (byte) target});
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
		if (mode.equalsIgnoreCase(ArduinoPrime.mode.on.toString()) && !state.getBoolean(State.values.strobeflashon)) {
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

//		if (!state.getBoolean(State.values.odometry)) Util.debug("serial in: " + response, this);
		
//		if(response.equals("reset")) {
//			sendCommand(GET_VERSION);
//			Util.debug(FIRMWARE_ID+" "+response, this);
//		}
		
		if(response.startsWith("version:")) {
			String versionstr = response.substring(response.indexOf("version:") + 8, response.length());
			Util.log(boardid + " firmware version: "+versionstr, this);
			firmwareversion = Double.valueOf(versionstr);
		}
	
		String[] s = response.split(" ");

		if (s[0].equals("moved")) {
			lastodomlinear = (int) (Double.parseDouble(s[1]) * Math.PI * settings.getInteger(ManualSettings.wheeldiameter));
			if (state.get(State.values.direction).equals(direction.arcleft.toString()) ||
					state.get(State.values.direction).equals(direction.arcright.toString()))
				lastodomlinear *= arcodomcomp;

			lastodomangle = Double.parseDouble(s[2]);
			lastodomangle *= settings.getDouble(ManualSettings.gyrocomp);
			
			state.set(State.values.distanceangle, lastodomlinear +" "+lastodomangle); //millimeters, degrees
            odomangleupdated = true;
            odomlinearupdated = true;
		}

		else if (s[0].equals("stop") && state.getBoolean(State.values.stopbetweenmoves)) 
			state.set(State.values.direction, direction.stop.toString());

		else if (s[0].equals("stopdetectfail")) {
//			if (settings.getBoolean(ManualSettings.debugenabled))
//				application.message("FIRMWARE STOP DETECT FAIL", null, null);
				Util.debug("FIRMWARE STOP DETECT FAIL", this);
			if (state.getBoolean(State.values.stopbetweenmoves)) 
				state.set(State.values.direction, direction.stop.toString());
		}

//		else if (s[0].equals("gyroOVR")) 	Util.debug("gyroOVR", this); // TODO: testing


		// TODO: testing
		if (!s[0].equals("moved")) Util.debug("serial in: " + response, this);

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
        			serialPort.readBytes(); // clear serial buffer
        			
        			serialPort.writeBytes(new byte[]{GET_PRODUCT, 13}); // query device
        			Thread.sleep(100); // some delay is required
					byte[] buffer = serialPort.readBytes();
        			
        			if (buffer == null) { // no response, move on to next port
						serialPort.closePort();
						continue;
					}
        			
        			String device = new String();
        			for (int n=0; n<buffer.length; n++) {
        				if((int)buffer[n] == 13 || (int)buffer[n] == 10) { break; }
        				if(Character.isLetterOrDigit((char) buffer[n]))
        					device += (char) buffer[n];
        			}
        			
        			if (device.length() == 0) break;
        			Util.debug(device+" "+portNames[i], this);
    				if (device.trim().startsWith("id")) device = device.substring(2, device.length());
    				Util.debug(device+" "+portNames[i], this);
    				
    				if (device.equals(FIRMWARE_ID) || device.equals(MALGDB_FIRMWARE_ID)) {
    					boardid = device;
    					Util.log(boardid + " connected to "+portNames[i], this);
    					
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
			byte[] input = serialPort.readBytes();
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
			if (cs.isAlive()) {
				Util.log("error, CommmandSender still alive", this);
//				return;
			}
			cs = new CommandSender();
			cs.start();
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

		if (state.getBoolean(State.values.controlsinverted)) {
			switch (cmd[0]) {
				case ArduinoPrime.FORWARD: cmd[0]=ArduinoPrime.BACKWARD; break;
				case ArduinoPrime.BACKWARD: cmd[0]=ArduinoPrime.FORWARD; break;
				case ArduinoPrime.LEFT: cmd[0]=ArduinoPrime.RIGHT; break;
				case ArduinoPrime.RIGHT: cmd[0]=ArduinoPrime.LEFT; break;
				case ArduinoPrime.RIGHTTIMED: cmd[0]=ArduinoPrime.LEFTTIMED; break;
				case ArduinoPrime.LEFTTIMED: cmd[0]=ArduinoPrime.RIGHTTIMED; break;
				case ArduinoPrime.FORWARDTIMED: cmd[0]=ArduinoPrime.BACKWARDTIMED; break;
				case ArduinoPrime.BACKWARDTIMED: cmd[0]=ArduinoPrime.FORWARDTIMED;
			}
		}
		
		/*
		if(settings.getBoolean(ManualSettings.debugenabled)) {
			String text = "sendCommand(): " + (char)cmd[0] + " ";
			for(int i = 1 ; i < cmd.length ; i++) 
				text += ((byte)cmd[i] & 0xFF) + " ";  // & 0xFF converts to unsigned byte
			
			Util.log("DEBUG: "+ text, this);
		}   // */

		int n = 0;
		while (commandlock) {
			Util.delay(1);
			n++;
		}

		commandlock = true;
		if (n!=0) Util.log("error, commandlock true for "+n+"ms", this);
		for (byte b : cmd) {
			if (b==10) b=11;
			else if (b==13) b=12;
			commandList.add(b);
		}
		commandList.add((byte) 13); // EOL
		commandlock = false;

	}
	
	
	public void sendCommand(byte cmd){
		sendCommand(new byte[]{cmd});
	}


	/** inner class to send commands to port in sequential order */
	private class CommandSender extends Thread {

		public CommandSender() {
			this.setDaemon(true);
		}

		public void run() {

			while (isconnected) {
				if (commandList.size() > 1 &! commandlock) { // >1 because NL required

//					if (!isconnected) {
//						Util.log("error, not connected", this); // TODO: not needed (never see this)
//						Util.delay(1);
//						continue;
//					}

					if (commandList.size() > 15) { // buffer in firmware is now 32 (was 8) AVR is 64?
						commandList.clear();
						Util.log("error, command stack up, all dropped", this);
						Util.delay(1);
						continue;
					}

					int EOLindex = commandList.indexOf((byte) 13);
					if (EOLindex == -1) {
//						String str = "";
//						for (int i = 0; i < commandList.size(); i++) str += String.valueOf((int) commandList.get(i)) + ", ";
//						Util.log("error, warning no EOL char: "+str, this); // nuke this, triggers sometimes as expected
						Util.delay(1);
						continue;
					}

					// in case of multiple EOL chars, read only up to 1st
					byte c[] = new byte[EOLindex+1];
					try {
						for (int i = 0; i <= EOLindex; i++) {
							c[i] = commandList.get(0);
							commandList.remove(0);
						}
					}  catch (Exception e) {
						Util.log("sendCommand() error, dropped, continuing: ", this); // , attempting reset", this);
						Util.printError(e);
//						commandList.clear(); // TODO: Testing removal
						Util.delay(1);
						continue;
					}

					try {
						// track last write
						lastSent = System.currentTimeMillis();
						serialPort.writeBytes(c); // writing as array ensures goes at baud rate?

					} catch (Exception e) {
						Util.log("sendCommand() error", this); // , attempting reset", this);
						Util.printError(e);
					}

				}

				Util.delay(1);

			}

			Util.log("thread exit, not connected", this);
		}
	}

	public void goForward() { goForward(0); }

	/**
	 *
	 * @param delay stop after delay ms (timed by firmware). If 0, continuous motion
	 *
	 */
	public void goForward(final int delay) {
		
		final long moveID = System.nanoTime();
		currentMoveID = moveID;
		
		if (state.getBoolean(State.values.stopbetweenmoves)) {
				
			if ( !state.get(State.values.direction).equals(direction.stop.toString())  &&
					!state.get(State.values.direction).equals(direction.forward.toString())) {

//				Util.debug("goForward, stop required 1st", this);

				stopGoing(moveID);
//				currentMoveID = moveID;

				new Thread(new Runnable() {public void run() {
					long stopwaiting = System.currentTimeMillis()+5000;
					while(!state.get(State.values.direction).equals(direction.stop.toString()) &&
							System.currentTimeMillis() < stopwaiting) { Util.delay(1); } // wait

					Util.delay(STOPBETWEENMOVESWAIT);

					if (currentMoveID == moveID)  goForward();
					
				} }).start();
				
				return;
			}
		}

		state.set(State.values.moving, true);
		state.set(State.values.movingforward, true);
		
		int speed1 = (int) voltsComp((double) speedslow);
		if (speed1 > 255) { speed1 = 255; }
		
//		int speed2= state.getInteger(State.values.motorspeed);
		
		int speed2;
		if (state.getBoolean(State.values.odometry)) {
			speed2 = state.getInteger(State.values.odomlinearpwm); 
			tracklinearrate(moveID);
		}
		else speed2= state.getInteger(State.values.motorspeed);

		if (speed2<speed1) { 		// voltcomp on slow speed only
			speed2 = (int) voltsComp((double) speed2);
			if (speed2 > 255) { speed2 = 255; }
		}
		
		// no full speed when on dock voltage
//		if (state.get(State.values.dockstatus).equals(AutoDock.DOCKED) && speed2==speedfast) {
//			speed2 = speedmed;
//		}

		if (delay != 0) timeddelay = delay + FIRMWARE_TIMED_OFFSET;

		// if already moving forward, go full speed and exit
		if (state.get(State.values.direction).equals(direction.forward.toString()) ) {
			int s = speed2;
			int[] comp = applyComp(s);
			int L, R;
			L = comp[0];
			R = comp[1];

			if (delay == 0)
				sendCommand(new byte[]{FORWARD, (byte) R, (byte) L});
			else {
				byte d1 = (byte) ((timeddelay >> 8) & 0xff);
				byte d2 = (byte) (timeddelay & 0xff);
				sendCommand(new byte[]{FORWARDTIMED, (byte) R, (byte) L, d1, d2});
			}
			return;
		}

		state.set(State.values.direction, direction.forward.toString());

		// always start slow, un-comped
		int s = speed1;

		// limit pwm on dock voltage for powerv1 boards
        if (state.get(State.values.dockstatus).equals(AutoDock.DOCKED) && s>MAXDOCKEDPWM
                && application.powerport.boardid.equals(ArduinoPower.FIRMWARE_IDV1))
            s = MAXDOCKEDPWM;

		if (delay == 0) {
			sendCommand(new byte[]{FORWARD, (byte) s, (byte) s});
		}
		else {
			int d = timeddelay;
			if (d > ACCEL_DELAY) d = ACCEL_DELAY;

			byte d1 = (byte) ((d >> 8) & 0xff);
			byte d2 = (byte) (d & 0xff);
			sendCommand(new byte[]{ FORWARDTIMED, (byte) s, (byte) s, d1, d2});
			if (timeddelay <= ACCEL_DELAY) return;
		}

		final int spd = speed2;

		// start accel to full speed after short ACCEL_DELAY, no comp (because initial comp causes considerable drift)
		if (speed2 > speed1) { 
			new Thread(new Runnable() {
				public void run() {
					
					Util.delay(ACCEL_DELAY);

					int s = spd;

                    // limit pwm on dock voltage for powerv1 boards
                    if (state.get(State.values.dockstatus).equals(AutoDock.DOCKED) && s>MAXDOCKEDPWM
                            && application.powerport.boardid.equals(ArduinoPower.FIRMWARE_IDV1))
                        s = MAXDOCKEDPWM;

					if (currentMoveID != moveID)  return;

					// actual speed, un-comped
					if (delay==0)
						sendCommand(new byte[] { FORWARD, (byte) s, (byte) s});
					else {
						int d = timeddelay;

						if (steeringcomp != 0 && d > COMP_DELAY)  d = COMP_DELAY-ACCEL_DELAY;

						byte d1 = (byte) ((d >> 8) & 0xff);
						byte d2 = (byte) (d & 0xff);
						sendCommand(new byte[]{ FORWARDTIMED, (byte) s, (byte) s, d1, d2});
						timeddelay -= COMP_DELAY;
					}

				} 
			}).start();
		}

		if (delay != 0) if (timeddelay <= COMP_DELAY) return;

		// apply comp only when up to full speed
		if (steeringcomp != 0) {  
			new Thread(new Runnable() {
				public void run() {
					Util.delay(COMP_DELAY);

					int s = spd;

                    // limit pwm on dock voltage for powerv1 boards
                    if (state.get(State.values.dockstatus).equals(AutoDock.DOCKED) && s>MAXDOCKEDPWM
                            && application.powerport.boardid.equals(ArduinoPower.FIRMWARE_IDV1))
                        s = MAXDOCKEDPWM;

					int[] comp = applyComp(s); // actual speed, comped
					int L,R;
					L = comp[0];
					R = comp[1];
					if (currentMoveID != moveID)  return;
					if (delay==0)
						sendCommand(new byte[] { FORWARD, (byte) R, (byte) L});
					else {
						int d = timeddelay-ACCEL_DELAY;
						byte d1 = (byte) ((d >> 8) & 0xff);
						byte d2 = (byte) (d & 0xff);
						sendCommand(new byte[]{ FORWARDTIMED, (byte) s, (byte) s, d1, d2});
					}
					
				} 
			}).start();
		}
		
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

		if (state.get(State.values.dockstatus).equals(AutoDock.DOCKED)) return;

		final long moveID = System.nanoTime();
		currentMoveID = moveID;

		if (state.getBoolean(State.values.stopbetweenmoves)) {
			
			if ( !state.get(State.values.direction).equals(direction.stop.toString()) &&
					!state.get(State.values.direction).equals(direction.backward.toString())) {

				stopGoing();
				currentMoveID = moveID;
				
				new Thread(new Runnable() {public void run() {
					long stopwaiting = System.currentTimeMillis()+5000;
					while(!state.get(State.values.direction).equals(direction.stop.toString()) &&
							System.currentTimeMillis() < stopwaiting) { Util.delay(1); } // wait

					Util.delay(STOPBETWEENMOVESWAIT);

					if (currentMoveID == moveID)  goBackward();
					
				} }).start();
				
				return;
			}
		}

		state.set(State.values.moving, true);
		state.set(State.values.movingforward, false);
//		if (settings.getBoolean(GUISettings.muteonrovmove))  application.muteROVMic();
		
		
		int speed1 = (int) voltsComp((double) speedslow);
		if (speed1 > 255) { speed1 = 255; }
		
//		int speed2= state.getInteger(State.values.motorspeed);
		int speed2;
		if (state.getBoolean(State.values.odometry)) {
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

		state.set(State.values.direction, direction.backward.toString()); // now go

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


					int[] comp = applyComp(spd); // apply comp now that up to speed
					int L = comp[1];
					int R = comp[0];
					if (currentMoveID != moveID)  return;
					sendCommand(new byte[] { BACKWARD, (byte) R, (byte) L});
					
				} 
			}).start();
		}
	}

	public void turnRight() {
		turnRight(0);
	}

	public void turnRight(int delay) {
		long moveID = System.nanoTime();
		turnRight(delay, moveID);
	}

	public void turnRight(final int delay, final long moveID) { // WITHOUTACCEL

//		Util.debug("turnRight, current dir="+state.get(State.values.direction), this);

		if (state.get(State.values.dockstatus).equals(AutoDock.DOCKED)) return;

		currentMoveID = moveID;

		if (state.getBoolean(State.values.stopbetweenmoves)) {

			if ( !state.get(State.values.direction).equals(direction.stop.toString())  &&
					!state.get(State.values.direction).equals(direction.right.toString())) {

//				Util.debug("turnRight, stop required 1st", this);

				stopGoing(moveID);

				new Thread(new Runnable() {public void run() {

					long stopwaiting = System.currentTimeMillis()+5000;
					while(!state.get(State.values.direction).equals(direction.stop.toString()) &&
							System.currentTimeMillis() < stopwaiting) { Util.delay(1); } // wait

					Util.delay(STOPBETWEENMOVESWAIT);

					if (currentMoveID == moveID)  turnRight(delay, moveID);
					else Util.debug("turnRight() !moveID",this);

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
			state.set(State.values.moving, true);
		}
		else {
			byte d1 = (byte) ((delay >> 8) & 0xff);
			byte d2 = (byte) (delay & 0xff);
			sendCommand(new byte[] { RIGHTTIMED, (byte) tmpspeed, (byte) tmpspeed, (byte) d1, (byte) d2});
		}
	}
	
	public void turnRightWITHACCEL(int delay, final long moveID) { // WITHACCEL (causes probs with rotate command?)

		if (state.get(State.values.dockstatus).equals(AutoDock.DOCKED)) return;

		currentMoveID = moveID;

		if (state.getBoolean(State.values.stopbetweenmoves)) {
			
			if ( !(state.get(State.values.direction).equals(direction.right.toString()) ||
				state.get(State.values.direction).equals(direction.stop.toString()) ) ) {
			
				stopGoing(moveID);

				new Thread(new Runnable() {public void run() {
					
					long stopwaiting = System.currentTimeMillis()+1000;
					while(!state.get(State.values.direction).equals(direction.stop.toString()) &&
							System.currentTimeMillis() < stopwaiting) { Util.delay(1); } // wait
					if (currentMoveID == moveID)  turnRight(delay, moveID);
					
				} }).start();
				
				return;
			}
		}


		int spd1;
		int spd2 = 0;

		if (state.getBoolean(State.values.odometry)) {
			spd2 = state.getInteger(State.values.odomturnpwm);
			if (state.get(State.values.direction).equals(direction.right.toString()) ) {
				spd1 = spd2;
			}
			else { spd1 = (int) voltsComp((double) speedslow); }
			trackturnrate(moveID);
		}
		else spd1 = state.getInteger(State.values.motorspeed) + TURNBOOST;

		if (spd1 > 255) spd1 = 255;
		if (spd2 > 255) spd2 = 255;

		if (delay!=0) {
			byte d1 = (byte) ((delay >> 8) & 0xff);
			byte d2 = (byte) (delay & 0xff);
			sendCommand(new byte[] { RIGHTTIMED, (byte) spd1, (byte) spd1, d1, d2});
			state.set(State.values.direction, direction.right.toString());
			return;
		}

		sendCommand(new byte[] { RIGHT, (byte) spd1, (byte) spd1 });

		final int s = spd2;
		if (state.exists(State.values.odomturndpms.toString())) {
			new Thread(new Runnable() {
				public void run() {

					Util.delay(ACCEL_DELAY);

					if (currentMoveID != moveID) return;

					sendCommand(new byte[]{RIGHT, (byte) s, (byte) s});
				}
			}).start();
		}

		state.set(State.values.direction, direction.right.toString());
		state.set(State.values.moving, true);
	}

	public void turnLeft() {
		turnLeft(0);
	}

	public void turnLeft(int delay) {
		long moveID = System.nanoTime();
		turnLeft(delay, moveID);
	}

	/**
	 * Turn Left
	 * @param delay milliseconds, then stop (timed directly by firmware). If 0, continuous movement
	 */
	public void turnLeft(final int delay, final long moveID) { // WITHOUTACCEL

//		Util.debug("turnLeft, current dir="+state.get(State.values.direction), this);

		if (state.get(State.values.dockstatus).equals(AutoDock.DOCKED)) return;

		currentMoveID = moveID;

		if (state.getBoolean(State.values.stopbetweenmoves)) {

			if ( !state.get(State.values.direction).equals(direction.stop.toString())  &&
					!state.get(State.values.direction).equals(direction.left.toString())) {

//				Util.debug("turnLeft, stop required 1st", this);

				stopGoing(moveID);

				new Thread(new Runnable() {public void run() {

					long stopwaiting = System.currentTimeMillis()+5000;
					while(!state.get(State.values.direction).equals(direction.stop.toString()) &&
							System.currentTimeMillis() < stopwaiting) { Util.delay(1); } // wait

					Util.delay(STOPBETWEENMOVESWAIT);

					if (currentMoveID == moveID)  turnLeft(delay, moveID);
					else Util.debug("turnLeft() !moveID",this);

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
			state.set(State.values.moving, true);
//			if (settings.getBoolean(GUISettings.muteonrovmove))  application.muteROVMic();
		}
		else {
			byte d1 = (byte) ((delay >> 8) & 0xff);
			byte d2 = (byte) (delay & 0xff);
			sendCommand(new byte[] { LEFTTIMED, (byte) tmpspeed, (byte) tmpspeed, d1, d2});
		}

	}

	public void turnLeftWITHACCEL(int delay, final long moveID) {    // WITHACCEL (causes probs with rotate command?)

		if (state.get(State.values.dockstatus).equals(AutoDock.DOCKED)) return;

		currentMoveID = moveID;
		
		if (state.getBoolean(State.values.stopbetweenmoves)) {
			
			if ( !(state.get(State.values.direction).equals(direction.left.toString()) ||
				state.get(State.values.direction).equals(direction.stop.toString()) ) ) {

				stopGoing(moveID);

				new Thread(new Runnable() {public void run() {
					
					long stopwaiting = System.currentTimeMillis()+1000;
					while(!state.get(State.values.direction).equals(direction.stop.toString()) &&
							System.currentTimeMillis() < stopwaiting) { Util.delay(1); } // wait
					if (currentMoveID == moveID)  turnLeft(delay, moveID);
					
				} }).start();
				
				return;
			}
		}	

		int spd1;
		int spd2 = 0;

		if (state.getBoolean(State.values.odometry)) {
			spd2 = state.getInteger(State.values.odomturnpwm);
			if (state.get(State.values.direction).equals(direction.left.toString()) ) {
				spd1 = spd2;
			}
			else { spd1 = (int) voltsComp((double) speedslow); }
			trackturnrate(moveID);
		}
		else spd1 = state.getInteger(State.values.motorspeed) + TURNBOOST;
		
		if (spd1 > 255) spd1 = 255;
		if (spd2 > 255) spd2 = 255;

		if (delay!=0) { // timed on PCB
			byte d1 = (byte) ((delay >> 8) & 0xff);
			byte d2 = (byte) (delay & 0xff);
			sendCommand(new byte[] { LEFTTIMED, (byte) spd1, (byte) spd1, d1, d2});
			state.set(State.values.direction, direction.left.toString());
			return;
		}

		sendCommand(new byte[] { LEFT, (byte) spd1, (byte) spd1 });

		final int s = spd2;
		if (state.exists(State.values.odomturndpms.toString())) {
			new Thread(new Runnable() {
				public void run() {

					Util.delay(ACCEL_DELAY);

					if (currentMoveID != moveID) return;

					sendCommand(new byte[]{LEFT, (byte) s, (byte) s});
				}
			}).start();
		}

		state.set(State.values.direction, direction.left.toString());
		state.set(State.values.moving, true);

	}
	

	private void trackturnrate(final long moveID) {
		if (!state.exists(State.values.odometrybroadcast)) return;

//		Util.debug("tracking turn rate, "+rotatestoptime, this);

		new Thread(new Runnable() {public void run() {
			final long turnstart = System.currentTimeMillis();
			long start = turnstart;
			long now;
			final double tolerance = state.getDouble(State.values.odomturndpms.toString())*0.08;
			final int PWMINCR = 5;
			final int ACCEL = 500;
			final double targetrate = state.getDouble(State.values.odomturndpms.toString());
			double totalangle = 0;
			boolean timecompd = false;

			byte dir = 0;
			if ( state.get(State.values.direction).equals(direction.left.toString()))
				dir = LEFT;
			else if ( state.get(State.values.direction).equals(direction.right.toString())) // extra thread safe
				dir = RIGHT;

			while (currentMoveID == moveID)  {

				if (odomangleupdated) {
                    odomangleupdated = false;

					totalangle += Math.abs(lastodomangle);



					now = System.currentTimeMillis();
					if (now - turnstart < ACCEL) {
						start = now;
						continue; // throw away 1st during accel, assuming broadcast interval is around 250ms
					}

					int currentpwm = state.getInteger(State.values.odomturnpwm);
					int newpwm = currentpwm;

                    if (rotatestoptime > turnstart) {
                        if (Math.abs(totalangle - expectedangle) <= ROTATETOLERANCE*2 && expectedangle > 15) {
//                            Util.debug("rotate cancelled" ,this);
                            rotatestoptime = now;
                            newpwm -= PWMINCR;
                            if (newpwm < speedslow) newpwm = speedslow;
                            state.set(State.values.odomturnpwm, newpwm);
                            break;
                        }
                    }

                    double rate = Math.abs(lastodomangle)/(now - start);

                    if (!timecompd && rotatestoptime > turnstart) {
						long projectedstopdiff = (turnstart + ACCEL_DELAY + (long) ((expectedangle-2) / rate)) - rotatestoptime;
						if (projectedstopdiff > rotatestoptime-turnstart) // sanity check for positive comp only
							projectedstopdiff = rotatestoptime-turnstart;
						rotatestoptime += (long) (projectedstopdiff*0.7);
						if (Math.abs(projectedstopdiff) > ACCEL_DELAY) {
							if (projectedstopdiff > 0) newpwm += PWMINCR;
							else newpwm -= PWMINCR;
						}
						timecompd = true;
//						Util.debug("projectedstopdiff: "+projectedstopdiff, this);
					}

					if (rate > targetrate + tolerance) {
						newpwm -= PWMINCR;
						if (newpwm < speedslow) newpwm = speedslow;
					}
					else if (rate < targetrate - tolerance) {
						newpwm += PWMINCR;
						if (newpwm > 255) newpwm = 255;
					}
//					else // within tolerance, kill thread to save cpu
//						break;

					// modify speed
					state.set(State.values.odomturnpwm, newpwm);

//                    Util.debug("oldpwm: " + currentpwm + ", newpwm: " + newpwm, this);

					if (currentMoveID == moveID && dir != 0 ) // extra thread safe
						sendCommand(new byte[] { dir, (byte) newpwm, (byte) newpwm });
					else break;

					start = now;
					
				}
				Util.delay(1);

			}

			// end of move. now further comp odomturnpwm if necessary
			// (if odomturndpms * time doesn't match angle moved)

			if (rotatestoptime > turnstart) {

				now = System.currentTimeMillis();
				while (!odomangleupdated && System.currentTimeMillis() - now < 5000) Util.delay(1);
				odomangleupdated = false;
				totalangle += Math.abs(lastodomangle);

				double anglediffpercent = totalangle / expectedangle - 1;
				final double ADPTOLERANCE = 0.05;
				double anglediff = totalangle - expectedangle;
				final double ADTOLERANCE = 3.0;

				Util.debug("totalangle: " + totalangle + ", expectedangle: " + expectedangle +
						", anglediffpercent: " + anglediffpercent, this);

				if (Math.abs(anglediffpercent) > ADPTOLERANCE && Math.abs(anglediff) > ADTOLERANCE) {
					int currentpwm = state.getInteger(State.values.odomturnpwm);
					int newpwm = currentpwm;

					if (anglediffpercent < 0) newpwm += PWMINCR;
					else newpwm -= PWMINCR;

					if (newpwm < speedslow) newpwm = speedslow;
					else if (newpwm > 255) newpwm = 255;

					// modify speed
					state.set(State.values.odomturnpwm, newpwm);

//					Util.debug("oldpwm: " + currentpwm + ", newpwm: " + newpwm, this);
				}

				finalangle = totalangle;

			}

			floorFrictionCheck();

		} }).start();
		
	}

	private void floorFrictionCheck() {
		if (!settings.getBoolean(ManualSettings.usearcmoves))  return;
		// 	measured carpet = 195pwm (volts comped 0.0857degrees per ms  12.04 battery volts)
		// no carpet = pwm 110-140
		boolean rosarcmove = state.getBoolean(State.values.rosarcmove);
		if (unVoltsComp(state.getInteger(State.values.odomturnpwm)) > settings.getInteger(ManualSettings.arcpwmthreshold))
			{ if (rosarcmove) state.set(State.values.rosarcmove, false); }
		else { if (!rosarcmove) state.set(State.values.rosarcmove, true); }
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
		if (!state.exists(State.values.odometrybroadcast)) return;
		
		new Thread(new Runnable() {public void run() {
			final long linearstart = System.currentTimeMillis();
			long start = linearstart;
			final double tolerance = state.getDouble(State.values.odomlinearmpms.toString()) * 0.05; //0.000015625; //  meters per ms --- 5% of target speed 0.32m/s (0.00032
			final int pwmincr = 10;
			final int accel = 480; // TODO: try lowering this (from 480), kind of useless for small linear moves during rosnav
			final double targetrate = state.getDouble(State.values.odomlinearmpms.toString());
			
			while (currentMoveID == moveID)  {

				if (odomlinearupdated) {
                    odomlinearupdated=false;
					
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
//					else // within tolerance, kill thread (save cpu)
//						break;
					
					//modify speed
					state.set(State.values.odomlinearpwm, newpwm);

					int[] comp = applyComp(newpwm); // steering comp
					int L = 0;
					int R = 0;
					byte dir = 0;

					
					if ( state.get(State.values.direction).equals(direction.forward.toString())) {
						dir = FORWARD;
						L = comp[0];
						R = comp[1];
					} else if ( state.get(State.values.direction).equals(direction.backward.toString())){ // extra thread safe
						dir = BACKWARD;
						L = comp[1];
						R = comp[0];
					}

					if (currentMoveID == moveID && dir != 0) // extra thread safe
						sendCommand(new byte[] { dir, (byte) R, (byte) L});
					else break;
					
					start = now;
				}

				Util.delay(1);

			}
		} }).start();
		
	}

	/** @param str command character followed by nothing or integers between 0-255 */
	public void malgcommand(String str) {
		String s[] = str.split(" ");
		if (s.length == 0) return;
		byte[] cmd = new byte[s.length];
		cmd[0] = s[0].getBytes()[0];
		for (int i = 1; i<s.length; i++ ) {
			cmd[i] = (byte) ((int) Integer.valueOf(s[i]));
		}

		sendCommand(cmd);
	}

	
	public void camCommand(cameramove move){
		
		// no camera moves in reverse except horiz, which cancels reverse mode
		if (state.getBoolean(State.values.controlsinverted) && !move.equals(cameramove.horiz)) {
			return;
		}
		
		int position;
		
		currentCamMoveID = System.nanoTime();
		
		switch (move) {
		
			case stop:
				camRelease(currentCamMoveID);
				break;
				
			case up:  
				cameraToPosition(CAM_MAX, currentCamMoveID);
				break;
			
			case down:
				cameraToPosition(CAM_MIN, currentCamMoveID);
				break;
			
			case horiz:
				cameraToPosition(CAM_HORIZ, currentCamMoveID);
				break;
			
			case downabit: 
				position= state.getInteger(State.values.cameratilt) + CAM_NUDGE*3;
				if (position >= CAM_MIN) { 
					position = CAM_MIN;
				}
				cameraToPosition(position, currentCamMoveID);
				break;
			
			case upabit: 
				position = state.getInteger(State.values.cameratilt) - CAM_NUDGE*3;
				if (position <= CAM_MAX) { 
					position = CAM_MAX;
				}
				cameraToPosition(position, currentCamMoveID);
				break;

			case rearstop:   // deprecated, same as reverse
				
			case reverse:
				cameraToPosition(CAM_REVERSE, currentCamMoveID);
				break;
		}
	
	}
	
//	public void cameraToPosition(int position) { // max movement speed
//		sendCommand(new byte[] { CAM, (byte) position} );
//		long camMoveID = System.nanoTime();
//		currentCamMoveID = camMoveID;
//		camRelease(camMoveID);
//		state.set(State.values.cameratilt, position);
//		return;
//	}

	public void camtilt(int position) {
		currentCamMoveID = System.nanoTime();
		cameraToPosition(position, currentCamMoveID);
	}

	// handle *all* camera movement
	private void cameraToPosition(final int goalposition, final long camMoveID) {
		if (state.getInteger(State.values.cameratilt) == goalposition) {
			camRelease(camMoveID);
			return;
		}

		// determine direction
		boolean temp = false; // down
		if (state.getInteger(State.values.cameratilt) > goalposition)  temp = true;  // up
		final boolean up = temp;

		new Thread(new Runnable() {
			public void run() {

				while (camMoveID == currentCamMoveID) {

					// check if reached goal
					int currentpos = state.getInteger(State.values.cameratilt);
					if ( (up && currentpos <= goalposition) || (!up && currentpos >= goalposition) ) { // position reached, stop
						checkIfInverted();
						camRelease(camMoveID);
						break;
					}

					// define new position
					int newposition;
					if (up) {
						newposition = currentpos - CAM_NUDGE;
						if (newposition <= goalposition)  newposition = goalposition;

					}
					else { // down
						newposition = currentpos + CAM_NUDGE;
						if (newposition >= goalposition)  newposition = goalposition;
					}
					state.set(State.values.cameratilt, newposition);

					// move cam
					sendCommand(new byte[] { CAM, (byte) state.getInteger(State.values.cameratilt) });

					Util.delay(CAM_SMOOTH_DELAY);
				}

				if (camMoveID == currentCamMoveID) {
					application.messageplayer(null, "cameratilt", state.get(State.values.cameratilt));
				}

			}

		}).start();
	}

	public static void  checkIfInverted() {
		int currentpos = state.getInteger(State.values.cameratilt);
		if (Math.abs(currentpos - CAM_REVERSE) < 5) {
			if (!state.getBoolean(State.values.controlsinverted))
				state.set(State.values.controlsinverted, true);
		} else {
			if (state.getBoolean(State.values.controlsinverted))
				state.set(State.values.controlsinverted, false);
		}
	}

	private void camRelease(final long camMoveID) {
		new Thread(new Runnable() {
			public void run() {
				Util.delay(CAM_RELEASE_DELAY);
				if (state.getBoolean(State.values.controlsinverted) && settings.getBoolean(ManualSettings.camhold))
					Util.delay(CAMHOLD_RELEASE_DELAY);
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
	        state.set(State.values.motorspeed, Integer.parseInt(str));
	    } catch(NumberFormatException e) {  // not integer
			final speeds update = speeds.valueOf(str);
			
			switch (update) {
				case slow: state.set(State.values.motorspeed, speedslow); break;
				case med: state.set(State.values.motorspeed, speedmed); break;
				case fast: state.set(State.values.motorspeed, speedfast); break;
			}
	    }

	}

	@SuppressWarnings("incomplete-switch")
	public void nudge(final direction dir) {

		new Thread(new Runnable() {
			public void run() {
				
				int n = nudgedelay;
				boolean movingforward = state.getBoolean(State.values.movingforward);
				
				switch (dir) {
				case right: turnRight(); break;
				case left: turnLeft(); break;
				case forward: 
					goForward();
					state.set(State.values.movingforward, false);
					n *= 4; 
					break;
				case backward:
					goBackward();
					n *= 4;
				}

				if (!state.getBoolean(State.values.odometry)) { // normal
					Util.delay((int) voltsComp(n));
				} 
				else { // odometry
					if (movingforward && (dir.equals(direction.right) || dir.equals(direction.left))) {
						long stopwaiting = System.currentTimeMillis()+LINEAR_STOP_DELAY;
						while( System.currentTimeMillis() < stopwaiting &&
								state.get(State.values.direction).equals(direction.forward.toString())  ) {  Util.delay(1);  } // wait for stop
					}
//					Util.delay((long) (12.5 / state.getDouble(State.values.odomturndpms.toString())) ); // before turn accel added
					Util.delay((long) (16.0 / state.getDouble(State.values.odomturndpms.toString())) + STOPBETWEENMOVESWAIT );
					if (movingforward)  state.set(State.values.movingforward, true);
				}

				if (movingforward) goForward();
				else stopGoing();
				
			}
		}).start();
	}

	public void rotate(direction dir, double degrees) {
		long moveID = System.nanoTime();
		rotate(dir, degrees, moveID);
	}

	public void rotate(final direction dir, final double degrees, final long moveID) {
		new Thread(new Runnable() {
			@SuppressWarnings("incomplete-switch")
			public void run() {

				int tempspeed = state.getInteger(State.values.motorspeed);
				state.set(State.values.motorspeed, speedfast);
				long delay = ACCEL_DELAY;

				switch (dir) {
					case right:
						if (state.get(State.values.direction).equals(direction.right.toString())) delay = 0;
						turnRight(0, moveID);
						break;
					case left:
						if (state.get(State.values.direction).equals(direction.left.toString())) delay = 0;
						turnLeft(0, moveID);
				}

				if (!state.exists(State.values.odomturndpms.toString())) { // normal
					double n = fullrotationdelay * degrees / 360;
					Util.delay((int) voltsComp(n));
				}
				
				else { // using gyro feedback
					long timeout = System.currentTimeMillis()+1000;
					while (!state.get(State.values.direction).equals(dir.name()) && System.currentTimeMillis() < timeout)
						Util.delay(1);
					double delaycomp = 3;
					if (degrees <=3) { delay = ACCEL_DELAY-(long)(ACCEL_DELAY/degrees); delaycomp = 0; }
					long now = System.currentTimeMillis();
					rotatestoptime = now + delay +
							(long) ((degrees-delaycomp) / state.getDouble(State.values.odomturndpms));
					expectedangle = degrees;
//					Util.debug("rotatestoptime: "+rotatestoptime+", rotatedelay: "+(rotatestoptime-now), this);
					while (System.currentTimeMillis() < rotatestoptime) Util.delay(1);
				}

				state.set(State.values.motorspeed, tempspeed);

				if (moveID != currentMoveID) { Util.debug("rotate() !moveid", this); return; }

				stopGoing();
				application.message(null, "motion", "stopped");
			}
		}).start();
	}

	// progressive rotate, using odom feedback
	public void rotate(final double degrees) {

//		Util.debug ("rotate double: "+degrees, this);

		if (degrees == 0) return;

		direction dir = direction.left;
		if (degrees < 0) dir = direction.right;

		if (!state.exists(State.values.odomturndpms.toString())) { // odometry not running, use normal
			rotate(dir, (int) Math.abs(degrees));
			return;
		}

		// TODO: required?
		if (state.getDouble(State.values.odomrotating) > 0) {
			Util.debug("error, odomrotating already", this);
			return;
		}

		// important to set in main thread
		long moveID = System.nanoTime();
		state.set(State.values.odomrotating, moveID);

		final direction directn = dir;


		new Thread(new Runnable() {
			public void run() {

				final int MAXATTEMPTS = 3;
				double angle = Math.abs(degrees);
				int attempts = 0;
				direction dir = directn;
				if (!state.exists(State.values.rotatetolerance))
					state.set(State.values.rotatetolerance, ROTATETOLERANCE);

				while ((angle > state.getDouble(State.values.rotatetolerance) || attempts == 0)
						&& attempts < MAXATTEMPTS && state.getDouble(State.values.odomrotating)==moveID) {

					finalangle = moveID;

					rotate (dir, angle);

					// wait for final angle
					long timeout = System.currentTimeMillis()+30000;
					while (finalangle == moveID && System.currentTimeMillis() < timeout) Util.delay(1);

					if (finalangle == moveID) {
						Util.debug("error, finalangle: "+moveID, this);
						break;
					}

					if (finalangle > angle) { // overshoot, reverse direction
						if (directn == direction.left) dir = direction.right;
						else dir = direction.left;
					}

					angle = Math.abs(angle - finalangle);

					attempts ++;

					Util.delay(TURNING_STOP_DELAY);

				}

				if (state.getDouble(State.values.odomrotating) == moveID)
				    state.set(State.values.odomrotating, false);
				else Util.debug("rotate deg: odomrotating !moveiD", this);

			}
		}).start();
	}
	
	public void movedistance(final direction dir, final double meters) {
		new Thread(new Runnable() {
			public void run() {
				
				final int tempspeed = state.getInteger(State.values.motorspeed);
				state.set(State.values.motorspeed, speedfast);
				
				
				// openni
				short[] depthFrameBefore = null;
				short[] depthFrameAfter = null;
				
				// stereo
				short[][] cellsBefore = null;
				short[][] cellsAfter = null;

				String currentdirection = state.get(State.values.direction);
				
				switch (dir) {
					case forward:
                        goForward();
						break;
						
					case backward:
						goBackward();
						break;
				}

//				state.block(State.values.direction, direction.forward.toString(), 1000); // wait for goForward ID to be assigned
//				long moveID = currentMoveID;

//				long moveID = 0;
//				if (currentdirection.equals(direction.forward.toString())) moveID = currentMoveID;

				if (!state.exists(State.values.odomlinearmpms.toString())) { // normal
					double n = onemeterdelay * meters;
					Util.delay((int) voltsComp(n));
				} 
				else { // continuous comp using gyro
					Util.delay((long) (meters / state.getDouble(State.values.odomlinearmpms.toString())));
				}

//				if (currentdirection.equals(direction.forward.toString()))
//					if (currentCamMoveID != moveID) return;

				stopGoing();
				
				String msg = null;

				state.set(State.values.motorspeed, tempspeed);
				application.message(msg, "motion", "stopped");
				
			}
		}).start();
	}

	public void arcmove(final double arclengthmeters, final int angledegrees) {

		if (arclengthmeters ==0) return;

		final long moveID = System.nanoTime();
		currentMoveID = moveID;

		if (state.getBoolean(State.values.stopbetweenmoves)) {

			Boolean reversing = false;
			if ((state.get(State.values.direction).equals(direction.forward.toString()) && arclengthmeters < 0) ||
					(state.get(State.values.direction).equals(direction.backward.toString()) && arclengthmeters > 1) )
				reversing = true;

			// stop only if turning-in-place or reversing
			if ( (!state.get(State.values.direction).equals(direction.stop.toString())  &&
					!state.get(State.values.direction).equals(direction.arcleft.toString()) &&
					!state.get(State.values.direction).equals(direction.arcright.toString()) ) ||
					reversing
					) {

				stopGoing();
				currentMoveID = moveID;

				new Thread(new Runnable() {public void run() {
					long stopwaiting = System.currentTimeMillis()+1000;
					while(!state.get(State.values.direction).equals(direction.stop.toString()) &&
							System.currentTimeMillis() < stopwaiting) { Util.delay(1); } // wait
					if (currentMoveID == moveID)  arcmove(arclengthmeters, angledegrees);

				} }).start();

				return;
			}
		}

		final int degreespermeter = (int) (Math.abs(angledegrees/arclengthmeters));

//		if (degreespermeter < 6) {
//			if (!state.get(State.values.direction).equals(direction.stop.toString()))
//				state.set(State.values.direction, direction.forward.toString()); // to skip stopbetweenmoves and accel
//			movedistance(direction.forward, arclengthmeters);
//			return;
//		}

		state.set(State.values.moving, true);

		// go straight and exit if only slight arc
		if (degreespermeter < 6) {

			if (!state.getBoolean(State.values.odometry)) {
				if (arclengthmeters > 0)
					movedistance(direction.forward, arclengthmeters);
				else movedistance(direction.backward, arclengthmeters);
				return;
			}

			// odometry running
			int speed = state.getInteger(State.values.odomlinearpwm);
			tracklinearrate(moveID);
			speed = (int) voltsComp((double) speed);
			if (speed > 255) speed = 255;
			int[] comp = applyComp(speed);
			int L, R;

			if (arclengthmeters > 0) { //forwards
				L = comp[0];
				R = comp[1];
				sendCommand(new byte[]{FORWARD, (byte) R, (byte) L});
				state.set(State.values.direction, direction.forward.toString());
			} else { // backwards
				L = comp[1];
				R = comp[0];
				sendCommand(new byte[]{BACKWARD, (byte) R, (byte) L});
				state.set(State.values.direction, direction.backward.toString());
			}

			new Thread(new Runnable() {
				public void run() {
					Util.delay((long) Math.abs(arclengthmeters / state.getDouble(State.values.odomlinearmpms.toString())));
					if (moveID == currentMoveID) stopGoing();
				}
			}).start();

			return;
		}
			
		new Thread(new Runnable() {
			public void run() {

				double angleradians = Math.toRadians(Math.abs(angledegrees));
				double radius = arclengthmeters/angleradians;

				final int degreespermetermin = 6; // pwm 100
				final int degreespermetermax = 70; // pwm 25
				final int minpwmarc = 25; // non-comped

				int rate = degreespermeter;
				if (rate > degreespermetermax) rate = degreespermetermax;
				if (rate < degreespermetermin) rate = degreespermetermin; // shouldn't be needed, filtered at top
				int pwm;
				if (rate >  40)
					pwm = (int) (minpwmarc + (degreespermetermax-rate)*0.6); // linear, not that accurate! TODO: bulge bottom end of range
				else
					pwm = (int) (41 + (41-rate)*1.928);

				// TODO: make arcmovecomp auto-adjusting
				pwm = (int) (voltsComp(pwm) * settings.getDouble(ManualSettings.arcmovecomp.toString()));

				if (angledegrees < 0) { // right, apply comp to left wheel TODO: double check comp direction!!
					state.set(State.values.direction, direction.arcright.toString());
					arcodomcomp = Math.abs(arclengthmeters / ((radius-0.13)*angleradians));
					int spd = speedfast;
					if (steeringcomp < 0) spd += steeringcomp;
					if (arclengthmeters > 0) // forwards
						sendCommand(new byte[] { FORWARD, (byte) spd, (byte) pwm});
					else sendCommand(new byte[] { BACKWARD, (byte) pwm, (byte) spd}); // backwards
				}
				else { // left
					state.set(State.values.direction, direction.arcleft.toString());
					arcodomcomp = Math.abs(arclengthmeters / ((radius+0.13)*angleradians));
					int spd = speedfast;
					if (steeringcomp > 0) spd -= steeringcomp;
					if (arclengthmeters > 0) //forwards
						sendCommand(new byte[] { FORWARD, (byte) pwm, (byte) spd});
					else sendCommand(new byte[] { BACKWARD, (byte) spd, (byte) pwm}); // backwards
				}

//				arcodomcomp *= 0.98; // 1.015;

				// sanity check
				if (arcodomcomp > 1.3) {
					Util.debug("error, whacky arcodomcomp: "+arcodomcomp, this);
					arcodomcomp = 1.3;
				}
				else if (arcodomcomp < 0.7) {
					Util.debug("error, whacky arcodomcomp: "+arcodomcomp, this);
					arcodomcomp = 0.7;
				}

				Util.delay((long) (Math.abs(arclengthmeters/0.32*1000)));

				// end of thread
				if (currentMoveID == moveID)  stopGoing();

			}
		}).start();
	}

	
	/**
	 * compensates timer (or pwm values) for drooping system voltage
	 * @param n original milliseconds
	 * @return modified (typically extended) milliseconds
	 */
	public double voltsComp(double n) {
		double volts = 12.0; // default
		final double nominalvolts = 12.0;
		final double exponent = 1.6;

		if (state.exists(State.values.batteryvolts.toString())) {
			if (Math.abs(state.getDouble(State.values.batteryvolts.toString()) - volts) > 2.5) // sanity check
				Util.log("error state:battvolts beyond expected range! "+state.get(State.values.batteryvolts), this);
			else  volts = Double.parseDouble(state.get(State.values.batteryvolts));
		}
		
		n = n * Math.pow(nominalvolts/volts, exponent);
		return n;
	}

	public double unVoltsComp(double n) {
		double volts = 12.0;
		final double nominalvolts = 12.0;
		final double exponent = 1.6;

		if (state.exists(State.values.batteryvolts.toString())) {
			if (Math.abs(state.getDouble(State.values.batteryvolts.toString()) - volts) > 2) // sanity check
				Util.log("error state:battvolts beyond expected range! "+state.get(State.values.batteryvolts), this);
			else  volts = Double.parseDouble(state.get(State.values.batteryvolts));
		}

		n = n / Math.pow(nominalvolts/volts, exponent);
		return n;
	}

	public void setInitialOdomPWM() {
		state.set(State.values.odomturnpwm,
				(int) voltsComp(Double.parseDouble(settings.readSetting(ManualSettings.odomturnpwm.name())) ) );
		state.set(State.values.odomlinearpwm,
				(int) voltsComp(Double.parseDouble(settings.readSetting(ManualSettings.odomlinearpwm.name()))));
	}

	public void delayWithVoltsComp(int n) {
		int delay = (int) voltsComp((double) n);
		Util.delay(delay);
	}

	public void clickSteer(final int x, int y) {
		clickCam(y);
		clickNudge(x, false);
	}

	public void clickNudge(final int x, final boolean firmwaretimed) {
		
		final double mult = Math.pow(((320.0 - (Math.abs(x))) / 320.0), 3)* clicknudgemomentummult + 1.0;
		final int clicknudgedelay = (int) ((maxclicknudgedelay * (Math.abs(x)) / 320) * mult);
		
		new Thread(new Runnable() {	
			public void run() {
				try {
					
					final int tempspeed = state.getInteger(State.values.motorspeed);
					state.set(State.values.motorspeed, speedfast);

					int delay = 0;
					if (firmwaretimed) delay = clicknudgedelay+FIRMWARE_TIMED_OFFSET;
					
					if (x > 0) turnRight(delay);
					else turnLeft(delay);
					
					Util.delay((int) voltsComp(clicknudgedelay));
					state.set(State.values.motorspeed, tempspeed);
					
					if (state.getBoolean(State.values.movingforward)) goForward();
					else stopGoing();
					
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();
	
	}

	public void stopGoing() {
		long moveID = System.nanoTime();
		stopGoing(moveID);
	}
	
	public void stopGoing(final long moveID) {
		currentMoveID = moveID;

		state.set(State.values.moving, false);
		state.set(State.values.movingforward, false);

		// needs deaccel!
//		if (state.getBoolean(State.values.stopbetweenmoves)) sendCommand(HARD_STOP);
//		else sendCommand(STOP);
		
		sendCommand(STOP);
		
//		if (!state.getBoolean(State.values.stopbetweenmoves)) { // firmware can call stop when odometry running
		if (!state.getBoolean(State.values.odometry)) { // firmware can call stop when odometry running
			state.set(State.values.direction, direction.stop.toString());
		}
		else { // TODO: in case MALG board doesn't report stop detect -- not needed?
			new Thread(new Runnable() {public void run() {
				Util.delay(10000); // was 1000, extended because of rotate command moveID pass thru
				if (currentMoveID == moveID)  {
					state.set(State.values.direction, direction.stop.toString());
				}

			} }).start();
		}
	}

	public void hardStop() {
		final long moveID = System.nanoTime();
		currentMoveID = moveID;

		state.set(State.values.moving, false);
		state.set(State.values.movingforward, false);
		state.set(State.values.direction, direction.stop.toString());

		sendCommand(HARD_STOP);
	}
	
	private void clickCam(final Integer y) {
		if (state.getBoolean(State.values.autodocking)) return;
		
		int n = maxclickcam * y / 240;
		n = state.getInteger(State.values.cameratilt) +n;

		if (n > CAM_MIN) { n= CAM_MIN; }
		if (n < CAM_MAX) { n= CAM_MAX; }
//		if (n == 13 || n== 10) { n=12; }
//		sendCommand(new byte[] { CAM, (byte) n });
//		long camMoveID = System.nanoTime();
//		currentCamMoveID = camMoveID;
//		camRelease(camMoveID);
//		state.set(State.values.cameratilt, n);
		camtilt(n);
	}
	
	public void setSteeringComp(String str) {
		if (str.contains("L")) { // left is negative
			steeringcomp = Integer.parseInt(str.replaceAll("\\D", "")) * -1;
		}
		else { steeringcomp = Integer.parseInt(str.replaceAll("\\D", "")); }
		steeringcomp = (int) ((double) steeringcomp * 255/100);
	}
	
	public void odometryStart() {
		if (state.getBoolean(State.values.odometry)) return;

		state.set(State.values.odometry, true);
		sendCommand(ODOMETRY_START);
		state.delete(State.values.distanceangle);
		state.set(State.values.stopbetweenmoves, true);
		state.set(State.values.odomlinearmpms, METERSPERSEC / 1000);
		state.set(State.values.odomturndpms, DEGPERMS);
		state.set(State.values.motorspeed, state.get(State.values.odomlinearpwm));

		if (state.exists(State.values.odometrybroadcast.toString()) && !odometryBroadCasting) {
			new Thread(new Runnable() {public void run() {

				odometryBroadCasting = true;

				while (state.exists(State.values.odometrybroadcast.toString()) &&
						state.exists(State.values.odometry.toString())) {
					if (state.getBoolean(State.values.odometry) &&
							state.getLong(State.values.odometrybroadcast) > 0 ) {
						Util.delay(state.getLong(State.values.odometrybroadcast));
						odometryReport();
						Util.delay(1);
					}
					else  break;
				}

				odometryBroadCasting = false;
			} }).start();
		}
	}

	public void odometryStop() {
		if (!state.getBoolean(State.values.odometry)) return;

		state.set(State.values.odometry, false);
		sendCommand(ODOMETRY_STOP_AND_REPORT);
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
