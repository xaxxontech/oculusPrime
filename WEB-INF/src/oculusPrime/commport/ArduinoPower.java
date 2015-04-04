package oculusPrime.commport;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;
import jssc.SerialPortList;
import oculusPrime.Application;
import oculusPrime.AutoDock;
import oculusPrime.GUISettings;
import oculusPrime.ManualSettings;
import oculusPrime.PlayerCommands;
import oculusPrime.Settings;
import oculusPrime.State;
import oculusPrime.Util;


public class ArduinoPower implements SerialPortEventListener  {

	public static final int DEVICEHANDSHAKEDELAY = 2000;
	public static final int DEAD_TIME_OUT = 10000; // was 15000 
	public static final int ERROR_TIME_OUT = (int) Util.ONE_MINUTE;
	public static final int WATCHDOG_DELAY = 5000;
	public static final int RESET_DELAY = 4 * (int) Util.ONE_HOUR;
	private static final int HOST_HEARTBEAT_DELAY =  (int) Util.ONE_MINUTE;
	public static final int BAUD = 115200;
	public static final String FIRMWARE_ID = "oculusPower";
	public static final byte INITIATESHUTDOWN= 'p';
	public static final byte CONFIRMSHUTDOWN= 'w'; 
	public static final byte GET_VERSION = '7';
	public static final byte READERROR = 's'; 
	public static final byte STATUSTOEEPROM = 'A';
	public static final byte TESTFORCEERRORCODE = '9';
	public static final byte CLEARALLWARNINGERRORS = 'B';
	public static final byte PING = 'X'; 
	public static final byte GET_PRODUCT = 'x';
	public static final int COMM_LOST = -99;
	
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
	
	// protected RandomAccessFile logger;
	
	// errors
	public static Map<Integer, String> pwrerr = new HashMap<Integer, String>();
	public static final int WARNING_ONLY_BELOW = 40;
	public static final int RESET_REQUIRED_ABOVE= 19;
	public static final List<Integer> IGNORE_ERROR = Arrays.asList(1,4);  // log only, suppress gui warnings:
	
	
	public ArduinoPower(Application app) {
		application = app;	
		state.put(State.values.powerport, portname);
		
		Map<String, Integer> temp = new HashMap<String, Integer>();
		// (reversed declaration for easier transcribing from firmware)
		// ERROR CODES
		temp.put("ERROR_NO_ERROR_ALL_IS_WELL",0);
		// EROR CODES, WARNING (1-19):
		temp.put("ERROR_ATTEMPT_ZERO_CURRENT_WHEN_ACTIVE",			1); 
		temp.put("ERROR_HIGH_CURRENT_SHUTDOWN", 					2);
		temp.put("ERROR_NO_BATTERY_CONNECTED", 						3); 
		temp.put("ERROR_WALL_BRICK_LOW_VOLTAGE", 					4);
		temp.put("ERROR_OVER_DISCHARGED_PACK",						5);
		//ERROR CODES, WARNING SAFE CHARGE (20-39):
		temp.put("ERROR_PACK_DRAINED_TO_ZERO_PERCENT",				22); 
		temp.put("ERROR_NO_HOST_DETECTED", 	   						23);
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
		
		// java only, not used by firmware:
		temp.put("ERROR_NO_COMM_WITH_POWER_PCB",                    COMM_LOST); // -99, java only
		
		// inverse: 
		for(Map.Entry<String, Integer> entry : temp.entrySet()){
			pwrerr.put(entry.getValue(), entry.getKey());
		}
		
		if(!settings.readSetting(ManualSettings.powerport).equals(Settings.DISABLED)) connect();
		
		if (isconnected) { 
			initialize();
			new WatchDog().start();
		}
	}
	
	public void initialize() {
		
		Util.debug("initialize", this);
		sendCommand(READERROR);
		lastReset = System.currentTimeMillis();
	}
	
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
    				//	log("power board connected to  "+portNames[i]);
    					lastRead = System.currentTimeMillis();
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
			
			lastHostHeartBeat = System.currentTimeMillis();
			// Util.debug("run", this);
			
			while (true) {
				long now = System.currentTimeMillis();

				if (now - lastReset > RESET_DELAY && isconnected) Util.log(FIRMWARE_ID+" past reset delay", this); 
				
				if (state.exists(oculusPrime.State.values.powererror.toString())) {
					String msg = "power PCB code: " + state.get(oculusPrime.State.values.powererror);
					application.message(msg, null, null);
					application.messageGrabber(msg, "");	
					Util.log(msg, this);
				}
				
				if (now - lastRead > DEAD_TIME_OUT && isconnected) {
					state.set(oculusPrime.State.values.batterylife, "TIMEOUT");
					application.message("power PCB timeout", "battery", "timeout");
					Util.log("power PCB timeout", this);
					reset();
				}
				
				if (now - lastReset > RESET_DELAY && isconnected && 
						!state.getBoolean(oculusPrime.State.values.autodocking) ){  
					application.message("power PCB periodic reset", "battery", "resetting");
					Util.log("power PCB periodic reset", this);
					Util.log("power PCB periodic reset", this);
					lastReset = now;
					reset();
				}

				if (now - lastHostHeartBeat > HOST_HEARTBEAT_DELAY && isconnected) { 
					sendCommand((byte) PING); // no response expected
					lastHostHeartBeat = now;
				}
				
				if (now - lastRead > ERROR_TIME_OUT && !isconnected) { // comm with pcb lost!
					if (state.exists(oculusPrime.State.values.powererror.toString())){ 
						state.set(oculusPrime.State.values.powererror, ","+COMM_LOST);
					} else { 
						state.set(oculusPrime.State.values.powererror, COMM_LOST);
					}
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
			
			if (!s[1].contains(",")) {
				int e = Integer.parseInt(s[1]);
				if (IGNORE_ERROR.contains(e) && !state.exists(State.values.powererror.toString())) {
					sendCommand(CLEARALLWARNINGERRORS);
					Util.log("Power warning "+e+", "+pwrerr.get(e)+", cleared", this); 
					return;
				}
			}
			
			if (!s[1].equals("0")) { 
				state.set(State.values.powererror, s[1]);
				application.message("from power PCB, code " + s[1], null, null);
			}
			
			else if (state.exists(State.values.powererror.toString())) {
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

				// redock if unplanned and redock set
				if (!state.exists(State.values.telnetusers.toString())) state.set(State.values.telnetusers, 0);
				if (!state.exists(State.values.driver.toString()) && state.getInteger(State.values.telnetusers) ==0 &&
						settings.getBoolean(GUISettings.redock) && state.get(State.values.dockstatus).equals(AutoDock.DOCKED)) { 
					Util.log("unplanned undock, trying redock",this);
					application.driverCallServer(PlayerCommands.redock, null);
				}
				
				state.put(State.values.dockstatus, AutoDock.UNDOCKED);
				application.message(null, "multiple", "dock "+AutoDock.UNDOCKED+" motion enabled");

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
			Util.log("POWER BOARD CALLED SYSTEM SHUTDOWN", this);
			Util.shutdown();
		}
		
		else if (s[0].equals("redock")) {
			application.driverCallServer(PlayerCommands.redock, null);
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
			// TODO: closeLog();
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
				sendCommand(new byte[]{s[0].getBytes()[0], val1, val2}); 
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
