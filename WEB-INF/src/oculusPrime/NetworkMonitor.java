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
	private static Vector<String> networkData = new Vector<String>();
	
	Timer networkTimer = new Timer();
	
	public static State state = State.getReference();
	private static NetworkMonitor singleton = null;
	public static NetworkMonitor getReference() {
		if(singleton == null) singleton = new NetworkMonitor();
		return singleton;
	}

	private NetworkMonitor(){
		networkTimer.schedule(new networkTask(), 1000, POLL_DELAY_MS);
		updateExternalIPAddress();
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
				 
				// Util.debug("networkTask: lines copied: " + networkData.size(), this);
				 
				readETH();
				readWAN();
				parseWLAN();
				getAccessPoints();
				parseETH();

			} catch (Exception e) {
				Util.debug("networkTask: " + e.getLocalizedMessage(), this);
			}		
	    }    
	}
	
	public static boolean isSSID(final String line){
		return line.contains("Strength") && line.contains("Freq");
	}
	
	public static boolean isCurrentSSID(String line){
		if( ! isSSID(line)) return false;
	
		line = line.trim();
		if(line.startsWith("*")) return line.contains(state.get(values.ssid));
	
		return line.startsWith("*");
	}
	
	private void readWAN(){
		for(int i = 0 ; i < networkData.size() ; i++){		
			String line = networkData.get(i);
			if(line.startsWith("- Device: " + WLAN)){	
				try { // fails on [xxxxx] if disconnected 
					String ss = line.substring((line.indexOf('[')+1), line.indexOf(']'));
					if( ! state.equals(values.ssid, ss)) state.set(values.ssid, ss);
				} catch (Exception e) {
					disconnecteddWAN();
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
			
		// Util.debug("readETH: " + ethData.size(), this);
		// for(int i = 0 ; i < ethData.size() ; i++) Util.debug("readETH: " + i + " " + ethData.get(i), this);
	}
	
	private void disconnecteddWAN(){	
		state.delete(values.ssid);
		state.delete(values.localaddress);
		state.delete(values.signalspeed);
	}

	private void parseETH(){
		for(int i = 0 ; i < ethData.size() ; i++){
		
			String line = ethData.get(i);
				
			if(line.contains("State: ")){
				if(line.contains("unavailable")){		
					Util.debug("...parseETH: NOT available", this);
					state.delete(values.ethernetaddress);
					return;
				}
			}
			
			if(line.contains("Address: ") && !line.startsWith("HW")){
				String addr = line.substring(line.indexOf("Address: ")+9).trim();
				Util.debug("parseETH: address: " + addr, this);
				if( ! state.contains(values.ethernetaddress)) state.set(values.ethernetaddress, addr); 	
				if( state.contains(values.ethernetaddress))
					if( ! state.equals(values.ethernetaddress, addr))
						state.set(values.ethernetaddress, addr); 			
			}
		}
	}

	private void parseWLAN(){
		
	// 	Util.debug("parseWLAN: " + wlanData.size(), this);
		
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
				Util.debug("parseWLAN: speed: " + speed, this);
				
				if( ! state.contains(values.signalspeed)) state.set(values.signalspeed, speed); 			
				if(state.contains(values.signalspeed)) 
					if( ! state.equals(values.signalspeed, speed)) 
					state.set(values.signalspeed, speed); 			
			}
			
			if(wlanData.get(i).startsWith("Address: ")){
				
				String addr = line.substring(line.indexOf("Address: ")+9).trim();
				Util.debug("parseWLAN: addr: " + addr, this);
				
				if( ! state.contains(values.localaddress)) state.set(values.localaddress, addr);
				if(state.contains(values.localaddress))
					if( ! state.equals(values.localaddress, addr))
						state.set(values.localaddress, addr);
			}
			
			if(line.startsWith("Gateway: ")){
				
				String gate = line.substring(line.indexOf("Gateway: ")+9).trim();
				Util.debug("parseWLAN: gate: " + gate, this);
				
				if( ! state.contains(values.gateway)) state.set(values.gateway, gate);
				if(state.contains(values.gateway))
					if( ! state.get(values.gateway).equals(gate))
						state.set(values.gateway, gate);
				
			}
		}			
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
				
		// Util.debug("getAccessPoints: found [" + result.length + "] wifi routers", this);
		// for(int i = 0; i < result.length ; i++) Util.debug((i + "\t" + result[i]), this);
		
		return result;
	}
	
	private void updateExternalIPAddress(){
		new Thread(new Runnable() { public void run() {
			try {

				URLConnection connection = (URLConnection) new URL("http://www.xaxxon.com/xaxxon/checkhost").openConnection();
				BufferedInputStream in = new BufferedInputStream(connection.getInputStream());

				int i;
				String address = "";
				while ((i = in.read()) != -1) address += (char)i;
				in.close();

				state.put(values.externaladdress, address);

			} catch (Exception e) {
				Util.log("updateExternalIPAddress()", e, this);
				state.delete(values.externaladdress);
			}


		} }).start();
	}
}
