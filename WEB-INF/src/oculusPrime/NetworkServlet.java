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
	
	static final long serialVersionUID = 1L;	

	static final double VERSION = new Updater().getCurrentVersion();
	NetworkMonitor monitor = NetworkMonitor.getReference();
	Settings settings = Settings.getReference();
	BanList ban = BanList.getRefrence();
	State state = State.getReference();
	
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
	}

	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}

	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
	
		if( ! ban.knownAddress(request.getRemoteAddr())){
			Util.log("unknown address: danger: "+request.getRemoteAddr(), this);
			response.sendRedirect("/oculusPrime");   
			return;
		}
	
		String action = null;
		String router = null; 
		String password = null;
		
		try {
			
			action = request.getParameter("action");
			router = request.getParameter("router");
			password = request.getParameter("password");
		
		} catch (Exception e) {
			Util.debug("doGet(): " + e.getLocalizedMessage(), this);
		}
			
		if(password != null){
			monitor.changeWIFI(router, password);
			response.sendRedirect("network"); 
			return;
		}
		
		if(action != null && router != null) { 
			if(action.equals("default")){	
				Util.log("set default: " + router, this);
				monitor.setDefault(router.trim());	
				response.sendRedirect("network"); 
				return;
			
			}
		}
			
		if(action.equals("delete")){	
			if(state.equals(values.ssid, router)){
				Util.log("... can't delete if conncted: " + router, this);
				response.sendRedirect("network"); 
				return;
			}
				
			Util.log(".. delete [" + router + "]", this);
			monitor.removeConnection(router.trim());	
			response.sendRedirect("network");  
			return;
		}
			
		
		if(action.equals("connect")){	
			if(monitor.connectionExists(router)){			
				Util.log(request.getServerName()+" connect existing [" + router + "]", this);
				monitor.changeWIFI(router);
				//	response.sendRedirect("network");                    
				//	return;
				}
			
				
			sendLogin(request, response, router);
			return;
		
		}
			
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		out.println("<html><head> \n");
		out.println(toHTML(request.getServerName()+":"+request.getServerPort() + "/oculusPrime/network"));
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
	
	public String toHTML(final String url){
			
		final String[] connections = monitor.getConnections(); 		
		final String[] available = monitor.getAccessPoints();		
		final String setdef  = "<a href=\"http://" + url + "?action=default&router=";
		final String delete  = "<a href=\"http://" + url + "?action=delete&router=";
		final String connect = "<a href=\"http://" + url + "?action=connect&router=";
	
		StringBuffer str = new StringBuffer("<table cellspacing=\"7\" border=\"0\">  \n");
		
		str.append("<tr><td colspan=\"3\"><center> Oculus Prime <br /> Version <b>" + VERSION + "</b></center>\n"); 
		str.append("<tr><td colspan=\"3\"><center> access points </center><hr>\n");
		
		for(int i = 0 ; i < connections.length ; i++) 
			str.append("<tr><td>" + connect + connections[i] + "\">"+ connections[i] +"</a><td>" 
		            + delete + connections[i] + "\"> x </a><td>" 
					+ setdef + connections[i] + "\">set</a></tr>\n");
	
		str.append("<tr><td colspan=\"3\"><center> access points </center><hr>  \n");
		
		for(int i = 0 ; i < available.length ; i++) 
			str.append("<tr><td colspan=\"3\">" + connect + available[i] + "\">" + available[i] + "</a> \n");
	
		str.append("\n</table>\n");
		return str.toString();
	}
}
