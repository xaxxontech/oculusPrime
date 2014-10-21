package oculusPrime.commport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.TooManyListenersException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import oculusPrime.Application;
import oculusPrime.AutoDock;
import oculusPrime.ManualSettings;
import oculusPrime.Settings;
import oculusPrime.State;
import oculusPrime.Util;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;

//import java.util.regex.Matcher;
//import java.util.regex.Pattern;

public class ArduinoPower implements SerialPortEventListener  {

	public static final int SETUP = 4000;
	public static final int DEAD_TIME_OUT = 10000;
	public static final int WATCHDOG_DELAY = 5000;
	public static final int RESET_DELAY = 4 * (int) Util.ONE_HOUR;
	public static final int BAUD = 115200;
	public static final String FIRMWARE_ID = "oculusPower";
//	public static final byte BATTERYPLUSPFETOFF = 'm';
//	public static final byte BATTERYPLUSPFETON= 'n';
	public static final byte INITIATESHUTDOWN= 'p';
	public static final byte CONFIRMSHUTDOWN= 'w'; 
	public static final byte GET_VERSION = '7';
	
	protected Application application = null;
	protected State state = State.getReference();
	protected static SerialPort serialPort = null;	
	protected static OutputStream out = null;
	protected static InputStream in = null;
	
	protected volatile boolean isconnected = false;
	protected long lastRead;
	protected long lastReset;
		
	protected byte[] buffer = new byte[256];
	protected int buffSize = 0;
	protected static Settings settings = Settings.getReference();
	protected String portname = settings.readSetting(ManualSettings.powerport);
	protected String version = null;
	
	public ArduinoPower(Application app) {
		application = app;	
		state.put(State.values.powerport, portname);
		
		if(powerReady()){
			
			Util.log("attempting to connect to port "+portname, this);
			
				if (!isconnected) {
					connect();
					Util.delay(SETUP);
				}
				if(isconnected){
					
					Util.log("Connected to port: " + state.get(State.values.powerport), this);
					initialize();
				}

			
		}

		new WatchDog().start();
		
	}
	
	public void initialize() {
		
		Util.debug("initialize", this);
		
		registerListeners();
		lastRead = System.currentTimeMillis();
		lastReset = lastRead;

		
	}
	
	public void connect() {
		try {

			Util.debug("attempting connect to "+portname, this);
			serialPort = (SerialPort) CommPortIdentifier.getPortIdentifier(portname).open(ArduinoPrime.class.getName(), SETUP);
			serialPort.setSerialPortParams(BAUD, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);

			// open streams
			out = serialPort.getOutputStream();
			in = serialPort.getInputStream();

			isconnected = true;
			Util.log("connected to: "+portname, this);

		} catch (Exception e) {
			application.message("battery board connect fail", "battery", "failure");
			e.printStackTrace(); // TODO: testing, delete this 
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
	
	/** inner class to check if getting responses in timely manor */
	public class WatchDog extends Thread {
		oculusPrime.State state = oculusPrime.State.getReference();

		public WatchDog() {
			this.setDaemon(true);
		}

		public void run() {

			
			Util.delay(SETUP);
			while (true) {
//				if (isconnected)  Util.debug("watchdog", this);
				
				long now = System.currentTimeMillis();

				if (now - lastReset > RESET_DELAY && isconnected) Util.debug(FIRMWARE_ID+" past reset delay", this); 
				
				if (now - lastRead > DEAD_TIME_OUT && isconnected) {
					state.set(oculusPrime.State.values.batterylife, "TIMEOUT");
					application.message("battery PCB timeout", "battery", "timeout");
					reset();
				}
				
				if (now - lastReset > RESET_DELAY && !state.getBoolean(oculusPrime.State.values.autodocking) && 
						state.get(oculusPrime.State.values.driver) == null &&
						state.getInteger(oculusPrime.State.values.telnetusers) == 0) {
					// check for autodocking = false; driver = false; telnet = false;
					application.message("battery board periodic reset", "battery", "resetting");
					Util.log("battery board periodic reset", this);
					reset();
				}
				
				Util.delay(WATCHDOG_DELAY);
			}		
		}
	}
	
	public void reset() {
		if (isconnected) {
			new Thread(new Runnable() {
				public void run() {
					close();
					connect();
					Util.delay(SETUP);
					registerListeners();
					long now = System.currentTimeMillis();
			
					lastReset = now;
					lastRead = now;
				}
			}).start();
		}
	}
	
	@Override
	public void serialEvent(SerialPortEvent event) {
		if (event.getEventType() == SerialPortEvent.DATA_AVAILABLE) {
			manageInput();
		}
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

	private boolean powerReady(){
		final String power = state.get(State.values.powerport); 
		if(power == null) return false; 
		if(power.equals(Discovery.params.disabled.name())) return false;
		if(power.equals(Discovery.params.discovery.name())) return false;
		
		return true;
	}
	
	
	/** respond to feedback from the device  */	
	public void execute() {
		String response = "";
		for (int i = 0; i < buffSize; i++)
			response += (char) buffer[i];
		
		Util.debug("serial in: " + response, this);

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
		
		else if (s[0].equals("docked")) {
			if (!state.get(State.values.dockstatus).equals(AutoDock.DOCKED)) {
				application.message(null, "dock", AutoDock.DOCKED);
				state.put(State.values.dockstatus, AutoDock.DOCKED);
				state.set(State.values.batterycharging, true);
			}
			if (state.getBoolean(State.values.motionenabled)) {
				state.set(State.values.motionenabled, false); }
		}
		
		else if (s[0].equals("undocked")) {
			if (!state.get(State.values.dockstatus).equals(AutoDock.UNDOCKED) &&
					!state.getBoolean(State.values.autodocking)) {
				state.put(State.values.dockstatus, AutoDock.UNDOCKED);
				state.put(State.values.batterylife, "draining");
				application.message(null, "multiple", "dock "+AutoDock.UNDOCKED+" battery draining");
				state.set(State.values.batterycharging, false);
			}
			if (!state.getBoolean(State.values.motionenabled)) { 
				state.set(State.values.motionenabled, true); }
		}
		
		else if (s[0].equals("shutdown")) {
			sendCommand(CONFIRMSHUTDOWN);
			Util.log("POWER BOARD CALLED SYSTEM SHUTDOWN");
			Util.shutdown();
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
			    Pattern pat = Pattern.compile("_sV:\\d+\\.\\d+");
			    Matcher mat = pat.matcher(extinfo);
			    while (mat.find()) {
			    	String sysvolts = mat.group().replaceFirst("_sV:", "");
			    	if (!state.exists(State.values.sysvolts.toString()) || 
							!state.get(State.values.sysvolts).equals(sysvolts)) {
			    		state.put(State.values.sysvolts, sysvolts);
			    	}
			    	break;
			    }
			    
			}
		}
			
		

	}
	
	public boolean isConnected() {
		return isconnected;
	}
	
	/** Close the serial port streams */
	private void close() {
		
		try {
			Util.debug("closing input stream", this);
			if (in != null) in.close(); in=null;
		} catch (Exception e) {
			Util.log("input stream close() error " + e.getMessage(), this);
		}
		try {
			Util.debug("closing output stream", this);
			if (out != null) out.close(); out=null;
		} catch (Exception e) {
			Util.log("output stream close() error" + e.getMessage(), this);
		}
		
		if (serialPort != null) {
			Util.debug("close port: " + serialPort.getName() + " baud: " + serialPort.getBaudRate(), this);
//			serialPort.removeEventListener();
			serialPort.close();
			serialPort = null;
		}
		
		isconnected = false;

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
			text += (byte)cmd[i] + " ";
		
		Util.debug(text, this);
		
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
					Util.log("ArduinoPower: sendCommand(), " + e.getMessage(), this);
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
		sendCommand(INITIATESHUTDOWN);
	}
	
//	public void prepareBatteryToDock() {
//		sendCommand(BATTERYPLUSPFETOFF);
//	}
//	
//	public void manualSetBatteryUnDocked() {
//		sendCommand(BATTERYPLUSPFETON);
//	}
}
