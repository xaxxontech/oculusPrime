package oculusPrime;

import java.io.File;
import java.io.IOException;
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
import oculusPrime.StateObserver.modes;
import oculusPrime.commport.PowerLogger;

public class DashboardServlet extends HttpServlet implements Observer {
	
	static final long serialVersionUID = 1L;	
	
	private static final int MAX_STATE_HISTORY = 40;
	private static final String HTTP_REFRESH_DELAY_SECONDS = "7"; 
	
	static final String restart = "<a href=\"dashboard?action=restart\" title=\"restart application\">";
	static final String reboot = "<a href=\"dashboard?action=reboot\" title=\"reboot linux os\">";
	static final String runroute = "<a href=\"dashboard?action=runroute\">";
	static final String deletelogs = "<a href=\"dashboard?action=deletelogs\" title=\"delete all log files, causes reboot.\">";
	static final String archivelogs = "<a href=\"dashboard?action=archivelogs\" title=\"archive all files in log folders\">";	

	static final String viewslinks = // "<tr><td><b>views</b><td colspan=\"11\">"+	
			"<a href=\"navigationlog/index.html\" target=\"_blank\">navigation</a>&nbsp&nbsp;&nbsp&nbsp;"+
			"<a href=\"dashboard?view=drive\">drive</a>&nbsp;&nbsp;&nbsp;&nbsp;"  +
			"<a href=\"dashboard?view=users\">users</a>&nbsp;&nbsp;&nbsp;&nbsp;" +
			"<a href=\"dashboard?view=stdout\">stdout</a>&nbsp;&nbsp;&nbsp;&nbsp;" +
			"<a href=\"dashboard?view=history\">history</a>&nbsp;&nbsp;" +
			"<a href=\"dashboard?view=state\">state</a>&nbsp;&nbsp;&nbsp;&nbsp;"  +
			"<a href=\"dashboard?action=snapshot\" target=\"_blank\">snap</a>&nbsp;&nbsp;&nbsp;&nbsp;"+ 
			"<a href=\"dashboard?action=email\">email</a>" ; 
	//+"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;";

	static double VERSION = new Updater().getCurrentVersion();
	static Vector<String> history = new Vector<String>();
	static Application app = null;
	static Settings settings = null;
	static String httpport = null;
	static BanList ban = null;
	static State state = null;
	
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
		String delay = null;	
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
					if(turnLightOnIfDark()) Util.log("light was turned on because was dark", this);
				}}).start();
			}
				
			if(action.equalsIgnoreCase("camoff")) {
				app.driverCallServer(PlayerCommands.publish, "stop");
				app.driverCallServer(PlayerCommands.spotlight, "0");
			}

			if(action.equalsIgnoreCase("gui")) state.delete(values.guinotify);
			if(action.equalsIgnoreCase("gotowp")) app.driverCallServer(PlayerCommands.gotowaypoint, route);
			if(action.equalsIgnoreCase("startrec")) app.driverCallServer(PlayerCommands.record, "true");
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
			
			response.sendRedirect("/oculusPrime/dashboard"); 
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
			
			//TODO:: ///////////////////////////////////////////////////////////////////////////////////////////////////////
			if(view.equalsIgnoreCase("state")){
				out.println("<html><body> \n");				
				out.println("&nbsp&nbsp&nbsp&nbsp<a href=\"dashboard\">dashboard</a>");
				out.println("&nbsp&nbsp&nbsp&nbsp<a href=\"dashboard?view=history\">history</a><br /><br />\n");
				out.println(toHTML() + "\n");
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
		out.println("<html><head><meta http-equiv=\"refresh\" content=\""+ delay + "\"></head><body> \n");
		out.println(toDashboard(request.getServerName()+":"+request.getServerPort() + "/oculusPrime/dashboard") + "\n");
		out.println("\n</body></html> \n");
		out.close();	
	}
	
	private boolean turnLightOnIfDark(){
		Util.debug("turnLightOnIfDark(): start, light level = " + state.getInteger(values.lightlevel), this);
		if (state.getInteger(values.spotlightbrightness) == 100) return false; // already on
		app.driverCallServer(PlayerCommands.getlightlevel, null); // get new value
		new StateObserver(modes.changed).block(values.lightlevel, 5000); // wait for a new value
		if(state.getInteger(values.lightlevel) < 25){
			Util.debug("turnLightOnIfDark(): light too low = " + state.getInteger(values.lightlevel), this);
			app.driverCallServer(PlayerCommands.spotlight, "100"); // light on
			return true;
		}
		
		Util.debug("turnLightOnIfDark(): exit, light level = " + state.getInteger(values.lightlevel), this);
		return false;
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
				str.append("<tr><td><b>" + key + "</b><td>" + props.get(key) + "</a>");
				
				if( !i.hasNext()) break;
				key = i.next();
				if(key.equals(values.rosamcl.name())) key = i.next();
				if(key.equals(values.rosglobalpath.name())) key = i.next();
				if(key.equals(values.rosmapinfo.name())) key = i.next();
				if(key.equals(values.rosscan.name())) key = i.next();
				if(key.equals(values.rosmapwaypoints.name())) key = i.next();
				if(key.equals(values.batteryinfo.name())) key = i.next();
				str.append("<td><b>" + key + "</b><td>" + props.get(key) + "</a>");
				if( !i.hasNext()) break;
				
			} catch (Exception e) { break; }
		}

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
			
		String life = state.get(values.batterylife);
		if(life == null) life = "";
		if(life.contains("%")) life = Util.formatFloat(life.substring(0, life.indexOf('%')+1), 1); 
		if(state.get(values.batterylife).contains("charging")) life += "</a>&nbsp;&nbsp;&nbsp;&#9889;"; // charge &#8599;
		// else life += "</a>&nbsp;&nbsp;&nbsp;&#8600;"; // draining 
		life = "<a href=\"dashboard?view=power\">"+life;
	
		//------------------- now build HTML STRING buffer ------------------------------------------------------------//  
		StringBuffer str = new StringBuffer("<table cellspacing=\"7\" border=\"0\" style=\"min-width: 630; max-width: 700px;\">\n");
		//str.append("\n\n<tr><td colspan=\"11\"><hr></tr> \n");	
		
		// version | ssid | ping delay
		String[] jetty = Util.getJettyStatus().split(",");
		str.append("\n<tr><td><b>version</b><td>" + VERSION); 
		str.append("\n<td><b>ssid</b><td><a href=\"http://"+state.get(values.localaddress) +"\">" + jetty[1]); 
		str.append("\n<td><b>ping</b><td>" + jetty[3]); 

		// motor | wan | hdd 		
		str.append("\n<tr>");
		str.append("<td><b>motor</b><td>" + motor );
		String ext = state.get(values.externaladdress);
		if( ext == null ) str.append("<td><b>wan</b><td>disconnected");
		else str.append("<td><b>wan</b><td><a href=\"http://"+ ext + ":" + httpport 
				+ "/oculusPrime/" +"\" target=\"_blank\" title=\"go to user interface on external address\">" + ext + "</a>");
		str.append( "<td><b>hdd</b><td>" + Util.diskFullPercent() + "% used</a></tr> \n"); 
	
		// power | lan | prime 
		str.append("\n<tr>");
		str.append("<td><b>power</b><td>" + power );
		str.append("<td><b>lan</b><td><a href=\"http://"+state.get(values.localaddress) 
			+"\" target=\"_blank\" title=\"go to network control panel\">" + state.get(values.localaddress) + "</a>");
		str.append("<td><b>prime</b><td>" + deletelogs + Util.countAllMbytes(".") + "</a> mb</tr> \n");
		
		// dock | battery | streams
		str.append("\n<tr>");
		str.append("<td><b>dock</b><td>" + dock);	
		str.append("<td><b>battery</b>&nbsp;<td>" + life); 
		str.append("<td><b>streams</b><td>" + Util.countAllMbytes(Settings.streamfolder) + " mb</tr> \n" );
	
		// record | booted | archive 
		str.append("\n<tr>");	
		str.append("<td><b>record</b><td>" + rec);
		str.append("<td><b>booted</b><td>" + reboot + (((System.currentTimeMillis() - state.getLong(values.linuxboot)) / 1000) / 60)+ "</a> mins ");
		str.append("<td><b>archive</b><td>"+ archivelogs + Util.countMbytes("./log/archive") + "</a> mb</tr> \n" );

		// camera | uptime | frames 
		str.append("\n<tr>");
		str.append("<td><b>camera</b><td>" + cam); 
		str.append("<td><b>up time</b><td>" + restart +(state.getUpTime()/1000)/60 + "</a> mins ");
		str.append("<td><b>frames</b><td>" + Util.countAllMbytes(Settings.framefolder) + " mb</tr> \n" );

		// navigation | cpu | logs
		str.append("\n<tr>");
		str.append("<td><b>nav</b><td>" + od);
		str.append("<td><b>cpu</b><td>" + state.get(values.cpu) + "% ");
		str.append("<td><b>logs</b><td>" + Util.countMbytes(Settings.logfolder) + " mb</tr> \n" );
		
		// debug | telnet | ros
		str.append("\n<tr>");
		str.append("<td><b>debug</b><td>" + debug  
			+ "<td><b>telnet</b><td>" + state.get(values.telnetusers) + " - " + StateObserver.alive + " " + StateObserver.total
			+ "<td><b>ros</b><td>" + Util.getRosCheck() + "</tr> \n" );
			// doesn't work on hidden file? Util.countMbytes(Settings.roslogfolder)
		
		str.append(getActiveRoute());

		str.append("<tr><td><b>routes</b>"+ getRouteLinks() +"</tr> \n");
		
		
		String msg = state.get(values.guinotify);
		if(msg == null) msg = "";
		else msg += "&nbsp;&nbsp;(<a href=\"dashboard?action=gui\">ignore</a>)";
		if(msg.length() > 1) {
			msg = "<tr><td><b>message</b><td colspan=\"11\">" + msg + "</tr> \n";
			str.append(msg);
		}

		Vector<String> list = NavigationUtilities.getWaypointsAll(NavigationUtilities.routesLoad());
		String nlink = "<tr><td><b>points</b><td colspan=\"11\">";
		for(int i = 0 ; i < list.size() ; i++)
			nlink += "<a href=\"dashboard?action=gotowp&route="+ list.get(i) +"\">" + list.get(i) + "</a>&nbsp;&nbsp;";
		str.append( nlink + "&nbsp;&nbsp;<a href=\"dashboard?action=gotodock\">dock</a>" ); 
		
		str.append("\n<tr><td><b>views</b><td colspan=\"11\">"+ viewslinks + "</tr> \n");	
	
		str.append("\n</table>\n");
		str.append(getTail(11) + "\n");
		return str.toString();
	}
	
	private String getRouteLinks(){   
		Vector<String> list = NavigationUtilities.getRoutes();
		String link = "<td colspan=\"11\">";
		String active = NavigationUtilities.getActiveRoute();
		if(active != null) link += "<a href=\"dashboard?action=cancel\">*" + active + "</a>&nbsp;&nbsp;";
		for(int i = 0; i < list.size(); i++){
			if( ! list.get(i).equals(active))
				link += "<a href=\"dashboard?action=runroute&route="+list.get(i)+"\">" + list.get(i) + "</a>&nbsp;&nbsp;";
		}
		return link;
	}
	
	private String getActiveRoute(){  	
		String rname = NavigationUtilities.getActiveRoute(); 
		String time = ((System.currentTimeMillis() - Navigation.routestarttime)/1000) +  " sec";
		String next = state.get(values.roswaypoint);
		if(state.equals(values.dockstatus, AutoDock.DOCKED) && !state.getBoolean(values.odometry)){
			if(state.exists(values.nextroutetime)){
				next = ((state.getLong(values.nextroutetime) - System.currentTimeMillis())/1000)+ " sec";
				time = "0";
			}
		} 
		
		if(next==null) time = "";
		
		String link = "<td><b>next</b><td>" + next; 
		link += "<td><b>meters</b><td>" 
				+ Util.formatFloat(Navigation.routemillimeters / (double)1000, 0)  
				+ "<td><b>time</b><td>" + time;
		
		link += "<tr><td><b>stats</b><td>" + NavigationUtilities.getRouteFailsString(rname)
			+ " / " + NavigationUtilities.getRouteCountString(rname) 
			+ "<td><b>est</b><td>" +  NavigationUtilities.getRouteDistanceEstimate(rname)
			+ "<td><b>sec</b><td>" + NavigationUtilities.getRouteTimeEstimate(rname);
		
		return link.trim(); 
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
	
		if(key.equals(values.dockstatus.name())){
			if(state.equals(values.dockstatus, AutoDock.DOCKED)){
		
				Util.debug("....... docked.... ", this);

			
			}
		}
		
		if(state.getBoolean(values.routeoverdue)) 
			state.set(values.guinotify, "route over due: " + NavigationUtilities.getActiveRoute()); 
		
		if(key.equals(values.batteryinfo.name())) return;
		if(key.equals(values.batterylife.name())) return;		
 		if(key.equals(values.batteryvolts.name())) return; 		
 		if(key.equals(values.cpu.name())) return; 

//		if(key.equals(values.framegrabbusy.name())) return;
//		if(key.equals(values.rosglobalpath.name())) return;
//		if(key.equals(values.rosscan.name())) return;
		
		// trim size
		if(history.size() > MAX_STATE_HISTORY) history.remove(0);
		if(state.exists(key)) history.add(System.currentTimeMillis() + " " +key + " = " + state.get(key));
	}
}

