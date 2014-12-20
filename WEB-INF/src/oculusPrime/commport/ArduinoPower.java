package oculusPrime.commport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.TooManyListenersException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import oculusPrime.Application;
import oculusPrime.AutoDock;
import oculusPrime.ManualSettings;
import oculusPrime.PlayerCommands;
import oculusPrime.Settings;
import oculusPrime.State;
import oculusPrime.Util;
import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;
import jssc.SerialPortList;


public class ArduinoPower implements SerialPortEventListener  {

	public static final int DEVICEHANDSHAKEDELAY = 2000;
	public static final int DEAD_TIME_OUT = 10000; // was 15000 
	public static final int WATCHDOG_DELAY = 5000;
	public static final int RESET_DELAY = 4 * (int) Util.ONE_HOUR;
	private static final int HOST_HEARTBEAT_DELAY =  (int) Util.ONE_MINUTE;
	public static final int BAUD = 115200;
	public static final String FIRMWARE_ID = "oculusPower";
//	public static final String ENABLED = "enabled";
	public static final byte INITIATESHUTDOWN= 'p';
	public static final byte CONFIRMSHUTDOWN= 'w'; 
	public static final byte GET_VERSION = '7';
	public static final byte READERROR = 's'; 
	public static final byte STATUSTOEEPROM = 'A';
	public static final byte TESTFORCEERRORCODE = '9';
	public static final byte CLEARALLWARNINGERRORS = 'B';
	public static final byte PING = 'X'; 
	public static final byte GET_PRODUCT = 'x';

	
	protected Application application = null;
	protected State state = State.getReference();
	protected static SerialPort serialPort = null;	
	
	protected volatile boolean isconnected = false;
	protected static long lastRead;
	protected long lastReset;
	protected long lastHostHeartBeat;
		
	protected byte[] buffer = new byte[256];
	protected int buffSize = 0;
	protected static Settings settings = Settings.getReference();
	protected String portname = settings.readSetting(ManualSettings.powerport);
	protected String version = null;
	
	// errors
	public static Map<Integer, String> pwrerr = new HashMap<Integer, String>();
	public static final int WARNING_ONLY_BELOW = 40;
	public static final int RESET_REQUIRED_ABOVE= 19;
	
	
	public ArduinoPower(Application app) {
		application = app;	
		state.put(State.values.powerport, portname);
		
		Map<String, Integer> temp = new HashMap<String, Integer>();
		// (reversed declaration for easier transcribing from firmware)
		// ERROR CODES
		temp.put("ERROR_NO_ERROR_ALL_IS_WELL",0);
		// EROR CODES, WARNING (1-19):
		temp.put("ERROR_ATTEMPT_ZERO_CURRENT_WHEN_ACTIVE",			1); 
		temp.put("ERROR_HIGH_DRAIN_CURRENT_SHUTDOWN", 				2);
		temp.put("ERROR_NO_BATTERY_CONNECTED", 						3); 
		temp.put("ERROR_WALL_BRICK_LOW_VOLTAGE", 					4);
		temp.put("ERROR_OVER_DISCHARGED_PACK",						5);
		//ERROR CODES, WARNING SAFE CHARGE (20-39):
		temp.put("ERROR_PACK_DRAINED_TO_ZERO_PERCENT",				22); 
		temp.put("ERROR_NO_HOST_HEARTBEAT", 						23);
		//ERROR CODES, FATAL NO CHARGE (40-59):		
		temp.put("ERROR_SEVERELY_UNBALANCED_PACK",					41); 
		temp.put("ERROR_MAX_PWM_BUT_LOW_CURRENT", 					42);
		temp.put("ERROR_OVERCHARGED_CELL", 							43); 
		temp.put("ERROR_REGULATOR_CANT_FIND_TARGET", 				44);
		temp.put("ERROR_UNABLE_TO_COMPLETE_CHARGE", 				45); 
		temp.put("ERROR_CHARGE_TIMED_OUT",							46);		
		//ERROR CODES, FATAL SAFE CHARGE (60-79):
		temp.put("ERROR_POWER_SOFT_SWITCH_FAIL",					61);
		temp.put("ERROR_EEPROM_ROLLOVER",							62);
		//ERROR CODES, FATAL NO CHARGE FORCE UNDOCK (80+):
		temp.put("ERROR_CURRENT_OFFSET_TOO_HIGH",					81);
		temp.put("ERROR_UNEXPECTED_CHARGE_CURRENT",					82);
		temp.put("ERROR_CONSISTENT_OVER_CHARGE",					83);
		// inverse: 
		for(Map.Entry<String, Integer> entry : temp.entrySet()){
			pwrerr.put(entry.getValue(), entry.getKey());
		}
		
		if(!settings.readSetting(ManualSettings.powerport).equals(Settings.DISABLED)) connect();
		initialize();
		new WatchDog().start();
		
	}
	
	public void initialize() {
		
		Util.debug("initialize", this);
		sendCommand(READERROR);
		lastRead = System.currentTimeMillis();
		lastReset = lastRead;
		
	}
	
	/**
	 * port query and connect 
	 */
	private void connect() {
		isconnected = false;
		
    	String[] portNames = SerialPortList.getPortNames();
        if (portNames.length == 0) return;
        
        String otherdevice = "";
        if (state.exists(State.values.motorport.toString())) 
        	otherdevice = state.get(State.values.motorport);
        
        for (int i=0; i<portNames.length; i++) {
    		if (portNames[i].matches("/dev/ttyUSB.+") && !portNames[i].equals(otherdevice)) {

    			try {
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
        			
    				if (device.trim().startsWith("id")) device = device.substring(2, device.length());
    				Util.debug(device+" "+portNames[i], this);
    				
    				if (device.equals(FIRMWARE_ID)) {

    					Util.log("power board connected to  "+portNames[i], this);
    					
    					isconnected = true;
    					state.set(State.values.powerport, portNames[i]);
//    	        		serialPort.setEventsMask(SerialPort.MASK_DSR);//Set mask to data ready
    		            serialPort.addEventListener(this, SerialPort.MASK_RXCHAR);//Add SerialPortEventListener
    					break; // job done, don't read any more ports
    				}
    				serialPort.closePort();

    			} catch (Exception e) {	Util.log("can't connect to port: " + e.getMessage(), this); }
        	}
        }
		
	}
	
	/** inner class to check if getting responses in timely manor */
	public class WatchDog extends Thread {
		oculusPrime.State state = oculusPrime.State.getReference();

		public WatchDog() {
			this.setDaemon(true);
		}

		public void run() {
			
//			Util.delay(Util.ONE_MINUTE);
			long now = System.currentTimeMillis();
			lastReset = now;
			lastHostHeartBeat = now;
			Util.debug("run", this);
			
			while (true) {
				now = System.currentTimeMillis();

				if (now - lastReset > RESET_DELAY && isconnected) Util.debug(FIRMWARE_ID+" past reset delay", this); 
				
				if (state.exists(oculusPrime.State.values.powererror.toString())) {
					String msg = "power PCB code: " + state.get(oculusPrime.State.values.powererror);
					application.message(msg, null, null);
					application.messageGrabber(msg, "");
				}
				
				if (now - lastRead > DEAD_TIME_OUT && isconnected) {
					state.set(oculusPrime.State.values.batterylife, "TIMEOUT");
					application.message("power PCB timeout", "battery", "timeout");
					lastRead = now;
					reset();
				}
				
				if (now - lastReset > RESET_DELAY && isconnected && 
						!state.getBoolean(oculusPrime.State.values.autodocking) ){  
					application.message("power PCB periodic reset", "battery", "resetting");
					Util.log("power PCB periodic reset", this);
					lastReset = now;
					reset();
				}

				if (now - lastHostHeartBeat > HOST_HEARTBEAT_DELAY && isconnected) { 
					sendCommand((byte) PING); // no response expected
					lastHostHeartBeat = now;
				}

				Util.delay(WATCHDOG_DELAY);
			}		
		}
	}
	
	public void reset() {

			new Thread(new Runnable() {
				public void run() {
					writeStatusToEeprom(); // this may throw error if port lost
					Util.delay(100); 
					
					Util.log("resetting Power board", this);
					disconnect();
					connect();
					initialize();
				}
			}).start();

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
	
	
	/** respond to feedback from the device  */	
	public void execute() {
		String response = "";
		for (int i = 0; i < buffSize; i++)
			response += (char) buffer[i];
		
		Util.log("serial in: " + response, this);

		String s[] = response.split(" ");
		
		if(s[0].equals("reset")) {
//			application.message(this.getClass().getName() + "arduinOculusPower board reset", null, null);
			version = null;
			sendCommand(GET_VERSION); 
			return;
		} 
	
		if(s[0].equals("version")) {
			version = s[1];
			application.message("power board firmware version: " + version, null, null);
			return;
		} 

		else if (s[0].equals("timeout")) {
			state.put(State.values.dockstatus, AutoDock.UNKNOWN);
			state.put(State.values.batterylife, s[0]);
			application.message(null, "battery", s[0]);
			return;
		}
		
		
		else if (s[0].equals("power_error")) {
			if (!s[1].equals("0")) { 
				state.set(State.values.powererror, s[1]);
				application.message("from power PCB, code " + s[1], null, null);
			}
			else  if (state.exists(State.values.powererror.toString())) {
				state.delete(State.values.powererror);
				application.message("power PCB code cleared", null, null);
			}
			return;
		}
		
		else if (s[0].equals("docked")) {

			if (!state.get(State.values.dockstatus).equals(AutoDock.DOCKED)) {
				application.message(null, "multiple", "dock "+AutoDock.DOCKED+" motion disabled");
				state.put(State.values.dockstatus, AutoDock.DOCKED);
			}
		}
		
		else if (s[0].equals("undocked")) {
			if (state.getBoolean(State.values.wallpower)) {
				state.set(State.values.wallpower, false);
				state.set(State.values.motionenabled, true); 
			}
			if (!state.get(State.values.dockstatus).equals(AutoDock.UNDOCKED) &&
					!state.getBoolean(State.values.autodocking)) {
				state.put(State.values.dockstatus, AutoDock.UNDOCKED);
//				state.put(State.values.batterylife, "draining");
//				application.message(null, "dock", AutoDock.UNDOCKED);
				application.message(null, "multiple", "dock "+AutoDock.UNDOCKED+" motion disabled");
			}

		}
		
		else if (s[0].equals("wallpower")) {
			if (!state.getBoolean(State.values.wallpower)) {
				state.set(State.values.wallpower, true);
				state.set(State.values.motionenabled, false);
			}
		}
		
		else if (s[0].equals("shutdown")) {
			sendCommand(CONFIRMSHUTDOWN);
			Util.log("POWER BOARD CALLED SYSTEM SHUTDOWN");
			Util.shutdown();
		}
		
		else if (s[0].equals("redock")) {
			application.playerCallServer(PlayerCommands.redock, null);
		}
		
		else if (s[0].equals("force_undock")) {
			state.set(State.values.forceundock, true);
		}
		
		if (s.length>2) {
			String battinfo = s[1]; 
			battinfo = battinfo.replaceFirst("\\.\\d*", "");
			if (!state.get(State.values.batterylife).equals(battinfo)) {
				state.put(State.values.batterylife, battinfo);
			
			}
	
			String extinfo = s[2];
			if (!state.exists(State.values.batteryinfo.toString()) || 
						!state.get(State.values.batteryinfo).equals(extinfo)) {
	
				state.put(State.values.batteryinfo, extinfo);
	
				// extract sysvolts '_sV:'
//			    Pattern pat = Pattern.compile("_sV:\\d+\\.\\d+");
//			    Matcher mat = pat.matcher(extinfo);
//			    while (mat.find()) {
//			    	String sysvolts = mat.group().replaceFirst("_sV:", "");
//			    	if ((!state.exists(State.values.sysvolts.toString()) || 
//							!state.get(State.values.sysvolts).equals(sysvolts))) {
////							&& !state.getBoolean(State.values.moving) && ! state.getBoolean(State.values.wallpower)) { 
//			    		// sysvolts only used by voltscomp, so only update when undocked and NOT moving
//			    		state.put(State.values.sysvolts, sysvolts);
//			    	}
//			    	break;
//			    }
			    
			    // extract battvolts '_lV:'
			    Pattern pat = Pattern.compile("_lV:\\d+\\.\\d+");
			    Matcher mat = pat.matcher(extinfo);
			    while (mat.find()) {
			    	String battvolts = mat.group().replaceFirst("_lV:", "");
			    	if ((!state.exists(State.values.battvolts.toString()) || 
							!state.get(State.values.battvolts).equals(battvolts))) {
			    		state.put(State.values.battvolts, battvolts);
			    	}
			    }
			    
			}
		}
			
		

	}
	
	protected void disconnect() {
		try {
			isconnected = false;
			serialPort.closePort();
			state.delete(State.values.powerport);
		} catch (Exception e) {
			Util.log("error in disconnect(): " + e.getMessage(), this);
		}
	}

	
	/**
	 * Send a multiple byte command to send the device
	 * 
	 * @param command
	 *            is a byte array of messages to send
	 */
	public void sendCommand(byte[] cmd) {

		if (!isconnected) return;
		
		String text = "sendCommand(): " + (char)cmd[0] + " ";
		for(int i = 1 ; i < cmd.length ; i++) 
			text += ((byte)cmd[i] & 0xFF) + " ";   // & 0xFF converts to unsigned byte
		Util.log(text, this);
		
		final byte[] command = cmd;
		new Thread(new Runnable() {
			public void run() {
				try {

					serialPort.writeBytes(command);  // byte array
					serialPort.writeInt(13);  					// end of command
		
				} catch (Exception e) {
					Util.log("ArduinoPower: sendCommand(), ERROR " + e.getMessage(), this);
				}
			}
		}).start();
		
		// track last write
//		lastSent = System.currentTimeMillis();
	}
	
	private void sendCommand(final byte cmd){
		sendCommand(new byte[]{cmd});
	}
	
	public void shutdown() {
		if (state.get(State.values.dockstatus).equals(AutoDock.DOCKED)) {
			application.message("can't power down when docked", null, null);
		}
		else sendCommand(INITIATESHUTDOWN);
	}
	
	public void writeStatusToEeprom() {
		sendCommand(STATUSTOEEPROM);
	}
	
	
	/**
	 * 		
	 * @param str command character followed by nothing or unsigned short (0-65536)
	 */
	public void powercommand(String str) {
		String s[] = str.split(" ");
		if (s.length == 0) return;
		else if (s.length == 1) sendCommand(str.getBytes());
		else {
			int val = Integer.parseInt(s[1]);
			if (val <= 255) sendCommand(new byte[]{s[0].getBytes()[0], (byte) val});
			else {
				byte val1 = (byte) (val & 0xFF);
				byte val2 = (byte) ((val >>8) & 0xFF);
				sendCommand(new byte[]{s[0].getBytes()[0], val2, val1}); // bigendian
			}
		}
	}
	
	public void clearWarningErrors() {

		boolean warningonly = true;
		boolean resetrequired = false;
		String code[] = state.get(State.values.powererror).split(",");
		for (int i=0; i < code.length; i++) {
			int c = Integer.parseInt(code[i]);
			if (c > WARNING_ONLY_BELOW ) warningonly = false;
			if (c > RESET_REQUIRED_ABOVE) resetrequired = true;
		}
		if (! warningonly) return;
		sendCommand(CLEARALLWARNINGERRORS);
		if (resetrequired) reset();
		//  state.powererror gets cleared by firmware if command succeeds
	}

	
}
