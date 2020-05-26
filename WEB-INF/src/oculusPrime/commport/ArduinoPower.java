package oculusPrime.commport;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;
import jssc.SerialPortList;
import oculusPrime.*;

public class ArduinoPower implements SerialPortEventListener  {

	public static final double FIRMWARE_VERSION_REQUIREDV1 = 0.957; // trailing zeros ignored!
	public static final String FIRMWARE_IDV1 = "oculusPower";
	public static final double FIRMWARE_VERSION_REQUIREDV2 = 0.24; // trailing zeros ignored!
	public static final String FIRMWARE_IDV2 = "xaxxonpowerv2";
	public static final int DEVICEHANDSHAKEDELAY = 2000;
	public static final int DEAD_TIME_OUT = 15000;
	public static final int ALLOW_FOR_RESET = 10000;
	public static final int ERROR_TIME_OUT = (int) Util.ONE_MINUTE;
	public static final int WATCHDOG_DELAY = 5000;
	public static final int RESET_DELAY = 12 * (int) Util.ONE_HOUR; // xaxxonpower clock rollover is ~18 hrs
	private static final int HOST_HEARTBEAT_DELAY =  (int) Util.ONE_MINUTE;
	public static final int BAUD = 115200;
	public static final byte INITIATESHUTDOWN= 'p';
	public static final byte CONFIRMSHUTDOWN= 'w'; 
	public static final byte GET_VERSION = '7';
	public static final byte READERROR = 's'; 
	public static final byte STATUSTOEEPROM = 'A';
	public static final byte TESTFORCEERRORCODE = '9';
	public static final byte CLEARALLWARNINGERRORS = 'B';
	public static final byte READCAPACITY = '6';
	public static final byte PING = 'X'; 
	public static final byte GET_PRODUCT = 'x';
	public static final byte READ_SAFECURRENT = 'G';
	public static final byte READ_DOCKVOLTAGE = 'E';
	public static final byte TEMPORARY_SHUTDOWN = 'K';
	public static final int COMM_LOST = -99;
//	public static final int LOWBATTPERCENTAGE = 90; // same or less than firmware var: gettingLowCell to enable capacity recalc

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
//	protected String version = null;
	private double firmwareversion = 0;
	public String boardid;

	// errors
	public static Map<Integer, String> pwrerr = new HashMap<Integer, String>();
	public static final int WARNING_ONLY_BELOW = 40;
	public static final int RESET_REQUIRED_ABOVE= 19;
	public static final int FORCE_UNDOCK_ABOVE = 79;
	public static final int ERROR_NO_BATTERY_CONNECTED = 3;
	public static final List<Integer> IGNORE_ERROR = Arrays.asList(1,4,7,ERROR_NO_BATTERY_CONNECTED);  // log only, suppress gui warnings:


	private volatile List<Byte> commandList = new ArrayList<>();
	private volatile boolean commandlock = false;
	private boolean batterypresent = true;

	public ArduinoPower(Application app) {
		application = app;	
		state.set(State.values.powerport, portname);
		state.set(State.values.dockstatus, AutoDock.UNKNOWN);
		state.set(State.values.batterylife, AutoDock.UNKNOWN);


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
		temp.put("ERROR_PACK_DRAINED_TO_ZERO_PERCENT",				6); // (MOVED, was 22)
		temp.put("ERROR_NO_HOST_DETECTED", 	   						7); // (MOVED, was 23)
		//ERROR CODES, WARNING SAFE CHARGE (20-39):
				// nothing here at this revision level
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

//		new CommandSender().start();

		if(!settings.readSetting(ManualSettings.powerport).equals(Settings.DISABLED)) connect();
		
		if (isconnected) {
			new CommandSender().start();
			checkFirmWareVersion();
			initialize();
			new WatchDog().start();
		}
	}

	public void initialize() {
		
		Util.debug("initialize", this);
		batterypresent = true;
		sendCommand(READERROR);
		sendCommand(READCAPACITY);
		sendCommand(READ_SAFECURRENT);
		sendCommand(READ_DOCKVOLTAGE);
	}
	
	private void connect() {
		isconnected = false;
		
    	String[] portNames = SerialPortList.getPortNames();
        if (portNames.length == 0) {
        	state.delete(State.values.powerport);
        	return;
        }
        
        String otherdevice = "";
        if (state.exists(State.values.motorport.toString())) 
        	otherdevice = state.get(State.values.motorport);
        
        for (int i=0; i<portNames.length; i++) {
//			if (portNames[i].matches("/dev/tty(USB|ACM).+") && !portNames[i].equals(otherdevice)) {
    		if (portNames[i].matches("/dev/ttyUSB.+") && !portNames[i].equals(otherdevice)) {

    			try {
        			Util.log("querying port "+portNames[i], this);
        			PowerLogger.append("querying port "+portNames[i], this);
        			
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
        			
    				if (device.trim().startsWith("id")) device = device.substring(2, device.length());
    				Util.debug(device+" "+portNames[i], this);
    				
    				if (device.equals(FIRMWARE_IDV1) || device.equals(FIRMWARE_IDV2)) {

    					Util.log("power board connected to  "+portNames[i], this);
    					PowerLogger.append("power board connected to  "+portNames[i], this);
    					
    					lastRead = lastReset = System.currentTimeMillis();
    					isconnected = true;
    					state.set(State.values.powerport, portNames[i]);
    		            serialPort.addEventListener(this, SerialPort.MASK_RXCHAR);//Add SerialPortEventListener
						boardid = device;
    					break; // job done, don't read any more ports
    				}
    				serialPort.closePort();

    			} catch (Exception e) {
					PowerLogger.append("can't connect to port: " + e.getMessage(), this);
					Util.log("can't connect to port: " + e.getMessage(), this);
					state.delete(State.values.powerport);
				}
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
			
			while (true) {
				long now = System.currentTimeMillis();
				
				// Util.debug("run....", this);

//				if (now - lastReset > RESET_DELAY && isconnected) PowerLogger.append(FIRMWARE_ID+" past reset delay", this);
				
				if (state.exists(oculusPrime.State.values.powererror)) {
					final String msg = "power PCB code: " + state.get(oculusPrime.State.values.powererror);
					application.message(msg, null, null);
					application.messageGrabber(msg, "");	
					Util.log(msg, this);
					PowerLogger.append(msg, this);
				}
				
				if (now - lastRead > DEAD_TIME_OUT && isconnected) {
					timeoutlogmessages();
					reset();
					Util.delay(ALLOW_FOR_RESET);
				}
				
				if (now - lastReset > RESET_DELAY && isconnected &&
						!state.getBoolean(oculusPrime.State.values.autodocking) ){
					application.message("power PCB periodic reset", "battery", "resetting");
					Util.log("power PCB periodic reset", this);
					PowerLogger.append("power PCB periodic reset", this);
					reset();
					Util.delay(ALLOW_FOR_RESET);
				}

				if (now - lastHostHeartBeat > HOST_HEARTBEAT_DELAY && isconnected) { 
					sendCommand((byte) PING); // no response expected
					lastHostHeartBeat = now;
				}
				
				if (now - lastRead > ERROR_TIME_OUT && !isconnected) { // comm with pcb lost!
					if (state.exists(oculusPrime.State.values.powererror.toString())){
						String err = state.get(oculusPrime.State.values.powererror);
						if (!err.matches(".*"+COMM_LOST+"$")) { // only set once

							// one last reset try
							reset();
							long start = System.currentTimeMillis();
							while (!isconnected && System.currentTimeMillis() - start < ALLOW_FOR_RESET)
								Util.delay(10);

							if (!isconnected) { // still not connected after waiting for reset
								state.set(oculusPrime.State.values.powererror, err + "," + COMM_LOST);
							}

						}

					} else {

						// one last reset try
						reset();
						long start = System.currentTimeMillis();
						while (!isconnected && System.currentTimeMillis() - start < ALLOW_FOR_RESET)
							Util.delay(10);

						if (!isconnected) // still not connected after waiting for reset
							state.set(oculusPrime.State.values.powererror, COMM_LOST);

					}
				}

				Util.delay(WATCHDOG_DELAY);
			}		
		}
	}

	private void timeoutlogmessages() {
		state.set(oculusPrime.State.values.batterylife, "TIMEOUT");
		application.message("power PCB timeout", "battery", "timeout");
		Util.log("power PCB timeout", this);
		PowerLogger.append("power PCB timeout", this);
	}


	public void reset() {
			new Thread(new Runnable() {
				public void run() {
					writeStatusToEeprom(); // this may throw error if port lost
					Util.delay(100); 
					
					Util.log("resetting Power board", this);
					PowerLogger.append("resetting Power board",this);
					disconnect();
					connect();
					initialize();
				}
			}).start();
	}

	private void checkFirmWareVersion() {
		if (!isconnected) return;

		double versionrequired;
		if (boardid.equals(FIRMWARE_IDV1)) versionrequired = FIRMWARE_VERSION_REQUIREDV1;
		else if (boardid.equals(FIRMWARE_IDV2)) versionrequired = FIRMWARE_VERSION_REQUIREDV2;
		else return;

		firmwareversion = 0;
		sendCommand(GET_VERSION);
		long start = System.currentTimeMillis();
		while(firmwareversion == 0 && System.currentTimeMillis() - start < 10000) { Util.delay(100);  }
		if (firmwareversion == 0) {
			String msg = "failed to determine current "+boardid+" firmware version";
			Util.log("error, "+msg, this);
			PowerLogger.append(msg, this);
			state.set(State.values.guinotify, msg);
			return;
		}
		if (firmwareversion != versionrequired) {

			if (state.get(State.values.osarch).equals(Application.ARM)) {// TODO: add ARM avrdude to package!
				String msg = "current power firmware: "+firmwareversion+
						" out of date! Update to: "+versionrequired;
//				state.set(State.values.guinotify, msg);
				Util.log(msg, this);
				PowerLogger.append(msg, this);
				return;
			}

			Util.log("Required "+boardid+" firmware version is "+versionrequired+", attempting update...", this);
			String port = state.get(State.values.powerport); // disconnect() nukes this state value
			disconnect();

			// note: blocking
			Updater.updateFirmware(boardid, versionrequired, port);

			connect();

			// check if successful
			firmwareversion = 0;
			sendCommand(GET_VERSION);
			start = System.currentTimeMillis();
			while(firmwareversion == 0 && System.currentTimeMillis() - start < 10000)  { Util.delay(100); }
			if (firmwareversion != versionrequired) {
				String msg = "unable to update " + boardid + " firmware to version "+versionrequired;
				Util.log("error, "+msg, this);
				state.set(State.values.guinotify, msg);
			}
		}
	}
		
	public void serialEvent(SerialPortEvent event) {
		if (!event.isRXCHAR())  return;

		try {
			byte[] input = new byte[32];
			
			if(serialPort == null){
				Util.log("serial port is null", this);
				PowerLogger.append("serial port is null", this);
				return;
			}
			
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
			PowerLogger.append("serialEvent:" + e.getLocalizedMessage(), this);
		}
	}
	
	
	/** respond to feedback from the device  */	
	public void execute() {
		String response = "";
		for (int i = 0; i < buffSize; i++)
			response += (char) buffer[i];
		
		PowerLogger.append("serial in: " + response, this);
		
		String s[] = response.split(" ");

		if(s[0].equals("version")) {
			Util.log("power firmware version: " + s[1], this);
			firmwareversion = Double.valueOf(s[1]);
			return;
		} 

		else if (s[0].equals("timeout")) { // TODO: never happens? no 'timeout' string in firmware 0.955
			state.set(State.values.dockstatus, AutoDock.UNKNOWN);
			state.set(State.values.batterylife, s[0]);
			application.message(null, "battery", s[0]);
			return;
		}

		else if (s[0].equals("power_error")) {
			
			if (!s[1].contains(",")) {
				int e = Integer.parseInt(s[1]);
				if (IGNORE_ERROR.contains(e) && !state.exists(State.values.powererror.toString())) {

					if (e == ERROR_NO_BATTERY_CONNECTED)   batterypresent = false;
					else {
						sendCommand(CLEARALLWARNINGERRORS);
						batterypresent = true;
					}

					Util.log("Power warning " + e + ", "+pwrerr.get(e)+", cleared", this);
					PowerLogger.append("Power warning "+e+", "+pwrerr.get(e)+", cleared", this);
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
				application.message(null, "multiple", "dock " + AutoDock.DOCKED + " motion disabled");
				state.set(State.values.dockstatus, AutoDock.DOCKED);
                application.comport.strobeflash("on", 120, 20);

				if (state.getBoolean(State.values.autodocking) && !state.getBoolean(State.values.docking))
					application.driverCallServer(PlayerCommands.move, ArduinoPrime.direction.stop.name()); // calls autodockcancel
			}

			if (!state.equals(State.values.redockifweakconnection, Settings.TRUE))
				state.set(State.values.redockifweakconnection, true);
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
				if (!state.exists(State.values.driver.toString()) && state.getInteger(State.values.telnetusers) == 0 &&
						settings.getBoolean(GUISettings.redock) && state.get(State.values.dockstatus).equals(AutoDock.DOCKED) &&
						 !state.getBoolean(State.values.forceundock) ) {
					Util.log("unplanned undock, trying redock",this);
					PowerLogger.append("unplanned undock, trying redock",this);
					application.driverCallServer(PlayerCommands.redock, null);
				}
				
				state.set(State.values.dockstatus, AutoDock.UNDOCKED);
				application.message(null, "multiple", "dock " + AutoDock.UNDOCKED + " motion enabled");
				state.delete(State.values.redockifweakconnection);
			}
		}
		
		else if (s[0].equals("wallpower")) {
			if (!state.getBoolean(State.values.wallpower)) {
				state.set(State.values.wallpower, true);
				state.set(State.values.motionenabled, false);
			}
		}

		// debugenabled skips graceful shutdown, straight to power cut
		else if (s[0].equals("shutdown")) {
			sendCommand(CONFIRMSHUTDOWN);
			Util.log("POWER BOARD CALLED SYSTEM SHUTDOWN", this);
			PowerLogger.append("POWER BOARD CALLED SYSTEM SHUTDOWN", this);
			application.driverCallServer(PlayerCommands.systemshutdown, null);
		}
		
		else if (s[0].equals("redock") && state.getUpTime() > Util.TWO_MINUTES) {
			if (!state.exists(State.values.redockifweakconnection))
				state.set(State.values.redockifweakconnection, false);
			if(settings.getBoolean(ManualSettings.redockifweakconnection) &&
					state.getBoolean(State.values.redockifweakconnection)) {
				application.driverCallServer(PlayerCommands.redock, null);
				PowerLogger.append("redock", this);
				Util.log("redock", this);
			}
			else {
				PowerLogger.append("error, redock due to weak connection, skipped", this);
				Util.log("error, redock due to weak connection, skipped", this);
			}
		}
		
//		else if (s[0].equals("force_undock")) { // moved to watchdog
//			state.set(State.values.forceundock, true);
//			Util.log("force undock", this);
//			PowerLogger.append("force undock", this);
//		}

		else if (s[0].equals("high_current")) {
			application.driverCallServer(PlayerCommands.move, ArduinoPrime.direction.stop.toString());
			if (state.getBoolean(State.values.motionenabled))
				application.driverCallServer(PlayerCommands.motionenabletoggle, null);
			application.message("high current detected", null, null);
			Util.log("error, high current warning, stopping motors, disabling motion", this);
		}
		
		if (s.length>2) {
			String battinfo = s[1];
			if (batterypresent)
				battinfo = battinfo.replaceFirst("\\.\\d*", "");
			else battinfo = "DISCONNECTED";
			if (!state.get(State.values.batterylife).equals(battinfo)) {
				state.set(State.values.batterylife, battinfo);
			}
	
			String extinfo = s[2];
			if (!state.exists(State.values.batteryinfo.toString()) || 
						!state.get(State.values.batteryinfo).equals(extinfo)) {
	
				state.set(State.values.batteryinfo, extinfo);
	
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
			    	if (!state.exists(State.values.batteryvolts.toString()) ) {
						state.set(State.values.batteryvolts, battvolts);
						application.comport.setInitialOdomPWM();
					}
					else if (!state.get(State.values.batteryvolts).equals(battvolts)) {
						state.set(State.values.batteryvolts, battvolts);
					}

			    }
			    
			}
		}
	}
	
	private void disconnect() {
		try {
			isconnected = false;
			state.delete(State.values.powerport);
			serialPort.closePort();
		} catch (Exception e) {
			Util.log("error in disconnect(): " + e.getMessage(), this);
		}
	}

	public boolean isConnected() {
		return isconnected;
	}

	/** check if alive
	 *  BLOCKING
	 * 	return true if connected, if not, wait for up to 10 seconds
	 * @return   boolean isconnected
	 */
	public boolean checkisConnectedBlocking() {
		long start = System.currentTimeMillis();
		if (!isconnected) {
			while (!isconnected && System.currentTimeMillis() - start < ALLOW_FOR_RESET)
				Util.delay(50);
		}
		if (!isconnected) { // still not connected after waiting for rest
			Util.log("power pcb not connected", this);
			PowerLogger.append("power pcb not connected", this);
			return false;
		}

		// isconnected true, but could be in the midst of timing out... send ping
		start = System.currentTimeMillis();
		lastRead = start; // set to now so not fighting with watchdog's 15 sec timeout
		sendCommand(GET_PRODUCT); // response expected immediately
		while (lastRead == start && System.currentTimeMillis() - start < 1000) Util.delay(1);

		if (lastRead == start) { // no response to ping, try reset once
			timeoutlogmessages();
			isconnected = false;
			reset();
			start = System.currentTimeMillis();
			while (!isconnected && System.currentTimeMillis() - start < ALLOW_FOR_RESET)
				Util.delay(10);

			if (!isconnected) { // still not connected after waiting for rest
				Util.log("power pcb not connected after waiting for reset", this);
				PowerLogger.append("power pcb not connected after waiting for reset", this);
				return false;
			}
		}

		return true;
	}
	
	/**
	 * Send a multiple byte command to send the device
	 * 
	 * @param cmd
	 *            is a byte array of messages to send
	 */
	private void sendCommand(byte[] cmd) {

		String text = "sendCommand(): " + (char)cmd[0] + " ";
		for(int i = 1 ; i < cmd.length ; i++) 
			text += ((byte)cmd[i] & 0xFF) + " ";   // & 0xFF converts to unsigned byte
		
		PowerLogger.append(text, this);



		int n = 0;
		while (commandlock) {
			Util.delay(1);
			n++;
		}

		commandlock = true;
		if (n!=0) Util.log("error, commandlock true for "+n+"ms", this);
		for (byte b : cmd) commandList.add(b);
		commandList.add((byte) 13); // EOL
		commandlock = false;

	}
	
	private void sendCommand(final byte cmd){
		sendCommand(new byte[]{cmd});
	}

	/** inner class to send commands to port in sequential order */
	public class CommandSender extends Thread {

		public CommandSender() {
			this.setDaemon(true);
		}

		public void run() {

			while (true) {
				if (commandList.size() > 0 &! commandlock) {

					if (commandList.size() > 15) {
						commandList.clear();
						Util.log("error, command stack up, all dropped", this);
						Util.delay(1);
						continue;
					}

					int EOLindex = commandList.indexOf((byte) 13);
					if (EOLindex == -1) {
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
						Util.log("sendCommand() error, dropped, continuing: ", this);
						Util.printError(e);
						commandList.clear();
						Util.delay(1);
						continue;
					}


					try {
						serialPort.writeBytes(c); // writing as array ensures goes at baud rate?

					} catch (Exception e) {
						Util.log("sendCommand() error", this); // , attempting reset", this);
						Util.printError(e);
					}

				}

				Util.delay(1);

			}
		}
	}

	public void shutdown(String seconds) {
		if (state.get(State.values.dockstatus).equals(AutoDock.DOCKED)) {
			application.message("unable to power down when docked", null, null);
			return;
		}

		if (boardid.equals(FIRMWARE_IDV1) || seconds == null) {
			sendCommand(INITIATESHUTDOWN);
			return;
		}

		// boardid.equals(FIRMWARE_IDV2), seconds != null
		if (seconds.matches("\\d+"))
			powercommand(new String(new byte[] {TEMPORARY_SHUTDOWN})+" "+seconds);
		else
			sendCommand(INITIATESHUTDOWN);
	}
	
	public void writeStatusToEeprom() {
		sendCommand(STATUSTOEEPROM);
	}
	
	
	/** @param str command character followed by nothing or unsigned short (0-65536) */
	public void powercommand(String str) {
		String s[] = str.split(" ");
		if (s.length == 0) return;
		else if (s.length == 1) sendCommand(str.getBytes());
		else {
			int val = Integer.parseInt(s[1]);
			if (val <= 255 && s[0].getBytes()[0] != TEMPORARY_SHUTDOWN)
				sendCommand(new byte[]{s[0].getBytes()[0], (byte) val});
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