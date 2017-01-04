package oculusPrime;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import developer.Navigation;
import developer.NavigationUtilities;
import oculusPrime.State.values;
import oculusPrime.commport.PowerLogger;

public class DashboardServlet extends HttpServlet implements Observer {
	
	static final long serialVersionUID = 1L;	
	private static final int MAX_STATE_HISTORY = 40;
	private static final String HTTP_REFRESH_DELAY_SECONDS = "30"; 
	
	static final String restart = "<a href=\"dashboard?action=restart\" title=\"restart application\">";
	static final String reboot = "<a href=\"dashboard?action=reboot\" title=\"reboot linux os\">";
	static final String runroute = "<a href=\"dashboard?action=runroute\">";
	
	static final String viewslinks = 
			"<a href=\"navigationlog/index.html\" target=\"_blank\">navigation log</a>\n"+
			"<a href=\"/oculusPrime/media\" target=\"_blank\">media files</a>" + 
			"<a href=\"dashboard?view=routes\">route stats</a>\n"  +
			"<a href=\"dashboard?view=drive\">drive</a>\n"  +
			"<a href=\"dashboard?view=users\">users</a>\n" +
			"<a href=\"dashboard?view=stdout\">stdout</a>\n" +
			"<a href=\"dashboard?view=history\">history</a>\n" +
			"<a href=\"dashboard?view=state\">state</a>\n"; 
	
	static final String commandlinks = 
			"<a href=\"dashboard?view=gotodock\">return dock</a>\n"  +
			"<a href=\"dashboard?view=cancel\">cancel route</a>\n" +
			"<a href=\"dashboard?action=archivelogs\"> archive log folder</a>" +
			"<a href=\"dashboard?view=deletelogs\">delete log files</a>\n"  +
			"<a href=\"dashboard?action=snapshot\" target=\"_blank\">snaphot</a>\n"+ 
			"<a href=\"dashboard?action=email\">send email</a>\n";
	
	static final double VERSION = new Updater().getCurrentVersion();
	static Vector<String> history = new Vector<String>();
	static Application app = null;
	static Settings settings = null;
	static String httpport = null;
	static BanList ban = null;
	static State state = null;

	String pointslist;
	String delay = "30";
	String estimatedmeters;
	String estimatedseconds; 
	long time;
	
	final String css = getCSS();
	
	public String getCSS(){
		StringBuffer buffer = new StringBuffer();
		try {	
			String line = null;
			FileInputStream filein = new FileInputStream(System.getenv("RED5_HOME") + "/webapps/oculusPrime/dashboard.css");
			BufferedReader reader = new BufferedReader(new InputStreamReader(filein));
			while ((line = reader.readLine()) != null) buffer.append(line + "\n");
			reader.close();
			filein.close();
		} catch (Exception e) {
			Util.log("getCSS(): " + e.getMessage(), this);
		}
		return buffer.toString();
	}
	
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		state = State.getReference();
		httpport = state.get(State.values.httpport);
		settings = Settings.getReference();
		ban = BanList.getRefrence();		
		state.addObserver(this);
	}

	public static void setApp(Application a){app = a;}
	
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}

	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		if( ! settings.getBoolean(ManualSettings.developer.name())){
			Util.log("dangerous.. not in developer mode: "+request.getRemoteAddr(), this);
			response.sendRedirect("/oculusPrime");   
			return;
		}
		
		if( ! ban.knownAddress(request.getRemoteAddr())){
			Util.log("unknown address: sending to login: "+request.getRemoteAddr(), this);
			response.sendRedirect("/oculusPrime");   
			return;
		}
		
		String view = null;	
		String action = null;
		String route = null;
		String move = null;
		
		try {
			view = request.getParameter("view");
			delay = request.getParameter("delay");
			action = request.getParameter("action");
			route = request.getParameter("route");
			move = request.getParameter("move");
		} catch (Exception e) {}
			
		if(delay == null) delay = HTTP_REFRESH_DELAY_SECONDS;
		
		if(move != null){ 
			if(state.equals(values.dockstatus, AutoDock.DOCKED)){ 
				state.set(values.motionenabled, true);
				double distance = 1.0;
				app.driverCallServer(PlayerCommands.forward, String.valueOf(distance));
				Util.delay((long) (distance / state.getDouble(values.odomlinearmpms.name())) + 2000);
			} else app.driverCallServer(PlayerCommands.nudge, move);
		}
		
		if(action != null && app != null){
			
			if(action.equalsIgnoreCase("camon")){
				new Thread(new Runnable() { public void run(){
					app.driverCallServer(PlayerCommands.publish, "camera");
					Util.delay(4000); // TODO: BETTER WAY TO KNOW IF CAMERA IS ON?
					if(Navigation.turnLightOnIfDark()) Util.log("light was turned on because was dark", this);
				}}).start();
			}
				
			if(action.equalsIgnoreCase("camoff")) {
				app.driverCallServer(PlayerCommands.publish, "stop");
				app.driverCallServer(PlayerCommands.spotlight, "0");
			}
			
			if(action.equalsIgnoreCase("resetstats")) app.driverCallServer(PlayerCommands.resetroutedata, route);

			if(action.equalsIgnoreCase("gui")) state.delete(values.guinotify);
			if(action.equalsIgnoreCase("gotowp")) app.driverCallServer(PlayerCommands.gotowaypoint, route);
			if(action.equalsIgnoreCase("startrec")) app.driverCallServer(PlayerCommands.record, "true dashboard");
			if(action.equalsIgnoreCase("stoprec"))  app.driverCallServer(PlayerCommands.record, "false");
			if(action.equalsIgnoreCase("motor")) app.driverCallServer(PlayerCommands.motorsreset, null);
			if(action.equalsIgnoreCase("power")) app.driverCallServer(PlayerCommands.powerreset, null);			
			if(action.equalsIgnoreCase("cancel")) app.driverCallServer(PlayerCommands.cancelroute, null);
			if(action.equalsIgnoreCase("gotodock")) app.driverCallServer(PlayerCommands.gotodock, null);
			if(action.equalsIgnoreCase("startnav")) app.driverCallServer(PlayerCommands.startnav, null);
			if(action.equalsIgnoreCase("stopnav")) app.driverCallServer(PlayerCommands.stopnav, null);
 			if(action.equalsIgnoreCase("deletelogs")) app.driverCallServer(PlayerCommands.deletelogs, null);			
			if(action.equalsIgnoreCase("archivelogs")) app.driverCallServer(PlayerCommands.archivelogs, null);
			if(route != null)if(action.equalsIgnoreCase("runroute")) app.driverCallServer(PlayerCommands.runroute, route);		
			if(action.equalsIgnoreCase("redock")) app.driverCallServer(PlayerCommands.redock, null); // SystemWatchdog.NOFORWARD);	
			if(action.equalsIgnoreCase("debugon")) app.driverCallServer(PlayerCommands.writesetting, ManualSettings.debugenabled.name() + " true");
			if(action.equalsIgnoreCase("debugoff")) app.driverCallServer(PlayerCommands.writesetting, ManualSettings.debugenabled.name() + " false");
			
			if(action.equalsIgnoreCase("email")){
				new Thread(new Runnable() { public void run() {
					StringBuffer text = new StringBuffer();
					text.append("\n\r-- " + new Date() + " --\n");
					text.append(Util.tail(999).replaceAll("<br>", "\n"));
					text.append("\n\r -- state history --\n");
					text.append(getHistory() + "\n\r");
					text.append("\n\r -- state values -- \n");
					text.append(state.toString().replaceAll("<br>", "\n"));	
					text.append("\n\r -- settings --\n");
					text.append(Settings.getReference().toString().replaceAll("<br>", "\n"));
					new SendMail("oculus prime snapshot", text.toString());
					// Util.delay(5000);
					// new SendMail("oculus prime log files", "see attached", new String[]{ Settings.settingsfile, Navigation.navroutesfile.getAbsolutePath() });
				}}).start();
			}  
			
			if(action.equalsIgnoreCase("reboot")){
				new Thread(new Runnable() { public void run(){
					Util.log("reboot called, going down..", this);	
					Util.delay(2000); // redirect before calling.. 
					app.driverCallServer(PlayerCommands.reboot, null);
				}}).start();
			}
	
			if(action.equalsIgnoreCase("restart")){
				new Thread(new Runnable(){ public void run(){
					app.driverCallServer(PlayerCommands.move, "stop");
					Util.log("restart called, going down..", this);
					Util.delay(2000); // redirect before calling.. 
					app.driverCallServer(PlayerCommands.restart, null);
				}}).start();
			}
			
			/*
			if(action.equalsIgnoreCase("save")) {	
				new Thread(new Runnable() { public void run() {
					if( ! new Downloader().FileDownload("http://" + state.get(values.localaddress)  
						+ ":" + httpport + "/oculusPrime/dashboard?action=snapshot", "snapshot_"+ System.currentTimeMillis() +".txt", "log"))
							Util.log("snapshot save failed", this);
				}}).start();
			}
			*/
			
			if(action.equalsIgnoreCase("snapshot")) {
				sendSnap(request, response);
				return;
			}
			
			response.sendRedirect("/oculusPrime/dashboard?delay=" + delay); 
		}
		
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
	
		if(view != null){
			if(view.equalsIgnoreCase("drive")){
				out.println("<html><head><meta http-equiv=\"refresh\" content=\""+ delay + "\"></head><body> \n");
				out.println("<br/>&nbsp;&nbsp;<a href=\"dashboard\">dashboard</a>&nbsp;&nbsp;&nbsp;&nbsp;");
				out.println("nudge: <a href=\"dashboard?view=drive&move=forward\">forward</a>&nbsp;&nbsp;");
				out.println("<a href=\"dashboard?view=drive&move=backward\">backward</a>&nbsp;&nbsp;");
				out.println("<a href=\"dashboard?view=drive&move=left\">left</a>&nbsp;&nbsp;");
				out.println("<a href=\"dashboard?view=drive&move=right\">right</a>&nbsp;&nbsp;&nbsp;&nbsp;");
				out.println("<br/><img src=\"frameGrabHTTP\"><br/>\n");
				out.println("\n</body></html> \n");
				out.close();
			}
			
			if(view.equalsIgnoreCase("users")){	
				out.println("<html><head><meta http-equiv=\"refresh\" content=\""+ delay + "\"></head><body> \n");
				out.println("<a href=\"dashboard\">dashboard</a>&nbsp;&nbsp;<br/>\n");
				out.println(ban + "<br />\n");
				out.println(ban.tail(30) + "\n");
				
				String str = "RTMP users login records:<br>";
				for (int i = 0; i < LoginRecords.list.size(); i++)
					str += i + " " + LoginRecords.list.get(i).toString() + "<br>";
				
				out.println("<br>" + str);
				out.println("\n</body></html> \n");
				out.close();
			}
			
			if(view.equalsIgnoreCase("state")){
				out.println("<html><body> \n");				
				out.println("&nbsp&nbsp&nbsp&nbsp<a href=\"dashboard\">dashboard</a>");
				out.println("&nbsp&nbsp&nbsp&nbsp<a href=\"dashboard?view=history\">history</a><br /><br />\n");
				out.println(toHTML() + "\n");
				out.println("\n</body></html> \n");
				out.close();
			}
			
			if(view.equalsIgnoreCase("routes")){
				out.println("<html><body> \n");				
				out.println("&nbsp&nbsp&nbsp&nbsp<a href=\"dashboard\">dashboard</a>");
				out.println(NavigationUtilities.getRouteStatsHTML() + "\n");
				out.println("\n");
				out.println("\n</body></html> \n");
				out.close();
			}
			
			if(view.equalsIgnoreCase("stdout")){
				out.println("<html><head><meta http-equiv=\"refresh\" content=\""+ delay + "\"></head><body> \n");
				out.println("<a href=\"dashboard\">dashboard</a>&nbsp;&nbsp;  \n" 
						+ new File(Settings.stdout).getAbsolutePath() + "<br /><br />\n");
				out.println(Util.tail(35) + "\n");
				out.println("\n</body></html> \n");
				out.close();
			}
			
			if(view.equalsIgnoreCase("power")){	
				out.println("<html><head><meta http-equiv=\"refresh\" content=\""+ delay + "\"></head><body> \n");
				out.println("<a href=\"dashboard\">dashboard</a>&nbsp;&nbsp; \n"
						+ new File(PowerLogger.powerlog).getAbsolutePath() + "<br /><br />\n");
				out.println(PowerLogger.tail(45) + "\n");
				out.println("\n</body></html> \n");
				out.close();
			}
			
			if(view.equalsIgnoreCase("history")){
				out.println("<html><head><meta http-equiv=\"refresh\" content=\""+ delay + "\"></head><body> \n");
				out.println("<a href=\"dashboard\">dashboard</a>");
				out.println("&nbsp&nbsp&nbsp&nbsp<a href=\"dashboard?view=state\">state</a><br /><br />\n");
				out.println(getHistory().replaceAll("\n", "<br>\n"));
				out.println("\n</body></html> \n");
				out.close();
			}	
		}
		
		// default view 
		out.println("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n");
		out.println("<html>\n<head><meta http-equiv=\"refresh\" content=\""+ delay + "\">\n<title>Oculus Prime</title>\n<style type=\"text/css\">"+ css +"</style></head>\n<body> \n");
		out.println(toDashboard(request.getServerName()+":"+request.getServerPort() + "/oculusPrime/dashboard") + "\n");
		out.println("\n</body></html>\n");
		out.close();	
	}
	
	public String toHTML(){
		StringBuffer str = new StringBuffer("<table style=\" max-width: 700px;\">"); 
		HashMap<String, String> props = state.getState();
		Set<String> keys = props.keySet();
		for(Iterator<String> i = keys.iterator(); i.hasNext();){
			try {
				
				if( !i.hasNext()) break;	
				String key = i.next();
				if(key.equals(values.rosamcl.name())) key = i.next();
				if(key.equals(values.rosglobalpath.name())) key = i.next();
				if(key.equals(values.rosmapinfo.name())) key = i.next();
				if(key.equals(values.rosscan.name())) key = i.next();
				if(key.equals(values.rosmapwaypoints.name())) key = i.next();
				if(key.equals(values.batteryinfo.name())) key = i.next();
				if(key.equals(values.networksinrange.name())) key = i.next();
				if(key.equals(values.rosmapinfo.name())) key = i.next();
				str.append("<tr><td><b>" + key + "</b><td>" + props.get(key) + "</a>");
				
				if( !i.hasNext()) break;
				key = i.next();
				if(key.equals(values.rosamcl.name())) key = i.next();
				if(key.equals(values.rosglobalpath.name())) key = i.next();
				if(key.equals(values.rosmapinfo.name())) key = i.next();
				if(key.equals(values.rosscan.name())) key = i.next();
				if(key.equals(values.rosmapwaypoints.name())) key = i.next();
				if(key.equals(values.batteryinfo.name())) key = i.next();
				if(key.equals(values.networksinrange.name())) key = i.next();
				if(key.equals(values.rosmapinfo.name())) key = i.next();
				str.append("<td><b>" + key + "</b><td>" + props.get(key) + "</a>");
				if( !i.hasNext()) break;
				
			} catch (Exception e) { break; }
		}
		
		Vector<String> points = getAllWaypoints();
		String pnames = "NONE";
		if(points.size() > 0) pnames = points.toString();
		str.append("<tr><td colspan=\"9\"><hr><tr><td colspan=\"9\"><b>wapoints: </b>" + pnames);
		str.append("<tr><td colspan=\"9\"><hr><tr><td colspan=\"9\"><b>null's</b>");
		for (values key : values.values()) if(! props.containsKey(key.name())) str.append(" " + key.name() + " ");
		str.append("</table>\n");
		return str.toString();
	}
	
	public void sendSnap(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("text");
		PrintWriter out = response.getWriter();
		out.println("\n\r-- " + new Date() + " --\n\r");
		out.println(Util.tail(999).replaceAll("<br>", ""));
		out.println("\n\r -- state history --\n\r");
		out.println(getHistory() + "\n\r");
		out.println("\n\r -- state values -- \n\r");
		out.println(state.toString().replaceAll("<br>", ""));	
		out.println("\n\r -- settings --\n\r");
		out.println(Settings.getReference().toString().replaceAll("<br>",  "\n"));
		out.println("\n\r -- route stats -- \n\r");
		out.println(NavigationUtilities.getRouteStats() + "\n");
		out.close();	
	}
	
	public String toDashboard(final String url){
		
		String motor, power;
		if(state.exists(values.powerport)) power = "<a href=\"dashboard?action=power\" >"+state.get(values.powerport)+"</a>";	
		else  power = "<a href=\"dashboard?action=power\" >connect power</a>";	
		if(state.exists(values.motorport)) motor = "<a href=\"dashboard?action=motor\" >"+state.get(values.motorport)+"</a>";
		else motor = "<a href=\"dashboard?action=motor\" >connect motors</a>";
		
		String rec = state.get(values.record);
		if(rec == null) rec = "off";
		else {
			if(rec.startsWith("cam")) rec = "<font color=\"blue\"><b>on</b></font>&nbsp;&nbsp;|&nbsp;&nbsp;<a href=\"dashboard?action=stoprec\" >off</a>";
			else rec = "<a href=\"dashboard?action=startrec\" >on</a>&nbsp;&nbsp;|&nbsp;&nbsp;<a href=\"dashboard?action=stoprec\" >off</a>";
		}
				
		String cam = state.get(values.stream);
		if(cam == null) cam = "<font color=\"blue\"><b>disabled</b>";
		else {
			if(cam.startsWith("cam")) cam = "<font color=\"blue\"><b>on</b></font>&nbsp;&nbsp;|&nbsp;&nbsp;<a href=\"dashboard?action=camoff\" >off</a>"; 
			else cam = "<a href=\"dashboard?action=camon\" >on</a>&nbsp;&nbsp;|&nbsp;&nbsp;<a href=\"dashboard?action=camoff\" >off</a>"; 
		}
		
		String od = "<a href=\"dashboard?action=startnav\" title=\"start ROS navigation \">on</a></font>&nbsp;&nbsp;|&nbsp;&nbsp;off"; 
		if(state.getBoolean(values.odometry)) 
			 od = "<font color=\"blue\"><b>on</b></font>&nbsp;&nbsp;|&nbsp;&nbsp;<a href=\"dashboard?action=stopnav\" title=\"stop ROS navigation \">off</a>";
		
		String debug = "<a href=\"dashboard?action=debugon\">on</a>&nbsp;&nbsp;|&nbsp;&nbsp;off";
		if(settings.getBoolean(ManualSettings.debugenabled)) 
			debug = "<font color=\"blue\"><b>on</b></font>&nbsp;&nbsp;|&nbsp;&nbsp;<a href=\"dashboard?action=debugoff\">off</a>";
				
		if(httpport == null) httpport = state.get(State.values.httpport);
		String dock = "<font color=\"blue\">undocked</font>";
		if(state.equals(values.dockstatus, AutoDock.DOCKED)) dock = "<a href=\"dashboard?action=redock\" title=\"force re-dock the robot\">docked</a>";	
		
		if(!state.getBoolean(values.odometry) || state.equals(values.dockstatus, AutoDock.DOCKED)) 
			dock = "<a href=\"dashboard?action=redock\" title=\"force re-dock the robot\">docked</a>";	

		if(state.equals(values.dockstatus, AutoDock.DOCKED)) 
			dock = "<a href=\"dashboard?action=redock\" title=\"force re-dock the robot\">docked</a>";	
		
		if(state.equals(values.dockstatus, AutoDock.UNDOCKED)) dock = "<a href=\"dashboard?action=redock\">un-docked</a>";	
		if(state.equals(values.dockstatus, AutoDock.DOCKING)) dock = "<font color=\"blue\">docking</font>";
		if(state.equals(values.dockstatus, AutoDock.UNKNOWN)) dock = "<font color=\"blue\">UNKNOWN</font>";
		if(state.getBoolean(values.autodocking)) dock = "<font color=\"blue\">auto-docking</font>";
	
		//------------------- now build HTML buffer ------------------------------------------------------------//  
		StringBuffer str = new StringBuffer("<table cellspacing=\"7\" border=\"1\" style=\"min-width: 700px; max-width: 700px;\">\n");

		// version | views | ssid | rate
		str.append("\n<tr><td bgcolor=\"#c2d6d6\"><b>version <td></b>" + VERSION);
	//	str.append("<td><div class=\"dropdown\"><button class=\"dropbtn\">views</button><div class=\"dropdown-content\">\n" + viewslinks + "\n</div></div>");
		str.append("\n<td bgcolor=\"#c2d6d6\"><b>ssid</b><td><a href=\"http://"+state.get(values.localaddress) +"\" target=\"_blank\">" + state.get(values.ssid)); 
		
		if(delay.equals("5"))  str.append("\n<td bgcolor=\"#c2d6d6\"><b>rate</b><td>" + "5 | <a href=\"dashboard?delay=10\">10</a> | <a href=\"dashboard?delay=30\">30</a>"); 
		if(delay.equals("10")) str.append("\n<td bgcolor=\"#c2d6d6\"><b>rate</b><td>" + "<a href=\"dashboard?delay=5\">5</a> | 10 | <a href=\"dashboard?delay=30\">30</a>"); 
		if(delay.equals("30")) str.append("\n<td bgcolor=\"#c2d6d6\"><b>rate</b><td>" + "<a href=\"dashboard?delay=5\">5</a> | <a href=\"dashboard?delay=10\">10</a> | 30"); 

		// motor | wan | hdd 		
		str.append("\n<tr>");
		str.append("<td bgcolor=\"#c2d6d6\"><b>motor</b><td>" + motor );
		String ext = state.get(values.externaladdress);
		if( ext == null ) str.append("<td bgcolor=\"#c2d6d6\"><b>wan</b><td>disconnected");
		else str.append("<td bgcolor=\"#c2d6d6\"><b>wan</b><td><a href=\"http://"+ ext + ":" + httpport 
				+ "/oculusPrime/" +"\" target=\"_blank\">" + ext + "</a>"); // target=\"_blank\" title=\"go to user interface on external address\"
		str.append( "<td bgcolor=\"#c2d6d6\"><b>hdd</b><td>" + Util.diskFullPercent() + "% used</a></tr> \n"); 
	
		// power | lan | prime 
		str.append("\n<tr>");
		str.append("<td bgcolor=\"#c2d6d6\"><b>power</b><td>" + power );
		str.append("<td bgcolor=\"#c2d6d6\"><b>lan</b><td><a href=\"http://"+state.get(values.localaddress) 
			+"\" target=\"_blank\">" + state.get(values.localaddress) + "</a>"); // title=\"go to network control panel\"
		str.append("<td bgcolor=\"#c2d6d6\"><b>prime</b><td>" + Util.countAllMbytes(".") + " mb</tr> \n");
		
		// dock | battery | streams
		str.append("\n<tr>");
		str.append("<td bgcolor=\"#c2d6d6\"><b>dock</b><td>" + dock);	
		str.append("<td bgcolor=\"#c2d6d6\"><b>battery</b><td><a href=\"dashboard?view=power\">" + state.get(values.batterylife) + "</a>"); 
		str.append("<td bgcolor=\"#c2d6d6\"><b>streams</b><td>" + Util.countAllMbytes(Settings.streamfolder) + "</a> mb</tr> \n" );
	
		// record | booted | archive 
		str.append("\n<tr>");	
		str.append("<td bgcolor=\"#c2d6d6\"><b>record</b><td>" + rec);
		str.append("<td bgcolor=\"#c2d6d6\"><b>booted</b><td>" + reboot + (((System.currentTimeMillis() - state.getLong(values.linuxboot)) / 1000) / 60)+ "</a> mins");
		str.append("<td bgcolor=\"#c2d6d6\"><b>archive</b><td>"+ Util.countMbytes("./log/archive") + " mb</tr> \n" );

		// camera | uptime | frames 
		str.append("\n<tr>");
		str.append("<td bgcolor=\"#c2d6d6\"><b>camera</b><td>" + cam); 
		str.append("<td bgcolor=\"#c2d6d6\"><b>up time</b><td>" + restart +(state.getUpTime()/1000)/60 + "</a> mins ");
		str.append("<td bgcolor=\"#c2d6d6\"><b>frames</b><td>" + Util.countAllMbytes(Settings.framefolder) + " mb</tr> \n" );

		// navigation | cpu | logs
		str.append("\n<tr>");
		str.append("<td bgcolor=\"#c2d6d6\"><b>nav</b><td>" + od);
			
		String cpuvalue; 
		if(state.getBoolean(values.waitingforcpu)) cpuvalue = "<font color=\"blue\"><b>" + state.get(values.cpu) + "%</b>"; 
		else cpuvalue = state.get(values.cpu) + "%"; 
		str.append("<td bgcolor=\"#c2d6d6\"><b>cpu</b><td>" + cpuvalue);
		str.append("<td bgcolor=\"#c2d6d6\"><b>logs</b><td>" + Util.countMbytes(Settings.logfolder) + " mb</tr> \n" );
		
		// debug | telnet | ros
		str.append("\n<tr>");
		str.append("<td bgcolor=\"#c2d6d6\"><b>debug</b><td>" + debug  
			+ "<td bgcolor=\"#c2d6d6\"><b>telnet</b><td>" + state.get(values.telnetusers) 
			+ "<td bgcolor=\"#c2d6d6\"><b>ros</b><td>" + Util.getRosCheck() + "</tr> \n" ); // doesn't work on hidden file? Util.countMbytes(Settings.roslogfolder)
		
		str.append(getActiveRoute());
		
		str.append("\n\n<tr><td bgcolor=\"#c2d6d6\"><b>routes</b><td>"+ getRouteLinks() +" \n");
		String waypoint = state.get(values.roswaypoint);
		if(waypoint == null) waypoint = "not active";
		String drop = "\n<td bgcolor=\"#c2d6d6\"><b>points</b><td><div class=\"dropdown\"><button class=\"dropbtn\">"+waypoint+"</button><div class=\"dropdown-content\">";
		Vector<String> waypointsAll = getAllWaypoints(); 
		if(waypointsAll != null){
			for(int i = 0 ; i < waypointsAll.size() ; i++)
				drop += "\n<a href=\"dashboard?action=gotowp&route="+ waypointsAll.get(i) +"\">" + waypointsAll.get(i) + "</a> ";
		}
		drop += "</div></div>";
		str.append(drop);
		str.append("<td><div class=\"dropdown\"><button class=\"dropbtn\">views</button><div class=\"dropdown-content\">\n" + viewslinks + "\n</div></div>");
		str.append("<td><div class=\"dropdown\"><button class=\"dropbtn\">commands</button><div class=\"dropdown-content\">\n" + commandlinks + "\n</div></div>");
		
		String m = pointslist;
		if(state.getBoolean(values.routeoverdue)) m += " *overdue* "; 		
		if(state.getBoolean(values.waypointbusy)) m += " *waypointbusy* "; 		
		if(state.getBoolean(values.rosgoalcancel)) m += " *ros goal cancel* "; 
		if(m == null) m = "";
		if(m.length() > 0)
			str.append("\n<tr><td bgcolor=\"#c2d6d6\"><b>points</b><td colspan=\"11\">"+ m + "</tr> \n");	

		String msg = state.get(values.guinotify);
		if(msg == null) msg = "";
		else msg += "&nbsp;&nbsp;(<a href=\"dashboard?action=gui\">ignore</a>)";
		if(msg.length() > 1) {
			msg = "<tr><td bgcolor=\"#c2d6d6\"><b>message</b><td colspan=\"11\">" + msg + "</tr> \n";
			str.append(msg);
		}
		
		str.append("\n</table>\n");
		str.append(getTail(12) + "\n"); 
		return str.toString();
	}
	
	private String getRouteLinks(){   
		Vector<String> list = NavigationUtilities.getRoutes();
	//	if(list == null ) return "";
		String rname = state.get(values.navigationroute);
		if(rname == null) rname = "not active";
		String drop = "\n<div class=\"dropdown\"><button class=\"dropbtn\">"+rname+"</button><div class=\"dropdown-content\">";
	//	drop += "<a href=\"dashboard?action=cancel\">cancel</a>";
		for(int i = 0; i < list.size(); i++) drop += "<a href=\"dashboard?action=runroute&route="+list.get(i)+"\">" + list.get(i) + "</a>";
		return drop += "</div></div>";
	}
	
	private String getActiveRoute(){  	
		
		String link = "<tr><td bgcolor=\"#c2d6d6\"><b>next</b><td>";
		String rname = state.get(values.navigationroute);
		String next = "err";
		rname = state.get(values.navigationroute);
		if(rname == null) rname = "xml: " + NavigationUtilities.getActiveRoute();
		time = ((System.currentTimeMillis() - Navigation.routestarttime)/1000);
		next = state.get(values.roswaypoint);
		if(state.equals(values.dockstatus, AutoDock.DOCKED) && !state.getBoolean(values.odometry)){
			if(state.exists(values.nextroutetime)){
				next = ((state.getLong(values.nextroutetime) - System.currentTimeMillis())/1000)+ " sec";
				time = 0;
			}
		}
	
		if(next==null) time = 0;
		link += next += "<td bgcolor=\"#c2d6d6\"><b>meters</b><td>" + Navigation.getRouteMeters() + " | " + estimatedmeters + "<td bgcolor=\"#c2d6d6\"><b>time</b><td>" + time + " | " +  estimatedseconds;
		return link; 
	}
	
	private String getTail(int lines){
		String reply = "\n\n<table cellspacing=\"7\" border=\"0\"> \n";
		reply += Util.tailFormated(lines) + " \n";
		reply += ("\n</table>\n");
		return reply;
	}

	/*
	private String getHistoryHTML(){
		String reply = "\n\n<table style=\"max-width:640px;\" cellspacing=\"2\" border=\"0\"> \n";
		reply += "\n<tr><td colspan=\"11\"><hr></tr> \n";	
		for(int i = 0 ; i < history.size() ; i++) {
			long time = Long.parseLong(history.get(i).substring(0, history.get(i).indexOf(" ")));
			String mesg = history.get(i).substring(history.get(i).indexOf(" "));
			String date =  new Date(time).toString();
			date = date.substring(10, date.length()-8).trim();
			mesg = mesg.replaceAll("=", "<td>&nbsp;&nbsp;&nbsp;&nbsp;");
			double delta = (double)(System.currentTimeMillis() - time) / (double) 1000;
			String unit = " sec ";
			if(delta > 60) { delta = delta / 60; unit = " min "; }
			reply += "\n<tr><td>" + Util.formatFloat(delta, 1) + "<td>" + unit + "<td>&nbsp;&nbsp;" + mesg; 
		}
		return reply + "\n</table>\n";
	}
	*/
	
	private String getHistory(){
		String reply = "";
		for(int i = 0 ; i < history.size() ; i++) {
			long time = Long.parseLong(history.get(i).substring(0, history.get(i).indexOf(" ")));
			String mesg = history.get(i).substring(history.get(i).indexOf(" "));
			String date =  new Date(time).toString();
			date = date.substring(10, date.length()-8).trim();
			double delta = (double)(System.currentTimeMillis() - time) / (double) 1000;
			String unit = " sec ";
			if(delta > 60) { delta = delta / 60; unit = " min "; }
			reply += " " + Util.formatFloat(delta, 1) + "  " + unit + "    " + mesg + "\n"; 
		}
		return reply;
	}
	
	@Override
	public void updated(String key){
		
//		if(state.exists(key)) Util.log("updated: " + key + " " + state.get(key), this);
		
		// only read from file on change 
		if(key.equals(values.navigationroute.name())){
			if(state.exists(values.navigationroute)){
				estimatedmeters = NavigationUtilities.getRouteDistanceEstimateString(state.get(values.navigationroute));
				estimatedseconds = NavigationUtilities.getRouteTimeEstimateString(state.get(values.navigationroute));
				pointslist = NavigationUtilities.getWaypointsForRoute(state.get(values.navigationroute)).toString();
			} else {
				pointslist = "none";	
				estimatedmeters = "0";
				estimatedseconds = "0";
				time = 0;
			}
		}
		
		if(key.equals(values.dockstatus.name())){
			if(state.equals(values.dockstatus, AutoDock.DOCKED)){
				// estimatedmeters = "0";
				// estimatedseconds = "0";
				time = 0;
			}
		}
			
//	if(state.getBoolean(values.routeoverdue)) 
//		state.set(values.guinotify, "route over due: " + NavigationUtilities.getActiveRoute()); 
//		if(key.equals(values.framegrabbusy.name())) return;
//		if(key.equals(values.rosglobalpath.name())) return;
//		if(key.equals(values.rosscan.name())) return;
		
		if(key.equals(values.networksinrange.name())) return;
		if(key.equals(values.batteryinfo.name())) return;
		if(key.equals(values.batterylife.name())) return;		
 		if(key.equals(values.batteryvolts.name())) return; 		
 		if(key.equals(values.cpu.name())) return; 
 		
		// trim size
		if(history.size() > MAX_STATE_HISTORY) history.remove(0);
		if(state.exists(key)) history.add(System.currentTimeMillis() + " " +key + " = " + state.get(key));
	}
	
	public static Vector<String> getAllWaypoints(){
		Vector<String> names = new Vector<String>();
		if( ! state.exists(values.rosmapwaypoints)) return names;	
		String[] points = state.get(values.rosmapwaypoints).split(",");
		for(int i = 0 ; i < points.length ; i++ ){
			try { Double.parseDouble(points[i]); } catch (NumberFormatException e){
				
				String value = points[i].trim(); // .replaceAll("&nbsp;", " ");			
				
//				if(value.contains("&nbsp;")) Util.log("getAllWaypoints(): ..WARNING.. html chars point: " + value);
//				if(names.contains(value)) Util.log("getAllWaypoints(): ..WARNING.. duplicate point: " + value);
				
				if( ! names.contains(value)) names.add(points[i]);
			}
		}
		return names;
	}
}

