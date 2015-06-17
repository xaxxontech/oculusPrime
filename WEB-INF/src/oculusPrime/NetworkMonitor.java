package oculusPrime;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import oculusPrime.State.values;

public class NetworkMonitor {

	public static final long AP_TIME_OUT = 9000;
	public static final long AP_PING_FAILS = 7;
	public static final String AP = "ap";

	private static Vector<String> wlanData = new Vector<String>();
	private static Vector<String> ethData = new Vector<String>();
	private static Vector<String> accesspoints = new Vector<String>();
	private static Vector<String> networkData = new Vector<String>();
	private static Vector<String> connections = new Vector<String>();
	private static Vector<String> ignore = new Vector<String>();
	private static long pingLast = System.currentTimeMillis();
	private static Settings settings = Settings.getReference();
	private static State state = State.getReference();	
	private static Application application = null; 
	private static boolean changingWIFI = false;	
	private static boolean wiredConnection = false;
	private static Timer pingTimer = new Timer();
	private static String pingValue = null;
	private static String wdev = null;	
	private static long scanLast = 0;
	private static long uuidConnetionLast = 0;
	private static int uuidConnetionAttempts = 0;
	private static int failedConnetion = 0;
	private static int apModeCounter = 0;
	private static int pingCounter = 0;
	private static int pingFails = 0;
	
	private static NetworkMonitor singleton = new NetworkMonitor();
	public static void setApp(Application a) {application = a;}
	public static NetworkMonitor getReference() {return singleton;}
	
	private NetworkMonitor(){
		
		updateExternalIPAddress();
		
		if(state.equals(values.ssid, AP) && ! settings.readSetting(ManualSettings.defaultuuid).equals("none")) 
			changeUUID(settings.readSetting(ManualSettings.defaultuuid));	

		if(settings.getBoolean(ManualSettings.networkmonitor)){
			pingTimer.schedule(new pingTask(), 1000, 1000);
			new eventThread().start();
			connectionUpdate();
			runNetworkTool();
			killApplet();
		}
	}

	private void panicPings(){
		
		pingLast = System.currentTimeMillis();
		
		// ping and see if recovers, let Linux try other connections.. 
		for(int i = 0 ; i < AP_PING_FAILS ; i++){
			
			pingValue = Util.pingWIFI(state.get(values.gateway));
			if(pingValue == null) pingFails++;
			else pingLast = System.currentTimeMillis();	
			
			if(i%3==0 && i > 3) {
				Util.delay(1500);
				// runNetworkTool();
				changeUUID(settings.readSetting(ManualSettings.defaultuuid));
			}
			
			if(state.exists(values.ssid)) {
				// pingLast = System.currentTimeMillis();
				Util.log("painic pings resolved.. ", this);
				return;
			}
			
			Util.delay(2000);
		}
		
		if(pingFails >= AP_PING_FAILS){
			pingLast = System.currentTimeMillis();
			pingCounter = 0;
			pingFails = 0;
			startAdhoc();
		}
	}
	
	private class pingTask extends TimerTask {			
	    @Override
	    public void run() {
	    	
	    	if(state.equals(values.ssid, AP)) {
	    		
	    		if (application != null && ! state.getBoolean(State.values.autodocking)) 
	    			application.driverCallServer(PlayerCommands.strobeflash, "on 10 10");
	    		
	    		if ((System.currentTimeMillis() - pingLast) > Util.FIVE_MINUTES) {
	    			Util.log("NetworkMonitor().pingTask() in AP mode for five minutes: " + apModeCounter, null);
	    			pingLast = System.currentTimeMillis();
	    			tryAnyConnection();
	    		} 
	    		
	    		return; 
	    	}
	    	    			
    		if((System.currentTimeMillis() - pingLast) > AP_TIME_OUT) {
    		
    			// try default 
    			changeUUID(settings.readSetting(ManualSettings.defaultuuid));
    			Util.delay(4000);
    			
    			// trouble
    			if((System.currentTimeMillis() - pingLast) > AP_TIME_OUT) panicPings();
    			
    			return;
    		}
    			
    		if(state.exists(values.gateway) && !changingWIFI){ 
    			pingCounter++; // how long connected measure 
    			if(pingCounter% 100 == 0) Util.debug("NetworkMonitor.pingTask(): ping count = " + pingCounter, null);
    			pingValue = Util.pingWIFI(state.get(values.gateway));
    			if(pingValue != null) pingLast = System.currentTimeMillis();
    			
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
					
					if(line.contains("completed")) scanLast = System.currentTimeMillis();
					
					if(line.contains("New")) {
						
						Util.log(line, this);
						
						if( System.currentTimeMillis() - scanLast > Util.TWO_MINUTES) {
							Util.log(" last scan was: " + ( System.currentTimeMillis() - scanLast ), this);
							runNetworkTool();
						}
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
			
			// Util.debug("networkTask: lines copied: " + networkData.size(), this);

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
	
	private void tryAnyConnection() {	
		String[] routers = getConnections();
		if(routers == null) return; 
		
		for(int i = 0 ; i < routers.length ; i++) Util.log(i + " tryAnyConnection: " + routers[i], this);
		
		String ssid = routers[ new Random().nextInt(routers.length) ];
		
		// try not to choose ap mode again
		if(ssid.equals(AP)) ssid = routers[ new Random().nextInt(routers.length) ];
		if(ssid.equals(AP)) ssid = routers[ new Random().nextInt(routers.length) ];
		if(ssid.equals(AP)) ssid = routers[ new Random().nextInt(routers.length) ];
		
		Util.log("tryAnyConnection: calling changeWIFI(" + ssid + ")", this);
		changeWIFI(ssid);
	}

	public boolean connectionExists(final String ssid){
		for(int i = 0 ; i < (connections.size()) ; i++)
			if(connections.get(i).startsWith(ssid.trim()))
				return true;

		return false;
	}
	
	public void removeConnection(final String ssid){
		
		Util.log("removeConnection(): called with: "+ssid, this);
		
		if(changingWIFI){
			Util.log("removeConnection(): busy, rejected", this);
			return;
		}
		
		if(ssid.equals(AP)) {
			Util.log("removeConnection(): can't remove AP", this);
			return;
		}
		
		if( ! ignore.contains(ssid)) ignore.add(ssid);
		
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
			while ((line = procReader.readLine()) != null){  //TODO: revisit 
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
					
					proc.waitFor();						
					Util.log("changeWIFI(password): exit code: " + proc.exitValue(), this);
					Util.log("changeWIFI(password): [" + ssid + "] time: " + (System.currentTimeMillis() - start)/1000 +  " seconds", this);
					
					Util.delay(400);
					runNetworkTool();
					connectionUpdate();
					changingWIFI = false;
					
		    	} catch (Exception e) {
					Util.log("changeWIFI(): [" + ssid + "] ", e, this); 
				}
		    }
		}.start();
	}

	public void changeUUID(final String uuid){
		
		if(uuid == null) return; 
		
		// if((System.currentTimeMillis() - pingLast) <= AP_TIME_OUT) return;
		
		if((System.currentTimeMillis() - uuidConnetionLast) <= 4000) {
			Util.log("changeUUID: busy, rejected: " + (System.currentTimeMillis() - uuidConnetionLast) + " ms too soon", this);
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
		    		uuidConnetionAttempts++;
		    		Util.log("[" +uuidConnetionAttempts + "] changeUUID: " + (System.currentTimeMillis() - uuidConnetionLast) + " ms", this);
		    		uuidConnetionLast = System.currentTimeMillis();
		    		
					long start = System.currentTimeMillis();
					String cmd[] = new String[]{"nmcli", "c", "up", "uuid", uuid};
					Process proc = Runtime.getRuntime().exec(cmd);
					
					BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));

					String line = null;
					while ((line = procReader.readLine()) != null)
						Util.log("changeUUID: " + uuid + " " + line, this);
					
					proc.waitFor();
					Util.log("changeUUID:" + uuid + " " + (System.currentTimeMillis() - start)/1000 +  " seconds", this); 
					
					proc.waitFor();						
					Util.log("changeUUID: exit code: " + proc.exitValue() + " attempts: " + uuidConnetionAttempts, this);
					Util.log("changeUUID: [" + uuid + "] time: " + (System.currentTimeMillis() - start)/1000 +  " seconds", this);
					
					if( proc.exitValue() == 0){
						
						Util.delay(3000);
							
						runNetworkTool();
						pingLast = System.currentTimeMillis();
						Util.log("changeUUID: connection up.. ", this);
						//changingWIFI = false;	
						return;
						
					}
					
					//Util.delay(3000);
					//runNetworkTool();
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
		
		if(state.exists(values.ssid)){
			if(state.get(values.ssid).equals(ssid)) {
				Util.log("changeWIFI(): already is the ssid", this);
				return;
			}
		}
		
		changingWIFI = true;
		disconnecteddWAN();
		killApplet();
		
		Util.log("changeWIFI(): switch to ssid = " + ssid, this);
		
		new Thread(){
		    public void run() {
		    	try {

		    		long start = System.currentTimeMillis();                        // TODO: spaces in ssid? 
					Process proc = Runtime.getRuntime().exec(new String[]{"nmcli", "c", "up", "id", ssid}); // "\""+ssid+"\"" }); 
					
					BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));

					String line = null;
					while ((line = procReader.readLine()) != null)
						Util.log("changeWIFI(): stdout: " + ssid + " " + line, this);
					
					procReader = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
					while ((line = procReader.readLine()) != null){
						Util.log("changeWIFI(): error: " + ssid + " " + line, this);
						
						if(line.toLowerCase().contains("timeout")){
							if(failedConnetion++ >= 3){
								Util.log("changeWIFI(): timeout counter = " + failedConnetion, this);
								pingLast = System.currentTimeMillis();
								startAdhoc();
							}
						}
					}
					
					proc.waitFor();
					Util.log("changeWIFI(): exit code: " + proc.exitValue(), this);
					Util.log("changeWIFI(): [" + ssid + "] time: " + (System.currentTimeMillis() - start)/1000 +  " seconds", this);
					
					Util.delay(400);
					runNetworkTool();
					connectionUpdate();
					changingWIFI = false;
					
				} catch (Exception e) {
					Util.log("changeWIFI(): [" + ssid + "] exception: ", e, this); 
				}
		    }
		}.start();
	}

	public void startAdhoc(){
		
		if(changingWIFI){
			Util.log("changeWIFI(): busy, rejected", this);
			return;
		}
		
		if(getAccessPoints() == null){
			Util.log("... no access points, can't be an ap.", this);
			return;
		}
		
		changeWIFI(AP);
	}

	private boolean isSSID(final String line) {
		return line.contains("Strength") && line.contains("Freq");
	}

	private void setSSID(final String line) {
		if(line.contains("[") && line.contains("]")){
			
			if(wdev==null){
				wdev = line.substring(line.indexOf("wlan"), line.indexOf(" [")).trim();
				Util.log("wedv: " +wdev, this);
			}
			
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
		
		// externalLookup = 0;
		
		apModeCounter = 0;
		pingCounter = 0;
		pingValue = "0"; 
		pingFails = 0;
		
		// wdev = null;
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
			
			if(isSSID(line) && ! accesspoints.contains(line)) accesspoints.add(line);

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
			
			if(line.startsWith("State: ")){
				
				String state = line.substring(line.indexOf("State: ")+7).trim();
				if(state.equals("disconnected")){
				
					Util.log("parseWLAN: disconnected ... State: " + state, this);
					disconnecteddWAN();
					
				}
			}	
		}
		
		if(!star) {
			Util.debug("nm-tool.. disconnected? no star", this);
			disconnecteddWAN();
		}
		
	}
	
	public String[] getAccessPoints(){
		Vector<String> aps = new Vector<String>();
		for(int j = 0 ; j < accesspoints.size() ; j++) {
			if(accesspoints.get(j).contains(":")){
				String ssid = accesspoints.get(j).substring(0, accesspoints.get(j).indexOf(":")).trim();
				if( ! aps.contains(ssid) && ! connectionExists(ssid)) aps.add(ssid);
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

	public String getPingTime() {
		return pingValue;
	}

	public void setDefault(final String router) {
		
		Util.debug("target connction id: "+router, this);
		
		for(int i = 0 ; i < connections.size() ; i++) {
			if(connections.get(i).startsWith(router)) {
				String uuid = getConnectionUUID(connections.get(i));
				if( ! settings.readSetting(ManualSettings.defaultuuid).equals(uuid)){
					settings.writeSettings(ManualSettings.defaultuuid, uuid);
					Util.log("writesetting: " + router + " =uuid= " + uuid, this);
				}
			}	
		}
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
