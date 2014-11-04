package oculusPrime.commport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Vector;

import oculusPrime.Application;
import oculusPrime.ManualSettings;
import oculusPrime.Settings;
import oculusPrime.State;
import oculusPrime.Util;
import gnu.io.*;

public class Discovery {
	
	// two states to watch for in settings 
	public static enum params {discovery, disabled};
	
	private static Settings settings = Settings.getReference();
	private static State state = State.getReference();
	private Application application = null;
	
	public static final long RESPONSE_DELAY = 1000;
	public static final int TIMEOUT = 2000;	
	public static final int BAUD = 115200;

	/* serial port configuration parameters */
	public static final int DATABITS = SerialPort.DATABITS_8;
	public static final int STOPBITS = SerialPort.STOPBITS_1;
	public static final int PARITY = SerialPort.PARITY_NONE;
	public static final int FLOWCONTROL = SerialPort.FLOWCONTROL_NONE;

	/* reference to the underlying serial port */
	private static SerialPort serialPort = null;
	private static InputStream inputStream = null;
	private static OutputStream outputStream = null;

	/* list of all free ports */
	private static Vector<String> ports = new Vector<String>();
	
	private static Vector<String> searchDevices = new Vector<String>();

	/* constructor makes a list of available ports */
	/* order: discovery > searchDevice > connect > doPortQuery > getProduct > lookup
	 * 
	 */
	public Discovery(Application app) {	
		
		application = app;
		
		if(settings.getBoolean(ManualSettings.diagnostic)) return;
		
		getAvailableSerialPorts();
		
		if(ports.size() == 0){
			Util.log("no serial ports found on host", this);
			return;
		}
		
		
		if(application.powerport.portname.equals(params.discovery.name())){	
			searchDevices.add(ArduinoPower.FIRMWARE_ID);
			
		} else { // port explicitly identified in settings		
			Util.debug("skipping discovery, power specified on: " + application.powerport.portname, this);
		} 
		
		
		if(application.comport.portname.equals(params.discovery.name())){	
			searchDevices.add(ArduinoPrime.FIRMWARE_ID);
			
		} else { // port explicitly identified in settings		
			Util.debug("skipping discovery, motors specified on: " + application.comport.portname, this);
		} 

		
//		if(application.gyroport.portname.equals(params.discovery.name())){	
//			searchDevices.add(ArduinoGyro.FIRMWARE_ID);
//			
//		} else { // port explicitly identified in settings		
//			Util.debug("skipping discovery, gyro specified on: " + application.gyroport.portname, this);
//		} 

		
		if (!searchDevices.isEmpty())  searchDevice(); 
		
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
			if (com.getPortType() == CommPortIdentifier.PORT_SERIAL) { 
				String name = com.getName();
				if (name.matches("/dev/ttyUSB.+")) ports.add(com.getName());
//				ports.add(com.getName());
			}
		}
	}

	/** connects on start up, return true is currently connected */
	private void connect(final String address) {

		Util.debug("try to connect to: " + address, this);

		try {

			/* construct the serial port */
			serialPort = (SerialPort) CommPortIdentifier.getPortIdentifier(address).open("Discovery", TIMEOUT);

			/* configure the serial port */
			serialPort.setSerialPortParams(BAUD, DATABITS, STOPBITS, PARITY);
			serialPort.setFlowControlMode(FLOWCONTROL);

			/* extract the input and output streams from the serial port */
			inputStream = serialPort.getInputStream();
			outputStream = serialPort.getOutputStream();
			
			Util.debug("connected: " + address, this);
			
			Util.delay(TIMEOUT*2);
			
			doPortQuery();
			
		} catch (Exception e) {
			Util.log("error connecting to: " + address, this);
			close();
//			return false;
		}

		// be sure
//		if (inputStream == null) return false;
//		if (outputStream == null) return false;
//
//		return true;
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
	

	/** Loop through all available serial ports and ask for product id's */
	private void searchDevice() {
			
		Util.debug("discovery for "+Integer.toString(searchDevices.size())+" device(s) starting on " 
				+ ports.size()+" ports", this);
		
		int size = ports.size();
		for (int i=0; i<size; i++) {
			connect(ports.get(i)); 
			Util.delay(TIMEOUT*2);
		}
	}
	
	
	/** check if this is a known derive, update in state */
	private void lookup(String id){	
		
		if (id == null) return;
		if (id.length() == 0) return;
		id = id.trim();
		
		if(id.startsWith("id")){	
			
			id = id.substring(2, id.length());
				
			Util.debug("found product id[" + id + "] on comm port: " +  getPortName(), this);
			
			for (int n=0; n < searchDevices.size(); n++) {
				if (id.equalsIgnoreCase(searchDevices.get(n))) {
//					state.set(deviceStateStrings.get(n), getPortName());
					
					if (id.equalsIgnoreCase(ArduinoPrime.FIRMWARE_ID)) {
						state.set(State.values.motorport, getPortName());
						application.comport.serialPort = serialPort;
						application.comport.in = inputStream;
						application.comport.out = outputStream;
						application.comport.isconnected = true;
						application.comport.portname = getPortName();
						application.comport.initialize();
						break;
					}
					
					else if (id.equalsIgnoreCase(ArduinoPower.FIRMWARE_ID)) {
						state.set(State.values.powerport, getPortName());
						application.powerport.serialPort = serialPort;
						application.powerport.in = inputStream;
						application.powerport.out = outputStream;
						application.powerport.isconnected = true;
						application.powerport.portname = getPortName();
						application.powerport.initialize();
						break;
					}
					
//					else if (id.equalsIgnoreCase(ArduinoGyro.FIRMWARE_ID)) {
//						state.set(State.values.gyroport, getPortName());
//						application.gyroport.serialPort = serialPort;
//						application.gyroport.in = inputStream;
//						application.gyroport.out = outputStream;
//						application.gyroport.isconnected = true;
//						application.gyroport.portname = getPortName();
//						application.gyroport.initialize();
//						break;
//					}
					
					
					else { close(); }
					
				} 
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
			outputStream.write(new byte[] { 'x', 13 }); // send 'get product' command
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
			if(device.length()>0 && ((int)buffer[j] == 13 || (int)buffer[j] == 10)) { break; } //read one line only
			if(Character.isLetter((char) buffer[j]))
				device += (char) buffer[j];
		}
		
		lookup(device);
//		close();
	}
}

