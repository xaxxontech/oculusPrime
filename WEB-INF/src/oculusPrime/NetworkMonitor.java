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
	
	// Linux wifi connect time out is 90 seconds 
	public static final long AP_TIME_OUT = 30 * 1000; 
	public static final String AP = "ap";

	private static Vector<String> wlanData = new Vector<String>();
	private static Vector<String> ethData = new Vector<String>();
	private static Vector<String> accesspoints = new Vector<String>();
	private static Vector<String> networkData = new Vector<String>();
	private static Vector<String> connections = new Vector<String>();
	private static Vector<String> ignore = new Vector<String>();
	private static Settings settings = Settings.getReference();
	private static State state = State.getReference();	
	private static Application application = null; 
	private static boolean wiredConnection = false;	
	private static boolean changingWIFI = false;
	private static long pingLast = System.currentTimeMillis();
	private static long apLast = System.currentTimeMillis();
	private static long scanLast = System.currentTimeMillis();
	private static int adhocBusy = 0;
//	private static Timer apTimer = new Timer();
	private static Timer pingTimer = new Timer();
	private static String pingValue = null;
	private static String wdev = null;	
	
	private static NetworkMonitor singleton = new NetworkMonitor();
	public static void setApp(Application a) {application = a;}
	public static NetworkMonitor getReference() {return singleton;}
	
	private NetworkMonitor(){
		
		updateExternalIPAddress();
	
		if(settings.getBoolean(ManualSettings.networkmonitor)){
			changeUUID(settings.readSetting(ManualSettings.defaultuuid));	
			pingTimer.schedule(new pingTask(), 5000, 2000);
			new eventThread().start();	
			state.addObserver(this);
			connectionUpdate();
			runNetworkTool();
			killApplet();
		}
	}
	
	@Override
	public void updated(String key) {
		
		if(key.equals(values.externaladdress.name())) 
			if( ! state.exists(values.ethernetaddress))
				updateExternalIPAddress();
	
		if(key.equals(values.ssid.name())) {
			if(state.exists(values.ssid)) {
				
				Util.log(".........updated: " + state.get(values.ssid), this);
				
				if(state.equals(values.ssid, AP)) {
					
					Util.log("--- ap mode starting ---", this);
					apLast = System.currentTimeMillis();
					adhocBusy = 0;
					
				}
			} else {
				Util.log("............. ssid was deleted", this);
			}
		}
	}
	
	private class pingTask extends TimerTask {			
	    @Override
	    public void run() {
	    
	    	if(state.equals(values.ssid, AP) || changingWIFI) {
	    		
	    		// TODO: revisit && ! state.getBoolean(State.values.autodocking)) 
	    		if(application != null) application.driverCallServer(PlayerCommands.strobeflash, "on 10 10");
	    		
	    		if((System.currentTimeMillis() - apLast) > Util.FIVE_MINUTES) {
	    			Util.log("apTask(): AP mode to long, try default", this);
	    			changeUUID(settings.readSetting(ManualSettings.defaultuuid));	
	    		}
	    		
	    	}
	    	
	    	if(state.exists(values.gateway)) { // && !changingWIFI){ 
    			pingValue = Util.pingWIFI(state.get(values.gateway));
    			if(pingValue == null) return;
    			else pingLast = System.currentTimeMillis(); 
	    	}
	    	
    		if((System.currentTimeMillis() - pingLast) > AP_TIME_OUT) {
    			Util.log("pingTask(): Start AP mode, last ping = " + (System.currentTimeMillis() - pingLast), this);
    			startAdhoc();
    		}
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
					
					Util.log("event: " + line, this);
					
					if(line.contains("completed")) scanLast = System.currentTimeMillis();
					
					///if(line.contains("New")) { //TODO: CPU SAVING? 
						// if( System.currentTimeMillis() - scanLast > Util.TWO_MINUTES) {
							
						//	runNetworkTool();
						// }
					// }
					Util.log(" last scan was: " + ( System.currentTimeMillis() - scanLast ), this);
					runNetworkTool();
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
	
	public void removeConnection(final String ssid){
		
		Util.log("removeConnection(): called with: "+ssid, this);
		
		if(ssid.equals(AP)) {
			Util.log("removeConnection(): can't remove AP", this);
			return;
		}
		
		if( ! ignore.contains(ssid)) ignore.add(ssid);
		
		settings.writeSettings(ManualSettings.ignoreuuids, getConnectionUUID(ssid));
		
		connectionUpdate();
	}
	
	private void killApplet(){
		try {
			Runtime.getRuntime().exec(new String[]{"pkill", "nmcli"});
			Runtime.getRuntime().exec(new String[]{"pkill", "nm-applet"});
		} catch (Exception e) {
			Util.debug("killApplet(): " + e.getLocalizedMessage(), this);
		}
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
		
		int size = connections.size();
		
		if(ignore.size() > 0){
			for(int c = 0 ; c < connections.size() ; c++)
				if(ignore.contains(NetworkMonitor.getConnectionName(connections.get(c)))) 
					size--;
		}
		
		String[] con = new String[size]; 
		for(int i = 0 ; i < con.length ; i++){
			 if( ! ignore.contains(NetworkMonitor.getConnectionName(connections.get(i))))
				con[i] = NetworkMonitor.getConnectionName(connections.get(i));
		}
	
		return con;
	}
	
	private void connectionUpdate(){
		try {
			connections.clear();
			Process proc = Runtime.getRuntime().exec(new String[]{"nmcli", "con"});
			BufferedReader procReader = new BufferedReader(
					new InputStreamReader(proc.getInputStream()));

			String line = null;
			while ((line = procReader.readLine()) != null){                 //TODO: revisit 
				if( ! line.startsWith("NAME") && line.contains("wireless")) // && !line.contains("never"))
					if( ! ignore.contains(NetworkMonitor.getConnectionName(line)))
						if( ! connections.contains(line))
							connections.add(line); 
			}
		} catch (Exception e) {
			Util.debug("connectionUpdate(): " + e.getLocalizedMessage(), this);
		}
	}
	
	public void changeWIFI(final String ssid, final String password){
	
		if(ssid == null) return; 
		
		if(changingWIFI){
			Util.log("changeWIFI(password): busy, rejected", this);
			return;
		}
		
		if(state.exists(values.ssid)){
			if(state.get(values.ssid).equals(ssid)) {
				Util.log("changeWIFI(password): already is the ssid", this);
				return;
			}
		}
		
		new Thread(){
		    public void run() {
		    	try {
		
		    		changingWIFI = true;
		    		disconnecteddWAN();
		    		killApplet();
					
		    		long start = System.currentTimeMillis();
					String cmd[] = new String[]{"nmcli", "dev", "wifi", "connect", ssid, "password", password};
					Process proc = Runtime.getRuntime().exec(cmd);
					
					BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));

					String line = null;
					while ((line = procReader.readLine()) != null)
						Util.log("changeWIFI(password): stdout: " + ssid + " " + line, this);
					
					proc.waitFor();
					Util.log("changeWIFI(password):" + ssid + " " + (System.currentTimeMillis() - start)/1000 +  " seconds", this); 
					Util.log("changeWIFI(): [" + ssid + "] time: " + (System.currentTimeMillis() - start)/1000 +  " seconds", this);
					
					if(proc.exitValue()==0) pingLast = System.currentTimeMillis(); 
					Util.delay(1000);
					connectionUpdate(); // new connection made 
					runNetworkTool();
					changingWIFI = false;		
					
		    	} catch (Exception e) {
					Util.log("changeWIFI(): [" + ssid + "] ", e, this); 
				}
		    }
		}.start();
	}

	public void changeUUID(final String uuid){
		
		if(uuid == null) return;
		
		// TODO: should test against settings default 
		if(ManualSettings.isDefault(ManualSettings.defaultuuid)) {
			Util.log("changeUUID: no default uuid in settings, rejected", this);
			return;
		}
		
		if(changingWIFI){
			Util.log("changeUUID: busy, rejected", this);
			return;
		}
		
		new Thread(){
		    public void run() {
		    	try {
		    		
		    		changingWIFI = true;
		    		disconnecteddWAN();
		    		killApplet();
		    		
					long start = System.currentTimeMillis();
					String cmd[] = new String[]{"nmcli", "c", "up", "uuid", uuid};
					Process proc = Runtime.getRuntime().exec(cmd);
					
					BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));

					String line = null;
					while ((line = procReader.readLine()) != null)
						Util.log("changeUUID: " + uuid + " " + line, this);
					
					proc.waitFor();
					Util.log("changeUUID:" + uuid + " " + (System.currentTimeMillis() - start)/1000 +  " seconds", this); 
					Util.log("changeUUID: [" + uuid + "] time: " + (System.currentTimeMillis() - start)/1000 +  " seconds", this);
					
					if(proc.exitValue()==0) pingLast = System.currentTimeMillis(); 
					Util.delay(1000);
					connectionUpdate();
					runNetworkTool();
					changingWIFI = false;	
					
		    	} catch (Exception e) {
					Util.log("changeUUID(): [" + uuid + "] ", e, this); 
				}
		    }
		}.start();
	}

	public void changeWIFI(final String ssid){
		
		if(ssid == null) return; 
		
		if(changingWIFI){
			Util.log("changeWIFI(): busy, rejected", this);
			return;
		}
		
		if(state.equals(values.ssid, ssid)) {
			Util.log("changeWIFI(): already is the ssid", this);
			return;
		}
		
		new Thread(){
		    public void run() {
		    	try {
	
		    		changingWIFI = true;	
		    		disconnecteddWAN();

		    		long start = System.currentTimeMillis();                                                // TODO: spaces in ssid? 
					Process proc = Runtime.getRuntime().exec(new String[]{"nmcli", "c", "up", "id", ssid}); // "\""+ssid+"\"" }); 
					
					BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));

					String line = null;
					while ((line = procReader.readLine()) != null)
						Util.log("changeWIFI(): stdout: " + ssid + " " + line, this);
					
					procReader = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
					
					/*
					while ((line = procReader.readLine()) != null){
						Util.log("changeWIFI(): timeout, trying default", this);
						if(line.toLowerCase().contains("timeout")) {
							
							changingWIFI = false;
							changeUUID(settings.readSetting(ManualSettings.defaultuuid));
							return;
							
						}
					}
					*/
					
					proc.waitFor();
					Util.log("changeWIFI(): exit code: " + proc.exitValue(), this);
					Util.log("changeWIFI(): [" + ssid + "] time: " + (System.currentTimeMillis() - start)/1000 +  " seconds", this);
					
					if(proc.exitValue()==0) pingLast = System.currentTimeMillis(); 
					Util.delay(1000);
					connectionUpdate();
					runNetworkTool();
					changingWIFI = false;	
					
		    	} catch (Exception e) {
					Util.log("changeWIFI(): [" + ssid + "] exception: ", e, this); 
				}
		    }
		}.start();
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
		state.delete(values.ssid);
		// state.delete(values.externaladdress);
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

		// boolean star = false;

		for(int i = 0 ; i < wlanData.size() ; i++){

			String line = wlanData.get(i).trim();

			if(line.startsWith("*")) {
				line = line.substring(1);
		//		star = true;
			}
			
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
		
		/*
		if(!star) {
			Util.log("nm-tool.. disconnected, no star", this);
			disconnecteddWAN();
		}
		*/	
	}
	
	public String[] getAccessPoints(){
		
		Vector<String> aps = new Vector<String>();
		for(int j = 0 ; j < accesspoints.size() ; j++) {
			if(accesspoints.get(j).contains(":")){
				String ssid = accesspoints.get(j).substring(0, accesspoints.get(j).indexOf(":")).trim();
				if( ! aps.contains(ssid) && ! connectionExists(ssid) && ! ignore.contains(ssid)) aps.add(ssid);
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
				else state.delete(values.externaladdress);
				
			} catch (Exception e) {
				Util.log("updateExternalIPAddress():"+ e.getMessage(), this);
				state.delete(values.externaladdress);
			}
		} }).start();
	}

	public void setDefault(final String router) {
		for(int i = 0 ; i < connections.size() ; i++) {
			if(connections.get(i).startsWith(router)) {
				String uuid = getConnectionUUID(connections.get(i));
				settings.writeSettings(ManualSettings.defaultuuid, uuid);
				// Util.log("writesetting: " + router + " == " + uuid, this);
			}	
		}
	}
	
	public String getPingTime() {
		return pingValue;
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
