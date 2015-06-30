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

	public static final String AP = "ap";

	public static final long TWO_MINUTES = 120000;
	static final long serialVersionUID = 1L;
	
	static Vector<String> accesspoints = new Vector<String>();
	static Vector<String> connections = new Vector<String>();
	
	static boolean connected = false;
	static String currentSSID = null;
	static String gateway = null;
	static boolean busy = false;
	static String wdev = null;
		
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		lookupCurrentSSID();
		connectionsNever();
		getConnections();
		lookupDevice();
		killApplet();
		iwlist();
	}

	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		System.out.println(new Date() + " doPost, redirect: " + request.getQueryString());
		doGet(request, response);
	}

	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		if(request.getQueryString() != null) System.out.println(new Date() + " " + request.getQueryString());
		else System.out.println(new Date() + " " + request.getServerName());
			
		String action = null;
		String router = null; 
		String ssid = null;
		String password = null;
	
		try {
			
			action = request.getParameter("action");
			router = request.getParameter("router");
			ssid = request.getParameter("ssid");
			password = request.getParameter("password");
			
		} catch (Exception e) {
			System.out.println("doGet(): " + e.getLocalizedMessage());
		}
		
		if(ssid != null && password != null){
			System.out.println("doGet(): changeWIFI, ssid: " + ssid + " password: " + password);
			changeWIFI(ssid, password);	
			response.sendRedirect("/oculusprime"); 
			return;
		}
 
		if(router != null && password != null){
			System.out.println("doGet(): changeWIFI, ssid: " + ssid + " password: " + password);
			changeWIFI(router, password);
			response.sendRedirect("/oculusprime"); 
			return;
		}
		
		if(action != null){ 
			
			if(action.equals("connect")){	
				sendLogin(request, response, router);
				return;
			}	
			
			if(action.equals("config")){	
				sendConfig(request, response);
				return;	
			}
			
			if(action.equals("up")) changeWIFI(router);
		
			if(action.equals("disconnect")) disconnect();
					
			if(action.equals("scan")) scan();
		
			if(action.equals(AP)) changeWIFI(AP); 
			
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
		out.println("<table><form method=\"post\"><tr><td>ssid <td><input type=\"text\" name=\"ssid\">"
				+ "<tr><td>password <td><input type=\"password\" name=\"password\"> " 
				+ "<tr><td><br><input type=\"submit\" value=\"configure router\"></form>");
		out.println("\n\n </body></html>");
		out.close();
	}
	
	private String toHTML(final String addr){
		
		StringBuffer html = new StringBuffer();
		
		//iwlist();
		
		if(currentSSID == null) lookupCurrentSSID();
		if(gateway == null) lookupGateway();
		
		if(currentSSID == null) html.append(" -- not connected --  <br> \n");
		else html.append("connected: <b>" + currentSSID + " </b><br> \n"); 
			
		html.append("gateway: " + gateway + " <br>\n");
		
		if( ! busy){
			html.append(" -- <a href=\"http://"+addr+"/oculusprime?action=ap\">start access point mode</a><br>\n");
			html.append(" -- <a href=\"http://"+addr+"/oculusprime?action=config\">configure router</a><br>\n");			
			html.append(" -- <a href=\"http://"+addr+"/oculusprime?action=disconnect\">disconnect</a><br>\n");
			html.append(" -- <a href=\"http://"+addr+"/oculusprime?action=scan\">scan wifi</a><br>\n");
		}
		
		// html.append("<hr width=\"100px\"><br>\n");
		html.append("<hr> \n");

		for(int i = 0 ; i < accesspoints.size() ; i++) {	
			if(busy) html.append(accesspoints.get(i) + " <br> \n");
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
	
	private class pingThread extends Thread {
		@Override
		public void run() {
			try{			
				
				Process proc = Runtime.getRuntime().exec(new String[]{"ping", ""}); // state.get(values.gateway)});
				BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));

				String line = null;
				long last = System.currentTimeMillis();
				while ((line = procReader.readLine()) != null){
					//if(line.contains("completed") || line.contains("New")) {
						
					System.out.println("pingThread [" + ( System.currentTimeMillis() - last ) + "] " + line);
					
					
					last = System.currentTimeMillis();
					
				}
			} catch (Exception e) {
				System.out.println("eventThread: " + e.getLocalizedMessage());
			}
		}
	}
	
	private /*synchronized*/ void changeWIFI(final String ssid, final String password){
		
		if(ssid == null || password == null) return; 

		if(busy){
			System.out.println("changeWIFI(ssid, passwod): busy, rejected.. ");
			return;
		}
		
		new Thread(){
		    public void run() {
		    	try {	
		    			
		    		busy = true;
		    		
		   // 		connectionsPurge(ssid);
		    		
		    		if(currentSSID == AP) disconnect();
		    		wifiEnable();
		    		iwlist();

		    		String cmd[] = new String[]{"nmcli", "dev", "wifi", "connect", ssid ,"password", password}; 
					Process proc = Runtime.getRuntime().exec(cmd);				
					proc.waitFor();	
					
					System.out.println("changeWIFI(password): [" + ssid + "] exit code: " + proc.exitValue());					
					if(proc.exitValue() == 0) {
						lookupCurrentSSID();	
						getConnections();
					}
					
					String line = null;
					BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getErrorStream()));					
					while ((line = procReader.readLine()) != null)			
						System.out.println("changeWIFI(password): " + line);	
					
					busy = false;
					
		    	} catch (Exception e) {
		    		System.out.println("changeWIFI(password): [" + ssid + "] Exception: " + e.getMessage()); 
		    		connections.remove(ssid);
					busy = false;
				}
		    }
		}.start();
	}

	public synchronized static void changeWIFI(final String ssid){
		
		if(ssid == null) return; 
		
		if(busy){
			System.out.println("changeWIFI(ssid): busy, rejected.. ");
			return;
		}
		
		new Thread(){
		    public void run() {
		    	try {
		    		busy = true;
					Process proc = Runtime.getRuntime().exec(new String[]{"nmcli", "c", "up", "id", ssid}); // "\""+ssid+"\"" }); 
					proc.waitFor();
					System.out.println("changeWIFI(): [" + ssid + "] exit code: " +  proc.exitValue());
					busy = false;
		    	} catch (Exception e) {
					System.out.println("changeWIFI(): [" + ssid + "] exception: " + e.getMessage()); 
					busy = false;
				}
		    }
		}.start();
	}
	
	private void scan(){
		
		if(busy){
			System.out.println("scan(): busy, rejected.. ");
			return;
		}
		
		new Thread(){
		    public void run() {
		    	
		    	busy = true;
		    	System.out.println("... scanning ... ");
		    
		    	try {	
		    			
		    		if(currentSSID != null) Runtime.getRuntime().exec(new String[]{"nmcli", "c", "down", "id", currentSSID}).waitFor();
		    		wifiDisable();
		    		wifiEnable();
		    		iwlist();
		    		Thread.sleep(TWO_MINUTES); // wrong, keep scanning.....
		    		iwlist();
		    		changeWIFI(AP);
		    		
		    	} catch (Exception e) {
		    		System.out.println("scan(): exception: " + e.getMessage()); 
		    		busy = false;
				}	    
    	
		    	busy = false;
		    }	    
		}.start();
	}
	
	private void iwlist(){
		
		if(wdev==null) lookupDevice();
		if( !connected) wifiEnable();
		
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

	private void lookupGateway(){
		try {
			Process proc = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", "nm-tool | grep Gateway"});
			String line = new BufferedReader(new InputStreamReader(proc.getInputStream())).readLine();
			if(line.contains("Gateway")) gateway = line.substring(line.indexOf(":")+1).trim();
			else gateway = null;
		} catch (Exception e) {
			System.out.println("lookupGateway(): exception: " + e.getLocalizedMessage());
			gateway = null;
		}
		
		/*
		 	try {
			Process proc = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", "nm-tool | grep Gateway"});
			String line = null;
			BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));					
			while ((line = procReader.readLine()) != null) {
				if(line.contains("Gateway")) gateway = line.substring(line.indexOf(":")+1).trim();
			}
		} catch (Exception e) {
			System.out.println("lookupGateway(): exception: " + e.getLocalizedMessage());
			gateway = null;
		}
		 */
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
	
	private static void disconnect(){
		if(currentSSID == null) {
			System.out.println("disconnect(): null ssid, reject.. ");
			return;
		}
		
		// System.out.println("disconnect(): from ssid: " + currentSSID);
		
		try {
			int code = Runtime.getRuntime().exec(new String[]{"nmcli", "c", "down", "id", currentSSID}).waitFor();	
    		System.out.println("disconnect(): code = " + code);
    		if(code == 0){
    			currentSSID = null;
    			connected = false;
    			gateway = null;
    		}
		} catch (Exception e) {
			System.out.println("disconnect(): exception: " + e.getLocalizedMessage());
		}
	}
	
	private static void connectionsNever(){
		try {			
			String[] cmd = new String[]{"/bin/sh", "-c", "nmcli -f timestamp,uuid con"};
			Process proc = Runtime.getRuntime().exec(cmd);
			proc.waitFor();
			
			String line = null;
			BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));					
			while ((line = procReader.readLine()) != null) {	
				String[] in = line.split(" ");
				if(in[0].equals("0")) {
					System.out.println("connectionsNever(): deleting uuid: " + in[in.length-1]); 
					Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", "nmcli con delete uuid " + in[in.length-1]});
				}
			}			
		} catch (Exception e) {
			System.out.println("connectionsNever(): exception: " + e.getLocalizedMessage());
		}
	}
	
	/*
	private static void connectionsPurge(final String ssid){
		try {			
			String[] cmd = new String[]{"/bin/sh", "-c", "nmcli -f name,uuid con"};
			Process proc = Runtime.getRuntime().exec(cmd);
			proc.waitFor();
			
			String line = null;
			BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));					
			while ((line = procReader.readLine()) != null) {	
				String[] in = line.trim().split(" ");
				if(in[0].startsWith(ssid)) {
					System.out.println(ssid + " connectionsPurge(): deleting duplicate: " + in[in.length-1]); 
					Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", "nmcli con delete uuid " + in[in.length-1]});
				}
			}			
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
	
	private static boolean getConnections(){
		try {			
			String[] cmd = new String[]{"/bin/sh", "-c", "nmcli -f name con"};
			Process proc = Runtime.getRuntime().exec(cmd);
			proc.waitFor();
			
			String line = null;
			BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));					
			while ((line = procReader.readLine()) != null) {	
				if(!connections.contains(line) && !line.equals("NAME"))
					connections.add(line.trim());
			}			
		} catch (Exception e) {
			System.out.println("getConnections(): exception: " + e.getLocalizedMessage());
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