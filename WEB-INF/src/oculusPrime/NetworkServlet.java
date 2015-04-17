package oculusPrime;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class NetworkServlet extends HttpServlet {
	
	private static final long serialVersionUID = 1L;	
		
	protected static final long HTTP_REFRESH_DELAY_SECONDS = 3; 
	protected static final long FAST_POLL_DELAY_MS = 400;
	protected static final long PING_DELAY_MS = 9000;
	protected static final long WLAN_TIMEOUT = 40000;

//	protected static final String PACKAGE = "oculusPrime/dashboard";
//	protected static final String AP_NAME = "oculusPrime";
//	protected static final String WLAN = "wlan0";
//	protected static final String ETH = "eth0";
			
//	private static final String NONE = "none";
//	private static String signalStrength = NONE;
//	private static String gatewayAddress = NONE;
	
//	private static String signalQuality = NONE;
//	private static String connectedSSID = NONE;
//	private static String wlanAddress = NONE;
	
	NetworkMonitor monitor = NetworkMonitor.getReference();

//	private static Vector<String> accesspoints = new Vector<String>();
	private static Vector<String> connections = new Vector<String>();
//	private Vector<String> networkData = new Vector<String>();
	
//	private static long last = System.currentTimeMillis();
//	private static float pingTime = 0;
//	private static int pingCount = 0;
//	private static int pingFail = 0;
	
//	Timer pingTimer = new Timer();
//	Timer networkTimer = new Timer();
//	Timer watchdogTimer = new Timer();
//	Timer stateTimer = new Timer();
	
	public HashMap<String, String> map = new HashMap<String, String>();

//	private State state = State.getReference();
	
	public void init(ServletConfig config) throws ServletException {
		
		super.init(config);
		
		// pingTimer.schedule(new pingTask(), 100, PING_DELAY_MS);
		
		connectionUpdate();
		
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
			System.out.println(e.getLocalizedMessage());
		}
		
		//if(password != null){
		//	response.sendRedirect("oculusPrime//network"); 
		//	changeWIFI(router, password);
		//	return;
		//}
		
		/*
		*/
	
		if(action != null){	
			//if(action.equals("shutdown")){	
			//	response.sendRedirect(PACKAGE);
			//	stopServer();
			//}
			///if(action.equals("adhoc")){	
			//	response.sendRedirect(PACKAGE);
			//	startAdhoc();
			//}
			///if(action.equals("disconnect")){
			///	response.sendRedirect(PACKAGE);
			//	disconnectWLAN();
			//	return;
			//}
			if(router != null){
				if(action.equals("connect")){	
					if(connectionExists(router, true)){
						response.sendRedirect("network");                              
						changeWIFI(router);
						return;
					}
				
					//sendLogin(request, response, router);
					//return;
				}
			}
		}	
		wifiSelection(request, response);
	}
	
	public void sendLogin(HttpServletRequest request, HttpServletResponse response, String ssid) throws IOException{
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		out.println("<html><body>");
		out.println("connect to: " + ssid);
		out.println("<form method=\"post\">password: <input type=\"password\" name=\"password\"></form>");
		out.println("</body></html>");
		out.close();
	}
	
	/*
	public void wifiInfo(HttpServletRequest request, HttpServletResponse response) throws IOException{
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
	
		out.println("<html><head><meta http-equiv=\"refresh\" content=\""+ HTTP_REFRESH_DELAY_SECONDS + "\"></head><body> \n");
		out.println(NetworkMonitor.getReference().wlanString() + "\n");
		
		out.println("\n</body></html> \n");
		out.close();		
	
	}
	*/
	
	public void wifiSelection(HttpServletRequest request, HttpServletResponse response) throws IOException{
				
		final String base = "<a href=\"http:\\\\"+request.getServerName()+":"+request.getServerPort() + "/oculusPrime/network";
		
		//final String disconnect = base + "?action=disconnect\">disconnect</a>";
		//final String adhoc = base + "?action=adhoc\">adhoc</a>";
		//final String shutdown = base + "?action=shutdown\">shutdown</a>";
		
		final String router = base + "?action=connect&router=";
	
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		out.println("<html><head><meta http-equiv=\"refresh\" content=\""+ HTTP_REFRESH_DELAY_SECONDS + "\"></head><body> \n");
		
		// out.println(monitor.wlanString() + "\n");	
			
		out.println("\n<br /><br /><table>");
		// out.println("\n<table>");
		
		String result[] = monitor.getAccessPoints();
		for(int i = 0; i < result.length ; i++)
			 out.println("\n<tr><td>" + router + result[i] + "\">"+ result[i] +"</a>");
		
		out.println("\n</table>");
		
		out.println("\n <br />v:99</body></html> \n");
		out.close();	
		
		// System.out.println("connectionUpdate(): " + router);
	
	}

		/*
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		out.println("<html><head><meta http-equiv=\"refresh\" content=\""+ HTTP_REFRESH_DELAY_SECONDS + "\"></head><body>");
		out.println("<b>wifi:</b>" + wlanAddress + " ...  "+adhoc+ " " +shutdown+ "<hr>"); 
	
		if( ! connectedSSID.equals(NONE))
			out.println(connectedSSID + " <i>(" + signalQuality + "%" + " " + signalStrength 
					+ "dBm "+ signalNoise +" noise)</i> ... "+disconnect +"<hr>");
		
		for(int i = 0 ; i < accesspoints.size() ; i++) 
			if( ! accesspoints.get(i).equals(connectedSSID))
				out.println(router + accesspoints.get(i) + "\">" + accesspoints.get(i) + "</a><br>");
		
		if( ! connectedSSID.equals(NONE)) {
			out.println("<hr>");
			out.println("ping errors: "+ pingFail +" ping sent: "+ pingCount +" ");
			out.println("network delay: " + pingTime + " ms ");
		}
		
	//	out.println(info.map.toString());
	// 	HashMap<String, String> map = info.map;
		
		Collection<String> keys = map.keySet();
		out.println("<table>"); //  border=\"1\">");
		for(Object key: keys){
		    out.println("<tr><td>" + key + " <td> " + map.get(key) + "</tr>");
		}
		out.println("</table>");
		
		out.println("</body></html>");
		out.close();
	
		
	}

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
	/*
	public class networkTask extends TimerTask {
	    @Override
	    public void run() {
	    	try{ 
	    			
	    		networkData.clear();
				Process proc = Runtime.getRuntime().exec(new String[]{"nm-tool"});
				BufferedReader procReader = new BufferedReader(
					new InputStreamReader(proc.getInputStream()));
				
				String line = null;
				while ((line = procReader.readLine()) != null){
					line = line.trim();
					if(line.length()>0) 
						if( ! networkData.contains(line))
							networkData.add(line);
				}
			
				proc.waitFor();
				readWAN();
				
			} catch (Exception e) {
				System.out.println("networkTask: " + e.getLocalizedMessage());
			}		
	    }    
	}

	public class pingTask extends TimerTask {		
		@Override
	    public void run() {
			try {
				
				if( !gatewayAddress.equals("0.0.0.0")){ //  && !connectedSSID.equals(NONE)){  
				
					/// System.out.println("pingTask: [" + connectedSSID + "] gateway: " + gatewayAddress + " ping: " + pingCount);
					
					if(wlanAddress.equals(NONE)) {
						if(System.currentTimeMillis() - last > WLAN_TIMEOUT){	
							System.out.println("pingTask: start adhoc after: " + (System.currentTimeMillis() - last) + " ms");
							last = System.currentTimeMillis();
							disconnectWLAN();
							startAdhoc();
						}
						
						System.out.println("pingTask: WLAN_TIMEOUT: " + (System.currentTimeMillis() - last) + " ms");
					//	pingCount = 0;
					//	pingFail = 0;
					//	pingTime = 0;
						
					} else {
						
					//	pingCount++;
						final String[] PING = new String[]{"ping", "-c", "1", "-I", WLAN, gatewayAddress};
						Process proc = Runtime.getRuntime().exec(PING);
						BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
											
						String line = null;
						while ((line = procReader.readLine()) != null){
							if(line.contains("Unreachable")){
								System.out.println("pingTask: Unreachable ip: " + gatewayAddress); 
						//		pingFail++;
							}
							if(line.contains("time=")){
								//pingTime = Float.parseFloat(line.substring(line.indexOf("time=")+5, line.indexOf(" ms")));
								last = System.currentTimeMillis();
							}	
						}
					}
				}
			} catch (Exception e) { 
				System.out.println("pingTask: " + e.getLocalizedMessage()); 
			}		
	    }
	}
	
	public void disconnectWLAN(){		
		try {
			Runtime.getRuntime().exec(new String[]{"nmcli", "device", "disconnect", "iface", WLAN});
		} catch (Exception e) {
			System.out.println("disconnectWLAN: " + e.getLocalizedMessage());
		}		
	}*/
	
	public synchronized void connectionUpdate(){		
		try {
			connections.clear();	
			Process proc = Runtime.getRuntime().exec(new String[]{"nmcli", "con", "list"});
			BufferedReader procReader = new BufferedReader(
				new InputStreamReader(proc.getInputStream()));
							
			String line = null;
			while ((line = procReader.readLine()) != null){
			//	if( ! line.startsWith("NAME")){								
			//		if( ! line.endsWith("never")){
						connections.add(line);
			//			System.out.println("connectionUpdate(): "+line);
			//		}
			//	}
			}
		} catch (Exception e) {
			System.out.println("connectionUpdate(): " + e.getLocalizedMessage());
		}		
	}

	public synchronized boolean connectionExists(final String ssid, final boolean update){
		if(update)connectionUpdate();
		return connectionExists(ssid);
	}
	
	public synchronized boolean connectionExists(final String ssid){	
		for(int i = 0 ; i < (connections.size()) ; i++)
			if(connections.get(i).startsWith(ssid.trim())) 
				return true;
			
		System.out.println(ssid + " was not found in listing of: " + connections.size());
		return false;
	}
	
	
	/*
	 
	public synchronized void connectionsNever(){	
		try {
					
			Process proc = Runtime.getRuntime().exec(new String[]{"nmcli", "con", "list"});
			BufferedReader procReader = new BufferedReader(
				new InputStreamReader(proc.getInputStream()));
							
			String line = null;
			while ((line = procReader.readLine()) != null){
				line = line.trim();						
				if(line.endsWith("never")){
					System.out.println("connectionsNever: " + line);
					removeConnection(line.substring(0, 8).trim());
					// TODO: magic 26? ssid max value 
				}
			}
		} catch (Exception e) {
			System.out.println("connectionsNever: " + e.getLocalizedMessage());
		}		
	}

	public void removeConnection(final String ssid){
			try {
						
				Process proc = Runtime.getRuntime().exec(new String[]{"nmcli", "con", "delete", "id", ssid});
				BufferedReader procReader = new BufferedReader(
					new InputStreamReader(proc.getInputStream()));
								
				String line = null;
				while ((line = procReader.readLine()) != null)
					System.out.println("removeConnection: " + line);
										
				proc.waitFor();
				
			} catch (Exception e) {
				System.out.println("removeConnection: " + e.getLocalizedMessage());
			}		
	}
		*/
	
	public void changeWIFI(final String ssid, final String password){	
		
		System.out.println("changeWIFI: start password = " + password);
		
		try {
			Runtime.getRuntime().exec(new String[]{"nmcli", "dev", "wifi", "connect", ssid, "password", password});
		} catch (Exception e) { 
			System.out.println("changeWIFI: " + e.getLocalizedMessage()); 
		}		
		
		System.out.println("changeWIFI: end \n");
	}
	
	public void changeWIFI(final String ssid){	
		
	//	System.out.println("changeWIFI: start ssid = " + ssid);
		
		try {
			Runtime.getRuntime().exec(new String[]{"nmcli", "c", "up", "id", ssid});
		} catch (Exception e) { 
			System.out.println("changeWIFI: " + e.getLocalizedMessage());
		}		
				
	//	System.out.println("changeWIFI: end \n");
	}
	/*
	public void startAdhoc(){
		
		// TODO:...............
		System.out.println("..........++.......startAdhoc: start");
		
		if( ! connectionExists(AP_NAME, true)){
			System.out.println("startAdhoc: doesn't have connection named: " + AP_NAME);
			return;
		}
	
		try {
			
			Process proc = Runtime.getRuntime().exec(new String[]{"nmcli", "c", "up", "id", AP_NAME});
			BufferedReader procReader = new BufferedReader(
					new InputStreamReader(proc.getInputStream()));
				
			String line = null;
			while ((line = procReader.readLine()) != null){
				System.out.println("startAdhoc: " + line);
			}
			
			proc.waitFor();
				
		} catch (Exception e) { 
			System.out.println("startAdhoc: " + e.getLocalizedMessage()); 
		}	
		
		System.out.println("startAdhoc: end...........++........");
	}
	*/
/*
	public void stopServer(){	
		try {
			Runtime.getRuntime().exec(new String[]{"java", "-jar", "stop.jar", "&"});
		} catch (Exception e) { 
			System.out.println("stopServer: " + e.getLocalizedMessage()); 
		}		
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
								signalStrength = results[7];
								signalQuality = results[5]; 
								// signalNoise = results[9];
								
								// for(int i = 0 ; i < results.length; i++) if(! results[i].equals("")) System.out.println(i + " -- " + results[i]);
								
								if(signalQuality.endsWith(".")) signalQuality = signalQuality.substring(0, signalQuality.length()-1);
								if(signalStrength.endsWith(".")) signalStrength = signalStrength.substring(0, signalStrength.length()-1);
							}	
						}
						
						proc.waitFor();
						Thread.sleep(FAST_POLL_DELAY_MS); 
						
					} catch (Exception e) {
						System.out.println("getSignalQuality: " + e.getLocalizedMessage());
					}										
				}
			}
		}).start();
	}
	*/
	/*
	public void getWlanAddress(){
		new Thread(new Runnable() {
			@Override
			public void run() {	
				while(true){
					try {
									
						Process proc = Runtime.getRuntime().exec(new String[]{"ifconfig", WLAN});
						BufferedReader procReader = new BufferedReader(
							new InputStreamReader(proc.getInputStream()));
								
						String line = null;
						String addr = null;
						while ((line = procReader.readLine()) != null){
							if(line.contains("inet addr:")) 
								addr = line.substring(line.indexOf("inet addr: ")+21, line.indexOf(" Bcast"));
						}
						
						proc.waitFor();
					
						if(addr==null) wlanAddress = NONE; 
						else if( ! addr.equals(wlanAddress)) wlanAddress = addr;							
					
						Thread.sleep(FAST_POLL_DELAY_MS);
						
					} catch (Exception e) {
						System.out.println("getWlanAddress: " + e.getLocalizedMessage());
					}		
				}
			}
		}).start();
	}
	*/
	
}
