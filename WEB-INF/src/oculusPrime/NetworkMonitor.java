package oculusPrime;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Vector;

import oculusPrime.State.values;

public class NetworkMonitor {

	/// protected static final long FAST_POLL_DELAY_MS = 500;
	protected static final long WAN_POLL_DELAY_MS = Util.ONE_DAY;
	protected static final long WIFI_POLL_DELAY_MS = 17000;
	protected static final long WIFI_CONNECT_TIMEOUT_MS = 60000;
	protected static final long HTTP_REFRESH_DELAY_SECONDS = 5;
	protected static final String WLAN = "wlan0";
	// protected static final String ETH = "eth0";

	private Vector<String> wlanData = new Vector<String>();
	private static Vector<String> accesspoints = new Vector<String>();
	public static State state = State.getReference();
	private static NetworkMonitor singleton = null;

	public static NetworkMonitor getReference() {
		if(singleton == null) singleton = new NetworkMonitor();
		return singleton;
	}

	private NetworkMonitor(){
		// getSSID();
		startWLAN();
		getSignalQuality();
		pollExternalAddress();
	}
	
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
						Thread.sleep(WIFI_POLL_DELAY_MS);
						
					} catch (Exception e) {
						Util.log("iw error: ", e, this);
					}										
				}
			}
		}).start();
	}
	
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
								state.set(values.singlalquality, signalQuality);
								state.set(values.signalnoise, signalNoise);
							}
						}
						
						proc.waitFor();
						Thread.sleep(WIFI_POLL_DELAY_MS); 
						
					} catch (Exception e) {
						Util.log("getSignalQuality()" + e, this);
					}										
				}
			}
		}).start();
	}
	
	public void pollExternalAddress(){
		new Thread(new Runnable() {
			@Override
			public void run() {		
				while(true){
					updateExternalIPAddress();	
					Util.delay(WAN_POLL_DELAY_MS);									
				}
			}
		}).start();
	}
	
	public static boolean isSSID(final String line){
		return line.contains("Strength");
	}
	
	public static boolean isCurrentSSID(String line){
		if( ! isSSID(line)) return false;
		return line.trim().startsWith("*");
	}
	
	private void updateWLAN(){
		try {
								
			Process proc = Runtime.getRuntime().exec(new String[]{"nm-tool"});
			BufferedReader procReader = new BufferedReader(
				new InputStreamReader(proc.getInputStream()));
						
			wlanData.clear();
			String line = null;
			while ((line = procReader.readLine()) != null){
				if(line.startsWith("- Device: " + WLAN)){	
					
					state.set(values.ssid, line.substring((line.indexOf('[')+1), line.indexOf(']')));
				
					while(true){
						line = procReader.readLine();
						if(line == null) break;
						if(line.startsWith("- Device:")) break;
						
						line = line.trim();
						if(line.length()>0) 
							if( ! wlanData.contains(line))
								wlanData.add(line);
					}
				}
			}
			
			proc.waitFor();
			
		} catch (Exception e) {
			System.out.println("getWIFI: " + e.getLocalizedMessage());
		}		
	}
	
	private void parseWLAN(){
		for(int i = 0 ; i < wlanData.size() ; i++){
			
			if(isCurrentSSID(wlanData.get(i))) wlanData.remove(i);			
			
			if(wlanData.get(i).contains("Speed: "))
				state.set(values.signalSpeed, wlanData.get(i).substring(wlanData.indexOf("Speed: ")+7).trim());
							
			if(isSSID(wlanData.get(i))) 
				if( ! accesspoints.contains(wlanData.get(i))) //TODO:.substring(0, 5)))??
					accesspoints.add(wlanData.get(i));
			
			if(wlanData.get(i).startsWith("Address: "))
				state.set(values.localaddress, wlanData.get(i).substring(wlanData.get(i).indexOf("Address: ")+9).trim());
		
			if(wlanData.get(i).startsWith("Gateway: "))
				state.set(values.gateway, wlanData.get(i).substring(wlanData.get(i).indexOf("Gateway: ")+9).trim());
		
		}
				
		getAccessPoints();
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
		
		for(int i = 0; i < result.length ; i++)
			System.out.println(r + "\t" + result[--r]);
		
		return result;
	}
	
	public void startWLAN(){
		new Thread(new Runnable() {
			@Override
			public void run() {	
				while(true){
					try {
						// Util.delay(WIFI_POLL_DELAY_MS);
						updateWLAN();
						parseWLAN();
						Util.delay(WIFI_POLL_DELAY_MS);
					} catch (Exception e) {
						Util.log("startWLAN()", e, this);
					}
				}
			}
		}).start();
	}
	
	public void updateExternalIPAddress(){
		try {
			
			String address = "";
			URLConnection connection = (URLConnection) new URL("http://checkip.dyndns.org/").openConnection();
			BufferedInputStream in = new BufferedInputStream(connection.getInputStream());

			int i;
			while ((i = in.read()) != -1) address += (char)i;
			in.close();

			// parse html file
			address = address.substring(address.indexOf(": ") + 2);
			address = address.substring(0, address.indexOf("</body>"));
			
			// TODO: Page could disappear, or change format ... should test valid IP  
			state.put(values.externaladdress, address);
		
		} catch (Exception e) {
			Util.log("updateExternalIPAddress()", e, this);
		}
	}
	
	// public static void main(String[] args) { new NetworkMonitor(); }
}
