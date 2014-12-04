package developer;

import jssc.SerialPort;
import jssc.SerialPortList;

public class Scratch {
	
	
	private static boolean connect(String portname, String id) {
		SerialPort serialport = new SerialPort(portname);
		try {
			System.out.println(portname);
			
			serialport.openPort();
			serialport.setParams(115200, 8, 1, 0);
			
			Thread.sleep(2000); // device handshake delay

			byte[] buffer = new byte[99];
			buffer = serialport.readBytes(); // clear serial buffer
			
			serialport.writeBytes(new byte[] { 'x', 13 });
			Thread.sleep(100);
			buffer = serialport.readBytes();
			
			
			if (buffer == null) return false;
			
			String device = new String();
			for (int i=0; i<buffer.length; i++) {
				if((int)buffer[i] == 13 || (int)buffer[i] == 10) { break; }
				if(Character.isLetter((char) buffer[i]))
					device += (char) buffer[i];
			}
			if (device.length() > 0)
				System.out.println(device);
				if (device.equals(id)) return true;

			
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return false;
	}

    public static void main(String[] args) {
    	String[] portNames = SerialPortList.getPortNames();
    	String id = "idoculusPower";
    	
    	System.out.println("available ports:");
        for(int i = 0; i < portNames.length; i++){
            System.out.println(portNames[i]);
        }
        
        System.out.println("\nchecking ports");
        
        if (portNames.length > 0){ 
        	for (int i=0; i<portNames.length; i++) {
        		if (portNames[i].matches("/dev/ttyUSB.+")) 
        			if (connect(portNames[i], id)) {
        				System.out.println("found "+id+" on port "+portNames[i]);
        				break;
        			}
        			else System.out.println("nil");
        	}
        }
        
        portNames = SerialPortList.getPortNames();
    	System.out.println("available ports:");
        for(int i = 0; i < portNames.length; i++){
            System.out.println(portNames[i]);
        }
    }
}
