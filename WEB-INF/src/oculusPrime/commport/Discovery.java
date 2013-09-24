package oculusPrime.commport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Vector;

import oculusPrime.ManualSettings;
import oculusPrime.Settings;
import oculusPrime.State;
import oculusPrime.Util;
import gnu.io.*;

public class Discovery {
	
	// two states to watch for in settings 
	public static enum params {discovery, disabled};
	
	private static Settings settings = Settings.getReference();
	private static final String motors = settings.readSetting(ManualSettings.serialport);
	private static State state = State.getReference();
	
	public static final long RESPONSE_DELAY = 1000;
	public static final int TIMEOUT = 2000;	

	/* serial port configuration parameters */
	public static final int DATABITS = SerialPort.DATABITS_8;
	public static final int STOPBITS = SerialPort.STOPBITS_1;
	public static final int PARITY = SerialPort.PARITY_NONE;
	public static final int FLOWCONTROL = SerialPort.FLOWCONTROL_NONE;

	/* add known devices here, strings returned from the firmware */
	// public static final String ARDUINO_MOTOR_SHIELD = "arduinoShield";
	public static final String ARDUINO_PRIME = "oculusPrime";
	public static final String LIGHTS = "L";
	
	/* reference to the underlying serial port */
	private static SerialPort serialPort = null;
	private static InputStream inputStream = null;
	private static OutputStream outputStream = null;

	/* list of all free ports */
	private static Vector<String> ports = new Vector<String>();
	// private boolean lightsFound = false;
	private boolean motorsFound = false;

	/* constructor makes a list of available ports */
	public Discovery() {
		
		if(motors.equals(params.disabled.toString())) {
			Util.debug("serial port is disabled", this);
			return;
		}
		
		getAvailableSerialPorts();
		
		if(ports.size() == 0){
			Util.log("no serial ports found on host", this);
			return;
		}
		
		if(motors.equals(params.discovery.name())){		
			
			searchMotors(); 	
		
		} else { // port explicitly identified in settings		
		
			Util.debug("skipping discovery, motors specified on: " + motors, this);
			state.put(State.values.serialport, motors);
		} 
		
	}
	
	/** */
	private static String getPortName(){
		
		String name = "";
		String com = serialPort.getName();
		
		//TODO: get a port name, or full device path for linux 
		if(Settings.os.equals("linux")) return com;
		else for(int i = 0 ; i < com.length();i++)
			if(com.charAt(i) != '/' && com.charAt(i) != '.')
				name += com.charAt(i);
		
		return name;
	}
	
	/** */
	private static void getAvailableSerialPorts() {
		ports.clear();
		@SuppressWarnings("rawtypes")
		Enumeration thePorts = CommPortIdentifier.getPortIdentifiers();
		while (thePorts.hasMoreElements()) {
			CommPortIdentifier com = (CommPortIdentifier) thePorts.nextElement();
			if (com.getPortType() == CommPortIdentifier.PORT_SERIAL) ports.add(com.getName());
		}
	}

	/** connects on start up, return true is currently connected */
	private boolean connect(final String address, final int rate) {

		Util.debug("try to connect to: " + address + " buad:" + rate, this);

		try {

			/* construct the serial port */
			serialPort = (SerialPort) CommPortIdentifier.getPortIdentifier(address).open("Discovery", TIMEOUT);

			/* configure the serial port */
			serialPort.setSerialPortParams(rate, DATABITS, STOPBITS, PARITY);
			serialPort.setFlowControlMode(FLOWCONTROL);

			/* extract the input and output streams from the serial port */
			inputStream = serialPort.getInputStream();
			outputStream = serialPort.getOutputStream();
			
			Util.debug("connected: " + address + " buad:" + rate, this);
			
			Util.delay(TIMEOUT*2);
			
			doPortQuery();
			
		} catch (Exception e) {
			Util.log("error connecting to: " + address, this);
			close();
			return false;
		}

		// be sure
		if (inputStream == null) return false;
		if (outputStream == null) return false;

		return true;
	}

	/** Close the serial port streams */
	private void close() {
		
		if (serialPort != null) {
			Util.debug("close port: " + serialPort.getName() + " baud: " + serialPort.getBaudRate(), this);
			serialPort.close();
			serialPort = null;
		}
		
		try {
			if (inputStream != null) inputStream.close();
		} catch (Exception e) {
			Util.log("input stream close():" + e.getMessage(), this);
		}
		try {
			if (outputStream != null) outputStream.close();
		} catch (Exception e) {
			Util.log("output stream close():" + e.getMessage(), this);
		}
	}
	
	/** Loop through all available serial ports and ask for product id's 
	public void searchLights() {
	
		// try to limit searching
		if(ports.contains(motors)) ports.remove(motors);
		if(motors != null) ports.remove(motors);
			
		Util.debug("discovery for lights starting on ports: " + ports.size(), this);
		
		for (int i = ports.size() - 1; i >= 0; i--) {
			if (lightsFound) { break; } // stop if find it
			if (connect(ports.get(i), 57600)) {
				Util.delay(TIMEOUT*2);
				if (serialPort != null) { close(); }
			}
		}
	}*/
	
	/** Loop through all available serial ports and ask for product id's */
	public void searchMotors() {
			
		Util.debug("discovery for motors starting on " + ports.size()+" ports", this); 
	
		for (int i=0; i<ports.size(); i++) {
			if (motorsFound) { break; } // stop if find it
			if (connect(ports.get(i), 115200)) {
				Util.delay(TIMEOUT*2);
				if (serialPort != null) { close(); }
			}
		}
	}
	
	/** check if this is a known derive, update in state */
	public void lookup(String id){	
		
		if (id == null) return;
		if (id.length() == 0) return;
		id = id.trim();
		
		if(id.startsWith("id")){	
			
			id = id.substring(2, id.length());
				
			Util.debug("found product id[" + id + "] on comm port: " +  getPortName(), this);
			
			if (id.equalsIgnoreCase(ARDUINO_PRIME)) {

//				settings.writeSettings(ManualSettings.serialport.name(), getPortName());
//				settings.writeSettings(ManualSettings.firmware.name(), ARDUINO_PRIME);
				state.set(State.values.serialport, getPortName());
				motorsFound = true;
				
			} 
		}
	}
	
	/** send command to get product id */
	public void getProduct() {
		
		try {
			inputStream.skip(inputStream.available());
		} catch (IOException e) {
			Util.log(e.getStackTrace().toString(),this);
			return;
		}
		try {
			outputStream.write(new byte[] { 'x', 13 });
		} catch (IOException e) {
			Util.log(e.getStackTrace().toString(),this);
			return;
		}

		// wait for reply
		Util.delay(RESPONSE_DELAY);
	}

	private void doPortQuery() {
		byte[] buffer = new byte[32];
		
		getProduct();
		
		String device = new String();
		int read = 0;
		try {
			Util.debug("doPortQuery, read buffer", this);
			if (inputStream.available() > 0) { //prevents linux hang
				read = inputStream.read(buffer); 
			}
			else { Util.debug("no bytes available to read", this); }
		} catch (IOException e) {
			Util.log(e.getStackTrace().toString(),this);
		}
		
		// read buffer
		Util.debug("doPortQuery, parse buffer", this);
		for (int j = 0; j < read; j++) {
			if(Character.isLetter((char) buffer[j]))
				device += (char) buffer[j];
		}
		
		lookup(device);
		close();
	}
}

