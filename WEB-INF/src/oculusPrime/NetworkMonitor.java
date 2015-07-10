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

public class NetworkMonitor implements Observer {
	
	public static final String AP = "ap";

	private static Vector<String> wlanData = new Vector<String>();
	private static Vector<String> ethData = new Vector<String>();
	private static Vector<String> accesspoints = new Vector<String>();
	private static Vector<String> networkData = new Vector<String>();
	private static Vector<String> connections = new Vector<String>();
	private static long pingLast = System.currentTimeMillis();
	private static long defaultLast = System.currentTimeMillis();
	private static long scanLast = System.currentTimeMillis();
	private static Settings settings = Settings.getReference();
	private static State state = State.getReference();	
	private static Application application = null; 
	private static boolean wiredConnection = false;	
	private static boolean changingWIFI = false;
	private static Timer pingTimer = new Timer();
	private static String currentUUID = null;
	private static String pingValue = null;
	private static String wdev = null;	
	
	public static void setApp(Application a) {application = a;}
		
	private NetworkMonitor(){
		
//		updateExternalIPAddress();
		connectionUpdate();
		runNetworkTool();
		
		/*
		if(settings.getBoolean(ManualSettings.networkmonitor)){
			pingTimer.schedule(new pingTask(), 5000, 3000);
			new eventThread().start();	
			state.addObserver(this);
			connectionUpdate();
			runNetworkTool();
			//killApplet();
		}
		*/
		
	}
	
	@Override
	public void updated(String key) {
		
		if(key.equals(values.externaladdress.name())) 
			if( ! state.exists(values.ethernetaddress))
				Util.updateExternalIPAddress();
	
		if(key.equals(values.ssid.name())) {
			
			changingWIFI = false;
//			apLast = System.currentTimeMillis();
			defaultLast = System.currentTimeMillis();
			pingLast = System.currentTimeMillis(); 
			
			if(state.exists(values.ssid)) currentUUID = lookupUUID(state.get(values.ssid));
			else Util.log("updated(): ssid was deleted.. ", null);
			
			if( ! state.equals(values.ssid, AP) && ManualSettings.isDefault(ManualSettings.defaultuuid))
				setDefault(state.get(values.ssid));
						
			if(getAccessPoints() == null && state.equals(values.ssid, AP)){
				Util.log("updated(): no access points, disconnect..", null);
				disconnect();
				wifiEnable();
			}			
		}
	}
	
	private class pingTask extends TimerTask {
		@Override
	    public void run() {
	
	    	if( ! state.getBoolean(State.values.autodocking)) { 
		    	if( ! state.exists(values.ssid) || state.equals(values.ssid, AP) || changingWIFI) { 
		    		if(application != null && (state.getUpTime() > Util.ONE_MINUTE))
		    			application.driverCallServer(PlayerCommands.strobeflash, "on 10 10");
		    	}
	    	}
	    
	    	if(state.exists(values.gateway) && !changingWIFI){ 
    			pingValue = Util.pingWIFI(state.get(values.gateway));
    			if(pingValue != null) pingLast = System.currentTimeMillis(); 
    			else Util.log("pingTask(): fail: " + (System.currentTimeMillis() - pingLast), null);
	    	}
	    		
	    	if((System.currentTimeMillis() - defaultLast) > Util.TWO_MINUTES) {
	    		Util.log("pingTask(): no default connection after mode after 2 minutes..", null);
	    		defaultLast = System.currentTimeMillis(); 
	    		changeUUID(settings.readSetting(ManualSettings.defaultuuid));
	    	}
    	
    		if((System.currentTimeMillis() - pingLast) > Util.FIVE_MINUTES) {
    			Util.log("pingTask(): Starting AP mode after 5 minutes disconnected..", null);
    			startAdhoc();
    		}
    		
			if(currentUUID != null) {
				if(currentUUID.equals(settings.readSetting(ManualSettings.defaultuuid))) 
					defaultLast = System.currentTimeMillis();
			}
			
    		if(state.equals(values.ssid, AP)) {    			
    			if((System.currentTimeMillis() - defaultLast) > Util.FIVE_MINUTES) {
    				Util.log("pingTask(): AP mode for 5 minutes, disconnecting..", null);
    				disconnect();
    			}
    			
    			// apLast = System.currentTimeMillis();
    		}
    	}
	}
	
	private class eventThread extends Thread {
		@Override
		public void run() {
			try{	
				
				Runtime.getRuntime().exec(new String[]{"pkill", "iwevent"});

				Util.delay(1000);
				
				Process proc = Runtime.getRuntime().exec(new String[]{"iwevent"});
				BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));

				String line = null;
				while ((line = procReader.readLine()) != null){
					
					// Util.log(" skipping nm-tool.... " + line, null);
					
					if(line.contains("completed") || line.contains("New")) {
						
						if(( System.currentTimeMillis() - scanLast ) > 2000){	
							
							Util.log("[" + ( System.currentTimeMillis() - scanLast )/1000 + "] seconds: " + line, null);
							scanLast = System.currentTimeMillis();
							runNetworkTool();
							
						}
					}
				}
			} catch (Exception e) {
				Util.debug("eventThread: " + e.getLocalizedMessage(), null);
			}
		}
	}
	
	/*
	private class pingThread extends Thread {
		@Override
		public void run() {
			try{			
				
				Process proc = Runtime.getRuntime().exec(new String[]{"ping", state.get(values.gateway)});
				BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));

				String line = null;
				long last = System.currentTimeMillis();
				while ((line = procReader.readLine()) != null){
					//if(line.contains("completed") || line.contains("New")) {
						
					Util.log("pingThread [" + ( System.currentTimeMillis() - last ) + "] " + line, null);
					
					
					last = System.currentTimeMillis();
					
				}
			} catch (Exception e) {
				Util.debug("eventThread: " + e.getLocalizedMessage(), null);
			}
		}
	}
	*/
	
	private static void runNetworkTool(){
		try {
			
			networkData.clear();
			wlanData.clear();
			ethData.clear();
			
			Process proc = Runtime.getRuntime().exec(new String[]{"nm-tool"});
			BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));

			String line = null;
			while ((line = procReader.readLine()) != null){
				line = line.trim();
				if(line.length()>0)
					if( ! networkData.contains(line))
						if(line.contains(":"))
							networkData.add(line);
			}
			
			Util.log("networkTask: lines copied: " + networkData.size(), null);

			proc.waitFor();
			readETH();
			readWAN();
			parseWLAN();
			getAccessPoints();
			parseETH();
			
		} catch (Exception ex){
			Util.log("networkTask: nm-tool failure: " + ex.getMessage(), null);
		}
	}
	
	public static boolean connectionExists(final String ssid){
		for(int i = 0 ; i < (connections.size()) ; i++)
			if(connections.get(i).startsWith(ssid.trim()))
				return true;

		return false;
	}
	
	private static String getConnectionName(final String input){
		String ans = null;
		String[] line = input.split(" ");
		for(int j = 0 ; j < line.length ; j++) {
			if(line[j].matches("[0-9a-f]{8}-([0-9a-f]{4}-){3}[0-9a-f]{12}")){
				String id = "";
				for(int u = 0 ; u < j ; u++) id+= line[u] + " ";
				ans = id.trim();
			}
		}
		
		return ans;
	}
	
	private static String getConnectionUUID(final String input){
		String ans = null;
		String[] line = input.split(" ");
		for(int j = 0 ; j < line.length ; j++) {
			if(line[j].matches("[0-9a-f]{8}-([0-9a-f]{4}-){3}[0-9a-f]{12}")){
				return line[j];
			}
		}
		
		return ans;
	}
	
	public static String[] getConnections() {
		String[] con = new String[connections.size()]; 
		for(int i = 0 ; i < con.length ; i++)
			con[i] = getConnectionName(connections.get(i));
		
		return con;
	}
	
	public static void setDefault(final String router) {
		
		if(router.equals(AP)) return;
		
		for(int i = 0 ; i < connections.size() ; i++) {
			if(connections.get(i).startsWith(router)) {
				String uuid = getConnectionUUID(connections.get(i));
				settings.writeSettings(ManualSettings.defaultuuid, uuid);
			}	
		}
	}
	
	public static String lookupUUID(final String ssid){
		for(int i = 0 ; i < connections.size() ; i++) {
			if(connections.get(i).startsWith(ssid)) 
				return getConnectionUUID(connections.get(i));
		}
		
		return null;
	}
	
	private static String lookupSSID(final String uuid){
		for(int i = 0 ; i < connections.size() ; i++) {
			if(connections.get(i).contains(uuid)) 
				return getConnectionName(connections.get(i));
		}
		
		return null;
	}
	
	private void connectionUpdate(){
		try {
			connections.clear();
			Process proc = Runtime.getRuntime().exec(new String[]{"nmcli", "con"});
			BufferedReader procReader = new BufferedReader(
					new InputStreamReader(proc.getInputStream()));

			String line = null;
			while ((line = procReader.readLine()) != null){              
				if( ! line.startsWith("NAME") && line.contains("wireless")) 
					if( ! connections.contains(line))
						connections.add(line); 
			}
		} catch (Exception e) {
			Util.debug("connectionUpdate(): " + e.getLocalizedMessage(), null);
		}
	}
	
	
	public synchronized static void changeUUID(final String uuid){
		
		if(uuid == null) return;
	
		if(changingWIFI){
			Util.debug("changeUUID: busy, rejected", null);
			return;
		}
		
		if(lookupSSID(uuid) == null){
			Util.log("changeUUID: null connection for uuid, startAdhoc.. ", null);
			startAdhoc();
			return;
		} else {
			if(settings.readSetting(ManualSettings.defaultuuid).equals(lookupSSID(uuid))){
				Util.log("changeUUID: already connected to the default connection, rejected", null);
				startAdhoc();
				return;
			}
		}

		if(ManualSettings.isDefault(ManualSettings.defaultuuid)) {
			Util.debug("changeUUID: no default uuid in settings, startAdhoc..", null);
			startAdhoc();
			return;
		}

		new Thread(){
		    public void run() {
		    	try {
		    		
		    		changingWIFI = true;
					Process proc = Runtime.getRuntime().exec(new String[]{"nmcli", "c", "up", "uuid", uuid});
					proc.waitFor();
					
					Util.log("changeUUID: [" + lookupSSID(uuid) + "] exit code: " + proc.exitValue(), null);
					
					if(proc.exitValue()==3) {	
						Util.log("changeUUID: timeout, try adhoc: " + proc.exitValue(), null);
						startAdhoc();
						return;
					}
					
					if(proc.exitValue() == 0) runNetworkTool();
					changingWIFI = false;	
					
		    	} catch (Exception e) {
					Util.log("changeUUID(): [" + uuid + "]: Exception: ", e, null); 
				}
		    }
		}.start();
	}

	public synchronized static void changeWIFI(final String ssid){
		
		if(ssid == null) return; 
		
		if(changingWIFI){
			Util.debug("changeWIFI(): busy, rejected", null);
			return;
		}
		
		if(state.equals(values.ssid, ssid)) {
			
			Util.debug("changeWIFI(): already is the ssid: " + ssid, null);
			
			if(ssid.equals(AP)) { 
				if(getAccessPoints().length == 0){ 
					Util.log("changeWIFI(): no access points, disconnecting..", null);
					disconnect();
					wifiEnable();
				}
			}
			
			return;
		}
		
		new Thread(){
		    public void run() {
		    	try {
	
		    		changingWIFI = true;	
					Process proc = Runtime.getRuntime().exec(new String[]{"nmcli", "c", "up", "id", ssid}); // "\""+ssid+"\"" }); 
					proc.waitFor();
					
					Util.log("changeWIFI(): [" + ssid + "] exit code: " +  proc.exitValue(), null);
					
					if(proc.exitValue()==0) {
						runNetworkTool();
						if(ManualSettings.isDefault(ManualSettings.defaultuuid)) {
							Util.log("changeWIFI(): setting as default ["+ssid+"]", null);		
							setDefault(ssid);	
						}
					}
					
					changingWIFI = false;	
					
		    	} catch (Exception e) {
					Util.log("changeWIFI(): [" + ssid + "] exception: ", e, null); 
				}
		    }
		}.start();
	}
	
	//private void killApplet(){
	//	try {
	//		Runtime.getRuntime().exec(new String[]{"pkill", "nmcli"});
	//		Runtime.getRuntime().exec(new String[]{"pkill", "nm-applet"});
	//	} catch (Exception e) {
	//		Util.debug("killApplet(): " + e.getLocalizedMessage(), null);
	//	}
	//}


	private static void disconnect(){
		
		if( wdev == null ){
			Util.log("disconnect(): wdev is null, can't disconnect..", null); 
			return;
		}
		
		Util.log("disconnect(): called to start scanning...", null); 
		
		try {
			
			// Process proc = Runtime.getRuntime().exec(new String[]{"nmcli", "dev", "disconnect", "iface", wdev});
			// Process proc = Runtime.getRuntime().exec(new String[]{"nmcli", "dev", "disconnect", "iface", wdev});
			
			Process proc = Runtime.getRuntime().exec(new String[]{"nmcli", "c", "down", "uuid", currentUUID});

			String line = null;
			BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));					
			while ((line = procReader.readLine()) != null)
				Util.debug("disconnect(): in: " + line, null); 
				
			procReader = new BufferedReader(new InputStreamReader(proc.getErrorStream()));					
			while ((line = procReader.readLine()) != null)
				Util.debug("disconnect(): error: " + line, null); 
				
			proc.waitFor();
			Util.log("disconnect(): exit code: " + proc.exitValue(), null); 

			if(proc.exitValue() == 0) disconnecteddWAN();
		
		} catch (Exception e) {
			Util.debug("disconnect(): " + e.getLocalizedMessage(), null);
		}
	}
	 

	private static void wifiEnable(){
		
		if( wdev == null ){
			Util.log("wifiEnable(): wdev is null, can't disconnect..", null); 
			return;
		}
		
		Util.log("wifiEnable(): called to start scanning...", null); 
		
		try {
			
			Process proc = Runtime.getRuntime().exec(new String[]{"nmcli", "nm", "wifi", "on"});
			
			String line = null;
			BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));					
			while ((line = procReader.readLine()) != null)
				Util.debug("wifiEnable(): in: " + line, null); 
				
			procReader = new BufferedReader(new InputStreamReader(proc.getErrorStream()));					
			while ((line = procReader.readLine()) != null)
				Util.debug("wifiEnable(): error: " + line, null); 
				
			proc.waitFor();
			Util.log("wifiEnable(): exit code: " + proc.exitValue(), null); 
/*
			if(proc.exitValue() == 0) {
				disconnecteddWAN();
			}
*/
			
		} catch (Exception e) {
			Util.debug("wifiEnable(): exception: " + e.getLocalizedMessage(), null);
		}
	}
	
	/*
	private void iwlist(){
		
		if( wdev == null ){
			Util.log("iwlist(): wdev is null, can't disconnect..", null); 
			return;
		}
		
		try {
			
			Util.delay(9000);
			
			Util.log("iwlist(): ----called to start scanning------", null); 
		
			Process proc = Runtime.getRuntime().exec(new String[]{"iwlist", wdev, "scanning", "|", "grep", "SSID"});
			
			//new BufferedWriter proc.getOutputStream();
			
			String line = null;
			BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));					
			while ((line = procReader.readLine()) != null)
				Util.debug("iwlist(): in: " + line, null); 
				
			procReader = new BufferedReader(new InputStreamReader(proc.getErrorStream()));					
			while ((line = procReader.readLine()) != null)
				Util.debug("iwlist(): error: " + line, null); 
				
			proc.waitFor();
			Util.log("iwlist(): exit code: " + proc.exitValue(), null); 

		} catch (Exception e) {
			Util.debug("iwlist(): exception: " + e.getLocalizedMessage(), null);
		}
	}
	*/ 
	
	public static void startAdhoc(){

		/*
		if(getAccessPoints().length == 0){
			Util.log("startAdhoc(): no access points, disconnecting..", null);
			disconnect();
			wifiEnable();
			return;
		}
		*/
		
		changingWIFI = false;
		changeWIFI(AP);
	}

	private static boolean isSSID(final String line) {
		return line.contains("Strength") && line.contains("Freq");
	}

	private static void setSSID(final String line) {
		if(line.contains("[") && line.contains("]")){	
			if(wdev==null) wdev = line.substring(line.indexOf("wlan"), line.indexOf(" [")).trim();
			String router = line.substring(line.indexOf("[")+1, line.indexOf("]"));
			if( ! state.equals(values.ssid, router)) state.set(values.ssid, router);
		}
	}

	private static void readWAN(){
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
	}

	private static void readETH() {
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
	}

	private static void disconnecteddWAN(){
		if(state.exists(values.localaddress)) state.delete(values.localaddress);
		if(state.exists(values.signalspeed)) state.delete(values.signalspeed);
		if(state.exists(values.gateway)) state.delete(values.gateway);	
		if(state.exists(values.ssid)) state.delete(values.ssid);
	}

	private static void parseETH(){
		for(int i = 0 ; i < ethData.size() ; i++){

			String line = ethData.get(i).trim();

			if(line.contains("State: ")){
				if(line.contains("unavailable")){
					state.delete(values.ethernetaddress);
					return;
				}
			}
			
			if(line.contains("Default: ")){
				if(line.contains("yes")){
					wiredConnection = true;
				}
			}
			
			if(line.contains("Address: ") && !line.startsWith("HW")){
				String addr = line.substring(line.indexOf("Address: ")+9).trim();
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

	private static void parseWLAN(){
		for(int i = 0 ; i < wlanData.size() ; i++){

			String line = wlanData.get(i).trim();

			if(line.startsWith("*")) line = line.substring(1);
	
			if(isSSID(line) && ! accesspoints.contains(line)) accesspoints.add(line);

			if(line.contains("Speed: ")){
				String speed = line.substring(line.indexOf("Speed: ")+7).trim();
				if( ! state.exists(values.signalspeed)) state.set(values.signalspeed, speed);
				if(state.exists(values.signalspeed))
					if( ! state.equals(values.signalspeed, speed))
						state.set(values.signalspeed, speed);
			}

			if(wlanData.get(i).startsWith("Address: ")){
				String addr = line.substring(line.indexOf("Address: ")+9).trim();	
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
				if(Util.validIP(gate)){
					
					if( ! state.exists(values.gateway)) state.set(values.gateway, gate);
					
					if(state.exists(values.gateway)){
						if( ! state.get(values.gateway).equals(gate)){
							state.set(values.gateway, gate);
						}
					}
				}
			}
			
			if(line.startsWith("State: ")){
				String state = line.substring(line.indexOf("State: ")+7).trim();
				if(state.equals("disconnected")){
					Util.log("nm-tool.. disconnected, state: " + state, null);
					disconnecteddWAN();
				}
			}	
		}
	}
	
	public static String[] getAccessPoints(){
		
		Vector<String> aps = new Vector<String>();
		for(int j = 0 ; j < accesspoints.size() ; j++) {
			if(accesspoints.get(j).contains(":")){
				String ssid = accesspoints.get(j).substring(0, accesspoints.get(j).indexOf(":")).trim();
				if( ! aps.contains(ssid) && ! connectionExists(ssid))
					aps.add(ssid);
			}
		}

		int r = 0; 
		String[] result = new String[aps.size()];
		for(int j = 0 ; j < aps.size() ; j++)
			result[r++] = aps.get(j);

		return result;
	}
	

	
	public static String getPingTime() {
		return /*	" a: " + (System.currentTimeMillis()-apLast)/1000 + */" d: " + (System.currentTimeMillis()-defaultLast)/1000 ; 
		// TODO: fix later 
		// return pingValue;
	}
	
	public static long getLastPing() {
		return pingLast;
	}
	
	public String getWLAN() {
		return wdev;
	}
	
	public boolean wiredConnectionActive(){
		return wiredConnection;
	}
	

	
	/*
	public class TelnetTask extends TimerTask {
		
		Socket socket = new Socket();
		BufferedReader input = null;
		BufferedWriter output = null;
		
		
		public TelnetTask(){
			
			try {
				socket.connect(new InetSocketAddress("192.168.1.7", 4444));
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
			
			try {
				input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			try {
				output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			new Thread(new Runnable() {
				@Override
				public void run() {
					
					String str = null;
					try {
						while((str = input.readLine().trim()) != null){
							
							if(str.startsWith("<multiline>")){
							
								map.clear();
								for(;;){
								
									str = input.readLine().trim();
									
									if(str.startsWith("</multiline>")) break;
																	
									String key = str.split(" ")[0];
									String value = str.split(" ")[1];
									map.put(key, value);
									
								}
							}
							
							else System.out.println("update: " + str);
							
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
					
				}
			}).start();
			
		}
		
		@Override
		public void run() {
			try {
				
				output.flush();
				output.write("state");
				output.newLine(); 
				output.flush();
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}
	*/
	
}
