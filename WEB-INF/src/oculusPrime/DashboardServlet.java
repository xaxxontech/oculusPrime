package oculusPrime;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import developer.Navigation;
import developer.NavigationUtilities;
import oculusPrime.State.values;
import oculusPrime.commport.PowerHistory;

public class DashboardServlet extends HttpServlet implements Observer {

	static final String viewslinks =
			"<a href=\"navigationlog/index.html\" target=\"_blank\">navigation log</a>\n"+
					"<a href=\"/oculusPrime/media\" target=\"_blank\">media files</a>" +
					"<a href=\"dashboard?view=routes\" target=\"_blank\">route stats</a>\n" +
					"<a href=\"dashboard?view=users\" target=\"_blank\">users</a>\n" +
					"<a href=\"dashboard?view=stdout\"target=\"_blank\">stdout</a>\n" +
					"<a href=\"dashboard?view=history\" target=\"_blank\">history</a>\n" +
					"<a href=\"dashboard?action=snapshot\" target=\"_blank\">snaphot</a>\n"+
					"<a href=\"dashboard?view=state\">state</a>\n";

	static final String commandlinks =
			"<a href=\"dashboard?action=save\">save snapshot</a>" +
					"<a href=\"dashboard?action=archivelogs\">zip log folder</a>\n" +
					"<a href=\"dashboard?action=archivemedia\">zip media</a>\n" +
					"<a href=\"dashboard?action=archivenavigation\">zip navigation</a>\n" +
					"<a href=\"dashboard?action=email\">send email</a>\n" +
					"<a href=\"dashboard?action=restart\">restart java</a>" +
					"<a href=\"dashboard?action=reboot\">reboot linux</a>" ;

	static final String loglinks =

			"<a href=\"dashboard?action=resetnavstats\">reset nav stats</a>" +
					"<a href=\"dashboard?action=truncmedia\">truncate media</a>" +
					"<a href=\"dashboard?action=trunclogs\">truncate logs</a>" +
					"<a href=\"dashboard?action=deletelogs\"><font color=\"#8533ff\">DELETE LOGS</font></a>\n";

	static final long serialVersionUID = 1L;
	private static final int MAX_LINE_LENGTH = 83;
	private static boolean DEBUG = false; // turn on for extra state debugging

	static Settings settings = Settings.getReference();
	static BanList ban = BanList.getRefrence();
	static State state = State.getReference();
	static final double VERSION = new Updater().getCurrentVersion();
	//	static final int restarts = settings.getInteger(ManualSettings.restarted);
	static final long DELAY = Util.ONE_MINUTE;
	static final String css = getCSS();

	private static final int MAX_STATE_HISTORY = 40;
	Vector<String> history = new Vector<String>();
	Vector<String> pointslist;
	Vector<PyScripts> pids;
	static Application app = null;
	String httpport = null;
	String estimatedmeters;
	String estimatedseconds;
	long allBytes;
	long streams;
	long archive;
	long frames;
	long time;
	long logs;
	int hdd;

	public static void setApp(Application a){app = a;}

	public static String getCSS(){
		StringBuffer buffer = new StringBuffer();
		/*
		try {	
			String line = null;
			FileInputStream filein = new FileInputStream(System.getenv("RED5_HOME") + "/webapps/oculusPrime/dashboard.css");
			BufferedReader reader = new BufferedReader(new InputStreamReader(filein));
			while ((line = reader.readLine()) != null) buffer.append(line + "\n");
			reader.close();
			filein.close();
		} catch (Exception e) { Util.log("getCSS(): " + e.getMessage()); }
		*/
		buffer.append("body, p, ol, ul {font-family: verdana, arial, helvetica, sans-serif; font-size: 16px;}");
		buffer.append(".dropbtn { border: none; cursor: pointer; font-family: verdana, arial, helvetica, sans-serif; text-align: left; background-color: #fffff0; padding: 0px; font-size: 16px; } \n");
		buffer.append(".dropdown { position: relative; display: inline-block; } \n");
		buffer.append(".dropdown-content { font-size: 18px; display: none; position: absolute; background-color: #ccd9ff; min-width: 170px;box-shadow: 0px 8px 16px 0px rgba(0,0,0,0.2); } \n");
		buffer.append(".dropdown-content a { color: black; padding: 5px 5px; text-decoration: none; display: block; } \n");
		buffer.append(".dropdown:hover .dropdown-content { display: block; } \n");
		buffer.append(".menu {	background-color: #c2d6d6; border: 1px solid grey; font-size: 16px;	min-width: 100px; max-width: 100px;	} \n");
		buffer.append(".data { background-color: #fffgfg; font-size: 16px;	} \n");
		buffer.append(".busy {	border: 3px solid red; } \n");
		buffer.append(".on { border: 3px solid blue; } \n");
		buffer.append(".off { border: 2px solid grey; } \n");
		buffer.append(".tail { #border-collapse: collapse;	border: 0px solid; font-size: 15px; } \n");
		buffer.append("table { border: 0px solid grey; min-width: 800px; max-width: 800px; padding: 3px; } \n");
		buffer.append("td { border: 2px solid grey; } \n");
		buffer.append("a { text-decoration: none; } \n");
		return buffer.toString();
	}

	private class Task extends TimerTask {
		public void run() { readFileSizes(); }
	}

	void readFileSizes(){
		allBytes = Util.countAllMbytes(".");
		streams = Util.countAllMbytes(Settings.streamfolder);
		archive = Util.countFilesMbytes("./log/archive");
		frames = Util.countAllMbytes(Settings.framefolder);
		logs = Util.countFilesMbytes(Settings.logfolder);
		hdd = Util.diskFullPercent();
	}

	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		state = State.getReference();
		httpport = state.get(State.values.httpport);
		settings = Settings.getReference();
		ban = BanList.getRefrence();
		new Timer().scheduleAtFixedRate(new Task(), 6000, DELAY);
		state.addObserver(this);
	}

	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}

	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		if( ! settings.getBoolean(ManualSettings.developer.name())){
			Util.debug("dangerous.. not in developer mode: "+request.getRemoteAddr(), this);
			response.sendRedirect("/oculusPrime");
			return;
		}

		if( ! ban.knownAddress(request.getRemoteAddr())){
			Util.log("unknown address: sending to login: "+request.getRemoteAddr(), this);
			response.sendRedirect("/oculusPrime");
			return;
		}

		String r = null;
		String view = null;
		String a = null;
		String p = null;
		String m = null;

		try {
			view = request.getParameter("view");
			a = request.getParameter("action");
			r = request.getParameter("route");
			p = request.getParameter("pid");
			m = request.getParameter("member");

		} catch (Exception e) {}

		final String action = a;
		final String route = r;
		final String pid = p;
		final String member = m;

		if(action != null && app != null) {
			new Thread(new Runnable() { public void run() {
				if(action.equalsIgnoreCase("debugon"))    app.driverCallServer(PlayerCommands.writesetting, ManualSettings.debugenabled.name() + " true");
				if(action.equalsIgnoreCase("debugoff"))   app.driverCallServer(PlayerCommands.writesetting, ManualSettings.debugenabled.name() + " false");
				if(action.equalsIgnoreCase("startrec"))   app.driverCallServer(PlayerCommands.record, "true dashboard");
				if(action.equalsIgnoreCase("dockcancel")) app.driverCallServer(PlayerCommands.autodock, "cancel");
				if(action.equalsIgnoreCase("motor"))      app.driverCallServer(PlayerCommands.motorsreset, null);
				if(action.equalsIgnoreCase("gotodock"))   app.driverCallServer(PlayerCommands.gotodock, null);
				if(action.equalsIgnoreCase("stoprec"))    app.driverCallServer(PlayerCommands.record, "false");
				if(action.equalsIgnoreCase("startnav"))   app.driverCallServer(PlayerCommands.startnav, null);
				if(action.equalsIgnoreCase("stopnav"))    app.driverCallServer(PlayerCommands.stopnav, null);
				if(action.equalsIgnoreCase("script"))     Util.systemCall("python telnet_scripts/" + pid);
				if(action.equalsIgnoreCase("gui"))        state.delete(values.guinotify);
				if(action.equalsIgnoreCase("state"))      state.delete(member);
				if(action.equalsIgnoreCase("email"))      sendEmail();


				if(action.equalsIgnoreCase("batterylog")){

					Vector lines = PowerHistory.getFile(5);
					for( int i = 0 ; i < lines.size() ; i++ )
						Util.log("file: "+lines.get(i), "DashboardServlet.doGet()");
						
					/*
					Vector<PowerHistory> tt = PowerHistory.getTail(9);
					// Util.debug("size: " + tt.size(), this);
				
					for( int i = 0 ; i < tt.size() ; i++ ) app.driverCallServer(PlayerCommands.log,  tt.get(0).toString()); 
					
					// Util.debug(i + " " + tt.get(0));
					// Util.debug("size: " + tt.size());
					*/

					Vector<PowerHistory> tt = PowerHistory.getTail(9);

					// Util.debug("---------------------undocked---------------------");
					// Vector<String> t = BatteryStatus.getUnDockedString(5);
					// Util.debug("size: " + t.size());
					// for( int i = 0 ; i < t.size() ; i++ ) Util.debug((String)t.get(i));

					// Util.debug("---------------------charging---------------------");
					// t = BatteryStatus.getChargingString(5);
					// Util.debug("size: " + t.size());
					// for( int i = 0 ; i < t.size() ; i++ ) Util.debug((String)t.get(i));
					// Util.delay(300);
					// try { response.sendRedirect("/oculusPrime/dashboard"); } catch (IOException e) { e.printStackTrace(); } 



				}

				if(action.equalsIgnoreCase("power")){
					app.driverCallServer(PlayerCommands.powercommand, "4");
					Util.delay(1300);
					app.driverCallServer(PlayerCommands.powerreset, null);
				}

				if(action.equalsIgnoreCase("truncmedia")){
					Util.log("truncmedia", this);
					app.driverCallServer(PlayerCommands.truncmedia, null);
					readFileSizes();
				}

				if(action.equalsIgnoreCase("trunclogs")){
					Util.log("trunclogs", this);
					Util.truncStaleArchive();
					Util.truncStaleLog();
					readFileSizes();
				}

				if(action.equalsIgnoreCase("deletelogs")){
					Util.log("deletelogs", this);
					app.driverCallServer(PlayerCommands.deletelogs, null);
					readFileSizes();
				}

				if(action.equalsIgnoreCase("archivenavigation")){
					Util.log("archivenav", this);
					app.driverCallServer(PlayerCommands.archivenavigation, null);
					readFileSizes();
				}

				if(action.equalsIgnoreCase("archivemedia")){
					Util.log("archivemedia", this);
					Util.archiveStreams();
					Util.archiveImages();
					readFileSizes();
				}

				if(action.equalsIgnoreCase("archivelogs")){
					Util.log("archivelogs", this);
					Util.archiveLogFiles();
					readFileSizes();
				}

				if(action.equalsIgnoreCase("resetnavstats")){
					Util.log("resetnavstats", this);
					NavigationUtilities.resetAllRouteStats();
				}


				if(action.equalsIgnoreCase("camon")){
					app.driverCallServer(PlayerCommands.publish, "camera");
					Util.delay(4000); // TODO: BETTER WAY TO KNOW IF CAMERA IS ON?
//					if(Navigation.turnLightOnIfDark()) Util.log("light was turned on because was dark", this);
				}

				if(action.equalsIgnoreCase("camoff")) {
					app.driverCallServer(PlayerCommands.publish, "stop");
					app.driverCallServer(PlayerCommands.spotlight, "0");
				}

				if( route != null ){
					if(action.equalsIgnoreCase("gotowp")) app.driverCallServer(PlayerCommands.gotowaypoint, route);
					if(action.equalsIgnoreCase("runroute")) app.driverCallServer(PlayerCommands.runroute, route);
				}

				if(action.equalsIgnoreCase("cancel")) {
					app.driverCallServer(PlayerCommands.move, "stop");
					app.driverCallServer(PlayerCommands.cancelroute, null);
					Util.delay(3000);
					app.driverCallServer(PlayerCommands.gotodock, null);
				}

				if(action.equalsIgnoreCase("tail")){
					String log = PyScripts.NONE;
					for( int i = 0 ; i < pids.size() ; i++ ) if(pids.get(i).pid.equals(pid)) log = pids.get(i).logFile;
					if( ! log.equals(PyScripts.NONE)){
						Util.log("[" + pid + "] " + log, "dashboardservlet");
						Vector<String> txt = Util.tail(log, 5);
						for( int i = 0 ; i < txt.size() ; i++)
							Util.log("["+pids.get(i).pyFile + "] " + (String) txt.get(i), "dashboardservlet");
					}
				}

				if(action.equalsIgnoreCase("kill")){
					if(pid.equals("pkill"))	Util.systemCall("pkill python");
					else Util.systemCall("kill -SIGINT " + pid);
				}

				if(action.equalsIgnoreCase("reboot")){
					app.driverCallServer(PlayerCommands.move, "stop");
					Util.log("reboot called, going down..", this);
					Util.appendUserMessage("reboot called, going down..");
					Util.delay(3000); // redirect before calling.. 
					app.driverCallServer(PlayerCommands.reboot, null);
				}

				if(action.equalsIgnoreCase("restart")){
					app.driverCallServer(PlayerCommands.move, "stop");
					Util.log("restart called, going down..", this);
					Util.appendUserMessage("restart called, going down..");
					Util.delay(2000); // redirect before calling.. 
					app.driverCallServer(PlayerCommands.restart, null);
				}

				if(action.equalsIgnoreCase("redock")) {
					if(state.equals(values.dockstatus, AutoDock.DOCKED)) app.driverCallServer(PlayerCommands.redock, null);
					else app.driverCallServer(PlayerCommands.redock, SystemWatchdog.NOFORWARD);
				}

				if(action.equalsIgnoreCase("save")) {
					if( ! new Downloader().FileDownload("http://" + state.get(values.localaddress)
							+ ":" + httpport + "/oculusPrime/dashboard?action=snapshot", "snapshot_"+ System.currentTimeMillis() +".txt", "log"))
						Util.log("snapshot save failed", this);
				}


			}}).start();

			// different views
			if(action.equalsIgnoreCase("snapshot")) {
				try { sendSnap(request, response); } catch (Exception e) { Util.printError(e);	}
				return;
			}

			response.sendRedirect("/oculusPrime/dashboard");
		}

		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
	
		/* add mobile css 
		 if(request.getHeader("User-Agent").indexOf("Mobile") != -1) {
			 
			 	Util.log("you're in mobile land");
			 	response.sendRedirect("/oculusPrime/dashboard?view=drive"); 
			 
				// default view 
				out.println("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n");
				out.println("<html>\n<head><meta http-equiv=\"refresh\" content=\"30\">\n<title>Oculus Prime</title>\n<style type=\"text/css\">"+ css +"</style></head>\n<body> \n");
				out.println(toDashboard(request.getServerName()+":"+request.getServerPort() + "/oculusPrime/dashboard") + "\n");
				out.println("\n  mobile view here \n");

				out.println("\n</body></html>\n");
				out.close();	
			  } */

		if(view != null){
			
			/*
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
			*/

			if(view.equalsIgnoreCase("users")){
				out.println("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n");
				out.println("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1252\"><title>Oculus Prime log file</title> \n");
				out.println("<style type=\"text/css\">");
				out.println("body, p, ol, ul {font-family: verdana, arial, helvetica, sans-serif; font-size: 12px;}");
				out.println("th, td { text-align: left; padding: 5px; }");
				out.println("tr:nth-child(even){background-color: #f2f2f2}");
				out.println("th { background-color: #4CAF50; color: white; }");
				out.println("</style><html><body>\n\n");
				out.println(LoginRecords.getReference().geHTML() +"\n\n");
				out.println("\n<table cellspacing=\"5\">\n<tbody><tr><th>LINUX<th>info</tr>\n");
				Vector<String> who = Util.getLinuxWho();
				for (int i = 0; i < who.size(); i++){
					String user = who.get(i).split(" ")[0].trim();
					String str =  who.get(i).toString().replace(user, "").trim();
					out.println("<tr><td>"+user + " <td>"+str+"\n");
				}
				out.println("</tbody></table>\n\n");
				out.println(ban.geHTML() + "<br />\n");
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
				out.println(NavigationUtilities.getRouteStatsHTML() + "\n");
				out.println("\n</body></html> \n");
				out.close();
			}

			if(view.equalsIgnoreCase("stdout")){
				out.println("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n");
				out.println("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1252\"><title>Oculus Prime log file</title> \n");
				out.println("<style type=\"text/css\">");
				out.println("body, p, ol, ul {font-family: verdana, arial, helvetica, sans-serif; font-size: 12px;}");
				out.println("th, td { text-align: left; padding: 5px; }");
				out.println("tr:nth-child(even){background-color: #f2f2f2}");
				out.println("th { background-color: #4CAF50; color: white; }");
				out.println("</style><html><body>\n");
				out.println("\n");
				out.println("<table cellspacing=\"5\"><tbody>");

				Vector<String> tail = Util.tail(Settings.stdout, 25);
				for( int j = 0 ; j < tail.size() ; j++) {
					String txt =  tail.get(j);
//					txt = Util.trimLength(txt, MAX_LINE_LENGTH);
					txt = txt.replaceFirst("oculusPrime.", "");
					txt = txt.replaceFirst(",", "");

					// TODO: CSS 
					if(txt.startsWith("OCULUS:"))     out.println("<tr><td>oculus<td>"+ Util.trimLength(txt.substring(7), MAX_LINE_LENGTH) +"\n");
					else if(txt.startsWith("DEBUG:")) out.println("<tr><td>debug<td>" + Util.trimLength(txt.substring(6), MAX_LINE_LENGTH) +"\n");
					else if(txt.startsWith("[INFO]")) out.println("<tr><td>info<td>"  + Util.trimLength(txt.substring(6), MAX_LINE_LENGTH) +"\n");
					else if(txt.startsWith("[WARN]")) out.println("<tr><td>warn<td>"  + Util.trimLength(txt.substring(6), MAX_LINE_LENGTH) +"\n");

					else out.println("<tr><td colspan=\"5\">"+ txt +"\n"); // single line
					
					/*
					 * 
					// TODO: CSS 
					if(txt.startsWith("OCULUS:")) out.println("<tr><td>oculus<td>"+ txt.substring(7) +"\n");
					else if(txt.startsWith("DEBUG:")) out.println("<tr><td>debug<td>"+ txt.substring(6) +"\n");
					else if(txt.startsWith("[INFO]")) out.println("<tr><td>info<td>"+ txt.substring(6) +"\n");
					else if(txt.startsWith("[WARN]")) out.println("<tr><td>warn<td>"+ txt.substring(6) +"\n");

					else out.println("<tr><td colspan=\"5\">"+ txt +"\n");
					 */
				}

				out.println("</tbody></table>");
				out.println("\n</body></html> \n");
				out.close();
			}

			/*
			if(view.equalsIgnoreCase("power")){	
				out.println("<html><head></head><body> \n");
				out.println("<a href=\"dashboard\">dashboard</a>\n"+ new File(PowerLogger.powerlog).getAbsolutePath() + "<br /><br />\n");
 				out.println(PowerLogger.tail(45) + "\n");
				out.println("\n</body></html> \n");
				out.close();
			}*/

			if(view.equalsIgnoreCase("history")){
				out.println("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n");
				out.println("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1252\"><title>state history</title> \n");
				out.println("<style type=\"text/css\">");
				out.println("body, p, ol, ul {font-family: verdana, arial, helvetica, sans-serif; font-size: 12px;}");
				out.println("th, td { text-align: left; padding: 5px; }");
				out.println("tr:nth-child(even){background-color: #f2f2f2}");
				out.println("th { background-color: #4CAF50; color: white; }");
				out.println("</style><html><body>\n");
				out.println("\n");
				out.println("<table cellspacing=\"5\"><tbody>");

				Calendar c = Calendar.getInstance();
				for(int i = 0 ; i < history.size() ; i++){
					String[] line = history.get(i).split(" ");
					c.setTimeInMillis(Long.parseLong(line[0]));
					out.println("<tr><td>"+ c.getTime() + "<td>" + line[1] + "<td>" + line[2] +"\n");
				}

				out.println("\n<table><tbody></body></html>\n");
				out.close();
			}
		}

		// default view 
		out.println("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n");
		out.println("<html>\n<head><meta http-equiv=\"refresh\" content=\"20\">\n<title>Oculus Prime</title>\n<style type=\"text/css\">"+ css +"</style></head>\n<body> \n");
		out.println(toDashboard(request.getServerName()+":"+request.getServerPort() + "/oculusPrime/dashboard") + "\n");
		out.println("\n</body></html>\n");
		out.close();
	}

	public void sendEmail(){
		new Thread(new Runnable() { public void run() {
			StringBuffer text = new StringBuffer();

			text.append("\n\r-- " + new Date() + " --\n");
			Vector<String> std = Util.tail(Settings.stdout, 60);
			for(int i = 0 ; i < std.size() ; i++) text.append(std.get(i)/*.replaceAll("&nbsp;", " ")*/ + "\n");

			text.append("\n\r-- ros log --\n");
			std = Util.tail(Settings.logfolder+"/ros.log", 60);
			for(int i = 0 ; i < std.size() ; i++) text.append(std.get(i)/*.replaceAll("&nbsp;", " ")*/ + "\n");

			text.append("\n\r -- state history --\n");
			text.append(getHistory() + "\n\r");
			text.append("\n\r -- state values -- \n");
			text.append(state.toString().replaceAll("<br>", "\n"));
//			text.append("\n\r -- battery --\n");
//			text.append(PowerLogger.tail(45) + "\n");
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
		Vector<String> std = Util.tail(Settings.stdout, 60);
		for(int i = 0 ; i < std.size() ; i++) out.println(std.get(i) + "\n");
//		out.println(Util.tail(999).replaceAll("<br>", "\n"));
		out.println("\n\r -- state history --\n\r");
		out.println(getHistory() + "\n\r");
		out.println("\n\r -- state values -- \n\r");
		out.println(state.toString().replaceAll("<br>", "\n"));
		out.println("\n\r -- settings --\n\r");
		out.println(Settings.getReference().toString().replaceAll("<br>",  "\n"));
		out.println("\n\r -- route stats -- \n\r");
		out.println(NavigationUtilities.getRouteStats() + "\n");
//		out.println("\n\r -- battery info -- \n\r");
//		out.println(PowerLogger.tail(99).replaceAll("<br>",  "\n") + "\n");
		out.close();
	}

	public String toDashboard(final String url){

		String motor, power;
		if(state.exists(values.powerport)) power = "<a href=\"dashboard?action=power\" >"+state.get(values.powerport)+"</a>";
		else  power = "<a href=\"dashboard?action=power\" >connect power</a>";
		if(state.exists(values.motorport)) motor = "<a href=\"dashboard?action=motor\" >"+state.get(values.motorport)+"</a>";
		else motor = "<a href=\"dashboard?action=motor\" >connect motors</a>";

		String rec = state.get(values.record);
		if(rec == null) rec = "<td class='menu'>&nbsp;&nbsp;record<td class='off'><a href=\"dashboard?action=startrec\">&nbsp;&nbsp;on</a>&nbsp;&nbsp;|&nbsp;&nbsp;<a href=\"dashboard?action=stoprec\">off</a>";
		else {
			if(rec.contains("cam")){
				rec = "<td class='menu'>&nbsp;&nbsp;record<td class='on'><a href=\"dashboard?action=startrec\">&nbsp;&nbsp;on</a>&nbsp;&nbsp;|&nbsp;&nbsp;<a href=\"dashboard?action=stoprec\">off</a>";
			} else {
				rec = "<td class='menu'>&nbsp;&nbsp;record<td class='off'><a href=\"dashboard?action=startrec\">&nbsp;&nbsp;on</a>&nbsp;&nbsp;|&nbsp;&nbsp;<a href=\"dashboard?action=stoprec\">off</a>";
			}
		}

		String cam = state.get(values.stream);
		if(cam == null) cam = "";
		else {
			if(cam.contains("cam")) {
				cam = "<td class='menu'>&nbsp;&nbsp;camera<td class='on'><a href=\"dashboard?action=camon\">&nbsp;&nbsp;on</a>&nbsp;&nbsp;|&nbsp;&nbsp;<a href=\"dashboard?action=camoff\">off</a>";
			} else {
				cam = "<td class='menu'>&nbsp;&nbsp;camera<td class='off'><a href=\"dashboard?action=camon\">&nbsp;&nbsp;on</a>&nbsp;&nbsp;|&nbsp;&nbsp;<a href=\"dashboard?action=camoff\">off</a>";
			}
		}

		String od = "<td class='menu'>&nbsp;&nbsp;nav<td class='off'><a href=\"dashboard?action=startnav\"\">&nbsp;&nbsp;on&nbsp;&nbsp;</a>|&nbsp;&nbsp;off";
		if(state.getBoolean(values.odometry))
			od = "<td class='menu'>&nbsp;&nbsp;nav<td class='on'>&nbsp;&nbsp;on&nbsp;&nbsp;|&nbsp;&nbsp;<a href=\"dashboard?action=stopnav\">off</a>";

		String debug = "<td class='menu'>&nbsp;&nbsp;debug<td class='off'><a href=\"dashboard?action=debugon\">&nbsp;&nbsp;on&nbsp;&nbsp;</a>|&nbsp;&nbsp;off";
		if(settings.getBoolean(ManualSettings.debugenabled))
			debug = "<td class='menu'>&nbsp;&nbsp;debug<td class='on'>&nbsp;&nbsp;on&nbsp;&nbsp;|&nbsp;&nbsp;<a href=\"dashboard?action=debugoff\">off</a>";

		if(httpport == null) httpport = state.get(State.values.httpport);
		String dock = "undocked>";
		if(state.equals(values.dockstatus, AutoDock.DOCKED)) dock = "<a href=\"dashboard?action=redock\" title=\"force re-dock the robot\">docked</a>";

		if(!state.getBoolean(values.odometry) || state.equals(values.dockstatus, AutoDock.DOCKED))
			dock = "<a href=\"dashboard?action=redock\" title=\"force re-dock the robot\">docked</a>";

		if(state.equals(values.dockstatus, AutoDock.DOCKED))
			dock = "<a href=\"dashboard?action=redock\" title=\"force re-dock the robot\">docked</a>";

		if(state.equals(values.dockstatus, AutoDock.UNDOCKED)) dock = "<a href=\"dashboard?action=redock\">un-docked</a>";
		if(state.equals(values.dockstatus, AutoDock.DOCKING))  dock = "<a href=\"dashboard?action=dockcancel\">docking</a>";
		if(state.equals(values.dockstatus, AutoDock.UNKNOWN))  dock = "<a href=\"dashboard?action=redock\">UNKNOWN</a>";
		if(state.getBoolean(values.autodocking)) dock = "auto-docking";

		String drv = state.get(values.driver);
		if(drv == null) drv = "none";

		//------------------- now build HTML buffer ------------------------------------------------------------//  
		StringBuffer str = new StringBuffer("<table cellspacing=\"3\"><tbody>\n");

		// version | ssid | driver
		str.append("\n<tr><td class='menu'>&nbsp;&nbsp;bulid&nbsp;" + VERSION + "&nbsp;");
		str.append("<td><div class=\"dropdown\"><button class=\"dropbtn\">&nbsp;&nbsp;commands</button><div class=\"dropdown-content\">\n" + commandlinks + "\n</div></div>");
		str.append("\n<td class='menu'>&nbsp;&nbsp;ssid<td>&nbsp;&nbsp;<a href=\"/oculusPrime/network\">" + state.get(values.ssid));
		str.append("\n<td class='menu'>&nbsp;&nbsp;logs <td><div class=\"dropdown\"><button class=\"dropbtn\">&nbsp;&nbsp;DEL</button><div class=\"dropdown-content\">\n" + loglinks + "\n</div></div>");

		// motor | wan | hdd 		
		str.append("\n<tr><td class='menu'>&nbsp;&nbsp;motor<td>&nbsp;&nbsp;" + motor );
		String ext = state.get(values.externaladdress);
		if( ext == null ) str.append("<td class='menu'>&nbsp;&nbsp;wan<td>disconnected");
		else str.append("<td class='menu'>&nbsp;&nbsp;wan<td>&nbsp;&nbsp;<a href=\"http://"+ ext + ":" + httpport + "/oculusPrime/\">" + ext + "</a>");
		str.append( "<td class='menu'>&nbsp;&nbsp;hdd<td>&nbsp;&nbsp;" + hdd + "% used</a></tr> \n");

		// power | lan | prime 
		str.append("\n<tr><td class='menu'>&nbsp;&nbsp;power<td>&nbsp;&nbsp;" + power );
		str.append("<td class='menu'>&nbsp;&nbsp;lan<td>&nbsp;&nbsp;<a href=\"http://"+state.get(values.localaddress) + ":" + httpport + "/oculusPrime/\">" + state.get(values.localaddress) + "</a>");
		str.append("<td class='menu'>&nbsp;&nbsp;prime<td>&nbsp;&nbsp;" + allBytes + " mb</tr> \n");

		// dock | battery | streams
		str.append("\n<tr>");
		if( ! state.equals(values.dockstatus, AutoDock.DOCKED)) str.append("<td class='menu'>&nbsp;&nbsp;dock<td class='busy'>&nbsp;&nbsp;" + dock);
		else str.append("<td class='menu'>&nbsp;&nbsp;dock<td>&nbsp;&nbsp;" + dock);
		str.append("<td class='menu'>&nbsp;&nbsp;battery<td>&nbsp;&nbsp;<a href=\"dashboard?view=power\">" + state.get(values.batterylife) + "</a>");
		str.append("<td class='menu'>&nbsp;&nbsp;streams<td>&nbsp;&nbsp;" + streams + "</a> mb</tr> \n" );

		// record | booted | frames 
		str.append("\n<tr>" + rec);
		str.append("<td class='menu'>&nbsp;&nbsp;booted<td>&nbsp;&nbsp;" + (((System.currentTimeMillis() - state.getLong(values.linuxboot)) / 1000) / 60)+ "</a> mins");
		str.append("<td class='menu'>&nbsp;&nbsp;frames<td>&nbsp;&nbsp;" + frames + " mb</tr> \n" );

		// camera | uptime | archive
		str.append("\n<tr>" + cam);
//		if(settings.getInteger(ManualSettings.restarted) < 5) 
		str.append("<td class='menu'>&nbsp;&nbsp;up time<td>&nbsp;&nbsp;" + (state.getUpTime()/1000)/60 + "</a> mins");
//		else str.append("<td class='menu'>&nbsp;&nbsp;up time<td class='busy'>&nbsp;&nbsp;" + (state.getUpTime()/1000)/60 + "</a> mins " + settings.getInteger(ManualSettings.restarted) + "");
		str.append("<td class='menu'>&nbsp;&nbsp;archive<td>&nbsp;&nbsp;"+ archive + " mb</tr> \n" );

		// navigation | cpu | logs
		str.append("\n<tr>"+od);
		String cpuvalue;
		if(state.getBoolean(values.waitingforcpu)) cpuvalue = "<td class='busy'>&nbsp;&nbsp;" + state.get(values.cpu) + "%&nbsp;&nbsp;waiting..</td>";
		else cpuvalue = "<td>&nbsp;&nbsp;" + state.get(values.cpu) + "%";
		str.append("<td class='menu'>&nbsp;&nbsp;cpu" + cpuvalue);
		str.append("<td class='menu'>&nbsp;&nbsp;logs<td>&nbsp;&nbsp;" + logs + " mb</tr> \n" );

		// debug | linux user (telnet) | driver
		str.append("\n<tr>" + debug + "<td class='menu'>&nbsp;&nbsp;user<td>&nbsp;&nbsp;" + Util.getLinuxUser() + " " +state.get(values.telnetusers));
		str.append("\n<td class='menu'>&nbsp;&nbsp;driver<td>&nbsp;&nbsp;" + drv);

		// next | meters | time
		str.append(getActiveRoute());

		// views 
		str.append("<tr><td><div class=\"dropdown\"><button class=\"dropbtn\">&nbsp;&nbsp;views</button><div class=\"dropdown-content\">\n" + viewslinks + "\n</div></div>");

		// route list 
		str.append("\n<td>"+ getRouteLinks() +" \n");

		// waypoints | routes | pids | scripts | logs 
		String waypoint = state.get(values.roswaypoint);
		if(waypoint == null) waypoint = "waypoint";
		final Vector<String> waypointsAll = getAllWaypoints();
		String drop = "\n<td><div class=\"dropdown\"><button class=\"dropbtn\">&nbsp;&nbsp;points "+ waypointsAll.size() +"</button><div class=\"dropdown-content\">";
		if(waypointsAll != null) for(int i = 0 ; i < waypointsAll.size() ; i++)
			drop += "\n<a href=\"dashboard?action=gotowp&route="+ waypointsAll.get(i).replaceAll("&nbsp;", " ").trim() +"\">" + waypointsAll.get(i) + "</a> ";
		drop += "</div></div>\n";
		str.append(drop);

		// pids 
		pids = PyScripts.getRunningPythonScripts();
		str.append("<td><div class=\"dropdown\"><button class=\"dropbtn\">&nbsp;&nbsp;kill "+pids.size()+"</button><div class=\"dropdown-content\"> \n");
		for(int i = 0 ; i < pids.size() ; i ++)
			str.append("<a href=\"dashboard?action=kill&pid="+pids.get(i).pid+"\">"+pids.get(i).name+"</a>\n");
		str.append("<a href=\"dashboard?action=kill&pid=pkill\">pkill python</a>\n");
		str.append("</div></div>\n");

		// python script files
		File[] names = PyScripts.getScriptFiles();
		if( names == null ) str.append("<td> none");
		else {
			str.append("<td><div class=\"dropdown\"><button class=\"dropbtn\">&nbsp;&nbsp;scripts " + names.length + "</button><div class=\"dropdown-content\">");
			for(int i = 0 ; i < names.length ; i++)
				str.append("\n<a href=\"dashboard?action=script&pid="+ names[i].getName() +"\">"+ names[i].getName() +"</a>");
			str.append("</div></div>\n");
		}

		// --- tail on pid --- //
		str.append("<td><div class=\"dropdown\"><button class=\"dropbtn\">&nbsp;&nbsp;logs</button><div class=\"dropdown-content\"> \n");
		str.append("<a href=\"dashboard?action=batterylog\">battery log</a>\n");
		// str.append("<a href=\"dashboard?action=batterylog\">battery log</a>\n");	
		for(int i = 0 ; i < pids.size() ; i ++) {
			if( ! pids.get(i).logFile.equals(PyScripts.NONE)) {
				String txt = pids.get(i).logFile;
				txt = Util.trimLength(txt, 15);
				str.append("<a href=\"dashboard?action=tail&pid="+pids.get(i).pid+"\">"+ txt +"</a>\n");
			}
		}
//		str.append("<a href=\"dashboard?action=tail&pid=stdout\">stdout</a>\n");
//		str.append("<a href=\"dashboard?action=tail&pid=ros\">ros log</a>\n");
//		str.append("<a href=\"dashboard?action=tail&pid=stdout\">stdout</a>\n");
		str.append("</div></div>\n");

		// --- active --- //
		if(state.exists(values.navigationrouteid)) { //  && state.equals(values.odometry, "true")){
			String m = "<a href=\"dashboard?action=cancel\">" + state.get(values.navigationroute) + "</a>&nbsp;&nbsp;-&nbsp;&nbsp;";
			if(pointslist != null) {
				for(int c = 0 ; c < pointslist.size(); c++ )
					m+= "<a href=\"media?filter="+ pointslist.get(c).replaceAll(" ", "_") + "\" target=\"_blank\">" + pointslist.get(c) + "</a>,  ";
			}
			// if(m.endsWith(",")) m.substring(0, m.length()-1);
			str.append("\n<tr><td>&nbsp;&nbsp;<a href=\"dashboard?action=cancel\">lap# " + Navigation.consecutiveroute +"&nbsp;&nbsp;</a><td class='tail' colspan=\"11\">&nbsp;&nbsp;"+ m.trim()
					+ "&nbsp;&nbsp;<a href=\"dashboard?action=gotodock\">dock</a></tr>\n");
		}

		// --- gui notify --- //
		String msg = state.get(values.guinotify);
		if(msg == null) msg = "";
		else msg += "&nbsp;&nbsp;(<a href=\"dashboard?action=gui\">dismiss</a>)";
		if(msg.length() > 1) {
			msg = "<tr><td class='menu'>&nbsp;&nbsp;message<td class='tail' colspan=\"11\">&nbsp;&nbsp;" + msg + "</tr> \n";
			str.append(msg);
		}

		// --- state flags --- //
		String flags = "";
//		if(state.getBoolean(values.routeoverdue))  flags += " * overdue* "; 		
		if(state.getBoolean(values.waypointbusy))   flags += "&nbsp;&nbsp;<a href=\"dashboard?action=state&member=waypointbusy\">waypointbusy</a>";
		if(state.getBoolean(values.rosgoalcancel))  flags += "&nbsp;&nbsp;<a href=\"dashboard?action=state&member=rosgoalcancel\">goal cancel</a>";
		if(state.getBoolean(values.framegrabbusy))  flags += "&nbsp;&nbsp;<a href=\"dashboard?action=state&member=framegrabbusy\">framegrab busy</a>";
		if(state.exists(values.writingframegrabs))  flags += "&nbsp;&nbsp;<a href=\"dashboard?action=state&member=writingframegrabs\">writing framegrabs</a>";
		if(flags.length() > 0) str.append("\n<tr><td class='menu'>&nbsp;&nbsp;flags<td class='tail' colspan=\"11\">&nbsp;&nbsp;" + flags + "</tr>\n");

		str.append(tailFormated(17) + "\n");
		str.append("\n</tbody></table>\n");
		return str.toString();
	}

	private String getRouteLinks(){
		Vector<String> list = NavigationUtilities.getRoutes();
		String rname = state.get(values.navigationroute);
		if(rname == null) rname = "routes";
		String drop = "\n<div class=\"dropdown\"><button class=\"dropbtn\">&nbsp;&nbsp;routes " + list.size() + "</button><div class=\"dropdown-content\">";
		for(int i = 0; i < list.size(); i++) drop += "<a href=\"dashboard?action=runroute&route="+list.get(i)+"\">" + list.get(i) + "</a>";
		return drop += "</div></div>";
	}

	private String getActiveRoute(){
		// next | meters | time 
		String link = "<tr><td class='menu'>&nbsp;&nbsp;next<td>&nbsp;&nbsp;";
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
			link += next += "<td class='menu'>&nbsp;&nbsp;meters<td>&nbsp;&nbsp;" + Navigation.getRouteMeters() + " | " + estimatedmeters
					+ "<td class='menu'>&nbsp;&nbsp;time<td>&nbsp;&nbsp;" + time + " | " +  estimatedseconds;
		else link += next += "<td class='menu'>&nbsp;&nbsp;meters<td>&nbsp;&nbsp;none <td class='menu'>&nbsp;&nbsp;time<td>&nbsp;&nbsp;none";
		return link;
	}

	public static String tailFormated(int lines){
		StringBuffer str = new StringBuffer();
		Vector<String> history = Util.tail(Settings.stdout, lines);
		for(int i = 0; i < history.size() ; i++) {
			String line = history.get(i).trim();
			line = line.replaceFirst("DEBUG:", "");
			line = line.replaceFirst("OCULUS:", "");
			line = line.replaceFirst("[INFO]", "");
			line = line.replaceFirst("oculusprime.", "");
			line = line.replaceFirst("oculusPrime", "");
			line = line.replaceFirst("Application.", "");
			line = line.replaceFirst("static, ", "");
			line = line.replaceFirst("commport.", "");
			line = line.replaceFirst("Downloader", "");
			line = line.replaceFirst("log", "");
			line = line.replace("TelnetServer$ConnectionHandler,", "telnet: ");
			line = line.replaceAll(">", "");
			line = line.replaceAll("<", "");
			line = line.replaceAll(",", "");
			line = line.trim();
			line = Util.trimLength(line, MAX_LINE_LENGTH);
			str.append("\n<tr><td class='tail' colspan=\"11\">&nbsp;&nbsp;" + line + "</tr>\n");
		}
		return str.toString();
	}
	/*
	public static String tailFormated(int lines){
		StringBuffer str = new StringBuffer();
		Vector<String> history = Util.tail(Settings.stdout, lines);
		for(int i = 0; i < history.size() ; i++) {
			String line = history.get(i).trim();
			line = line.replaceFirst("DEBUG:", "");
			line = line.replaceFirst("OCULUS:", "");
			line = line.replaceFirst("[INFO]", "");
			line = line.replaceFirst("oculusprime.", "");
			line = line.replaceFirst("oculusPrime.", "");
			line = line.replaceFirst("Application.", "");
			line = line.replaceFirst("static, ", "");	
			line = line.replaceFirst("commport.", "");
			line = line.replaceFirst("Downloader", "");
			line = line.replace("TelnetServer$ConnectionHandler,", "telnet: ");
			line = line.replaceAll(">", "");	
			line = line.replaceAll("<", "");	
			line = line.replaceAll(",", "");	
			// line = line.trim();
			line = Util.trimLength(line, MAX_LINE_LENGTH);
			str.append("\n<tr><td class='tail' colspan=\"11\">" + line + "</tr>\n"); 
		}
		return str.toString();
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
			reply += " " + Util.formatFloat(delta, 1) + " " + unit + " " + mesg.trim() + "\n";
		}
		return reply;
	}

	@Override
	public void updated(String key){

		if(key.equals(values.networksinrange.name())) return;
		if(key.equals(values.rosmapwaypoints.name())) return;
		if(key.equals(values.framegrabbusy.name())) return;
		if(key.equals(values.rosglobalpath.name())) return;
		if(key.equals(values.batteryinfo.name())) return;
		if(key.equals(values.batterylife.name())) return;
		if(key.equals(values.batteryvolts.name())) return;
		if(key.equals(values.rosscan.name())) return;
		if(key.equals(values.cpu.name())) return;

		if(DEBUG && state.exists(key)) Util.debug("updated: " + key + " = " + state.get(key), this);

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

		// trim size
		if(history.size() > MAX_STATE_HISTORY) history.remove(0);
		if(state.exists(key)) {
//			if( ! state.get(key).equals(state.get(key))) // count changes only 
			history.add(System.currentTimeMillis() + " " + key + " " + state.get(key));
		}
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
