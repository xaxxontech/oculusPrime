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
		
		if(action != null){ 
			
			if(action.equals("default")  && (router != null)){	

				monitor.setDefault(router.trim());	
				response.sendRedirect("network"); 
				return;
			
			}
			
			if(action.equals("delete")  && (router != null)){	
				
				if(state.equals(values.ssid, router)){
					Util.log("can't delete if conncted: " + router, this);
					response.sendRedirect("network"); 
					return;
				} 
				
				Util.log(request.getServerName()+" delete [" + router + "]", this);
				monitor.removeConnection(router.trim());	
				response.sendRedirect("network");  
				return;
			}
			
			if(action.equals("connect")  && (router != null)){	
				if(monitor.connectionExists(router)){			
					Util.log(request.getServerName()+" connect existing [" + router + "]", this);
					monitor.changeWIFI(router);
					response.sendRedirect("network");                    
					return;
				}
			
				sendLogin(request, response, router);
				return;
			}
		}	
			
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		out.println("<html><head> \n");
		out.println(toDashboard(request.getServerName()+":"+request.getServerPort() + "/oculusPrime/dashboard") + "\n");
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
	
	public String toDashboard(final String url){
		
		StringBuffer str = new StringBuffer("<table cellspacing=\"10\" border=\"1\">  \n");
		
		String list = "oculus prime <br />version <b>" + VERSION + "</b>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<br /><br />connections <hr> \n";
		String[] ap = monitor.getConnections(); 		
		
		final String delete = "&nbsp;<a href=\"http://" + url + "?action=delete&router=";
		final String router = "<a href=\"http://" + url + "?action=connect&router=";
		for(int i = 0 ; i < ap.length ; i++)
			list += delete + ap[i] + "\">x</a>&nbsp;&nbsp;" + router + ap[i] + "\">" + ap[i] + "</a><br />\n";
		 
		list += "<br />access points&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; <hr>  \n";
		ap = monitor.getAccessPoints();		
		final String pw = "<a href=\"http://" + url + "?action=connect&router=";
		for(int i = 0 ; i < ap.length ; i++) list += (pw + ap[i] + "\">" + ap[i] + "</a><br /> \n");
		str.append("<tr><td>"+ list +"</tr> \n");
		str.append("\n</table>\n");
		return str.toString();
	}

}
