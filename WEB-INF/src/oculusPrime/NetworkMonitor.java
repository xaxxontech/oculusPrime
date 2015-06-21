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
	private static long apLast = System.currentTimeMillis();
	private static Settings settings = Settings.getReference();
	private static State state = State.getReference();	
	private static Application application = null; 
	private static boolean wiredConnection = false;	
	private static boolean changingWIFI = false;
	private static Timer pingTimer = new Timer();
	private static String currentUUID = null;
	private static String pingValue = null;
	private static String wdev = null;	
	private static int adhocBusy = 0;
	private static int uuidFail = 0;
    private static int pingFail = 0;
    
	private static NetworkMonitor singleton = new NetworkMonitor();
	public static void setApp(Application a) {application = a;}
	public static NetworkMonitor getReference() {return singleton;}
	
	private NetworkMonitor(){
		
		updateExternalIPAddress();
	
		if(settings.getBoolean(ManualSettings.networkmonitor)){
	
			connectionUpdate();
			runNetworkTool();
			changeUUID(settings.readSetting(ManualSettings.defaultuuid));	
			pingTimer.schedule(new pingTask(), 2000, 2000);
			new eventThread().start();	
			state.addObserver(this);
			
		}
	}
	
	@Override
	public void updated(String key) {
		
		if(key.equals(values.externaladdress.name())) 
			if( ! state.exists(values.ethernetaddress))
				updateExternalIPAddress();
	
		if(key.equals(values.ssid.name())) {
			
			pingFail = 0;
			changingWIFI = false;
			apLast = System.currentTimeMillis();
			defaultLast = System.currentTimeMillis();
			pingLast = System.currentTimeMillis(); 
			
			if( ! state.equals(values.ssid, AP) && ManualSettings.isDefault(ManualSettings.defaultuuid))
				setDefault(state.get(values.ssid));
			
			if(state.exists(values.ssid)) currentUUID = lookupUUID(state.get(values.ssid));

		}
	}
	
	private class pingTask extends TimerTask {
		@Override
	    public void run() {
	    		
	    	// TODO: revisit && ! state.getBoolean(State.values.autodocking)) 
	    	if(state.equals(values.ssid, AP) || changingWIFI) { 
	    		if(application != null && (state.getUpTime() > Util.ONE_MINUTE))
	    			application.driverCallServer(PlayerCommands.strobeflash, "on 10 10");
	    	}
	    	
	    	if(state.exists(values.gateway) && !changingWIFI){ 
    			pingValue = Util.pingWIFI(state.get(values.gateway));
    			if(pingValue == null) {
    				
    				pingFail++;
    				
    				Util.log("pingTask(): ping failed: "+ pingFail +" last: " + (System.currentTimeMillis() - pingLast), this);
    				if(pingFail > 100) {
    					Util.log("pingTask(): ping failed too much, try adhoc..", this);
    					disconnecteddWAN();
    					startAdhoc(); // TODO:.............................. 
    				}
    				
    			} else {
    				pingLast = System.currentTimeMillis(); 
    				if(pingFail-- < 0) pingFail = 0;
    			}
    			
    			if((System.currentTimeMillis() - apLast) > 100) pingFail = 0;
	    	}
	    	
	    	if((System.currentTimeMillis() - defaultLast) > Util.TWO_MINUTES) {
	    		Util.log("pingTask(): not connected to default, try to connect.. ", this);
    			changeUUID(settings.readSetting(ManualSettings.defaultuuid));	
    			
	    	}
    	
    		if((System.currentTimeMillis() - pingLast) > Util.FIVE_MINUTES) {
    			Util.log("pingTask(): Start AP mode after 5 minutes disconnected..", this);
    			startAdhoc();
    		}
    		
    		//if(state.exists(values.ssid)){
	    	// 	currentUUID = lookupUUID(state.get(values.ssid));
			if(currentUUID != null)
				if(currentUUID.equals(settings.readSetting(ManualSettings.defaultuuid))) 
					defaultLast = System.currentTimeMillis();
    	
    		if(state.equals(values.ssid, AP)) apLast = System.currentTimeMillis();
    		
    	}
	}
	
	private class eventThread extends Thread {
		@Override
		public void run() {
			try{			
				
				Process proc = Runtime.getRuntime().exec(new String[]{"iwevent"});
				BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));

				String line = null;
				while ((line = procReader.readLine()) != null){
					if(line.contains("completed") || line.contains("New")) {
						// Util.log("[" + ( System.currentTimeMillis() - scanLast ) + "] " + line, this);
						// scanLast = System.currentTimeMillis();
						runNetworkTool();
					}
				}
			} catch (Exception e) {
				Util.debug("eventThread: " + e.getLocalizedMessage(), this);
			}
		}
	}
	
	private void runNetworkTool(){
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
			
			Util.debug("networkTask: lines copied: " + networkData.size(), this);

			proc.waitFor();
			readETH();
			readWAN();
			parseWLAN();
			getAccessPoints();
			parseETH();
			
		} catch (Exception ex){
			Util.log("networkTask: nm-tool failure: " + ex.getLocalizedMessage(), this);
		}
	}
	
	public boolean connectionExists(final String ssid){
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
	
	public String[] getConnections() {
		
		String[] con = new String[connections.size()]; 
		for(int i = 0 ; i < con.length ; i++)
			con[i] = NetworkMonitor.getConnectionName(connections.get(i));
		
		return con;
	}
	
	public void setDefault(final String router) {
		
		if(router.equals(AP)) { // TODO: COLIN
			Util.log("changeWIFI(): user needs to be warned of this something.. ", this);
		}
		
		
		for(int i = 0 ; i < connections.size() ; i++) {
			if(connections.get(i).startsWith(router)) {
				String uuid = getConnectionUUID(connections.get(i));
				settings.writeSettings(ManualSettings.defaultuuid, uuid);
			}	
		}
	}
	
	public String lookupUUID(final String ssid){
		for(int i = 0 ; i < connections.size() ; i++) {
			if(connections.get(i).startsWith(ssid)) 
				return getConnectionUUID(connections.get(i));
		}
		
		return null;
	}
	
	private String lookupSSID(final String uuid){
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
			while ((line = procReader.readLine()) != null){                 //TODO: revisit 
				if( ! line.startsWith("NAME") && line.contains("wireless") && !line.contains("never"))
						if( ! connections.contains(line))
							connections.add(line); 
			}
		} catch (Exception e) {
			Util.debug("connectionUpdate(): " + e.getLocalizedMessage(), this);
		}
	}
	
	public void changeWIFI(final String ssid, final String password){
	
		if(ssid == null || password == null || changingWIFI) return; 
		
		new Thread(){
		    public void run() {
		    	try {
		
		    		changingWIFI = true;
		    		disconnecteddWAN();
					String cmd[] = new String[]{"nmcli", "dev", "wifi", "connect", ssid, "password", password};
					Process proc = Runtime.getRuntime().exec(cmd);
					proc.waitFor();
					
					if(proc.exitValue()==4){ // TODO: COLIN tell user 
						Util.log("changeWIFI(password): [" + ssid + "] exit code: " + proc.exitValue(), this);					
						Util.log("changeWIFI(password): failed login, bad password so trying default.. ", this);
		    			changeUUID(settings.readSetting(ManualSettings.defaultuuid));	
					}
					
					if(proc.exitValue()==0) {
						if(ManualSettings.isDefault(ManualSettings.defaultuuid)) {
							Util.log("changeWIFI(password): setting as default ["+ssid+"]", this);	
							connectionUpdate();	
							setDefault(ssid);	
						}
					}
		
					changingWIFI = false;		
					
		    	} catch (Exception e) {
					Util.log("changeWIFI(): [" + ssid + "] ", e, this); 
				}
		    }
		}.start();
	}

	public void changeUUID(final String uuid){
		
		if(uuid == null) return;
		
		if(changingWIFI){
			Util.debug("changeUUID: busy, rejected", this);
			return;
		}
		
		if(ManualSettings.isDefault(ManualSettings.defaultuuid)) {
			Util.log("changeUUID: no default uuid in settings, try adhoc..", this);
			uuidFail++;
			startAdhoc();
			return;
		}
		
		if(uuidFail > 10){
			Util.log("changeUUID: default router failed 10 times, trying adhoc..", this);
			settings.writeSettings(ManualSettings.defaultuuid, ManualSettings.getDefault(ManualSettings.defaultuuid));
			uuidFail = 0;
			startAdhoc();
			return;
		}
		
		if(lookupSSID(uuid) == null){
			Util.log("changeUUID: null connection uuid for ["+lookupSSID(uuid)+"], rejected", this);
			startAdhoc();
			uuidFail++;
			return;
		}
	
		new Thread(){
		    public void run() {
		    	try {
		    		
		    		changingWIFI = true;
		    		disconnecteddWAN();
					Process proc = Runtime.getRuntime().exec(new String[]{"nmcli", "c", "up", "uuid", uuid});
					proc.waitFor();
					
					Util.log("changeUUID: [" + lookupSSID(uuid) + "] exit code: " + proc.exitValue(), this);
					
					if(proc.exitValue()==3) {	
						uuidFail++;
						Util.log( uuidFail + " changeUUID: timeout, try adhoc: " + proc.exitValue(), this);
						startAdhoc();
						return;
					}
					
					if(proc.exitValue()==0) runNetworkTool();
					changingWIFI = false;	
					
		    	} catch (Exception e) {
					Util.log("changeUUID(): [" + uuid + "]: ", e, this); 
				}
		    }
		}.start();
	}

	public void changeWIFI(final String ssid){
		
		if(ssid == null || changingWIFI) return; 
		
		if(state.equals(values.ssid, ssid)) {
			
			Util.debug("changeWIFI(): already is the ssid: " + ssid, this);
			
			if(ssid.equals(AP) && ManualSettings.isDefault(ManualSettings.defaultuuid)){
				Util.log("changeWIFI(): ..... all hope is lost, user needs to do something..... ", this);
			}// TODO: COLIN
			
			return;
		}
		
		new Thread(){
		    public void run() {
		    	try {
	
		    		changingWIFI = true;	
		    		disconnecteddWAN();
					Process proc = Runtime.getRuntime().exec(new String[]{"nmcli", "c", "up", "id", ssid}); // "\""+ssid+"\"" }); 
					proc.waitFor();
					Util.log("changeWIFI(): [" + ssid + "] exit code: " +  proc.exitValue(), this);
					if(proc.exitValue()==0) {
						if(ManualSettings.isDefault(ManualSettings.defaultuuid))
							if( ! state.equals(values.ssid, AP)) 
								setDefault(ssid);
					}
					
					runNetworkTool();
					changingWIFI = false;	
					
		    	} catch (Exception e) {
					Util.log("changeWIFI(): [" + ssid + "] exception: ", e, this); 
				}
		    }
		}.start();
	}
	
	private void killApplet(){
		try {
			Runtime.getRuntime().exec(new String[]{"pkill", "nmcli"});
			Runtime.getRuntime().exec(new String[]{"pkill", "nm-applet"});
		} catch (Exception e) {
			Util.debug("killApplet(): " + e.getLocalizedMessage(), this);
		}
	}
	
	public void startAdhoc(){

		if(changingWIFI){
			if( adhocBusy++ > 10) {
				
				Util.log("startAdhoc(): break through, reset busy flag.", this);
				changingWIFI = false;
				adhocBusy = 0;
				
			} else {
				Util.log("startAdhoc(): busy, rejected: " + adhocBusy, this);
				return;
			}
		}
		
		if(getAccessPoints() == null){
			Util.log("startAdhoc(): no access points, can't be an an access point.", this);
			return;
		}
		
		changingWIFI = false;
		changeWIFI(AP);
	}

	private boolean isSSID(final String line) {
		return line.contains("Strength") && line.contains("Freq");
	}

	private void setSSID(final String line) {
		if(line.contains("[") && line.contains("]")){	
			if(wdev==null) wdev = line.substring(line.indexOf("wlan"), line.indexOf(" [")).trim();
			String router = line.substring(line.indexOf("[")+1, line.indexOf("]"));
			if( ! state.equals(values.ssid, router)) state.set(values.ssid, router);
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
	}

	private void disconnecteddWAN(){
		killApplet();
		state.delete(values.ssid);
		state.delete(values.localaddress);
		state.delete(values.signalspeed);
		state.delete(values.gateway);
	}

	private void parseETH(){
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

	private void parseWLAN(){
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
					Util.log("nm-tool.. disconnected, state: " + state, this);
					disconnecteddWAN();
				}
			}	
		}
	}
	
	public String[] getAccessPoints(){
		
		Vector<String> aps = new Vector<String>();
		for(int j = 0 ; j < accesspoints.size() ; j++) {
			if(accesspoints.get(j).contains(":")){
				String ssid = accesspoints.get(j).substring(0, accesspoints.get(j).indexOf(":")).trim();
				if( ! aps.contains(ssid) && ! connectionExists(ssid)) //&& ! ignore.contains(ssid)) 
					aps.add(ssid);
			}
		}

		int r = 0; 
		String[] result = new String[aps.size()];
		for(int j = 0 ; j < aps.size() ; j++)
			result[r++] = aps.get(j);

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
				else state.delete(values.externaladdress);
				
			} catch (Exception e) {
				Util.log("updateExternalIPAddress():"+ e.getMessage(), this);
				state.delete(values.externaladdress);
			}
		} }).start();
	}
	
	public String getPingTime() {
		return 	" a: " + (System.currentTimeMillis()-apLast)/1000 + " d: " + (System.currentTimeMillis()-defaultLast)/1000 ; 
		// TODO: fix later 
		//pingValue;
	}
	
	public long getLastPing() {
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
