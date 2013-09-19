package oculus.commport;

import oculus.Application;
import oculus.AutoDock;
import oculus.ManualSettings;
import oculus.State;
import oculus.Util;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;

/**
 * 
 * Support lights on second usb port, and current sense on motors. 
 * 
 * Battery and docking states now updated from this class  
 *
 */
public class ArduinoPrime extends AbstractArduinoComm implements SerialPortEventListener, ArduinoPort {
	
	// TODO: hold over if two serial ports
	private LightsComm light;
	
	public ArduinoPrime(Application app) {	
		super(app);
		
		if( state.get(State.values.lightport) != null ){
			light = new LightsComm(application);
		}
	
//		floodLightOff();
		floodLight("off");
		setSpotLightBrightness(0);
		
		Util.debug("ArduinoPrime(): " + "... starting up... ", this);
		
		// not needed if done on ardunio
		 new PowerThread(this);
	}
	
	@Override 
	public void setSpotLightBrightness(int target){
		
		Util.debug("........ setSpotLightBrightness: " + target, this);
		
		if(light != null) { //TODO: hold over if two serial ports
			light.setSpotLightBrightness(target);
			return;
		} 
		
		sendCommand(new byte[]{ArduinoPort.LIGHT_LEVEL, (byte)target});
		state.set(State.values.spotlightbrightness, target);
		application.message("spotlight brightness set to "+target+"%", "light", Integer.toString(target));
	}

//	@Override 
//	public void floodLightOff(){ 
//		
//		Util.debug("........ comport, floodLightOff ", this);
//		
//		if(light != null) { //TODO: hold over if two serial ports
//			light.floodLight("off");
//			return;
//		}
//		
//		sendCommand(ArduinoPort.FLOOD_OFF);
//		state.set(State.values.floodlighton, false);
//		application.message("floodlight OFF", "floodlight", state.get(State.values.floodlighton));
//	}
	
//	@Override 
//	public void floodLightOn(){ 
//		
//		Util.debug("........ comport, floodLightOn ", this);
//		
//		if(light != null) { //TODO: hold over if two serial ports
//			light.floodLight("on");
//			return;
//		}
//		
//		sendCommand(ArduinoPort.FLOOD_ON);
//		state.set(State.values.floodlighton, true);
//		application.message("floodlight ON", "floodlight", state.get(State.values.floodlighton));
//	}
	
	@Override 
	public void floodLight(String str) {
		if(light != null) {
			light.floodLight(str);
		}
	}
	
	/** respond to feedback from the device  */
	@Override
	public void execute() {
		String response = "";
		for (int i = 0; i < buffSize; i++)
			response += (char) buffer[i];
		
		if(AbstractArduinoComm.DEBUGGING) Util.debug("serial in: " + response ,this);
		
		if (response.equals("reset")) {
			isconnected = true;
			version = null;
			sendCommand(GET_VERSION);
		} 
		
		if (response.startsWith("version:")) {
			version = response.substring(response.indexOf("version:") + 8, response.length());
			application.message(this.getClass().getName() + " v: " + version, null, null);		
		} 
		
	//	if(response.startsWith("tilt")){
	//		Util.debug("stepper moved to: " + response ,this);
	//	}
			
		if(response.equals(AutoDock.DOCKED)){
			
			Util.debug("docked: " + response ,this);
					
			state.set(State.values.batterycharging, true);
			state.put(State.values.dockstatus, AutoDock.DOCKED);
			application.message(null, "dock", AutoDock.DOCKED);	
			if (state.getBoolean(State.values.motionenabled)) state.set(State.values.motionenabled, false);
		}
			
		if(response.equals(AutoDock.UNDOCKED)){
			
			Util.debug("docked: " + response ,this);
			
			state.set(State.values.batterycharging, false);
			state.put(State.values.dockstatus, AutoDock.UNDOCKED);
			if (!state.getBoolean(State.values.motionenabled)) state.set(State.values.motionenabled, true);
			
			application.message(null, "dock", AutoDock.UNDOCKED );
		}

		if(response.startsWith("power")){
			
			String level = response.split(" ")[1];
			state.put(State.values.batterylife, level+"V");
			
//			application.message(null, "multiple", "battery " + level + "V"); //not here, only if client asks
		}
		
		if(response.startsWith("timeout")){
			
			// motors stopped by firmware, but tell state 
			
			boolean timeout = false;
			if(state.getBoolean(State.values.moving)){
				state.put(State.values.moving, false);
				timeout = true;
			}
			if(state.getBoolean(State.values.moving)){
				state.put(State.values.movingforward, false);
				timeout = true;
			}
			
			if(timeout) application.message(null, "motion", "STOPPED");
		}
		
		if(response.startsWith("tiltpos")) {
			String position = response.split(" ")[1];
			state.set(State.values.cameratilt, position);
			application.messageplayer(null, "cameratilt", state.get(State.values.cameratilt));
		}
	}

	@Override
	public void connect() {
		try {

			serialPort = (SerialPort) CommPortIdentifier.getPortIdentifier(state.get(State.values.serialport)).open(ArduinoPrime.class.getName(), SETUP);
			serialPort.setSerialPortParams(115200, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);

			// open streams
			out = serialPort.getOutputStream();
			in = serialPort.getInputStream();

			// register for serial events
			serialPort.addEventListener(this);
			serialPort.notifyOnDataAvailable(true);

		} catch (Exception e) {
//			settings.writeSettings(ManualSettings.arduinoprime.name(), Discovery.params.discovery.name());
//			return;
		}	
	}
	
	@Override
	public void serialEvent(SerialPortEvent event) {
		if (event.getEventType() == SerialPortEvent.DATA_AVAILABLE) {
			super.manageInput();
		}
	}
}

