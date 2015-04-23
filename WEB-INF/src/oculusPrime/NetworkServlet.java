package oculusPrime;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import oculusPrime.State.values;

public class NetworkServlet extends HttpServlet {
	
	private static final long serialVersionUID = 1L;	
	protected static final long HTTP_REFRESH_DELAY_SECONDS = 3; 	
	NetworkMonitor monitor = NetworkMonitor.getReference();
	State state = oculusPrime.State.getReference();

	
	public void init(ServletConfig config) throws ServletException {		
		super.init(config);
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
		
		if(password != null){
			response.sendRedirect("dashboard"); 
			monitor.changeWIFI(router, password);
			return;
		}
		
		if(action != null){ 
			
			if(action.equals("adhoc")){		
				monitor.startAdhoc();
				Util.log("..... start adhoc called", this);
				Util.delay(5000);
				response.sendRedirect("http://10.42.0.1:" + ":"+request.getServerPort() + "/oculusPrime/network"); 
				return;
			}
			
			if(action.equals("connect")  && (router != null)){	
				if(monitor.connectionExists(router)){
					
					response.sendRedirect("dashboard");                              
					monitor.changeWIFI(router);
					return;
				}
			
				sendLogin(request, response, router);
				return;
			}
		}	
		
		wifiSelection(request, response);
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
				
		final String base = "<a href=\"http://"+request.getServerName()+":"+request.getServerPort() + "/oculusPrime/network";	
		final String adhoc = base + "?action=adhoc\">start adhoc mode</a>";
		final String router = base + "?action=connect&router=";
		
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		out.println("<html><head><meta http-equiv=\"refresh\" content=\""+ HTTP_REFRESH_DELAY_SECONDS + "\"></head><body> \n");
		out.println("<table cellpadding=\"5\" >");
		out.println("\n<tr><td colspan=\"15\"><center><b>available access points </b></center><hr></td></tr>");
		
		if(state.equals(values.ssid, "ap"))  out.println("\n<tr><td colspan=\"15\"><i>ah-Hoc mode enabled</i></td></tr>");
		else out.println("\n<tr><td colspan=\"15\">"+ adhoc +"</td></tr>");
		
		out.println("\n<tr>");
		String[] result = monitor.getAccessPoints();
		for(int i = 0; i < result.length ; i++){
			if( ! state.equals(values.ssid, result[i])) 
				out.println("\n<tr><td>" + router + result[i] + "\">"+ result[i] +"</a>");
		}
		out.println("\n</table>");
		out.println("\n <br /> \n </body></html> \n");
		out.close();	
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
