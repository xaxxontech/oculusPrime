package oculusPrime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Date;
import java.util.Vector;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TestServlet extends HttpServlet {

	static final long serialVersionUID = 1L;
	static final long PING_TIMEOUT = 60000;
	static final String AP = "ap";
	static boolean DEBUG = true;

	static Vector<String> accesspoints = new Vector<String>();
	static Vector<String> connections = new Vector<String>();	
		
	static private Thread watchdogThread = new watchdogThread();
	static private Thread pingThread = new pingThread();
	static long lastping = System.currentTimeMillis();
	
	static boolean cancelAccessPoint = false;	
	static boolean runningPingThread = true;
	static boolean connected = false;
	static boolean wifiBusy = false;
	static String currentSSID = null;
	static String pingtime = null;
	static String gateway = null;	
	static String status = null;
	static String apUUID = null;
	static String wdev = null;
		
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		
		lookupAccessPoint();
		lookupCurrentSSID();
		lookupConnections();
		lookupDevice();
		killApplet();
		iwlist();
		
		new iweventThread().start();
		watchdogThread.start();
		pingThread.start();
		
	}

	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}

	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
			
		String ip = request.getServerName(); // only permit known addresses 
		if(!(ip.startsWith("10.42.0") || ip.startsWith("127.0.0") || ip.startsWith("0.0.0"))){
			System.out.println("doGet(): only allow lan IP's addresses... " +ip);
		//	return;
		// TODO: ACTIVATE 
		}
			
		String action = null;
		String router = null; 
		String password = null;
	
		try {
			
			action = request.getParameter("action");
			router = request.getParameter("router");
			password = request.getParameter("password");
			
		} catch (Exception e) {
			System.out.println("doGet(): " + e.getLocalizedMessage());
		}
		
		if(router != null && password != null){ 
			System.out.println("doGet(): changeWIFI, router: " + router + " password: " + password);
			changeWIFI(router, password);
			response.sendRedirect("/oculusprime"); 
			return;
		}
	
		if(action != null){ 

			if(action.equals("status")){
				response.setContentType("text/html");
				PrintWriter out = response.getWriter();				
				out.println(status);
				out.close();
				return;
			}
			
			if(action.equals("connect")){	
				sendLogin(request, response, router);
				return;
			}	
			
			if(action.equals("config")){	
				sendConfig(request, response);
				return;	
			}
			
			if(action.equals("purge")) connectionsPurge();
			if(action.equals("disconnect")) disconnect();
			if(action.equals("up")) changeWIFI(router);
			if(action.equals(AP)) changeWIFI(AP); 		
			if(action.equals("scan")) scan();
		
			response.sendRedirect("/oculusprime"); 
			return;
		}
		
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
//	    if(currentSSID == null) out.println("<html><head><meta http-equiv=\"refresh\" content=\"3\"></head><body> \n");
		
		out.println("<html><head><body> \n");
		out.println(toHTML(request.getServerName()));
		out.println("\n</body></html> \n");
		out.close();	
	}
	
	public void sendLogin(HttpServletRequest request, HttpServletResponse response, String ssid) throws IOException{
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		out.println("<html><body> \n\n");
		out.println("connect to: " + ssid);
		out.println("<form method=\"post\">password: <input type=\"password\" name=\"password\"></form>");
		out.println("\n\n </body></html>");
		out.close();
	}
	
	public void sendConfig(HttpServletRequest request, HttpServletResponse response) throws IOException{
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		out.println("<html><body> \n\n");
		out.println("<table><form method=\"post\"><tr><td>ssid <td><input type=\"text\" name=\"router\">"
				+ "<tr><td>password <td><input type=\"password\" name=\"password\"> " 
				+ "<tr><td><td><br><input type=\"submit\" value=\"configure router\"></form>");
		out.println("\n\n </body></html>");
		out.close();
	}
	
	private String toHTML(final String addr){
		iwlist();
		if(currentSSID == null) lookupCurrentSSID();
		if(gateway == null) lookupGateway();

		StringBuffer html = new StringBuffer();
		if(currentSSID == null) html.append(" -- not connected --  <br> \n");
		else html.append("connected: <b>" + currentSSID + " </b><br> \n"); 
					
		if( ! wifiBusy){
			html.append("<a href=\"http://"+addr+"/oculusprime?action=ap\">start access point mode</a><br>\n");
			html.append("<a href=\"http://"+addr+"/oculusprime?action=purge\">purge all connections</a><br>\n");
			html.append("<a href=\"http://"+addr+"/oculusprime?action=config\">configure router</a><br>\n");	
			html.append("<a href=\"http://"+addr+"/oculusprime?action=disconnect\">disconnect</a><br>\n");
			html.append("<a href=\"http://"+addr+"/oculusprime?action=scan\">scan wifi</a><br><br>\n");
		}
		
		html.append(" -- access points -- <br>\n");
		for(int i = 0 ; i < accesspoints.size() ; i++) {	
			if(wifiBusy) html.append(accesspoints.get(i) + " <br> \n");
			else {
				if(connections.contains(accesspoints.get(i))){
					// no password needed 
					html.append("<a href=\"http://"+addr+"/oculusprime?action=up&router=" 
						+ accesspoints.get(i) + "\">" + accesspoints.get(i) + "</a> ** <br> \n");
				} else {
					// password required 
					html.append("<a href=\"http://"+addr+"/oculusprime?action=connect&router=" 
						+ accesspoints.get(i) + "\">" + accesspoints.get(i) + "</a><br> \n");
				}
			}		
		}
		return html.toString();
	}
	
	private static class pingThread extends Thread {	
		@Override
		public void run() {
			try{			
				
				if(DEBUG) System.out.println("pingThread: starting thread..");
				if(gateway == null) lookupGateway();
				if(gateway == null) return;
				
				Process proc = Runtime.getRuntime().exec(new String[]{"ping", gateway});
				BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));

				runningPingThread = true;
				String line = null;
				lastping = System.currentTimeMillis();
				while ((line = procReader.readLine()) != null){	
					if(line.contains("filtered") || !runningPingThread) break;
					if(line.contains("time")) {
						pingtime = line.substring(line.indexOf("time=")+5, line.indexOf(" ms"));
						status = new Date().toString() + ", " + currentSSID + ", " + gateway + ", " 
								+ pingtime + "ms, " + (System.currentTimeMillis() - lastping) + "ms";	
					} 
					
					lastping = System.currentTimeMillis();
				}
				
				if(DEBUG) System.out.println("pingThread: exit..");
				wifiBusy = false;
				
			} catch (Exception e) {
				System.out.println("pingThread: " + e.getLocalizedMessage());
				pingtime = null;
				wifiBusy = false;
			}
		}
	}
	
	private static class watchdogThread extends Thread {
		@Override
		public void run() {		
			try{				
				for(;;){ 
						
					Thread.sleep(10000);
			
					if(currentSSID.equalsIgnoreCase(AP)) runningPingThread = false;
					else {
						if(( System.currentTimeMillis() - lastping ) > PING_TIMEOUT/3) reset();
						if(( System.currentTimeMillis() - lastping ) > PING_TIMEOUT) {
							if(DEBUG) System.out.println("watchdog the ping thread is timed out, starting AP..");
							lastping = System.currentTimeMillis();
							if(!wifiBusy) changeWIFI(AP);
						}
					}
				}
			} catch (Exception e) {
				System.out.println("watchdogThread: " + e.getLocalizedMessage());
			}
		}
	}
	
	private static class accesspointThread extends Thread {
				
		@Override
		public void run() {		
			try{				
				for(int i = 0 ; i < 60 ; i++){ 
						
					Thread.sleep(2000);
			
					if(DEBUG) System.out.println("accesspointThread: " + i );
					
					if(cancelAccessPoint) break;
				}
				
				if( ! cancelAccessPoint){
					if(DEBUG) System.out.println("accesspointThread: timed out, try any connection.. ");
					String tryme = lookupAutoConnect();
					if(DEBUG) System.out.println("accesspointThread: try ssid: " + tryme);
					if(tryme != null) changeWIFI(tryme);
				}
				
				if(DEBUG) System.out.println("accesspointThread: exit.. ");
				
			} catch (Exception e) {
				System.out.println("accesspointThread: " + e.getLocalizedMessage());
			}
		}
	}

	/* look for linux connection events beyond our control */
	private static class iweventThread extends Thread {
		@Override
		public void run() {
			try{			
				Process proc = Runtime.getRuntime().exec(new String[]{"iwevent"});
				BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));

				String line = null;
				while ((line = procReader.readLine()) != null){	
					if(line.contains("IEs:")) {
						if(DEBUG) System.out.println("iweventThread: connection changed..");
						runningPingThread = false;
						lookupCurrentSSID();	
						lookupConnections();
						lookupGateway();
					} 
				}
			} catch (Exception e) {
				System.out.println("iweventThread: " + e.getLocalizedMessage());
			}
		}
	}

	private void changeWIFI(final String ssid, final String password){
		
		if(ssid == null || password == null || wifiBusy) return; 

		new Thread(){
			public void run() {
		    	try {	
		    			
		    		wifiBusy = true;
		    		
		   // 		connectionsPurge(ssid);
		    		
		    		disconnect();
		    		wifiDisable();
		    		wifiEnable();
		    		iwlist();

		   // 		Thread.sleep(1000);
		    		
		    		String cmd[] = new String[]{"nmcli", "dev", "wifi", "connect", ssid ,"password", password}; 
					Process proc = Runtime.getRuntime().exec(cmd);				
					proc.waitFor();	
					
					System.out.println("changeWIFI(password): [" + ssid + "] exit code: " + proc.exitValue());					
					if(proc.exitValue() == 0) {
						cancelAccessPoint = true;	
						reset();
					}
					
					String line = null;
					BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getErrorStream()));					
					while ((line = procReader.readLine()) != null)			
						System.out.println("changeWIFI(password): " + line);	
					
					wifiBusy = false;
					
		    	} catch (Exception e) {
		    		System.out.println("changeWIFI(password): [" + ssid + "] Exception: " + e.getMessage()); 
		    		reset();
				}
		    }
		}.start();
	}

	public static void changeWIFI(final String ssid){
		
		if(ssid == null || wifiBusy) return; 
	
		new Thread(){
		    public void run() {
		    	try {
		    		wifiBusy = true;
					Process proc = Runtime.getRuntime().exec(new String[]{"nmcli", "c", "up", "id", ssid}); // "\""+ssid+"\"" }); 
					proc.waitFor();
					System.out.println("changeWIFI(): [" + ssid + "] exit code: " +  proc.exitValue());
					if(proc.exitValue() == 0) {
						if(ssid.equalsIgnoreCase(AP)) {
							System.out.println("changeWIFI(): [" + ssid + "] ap mode, start timer..");
							runningPingThread = false;
							new accesspointThread().start();
						} else {
							System.out.println("changeWIFI(): ap turnned off, stop timer..");
							cancelAccessPoint = true;
							reset();
						}	
					}
					
					wifiBusy = false;
		    	} catch (Exception e) {
					System.out.println("changeWIFI(): [" + ssid + "] exception: " + e.getMessage()); 
					reset();
				}
		    }
		}.start();
	}
	
	private synchronized static void reset(){
		wifiBusy = true;		
		runningPingThread = false;
		try { Thread.sleep(2000); } catch (InterruptedException e) {} //TODO: UNNESSISARRY?? 
		lookupCurrentSSID();	
		lookupConnections();
		lookupGateway();
		pingThread = new pingThread();
		pingThread.start();
		wifiBusy = false;
	}
	
	private void scan(){
		
		if(wifiBusy){
			System.out.println("scan(): busy, rejected.. ");
			return;
		}
		
		new Thread(){
		    public void run() {
		    	
		    	wifiBusy = true;
		    	System.out.println("... scanning ...");
		    
		    	try {	
		    			
		    		disconnect();
		    		wifiDisable();
		    		wifiEnable();
		    
		    		for(int i = 0 ; i < 10 ; i++) iwlist();
		    		
		    		wifiDisable();
		    		wifiEnable();
		    		changeWIFI(AP);
		    		
		    	} catch (Exception e) {
		    		System.out.println("scan(): exception: " + e.getMessage()); 
		    		wifiBusy = false;
				}	    
    	
		    	wifiBusy = false;
		    }	    
		}.start();
	}
	
	private void iwlist(){
		
		if(wdev==null) lookupDevice();
		if(!connected) wifiEnable();
		
		// accesspoints.clear();
	
		if(wdev==null) return;
		
		try {
			String[] cmd = new String[]{"/bin/sh", "-c", "iwlist " + wdev + " scanning | grep ESSID"};
			Process proc = Runtime.getRuntime().exec(cmd);
			String line = null;
			BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));					
			while ((line = procReader.readLine()) != null) {
				line = line.substring(line.indexOf("\"")+1, line.length()-1).trim();
				if((line.length() > 0) && !accesspoints.contains(line)) 
					accesspoints.add(line.trim());
			}
		} catch (Exception e) {
			System.out.println("iwlist(): exception: " + e.getLocalizedMessage());
		}
	}

	private static void lookupGateway(){
		try {
			Process proc = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", "nm-tool | grep Gateway"});
			String line = new BufferedReader(new InputStreamReader(proc.getInputStream())).readLine();
			if(line.contains("Gateway")) gateway = line.substring(line.indexOf(":")+1).trim();
			else gateway = null;
		} catch (Exception e) {
			System.out.println("lookupGateway(): exception: " + e.getLocalizedMessage());
			gateway = null;
		}
	}
	
	private void lookupDevice(){
		try {
			Process proc = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", "nmcli dev"});
			String line = null;
			BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));					
			while ((line = procReader.readLine()) != null) {
				if( ! line.startsWith("DEVICE") && line.contains("wireless")){
					if(line.contains("connected")) connected = true;
					if(line.contains("unavailable")) connected = false;
					String[] list = line.split(" ");
					wdev = list[0];
				}
			}
		} catch (Exception e) {
			System.out.println("lookupDevice(): exception: " + e.getLocalizedMessage());
		}
	}
	
	private static void wifiEnable(){
		try {
			Runtime.getRuntime().exec(new String[]{"nmcli", "nm", "wifi", "on"}).waitFor();
		} catch (Exception e) {
			System.out.println("wifiEnable(): exception: " + e.getLocalizedMessage());
		}
	}
	
	private static void wifiDisable(){
		try {
			Runtime.getRuntime().exec(new String[]{"nmcli", "nm", "wifi", "off"}).waitFor();
		} catch (Exception e) {
			System.out.println("wifiEnable(): exception: " + e.getLocalizedMessage());
		}
	}
	
	private static synchronized void disconnect(){
		
		if(currentSSID == null) return;
		
		if(DEBUG) System.out.println("disconnect(): from ssid: " + currentSSID);
		
		try {
			int code = Runtime.getRuntime().exec(new String[]{"nmcli", "c", "down", "id", currentSSID}).waitFor();	
    		System.out.println("disconnect(): code = " + code);
    		if(code == 0) reset();
		} catch (Exception e) {
			System.out.println("disconnect(): exception: " + e.getLocalizedMessage());
		}
	}
	
	/*
	private static void connectionsNever(){
		try {			
			String[] cmd = new String[]{"/bin/sh", "-c", "nmcli -f timestamp,uuid con"};
			Process proc = Runtime.getRuntime().exec(cmd);
			proc.waitFor();
			
			String line = null;
			BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));					
			while ((line = procReader.readLine()) != null) {	
				String[] in = line.split(" ");
				if(in[0].equals("0") && !line.contains(apUUID)) {
					System.out.println("connectionsNever(): deleting uuid: " + in[in.length-1]); 
					Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", "nmcli con delete uuid " + in[in.length-1]});
				}
			}			
		} catch (Exception e) {
			System.out.println("connectionsNever(): exception: " + e.getLocalizedMessage());
		}
	}
	*/
	
	private static String lookupAutoConnect(){
		try {			
			String[] cmd = new String[]{"/bin/sh", "-c", "nmcli -f autoconnect,name con"};
			Process proc = Runtime.getRuntime().exec(cmd);
			proc.waitFor();
			
			String line = null;
			BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));					
			while ((line = procReader.readLine()) != null) {					
				if(line.startsWith("yes") && !line.contains(AP)) {	
					String[] args = line.split(" ");
					return args[args.length-1];
				}
			}			
		} catch (Exception e) {
			System.out.println("lookupAutoConnect(): exception: " + e.getLocalizedMessage());
		}
		
		return null;
	}

	private static void connectionsPurge(){
		try {			
			String[] cmd = new String[]{"/bin/sh", "-c", "nmcli -f uuid con"};
			Process proc = Runtime.getRuntime().exec(cmd);
			proc.waitFor();
			
			String line = null;
			BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));					
			while ((line = procReader.readLine()) != null) {	
				String[] in = line.trim().split(" ");
				if(!line.contains(apUUID)){ 
					System.out.println("connectionsPurge(): deleting: " + in[in.length-1]); 
					Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", "nmcli con delete uuid " + in[in.length-1]});
				}
			}			
		} catch (Exception e) {
			System.out.println("connectionsNever(): exception: " + e.getLocalizedMessage());
		}
		
		// nothing else can do
		connections.clear();
		wifiBusy = false;
		changeWIFI(AP);
	}
	
	/*
	private static void lookupEthernet(){
		try {			
			String[] cmd = new String[]{"/bin/sh", "-c", "nmcli con"};
			Process proc = Runtime.getRuntime().exec(cmd);
		
			String line = null;
			BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));					
			while ((line = procReader.readLine()) != null) {	
				 if(line.contains("Strength")) { 
					 currentSSID = line.substring(line.indexOf("*")+1, line.indexOf(":"));
					 return;
				 }
			}
			
			currentSSID = null;
			
		} catch (Exception e) {
			System.out.println("connectionsNever(): exception: " + e.getLocalizedMessage());
		}
	}
	*/
	
	private static void lookupCurrentSSID(){
		try {			
			String[] cmd = new String[]{"/bin/sh", "-c", "nm-tool | grep \"*\""};
			Process proc = Runtime.getRuntime().exec(cmd);
		
			String line = null;
			BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));					
			while ((line = procReader.readLine()) != null) {	
				 if(line.contains("Strength")) { 
					 currentSSID = line.substring(line.indexOf("*")+1, line.indexOf(":"));
					 return;
				 }
			}
			
			currentSSID = null;
			
		} catch (Exception e) {
			System.out.println("connectionsNever(): exception: " + e.getLocalizedMessage());
		}
	}
	
	private static boolean lookupConnections(){
		try {			
			String[] cmd = new String[]{"/bin/sh", "-c", "nmcli -f type,name con"};
			Process proc = Runtime.getRuntime().exec(cmd);
			proc.waitFor();
			
			String line = null;
			BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));					
			while ((line = procReader.readLine()) != null) {	
				if(!connections.contains(line) && !line.equals("NAME")){
					String[] args = line.split(" ");
					if(args[0].contains("wireless")){
						
						System.out.println("**get[" + line.substring(args[0].length()).trim() + "]");
						connections.add(line.substring(args[0].length()).trim());
					
					}
				}
			}			
		} catch (Exception e) {
			System.out.println("getConnections(): exception: " + e.getLocalizedMessage());
		}
		
		return false;
	}
	
	private static boolean lookupAccessPoint(){
		try {			
			String[] cmd = new String[]{"/bin/sh", "-c", "nmcli -f name,uuid con"};
			Process proc = Runtime.getRuntime().exec(cmd);
			proc.waitFor();
			
			String line = null;
			BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));					
			while ((line = procReader.readLine()) != null) {	
				if(line.startsWith(AP)) {
					String[] data = line.split(" ");
					apUUID = data[data.length-1];
				}			
			}
		} catch (Exception e) {
			System.out.println("lookupAccessPoint(): exception: " + e.getLocalizedMessage());
		}
		
		return false;
	}
	
	private static void killApplet(){
		try {
			Runtime.getRuntime().exec(new String[]{"pkill", "nmcli"});
			Runtime.getRuntime().exec(new String[]{"pkill", "nm-applet"});
		} catch (Exception e) {
			System.out.println("killApplet(): " + e.getMessage());
		}
	}
}