package oculusPrime;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
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
import developer.NavigationLog;
import oculusPrime.State.values;
import oculusPrime.commport.PowerLogger;

public class DashboardServlet extends HttpServlet implements Observer {
	
	static final long serialVersionUID = 1L;	
	static final String HTTP_REFRESH_DELAY_SECONDS = "7";
	
	static String restart = "<a href=\"dashboard?action=restart\">";
	static String reboot = "<a href=\"dashboard?action=reboot\">";
	static final String runroute = "<a href=\"dashboard?action=runroute\">";
	static final String managelogs = "<a href=\"dashboard?action=managelogs\">";
	
	static final String truncros = "<a href=\"dashboard?action=truncros\">";
	static final String truncimages = "<a href=\"dashboard?action=truncimages\">";
	static final String truncarchive = "<a href=\"dashboard?action=truncarchive\">";	
	
	static final String archiveros = "<a href=\"dashboard?action=archiveros\">";
	static final String archiveimages = "<a href=\"dashboard?action=archiveimages\">";
	static final String archivelogs = "<a href=\"dashboard?action=archivelogs\">";	
	
	static final String gotodock = "<a href=\"dashboard?action=gotodock\">dock</a>&nbsp;&nbsp;";

	static double VERSION = new Updater().getCurrentVersion();
	static Vector<String> history = new Vector<String>();
	static Application app = null;
	static NodeList routes = null;
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
		
		Document document = Util.loadXMLFromString(routesLoad());
		routes = document.getDocumentElement().getChildNodes();
	}

	public static void setApp(Application a) {app = a;}
	
	public static String routesLoad() {
		String result = "";
		BufferedReader reader;
		try {
			reader = new BufferedReader(new FileReader(Navigation.navroutesfile));
			String line = "";
			while ((line = reader.readLine()) != null) 	result += line;
			reader.close();	
		} catch (Exception e) {
			return "<routeslist></routeslist>";
		}

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
		 
		try {
			view = request.getParameter("view");
			delay = request.getParameter("delay");
			action = request.getParameter("action");
			route = request.getParameter("route");
		} catch (Exception e) {}
			
		if(delay == null) delay = HTTP_REFRESH_DELAY_SECONDS;
		
		if(action != null&& app != null){

			if(action.equalsIgnoreCase("gui")) state.delete(values.guinotify);
			if(action.equalsIgnoreCase("reboot")) app.driverCallServer(PlayerCommands.reboot, null);
			if(action.equalsIgnoreCase("restart")) app.driverCallServer(PlayerCommands.restart, null);
			if(action.equalsIgnoreCase("truncros")) app.driverCallServer(PlayerCommands.truncros, null);
			if(action.equalsIgnoreCase("gotodock")) app.driverCallServer(PlayerCommands.gotodock, null);
			if(action.equalsIgnoreCase("managelogs")) app.driverCallServer(PlayerCommands.archive, null);			
			if(action.equalsIgnoreCase("cancel")) app.driverCallServer(PlayerCommands.cancelroute, null);
			if(action.equalsIgnoreCase("archiveros")) app.driverCallServer(PlayerCommands.archiveros, null);
			if(action.equalsIgnoreCase("truncimages")) app.driverCallServer(PlayerCommands.truncimages, null);
			if(action.equalsIgnoreCase("archivelogs")) app.driverCallServer(PlayerCommands.archivelogs, null);
			if(action.equalsIgnoreCase("truncarchive")) app.driverCallServer(PlayerCommands.truncarchive, null);
			if(action.equalsIgnoreCase("archiveimages")) app.driverCallServer(PlayerCommands.archiveimages, null);
			if(route != null)if(action.equalsIgnoreCase("runroute")) app.driverCallServer(PlayerCommands.runroute, route);
			if(action.equalsIgnoreCase("debugon")) app.driverCallServer(PlayerCommands.writesetting, ManualSettings.debugenabled.name() + " true");
			if(action.equalsIgnoreCase("debugoff")) app.driverCallServer(PlayerCommands.writesetting, ManualSettings.debugenabled.name() + " false");

			if(action.equalsIgnoreCase("snapshot")) {	
				if(Util.archivePID()) {
					Util.log("busy, skipping..", this);
					return;
				}
				Util.archiveFiles("./archive" + Util.sep + "snapshot_"+System.currentTimeMillis() 
					+ ".tar.bz2", new String[]{NavigationLog.navigationlogpath, state.dumpFile("dashboard command")});
			}
			
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
			
			if(view.equalsIgnoreCase("stdout")){
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
		
	//	str.append("<tr><td><b>rosmapwaypoints</b><td colspan=\"7\">" + state.get(values.rosmapwaypoints) );
		str.append("<tr><td><b>rosglobalpath</b><td colspan=\"10\">" + state.get(values.rosglobalpath) + "</tr> \n");
		str.append("\n</table>\n");
		return str.toString();
	}
	
	public String toDashboard(final String url){
		
		if(httpport == null) httpport = state.get(State.values.httpport);
		StringBuffer str = new StringBuffer("<table cellspacing=\"7\" border=\"0\"> \n");
		str.append("\n<tr><td colspan=\"11\"><b>v" + VERSION + "</b>&nbsp;&nbsp;" + Util.getJettyStatus().toLowerCase() + "</tr> \n");
		str.append("\n<tr><td colspan=\"11\"><hr></tr> \n");
		str.append("<tr><td><b>lan</b><td><a href=\"http://"+state.get(values.localaddress) 
			+"\" target=\"_blank\">" + state.get(values.localaddress) + "</a>");
		
		String ext = state.get(values.externaladdress);
		if( ext == null ) str.append("<td><b>wan</b><td>disconnected");
		else str.append("<td><b>wan</b><td><a href=\"http://"+ ext + ":" + httpport 
				+ "/oculusPrime/" +"\" target=\"_blank\">" + ext + "</a>");
		str.append( "<td><b>linux</b><td colspan=\"2\">" + Util.diskFullPercent() + "% used</tr> \n"); 
		
		String dock = "<font color=\"blue\">undocked</font>";
		if(state.equals(values.dockstatus, AutoDock.DOCKED)) dock = "docked";		
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
			+ "<td><b>prime</b><td colspan=\"2\">" + Util.countMbytes(".") + " mbytes </a></tr> \n");
		
		String power = " not connected";
		if(state.exists(values.powerport)) power = state.get(values.powerport);
		str.append("<tr><td><b>power</b><td>" + power
			+ "<td><b>java</b><td>" + restart + (state.getUpTime()/1000)/60  + "</a> mins"
			+ "<td><b>archive</b><td>" + managelogs + Util.countMbytes(Settings.archivefolder) 
			+ "</a> mb <td>" + truncarchive + "x</a>&nbsp;&nbsp;&nbsp;&nbsp;</tr> \n");
			
		str.append("<tr><td><b>battery</b>&nbsp;<td>" + volts
			+ "<td><b>dock</b><td>" + dock
			+ "<td><b>images</b><td>" + archiveimages + Util.countMbytes(Settings.framefolder) + "</a> mb <td>" 
			+ truncimages + "x</a>&nbsp;&nbsp;&nbsp;&nbsp;</tr> \n");
		
		String od = "disabled"; String debug = null; 
		if(state.getBoolean(values.odometry)) od = "enabled";
		if(settings.getBoolean(ManualSettings.debugenabled)) debug = "on&nbsp;|&nbsp;<a href=\"dashboard?action=debugoff\">off</a>";
		else debug = "off&nbsp;|&nbsp;<a href=\"dashboard?action=debugon\">on</a>";
		
		str.append("<tr><td><b>odometry&nbsp;</b><td>" + od
		    + "<td><b>debug</b><td>" + debug 
			+ "<td><b>logs</b><td>" + archivelogs  
			+ Util.countMbytes(Settings.logfolder) + "</a> mb <td>" + truncarchive
			+ "x</a>&nbsp;&nbsp;&nbsp;&nbsp;</tr> \n");
		
		str.append("<tr><td><b>telet</b><td>" + state.get(values.telnetusers) + " clients"
			+ "<td><b>cpu</b><td>" + Util.getCPU() + "% "
			+ "<td><b>ros</b><td>" + archiveros + Util.getRosCheck() + "</a> mb<td>" 
			+ truncros +"x</a>&nbsp;&nbsp;&nbsp;&nbsp;</tr> \n");
			// doesn't work on hidden file?? 
			//	+ Util.countMbytes(Settings.roslogfolder) + "</a> mbytes (" 
			//	+ Util.countFiles(Settings.roslogfolder) 
		
		str.append("<tr><td colspan=\"11\"><hr></tr> \n");
		str.append("<tr><td colspan=\"11\">"+ getCommands() + "</tr> \n");
	
		String msg = state.get(values.guinotify);
		if(msg == null) msg = "";
		else msg += "&nbsp;&nbsp;<a href=\"dashboard?action=gui\">(ignore)</a>";
		if(msg.length() > 1) msg = "<br><b>user message: </b>&nbsp;&nbsp;" + msg;
		str.append("<tr><td colspan=\"11\">"+ getRouteLinks() + msg + "</tr> \n");
//		str.append("<tr><td colspan=\"11\"><hr></tr> \n");	
		str.append("<tr><td colspan=\"11\"><hr></tr> \n");	
		str.append("\n</table>\n");
		str.append(getTail(20) + "\n");
		
// TODO: toggle view 
//		str.append(getHistory() + "\n");
		
		return str.toString();
	}
	
	private String getRouteLinks(){     
		String link = "<b>routes: </b>&nbsp";//;<a href=\"navigationlog/index.html\" target=\"_blank\">log</a>&nbsp;&nbsp;";
		for (int i = 0; i < routes.getLength(); i++) {  
			String r = ((Element) routes.item(i)).getElementsByTagName("rname").item(0).getTextContent();
			if( ! state.equals(values.navigationroute, r)) 
				link += "<a href=\"dashboard?action=runroute&route="+r+"\">" +r+ "</a>&nbsp;&nbsp;";
		}
		
		if( ! state.equals(values.dockstatus, AutoDock.DOCKED)) link += gotodock;
	
		if(state.exists(values.navigationroute)){ // active route 
			link += " <b>active: </b>" + state.get(values.navigationroute) + "&nbsp;"; 
		}
		
		if(state.exists(values.navigationroute) && state.exists(values.roswaypoint)){
			if( ! state.equals(values.dockstatus, AutoDock.DOCKED)) link += " | "+ state.get(values.roswaypoint) + "&nbsp;";
			else if(state.exists(values.nextroutetime)) 
				link += ((state.getLong(values.nextroutetime) - System.currentTimeMillis())/1000) + "&nbsp;seconds&nbsp;"
						+ "<a href=\"dashboard?action=cancel\">x</a>";
		}
		return link; 
	}
	
	private String getCommands(){     
		String link = 
				"<b>commands: </b>&nbsp;"+	
				"<a href=\"navigationlog/index.html\" target=\"_blank\">navigation log</a>&nbsp;"+
				"<a href=\"dashboard?action=snapshot\">snaphot</a>&nbsp"
			//	"<a href=\"dashboard?action=viewstate\">state</a> | log | stdot | power&nbsp;"
			
			;
			
		return link; 
	}
	 
	private String getTail(int lines){
		String reply = "\n\n<table style=\"max-width:640px;\" cellspacing=\"2\" border=\"0\"> \n";
		reply += Util.tailFormated(lines) + " \n";
		reply += ("\n</table>\n");
		return reply;
	}
	
	private String getHistory(){
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

	@Override
	public void updated(String key) {
		if(key.equals(values.batteryinfo.name())) return;
		if(key.equals(values.batterylife.name())) return;
		if(key.equals(values.batteryvolts.name())) return;
		if(key.equals(values.framegrabbusy.name())) return;
		if(key.equals(values.rosglobalpath.name())) return;
		if(key.equals(values.rosscan.name())) return;
 		if(key.equals(values.cpu.name())) return; 
 		
 		/* {
 			cpuHistory.add(state.getDouble(values.cpu));
 			if(cpuHistory.size() > CPU_HISTORY) cpuHistory.remove(0); 
 			
 			cpuAVG = 0; String j = "";
 			for(int i = 0 ; i < cpuHistory.size() ; i++){
 				cpuAVG +=  cpuHistory.get(i);
 				j +=  Util.formatFloat(cpuHistory.get(i), 0) + ", ";
 			}
 			cpuAVG = cpuAVG / cpuHistory.size();
 			Util.debug(" " + j, this);
 			return;
 		}*/
		
		if(history.size() > 10) history.remove(0);
		if(state.exists(key)) history.add(System.currentTimeMillis() + " " +key + " = " + state.get(key));
	}
}
