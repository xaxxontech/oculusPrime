package oculus.commport;

import oculus.Application;

import oculus.AutoDock;
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
public class ArduinoMotorSheild extends AbstractArduinoComm implements SerialPortEventListener, ArduinoPort {
	
	private static final int AVERAGE_LEVEL = 5;
	int right, left, rightTotal, leftTotal, samples = 0;

	//TODO: hold over if two serial ports
	private LightsComm light;
	
	public ArduinoMotorSheild(Application app) {	
		super(app);
		
		if(state.get(State.values.lightport) != null) {
			light = new LightsComm(app);
			light.connect();
		}
	
		Util.delay(ArduinoPort.WATCHDOG_DELAY);
		if( isconnected) {
			
//			floodLightOff();
			setSpotLightBrightness(0);
			
			// battery watch dog 
			new PowerThread(this);
			
		} else Util.log("no hardware connected", this);
	}
	
	@Override 
	public void setSpotLightBrightness(int target){
		
		Util.debug("........ setSpotLightBrightness: " + target, this);
		
		if(light != null) {//TODO: hold over if two serial ports
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
//	
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
	
	/** respond to feedback from the device  */
	@Override
	public void execute() {
		String response = "";
		for (int i = 0; i < buffSize; i++)
			response += (char) buffer[i];
		
		if(AbstractArduinoComm.DEBUGGING) Util.debug("_in: " + response ,this);
		
		if (response.equals("reset")) {

			isconnected = true;
			version = null;
			sendCommand(GET_VERSION);
			// updateSteeringComp();

		} 
		
		if (response.startsWith("version:")) {
			if (version == null) {
				version = response.substring(response.indexOf("version:") + 8, response.length());
				application.message("arduinoMotorShield v: " + version, null, null);
			}
		} 
			
		if(response.startsWith("current")){
			right = Integer.parseInt(response.split(" ")[1]);
			left = Integer.parseInt(response.split(" ")[2]);
			
			if(right < 5) return;
			rightTotal += right;
			state.set("leftCurrent", left);
			
			if(left < 5) return;
			leftTotal += left;
			state.set("rightCurrent", right);
			
			if(++samples >= AVERAGE_LEVEL){
				application.sendplayerfunction("debug", "right: " + rightTotal/samples + " left: " + leftTotal/samples);
				samples = rightTotal = leftTotal = 0;
			}
		}
			
		if(response.equals(AutoDock.DOCKED)){
			
			Util.debug("docked: " + response ,this);
					
			state.set(State.values.batterycharging, true);
			state.put(State.values.dockstatus, AutoDock.DOCKED);
			application.message(null, "multiple", " dock " + AutoDock.UNDOCKED );	
		}
			
		if(response.equals(AutoDock.UNDOCKED)){
			
			Util.debug("docked: " + response ,this);
			
			state.set(State.values.batterycharging, false);
			state.put(State.values.dockstatus, AutoDock.UNDOCKED);
			
			// application.message(null, "multiple", " draining battery 99% dock " + AutoDock.UNDOCKED );
		}

		if(response.startsWith("power")){
			
			Util.debug("power: " + response ,this);
			String level = response.split(" ")[1];
			state.put(State.values.batterylife, level);
			
			application.message(null, "multiple", "battery " + level + " %"); 
		}
	}
	
	@Override
	public void serialEvent(SerialPortEvent event) {
		if (event.getEventType() == SerialPortEvent.DATA_AVAILABLE) {
			super.manageInput();
		}
	}
	
	@Override
	public void connect() {
		try {

			serialPort = (SerialPort) CommPortIdentifier.getPortIdentifier(state.get(State.values.serialport)).open(ArduinoMotorSheild.class.getName(), SETUP);
			serialPort.setSerialPortParams(115200, SerialPort.DATABITS_8,SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);

			// open streams
			out = serialPort.getOutputStream();
			in = serialPort.getInputStream();

			// register for serial events
			serialPort.addEventListener(this);
			serialPort.notifyOnDataAvailable(true);

		} catch (Exception e) {
			return;
		}
	}

	@Override
	public void floodLight(String str) {
		// TODO Auto-generated method stub
		
	}
}