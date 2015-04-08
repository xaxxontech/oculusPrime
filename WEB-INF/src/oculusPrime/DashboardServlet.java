package oculusPrime;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import oculusPrime.commport.PowerLogger;

public class DashboardServlet extends HttpServlet {
	
	static final long serialVersionUID = 1L;	
	static final long HTTP_REFRESH_DELAY_SECONDS = 2;
	
// 	PowerLogger power = PowerLogger.getRefrence();
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
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		if ( ! settings.getBoolean(ManualSettings.developer.name())){
			out.println("this service is for developers only, check settings..");
			out.close();	
			return;
		}
		
		if(ban.isBanned(request.getRemoteAddr())){
			out.println("this is a banned address: " + ban);
			out.close();	
			return;
		}
		
		out.println("<html><head><meta http-equiv=\"refresh\" content=\""+ HTTP_REFRESH_DELAY_SECONDS + "\"></head><body> \n");

		String view = null;	
		try {
			view = request.getParameter("view");
		} catch (Exception e) {
			System.out.println(e.getLocalizedMessage());
		}
		
		if(view != null){
			if(view.equalsIgnoreCase("ban")){
				out.println(ban + "<br />\n");
				out.println(ban.tail(30) + "\n");
			}
			
			if(view.equalsIgnoreCase("state")){
				out.println(state.toHTML() + "\n");
			}
			
			if(view.equalsIgnoreCase("sysout")){
				out.println(new File(Settings.stdout).getAbsolutePath() + "<br />\n");
				out.println(Util.tail(30) + "\n");
			}
			
			if(view.equalsIgnoreCase("power")){	
				out.println(new File(PowerLogger.powerlog).getAbsolutePath() + "<br />\n");
				out.println(PowerLogger.tail(30) + "\n");
			}
			
			if(view.equalsIgnoreCase("ros")){
				out.println(state.rosDashboard() + "\n");
			}
			
			if(view.equalsIgnoreCase("log")){
				out.println("\nsystem output: <hr>\n");
				out.println(Util.tail(15) + "\n");
				out.println("\n<br />power log: <hr>\n");
				out.println("\n" + PowerLogger.tail(10) + "\n");
				out.println("\n<br />banned addresses: " +  ban + "<hr>\n");
				out.println("\n" + ban.tail(5) + "\n");
			}
		}
		
		// default
		else out.println(state.toDashboard() + "\n");
		
		out.println("\n</body></html> \n");
		out.close();	
	}
	
	/*
	public String toHTML(){	
		Properties props = state.getProperties();
		StringBuffer str = new StringBuffer("\n<table>");
		Set<String> keys = props.keySet();
		for(Iterator<String> i = keys.iterator(); i.hasNext(); ){
			String key = "\n<tr><td>" + i.next() + "<td>";
			str.append(key + props.get(key) + "</tr>");
		}
		str.append("</table>\n");
		return str.toString();
	}
	*/


}
