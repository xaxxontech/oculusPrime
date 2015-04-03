package oculusPrime;

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
	
	PowerLogger power = PowerLogger.getRefrence();
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
		
		out.println("<html><head><meta http-equiv=\"refresh\" content=\""+ HTTP_REFRESH_DELAY_SECONDS + "\"></head><body> \n");

		String view = null;	
		try {
			view = request.getParameter("view");
		} catch (Exception e) {
			System.out.println(e.getLocalizedMessage());
		}
		
		if(view != null){
			if(view.equals("ban")){
				out.println(ban.list.toString() + "\n");
				out.println(ban.tail(30) + "\n");
			}
			
			if(view.equals("state")){
				out.println(state.toHTML() + "\n");
			}
			
			if(view.equals("sysout")){
				out.println(Util.tail(30) + "\n");
			}
			
			if(view.equals("power")){
				out.println(power.tail(30) + "\n");
			}
			
			if(view.equals("log")){
				out.println("\nsystem output: <hr>\n");
				out.println(Util.tail(20) + "\n");
				out.println("\n<br />power log: <hr>\n");
				out.println("\n<br />" + power.tail(5) + "\n");
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
