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
	static int i = 0;
	
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		lookupDevice();
	}

	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}

	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		iwlist();
		
		out.println("<html><head> \n");
		out.println(accesspoints.size() + " -- available accesspoints <br><br>");		
		for(int i = 0 ; i < accesspoints.size() ; i++) out.println(accesspoints.get(i) + "<br>");
		out.println("<br /><br />count: " + i++ + "\n</body></html> \n");
		out.close();	
	}
	
	private void iwlist(){
		
		if(wdev==null || !connected) return;
		
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