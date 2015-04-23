package oculusPrime;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class StateServlet extends HttpServlet {
	
	static final long serialVersionUID = 1L;	
	static final long HTTP_REFRESH_DELAY_SECONDS = 2;
	
	Settings settings = Settings.getReference();
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
			System.out.println("this service is for developers only, check settings.,");
			out.close();	
			return;
		}
				
		out.println("<html><head><meta http-equiv=\"refresh\" content=\""+ HTTP_REFRESH_DELAY_SECONDS 
				+ "\"></head><body>" + state.toHTML() + "\n </body></html>");
		
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
