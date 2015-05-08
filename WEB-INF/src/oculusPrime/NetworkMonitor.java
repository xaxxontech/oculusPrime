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

	private static Timer networkTimer = new Timer();
	private static Timer pingTimer = new Timer();
	
	private static long pingLast = System.currentTimeMillis();
	private static String pingValue = null;
	
	public static State state = State.getReference();
	public static Settings settings = Settings.getReference();
	private static NetworkMonitor singleton = new NetworkMonitor();
	public static NetworkMonitor getReference() {
		return singleton;
	}

	private NetworkMonitor(){

		pingTimer.schedule(new pingTask(), AP_TIME_OUT, AP_TIME_OUT);
		networkTimer.schedule(new networkTask(), 3000, 3000);	
	
		updateExternalIPAddress();
		connectionUpdate();
		connectionsNever();
		killApplet();
	}
	
	public class pingTask extends TimerTask {			
	    @Override
	    public void run() {
	    	
			if(settings.readSetting(ManualSettings.networkmonitor).equals("false")) return;
	    	
	    	if(! state.equals(values.ssid, AP)) {
	    		if(state.exists(values.gateway)){ //values.externaladdress)){
	    			
	    			pingValue = pingWIFI(state.get(values.gateway));//"www.xaxxon.com");
	    			if(pingValue == null) {
	    				
	    				Util.log("NetworkMonitor().pingTask() ... dropped ping...", "");
	    				
	    				//Util.delay(900);
	    				//pingValue = pingWIFI(state.get(values.gateway));//"www.xaxxon.com");
		    			//if(pingValue == null) pingLast = System.currentTimeMillis();
	    			
	    			} else {
	    					
	    				pingLast = System.currentTimeMillis();
	    		
	    			}
	    		}
	    			
	    		if( ! state.exists(values.externaladdress)) updateExternalIPAddress();
	    		
	    		if((System.currentTimeMillis() - pingLast) > (AP_TIME_OUT*2)){
	    			Util.log("pingTask()... starting ap mode now...", this);
	    			// pingLast = System.currentTimeMillis() + AP_TIME_OUT*5;
	    			// startAdhoc();
	    		}
	    	}
	    }    
	}
	
	public String pingWIFI(final String addr){
		
		long start = System.currentTimeMillis();
		
		Process proc = null;
		try {                                              
			// TODO: force interface
			proc = Runtime.getRuntime().exec(new String[]{"ping", "-c1", "-W1", addr});
		} catch (IOException e) {
			Util.log("NetworkMonitor().pingWIFI(): "+ e.getMessage(), "");
			Util.log("NetworkMonitor()pingWIFI(): ping fail: " + (System.currentTimeMillis()-start), "");
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


		if((System.currentTimeMillis()-start) > 10100){
			Util.log("pingWIFI(): ping timed out, took over a second: " + (System.currentTimeMillis()-start), "");
			if(time == null) Util.log("pingWIFI(): null result for address: " + addr, "");
		}

		return time;	
	}
	
	public class networkTask extends TimerTask {
		@Override
		public void run() {
			try{

				if(settings.readSetting(ManualSettings.networkmonitor).equals("false")) return;

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
				
				// connectionUpdate();


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

			Process proc = Runtime.getRuntime().exec(new String[]{"nmcli", "con" });
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
			Process proc = Runtime.getRuntime().exec(new String[]{"nmcli", "con", "delete", "id", ssid});
			BufferedReader procReader = new BufferedReader(
					new InputStreamReader(proc.getInputStream()));

			String line = null;
			while ((line = procReader.readLine()) != null){
				line = line.trim();
				if(line.endsWith("never")){
					Util.log("removeConnection: " + line, this);
				}
			}
		} catch (Exception e) {
			Util.debug("killApplet(): " + e.getLocalizedMessage(), this);
		}
	}

	private void killApplet(){
		try {
			Runtime.getRuntime().exec(new String[]{"pkill", "nmcli"});
			Runtime.getRuntime().exec(new String[]{"pkill", "nm-applet"});
		} catch (Exception e) {
			Util.debug("killApplet(): " + e.getLocalizedMessage(), this);
		}
	}

	public synchronized String[] getConnections() {
		String[] con = new String[connections.size()];
		for(int i = 0 ; i < con.length ; i++){	
			
			String[] line = connections.get(i).split(" ");
			for(int j = 0 ; j < line.length ; j++) {
				if(line[j].matches("[0-9a-f]{8}-([0-9a-f]{4}-){3}[0-9a-f]{12}")){
					String id = "";
					for(int u = 0 ; u < j ; u++) id+= line[u] + " ";
					con[i] = id.trim();
				}
			}
		}
	
		return con;
	}
	
	public synchronized void connectionUpdate(){
		try {
			connections.clear();
			Process proc = Runtime.getRuntime().exec(new String[]{"nmcli", "con"});
			BufferedReader procReader = new BufferedReader(
					new InputStreamReader(proc.getInputStream()));

			String line = null;
			while ((line = procReader.readLine()) != null){
				if( !line.startsWith("NAME") && line.contains("wireless") && !line.contains("never"))
					connections.add(line);
			}
		} catch (Exception e) {
			Util.debug("connectionUpdate(): " + e.getLocalizedMessage(), this);
		}
	}

	public void changeWIFI(final String ssid, final String password){
	
		disconnecteddWAN();
		
		new Thread(){
		    public void run() {
		    	try {
		
					long start = System.currentTimeMillis();
					Process proc = Runtime.getRuntime().exec(new String[]{"nmcli", "dev", "wifi", "connect", ssid, "password", password});
					
					BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));

					String line = null;
					while ((line = procReader.readLine()) != null)
						Util.log("changeWIFI(): stdout: " + ssid + " " + line, this);
					
					proc.waitFor();
					Util.log("changeWIFI(): (with password):" + ssid + " " + (System.currentTimeMillis() - start)/1000 +  " seconds", this); 
				    	
		    	} catch (Exception e) {
					Util.log("changeWIFI(): [" + ssid + "] ", e, this); 
				}
		    }
		}.start();
	}

	public void changeWIFI(final String ssid){
		
		disconnecteddWAN();
		
		new Thread(){
		    public void run() {
		    	try {

		    		long start = System.currentTimeMillis();                                                // TODO: spaces in ssid? 
					Process proc = Runtime.getRuntime().exec(new String[]{"nmcli", "c", "up", "id", ssid}); //  "\""+ssid+"\"" }); 
					
					//InputStream in = proc.getInutStream();
				/* give root pw? 
					OutputStream in = proc.getOutputStream();
					in.write("xxxxxxx\n\r".getBytes());
					in.close();
				*/
					BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));

					String line = null;
					while ((line = procReader.readLine()) != null)
						Util.log("changeWIFI(): stdout: " + ssid + " " + line, this);
					
					procReader = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
					while ((line = procReader.readLine()) != null)
						Util.log("changeWIFI(): error: " + ssid + " " + line, this);
					
					proc.waitFor();
					Util.log("changeWIFI(): [" + ssid + "] exit code: " + proc.exitValue(), this);
					Util.log("changeWIFI(): [" + ssid + "] " + (System.currentTimeMillis() - start)/1000 +  " seconds", this);
					
				} catch (Exception e) {
					Util.log("changeWIFI(): [" + ssid + "] ", e, this); 
				}
		    }
		}.start();
	}

	public synchronized void startAdhoc(){
		disconnecteddWAN();
		changeWIFI(AP);
	}

	private boolean isSSID(final String line) {
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
				if(Util.validIP(addr)){
					if( ! state.exists(values.ethernetaddress)) state.set(values.ethernetaddress, addr);
					
					if( state.exists(values.ethernetaddress)){
						if( ! state.equals(values.ethernetaddress, addr)){
							state.set(values.ethernetaddress, addr);
						}
					}
				} 
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
				if(Util.validIP(addr)){

					if( ! state.exists(values.localaddress)) state.set(values.localaddress, addr);
					
					if(state.exists(values.localaddress)){
						if( ! state.equals(values.localaddress, addr)){
							state.set(values.localaddress, addr);
						}
					}
				}
			}

			if(line.startsWith("Gateway: ")){

				String gate = line.substring(line.indexOf("Gateway: ")+9).trim();
				// Util.debug("parseWLAN: gate: " + gate, this);
				if(Util.validIP(gate)){
					
					if( ! state.exists(values.gateway)) state.set(values.gateway, gate);
					
					if(state.exists(values.gateway)){
						if( ! state.get(values.gateway).equals(gate)){
							state.set(values.gateway, gate);
						}
					}
				}
			}
			
			/*if(line.startsWith("State: ")){
				
				String gate = line.substring(line.indexOf("Gateway: ")+9).trim();
				Util.debug("parseWLAN: State: " + gate, this);
				killApplet
			}*/

		}

		if( !star ) disconnecteddWAN();
	}
	
	public String[] getAccessPoints(){
		Vector<String> aps = new Vector<String>();
		for(int j = 0 ; j < accesspoints.size() ; j++) {
			if(accesspoints.get(j).contains(":")){
				String ssid = accesspoints.get(j).substring(0, accesspoints.get(j).indexOf(":")).trim();
				if( ! aps.contains(ssid))
					if( ! connectionExists(ssid))
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
	
	private void updateExternalIPAddress(){
		new Thread(new Runnable() { public void run() {
			try {

				URLConnection connection = (URLConnection) new URL("http://www.xaxxon.com/xaxxon/checkhost").openConnection();
				BufferedInputStream in = new BufferedInputStream(connection.getInputStream());

				int i;
				String address = "";
				while ((i = in.read()) != -1) address += (char)i;
				in.close();

				if(Util.validIP(address)) state.put(values.externaladdress, address);
				else Util.log("read invalid address from server", this);
		
			} catch (Exception e) {
				Util.log("updateExternalIPAddress():", e, this);
				state.delete(values.externaladdress);
			}
		} }).start();
	}

	public String getPingTime() {
		return pingValue;
	}

	public long getLast() {
		return pingLast;
	}
}
