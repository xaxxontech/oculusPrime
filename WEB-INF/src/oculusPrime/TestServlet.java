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
	
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		lookupDevice();
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
		
		if(password != null && router != null) {
			
			System.out.println("doGet(): password given, try to connect...");
			changeWIFI(router, password);
			
		} else {
		
			System.out.println("doGet(): scanning...");
			iwlist();
		
		}
		
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		out.println("<html><head> \n");
		out.println(toHTML(request.getServerName()));
		out.println("\n</body></html> \n");
		out.close();	
	}
	
	private String toHTML(final String addr){
		StringBuffer html = new StringBuffer();
		
		html.append(accesspoints.size() + " -- available accesspoints <br><br> \n");		
		for(int i = 0 ; i < accesspoints.size() ; i++) {
				html.append("<a href=\"http://"+addr+"/oculusprime?router=" 
					+ accesspoints.get(i) + "\">" + accesspoints.get(i) + "</a><br> \n");
		
		}	
	
		return html.toString();
	}
	
	// http://192.168.1.7/oculusprime/?router=bradzcave&password=xxxxx
	private synchronized static void changeWIFI(final String ssid, final String password){
		
		if(ssid == null || password == null) return; 
		
		new Thread(){
		    public void run() {
		    	try {
			
		    		String cmd[] = new String[]{"nmcli", "dev", "wifi", "connect", ssid ,"password", password}; 
		    	//	String cmd[] = new String[]{"nmcli", "dev", "wifi", "connect", "\"" + ssid + "\"", "password", password}; 
					Process proc = Runtime.getRuntime().exec(cmd);
				
					System.out.println("changeWIFI(password): [" + ssid + "] exit code: " + proc.exitValue());					

					String line = null;
					BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));					
					while ((line = procReader.readLine()) != null){
						System.out.println("changeWIFI(password): input: " + line);					
					}

					procReader = new BufferedReader(new InputStreamReader(proc.getErrorStream()));					
					while ((line = procReader.readLine()) != null){			
						System.out.println("changeWIFI(password): _error_ " + line);			
	   //				Error: No network with SSID 'bradcave' found.		
					}
						
					proc.waitFor();	
					System.out.println("changeWIFI(password): exit code = " + proc.exitValue());
					
		    	} catch (Exception e) {
		    		System.out.println("changeWIFI(password): [" + ssid + "] Exception: " + e.getMessage()); 
				}
		    }
		}.start();
	}
	
	private void iwlist(){
		
		if(wdev==null) return; //  || !connected) return;
		
		accesspoints.clear();
		
		try {
			Process proc = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", "iwlist " + wdev + " scanning | grep ESSID"});
			String line = null;
			BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));					
			while ((line = procReader.readLine()) != null) {
				line = line.substring(line.indexOf("\"")+1, line.length()-1).trim();
				if((line.length() > 0) && !accesspoints.contains(line)) accesspoints.add(line);
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
					String[] list = line.split(" ");
					wdev = list[0];
				}
			}
		} catch (Exception e) {
			System.out.println("lookupDevice(): exception: " + e.getLocalizedMessage());
		}
	}
	
}