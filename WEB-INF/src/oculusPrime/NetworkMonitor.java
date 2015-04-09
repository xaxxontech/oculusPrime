package oculusPrime;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import oculusPrime.State.values;

public class NetworkMonitor { 
	
	protected static final long WAN_POLL_DELAY_MS = Util.ONE_HOUR; // Util.ONE_DAY;
	protected static final long POLL_DELAY_MS = Util.ONE_MINUTE;

	protected static final String WLAN = "wlan";
	protected static final String ETH = "eth0";
	
	private static Vector<String> wlanData = new Vector<String>();
	private static Vector<String> ethData = new Vector<String>();
	private static Vector<String> accesspoints = new Vector<String>();
//	private static Vector<String> connections = new Vector<String>();
	private static Vector<String> networkData = new Vector<String>();
	
	Timer networkTimer = new Timer();
	/*
	private static long last = System.currentTimeMillis();
	private static float pingTime = 0;
	private static int pingCount = 0;
	private static int pingFail = 0;
	
	Timer pingTimer = new Timer();
	Timer watchdogTimer = new Timer();
	Timer stateTimer = new Timer();
	

	 */
	
	public static State state = State.getReference();
	private static NetworkMonitor singleton = null;
	public static NetworkMonitor getReference() {
		if(singleton == null) singleton = new NetworkMonitor();
		return singleton;
	}

	private NetworkMonitor(){
	
		//getSignalQuality();
			
		networkTimer.schedule(new networkTask(), 1000, POLL_DELAY_MS);
	
	}
	
	public class networkTask extends TimerTask {
	    @Override
	    public void run() {
	    	try{ 
	    			
	    		networkData.clear();
	    		wlanData.clear();
	    		accesspoints.clear();
	    		
				Process proc = Runtime.getRuntime().exec(new String[]{"nm-tool"});
				BufferedReader procReader = new BufferedReader(
					new InputStreamReader(proc.getInputStream()));
				
				String line = null;
				while ((line = procReader.readLine()) != null){
					line = line.trim();
					if(line.length()>0) 
						if( ! networkData.contains(line))
							if(line.contains(":"))
								networkData.add(line);
				}
			
				proc.waitFor();
				 
				Util.debug("networkTask: lines copied: " + networkData.size(), this);
				 
				readETH();
				readWAN();
				parseWLAN();
				parseETH();

			} catch (Exception e) {
				Util.debug("networkTask: " + e.getLocalizedMessage(), this);
			}		
	    }    
	}
	/*
	public void getSSID(){
		new Thread(new Runnable() {
			@Override
			public void run() {
				
				final String[] IW = {"iw", "dev", WLAN, "link"}; 
				
				while(true){
					try {
						
						Process proc = Runtime.getRuntime().exec(IW);
						BufferedReader procReader = new BufferedReader(
							new InputStreamReader(proc.getInputStream()));
										
						String line = null;
						while ((line = procReader.readLine()) != null){
							
							if(line.contains("Not connected")){
								state.delete(values.signalstrength);
								state.delete(values.signalnoise);
								state.set(values.ssid, "none");
							}
							
							if(line.contains("signal:"))
								state.set(values.ssid, line.substring(line.indexOf(": ")+2, line.indexOf(" dBm")));
							
							if(line.contains("SSID:")) 
								state.set(values.ssid, line.substring(line.indexOf(": ")+2));
							
						}
						
						proc.waitFor();
						Thread.sleep(POLL_DELAY_MS);
						
					} catch (Exception e) {
						Util.log("iw error: ", e, this);
					}										
				}
			}
		}).start();
	}

*/
	/*
	public void getSignalQuality(){	
		new Thread(new Runnable() {
			@Override
			public void run() {
				while(true){
					try {
						
						Process proc = Runtime.getRuntime().exec(new String[]{"cat", "/proc/net/wireless"});
						BufferedReader procReader = new BufferedReader(
							new InputStreamReader(proc.getInputStream()));
										
						String line = null;
						while ((line = procReader.readLine()) != null){
							if(line.contains(WLAN)){
	
								String[] results = line.split(" ");
								String signalStrength = results[7];
								String signalQuality = results[5]; 
								String signalNoise = results[9];
								
								//for(int i = 0 ; i < results.length; i++) 
								//	if(! results[i].equals("")) System.out.println(i + " -- " + results[i]);
								
								if(signalQuality.endsWith(".")) signalQuality = signalQuality.substring(0, signalQuality.length()-1);
								if(signalStrength.endsWith(".")) signalStrength = signalStrength.substring(0, signalStrength.length()-1);
								
								state.set(values.signalstrength, signalStrength);
								state.set(values.signalquality, signalQuality);
								state.set(values.signalnoise, signalNoise);
							}
						}
						
						proc.waitFor();
						Thread.sleep(POLL_DELAY_MS); 
						
					} catch (Exception e) {
						Util.log("getSignalQuality()" + e, this);
					}										
				}
			}
		}).start();
	}
	*/
	

	public static boolean isSSID(final String line){
		return line.contains("Strength") && line.contains("Freq");
	}
	
	public static boolean isCurrentSSID(String line){
		if( ! isSSID(line)) return false;
	
		//if( ! state.contains(values.ssid)) return false;
		
		line = line.trim();
		if(line.startsWith("*")) return line.contains(state.get(values.ssid));
	
		return line.startsWith("*");
	}
	
	/*	*/
	private void readWAN(){
		for(int i = 0 ; i < networkData.size() ; i++){		
			String line = networkData.get(i);
			if(line.startsWith("- Device: " + WLAN)){	
				try { // fails on [xxxxx] if disconnected 
					String ss = line.substring((line.indexOf('[')+1), line.indexOf(']'));
					if( ! state.equals(values.ssid, ss)) state.set(values.ssid, ss);
				} catch (Exception e) {
					// disconnecteddWAN();
					Util.debug("..... no wan addrsss", this);
				}
								
				wlanData.clear();
				for(int j = i+1 ; j < networkData.size() ; j++){
					if(networkData.get(j).contains("- Device")) return;
					else wlanData.add(networkData.get(j));
				}
			}	
		}
	}
	
	private void readETH() { 
		for(int i = 0 ; i < networkData.size() ; i++){		
			String line = networkData.get(i);
			if(line.startsWith("- Device: " + ETH)) {				
				ethData.clear();
				for(int j = i+1 ; j < networkData.size() ; j++){
					if(networkData.get(j).contains("- Device")) return;
					else ethData.add(networkData.get(j));
				}
			}
		}
			
		Util.debug("readETH: " + ethData.size(), this);
		// for(int i = 0 ; i < ethData.size() ; i++) Util.debug("readETH: " + i + " " + ethData.get(i), this);
	}

	
	/*
	private void callNetworkTool(){
		try {
								
			Process proc = Runtime.getRuntime().exec(new String[]{"nm-tool"});
			BufferedReader procReader = new BufferedReader(
				new InputStreamReader(proc.getInputStream()));
						
			networkData.clear();
			
			String line = null;
			while ((line = procReader.readLine()) != null){
				line = line.trim();
				if(line.length()>0) 
					if( ! networkData.contains(line))
						if(line.contains(":"))
							networkData.add(line);
			}
		
			proc.waitFor();
			
		} catch (Exception e) {
			System.out.println("callNetworkTool: " + e.getLocalizedMessage());
		}		
		
		// Util.debug("callNetworkTool: lines copied: " + networkData.size(), this);
	}

	
	private void disconnecteddWAN(){	
		state.delete(values.ssid);
		state.delete(values.localaddress);
		state.delete(values.signalnoise);
		state.delete(values.signalspeed);
		state.delete(values.signalstrength);
	}
	*/
	
	private void parseETH(){
		for(int i = 0 ; i < ethData.size() ; i++){
		
			String line = ethData.get(i);
				
			if(line.contains("State: ")){
				if(line.contains("unavailable")){
					// if(state.contains(values.ethernetaddress)){
						Util.debug("\n .. parseETH: NOT available", this);
					// 	state.delete(values.ethernetaddress);
					// }
					
					// if(state.contains(values.externaladdress)){
					//	Util.debug("... parseETH:.... has an exteral address ", this);
					// state.delete(values.externaladdress);
					// }
					
					return;
				}
			}
			
			if(line.contains("Address: ") && !line.startsWith("HW")){
				String addr = line.substring(line.indexOf("Address: ")+9).trim();
				Util.debug("parseETH: address: " + addr, this);
				//if( ! state.equals(values.ethernetaddress, addr))
				state.set(values.ethernetaddress, addr); 			
			}

			/*
			if(line.contains("Type: ")){
				String type = line.substring(line.indexOf("Type: ")+5).trim();
				Util.debug("parseETH: type: " + type, this);			
			}
			*/
			/*
			if(line.contains("HW Address: ")){
				String speed = line.substring(line.indexOf("HW Address: ")+12).trim();
				Util.debug("parseETH: mac: " + speed, this);
			}
			*/
		}
	}

	private void parseWLAN(){
		
		Util.debug("parseWLAN: " + wlanData.size(), this);
		
		for(int i = 0 ; i < wlanData.size() ; i++){
		
			String line = wlanData.get(i);
			
			if(isCurrentSSID(line)) 
				if( ! accesspoints.contains(line)) 
					accesspoints.add(line.substring(1));
			
			if(isSSID(line) && !isCurrentSSID(line)) 
				if( ! accesspoints.contains(line)) 
					accesspoints.add(line);
			
			if(line.contains("Speed: ")){
				String speed = line.substring(line.indexOf("Speed: ")+7).trim();
				// Util.debug("parseWLAN: speed: " + speed, this);
				// if( ! state.equals(values.signalspeed, speed))
					state.set(values.signalspeed, speed); 			
			}
			
			if(wlanData.get(i).startsWith("Address: ")){
				String addr = line.substring(line.indexOf("Address: ")+9).trim();
				// Util.debug("parseWLAN: addr: " + addr, this);
				// if( ! state.equals(values.localaddress, addr))
					state.set(values.localaddress, addr);
			}
			
			// TODO:--------------------------------------------------------
			if(line.startsWith("Gateway: ")){
				String gate = line.substring(line.indexOf("Gateway: ")+9).trim();
				Util.debug("parseWLAN: gate: " + gate, this);
				// if(state.contains(values.gateway)) 
					// if( ! state.get(values.gateway).equals(gate))
						state.set(values.gateway, gate);
			}
			
			
			/*	
			if(line.startsWith("State:")){
				
				// Util.debug(".... parseWLAN: found wireless state: " + line, this);

				if(line.contains("disconnected")){
				
					// state.delete(values.ssid);
					disconnecteddWAN();
					Util.debug(".......... parseWLAN: ssid is disconected, delete...", this);
					
				}
			}
			*/
			
		}			
		
		getAccessPoints();
	}
	
	public String wlanString(){
		String text = new String("<table cellpadding=\"5\"border=\"1\">\n");
		for(int j = 0 ; j < accesspoints.size() ; j++) {
			String line = accesspoints.get(j);
		
			if(state.get(values.ssid) != null)
				if(line.startsWith(state.get(values.ssid)))
					line = line.replaceFirst(state.get(values.ssid), "<b>" + state.get(values.ssid) + "</b>");
			
			line = line.replaceAll(":", "<td>");
			line = line.replaceAll(",", "<td>");
			
			text += "<tr><td>" + line + "<br />\n"; 
		}
		text += "</table>";
		
		return text;	
	}
	
	public String[] getAccessPoints(){
		Vector<String> aps = new Vector<String>();
		for(int j = 0 ; j < accesspoints.size() ; j++) {
			String ssid = accesspoints.get(j).substring(0, accesspoints.get(j).indexOf(":")).trim();
			if( ! aps.contains(ssid)) 
				aps.add(ssid);
		}
		
		int r = 0;
		String[] result = new String[aps.size()];
		for(int j = 0 ; j < aps.size() ; j++) 
			result[r++] = aps.get(j);
				
		Util.debug("getAccessPoints: found [" + result.length + "] wifi routers", this);
		// for(int i = 0; i < result.length ; i++) Util.debug((i + "\t" + result[i]), this);
		
		return result;
	}
	
	private void updateExternalIPAddress(){
		try {
			
			URLConnection connection = (URLConnection) new URL("http://checkip.dyndns.org/").openConnection();
			BufferedInputStream in = new BufferedInputStream(connection.getInputStream());

			int i;			
			String address = "";
			while ((i = in.read()) != -1) address += (char)i;
			in.close();

			// parse HTML file
			address = address.substring(address.indexOf(": ") + 2);
			address = address.substring(0, address.indexOf("</body>"));
			
			// TODO: Page could disappear, or change format ... should test valid IP  
			state.put(values.externaladdress, address);
			
			// validateNetwork();
		
		} catch (Exception e) {
			Util.log("updateExternalIPAddress()", e, this);
			state.delete(values.externaladdress);
		}
	}
	
	/*
	private void validateNetwork(){
		
		if( ! state.contains(values.externaladdress)) {
			Util.debug("validateNetwork: manage this situation...." , this);
			return;
		}
		
		try {

			Util.debug("validateNetwork: " + state.get(values.externaladdress), this);

			Process proc = Runtime.getRuntime().exec(new String[]{"ping", "-c", "1", state.get(values.externaladdress)});
			BufferedReader procReader = new BufferedReader(
				new InputStreamReader(proc.getInputStream()));
						
			String line = null;
			while ((line = procReader.readLine()) != null){
				
				Util.debug(" -- "+line, this);
				
				if(line.contains("Netork is unreachable"))
					Util.debug("validateNetwork: ............... very dangerous", this);
			
			}
			
			proc.waitFor();
			
		} catch (Exception e) {
			System.out.println("validateNetwork: " + e.getLocalizedMessage());
		}		
	}

*/
	
	/*
	@Override
	public void updated(String key) {
		
//		Util.debug("---- state updated: " + key, this);

		if (key.equals(values.ethernetaddress.name())) {

			Util.debug("eth updated: " + key + " = "+ state.get(key) , this);
			
			
		}
		
		
		if (key.equals(values.ssid.name())) {
			
			// Util.debug("updated: " + key, this);
			
			if( ! state.contains(State.values.ssid)) {
				Util.debug(".......... contains ssid: " + state.get(key), this);
			}
		}
		
	}
	*/
}
