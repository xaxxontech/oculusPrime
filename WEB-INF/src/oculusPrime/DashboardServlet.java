package oculusPrime;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.Vector;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import oculusPrime.State.values;
import oculusPrime.commport.PowerLogger;

public class DashboardServlet extends HttpServlet implements Observer {
	
	static final long serialVersionUID = 1L;	
	static final String HTTP_REFRESH_DELAY_SECONDS = "5";
	static double VERSION = new Updater().getCurrentVersion();
	static String httpport = null;
	static Settings settings = null; ;
	static BanList ban = null;
	static State state = null;
	static Vector<String> history;
	
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		history = new Vector<String>();
		state = State.getReference();
		httpport = state.get(State.values.httpport);
		settings = Settings.getReference();
		ban = BanList.getRefrence();
		state.addObserver(this);
	}

	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}

	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
	
		if( ! ban.knownAddress(request.getRemoteAddr())){
			Util.log("unknown address: sending to login: "+request.getRemoteAddr(), this);
			response.sendRedirect("/oculusPrime");   
			return;
		}
		
		String view = null;	
		String delay = null;	
		String action = null;
		
		try {
			view = request.getParameter("view");
			delay = request.getParameter("delay");
			action = request.getParameter("action");
		} catch (Exception e) {}
			
		if(delay == null) delay = HTTP_REFRESH_DELAY_SECONDS;
		
		if(action != null){
			
			if(action.equalsIgnoreCase("reboot")) Util.callReboot();
			
			if(action.equalsIgnoreCase("restart")) Util.callRestart("dashboard command");
			
		//	if(action.equalsIgnoreCase("frames")) Util.truncFrames()
		//	if(action.equalsIgnoreCase("trunc")) Util.manageLogs();
			
			response.sendRedirect("/oculusPrime/dashboard"); 
		}
	
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		out.println("<html><head><meta http-equiv=\"refresh\" content=\""+ delay + "\"></head><body> \n");
	
		if(view != null){
		
			if(view.equalsIgnoreCase("ban")){
				out.println(ban + "<br />\n");
				out.println(ban.tail(30) + "\n");
				out.println("\n</body></html> \n");
				out.close();
			}
			
			if(view.equalsIgnoreCase("state")){
				out.println(state.toHTML() + "\n");
				out.println("\n</body></html> \n");
				out.close();
			}
			
			if(view.equalsIgnoreCase("sysout")){
				out.println(new File(Settings.stdout).getAbsolutePath() + "<br />\n");
				out.println(Util.tail(40) + "\n");
				out.println("\n</body></html> \n");
				out.close();
			}
			
			if(view.equalsIgnoreCase("power")){	
				out.println(new File(PowerLogger.powerlog).getAbsolutePath() + "<br />\n");
				out.println(PowerLogger.tail(40) + "\n");
				out.println("\n</body></html> \n");
				out.close();
			}
			
			if(view.equalsIgnoreCase("ros")){
				out.println(rosDashboard() + "\n");
				out.println("\n</body></html> \n");
				out.close();
			}
			
			if(view.equalsIgnoreCase("log")){
				out.println("\nsystem output: <hr>\n");
				out.println(Util.tail(20) + "\n");
				out.println("\n<br />power log: <hr>\n");
				out.println("\n" + PowerLogger.tail(5) + "\n");
				out.println("\n<br />" +  ban + "<hr>\n");
				out.println("\n" + ban.tail(7) + "\n");
				out.println("\n</body></html> \n");
				out.close();
			}
		}
		
		// default view 
		out.println(toDashboard(request.getServerName()+":"+request.getServerPort() + "/oculusPrime/dashboard") + "\n");
		out.println("\n</body></html> \n");
		out.close();	
	}
	
	public String toTableHTML(){
		StringBuffer str = new StringBuffer("<table cellspacing=\"10\" border=\"2\"> \n");
		
		str.append("<tr>" 
				+ "<td><b>distanceangle</b><td>" + state.get(values.distanceangle)
				+ "<td><b>direction</b><td>" + state.get(values.direction)
				+ "<td><b>odometry</b><td>" + state.get(values.odometry) 
				+ "</tr> \n");
		
		str.append("<tr>" 
				+ "<td><b>distanceanglettl</b><td>" + state.get(values.distanceanglettl) 
				+ "<td><b>stopbetweenmoves</b><td>" + state.get(values.stopbetweenmoves) 
				+ "<td><b>odometrybroadcast</b><td>" + state.get(values.odometrybroadcast) 
				+ "<td><b>odomturndpms</b><td>" + state.get(values.odomturndpms) 
				+ "</tr> \n");
		
		str.append("<tr>" 
				+ "<td><b>odomturnpwm</b><td>" + state.get(values.odomturnpwm) 
				+ "<td><b>odomupdated</b><td>" + state.get(values.odomupdated) 
				+ "<td><b>odomlinearmpms</b><td>" + state.get(values.odomlinearmpms) 
				+ "<td><b>odomlinearpwm</b><td>" + state.get(values.odomlinearpwm) 
				+ "</tr> \n");
		
		str.append("<tr>"
				+ "<td><b>rosmapinfo</b><td colspan=\"7\">" + state.get(values.rosmapinfo) 
				+ "</tr> \n");
			
		str.append("<tr><td><b>roscurrentgoal</b><td>" + state.get(values.roscurrentgoal) 
				+ "<td><b>rosmapupdated</b><td>" + state.get(values.rosmapupdated) 
				+ "<td><b>navsystemstatus</b><td>" + state.get(values.navsystemstatus)
				+ "</tr> \n");
		
		str.append("<tr>" 
				+ "<td><b>rossetgoal</b><td>" + state.get(values.rossetgoal) 
				+ "<td><b>rosgoalstatus</b><td>" + state.get(values.rosgoalstatus)
				+ "<td><b>rosgoalcancel</b><td>" + state.get(values.rosgoalcancel) 
				+ "<td><b>navigationroute</b><td>" + state.get(values.navigationroute)
				+ "</tr> \n");
		
		str.append("<tr>" 
				+ "<td><b>rosinitialpose</b><td>" + state.get(values.rosinitialpose) 
				+ "<td><b>navigationrouteid</b><td>" + state.get(values.navigationrouteid) 
				+ "</tr> \n");
		
		str.append("<tr><td><b>rosmapwaypoints</b><td colspan=\"7\">" + state.get(values.rosmapwaypoints) );
		
		str.append("<tr><td><b>rosglobalpath</b><td colspan=\"10\">" + state.get(values.rosglobalpath) + "</tr> \n");
				
		str.append("\n</table>\n");
		return str.toString();
	}
	
	public String rosDashboard(){	
		StringBuffer str = new StringBuffer("<table cellspacing=\"5\" border=\"1\"> \n");
		
		str.append("<tr>" 
				+ "<td><b>distanceangle</b><td>" + state.get(values.distanceangle)
				+ "<td><b>direction</b><td>" + state.get(values.direction)
				+ "<td><b>odometry</b><td>" + state.get(values.odometry) 
				+ "</tr> \n");
		
		str.append("<tr>" 
				+ "<td><b>distanceanglettl</b><td>" + state.get(values.distanceanglettl) 
				+ "<td><b>stopbetweenmoves</b><td>" + state.get(values.stopbetweenmoves) 
				+ "<td><b>odometrybroadcast</b><td>" + state.get(values.odometrybroadcast) 
				+ "<td><b>odomturndpms</b><td>" + state.get(values.odomturndpms) 
				+ "</tr> \n");
		
		str.append("<tr>" 
				+ "<td><b>odomturnpwm</b><td>" + state.get(values.odomturnpwm) 
				+ "<td><b>odomupdated</b><td>" + state.get(values.odomupdated) 
				+ "<td><b>odomlinearmpms</b><td>" + state.get(values.odomlinearmpms) 
				+ "<td><b>odomlinearpwm</b><td>" + state.get(values.odomlinearpwm) 
				+ "</tr> \n");
		
		str.append("<tr>"
				+ "<td><b>rosmapinfo</b><td colspan=\"7\">" + state.get(values.rosmapinfo) 
				+ "</tr> \n");
			
		str.append("<tr><td><b>roscurrentgoal</b><td>" + state.get(values.roscurrentgoal) 
				+ "<td><b>rosmapupdated</b><td>" + state.get(values.rosmapupdated) 
				+ "<td><b>navsystemstatus</b><td>" + state.get(values.navsystemstatus)
				+ "</tr> \n");
		
		str.append("<tr>" 
				+ "<td><b>rossetgoal</b><td>" + state.get(values.rossetgoal) 
				+ "<td><b>rosgoalstatus</b><td>" + state.get(values.rosgoalstatus)
				+ "<td><b>rosgoalcancel</b><td>" + state.get(values.rosgoalcancel) 
				+ "<td><b>navigationroute</b><td>" + state.get(values.navigationroute)
				+ "</tr> \n");
		
		str.append("<tr>" 
				+ "<td><b>rosinitialpose</b><td>" + state.get(values.rosinitialpose) 
				+ "<td><b>navigationrouteid</b><td>" + state.get(values.navigationrouteid) 
				+ "</tr> \n");
		
		str.append("<tr><td><b>rosmapwaypoints</b><td colspan=\"7\">" + state.get(values.rosmapwaypoints) );
		str.append("<tr><td><b>rosglobalpath</b><td colspan=\"10\">" + state.get(values.rosglobalpath) + "</tr> \n");
		str.append("\n</table>\n");
		return str.toString();
	}
	
	public String toDashboard(final String url){
		
		if(httpport == null) httpport = state.get(State.values.httpport);
		
		StringBuffer str = new StringBuffer("<table cellspacing=\"0\" style=\"max-width:640px;\" border=\"0\"> \n");
		
		str.append("\n<tr><td colspan=\"11\"><b>v" + VERSION + "</b>&nbsp;&nbsp;" + Util.getJettyStatus() + "</tr> \n");
		str.append("\n<tr><td colspan=\"11\"><hr></tr> \n");
			
		str.append("<tr><td><b>lan</b>&nbsp;&nbsp;<td><a href=\"http://"+state.get(values.localaddress) +"\" target=\"_blank\" \">" 
				+ state.get(values.localaddress) + "</a>&nbsp;&nbsp;&nbsp;&nbsp;");
		
		String ext = state.get(values.externaladdress);
		if( ext == null ) str.append("<td><b>wan</b><td>disconnected");
		else str.append("<td><b>wan</b>&nbsp;&nbsp;<td><a href=\"http://"+ ext + ":" + httpport + "/oculusPrime" +"\" target=\"_blank\" \">" 
				+ ext + "</a>&nbsp;&nbsp;&nbsp;&nbsp;");

		str.append("<td><b>telnet</b><td>" + state.get(values.telnetusers) + " clients </tr> \n");
		
	//	final String trunc = "<a href=\"dashboard?action=trunc\">";
	//	final String frames = "<a href=\"dashboard?action=frames\">";
		final String restart = "<a href=\"dashboard?action=restart\">";
		final String reboot = "<a href=\"dashboard?action=reboot\">";
		
		str.append("<tr><td><b>frames</b><td>" + Util.countFrameGrabs()  
				+ "<td><b>logs</b><td>" + Util.getLogSize() 
				+ "<td><b>cpu</b><td>" + state.get(values.cpu) + "% &nbsp;&nbsp;" + restart + "</tr> \n");
	
		str.append("<tr><td><b>motor</b><td>" + state.get(values.motorport) + "&nbsp;&nbsp;&nbsp;&nbsp;"
				+ "<td><b>linux</b>&nbsp;&nbsp;<td>" + reboot + (((System.currentTimeMillis() - state.getLong(values.linuxboot)) / 1000) / 60)+ " mins</a>"
				+ "<td><b>life</b>&nbsp;&nbsp;<td>" + state.get(values.batterylife) + "</tr> \n");
				
		str.append("<tr><td><b>power</b><td>" + state.get(values.powerport) + "&nbsp;&nbsp;&nbsp;&nbsp;"
				+ "<td><b>java</b>&nbsp;&nbsp;<td>" + restart + (state.getUpTime()/1000)/60  + " mins</a>"
				+ "<td><b>volts</b>&nbsp;&nbsp;<td>" + state.get(values.batteryvolts) + "</tr> \n");
	
	//	str.append("\n<tr><td colspan=\"11\"><hr></tr> \n");
	//	str.append(getStatustring());
		str.append("<tr><td colspan=\"11\"><hr></tr> \n");	
		str.append("\n<tr><td colspan=\"11\">" + Util.tailShort(10) + "</tr> \n");
		str.append("\n<tr><td colspan=\"11\"><hr></tr> \n");	
		str.append("\n</table>\n");
		str.append(getHTML() + "\n");
		return str.toString();
	}
	
	//  private String getStatustring(){
	//	String hr = "<tr><td colspan=\"11\"><hr></tr> \n";	
	//	String route = state.get(values.navigationroute);
	//	String ros = "\n<tr><td colspan=\"11\">next route: ";
	//	String line = "";
		
	// 	if(state.exists(values.nextroutetime)) ros += new Date(state.getLong(values.nextroutetime)).toString();
	//	if(route != null) line += " route: " + route;
		
	//	if(state.equals(values.navsystemstatus, "running")) line = ros + " running </tr> \n";	
		
	//	if(state.equals(values.navsystemstatus, "stopped")) line = ros + " stopped </tr> \n";
		
	private String getHTML(){
		String reply = "\n\n<table style=\"max-width:640px;\" cellspacing=\"1\" border=\"0\"> \n";
		for(int i = 0 ; i < history.size() ; i++) {
			
			long time = Long.parseLong(history.get(i).substring(0, history.get(i).indexOf(" ")));
			String mesg = history.get(i).substring(history.get(i).indexOf(" "));
			String date =  new Date(time).toString();
			date = date.substring(10, date.length()-8).trim();
			mesg = mesg.replaceAll("=", "<td>&nbsp;&nbsp;&nbsp;&nbsp;");
			double delta = (double)(System.currentTimeMillis() - time) / (double) 1000;
			String unit = " sec ";
			if(delta > 60) { delta = delta / 60; unit = " min "; }
			reply += "\n<tr><td>" + date + "&nbsp;&nbsp;&nbsp;&nbsp;<td>" + Util.formatFloat(delta) + unit + "<td>&nbsp;&nbsp;&nbsp;&nbsp;" + mesg; 
			
		}
		return reply + "\n</table>\n";
	}

	@Override
	public void updated(String key) {
		if(key.equals(values.batteryinfo.name())) return;
		if(key.equals(values.batterylife.name())) return;
		if(key.equals(values.batteryvolts.name())) return;
		if(key.equals(values.framegrabbusy.name())) return;
		if(key.equals(values.rosglobalpath.name())) return;
		if(key.equals(values.rosscan.name())) return;
// 		if(key.equals(values.cpu.name())) return;
		
		if(history.size() > 10) history.remove(0);
		if(state.exists(key)) history.add(System.currentTimeMillis() + " " +key + " = " + state.get(key));
	}
}
