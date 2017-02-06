package oculusPrime;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
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
//	private static final int MAX_STATE_HISTORY = 40;
	private static final String HTTP_REFRESH_DELAY_SECONDS = "30"; 
	
	static final String runroute = "<a href=\"dashboard?action=runroute\">";
	
	static final String viewslinks = 
			"<a href=\"navigationlog/index.html\" target=\"_blank\">navigation log</a>\n"+
			"<a href=\"/oculusPrime/media\" target=\"_blank\">media files</a>" + 
			"<a href=\"dashboard?view=routes\">route stats</a>\n"  +
	//		"<a href=\"dashboard?view=drive\">drive</a>\n"  +
			"<a href=\"dashboard?action=snapshot\" target=\"_blank\">snaphot</a>\n"+ 
			"<a href=\"dashboard?view=users\">users</a>\n" +
			"<a href=\"dashboard?view=stdout\">stdout</a>\n" +
			"<a href=\"dashboard?view=history\">history</a>\n" +
			"<a href=\"dashboard?view=state\">state</a>\n"; 
	
	static final String commandlinks = 
			"<a href=\"dashboard?action=gotodock\">return dock</a>\n"  +
			"<a href=\"dashboard?action=cancel\">cancel route</a>\n" +
			"<a href=\"dashboard?action=deletelogs\">delete log files</a>\n"  +
			"<a href=\"dashboard?action=trunc\">truncate media</a>" +
			"<a href=\"dashboard?action=archivelogs\">archive log folder</a>" +
			"<a href=\"dashboard?action=reboot\">reboot linux</a>" +
			"<a href=\"dashboard?action=restart\">restart java</a>" +
			"<a href=\"dashboard?action=email\">send email</a>\n";
	
	static final double VERSION = new Updater().getCurrentVersion();
//	static Vector<String> history = new Vector<String>();
	static Settings settings = Settings.getReference();
	static BanList ban = BanList.getRefrence();	
	static State state = State.getReference();
	static Application app = null;
	static String httpport = null;
	static Vector<String> pointslist;
	String delay = "30";
	String estimatedmeters;
	String estimatedseconds; 
	long time;
	

	
	
	final String css = getCSS();
	final int restarts = settings.getInteger(ManualSettings.restarted);
	
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

	public class PID {
		
		String logFile, id, pyFile = null;
		
		PID (String id, String pyFile, String log ){
			this.pyFile = pyFile;
			this.logFile = log;
			this.id = id;
		}
		
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
		
		String action = null;
		String route = null;
		String view = null;	
		String move = null;
		String pid = null;
		
		try {
			view = request.getParameter("view");
			delay = request.getParameter("delay");
			action = request.getParameter("action");
			route = request.getParameter("route");
			move = request.getParameter("move");
			pid = request.getParameter("pid");
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
			
			if( route != null ){
				
//				Util.debug("......doGet(): route: " + route, this);
//				if(action.equalsIgnoreCase("resetstats")) app.driverCallServer(PlayerCommands.resetroutedata, route);
				if(action.equalsIgnoreCase("gotowp")) app.driverCallServer(PlayerCommands.gotowaypoint, route);
				if(action.equalsIgnoreCase("runroute")) app.driverCallServer(PlayerCommands.runroute, route);		
			}
			
			if(action.equalsIgnoreCase("cancel")) {
				
//				Util.debug("......doGet(): cancel route: " + state.get(values.navigationroute), this);
				
				app.driverCallServer(PlayerCommands.move, "stop");
				app.driverCallServer(PlayerCommands.cancelroute, null);
				Util.delay(3000);
				app.driverCallServer(PlayerCommands.gotodock, null);
			}

			if(action.equalsIgnoreCase("kill")){
//				Util.debug("kill " + pid);
				Util.systemCall("kill -SIGINT " + pid);	
			}
			
			if(action.equalsIgnoreCase("script")){
//				Util.debug("python telnet_scripts/" + pid);
				Util.systemCall("python telnet_scripts/" + pid);	
			}
			
			if(action.equalsIgnoreCase("debugon"))     app.driverCallServer(PlayerCommands.writesetting, ManualSettings.debugenabled.name() + " true");
			if(action.equalsIgnoreCase("debugoff"))    app.driverCallServer(PlayerCommands.writesetting, ManualSettings.debugenabled.name() + " false");
			if(action.equalsIgnoreCase("startrec"))    app.driverCallServer(PlayerCommands.record, "true dashboard");
			if(action.equalsIgnoreCase("stoprec"))     app.driverCallServer(PlayerCommands.record, "false");
			if(action.equalsIgnoreCase("motor"))       app.driverCallServer(PlayerCommands.motorsreset, null);
			if(action.equalsIgnoreCase("power"))       app.driverCallServer(PlayerCommands.powerreset, null);			
			if(action.equalsIgnoreCase("gotodock"))    app.driverCallServer(PlayerCommands.gotodock, null);
			if(action.equalsIgnoreCase("startnav"))    app.driverCallServer(PlayerCommands.startnav, null);
			if(action.equalsIgnoreCase("stopnav"))     app.driverCallServer(PlayerCommands.stopnav, null);
			if(action.equalsIgnoreCase("redock"))      app.driverCallServer(PlayerCommands.redock, null); 
 			if(action.equalsIgnoreCase("deletelogs"))  app.driverCallServer(PlayerCommands.deletelogs, null);			
			if(action.equalsIgnoreCase("archivelogs")) app.driverCallServer(PlayerCommands.archivelogs, null);
			
			if(action.equalsIgnoreCase("trunclogs")){
				Util.log(".... trunc logs stub ....");
				// Util.truncStaleNavigationFiles();
				Util.truncStaleAudioVideo();
				Util.truncStaleFrames();
			}
					
			if(action.equalsIgnoreCase("gui"))   state.delete(values.guinotify);
			if(action.equalsIgnoreCase("email")) sendEmail();

			if(action.equalsIgnoreCase("reboot")){
				new Thread(new Runnable() { public void run(){
					Util.log("reboot called, going down..", this);	
					Util.appendUserMessage("reboot called, going down..");
					Util.delay(3000); // redirect before calling.. 
					app.driverCallServer(PlayerCommands.reboot, null);
				}}).start();
			}
	
			if(action.equalsIgnoreCase("restart")){
				new Thread(new Runnable(){ public void run(){
			//		app.driverCallServer(PlayerCommands.move, "stop");
					Util.log("restart called, going down..", this);
					Util.appendUserMessage("restart called, going down..");
					Util.delay(2000); // redirect before calling.. 
					app.driverCallServer(PlayerCommands.restart, null);
				}}).start();
			}
			
			if(action.equalsIgnoreCase("snapshot")) {
				sendSnap(request, response);
				return;
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
			
			response.sendRedirect("/oculusPrime/dashboard?delay=" + delay); 
		}
			 
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
	
		/* add mobile css 
		 if(request.getHeader("User-Agent").indexOf("Mobile") != -1) {
			    //you're in mobile land
			 Util.log("you're in mobile land");
		//	 response.sendRedirect("/oculusPrime/dashboard?view=drive"); 
			 
			// default view 
				out.println("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n");
				out.println("<html>\n<head><meta http-equiv=\"refresh\" content=\"30\">\n<title>Oculus Prime</title>\n<style type=\"text/css\">"+ css +"</style></head>\n<body> \n");
			//	out.println(toDashboard(request.getServerName()+":"+request.getServerPort() + "/oculusPrime/dashboard") + "\n");
				out.println("\n  mobile view here \n");

				out.println("\n</body></html>\n");
				out.close();	
			  } */ 
	
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
				out.println("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n");
				out.println("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1252\"><title>Oculus Prime Route Statistics</title> \n");
				out.println("<style type=\"text/css\">");
				out.println("body, p, ol, ul {font-family: verdana, arial, helvetica, sans-serif; font-size: 16px;}");
				out.println("th, td { text-align: left; padding: 12px; }");
				out.println("tr:nth-child(even){background-color: #f2f2f2}");
				out.println("th { background-color: #4CAF50; color: white; }");
				out.println("</style><html><body>\n");
				out.println("\n");	
//				out.println("&nbsp&nbsp&nbsp&nbsp<a href=\"dashboard\">back to dashboard</a></br></br>");
				out.println(NavigationUtilities.getRouteStatsHTML() + "\n");
				out.println("\n</body></html> \n");
				out.close();
			}
			
			if(view.equalsIgnoreCase("stdout")){
				out.println("<html><head><meta http-equiv=\"refresh\" content=\""+ delay + "\"></head><body> \n");
				out.println("<a href=\"dashboard\">dashboard</a>\n" + new File(Settings.stdout).getAbsolutePath() + "<br /><br />\n");
				out.println(Util.tail(35) + "\n");
				out.println("\n</body></html> \n");
				out.close();
			}
			
			if(view.equalsIgnoreCase("power")){	
				out.println("<html><head><meta http-equiv=\"refresh\" content=\""+ delay + "\"></head><body> \n");
				out.println("<a href=\"dashboard\">dashboard</a>\n"+ new File(PowerLogger.powerlog).getAbsolutePath() + "<br /><br />\n");
				out.println(PowerLogger.tail(45) + "\n");
				out.println("\n</body></html> \n");
				out.close();
			}

			/*
			if(view.equalsIgnoreCase("history")){
				out.println("<html><head><meta http-equiv=\"refresh\" content=\""+ delay + "\"></head><body> \n");
				out.println("<a href=\"dashboard\">dashboard</a>");
				out.println("&nbsp&nbsp&nbsp&nbsp<a href=\"dashboard?view=state\">state</a><br /><br />\n");
				out.println(getHistory().replaceAll("\n", "<br>\n"));
				out.println("\n</body></html> \n");
				out.close();
			}
			*/	
		}
		
		// default view 
		out.println("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n");
		out.println("<html>\n<head><meta http-equiv=\"refresh\" content=\""+ delay + "\">\n<title>Oculus Prime</title>\n<style type=\"text/css\">"+ css +"</style></head>\n<body> \n");
		out.println(toDashboard(request.getServerName()+":"+request.getServerPort() + "/oculusPrime/dashboard") + "\n");
		out.println("\n</body></html>\n");
		out.close();	
	}
	
	public void sendEmail(){
		new Thread(new Runnable() { public void run() {
			StringBuffer text = new StringBuffer();
			text.append("\n\r-- " + new Date() + " --\n");
			text.append(Util.tail(999).replaceAll("<br>", "\n"));
//			text.append("\n\r -- state history --\n");
//			text.append(getHistory() + "\n\r");
			text.append("\n\r -- state values -- \n");
			text.append(state.toString().replaceAll("<br>", "\n"));	
			text.append("\n\r -- battery --\n");
			text.append(PowerLogger.tail(45) + "\n");
			text.append("\n\r -- settings --\n");
			text.append(Settings.getReference().toString().replaceAll("<br>", "\n"));
			new SendMail("oculus prime snapshot", text.toString());
			// Util.delay(5000);
			// new SendMail("oculus prime log files", "see attached", new String[]{ Settings.settingsfile, Navigation.navroutesfile.getAbsolutePath() });
		}}).start();
	}
	
	public String toHTML(){
		StringBuffer str = new StringBuffer("<table>"); 
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
				str.append("<tr><td>" + key + "<td>" + props.get(key) + "</a>");
				
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
				str.append("<td>" + key + "<td>" + props.get(key) + "</a>");
				if( !i.hasNext()) break;
				
			} catch (Exception e) { break; }
		}
		
		Vector<String> points = getAllWaypoints();
		String pnames = "NONE";
		if(points.size() > 0) pnames = points.toString();
		str.append("<tr><td colspan=\"9\"><hr><tr><td colspan=\"9\">wapoints: " + pnames);
		str.append("<tr><td colspan=\"9\"><hr><tr><td colspan=\"9\">null's");
		for (values key : values.values()) if(! props.containsKey(key.name())) str.append(" " + key.name() + " ");
		str.append("</table>\n");
		return str.toString();
	}
	
	public void sendSnap(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("text");
		PrintWriter out = response.getWriter();
		out.println("\n\r-- " + new Date() + " --\n\r");
		out.println(Util.tail(999).replaceAll("<br>", "\n"));
//		out.println("\n\r -- state history --\n\r");
//		out.println(getHistory() + "\n\r");
		out.println("\n\r -- state values -- \n\r");
		out.println(state.toString().replaceAll("<br>", "\n"));	
		out.println("\n\r -- settings --\n\r");
		out.println(Settings.getReference().toString().replaceAll("<br>",  "\n"));
		out.println("\n\r -- route stats -- \n\r");
		out.println(NavigationUtilities.getRouteStats() + "\n");
		out.println("\n\r -- batter info -- \n\r");
		out.println(PowerLogger.tail(99).replaceAll("<br>",  "\n") + "\n");
		out.close();	
	}
	
	public String toDashboard(final String url){
		
		String motor, power;
		if(state.exists(values.powerport)) power = "<a href=\"dashboard?action=power\" >"+state.get(values.powerport)+"</a>";	
		else  power = "<a href=\"dashboard?action=power\" >connect power</a>";	
		if(state.exists(values.motorport)) motor = "<a href=\"dashboard?action=motor\" >"+state.get(values.motorport)+"</a>";
		else motor = "<a href=\"dashboard?action=motor\" >connect motors</a>";
		
		String rec = state.get(values.record);
		if(rec == null) rec = "<td class='menu'>record<td class='off'><a href=\"dashboard?action=startrec\">&nbsp;&nbsp;on</a>&nbsp;&nbsp;|&nbsp;&nbsp;<a href=\"dashboard?action=stoprec\">off</a>";
		else {
			if(rec.contains("cam")){
				rec = "<td class='menu'>record<td class='on'><a href=\"dashboard?action=startrec\">&nbsp;&nbsp;on</a>&nbsp;&nbsp;|&nbsp;&nbsp;<a href=\"dashboard?action=stoprec\">off</a>";
			} else {
				rec = "<td class='menu'>record<td class='off'><a href=\"dashboard?action=startrec\">&nbsp;&nbsp;on</a>&nbsp;&nbsp;|&nbsp;&nbsp;<a href=\"dashboard?action=stoprec\">off</a>";
			}
		}
				
		String cam = state.get(values.stream);
		if(cam == null) cam = ""; 
		else {
			if(cam.contains("cam")) {
				cam = "<td class='menu'>camera<td class='on'><a href=\"dashboard?action=camon\">&nbsp;&nbsp;on</a>&nbsp;&nbsp;|&nbsp;&nbsp;<a href=\"dashboard?action=camoff\">off</a>"; 
			} else {
				cam = "<td class='menu'>camera<td class='off'><a href=\"dashboard?action=camon\">&nbsp;&nbsp;on</a>&nbsp;&nbsp;|&nbsp;&nbsp;<a href=\"dashboard?action=camoff\">off</a>"; 
			}
		}
		
		String od = "<td class='menu'>nav<td class='off'><a href=\"dashboard?action=startnav\"\">&nbsp;&nbsp;on&nbsp;&nbsp;</a>|&nbsp;&nbsp;off"; 
		if(state.getBoolean(values.odometry)) 
			 od = "<td class='menu'>nav<td class='on'>&nbsp;&nbsp;on&nbsp;&nbsp;|&nbsp;&nbsp;<a href=\"dashboard?action=stopnav\">off</a>";
		
		String debug = "<td class='menu'>debug<td class='off'><a href=\"dashboard?action=debugon\">&nbsp;&nbsp;on&nbsp;&nbsp;</a>|&nbsp;&nbsp;off";
		if(settings.getBoolean(ManualSettings.debugenabled)) 
			debug = "<td class='menu'>debug<td class='on'>&nbsp;&nbsp;on&nbsp;&nbsp;|&nbsp;&nbsp;<a href=\"dashboard?action=debugoff\">off</a>";
				
		if(httpport == null) httpport = state.get(State.values.httpport);
		String dock = "undocked>";
		if(state.equals(values.dockstatus, AutoDock.DOCKED)) dock = "<a href=\"dashboard?action=redock\" title=\"force re-dock the robot\">docked</a>";	
		
		if(!state.getBoolean(values.odometry) || state.equals(values.dockstatus, AutoDock.DOCKED)) 
			dock = "<a href=\"dashboard?action=redock\" title=\"force re-dock the robot\">docked</a>";	

		if(state.equals(values.dockstatus, AutoDock.DOCKED)) 
			dock = "<a href=\"dashboard?action=redock\" title=\"force re-dock the robot\">docked</a>";	
		
		if(state.equals(values.dockstatus, AutoDock.UNDOCKED)) dock = "<a href=\"dashboard?action=redock\">un-docked</a>";	
		if(state.equals(values.dockstatus, AutoDock.DOCKING))  dock = "docking";
		if(state.equals(values.dockstatus, AutoDock.UNKNOWN))  dock = "UNKNOWN";
		if(state.getBoolean(values.autodocking)) dock = "auto-docking";
	
		//------------------- now build HTML buffer ------------------------------------------------------------//  
		StringBuffer str = new StringBuffer("<table cellspacing=\"8\"><tbody>\n");
		
		// version | views | ssid | rate
		str.append("\n<tr><td class='menu'>version " + VERSION);
		str.append("<td><div class=\"dropdown\"><button class=\"dropbtn\">commands</button><div class=\"dropdown-content\">\n" + commandlinks + "\n</div></div>");
		str.append("\n<td class='menu'>ssid<td><a href=\"http://"+state.get(values.localaddress) +"\" target=\"_blank\">" + state.get(values.ssid)); 
		
		if(delay.equals("5"))  str.append("\n<td class='menu'>rate<td>" + "5 | <a href=\"dashboard?delay=10\">10</a> | <a href=\"dashboard?delay=30\">30</a>"); 
		if(delay.equals("10")) str.append("\n<td class='menu'>rate<td>" + "<a href=\"dashboard?delay=5\">5</a> | 10 | <a href=\"dashboard?delay=30\">30</a>"); 
		if(delay.equals("30")) str.append("\n<td class='menu'>rate<td>" + "<a href=\"dashboard?delay=5\">5</a> | <a href=\"dashboard?delay=10\">10</a> | 30"); 

		// motor | wan | hdd 		
		str.append("\n<tr>");
		str.append("<td class='menu'>motor<td>" + motor );
		String ext = state.get(values.externaladdress);
		if( ext == null ) str.append("<td class='menu'>wan<td>disconnected");
		else str.append("<td class='menu'>wan<td><a href=\"http://"+ ext + ":" + httpport + "/oculusPrime/" +"\" target=\"_blank\">" + ext + "</a>"); 
		str.append( "<td class='menu'>hdd<td>" + Util.diskFullPercent() + "% used</a></tr> \n"); 
	
		// power | lan | prime 
		str.append("\n<tr>");
		str.append("<td class='menu'>power<td>" + power );
		str.append("<td class='menu'>lan<td><a href=\"http://"+state.get(values.localaddress) +"\" target=\"_blank\">" + state.get(values.localaddress) + "</a>"); 
		str.append("<td class='menu'>prime<td>" + Util.countAllMbytes(".") + " mb</tr> \n");
		
		// dock | battery | streams
		str.append("\n<tr>");
		if( ! state.equals(values.dockstatus, AutoDock.DOCKED)) str.append("<td class='menu'>dock<td class='busy'>" + dock);	
		else str.append("<td class='menu'>dock<td>" + dock);	
		str.append("<td class='menu'>battery<td><a href=\"dashboard?view=power\">" + state.get(values.batterylife) + "</a>"); 
		str.append("<td class='menu'>streams<td>" + Util.countAllMbytes(Settings.streamfolder) + "</a> mb</tr> \n" );
	
		// record | booted | archive 
		str.append("\n<tr>");	
		str.append(rec);
		str.append("<td class='menu'>booted<td>" + (((System.currentTimeMillis() - state.getLong(values.linuxboot)) / 1000) / 60)+ "</a> mins");
		str.append("<td class='menu'>archive<td>"+ Util.countMbytes("./log/archive") + " mb</tr> \n" );

		// camera | uptime | frames 
		str.append("\n<tr>");
		str.append(cam);
		if(restarts < 5) str.append("<td class='menu'>up time<td>" + (state.getUpTime()/1000)/60 + "</a> mins");
		else str.append("<td class='menu'>up time<td class='busy'>" + (state.getUpTime()/1000)/60 + "</a> mins (" + restarts + ")");
		
		str.append("<td class='menu'>frames<td>" + Util.countAllMbytes(Settings.framefolder) + " mb</tr> \n" );

		// navigation | cpu | logs
		str.append("\n<tr>");
		str.append(od);
		String cpuvalue; 
		if(state.getBoolean(values.waitingforcpu)) cpuvalue = "<td class='busy'>" + state.get(values.cpu) + "%&nbsp;&nbsp;waiting..</td>"; 
		else cpuvalue = "<td>" + state.get(values.cpu) + "%"; 
		str.append("<td class='menu'>cpu" + cpuvalue);
		str.append("<td class='menu'>logs<td>" + Util.countMbytes(Settings.logfolder) + " mb</tr> \n" );
		
		// debug | telnet | ros
		str.append("\n<tr>");
		str.append(debug  
			+ "<td class='menu'>telnet<td>" + state.get(values.telnetusers)
			+ "<td class='menu'>ros<td>" + Util.getRosCheck() + "</tr> \n" ); // doesn't work on hidden file? Util.countMbytes(Settings.roslogfolder)
		
		str.append(getActiveRoute());
		// <td class='menu'>routes
		str.append("\n\n<tr><td>"+ getRouteLinks() +" \n");
		String waypoint = state.get(values.roswaypoint);
		if(waypoint == null) waypoint = "not active";
		String drop = "\n<td><div class=\"dropdown\"><button class=\"dropbtn\">"+waypoint+"</button><div class=\"dropdown-content\">";
		Vector<String> waypointsAll = getAllWaypoints(); 
		if(waypointsAll != null){
			for(int i = 0 ; i < waypointsAll.size() ; i++) {
				drop += "\n<a href=\"dashboard?action=gotowp&route="+ waypointsAll.get(i).replaceAll("&nbsp;", " ").trim() +"\">" + waypointsAll.get(i) + "</a> ";
			}
		}
		drop += "\n</div></div>\n";
		str.append(drop);
		str.append("<td><div class=\"dropdown\"><button class=\"dropbtn\">views</button><div class=\"dropdown-content\">\n" + viewslinks + "\n</div></div>");

		// pids 
		Vector<PyScripts> pids = Util.getRunningPythonScripts();
		if(pids.size() == 0) str.append("<td>pids 0 ");
		else if(pids.size() > 0) {
			str.append("<td><div class=\"dropdown\"><button class=\"dropbtn\">pids "+pids.size()+"</button><div class=\"dropdown-content\"> \n");
			for(int i = 0 ; i < pids.size() ; i ++) str.append("<a href=\"dashboard?action=kill&pid="+pids.get(i).id+"\">"+pids.get(i).pyFile+"</a>\n");
			str.append("\n</div></div>");
		}
		
		// python files
		if(new File(Settings.redhome+"/telnet_scripts").exists()){
			File telnet = new File(Settings.redhome+"/telnet_scripts");
			File[] names = telnet.listFiles(new FileFilter() {
				@Override
				public boolean accept(File pathname) { return pathname.getName().endsWith(".py"); }
			});
			if( names == null ) str.append("<td> none");
			else {
				str.append("<td><div class=\"dropdown\"><button class=\"dropbtn\">scripts" 
					+ " " + names.length + "</button><div class=\"dropdown-content\">"); 
				for(int i = 0 ; i < names.length ; i++)
					str.append("\n<a href=\"dashboard?action=script&pid="+ names[i].getName() +"\">"+ names[i].getName() +"</a>");
		
				str.append("\n</div></div>\n");
			}
		} else str.append("<td> none");
		
		//
		str.append("<td><div class=\"dropdown\"><button class=\"dropbtn\">logs "+pids.size()+"</button><div class=\"dropdown-content\"> \n");
		for(int i = 0 ; i < pids.size() ; i ++) str.append("<a href=\"dashboard?action=kill&pid="+pids.get(i).id+"\">"+pids.get(i).logFile+"</a>\n");
		str.append("\n</div></div>");
		
		//===============================================================================================================================================//
	
		// --- active --- //
		String m = "# " + Navigation.consecutiveroute + " ";
		if(pointslist != null) // m+= pointslist;
			for(int c = 0 ; c < pointslist.size(); c++ )
				m+= "<a href=\"media?filter="+ pointslist.get(c) + "\" target=\"_blank\">" + pointslist.get(c) + "</a>,  ";
		m += "  <a href=\"dashboard?action=gotodock\">return to dock</a> ";
//		if(state.getBoolean(values.routeoverdue)) m += " *overdue* "; 		
//		if(state.getBoolean(values.waypointbusy)) m += " *waypointbusy* "; 		
		if(state.getBoolean(values.rosgoalcancel)) m += " *ros goal cancel* "; 
		if(m.length() > 0) str.append("\n<tr><td class='menu'>active<td class='tail' colspan=\"11\">"+ m.trim() +"</tr>\n");	

		// --- gui notify --- //
		String msg = state.get(values.guinotify);
		if(msg == null) msg = "";
		else msg += "&nbsp;&nbsp;(<a href=\"dashboard?action=gui\">ignore</a>)";
		if(msg.length() > 1) {
			msg = "<tr><td class='menu'>message<td class='tail' colspan=\"11\">" + msg + "</tr> \n";
			str.append(msg);
		}
	
		str.append(getTail(10) + "\n");
		str.append("\n</tbody></table>\n");
		return str.toString();
	}
	
	private String getRouteLinks(){   
		Vector<String> list = NavigationUtilities.getRoutes();
		String rname = state.get(values.navigationroute);
		if(rname == null) rname = "not active";
		String drop = "\n<div class=\"dropdown\"><button class=\"dropbtn\">"+rname+"</button><div class=\"dropdown-content\">";
		for(int i = 0; i < list.size(); i++) drop += "<a href=\"dashboard?action=runroute&route="+list.get(i)+"\">" + list.get(i) + "</a>";
		return drop += "</div></div>";
	}
	
	private String getActiveRoute(){  			
		String link = "<tr><td class='menu'>next<td>";
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
		if(state.exists(values.navigationrouteid))
		link += next += "<td class='menu'>meters<td>" + Navigation.getRouteMeters() + " | " + estimatedmeters + "<td class='menu'>time<td>" + time + " | " +  estimatedseconds;
		else link += next += "<td class='menu'>meters<td> none <td class='menu'>time<td> none";
		return link; 
	}
	
	public static String tailFormated(int lines){
		int i = 0;
		final long now = System.currentTimeMillis();
		StringBuffer str = new StringBuffer();
	 	if(Util.history.size() > lines) i = Util.history.size() - lines;
	 	
		for(; i < Util.history.size() ; i++){
			String line = Util.history.get(i).substring(Util.history.get(i).indexOf(",")+1).trim();
			String stamp = Util.history.get(i).substring(0, Util.history.get(i).indexOf(","));
			line = line.replaceFirst("\\$[0-9]", "");
			line = line.replaceFirst("^oculusprime.", "");
			line = line.replaceFirst("^oculusPrime.", "");
			line = line.replaceFirst("^Application.", "");
			line = line.replaceFirst("^static, ", "");		
			double delta = (double)(now - Long.parseLong(stamp)) / (double) 1000;
			String unit = "&nbsp;sec&nbsp;";
			String d = Util.formatFloat(delta, 0);
			if(delta > 60) { delta = delta / 60; unit = " min "; d =  Util.formatFloat(delta, 1); }
			str.append("\n<tr><td class='menu'>" + d + "&nbsp;" + unit + "<td class='tail' colspan=\"11\">" + line.trim() + "</tr> \n"); 
		}
		return str.toString();
	}
	
	private String getTail(int lines){
		String reply = "\n\n"; // <table cellspacing=\"8\"> \n";
		reply += tailFormated(lines); 
		// reply += ("\n</table>\n");
		return reply;
	}

	/*
	private String getHistory(){
		String reply = "";
		for(int i = 0 ; i < history.size() ; i++) {
			long time = Long.parseLong(history.get(i).substring(0, history.get(i).indexOf(" ")));
			String mesg = history.get(i).substring(history.get(i).indexOf(" "));
			String date =  new Date(time).toString();
			date = date.substring(10, date.length()-8).trim();
			double delta = (double)(System.currentTimeMillis() - time) / (double) 1000;
			String unit = "&nbsp;sec&nbsp;";
			if(delta > 60) { delta = delta / 60; unit = " min "; }
			reply += "&nbsp;" + Util.formatFloat(delta, 1) + "&nbsp;" + unit + "" + mesg.trim() + "\n"; 
		}
		return reply;
	}
	*/
	
	@Override
	public void updated(String key){
		
//		if(state.exists(key)) Util.log("updated: " + key + " " + state.get(key), this);
		
		// only read from file on change 
		if(key.equals(values.navigationroute.name())){
			if(state.exists(values.navigationroute)){
				estimatedmeters = NavigationUtilities.getRouteDistanceEstimateString(state.get(values.navigationroute));
				estimatedseconds = NavigationUtilities.getRouteTimeEstimateString(state.get(values.navigationroute));
				pointslist = NavigationUtilities.getWaypointsForRoute(state.get(values.navigationroute));
			} else {
				pointslist = null;	
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
			
//    	if(state.getBoolean(values.routeoverdue)) 
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
//		if(history.size() > MAX_STATE_HISTORY) history.remove(0);
//		if(state.exists(key)) history.add(System.currentTimeMillis() + " " +key + " = " + state.get(key));
	}
	
	public static Vector<String> getAllWaypoints(){
		Vector<String> names = new Vector<String>();
		if( ! state.exists(values.rosmapwaypoints)) return names;	
		String[] points = state.get(values.rosmapwaypoints).split(",");
		for(int i = 0 ; i < points.length ; i++ ){
			try { Double.parseDouble(points[i]); } catch (NumberFormatException e){
				
				String value = points[i].replaceAll("&nbsp;", " ").trim();			
				
//				if(value.contains("&nbsp;")) Util.log("getAllWaypoints(): ..WARNING.. html chars point: " + value);
//				if(names.contains(value)) Util.log("getAllWaypoints(): ..WARNING.. duplicate point: " + value);
				
				if( ! names.contains(value)) names.add(points[i]);
			}
		}
		return names;
	}
}

