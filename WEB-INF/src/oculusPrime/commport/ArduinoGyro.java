package oculusPrime.commport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.TooManyListenersException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import oculusPrime.Application;
import oculusPrime.AutoDock;
import oculusPrime.ManualSettings;
import oculusPrime.State;
import oculusPrime.Util;
import oculusPrime.State.values;
import oculusPrime.commport.ArduinoPrime.direction;

public class ArduinoGyro implements SerialPortEventListener {


	protected String portname = "discovery"; // settings.readSetting(ManualSettings.powerport);
	protected Application application = null;
	protected volatile boolean isconnected = false;
	public static final int SETUP = 4000;
	public static final int DEAD_TIME_OUT = 10000;
	public static final int RESET_DELAY = 3600000;
	public static final int BAUD = 115200;
	public static final String FIRMWARE_ID = "oculusGyro";
	protected static SerialPort serialPort = null;	
	protected static OutputStream out = null;
	protected static InputStream in = null;
	protected State state = State.getReference();
	protected byte[] buffer = new byte[256];
	protected int buffSize = 0;
	public static final byte GET_PRODUCT = 'x';


	
	public ArduinoGyro(Application app) {
		application = app;	
		
		if(gyroReady()){
			
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

	private boolean gyroReady(){
		final String gyro = state.get(State.values.gyroport); 
		if(gyro == null) return false; 
		if(gyro.equals(Discovery.params.disabled.name())) return false;
		if(gyro.equals(Discovery.params.discovery.name())) return false;
		
		return true;
	}
	
	public void initialize() {
		
		Util.debug("initialize", this);
		
		registerListeners();
		
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

	/** respond to feedback from the device  */	
	public void execute() {
		String response = "";
		for (int i = 0; i < buffSize; i++)
			response += (char) buffer[i];
		
		Util.debug("serial in: " + response, this);
		
		if(response.equals("reset")) {
			application.message(this.getClass().getName() + "arduinoGyro board reset", null, null);
		} 
	
		String[] s = response.split(" ");

		if (s[0].equals("moved")) {
			int d = (int) (Double.parseDouble(s[1]) * Math.PI * 110); // TODO: 110 should be taken from settings
			double a = Double.parseDouble(s[2]);
			
//			if (state.getBoolean(State.values.controlsinverted)) {
//				d*=-1;
//				a*=-1;
//			}
			
			state.set(State.values.distanceangle, d +" "+a);
			
			// TODO: testing only ----------------
			if (!state.exists(State.values.distanceanglettl.toString())) {
				state.set(State.values.distanceanglettl, "0 0");
			}
			
			int dttl = Integer.parseInt(state.get(State.values.distanceanglettl).split(" ")[0]);
			double attl = Double.parseDouble(state.get(State.values.distanceanglettl).split(" ")[1]);
			dttl += d;
			attl += a;
			String dattl = dttl+" "+attl;
			state.set(State.values.distanceanglettl,dattl);
			
			// end of testing only ----------------
		}
		else if (s[0].equals("stop") && state.getBoolean(State.values.stopbetweenmoves)) state.set(State.values.direction, direction.stop.toString());
		else if (s[0].equals("stopdetectfail")) application.message("FIRMWARE STOP DETECT FAIL", null, null);
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
					Util.log("ERROR on sendCommand() " + e.getMessage(), this);
				}
			}
		}).start();
		
		// track last write
//		lastSent = System.currentTimeMillis();
	}
	
	public void sendCommand(final byte cmd){
		sendCommand(new byte[]{cmd});
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
			
//					lastReset = now;
//					lastRead = now;
				}
			}).start();
		}
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
	
}
