package oculus.commport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Timer;
import java.util.TimerTask;

import oculus.Application;
import oculus.GUISettings;
import oculus.ManualSettings;
import oculus.Settings;
import oculus.State;
import oculus.Util;

import gnu.io.SerialPort;

public abstract class AbstractArduinoComm implements ArduinoPort {
	
	// toggle to see bytes sent in log 
	public static final boolean DEBUGGING = false;
	
	// Tweak these constants here 
	public static final int CAM_HORIZ = 19; // degrees (CAD measures 19)
	public static final int CAM_MAX = 219; // degrees (CAD measures 211)
	public static final int CAM_MIN = 0; // degrees
	public static final int CAM_NUDGE = 5; // degrees
	public static final long CAM_NUDGE_DELAY = 100; 
	public static final int CAM_EXTRA_FOR_CALIBRATE = 90; // degrees
	
	protected static Settings settings = Settings.getReference();
	protected long lastSent = System.currentTimeMillis();
	protected long lastRead = System.currentTimeMillis();
	protected State state = State.getReference();
	protected Application application = null;
	protected SerialPort serialPort = null;	
	protected String version = null;
	protected OutputStream out = null;
	protected InputStream in = null;
	
	protected final String portName = settings.readSetting(ManualSettings.serialport);
	protected final String firmware = settings.readSetting(ManualSettings.firmware);
	
	// data buffer 
	protected byte[] buffer = new byte[32];
	protected int buffSize = 0;
	
	// thread safety 
	protected volatile boolean isconnected = false;
	public volatile boolean sliding = false;
	
	// tracking motor moves 
	private static Timer cameraTimer = null;
	
	// take from settings 
	public double clicknudgemomentummult = settings.getDouble(GUISettings.clicknudgemomentummult);	
	public int maxclicknudgedelay = settings.getInteger(GUISettings.maxclicknudgedelay);
	public int speedslow = settings.getInteger(GUISettings.speedslow);
	public int speedmed = settings.getInteger(GUISettings.speedmed);
	public int nudgedelay = settings.getInteger(GUISettings.nudgedelay);
	public int maxclickcam = settings.getInteger(GUISettings.maxclickcam);
	public int fullrotationdelay = settings.getInteger(GUISettings.fullrotationdelay);
	
	private static final int TURNBOOST = 25; 
	public int speedfast = 255;
	public int turnspeed = 255;
	

	public abstract void execute(); 
	
	public static boolean lightsAvailable(){
		final String lights = settings.readSetting(ManualSettings.lightport); 
		if(lights == null) { 
			return false;
		} else {
			if(lights.equals(Discovery.params.discovery.name()) || lights.equals(Discovery.params.disabled.name()))
				return false;
		}
		
		return true;
	}
	
	/**/
	public static boolean motorsAvailable(){
		final String lights = settings.readSetting(ManualSettings.serialport); 
		if(lights != null) {
			if(lights.equals(Discovery.params.discovery.name()) || lights.equals(Discovery.params.disabled.name()))
				return false;
		}
		
		return true;
	}
	
	@Override
	public abstract void connect();
	
	@Override
	public boolean isConnected() {
		return isconnected;
	}
	
	@Override
	public void close(){
		if(serialPort!=null) serialPort.close();
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
	
	@Override
	public long getWriteDelta() {
		return System.currentTimeMillis() - lastSent;
	}

	@Override
	public String getVersion() {
		return version;
	}

	@Override
	public long getReadDelta() {
		return System.currentTimeMillis() - lastRead;
	}

	@Override
	public void setEcho(boolean update) {
		if (update) sendCommand(ECHO_ON);
		else sendCommand(ECHO_OFF);
	}

	@Override
	public void reset() {
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
			if(in!=null) in.close();
			if(out!=null) out.close();
			isconnected = false;
			version = null;
		} catch (Exception e) {
			Util.log("disconnect(): " + e.getMessage(), this);
		}
		if(serialPort!=null) serialPort.close();
	}

	/**
	 * Send a multiple byte command to send the device
	 * 
	 * @param command
	 *            is a byte array of messages to send
	 */
	public void sendCommand(byte[] cmd) {

		if (!isconnected) return;
		
		if(DEBUGGING) {
			String text = "sendCommand(): " + (char)cmd[0] + " ";
			for(int i = 1 ; i < cmd.length ; i++) 
				text += (byte)cmd[i] + " ";
			
			Util.debug(text, this);
		}
		
		if (state.getBoolean(State.values.controlsinverted)) {
			switch (cmd[0]) {
			case ArduinoPort.FORWARD: cmd[0]=ArduinoPort.BACKWARD; break;
			case ArduinoPort.BACKWARD: cmd[0]=ArduinoPort.FORWARD; break;
			case ArduinoPort.LEFT: cmd[0]=ArduinoPort.RIGHT; break;
			case ArduinoPort.RIGHT: cmd[0]=ArduinoPort.LEFT; 
			}
		}
		
		final byte[] command = cmd;
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				try {

					// send
					out.write(command);
		
					// end of command
					out.write(13);
		
				} catch (Exception e) {
					reset();
					Util.log("OCULUS: sendCommand(), " + e.getMessage(), this);
				}
			}
		}).start();
		
		// track last write
		lastSent = System.currentTimeMillis();
	}
	
	@Override
	public void sendCommand(final byte cmd){
		sendCommand(new byte[]{cmd});
	}
	
	@Override
	public void updateBatteryLevel() {
		sendCommand(POWER);
	}
	
	@Override
	public void getDockStatus() {
		sendCommand(DOCK_STATUS);
	}

	@Override
	public void digitalRead(int pin){
		Util.debug("digitalRead(): " + pin, this);
	}

	@Override
	public void AnalogWrite(int pin){
		Util.debug("AnalogWrite(): " + pin, this);
	}
	
	@Override
	public void setSpotLightBrightness(int target){}

//	@Override
//	public void floodLightOn(){
//		Util.debug("floodLightOn(): should be overriden", this);
//	}
	
//	@Override
//	public void floodLightOff(){
//		Util.debug("floodLightOff(): should be overriden", this);
//	}

	@Override
	public void stopGoing() {
		sendCommand(STOP);
		state.put(State.values.moving, false);
		state.put(State.values.movingforward, false);
		if (state.getBoolean(State.values.muteOnROVmove) && state.getBoolean(State.values.moving)) application.unmuteROVMic();
	}

	@Override
	public void goForward() {
		sendCommand(new byte[] { FORWARD, (byte) state.getInteger(State.values.motorspeed) });
		state.put(State.values.moving, true);
		state.put(State.values.movingforward, true);
		if (state.getBoolean(State.values.muteOnROVmove)) application.muteROVMic();
	}

	@Override
	public void goBackward() {
		sendCommand(new byte[] { BACKWARD, (byte) state.getInteger(State.values.motorspeed) });
		state.put(State.values.moving, true);
		state.put(State.values.movingforward, false);
		if (state.getBoolean(State.values.muteOnROVmove)) application.muteROVMic();
	}

	@Override
	public void turnRight() {
		int tmpspeed = turnspeed;
		int boost = TURNBOOST;
		int speed = state.getInteger(State.values.motorspeed);
		if (speed < turnspeed && (speed + boost) < speedfast)
			tmpspeed = speed + boost;

		sendCommand(new byte[] { RIGHT, (byte) tmpspeed });
		state.put(State.values.moving, true);
		if (state.getBoolean(State.values.muteOnROVmove)) application.muteROVMic();
	}

	@Override
	public void turnLeft() {
		int tmpspeed = turnspeed;
		int boost = TURNBOOST;
		int speed = state.getInteger(State.values.motorspeed);
		if (speed < turnspeed && (speed + boost) < speedfast)
			tmpspeed = speed + boost;

		sendCommand(new byte[] { LEFT, (byte) tmpspeed });
		state.put(State.values.moving, true);
		if (state.getBoolean(State.values.muteOnROVmove)) application.muteROVMic();
	}
	
	private class cameraUpTask extends TimerTask {
		@Override
		public void run(){
			
			state.set(State.values.cameratilt, state.getInteger(State.values.cameratilt) + CAM_NUDGE);
			
			if (state.getInteger(State.values.cameratilt) >= CAM_MAX) {
				cameraTimer.cancel();
				sendCommand(new byte[] { HOME_TILT_REAR, (byte) CAM_MAX }); //calibrate
				state.set(State.values.cameratilt, CAM_MAX);
				return;
			}
		
			sendCommand(new byte[] { CAM, (byte) state.getInteger(State.values.cameratilt) });
		}
	}
	
	private class cameraDownTask extends TimerTask {
		@Override
		public void run(){
			
			state.set(State.values.cameratilt, state.getInteger(State.values.cameratilt) - CAM_NUDGE);
			
			if (state.getInteger(State.values.cameratilt) <= CAM_MIN) {
				cameraTimer.cancel();
				sendCommand(new byte[] { HOME_TILT_FRONT }); // calibrate
				state.set(State.values.cameratilt, CAM_MIN);
				return;
			}
	
			sendCommand(new byte[] { CAM, (byte) state.getInteger(State.values.cameratilt) });
		}
	}
	
	
	@Override
	public void camCommand(ArduinoPort.cameramove move){ 

		Util.debug("camCommand(): " + move, this);
		
		if (state.getBoolean(State.values.autodocking)) {
			application.messageplayer("command dropped, autodocking", null, null);
			return;
		}
		
		if (state.getBoolean(State.values.controlsinverted)) {
			switch (move) {
			case up: move=ArduinoPort.cameramove.down; break;
			case down: move=ArduinoPort.cameramove.up; break;
			case upabit: move=ArduinoPort.cameramove.downabit; break;
			case downabit: move=ArduinoPort.cameramove.upabit; break; 
			}
		}
		
		int position;
		
		switch (move) {
		
			case stop: 
				if(cameraTimer != null) cameraTimer.cancel();
				cameraTimer = null;
				return;
				
			case up:  
				cameraTimer = new java.util.Timer();  
				cameraTimer.scheduleAtFixedRate(new cameraUpTask(), 0, CAM_NUDGE_DELAY);
				return;
			
			case down:  
				cameraTimer = new java.util.Timer();  
				cameraTimer.scheduleAtFixedRate(new cameraDownTask(), 0, CAM_NUDGE_DELAY);
				return;
			
			case horiz: 
				sendCommand(new byte[] { CAM, (byte) CAM_HORIZ });
				state.set(State.values.cameratilt, CAM_HORIZ);
				return;
			
			case downabit: 
				position= state.getInteger(State.values.cameratilt) - CAM_NUDGE*2;
				if (position <= CAM_MIN) { 
					position = CAM_MIN;
					sendCommand(new byte[] { HOME_TILT_FRONT }); //calibrate 
				}
				else {  sendCommand(new byte[] { CAM, (byte) position }); }
				state.set(State.values.cameratilt, position);
				return; 
			
			case upabit: 
				position = state.getInteger(State.values.cameratilt) + CAM_NUDGE*2;
				if (position >= CAM_MAX) { 
					position = CAM_MAX;
					sendCommand(new byte[] { HOME_TILT_REAR, (byte) CAM_MAX }); //calibrate
				}
				else { sendCommand(new byte[] { CAM, (byte) position }); }
				state.set(State.values.cameratilt, position);
				return;
				
			case frontstop:  
				sendCommand(new byte[] { HOME_TILT_FRONT });
				state.set(State.values.cameratilt, CAM_MIN);
				return;
				
			case rearstop: 
				sendCommand(new byte[] { HOME_TILT_REAR, (byte) CAM_MAX });
				state.set(State.values.cameratilt, CAM_MAX);
				return;
		}
	
	}
	

	@Override
	public void speedset(final ArduinoPort.speeds update) {
		
		Util.debug("speedset(): " + update, this);
		
		switch (update) {
		case slow: state.put(State.values.motorspeed, speedslow); break;
		case med: state.put(State.values.motorspeed, speedmed); break;
		case fast: state.put(State.values.motorspeed, speedfast); break;
		}
	}

	@Override
	public void nudge(final ArduinoPort.direction dir) {
		
		Util.debug("nudge(): " + dir, this);
		
		new Thread(new Runnable() {
			public void run() {
				
				int n = nudgedelay;
				
				switch (dir) {
				case right: turnRight(); break;
				case left: turnLeft(); break;
				case forward: 
					goForward();
					state.put(State.values.movingforward, false);
					n *= 4; 
					break;
				case backward:
					goBackward();
					n *= 4;
				}
				
				Util.delay(n);

				if (state.getBoolean(State.values.movingforward)) goForward();
				else stopGoing();
				
			}
		}).start();
	}

	@Override
	public void rotate(final ArduinoPort.direction dir, final int degrees) {
		new Thread(new Runnable() {
			public void run() {
				
				final int tempspeed = state.getInteger(State.values.motorspeed);
				state.put(State.values.motorspeed, speedfast);
				
				int n = fullrotationdelay * degrees / 360;
				
				switch (dir) {
					case right: turnRight(); break;
					case left: turnLeft(); 
				}
				
				Util.delay(n);

				stopGoing();
				state.put(State.values.motorspeed, tempspeed);
				application.message(null, "motion", "stopped");
				
			}
		}).start();
	}
	
	@Override
	public void slide(final ArduinoPort.direction dir){
		
		Util.debug("slide(): " + dir, this);
		
		if (sliding == false) {
			sliding = true;
			
			new Thread(new Runnable() {
				public void run() {
					try {	
						
						final int tempspeed = state.getInteger(State.values.motorspeed);
						final int distance = 300;
						final int turntime = 500;
						
						state.put(State.values.motorspeed, speedfast);
						
						switch (dir) {
						case right: turnLeft(); break;
						case left: turnRight(); break;
						}
				
						Thread.sleep(turntime);
						
						if (sliding == true) {
							goBackward();
							Thread.sleep(distance);
							
							if (sliding == true) {
								
								switch (dir) {
								case right: turnRight();
								case left: turnLeft();
								}	
									
								Thread.sleep(turntime);
							 
								if (sliding == true) {
									goForward();
									Thread.sleep(distance);
									if (sliding == true) {
										stopGoing();
										sliding = false;
										state.put(State.values.motorspeed, tempspeed);
									}
								}
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}).start();
		}
	}

	@Override
	public void slidecancel() {
		if (sliding == true) sliding = false;
	}

	@Override
	public void clickSteer(final int x, int y) {
		
		Util.debug("__clickSteer(): " + x, this);
		
		if (state.getBoolean(State.values.controlsinverted)) {
			y=-y;
		}
		
		clickCam(y);
		clickNudge(x);
	}

	private void clickNudge(final Integer x) {
		
		Util.debug("__clickNudge(): " + "x: " + x, this);
		
		//TODO:  unsure of this 
		final double mult = Math.pow(((320.0 - (Math.abs(x))) / 320.0), 3)* clicknudgemomentummult + 1.0;
		final int clicknudgedelay = (int) ((maxclicknudgedelay * (Math.abs(x)) / 320) * mult);
		
		Util.debug("__clickNudge() n: "+clicknudgemomentummult+" mult: "+mult+" clicknudgedelay-after: "+clicknudgedelay, this);
		
		new Thread(new Runnable() {	
			public void run() {
				try {
					
					final int tempspeed = state.getInteger(State.values.motorspeed);
					state.put(State.values.motorspeed, speedfast);
					
					if (x > 0) turnRight();
					else turnLeft();
					
					Thread.sleep(clicknudgedelay);
					state.put(State.values.motorspeed, tempspeed);
					
					if (state.getBoolean(State.values.movingforward)) goForward();
					else stopGoing();
					
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();
	}

	private void clickCam(final Integer y) {
		
		Util.debug("___clickCam(): " + "y: " + y, this);
		
		Integer n = maxclickcam * y / 240;  
		state.set(State.values.cameratilt, state.getInteger(State.values.cameratilt) - n);
		
		// range check 
		if (state.getInteger(State.values.cameratilt) < CAM_MIN) state.set(State.values.cameratilt, CAM_MIN);
		if (state.getInteger(State.values.cameratilt) > CAM_MAX) state.set(State.values.cameratilt, CAM_MAX);
		
		application.messageplayer(null, "cameratilt", state.get(State.values.cameratilt));
		sendCommand(new byte[] { CAM, (byte) state.getInteger(State.values.cameratilt) });	
		
	}
}