package oculusPrime.commport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import oculusPrime.Application;
import oculusPrime.AutoDock;
import oculusPrime.State;
import oculusPrime.Util;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;

public class ArduinoPower implements SerialPortEventListener  {

	public static final int SETUP = 2000;
	
	protected Application application = null;
	protected static State state = State.getReference();
	protected SerialPort serialPort = null;	
	protected OutputStream out = null;
	protected InputStream in = null;
	
	protected volatile boolean isconnected = false;
	protected long lastSent = System.currentTimeMillis();
	protected long lastRead = System.currentTimeMillis();
	
	protected final String portName = state.get(State.values.powerport);

	protected byte[] buffer = new byte[32];
	protected int buffSize = 0;

	
	public ArduinoPower(Application app) {
		application = app;	
		Util.log("attempting to connect to port", this);
		
		if(powerAvailable()){
			
			Util.log("attempting to connect to port", this);
			
			new Thread(new Runnable() {
				public void run() {
					connect();
					Util.delay(SETUP);
					if(isconnected){
						
						Util.log("Connected to port: " + state.get(State.values.motorport), this);

					}
				}
			}).start();

		}
		
	}
	
	public void connect() {
		try {

			serialPort = (SerialPort) CommPortIdentifier.getPortIdentifier(portName).open(ArduinoPrime.class.getName(), SETUP);
			serialPort.setSerialPortParams(115200, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);

			// open streams
			out = serialPort.getOutputStream();
			in = serialPort.getInputStream();

			// register for serial events
			serialPort.addEventListener(this);
			serialPort.notifyOnDataAvailable(true);
			
			isconnected = true;

		} catch (Exception e) {
			
			Util.log("can't connect to port: " + e.getMessage(), this);
			
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

	public static boolean powerAvailable(){
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
		
		if(ArduinoPrime.DEBUGGING) Util.debug("serial in: " + response, this);
		
		if(response.equals("reset")) {
			application.message(this.getClass().getName() + " board reset", null, null);
		} 
	
		if(response.startsWith("battery")){
			String s = response.split(" ")[1];
			if (s.equals("timeout")) {
				state.put(State.values.dockstatus, AutoDock.UNKNOWN);
				state.put(State.values.batterylife, s);
				application.message(null, "battery", s);
				return;
			}
			
			if (s.equals("docked")) {
				if (!state.get(State.values.dockstatus).equals(AutoDock.DOCKED)) {
					application.message(null, "dock", AutoDock.DOCKED);
					state.put(State.values.dockstatus, AutoDock.DOCKED);
					state.set(State.values.batterycharging, true);
				}
				if (state.getBoolean(State.values.motionenabled)) {
					state.set(State.values.motionenabled, false); }
			}
			
			if (s.equals("undocked")) {
				if (!state.get(State.values.dockstatus).equals(AutoDock.UNDOCKED)) {
					state.put(State.values.dockstatus, AutoDock.UNDOCKED);
					application.message(null, "dock", AutoDock.UNDOCKED);
					state.set(State.values.batterycharging, false);
				}
				if (!state.getBoolean(State.values.motionenabled)) { 
					state.set(State.values.motionenabled, true); }
			}
			
			String battinfo = response.split(" ")[2];
			if (!state.get(State.values.batterylife).equals(battinfo)) {
				state.put(State.values.batterylife, battinfo);
			}
		}

	}
	
	public boolean isConnected() {
		return isconnected;
	}

	
}
