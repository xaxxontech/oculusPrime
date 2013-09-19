package oculus.commport;

import oculus.commport.ArduinoPort.direction;

public interface ArduinoPort {
	
	public enum direction { stop, right, left, forward, backward };
	public enum cameramove { stop, up, down, horiz, upabit, downabit, frontstop, rearstop };
	public enum speeds { slow, med, fast }; // better motors, maybe add speeds? 

	public static final long DEAD_TIME_OUT = 30000;
	public static final long WATCHDOG_DELAY = 5000;	
	public static final long DOCKING_DELAY = 1000;
	public static final int SETUP = 2000;
	
	public static final byte FORWARD = 'f';
	public static final byte BACKWARD = 'b';
	public static final byte LEFT = 'l';
	public static final byte RIGHT = 'r';
	public static final byte COMP = 'c';
	public static final byte CAM = 'v';
	public static final byte ECHO = 'e';
	public static final byte POWER = 'p';
	public static final byte FLOOD_ON = 'w'; // dup
	public static final byte FLOOD_OFF = 'o';
	public static final byte DOCK_STATUS = 'q';
	public static final byte LIGHT_LEVEL = 'z';
	public static final byte FIND_HOME_TILT = 'm';
	public static final byte HOME_TILT_FRONT = 't';
	public static final byte HOME_TILT_REAR = 'z';
	public static final byte STOP = 's';
	public static final byte GET_PRODUCT = 'x';
	public static final byte GET_VERSION = 'y';
	public static final byte[] ECHO_ON = { 'e', '1' };
	public static final byte[] ECHO_OFF = { 'e', '0' };

	/** open port, enable read and write, enable events */
	public abstract void connect(); 
	
	public abstract void close();
	
	public abstract void reset();
	
	public abstract void execute();

	/** @return True if the serial port is open */
	public abstract boolean isConnected();

	/** @return the time since last write() operation */
	public abstract long getWriteDelta();

	/** @return this device's firmware version */
	public abstract String getVersion();

	/** @return the time since last read operation */
	public abstract long getReadDelta();

	/** @param update is set to true to turn on echo'ing of serial commands */
	public abstract void setEcho(boolean update);

	public abstract void sendCommand(byte cmd);
	
	public abstract void sendCommand(byte[] cmd);

	public abstract void stopGoing();

	public abstract void goForward();

	public abstract void goBackward();

	public abstract void turnRight();

	public abstract void turnLeft();

	public abstract void camCommand(final cameramove dir); 

	public abstract void speedset(final speeds speed);

	public abstract void nudge(final direction target);

	public abstract void slide(final direction target);

	public abstract void slidecancel();

	public abstract void clickSteer(final int x, final int y);

	// TODO: lights, set methods only, get the levels out of state
	
	public abstract void setSpotLightBrightness(int target);

//	public abstract void floodLightOn();
//	
//	public abstract void floodLightOff();
	
	public void floodLight(String str);
	
	// battery... not needed if arduino is sending on its own 

	public abstract void updateBatteryLevel();
	
	public abstract void getDockStatus();

	// expansion 
	
	public abstract void digitalRead(int pin);

	public abstract void AnalogWrite(int pin);

	public abstract void rotate(direction dir, int degrees);

	
	
}