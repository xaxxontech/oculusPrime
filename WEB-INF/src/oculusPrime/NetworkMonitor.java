package oculusPrime;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import oculusPrime.State.values;

public class NetworkMonitor {

	public static final long AP_TIME_OUT = 5000;
	public static final String AP = "ap";
	
	private static Vector<String> wlanData = new Vector<String>();
	private static Vector<String> ethData = new Vector<String>();
	private static Vector<String> accesspoints = new Vector<String>();
	private static Vector<String> networkData = new Vector<String>();
	private static Vector<String> connections = new Vector<String>();

	Timer networkTimer = new Timer();
	Timer routerTimer = new Timer();
	Timer pingTimer = new Timer();

	public static String pingValue = null;
	public static long pingLast = System.currentTimeMillis();

	public static State state = State.getReference();
	private static NetworkMonitor singleton = new NetworkMonitor();
	public static NetworkMonitor getReference() {
		return singleton;
	}

	private NetworkMonitor(){
		
		if(Settings.getReference().readSetting(ManualSettings.networkmonitor).equals("false")) return;
		
		networkTimer.schedule(new networkTask(), AP_TIME_OUT-200);
		// routerTimer.schedule(new checkRouterTask(), 5000, Util.FIVE_MINUTES);
		pingTimer.schedule(new pingTask(), AP_TIME_OUT, AP_TIME_OUT-300);
		updateExternalIPAddress();
		connectionUpdate();
		connectionsNever();
		killApplet();
	}
	
	public class pingTask extends TimerTask {			
	    @Override
	    public void run() {
	    	if(! state.equals(values.ssid, AP)) {
	    		if(state.exists(values.externaladdress)){
	    			
	    			pingValue = pingWIFI("www.xaxxon.com");
	    			if(pingValue != null) pingLast = System.currentTimeMillis();
	    		
	    		} else {	
	    			updateExternalIPAddress();
	    		}
	    		
	    		if((System.currentTimeMillis() - pingLast) > AP_TIME_OUT){
	    			Util.log("pingTask()... starting ap mode now...", this);
	    			startAdhoc();
	    		}
	    	}
	    }    
	}
	
	/*
	public class checkRouterTask extends TimerTask {			
	    @Override
	    public void run() {
			if(connectionExists(DEFAULT_ROUTER)){
				if( ! state.equals(values.ssid, DEFAULT_ROUTER)) {
					Util.log("checkRouterTask: current = "+ state.get(values.ssid) + " prefered = " + DEFAULT_ROUTER, this);
					changeWIFI(DEFAULT_ROUTER);
				}
			}
	    }    
	}
	*/
	
	public static String pingWIFI(final String addr){
		
		long start = System.currentTimeMillis();
		
		Process proc = null;
		try {                                              
			// TODO: force interface
			proc = Runtime.getRuntime().exec(new String[]{"ping", "-c1", "-W1", addr});
		} catch (IOException e) {
			Util.log("pingWIFI(): ", e.getMessage());
			Util.log("pingWIFI(): ping fail: " + (System.currentTimeMillis()-start));
			return null;
		}  
		
		String line = null;
		String time = null;
		BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));					
		
		try {
			while ((line = procReader.readLine()) != null){
				if(line.contains("time=")){
					time = line.substring(line.indexOf("time=")+5, line.indexOf(" ms"));
					break;
				}	
			}
		} catch (IOException e) {
			Util.log("pingWIFI(): ", e.getMessage());
		}
		
		if((System.currentTimeMillis()-start) > 1000)
			Util.log("pingWIFI(): ping timed out, took over a second: " + (System.currentTimeMillis()-start));
		
		return time;	
	}
	
	public class networkTask extends TimerTask {
		@Override
		public void run() {
			try{

				// long start = System.currentTimeMillis();

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


				// Util.log("networkTask: lines copied: " + networkData.size(), this);

				proc.waitFor();
				readETH();
				readWAN();
				parseWLAN();
				getAccessPoints();
				parseETH();

				// is about 250ms to reply
				// Util.debug("networkTask(): nm-tool took: " + (System.currentTimeMillis()-start));

			} catch (Exception e) {
				Util.debug("networkTask: " + e.getLocalizedMessage(), this);
			}
		}
	}

	public boolean connectionExists(final String ssid){
		for(int i = 0 ; i < (connections.size()) ; i++)
			if(connections.get(i).startsWith(ssid.trim()))
				return true;

		return false;
	}

	private void connectionsNever(){
		try {

			Process proc = Runtime.getRuntime().exec(new String[]{"nmcli", "con", /*"list"*/ });
			BufferedReader procReader = new BufferedReader(
					new InputStreamReader(proc.getInputStream()));

			String line = null;
			while ((line = procReader.readLine()) != null){
				line = line.trim();
				if(line.endsWith("never")){
					Util.debug("connectionsNever: " + line, this);
					removeConnection(line.substring(0, 8).trim());
				}
			}
		} catch (Exception e) {
			Util.debug("connectionsNever: " + e.getLocalizedMessage(), this);
		}
	}

	private void removeConnection(final String ssid){
		try {
			Runtime.getRuntime().exec(new String[]{"nmcli", "con", "delete", "id", ssid});
			Util.debug("removeConnection: " + ssid, this);
		} catch (Exception e) {
			Util.debug("removeConnection: " + e.getLocalizedMessage(), this);
		}
	}

	private void killApplet(){
		try {
			Runtime.getRuntime().exec(new String[]{"pkill", "nmcli"});
			Runtime.getRuntime().exec(new String[]{"pkill", "nm-applet"});
			// Util.debug("killApplet(): called... do nada ", this);
		} catch (Exception e) {
			Util.debug("killApplet(): " + e.getLocalizedMessage(), this);
		}
	}

	public void connectionUpdate(){
		try {
			connections.clear();
			Process proc = Runtime.getRuntime().exec(new String[]{"nmcli", "con", /* "list"*/ });
			BufferedReader procReader = new BufferedReader(
					new InputStreamReader(proc.getInputStream()));

			String line = null;
			while ((line = procReader.readLine()) != null){
				if( !line.startsWith("NAME") && line.contains("wireless"))
					connections.add(line);
			}
		} catch (Exception e) {
			Util.debug("connectionUpdate(): " + e.getLocalizedMessage(), this);
		}
	}

	public void changeWIFI(final String ssid, final String password){
		try {
			killApplet();
			long start = System.currentTimeMillis();
			Process proc = Runtime.getRuntime().exec(new String[]{"nmcli", "dev", "wifi", "connect", ssid, "password", password});
			proc.waitFor();
			Util.log("changeWIFI(): (with password):" + ssid + " " + (System.currentTimeMillis() - start)/1000 +  " seconds", this); 
		} catch (Exception e) { 
			Util.debug("changeWIFI: " + e.getLocalizedMessage(), this); 
		}		
	}

	public void changeWIFI(final String ssid){
		long start = System.currentTimeMillis();
		try {

			Process proc = Runtime.getRuntime().exec(new String[]{"nmcli", "c", "up", "id", ssid }); // , "&"});
			proc.waitFor();
			Util.log("changeWIFI(): " + ssid + " exit code: " + proc.exitValue(), this);
			Util.log("changeWIFI(): " + ssid + " " + (System.currentTimeMillis() - start)/1000 +  " seconds", this);
			
		} catch (Exception e) {
			Util.log("changeWIFI(): " + ssid + " " + (System.currentTimeMillis() - start)/1000 +  " seconds", this); 
		}
	}

	public void startAdhoc(){
		disconnecteddWAN();
		changeWIFI(AP);
	}

	private static boolean isSSID(final String line) {
		return line.contains("Strength") && line.contains("Freq");
	}

	private void setSSID(final String line) {
		// Util.debug("ssid: " + line, this);
		if(line.contains("[") && line.contains("]")){
			String router = line.substring(line.indexOf("[")+1, line.indexOf("]"));
			if( ! state.equals(values.ssid, router))
				state.set(values.ssid, router);
		}
	}

	private void readWAN(){
		for(int i = 0 ; i < networkData.size() ; i++){
			String line = networkData.get(i);
			if(line.startsWith("- Device: wlan")) {
				setSSID(line);
				wlanData.clear();
				for(int j = i+1 ; j < networkData.size() ; j++){
					if(networkData.get(j).contains("- Device")) return;
					else wlanData.add(networkData.get(j));
				}
			}
		}

		//	Util.debug("readWAN(): " + wlanData.size(), this);
		//	for(int i = 0 ; i < wlanData.size() ; i++) Util.debug("wlanData: " + i + " " + wlanData.get(i));
	}

	private void readETH() {
		for(int i = 0 ; i < networkData.size() ; i++){
			String line = networkData.get(i);
			if(line.startsWith("- Device: eth")) {
				ethData.clear();
				for(int j = i+1 ; j < networkData.size() ; j++){
					if(networkData.get(j).contains("- Device")) return;
					else ethData.add(networkData.get(j));
				}
			}
		}

		//	Util.debug("readETH: " + ethData.size(), this);
		//	for(int i = 0 ; i < ethData.size() ; i++) Util.debug("readETH: " + i + " " + ethData.get(i), this);
	}

	private void disconnecteddWAN(){
		state.delete(values.ssid);
		state.delete(values.localaddress);
		state.delete(values.signalspeed);
		state.delete(values.gateway);
		pingValue = null;
	}

	private void parseETH(){
		for(int i = 0 ; i < ethData.size() ; i++){

			String line = ethData.get(i).trim();

			if(line.contains("State: ")){
				if(line.contains("unavailable")){
					// Util.debug("...parseETH: NOT available", this);
					state.delete(values.ethernetaddress);
					return;
				}
			}

			if(line.contains("Address: ") && !line.startsWith("HW")){
				String addr = line.substring(line.indexOf("Address: ")+9).trim();
				// Util.debug("parseETH: address: " + addr, this);
				if( ! state.exists(values.ethernetaddress)) state.set(values.ethernetaddress, addr);
				if( state.exists(values.ethernetaddress))
					if( ! state.equals(values.ethernetaddress, addr))
						state.set(values.ethernetaddress, addr);
			}
		}
	}

	private void parseWLAN(){

		// 	Util.debug("parseWLAN: " + wlanData.size(), this);

		boolean star = false;

		for(int i = 0 ; i < wlanData.size() ; i++){

			String line = wlanData.get(i).trim();

			if(line.startsWith("*")) {
				line = line.substring(1);
				star = true;
			}

			if(isSSID(line))
				if( ! accesspoints.contains(line))
					accesspoints.add(line);

			if(line.contains("Speed: ")){

				String speed = line.substring(line.indexOf("Speed: ")+7).trim();
				// Util.debug("parseWLAN: speed: " + speed, this);
				if( ! state.exists(values.signalspeed)) state.set(values.signalspeed, speed);
				if(state.exists(values.signalspeed))
					if( ! state.equals(values.signalspeed, speed))
						state.set(values.signalspeed, speed);
			}

			if(wlanData.get(i).startsWith("Address: ")){

				String addr = line.substring(line.indexOf("Address: ")+9).trim();
				// Util.debug("parseWLAN: addr: " + addr, this);			
				if( ! state.exists(values.localaddress)) state.set(values.localaddress, addr);
				if(state.exists(values.localaddress))
					if( ! state.equals(values.localaddress, addr))
						state.set(values.localaddress, addr);
			}

			if(line.startsWith("Gateway: ")){

				String gate = line.substring(line.indexOf("Gateway: ")+9).trim();
				// Util.debug("parseWLAN: gate: " + gate, this);
				if( ! state.exists(values.gateway)) state.set(values.gateway, gate);
				if(state.exists(values.gateway))
					if( ! state.get(values.gateway).equals(gate))
						state.set(values.gateway, gate);

			}
			
			/*if(line.startsWith("State: ")){
				
				String gate = line.substring(line.indexOf("Gateway: ")+9).trim();
				Util.debug("parseWLAN: State: " + gate, this);
				killApplet
			}*/

		}

		if( !star ) disconnecteddWAN();
	}
	
	/*
	public String wlanHTML(){
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
	}*/
	
	public String[] getAccessPoints(){
		Vector<String> aps = new Vector<String>();
		for(int j = 0 ; j < accesspoints.size() ; j++) {
			if(accesspoints.get(j).contains(":")){
				String ssid = accesspoints.get(j).substring(0, accesspoints.get(j).indexOf(":")).trim();
				if( ! aps.contains(ssid))
					aps.add(ssid);
			}
		}

		int r = 0; // copy results
		String[] result = new String[aps.size()];
		for(int j = 0 ; j < aps.size() ; j++)
			result[r++] = aps.get(j);

		// Util.debug("getAccessPoints(): found [" + result.length + "] wifi routers", this);
		// for(int i = 0; i < result.length ; i++) Util.debug((result[i]));

		return result;
	}
	
	/*
	public String[] getKnownAccessPoints() {
		Vector<String> aps = new Vector<String>();
		for(int j = 0 ; j < accesspoints.size() ; j++) {
			String ssid = accesspoints.get(j).substring(0, accesspoints.get(j).indexOf(":")).trim();
			if(connectionExists(ssid))
				aps.add(ssid);
		}
		
		int r = 0;
		String[] result = new String[aps.size()];
		for(int j = 0 ; j < aps.size() ; j++) 
			result[r++] = aps.get(j);
				
		//Util.debug("getAccessPoints: found [" + result.length + "] wifi routers", this);
		//for(int i = 0; i < result.length ; i++) Util.debug((i + "\t" + result[i]), this);
		
		return result;
	}
	*/

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
				Util.log("updateExternalIPAddress():", e, this);
				state.delete(values.externaladdress);
			}
		} }).start();
	}
}
