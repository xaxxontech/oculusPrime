package oculusPrime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Vector;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TestServlet extends HttpServlet {
	
	static final long serialVersionUID = 1L;
	static Vector<String> accesspoints = new Vector<String>();
	static boolean connected = false;
	static String wdev = null;
	static String currentSSID = null;
	static boolean busy = false;
	
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		lookupCurrentSSID();
		connectionsNever();
		lookupDevice();
		iwlist();
	}

	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}

	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
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
			changeWIFI(router, password);
			response.sendRedirect("/oculusprime"); 
			return;
		}
		
		if(action != null && router != null) { 
			if(action.equals("connect")){	
				sendLogin(request, response, router);
				return;
			}	
		}
		
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
//	    if(currentSSID == null) out.println("<html><head><meta http-equiv=\"refresh\" content=\"3\"></head><body> \n");
//		else out.println("<html><head><body> \n");
		out.println(toHTML(request.getServerName()));
//		out.println("\n</body></html> \n");
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
	
	private String toHTML(final String addr){
		
		iwlist();
		lookupCurrentSSID();
		StringBuffer html = new StringBuffer();
		
		if(currentSSID == null) html.append(" -- not connected --  <br> \n");
		else html.append("<b>ssid: " + currentSSID + " </b><br> \n");
		
		for(int i = 0 ; i < accesspoints.size() ; i++) {	
			if(busy) html.append(accesspoints.get(i) + " <br> \n");
			else html.append("<a href=\"http://"+addr+"/oculusprime?action=connect&router=" 
						+ accesspoints.get(i) + "\">" + accesspoints.get(i) + "</a><br> \n");
		}	
	
		return html.toString();
	}
	
	private /*synchronized*/ void changeWIFI(final String ssid, final String password){
		
		if(ssid == null || password == null) return; 
		
		new Thread(){
		    public void run() {
		    	try {	
		    			
		    		busy = true;
		    		
		    		if( !connected) wifiEnable();
	
		    		connectionsPurge(ssid);
		    		
		    		String cmd[] = new String[]{"nmcli", "dev", "wifi", "connect", ssid ,"password", password}; 
					Process proc = Runtime.getRuntime().exec(cmd);				
					proc.waitFor();	
					
					System.out.println("changeWIFI(password): [" + ssid + "] exit code: " + proc.exitValue());					
					if(proc.exitValue() == 0) lookupCurrentSSID();
					
					String line = null;
					BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getErrorStream()));					
					while ((line = procReader.readLine()) != null)			
						System.out.println("changeWIFI(password): " + line);	
					
					busy = false;
					
		    	} catch (Exception e) {
		    		System.out.println("changeWIFI(password): [" + ssid + "] Exception: " + e.getMessage()); 
				}
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
					accesspoints.add(line);
			}
		} catch (Exception e) {
			System.out.println("iwlist(): exception: " + e.getLocalizedMessage());
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
			Runtime.getRuntime().exec(new String[]{"nmcli", "nm", "wifi", "on"});
		} catch (Exception e) {
			System.out.println("wifiEnable(): exception: " + e.getLocalizedMessage());
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
}