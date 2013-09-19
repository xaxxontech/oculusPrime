package oculus.commport;

import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.TooManyListenersException;

import oculus.Application;
import oculus.State;
import oculus.Util;

/***
 * 
 * Comparability only... two serial ports used 
 *
 */
public class LightsComm implements SerialPortEventListener {
	
	public static final long DEAD_MAN_TIME_OUT = 30000;
	public static final long USER_TIME_OUT = 5 * 60000;
	public static final int TOO_MANY_COMMANDS = 10;
	private static final int BAUD_RATE = 57600;
	public static final int WATCHDOG_DELAY = 5000;
	private static final int SETUP = 2000;
	
	public static final byte GET_PRODUCT = 'x';
	public static final byte GET_VERSION = 'y';
	public  static final byte FLOOD_ON_HIGH = 'w';
	public static final byte FLOOD_ON_MED = 'm';
	public static final byte FLOOD_ON_LOW = 'l';
	public static final byte FLOOD_OFF = 'o';
	public static final byte SPOT_OFF = 'a';
	public static final byte SPOT_1 = 'b';
	public static final byte SPOT_2 = 'c';
	public static final byte SPOT_3 = 'd';
	public static final byte SPOT_4 = 'e';
	public static final byte SPOT_5 = 'f';
	public static final byte SPOT_6 = 'g';
	public static final byte SPOT_7 = 'h';
	public static final byte SPOT_8 = 'i';
	public static final byte SPOT_9 = 'j';
	public static final byte SPOT_MAX = 'k';
	private SerialPort serialPort = null;
	
	private InputStream in = null;
	private OutputStream out= null;
	
	private State state = State.getReference();
	
	// will be discovered from the device 
	protected String version = null;

	// track write times
	private long lastSent = System.currentTimeMillis();
	private long lastRead = System.currentTimeMillis();

	// make sure all threads know if connected 
	private boolean isconnected = false;
	
	// call back
	private Application application = null;

	/**
	 * Constructor but call connect to configure
	 * 
	 * @param app 
	 * 			  is the main oculus application, we need to call it on
	 * 			Serial events like restet            
	 */
	public LightsComm(Application app) {
		application = app; 
		if( state.get(State.values.lightport) != null ){
			new Thread(new Runnable() { 
				public void run() {
					connect();				
					Util.delay(SETUP);
				}	
			}).start();
			
			new WatchDog().start();
		}	
	}
	
	/** open port, enable read and write, enable events */
	public void connect() {
		try {

			serialPort = (SerialPort)CommPortIdentifier.getPortIdentifier(
					state.get(State.values.lightport)).open(LightsComm.class.getName(), SETUP);
			serialPort.setSerialPortParams(BAUD_RATE, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);

			// open streams
			out = serialPort.getOutputStream();
			in = serialPort.getInputStream();
			
		} catch (Exception e) {
			Util.log("could NOT connect to the the lights on:" + state.get(State.values.lightport), this);
			application.message("could NOT connect to the the lights on:" + state.get(State.values.lightport), null, null);
			return;
		}
		
		
		// register for serial events
		try {
			serialPort.addEventListener(this);
		} catch (TooManyListenersException e) {
			Util.log(e.getMessage(), this);
		}
		serialPort.notifyOnDataAvailable(true);
		isconnected = true;	
		state.set(State.values.floodlighton, false);
		state.set(State.values.spotlightbrightness, 0);
		Util.log("connected to the the lights on:" + state.get(State.values.lightport), this);
	}

	/** @return True if the serial port is open */
	public boolean isConnected(){
		return isconnected;
	}
	
	@Override
	/** manage input from lights */
	public void serialEvent(SerialPortEvent event) {
		if (event.getEventType() == SerialPortEvent.DATA_AVAILABLE) {
			try {
				
				in.skip(in.available());
				
				// really, we just care are getting replies.
				lastRead = System.currentTimeMillis();
				
			} catch (IOException e) {
				Util.log("event : " + e.getMessage(), this);
			}
		}
	}	
	
	
	/** inner class to check if getting responses in timely manor */
	public class WatchDog extends Thread {
		public WatchDog() {
			this.setDaemon(true);
		}

		public void run() {
			Util.delay(SETUP);
			while (true) {
				if((System.currentTimeMillis() - state.getLong(oculus.State.values.lastusercommand)) > USER_TIME_OUT){
					if(state.getBoolean(oculus.State.values.floodlighton) 
						|| (state.getInteger(oculus.State.values.spotlightbrightness) > 0)){
							application.message("lights on too long", null, null);
							sendCommand(SPOT_OFF);
							sendCommand(FLOOD_OFF);
							
							state.set(oculus.State.values.floodlighton, false); 
							state.set(oculus.State.values.spotlightbrightness, 0);
						}
				}
				
				// refresh values
				if(getReadDelta() > (DEAD_MAN_TIME_OUT/3)){
										
					//TODO: disable due to varyinglevels for floodlight
//					if(state.getBoolean(oculus.State.values.floodlighton)) sendCommand(FLOOD_ON_HIGH);
//					else sendCommand(FLOOD_OFF);
//					
//					int spot = state.getInteger(oculus.State.values.spotlightbrightness);
//					if(spot==0) sendCommand((byte) SPOT_OFF);
//					else if(spot==10)sendCommand((byte) SPOT_1);
//					else if(spot==20) sendCommand((byte) SPOT_2);
//					else if(spot==30) sendCommand((byte) SPOT_3); 
//					else if(spot==40) sendCommand((byte) SPOT_4);
//					else if(spot==50) sendCommand((byte) SPOT_5);
//					else if(spot==60) sendCommand((byte) SPOT_6);
//					else if(spot==70) sendCommand((byte) SPOT_7);
//					else if(spot==80) sendCommand((byte) SPOT_8);
//					else if(spot==90) sendCommand((byte) SPOT_9);
//					else if(spot==100) sendCommand((byte) SPOT_MAX);
					
					sendCommand(GET_PRODUCT); // TODO: testing only, nuke this
					
				}
				
				// error state
//				if(getReadDelta() > DEAD_MAN_TIME_OUT) error();
				
				// sendCommand((byte) GET_VERSION);
				Util.delay(WATCHDOG_DELAY);
			}		
		}
	}

	/***/ 
	public void error(){
		disconnect();
		application.message("lights failure, time out!", null, null);
		Util.debug("lights failure, time out!", this);
	} 
	
	/** @return the time since last write() operation */
	public long getWriteDelta() {
		return System.currentTimeMillis() - lastSent;
	}

	/** @return this device's firmware version */
	public String getVersion(){
		return version;
	}
	
	/** @return the time since last read operation */
	public long getReadDelta() {
		return System.currentTimeMillis() - lastRead;
	}

	/** inner class to send commands */
	private class Sender extends Thread {		
		private byte command = 13;
		public Sender(final byte cmd) {
			command = cmd;
			if(isConnected())start();
		}
		public void run() {
			try {
				out.write(command);
			} catch (Exception e) {
				Util.log(e.getMessage(), this);
				reset();
			}
			lastSent = System.currentTimeMillis();
		}
	}

	/***/
	public void reset(){
		if (isconnected) {
			new Thread(new Runnable() { 
				public void run() {
					disconnect();
					connect();
				}
			}).start();
		}
	} 

	/** shutdown serial port */
	protected void disconnect() {
		try {
			in.close();
			out.close();
			isconnected = false;
		} catch (Exception e) {
			System.out.println("close(): " + e.getMessage());
		}
		serialPort.close();
	}

	/**
	 * Send a multi byte command to send the arduino 
	 * 
	 * @param command
	 *            is a byte array of messages to send
	*/
	private /* synchronized */ void sendCommand(final byte command) {
		
		if(!isconnected) return;
		
		new Sender(command);

		// track last write
		lastSent = System.currentTimeMillis();
	} 

	public /* synchronized*/ void setSpotLightBrightness(int target){
		
		if( !isConnected()){
			Util.log("lights NOT found", this);
			return;
		}
		
		Util.debug("set spot: " + target, this);
		
		if(target==0) sendCommand((byte) SPOT_OFF);
		else if(target==10)sendCommand((byte) SPOT_1);
		else if(target==20) sendCommand((byte) SPOT_2);
		else if(target==30) sendCommand((byte) SPOT_3); 
		else if(target==40) sendCommand((byte) SPOT_4);
		else if(target==50) sendCommand((byte) SPOT_5);
		else if(target==60) sendCommand((byte) SPOT_6);
		else if(target==70) sendCommand((byte) SPOT_7);
		else if(target==80) sendCommand((byte) SPOT_8);
		else if(target==90) sendCommand((byte) SPOT_9);
		else if(target==100) sendCommand((byte) SPOT_MAX);
		
		state.set(State.values.spotlightbrightness, target);
		application.message("spotlight brightness set to "+target+"%", "light", Integer.toString(target));
	}
	
	public /* synchronized */ void floodLight(String mode){
		if( !isConnected()){
			Util.log("lights NOT found", this);
			application.message("lights not found", null, null);
			return;
		}
		if (mode.equals("on")) { 
			sendCommand(FLOOD_ON_HIGH);
			state.set(State.values.floodlighton, true);
		} else if (mode.equals("med")) { 
			sendCommand(FLOOD_ON_MED);
			state.set(State.values.floodlighton, true);
		} else if (mode.equals("low")) { 
			sendCommand(FLOOD_ON_LOW);
			state.set(State.values.floodlighton, true);
		}
		else {
			sendCommand(FLOOD_OFF);
			state.set(State.values.floodlighton, false);
		}
		
		application.message("floodlight "+mode, "floodlight", state.get(State.values.floodlighton));
	}
}