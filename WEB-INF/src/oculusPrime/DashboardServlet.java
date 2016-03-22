package oculusPrime;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import developer.Navigation;
import oculusPrime.State.values;
import oculusPrime.commport.PowerLogger;

public class DashboardServlet extends HttpServlet implements Observer {
	
	static final long serialVersionUID = 1L;	
	
	private static final int MAX_STATE_HISTORY = 100; // in development keep high number 
	private static final String HTTP_REFRESH_DELAY_SECONDS = "10"; // keep low in development 
	
	static final String restart = "<a href=\"dashboard?action=restart\" title=\"restart application\">";
	static final String reboot = "<a href=\"dashboard?action=reboot\" title=\"reboot linux os\">";
	static final String runroute = "<a href=\"dashboard?action=runroute\">";
	static final String deletelogs = "<a href=\"dashboard?action=deletelogs\" title=\"delete all log files, causes reboot.\">";
	static final String archivelogs = "<a href=\"dashboard?action=archivelogs\" title=\"archive all files in log folders\">";	

	static final String link = "<tr><td><b>views</b><td colspan=\"11\">"+	
			"<a href=\"navigationlog/index.html\" target=\"_blank\">navigation</a>&nbsp&nbsp;"+
			"<a href=\"dashboard?view=ban\">ban</a>&nbsp;&nbsp;" +
			"<a href=\"dashboard?view=power\">power</a>&nbsp;&nbsp;" +
			"<a href=\"dashboard?view=stdout\">stdout</a>&nbsp;&nbsp;" +
		//	"<a href=\"dashboard?view=ros\">ros</a>&nbsp;&nbsp;" +
			"<a href=\"dashboard?view=history\">history</a>&nbsp;&nbsp;" +
			"<a href=\"dashboard?view=state\">state</a>&nbsp;&nbsp;"  +
			"<a href=\"dashboard?action=snapshot\" target=\"_blank\">snap</a>&nbsp;&nbsp;"+ 
			"<a href=\"dashboard?action=save\">save</a>&nbsp;&nbsp;" +
			"<a href=\"dashboard?action=email\">email</a>&nbsp;&nbsp;";
	

	static double VERSION = new Updater().getCurrentVersion();
	static Vector<String> history = new Vector<String>();
	
	static Application app = null;
	static NodeList routes = null;
	static Settings settings = null;
	static String httpport = null;
	static BanList ban = null;
	static State state = null;
	static long routedistance = 0;
	
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		state = State.getReference();
		httpport = state.get(State.values.httpport);
		settings = Settings.getReference();
		ban = BanList.getRefrence();
		state.addObserver(this);
		Document document = Util.loadXMLFromString(routesLoad());
		routes = document.getDocumentElement().getChildNodes();
	}

	public static void setApp(Application a){app = a;}
	
	public static String routesLoad() {
		String result = "";
		BufferedReader reader;
		try {
			reader = new BufferedReader(new FileReader(Navigation.navroutesfile));
			String line = "";
			while ((line = reader.readLine()) != null) result += line;
			reader.close();	
		} catch (Exception e){return "<routeslist></routeslist>";}
		return result;
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
		String route = null;
		String member = null;
		
		try {
			view = request.getParameter("view");
			delay = request.getParameter("delay");
			action = request.getParameter("action");
			route = request.getParameter("route");
			member = request.getParameter("member");
		} catch (Exception e) {}
			
		if(delay == null) delay = HTTP_REFRESH_DELAY_SECONDS;
		
		if(action != null && app != null && member != null){
			if(action.equals("delete")){
				Util.log("doGet: .. detete state member: " + member, this);
				state.delete(member);
				action = null;
			}
		}
		
		if(action != null && app != null){
	
			if(action.equalsIgnoreCase("gui")) state.delete(values.guinotify);
			if(action.equalsIgnoreCase("redock")) app.driverCallServer(PlayerCommands.redock, null);	
			if(action.equalsIgnoreCase("cancel")) app.driverCallServer(PlayerCommands.cancelroute, null);
			if(action.equalsIgnoreCase("gotodock")) app.driverCallServer(PlayerCommands.gotodock, null);
			if(action.equalsIgnoreCase("startnav")) app.driverCallServer(PlayerCommands.startnav, null);
			if(action.equalsIgnoreCase("stopnav")) app.driverCallServer(PlayerCommands.stopnav, null);
			if(action.equalsIgnoreCase("deletelogs")) app.driverCallServer(PlayerCommands.deletelogs, null);			
			if(action.equalsIgnoreCase("archivelogs")) app.driverCallServer(PlayerCommands.archivelogs, null);
			if(route != null)if(action.equalsIgnoreCase("runroute")) app.driverCallServer(PlayerCommands.runroute, route);
			if(action.equalsIgnoreCase("debugon")) app.driverCallServer(PlayerCommands.writesetting, ManualSettings.debugenabled.name() + " true");
			if(action.equalsIgnoreCase("debugoff")) app.driverCallServer(PlayerCommands.writesetting, ManualSettings.debugenabled.name() + " false");

			if(action.equalsIgnoreCase("email")){
				Util.log("sending email...", this);
				app.driverCallServer(PlayerCommands.email, settings.readSetting(GUISettings.email_to_address) 
						+ " [oculus shapshot] " + getHistory() + "\n\n" + Util.tail(100));	
			}
			
			if(action.equalsIgnoreCase("resetstats") && route!=null){
				Navigation.updateRouteStats(route, 0, 0);
			}
			
			if(action.equalsIgnoreCase("reboot")){
				new Thread(new Runnable() { public void run() {
					Util.log("reboot called, going down..", this);
					Util.delay(3000); // redirect before calling.. 
					app.driverCallServer(PlayerCommands.reboot, null);
				}}).start();
			}
			
			if(action.equalsIgnoreCase("restart")){
				new Thread(new Runnable() { public void run() {
					Util.log("restart called, going down..", this);
					Util.delay(3000); // redirect before calling.. 
					app.driverCallServer(PlayerCommands.restart, null);
				}}).start();
			}
			
			if(action.equalsIgnoreCase("save")) {	
				new Thread(new Runnable() { public void run() {
					if( ! new Downloader().FileDownload("http://" + state.get(values.localaddress)  
						+ ":" + httpport + "/oculusPrime/dashboard?action=snapshot", "snapshot_"+ System.currentTimeMillis() +".txt", "log"))
							Util.log("snapshot save failed", this);
				}}).start();
			}
			
			if(action.equalsIgnoreCase("snapshot")) {	
				if(Util.archivePID()) {
					Util.log("busy, skipping..", this);
					return;
				}
//				Util.archiveFiles("./archive" + Util.sep + "snapshot_"+System.currentTimeMillis() 
//					+ ".tar.bz2", new String[]{NavigationLog.navigationlogpath, state.dumpFile("dashboard command")});
				sendSnap(request, response);
				return;
			}
			
			response.sendRedirect("/oculusPrime/dashboard"); 
		}
		
	
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		out.println("<html><head><meta http-equiv=\"refresh\" content=\""+ delay + "\"></head><body> \n");
	
		if(view != null){
			if(view.equalsIgnoreCase("ban")){	
				out.println("<a href=\"dashboard\">dashboard</a> &nbsp;&nbsp; <br />\n");
				out.println(ban + "<br />\n");
				out.println(ban.tail(30) + "\n");
				out.println("\n</body></html> \n");
				out.close();
			}
			
			if(view.equalsIgnoreCase("state")){
				out.println(toHTML() + "\n");
				out.println("<br />&nbsp&nbsp&nbsp&nbsp<a href=\"dashboard\">dashboard</a><br />\n");
				out.println("\n</body></html> \n");
				out.close();
			}
			
			if(view.equalsIgnoreCase("stdout")){
				out.println("<a href=\"dashboard\">dashboard</a>&nbsp;&nbsp;  \n" 
						+ new File(Settings.stdout).getAbsolutePath() + "<br />\n");
				out.println(Util.tail(40) + "\n");
				out.println("\n</body></html> \n");
				out.close();
			}
			
			if(view.equalsIgnoreCase("power")){	
				out.println("<a href=\"dashboard\">dashboard</a>&nbsp;&nbsp; \n"
						+ new File(PowerLogger.powerlog).getAbsolutePath() + "<br />\n");
				out.println(PowerLogger.tail(40) + "\n");
				out.println("\n</body></html> \n");
				out.close();
			}
			
			if(view.equalsIgnoreCase("history")){
				out.println("<a href=\"dashboard\">dashboard</a> state history: "+ new Date().toString() +"<br />\n");
				out.println(getHistory().replaceAll("\n", "<br>\n"));
				out.println("\n</body></html> \n");
				out.close();
			}	
		}
		
		// default view 
		out.println(toDashboard(request.getServerName()+":"+request.getServerPort() + "/oculusPrime/dashboard") + "\n");
		out.println("\n</body></html> \n");
		out.close();	
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
				str.append("<tr><td><b>" + key + "</b><td><a href=\"dashboard?view=state&action=delete&member=" 
						+ key + "\">" + props.get(key) + "</a>");
	
				if( !i.hasNext()) break;
				
				key = i.next();
				if(key.equals(values.rosamcl.name())) key = i.next();
				if(key.equals(values.rosglobalpath.name())) key = i.next();
				if(key.equals(values.rosmapinfo.name())) key = i.next();
				if(key.equals(values.rosscan.name())) key = i.next();
				if(key.equals(values.rosmapwaypoints.name())) key = i.next();
				if(key.equals(values.batteryinfo.name())) key = i.next();
				str.append("<td><b>" + key + "</b><td><a href=\"dashboard?view=state&action=delete&member=" 
						+ key + "\">" + props.get(key) + "</a>");
				
				if( !i.hasNext()) break;
				
				key = i.next();
				if(key.equals(values.rosamcl.name())) key = i.next();
				if(key.equals(values.rosglobalpath.name())) key = i.next();
				if(key.equals(values.rosmapinfo.name())) key = i.next();
				if(key.equals(values.rosscan.name())) key = i.next();
				if(key.equals(values.rosmapwaypoints.name())) key = i.next();
				if(key.equals(values.batteryinfo.name())) key = i.next();
				str.append("<td><b>" + key + "</b><td><a href=\"dashboard?view=state&action=delete&member=" 
						+ key + "\">" + props.get(key) + "</a>");
				
			} catch (Exception e) { break; }
		}
	
	//	if(props.containsKey(values.rosamcl.name())) 
	//		str.append("<tr><td><b>rosamcl</b><td colspan=\"9\"> " + props.get(values.rosamcl.name()) + " </tr> \r");
	
	//	if(props.containsKey(values.batteryinfo.name())) 
	//		str.append("<tr><td colspan=\"9\"><br><hr><b>bateryinfo</b> " + props.get(values.batteryinfo.name()) + " </tr> \r");
		
		str.append("<tr><td colspan=\"9\"><hr><tr><td colspan=\"9\"><b>null's</b>");
		for (values key : values.values()) if(! props.containsKey(key.name())) str.append(" " + key.name() + " ");
		str.append("</table>\n");
		return str.toString();
	}
	
	public void sendSnap(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("text");
		PrintWriter out = response.getWriter();
		
		out.println("\n\r-- " + new Date() + " --\n\r");
		out.println(Util.tail(100).replaceAll("<br>", ""));

		out.println("\n\r -- state history --\n\r");
		out.println(getHistory() + "\n\r");

		out.println("\n\r -- state values -- \n\r");
		out.println(state.toString().replaceAll("<br>", ""));	
		
		out.println("\n\r -- settings --\n\r");
		out.println(Settings.getReference().toString().replaceAll("<br>",  "\n"));
	
		out.close();	
	}
	
	/*
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
		str.append("<tr><td><b>rosmapinfo</b><td colspan=\"7\">" + state.get(values.rosmapinfo) +"</tr> \n");
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
		
	//	str.append("<tr><td><b>rosglobalpath</b><td colspan=\"10\">" + state.get(values.rosglobalpath) + "</tr> \n");
				
		str.append("\n</table>\n");
		return str.toString();
	}
	*/
	
	/*
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
		
		str.append("<tr><td><b>rosglobalpath</b><td colspan=\"10\">" + state.get(values.rosglobalpath) + "</tr> \n");
		str.append("\n</table>\n");
		return str.toString();
	}
	*/
	
	public String toDashboard(final String url){
		
		if(httpport == null) httpport = state.get(State.values.httpport);
		StringBuffer str = new StringBuffer("<table cellspacing=\"5\" border=\"0\"> \n");
		str.append("\n<tr><td colspan=\"11\"><b>v" + VERSION + "</b>&nbsp;&nbsp;" + Util.getJettyStatus().toLowerCase() + "</tr> \n");
		str.append("\n<tr><td colspan=\"11\">---------------------------------------------------------------------------------------------------------\n");
		//str.append("\n<tr><td colspan=\"11\"><hr></tr> \n");
		str.append("<tr><td><b>lan</b><td><a href=\"http://"+state.get(values.localaddress) 
			+"\" target=\"_blank\" title=\"go to network control panel\">" + state.get(values.localaddress) + "</a>");
		
		String ext = state.get(values.externaladdress);
		if( ext == null ) str.append("<td><b>wan</b><td>disconnected");
		else str.append("<td><b>wan</b><td><a href=\"http://"+ ext + ":" + httpport 
				+ "/oculusPrime/" +"\" target=\"_blank\" title=\"go to user interface on external address\">" + ext + "</a>");
		str.append( "<td><b>linux</b><td>" + Util.diskFullPercent() + "%</a> used</tr> \n"); 
		
		String dock = "<font color=\"blue\">undocked</font>";
		if(state.equals(values.dockstatus, AutoDock.DOCKED)) {
			dock = "<a href=\"dashboard?action=redock\" title=\"force re-dock the robot\">docked</a>";	
			routedistance = 0;
		}
		if(!state.getBoolean(values.odometry) || state.equals(values.dockstatus, AutoDock.DOCKED)) {
			dock = "<a href=\"dashboard?action=redock\" title=\"force re-dock the robot\">docked</a>";	
			routedistance = 0;
		}
		if(state.equals(values.dockstatus, AutoDock.DOCKED)) {
			dock = "<a href=\"dashboard?action=redock\" title=\"force re-dock the robot\">docked</a>";	
		}
		
		if(state.equals(values.dockstatus, AutoDock.DOCKING)) dock = "<font color=\"blue\">docking</font>";
		if(state.getBoolean(values.autodocking)) dock = "<font color=\"blue\">auto-docking</font>";
		
		String volts = state.get(values.batteryvolts); 
		if(volts == null) volts = "";
		else volts += "v ";
		
		String life =  state.get(values.batterylife);
		if(life == null) life = "";
		if(life.contains("%")) life = Util.formatFloat(life.substring(0, life.indexOf('%')+1), 1); 
		volts += ("&nbsp;&nbsp;" + life).trim();
		volts = volts.trim();
		
		String motor = " not connected";
		if(state.exists(values.motorport)) motor = state.get(values.motorport);
		str.append("<tr><td><b>motor</b><td>" + motor 
			+ "<td><b>linux</b><td>" + reboot + (((System.currentTimeMillis() 
			- state.getLong(values.linuxboot)) / 1000) / 60)+ "</a> mins " 
			+ "<td><b>prime</b><td>" + deletelogs + Util.countMbytes(".") + "</a> mb</tr> \n");
		
		String power = " not connected";
		if(state.exists(values.powerport)) power = state.get(values.powerport);
		str.append("<tr><td><b>power</b><td>" + power
			+ "<td><b>java</b><td>" + restart +(state.getUpTime()/1000)/60 + "</a> mins " 
			+ "<td><b>archive</b><td>"+ archivelogs + Util.countMbytes(Settings.archivefolder) + "</a> mb<td></tr> \n" );
			
		str.append("<tr><td><b>battery</b>&nbsp;<td>" + volts
			+ "<td><b>cpu</b><td>" + state.get(values.cpu) + "% "
			+ "<td><b>images</b><td>" + Util.countMbytes(Settings.framefolder) + " mb<td></tr> \n" );
		
		String od = "<a href=\"dashboard?action=startnav\" title=\"start ROS navigation \">on</a></font>&nbsp;&nbsp;|&nbsp;&nbsp;off"; 
		 if(state.getBoolean(values.odometry)) 
			 od = "<font color=\"blue\"><b>on</b></font>&nbsp;&nbsp;|&nbsp;&nbsp;<a href=\"dashboard?action=stopnav\" title=\"stop ROS navigation \">off</a>";
		
		String debug = "<a href=\"dashboard?action=debugon\">on</a>&nbsp;&nbsp;|&nbsp;&nbsp;off";
		if(settings.getBoolean(ManualSettings.debugenabled)) 
			debug = "<font color=\"blue\"><b>on</b></font>&nbsp;&nbsp;|&nbsp;&nbsp;<a href=\"dashboard?action=debugoff\">off</a>";
		
		str.append("<tr><td><b>nav</b><td>" + od
		    + "<td><b>telet</b><td>" + state.get(values.telnetusers) + " clients" 
			+ "<td><b>logs</b><td>" + Util.countMbytes(Settings.logfolder) + " mb<td></tr> \n" );
		
		str.append("<tr><td><b>debug</b><td>" + debug  
			+ "<td><b>dock</b><td>" + dock
			+ "<td><b>ros</b><td>" + Util.getRosCheck() + "</a> mb</tr> \n" );
			// doesn't work on hidden file? Util.countMbytes(Settings.roslogfolder)
		
	//	str.append("\n<tr><td colspan=\"11\">---------------------------------------------------------------------------------------------------------\n");
		str.append("<tr><td colspan=\"11\">"+ link + "</tr> \n");
	
		String r = getRouteLinks();
		if(r != null) str.append("<tr><td><b>routes</b>"+r+ "</tr> \n");
		
		String act = getActiveRoute();
		if(act != null) str.append("<tr><td><b>active</b>"+ getActiveRoute() + "</tr> \n");

		String msg = state.get(values.guinotify);
		if(msg == null) msg = "";
		else msg += "&nbsp;&nbsp;<a href=\"dashboard?action=gui\">(ignore)</a>";
		if(msg.length() > 1) {
			msg = "<tr><td><b>message</b><td colspan=\"11\">" + msg + "</tr> \n";
			str.append(msg);
		}
		
		str.append("\n<tr><td colspan=\"11\">---------------------------------------------------------------------------------------------------------\n");
		str.append("\n</table>\n");
		str.append(getTail(15) + "\n");
		return str.toString();
	}
	
	private String getRouteLinks(){   
	
		if( state.getBoolean(values.autodocking) 
		//		|| state.equals(values.dockstatus, AutoDock.DOCKING)
		//		|| ! state.exists(values.navigationroute)
				) return null;
	
		String link = "<td colspan=\"11\">";
		for (int i = 0; i < routes.getLength(); i++) {  
			String r = ((Element) routes.item(i)).getElementsByTagName("rname").item(0).getTextContent();
			if( ! state.equals(values.navigationroute, r)) 
				link += "<a href=\"dashboard?action=runroute&route="+r+"\">" +r+ "</a>&nbsp;&nbsp;";
		}
		return link + "<a href=\"dashboard?action=gotodock\" title=\"return to the dock\">dock</a>"; 
	}
	
	private String getActiveRoute(){  
			
		if( state.getBoolean(values.autodocking) 
				|| state.equals(values.dockstatus, AutoDock.DOCKING)
				|| ! state.exists(values.navigationroute)) return null;
		
		String link = "<td colspan=\"11\">";
		
		if(state.exists(values.navigationroute)) link += state.get(values.navigationroute) 
				+ " <a href=\"dashboard?action=resetstats&route="+ state.get(values.navigationroute) +"\">"
				+ " " + Navigation.getRouteCount(state.get(values.navigationroute))
				+ " " + Navigation.getRouteFails(state.get(values.navigationroute)); 
		

			
		if(state.equals(values.dockstatus, AutoDock.DOCKED) && !state.getBoolean(values.odometry)){
			if(state.exists(values.nextroutetime)) {
				return link + " | starting in "
				+((state.getLong(values.nextroutetime) - System.currentTimeMillis())/1000)+ "&nbsp;seconds&nbsp;&nbsp;" 
				+ "<a href=\"dashboard?action=cancel\" title=\"cancel sheduled route\">cancel</a></td></tr>";
			}
		} 
		
		//if(state.equals(values.dockstatus, AutoDock.DOCKED) || !state.getBoolean(values.odometry)){
	
		if(state.exists(values.roswaypoint)) link += "&nbsp;|&nbsp;waypoint " + state.get(values.roswaypoint);			
		if(routedistance > 0) link += "&nbsp;|&nbsp;distance " + Util.formatFloat((double)routedistance/(double)1000) + " meters";
		
		if(state.getBoolean(values.routeoverdue)) link += " <font color=\"blue\">**overdue**</font>";
		if(state.getBoolean(values.recoveryrotation)) link += " <font color=\"blue\">**recovery**</font>";
	
		link = link.trim();
		if(link.startsWith("|")) link = link.substring(1, link.length());
		
		return link.trim(); 
	}
	
	private String getTail(int lines){
		String reply = "\n\n<table cellspacing=\"2\" border=\"0\"> \n";
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
			reply += " " + Util.formatFloat(delta, 1) + "  " + unit + " " + mesg + "\n"; 
		}
		return reply;
	}
	
	@Override
	public void updated(String key) {

		if(key.equals(values.batteryinfo.name())) return;
		if(key.equals(values.batterylife.name())) return;
// 		if(key.equals(values.batteryvolts.name())) return;
//		if(key.equals(values.framegrabbusy.name())) return;
//		if(key.equals(values.rosglobalpath.name())) return;
//		if(key.equals(values.rosscan.name())) return;
// 		if(key.equals(values.cpu.name())) return; 
		
		if(key.equals(values.distanceangle.name())){
			try { 
				routedistance += Double.parseDouble(state.get(values.distanceangle).split(" ")[0]);
			} catch (Exception e){}
		}
		
		if(key.equals(values.docking)){
			if(state.getBoolean(values.docking)) routedistance = 0;
		}
		
		// trim size
		if(history.size() > MAX_STATE_HISTORY) history.remove(0);
		if(state.exists(key)) history.add(System.currentTimeMillis() + " " +key + " = " + state.get(key));
	}
}
