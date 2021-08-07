package oculusPrime;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import oculusPrime.servlet.CommServlet;
import org.jasypt.util.password.ConfigurablePasswordEncryptor;

import developer.Calibrate;
import developer.Navigation;
import developer.NavigationLog;
import developer.Ros;
import developer.image.OpenCVMotionDetect;
import developer.image.OpenCVObjectDetect;
import developer.image.OpenCVUtils;
import oculusPrime.State.values;
import oculusPrime.commport.ArduinoPower;
import oculusPrime.commport.Malg;
import oculusPrime.commport.PowerLogger;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;


/** red5 application */
public class Application implements ServletContextListener {

	public enum streamstate { stop, camera, camandmic, mic }
	public enum camquality { low, med, high, custom }
	public enum driverstreamstate { stop, mic, pending, disabled }
    private static final String SERVEROK = "serverok";
    public static final String ARM = "arm";
    private static final String RESTARTFILE = "oculusrestart"; // must match oculusprime.sh
    private static final String HALTFILE = "oculushalt";       // must match oculusprime.sh

    private ConfigurablePasswordEncryptor passwordEncryptor = new ConfigurablePasswordEncryptor();
	protected boolean initialstatuscalled = false;
	private String authtoken = null;
	private String salt = null;
	
	private Settings settings = Settings.getReference();
	private BanList banlist = BanList.getRefrence();
	private State state = State.getReference();
	protected LoginRecords loginRecords = null;
	protected SystemWatchdog watchdog = null;
	private AutoDock docker = null;
	public Video video = null;

	public Malg comport = null;
	public ArduinoPower powerport = null;
	public TelnetServer commandServer = null;
	private developer.Navigation navigation = null;

//	public static byte[] framegrabimg  = null;
	public static BufferedImage processedImage = null;
	public static BufferedImage videoOverlayImage = null;

	public Network network = null;

    public volatile boolean running = true;


    public Application() {

		PowerLogger.append("==============Oculus Prime Java Start===============\n", this); // extra newline on end
		Util.log ("==============Oculus Prime Java Start===============\n", this); // extra newline on end
		Util.log("Linux Version:"+Util.getUbuntuVersion()
				+", Java Model:"+System.getProperty("sun.arch.data.model")
				+", Java Arch:"+state.get(values.osarch), this);

		passwordEncryptor.setAlgorithm("SHA-1");
		passwordEncryptor.setPlainDigest(true);
		loginRecords = LoginRecords.getReference();
        CommServlet.setApp(this);
        DashboardServlet.setApp(this);
		FrameGrabHTTP.setApp(this);
		initialize();

	}


    @Override
    public void contextDestroyed(ServletContextEvent arg0) {
        shutdownApplication();
    }

    /** */
	public void initialize() {
		settings.writeFile();
		salt = settings.readSetting("salt");
		Util.getLinuxUptime();

		if (settings.readSetting("user0") == null)
			driverCallServer(PlayerCommands.new_user_add, "oculus robot");

		// video now requires ROS+telnet, telnet can != disabled
		if (settings.readSetting(GUISettings.telnetport).equals(Settings.DISABLED))
		    settings.writeSettings(GUISettings.telnetport, TelnetServer.DEFAULTPORT);

		comport = new Malg(this);   // note: blocking
		powerport = new ArduinoPower(this); // note: blocking

		state.set(State.values.httpport, settings.readHTTPport());
		initialstatuscalled = false;

		if (!settings.readSetting(GUISettings.telnetport).equals(Settings.DISABLED.toString()))
			commandServer = new TelnetServer(this);

		// OpenCV, requires restart if ubuntu 14.04 running, with jar file targeted at 16.04 was present
		OpenCVUtils ocv = new OpenCVUtils(this);
		ocv.loadOpenCVnativeLib();

		Util.setSystemVolume(settings.getInteger(GUISettings.volume));
		state.set(State.values.volume, settings.getInteger(GUISettings.volume));

		state.set(State.values.driverstream, driverstreamstate.disabled.toString());

        video = new Video(this);

        state.set(State.values.lastusercommand, System.currentTimeMillis()); // must be before watchdog
		docker = new AutoDock(this, comport, powerport);
		network = new Network(this);	
		watchdog = new SystemWatchdog(this);

		if(settings.getBoolean(GUISettings.navigation)) {
			navigation = new developer.Navigation(this);
			navigation.runAnyActiveRoute();
		}

		Util.debug("application initialize done", this);
	}

    // nonflash xmlhttp clients only TODO: multiple clients + relay omitted, set xmlhttpclient flag here
    public void driverSignIn(String username, long clientID) {
        Util.log("driver sign in: "+username, this);
        state.set(State.values.driver, username);

        String str = "connection connected user " + username;
        str += " streamsettings " + streamSettings();

        String vals[] = settings.readSetting(settings.readSetting(GUISettings.vset)).split("_");
        str += " videowidth "+vals[0]+ " videoheight "+vals[1];

        str += " webrtcserver " + settings.readSetting(ManualSettings.webrtcserver);
        str += " webrtcport " + settings.readSetting(ManualSettings.webrtcport);
        str += " turnserverlogin " + settings.readSetting(ManualSettings.turnserverlogin);
        str += " turnserverport " + settings.readSetting(ManualSettings.turnserverport);

        if (authtoken != null) {
            str += " storecookie " + authtoken;
            authtoken = null;
        }
        messageplayer(username + " connected to Oculus Prime", "multiple", str);
        initialstatuscalled = false;

        loginRecords.beDriver();

        if (settings.getBoolean(GUISettings.loginnotify)) {
            saySpeech("lawg inn " + state.get(State.values.driver));
        }

        watchdog.lastpowererrornotify = null; // new driver not notified of any errors yet
        state.set(values.driverclientid, clientID);
        state.set(State.values.lastusercommand, System.currentTimeMillis());
    }

    // xmlhttp clients only
    public void driverSignOut() {

        if (!state.exists(values.driver)) return;


        String str = state.get(State.values.driver) + " disconnected";

        Util.log("driverSignOut(): " + str,this);

        sendplayerfunction("commclientclose", null); // calls javascript: commclientclose()
        Util.delay(100);

        loginRecords.signoutDriver();

        if (CommServlet.clientaddress != null) banlist.removeAddress(CommServlet.clientaddress);

        //if autodocking, keep autodocking, otherwise do this
        if (!state.getBoolean(State.values.autodocking) &&
                !(state.exists(values.navigationroute) && !state.exists(values.nextroutetime)) ) {

            if (!state.get(State.values.driverstream).equals(driverstreamstate.pending.toString())) {

                if (state.get(State.values.stream) != null) {
                    if (!state.get(State.values.stream).equals(streamstate.stop.toString())) {
                        publish(streamstate.stop);
                    }
                }

                if (comport.isConnected()) {
                    comport.setSpotLightBrightness(0);
                    comport.floodLight(0);
                    comport.stopGoing();
                }

            }
        }

        state.delete(values.driverclientid);

    }


	public void driverCallServer(PlayerCommands fn, String str) {
		playerCallServer(fn, str, true);
	}

	/**
	 * called by remote flash
	 * */
	public void playerCallServer(String fn, String str) {
		
		if (fn == null) return;
		if (fn.equals("")) return;

		PlayerCommands cmd = null;
		try {
			cmd = PlayerCommands.valueOf(fn);
		} catch (Exception e) {
			Util.debug("playerCallServer() command not found:" + fn, this);
			messageplayer("error: unknown command, "+fn,null,null);
			return;
		}
		if (cmd != null) playerCallServer(cmd, str, false);	
	}
	
	@SuppressWarnings("incomplete-switch")
	private void playerCallServer(PlayerCommands fn, String str, boolean passengerOverride) {

		if (PlayerCommands.requiresAdmin(fn) && !passengerOverride) {
			if ( ! loginRecords.isAdmin()){ 
				Util.debug("playerCallServer(), must be an admin to do: " + fn.name() + " curent driver: " + state.get(State.values.driver), this);
				return;
			}
		}
			
		// track last user command
		if(fn != PlayerCommands.statuscheck && !passengerOverride)
			state.set(State.values.lastusercommand, System.currentTimeMillis());


		String[] cmd = null;
		if(str!=null) cmd = str.split(" ");

		switch (fn) {
			case chat: chat(str) ;return;
		}


		switch (fn) {
	
		case move: {

			if (settings.getBoolean(GUISettings.navigation)) navigation.navdockactive = false;

			if (state.exists(State.values.navigationroute) && !passengerOverride && 
					str.equals(Malg.direction.stop.toString())) {
				messageplayer("navigation route "+state.get(State.values.navigationroute)+" cancelled by stop", null, null);
				navigation.navlog.newItem(NavigationLog.INFOSTATUS, "Route cancelled by user",
						Navigation.routestarttime, null, state.get(values.navigationroute),
						Navigation.consecutiveroute, 0);
				navigation.cancelAllRoutes();
			}
			else if (state.exists(State.values.roscurrentgoal) && !passengerOverride && str.equals(Malg.direction.stop.toString())) {
				Navigation.goalCancel();
				messageplayer("navigation goal cancelled by stop", null, null);
			}

			if (!passengerOverride && watchdog.redocking) watchdog.redocking = false;
				
			move(str); 
			break;
		}

		case battstats: messageplayer(state.get(State.values.batteryinfo), "battery", state.get(State.values.batterylife)); break; 
		case cameracommand: 
			if (state.getBoolean(State.values.autodocking)) {
				messageplayer("command dropped, autodocking", null, null);
				return;
			}
			comport.camCommand(Malg.cameramove.valueOf(str));
			break;
//		case camtiltfast: comport.cameraToPosition(Integer.parseInt(str)); break;
		case camtilt: comport.camtilt(Integer.parseInt(str)); break;
		case getdrivingsettings:getDrivingSettings();break;
		case drivingsettingsupdate:drivingSettingsUpdate(str);break;
		case getemailsettings: getEmailSettings(); break;
		case emailsettingsupdate: emailSettingsUpdate(str); break;
		case motionenabletoggle:motionEnableToggle();break;
		case clicksteer:clickSteer(str);break;
		case streamsettingscustom: streamSettingsCustom(str);break;
		case streamsettingsset:streamSettingsSet(str);break;
		case driverexit: driverSignOut(); break;
		case logout:
			Util.log("browser user logout", this);
            driverSignOut();
			break;
		case password_update: account("password_update", str); break;
		case new_user_add: account("new_user_add", str); break;
		case user_list: account("user_list", ""); break;
		case delete_user: account("delete_user", str); break;
		case statuscheck: statusCheck(str); break;
		case extrauser_password_update: account("extrauser_password_update", str); break;
		case username_update: account("username_update", str); break;
		case showlog: showlog(str); break;
		case publish: publish(streamstate.valueOf(str)); break;
		case record: // record [true | false] optionalfilename
			if(str == null) break;
			if(str.startsWith("true ")) video.record("true", str.substring(4).trim().replace(" ", "_")); 
			if(str.equals("true") || str.equals("false")) video.record(str); 
			break;  
		case autodockcalibrate: docker.autoDock("calibrate " + str); break;
		case redock: watchdog.redock(str); break;

		case restart: restart(); break;
		case powershutdown: powerport.shutdown(str); break;
		case reboot: reboot(); break;
		case systemshutdown: powerdown(); break;

		case softwareupdate: softwareUpdate(str); break;
//		case muterovmiconmovetoggle: muteROVMicOnMoveToggle(); break;
		case quitserver: shutdownApplication(); break;
		case email: new SendMail(str, this); break;
		case uptime:
			// messageplayer("uptime: " + state.getUpTime() + " ms", null, null); 
			commandServer.sendToGroup(TelnetServer.TELNETTAG + " uptime " + state.getUpTime()); 
			break;
			// SEND TO TELNET NOT GUI 
		case memory: messageplayer(Util.memory(), null, null); break;
		case who: messageplayer(loginRecords.who(), null, null); break;
		case messageclients: messageplayer(str, null,null); Util.log("messageclients: "+str,this); break;
		case dockgrab: 
			if (str!=null) if (str.equals(AutoDock.HIGHRES)) docker.lowres = false;
			docker.dockGrab(AutoDock.dockgrabmodes.start, 0, 0);
			docker.lowres = true;
			break;
		case dockgrabtest:
			if (str.equals(AutoDock.HIGHRES)) docker.lowres = false; // ?
			docker.dockGrab(AutoDock.dockgrabmodes.test, 0, 0);
			docker.lowres = true; // ?
			break;
		case rssadd: new RssFeed().newItem(str); break;
		case nudge: nudge(str); break;
		
		case state: 
			String s[] = str.split(" ");
			if (s.length == 2) { // two args
				if (s[0].equals("delete")) state.delete(s[1]); 
				else state.set(s[0], s[1]); 
			}
			else if (s.length > 2) { // 2nd arg has spaces
				String stateval = "";
				for (int i=1; i<s.length; i++) stateval += s[i]+" ";
				state.set(s[0], stateval.trim());
			}
			else {  
				if (s[0].matches("\\S+")) { // one arg 
					messageplayer("<state> "+s[0]+" "+state.get(s[0]), null, null); 
				} else {  // no args
					messageplayer("<state> "+state.toString(), null, null);
				} 
			}
			break;
	
		case writesetting:
			if (settings.readSetting(cmd[0]) == null) {
				settings.newSetting(cmd[0], cmd[1]);
				messageplayer("new setting: " + cmd[1], null, null);
			} else {
				settings.writeSettings(cmd[0], cmd[1]);
				messageplayer(cmd[0] + " " + cmd[1], null, null);
			}
			settings.writeFile();
			break;
			
		case readsetting:
			messageplayer("setting "+cmd[0]+" "+settings.readSetting(cmd[0]),null,null);
			break;

		case speed:
			comport.speedset(str);
			messageplayer("speed set: " + str, "speed", str.toUpperCase());
			break;

		case left:
		case right:
			if (!state.getBoolean(State.values.motionenabled.name())) {
				messageplayer("motion disabled", "motion", "disabled");
				break;
			}
			if (state.getBoolean(State.values.autodocking.name())) {
				messageplayer("command dropped, autodocking", null, null);
				break;
			}
			moveMacroCancel();
			comport.rotate(Malg.direction.valueOf(fn.toString()), Double.parseDouble(str));
			messageplayer(Malg.direction.valueOf(fn.toString())+" " + str+"&deg;", "motion", "moving");
			break;

		case rotate:
			if (!state.getBoolean(State.values.motionenabled.name())) {
				messageplayer("motion disabled", "motion", "disabled");
				break;
			}
			if (state.getBoolean(State.values.autodocking.name())) {
				messageplayer("command dropped, autodocking", null, null);
				break;
			}
			moveMacroCancel();
			comport.rotate(Double.parseDouble(str));
			messageplayer(null, "motion", "moving");
			break;

		case forward:
		case backward:
			if (!state.getBoolean(State.values.motionenabled.name())) {
				messageplayer("motion disabled", "motion", "disabled");
				break;
			}
			if (state.getBoolean(State.values.autodocking.name())) {
				messageplayer("command dropped, autodocking", null, null);
				break;
			}
			moveMacroCancel();
			comport.movedistance(Malg.direction.valueOf(fn.toString()),Double.parseDouble(str));
			messageplayer(Malg.direction.valueOf(fn.toString())+" " + str+"m", "motion", "moving");
			break;

		case arcmove:
			if (!state.getBoolean(values.motionenabled) || state.getBoolean(values.autodocking)) return;
			moveMacroCancel();
			String[] metersdegrees = str.split(" ");
			comport.arcmove(Double.parseDouble(metersdegrees[0]), Integer.parseInt(metersdegrees[1]));
			break;
			
		case odometrystart:	 	comport.odometryStart(); break;
		case odometryreport: 	comport.odometryReport(); break;
		case odometrystop: 		comport.odometryStop(); break;
			
		case lefttimed: comport.turnLeft(Integer.parseInt(str)); break;
		case righttimed: comport.turnRight(Integer.parseInt(str)); break;
		case forwardtimed: comport.goForward(Integer.parseInt(str)); break;
		
		case systemcall:
			if(str != null){
				if(str.length() > 1){
					Util.log("received: " + str,this);
					messageplayer("system command received", null, null);
					Util.systemCall(str);
				}
			}
			break;

		case docklineposupdate:
			settings.writeSettings(GUISettings.vidctroffset, str);
			messageplayer("vidctroffset set to : " + str, null, null);
			break;

		case motorsreset:
			comport.reset();
			messageplayer("resetting malg board", null, null);
			break;

		case speech:
			messageplayer("synth voice: " + str, null, null);
			saySpeech(str);
			break;
		
		case setsystemvolume:
			Util.setSystemVolume(Integer.parseInt(str));
			messageplayer("ROV volume set to "+str+"%", null, null); 
			state.set(State.values.volume, str);
			break;		

		case spotlightsetbrightness: // deprecated, maintained for mobile client compatibility
		case spotlight: 
			comport.setSpotLightBrightness(Integer.parseInt(str));
			messageplayer("spotlight brightness set to "+str+"%", "light", str);		
			break;
		case floodlight: 
			comport.floodLight(Integer.parseInt(str));  
			messageplayer("floodLight brightness set to "+str+"%", "floodlight", str);
			break;
		case fwdflood:
			comport.fwdflood(Integer.parseInt(str));  
			messageplayer("forward floodLight brightness set to "+str+"%", "fwdflood", str);
			break;
		case autodock:
			docker.autoDock(str); 
			break;
		case getlightlevel:
			docker.getLightLevel(); break;
		case dock:
			docker.dock();
			break;
		case strobeflash:
			String mode = "on";
			int duration = 0;
			int intensity = 0;
			if (str != null) {
				String[] t = str.split(" ");
				mode = t[0];
				if (t.length >= 3) {
					duration = Integer.parseInt(t[1]);
					intensity = Integer.parseInt(t[2]);
				}
			}
			comport.strobeflash(mode, duration, intensity);
			messageplayer("strobeflash "+str, null, null);
			break;

		case powerreset:
			messageplayer("resetting power board", null, null);
			powerport.reset();
			break;
			
		case powercommand:
			messageplayer("powercommand: "+str, null, null);
			powerport.powercommand(str);
			break;

		case malgcommand:
				if (!passengerOverride) messageplayer("malgcommand: "+str, null, null);
				comport.malgcommand(str);
				break;

		case erroracknowledged:
			if (str.equals("true")) {
				Util.log("power error acknowledged",this);
				Util.log("power error acknowledged","Application_power");
				
				if (watchdog.powererrorwarningonly) { 
					powerport.clearWarningErrors(); 
					watchdog.lastpowererrornotify = null; 
				}
			}
			else { 
				Util.log("power error purposefully dismissed",this);
			}
			break;
		
		case roslaunch:
			Ros.launch(str);
			messageplayer("roslaunch "+str+".launch", null, null);

			break;
		
		case savewaypoints:
			Ros.savewaypoints(str);
			messageplayer("waypoints saved", null, null);
			break;
			
		case saveroute: 
			if (navigation != null) {
				navigation.saveRoute(str);
				messageplayer("route saved", null, null);
			}
			break;
			
		case runroute:
			if (navigation != null) {
				navigation.navlog.newItem(NavigationLog.INFOSTATUS, "Route activated by user",
						System.currentTimeMillis(), null, str, Navigation.consecutiveroute, 0);
				navigation.runRoute(str);
			}
			break;

		case cancelroute:
			if (navigation != null && state.exists(values.navigationroute)) {
				navigation.navlog.newItem(NavigationLog.INFOSTATUS, "Route cancelled",
						Navigation.routestarttime, null, state.get(values.navigationroute),
						Navigation.consecutiveroute, 0);
				navigation.cancelAllRoutes();
			}
			break;
		
		case gotowaypoint: if (navigation != null) navigation.gotoWaypoint(str); break;
		case startnav:     if (navigation != null) navigation.startNavigation(); break;
		case stopnav:      if (navigation != null) navigation.stopNavigation(); break;
		case startmapping: if (navigation != null) navigation.startMapping(); break;
		case savemap:      if (navigation != null) navigation.saveMap(); break;
		case gotodock:     if (navigation != null) navigation.dock(); break;
		
		case motiondetectstream: new OpenCVMotionDetect(this).motionDetectStream(); break;
		case objectdetectstream: new OpenCVObjectDetect(this).detectStream(str); break;
		case motiondetect: new OpenCVMotionDetect(this).motionDetectGo(); break;
		case motiondetectcancel: state.delete(State.values.motiondetect); break;
		case objectdetect: new OpenCVObjectDetect(this).detectGo(str); break;
		case settings: messageplayer(settings.toString(), null, null); break;
		case objectdetectcancel: state.delete(values.objectdetect); break;
		case waitforcpu: watchdog.waitForCpuThread(); break;	
		case unblock: banlist.removeblockedFile(str); break;
		case block:	banlist.addBlockedFile(str); break;
		case log: Util.log("log: "+str, this); break;
		case sounddetect: video.sounddetectgst(str); break;

		case cpu:
			String cpu = String.valueOf(Util.getCPU());
			if(cpu != null) state.set(values.cpu, cpu);
			break;
		
		// 
		// (was before) 	
		// case framegrabtofile: messageplayer(FrameGrabHTTP.saveToFile(str), null, null); break;
		// 
		case framegrabtofile: // allow extra name to be added 
			if(cmd.length == 2) { 
				FrameGrabHTTP.saveToFile(cmd[0], cmd[1]); // ?mode=processedImgJPG file_name
				Util.debug("framegrabtofile(mode, fname): " + cmd[0] + " " + cmd[1], this);
			}
			if(cmd.length == 1) { 
				FrameGrabHTTP.saveToFile(cmd[0]); // framegrabtofile?mode=processedImgJPG 
				Util.debug("framegrabtofile(mode): "+cmd[0], this);
			}
			if(cmd.length == 0){
				FrameGrabHTTP.saveToFile(null); // default filename
				Util.debug("framegrabtofile(default):", this);
			}			
			break;
		
		// dev tool only
		case test:
			try {
				Util.log("testing", this.getClass().getEnclosingMethod().toString());
			} catch (Exception e)  { Util.printError(e); }
			break;

		case jpgstream:
			if (str== null) str="";
			if (str.equals(streamstate.stop.toString())) {
				state.delete(values.jpgstream);
				break;
			}
			if (str.equals("")) str = AutoDock.HIGHRES;
			new OpenCVUtils(this).jpgStream(str);
//			opencvutils.jpgStream(str);
			break;
			
		case deletelogs: // super dangerous, purge all log folders and ros logs, causes restart 
			if( !state.equals(values.dockstatus, AutoDock.DOCKED)) {
				Util.log("must be docked, skipping.. ", null);
				break;
			}
			state.set(values.guinotify, "logs being deleted, rebooting");
			Util.deleteLogFiles();
			break;
			
		case archivelogs: // create zip of log folder 
			Util.archiveLogFiles();
			break;

		case archivenavigation: // create zip file with settings, tailf of main logs, nav log, routes.xml 
			Util.archiveNavigation();
			break;
		
		case truncmedia: // remove any frames or videos not currently linked in navigation log  
			Util.truncStaleFrames();
			Util.truncStaleAudioVideo();
			break;
			
		case streammode: // TODO: testing ffmpeg/avconv streaming
            setStreamMode(str);
			break;

		case calibraterotation:
			new Calibrate(this).calibrateRotation(str);
			break;


		case networksettings:
			network.getNetworkSettings();
			break;

		case networkconnect:
			network.connectNetwork(str);
			break;

        case launchsignalling:
            video.killSignallingServer();
            video.launchSignallingServer();
            break;

        case webrtcrestart:
            video.webrtcRestart();
            break;

        case clientjs:
            String arr[] = str.split(" ",2);
            if (arr.length == 1)
                sendplayerfunction(arr[0], "");
            else if (arr.length >= 2)
                sendplayerfunction(arr[0], arr[1]);
            break;

		}



	}

    private void setStreamMode(String str) {
        state.set(State.values.stream, str);
        if ( str.equals(streamstate.camandmic.toString()) )
            str = str+"_2";
        final String stream = str;

        Util.log("streaming " + stream, this);

        // message driver
        if (stream.equals(streamstate.camera.toString()) || stream.equals(streamstate.camandmic.toString()))
            messageplayer("streaming " + stream, "multiple",
                    "stream "+stream+" videowidth "+video.lastwidth+" videoheight "+video.lastheight);
        else  messageplayer("streaming " + stream, "stream", stream);
    }

	public void publish(streamstate mode) {
		
		if (state.getBoolean(State.values.autodocking.name())) {
			messageplayer("command dropped, autodocking", null, null);
			return;
		}

		Malg.checkIfInverted();

		String current = settings.readSetting(GUISettings.vset);
		String vals[] = (settings.readSetting(current)).split("_");
		int width = Integer.parseInt(vals[0]);
		int height = Integer.parseInt(vals[1]);
		int fps = Integer.parseInt(vals[2]);
		int quality = Integer.parseInt(vals[3]);

        video.publish(mode, width, height, fps, quality);
        return;

	}


    public boolean frameGrab() {

        if (!(state.get(State.values.stream).equals(Application.streamstate.camera.toString()) ||
                state.get(State.values.stream).equals(Application.streamstate.camandmic.toString()))) {
            Util.log("stream unavailable", this);
            return false;
        }

        if(state.getBoolean(State.values.framegrabbusy)) {
            Util.log("state framegrab busy", this);
            return false;
        }

        video.framegrab();

        return true;
    }

	public void messageplayer(String str, String status, String value) {

        CommServlet.sendToClient(str, "green", status, value );

		if(commandServer!=null) {
			if(str!=null){
				if(! str.equals(SERVEROK)) // basic ping from client, ignore
				commandServer.sendToGroup(TelnetServer.MSGPLAYERTAG + " " + str);
			}
			if (status !=null) {
				commandServer.sendToGroup(TelnetServer.MSGPLAYERTAG + " <status> " + status + " " + value);
			}
		}

	}

	public void sendplayerfunction(String fn, String params) {

        CommServlet.sendToClientFunction(fn, params);
    }

	public void saySpeech(String str) {

		Util.debug("SPEECH sayspeech: " + str, this);
		
		try {
			String strarr[] = {"espeak",str};
			Runtime.getRuntime().exec(strarr);
		} catch (IOException e) { Util.printError(e); }
	
	}

	private void getEmailSettings() {
		String str = settings.readSetting(GUISettings.email_smtp_server) + " "
				+ settings.readSetting(GUISettings.email_smtp_port) + " "
				+ settings.readSetting(GUISettings.email_username) + " "
				+ settings.readSetting(GUISettings.email_password) + " " // display as dots
				+ settings.readSetting(GUISettings.email_from_address) + " "
				+ settings.readSetting(GUISettings.email_to_address);

		sendplayerfunction("emailsettingsdisplay", str);
	}

	private void emailSettingsUpdate(String str) {
		String s[] = str.split(" ");
		settings.writeSettings(GUISettings.email_smtp_server, s[0]);
		settings.writeSettings(GUISettings.email_smtp_port, s[1]);
		settings.writeSettings(GUISettings.email_username, s[2]);
		settings.writeSettings(GUISettings.email_password, s[3]);
		settings.writeSettings(GUISettings.email_from_address, s[4]);
		settings.writeSettings(GUISettings.email_to_address, s[5]);
		messageplayer("email settings updated", null, null);
	}

	private void getDrivingSettings() {
		if (loginRecords.isAdmin()) {
			String str = comport.speedslow + " " + comport.speedmed + " "
					+ comport.nudgedelay + " " + comport.maxclicknudgedelay
					+ " " + comport.maxclickcam
					+ " " + comport.fullrotationdelay + " " + comport.onemeterdelay + " " 
					+ settings.readSetting(GUISettings.steeringcomp.name()) + " "
					+ Malg.CAM_HORIZ + " " + Malg.CAM_REVERSE;
			sendplayerfunction("drivingsettingsdisplay", str);
		}
	}

	private void drivingSettingsUpdate(String str) {
		if (loginRecords.isAdmin()) {
			String comps[] = str.split(" "); 
			comport.speedslow = Integer.parseInt(comps[0]);
			settings.writeSettings(GUISettings.speedslow, Integer.toString(comport.speedslow));
			
			comport.speedmed = Integer.parseInt(comps[1]);
			settings.writeSettings(GUISettings.speedmed, Integer.toString(comport.speedmed));
			
			comport.nudgedelay = Integer.parseInt(comps[2]);
			settings.writeSettings(GUISettings.nudgedelay, Integer.toString(comport.nudgedelay));
			
			comport.maxclicknudgedelay = Integer.parseInt(comps[3]);
			settings.writeSettings(GUISettings.maxclicknudgedelay, Integer.toString(comport.maxclicknudgedelay));
			
			comport.maxclickcam = Integer.parseInt(comps[4]);
			settings.writeSettings(GUISettings.maxclickcam,Integer.toString(comport.maxclickcam));
			
			comport.fullrotationdelay = Integer.parseInt(comps[5]);
			settings.writeSettings(GUISettings.fullrotationdelay, Integer.toString(comport.fullrotationdelay));

			comport.onemeterdelay = Integer.parseInt(comps[6]);
			settings.writeSettings(GUISettings.onemeterdelay, Integer.toString(comport.onemeterdelay));
			
			comport.setSteeringComp(comps[7]);
			settings.writeSettings(GUISettings.steeringcomp, comps[7]);
			
			comport.setCameraStops(Integer.parseInt(comps[8]), Integer.parseInt(comps[9]));

			String s = comport.speedslow + " " + comport.speedmed + " " 
					+ comport.nudgedelay + " " + comport.maxclicknudgedelay
					+ " " + comport.maxclickcam
					+ " " + comport.fullrotationdelay 
					+ " " + comport.onemeterdelay +  " "  + comport.steeringcomp + " "
					+ Malg.CAM_HORIZ + " " + Malg.CAM_REVERSE;
			
			messageplayer("driving settings set to: " + s, null, null);
		}
	}

	public void message(String str, String status, String value) {
		messageplayer(str, status, value);
	}

	private void moveMacroCancel() {
		if (state.getBoolean(State.values.docking.name())) {
			String str = "";
            if (!state.equals(State.values.dockstatus, AutoDock.DOCKED)) {
                state.set(State.values.dockstatus, AutoDock.UNDOCKED);
                str += "dock " + AutoDock.UNDOCKED;
            }
			messageplayer("docking cancelled", "multiple", str);
			state.set(State.values.docking, false);
//			powerport.manualSetBatteryUnDocked();
		}
	}

	private void statusCheck(String s) {

		if (initialstatuscalled == false || s.equals("intial")) {
			initialstatuscalled = true; 
			
			// build string
			String str = "";
			if (comport != null) {
				String spd = "FAST";
				if (state.getInteger(State.values.motorspeed) == comport.speedmed) spd = "MED";
				if (state.getInteger(State.values.motorspeed) == comport.speedslow) spd = "SLOW";

				String mov = "STOPPED";
				if (!state.getBoolean(State.values.motionenabled)) mov = "DISABLED";
				if (state.getBoolean(State.values.moving)) mov = "MOVING";
				str += " speed " + spd + " cameratilt " + state.get(State.values.cameratilt) + " motion " + mov;
				str += " light " + state.get(State.values.spotlightbrightness);
				str += " floodlight " + state.get(State.values.floodlightlevel);

			}

			str += " vidctroffset " + settings.readSetting(GUISettings.vidctroffset);
			str += " rovvolume " + settings.readSetting(GUISettings.volume);
			str += " stream " + state.get(State.values.stream);

			if (loginRecords.isAdmin())    str += " admin true";
			
			if (state.get(State.values.dockstatus) != null) str += " dock "+ state.get(State.values.dockstatus);
			
			
			if (settings.getBoolean(ManualSettings.developer)) str += " developer true";
			if (settings.getBoolean(GUISettings.navigation)) str += " navigation "+state.get(values.navsystemstatus);

			str += " battery " + state.get(State.values.batterylife);

			if (state.exists(values.record)) str += " record " + state.get(values.record);
			
			messageplayer(SERVEROK, "multiple", str.trim());

		} else { 
			if (s.equals("battcheck")) { 
				messageplayer(SERVEROK, "battery", state.get(State.values.batterylife));
			} else { // ping only
				messageplayer(SERVEROK,null, null);
			}
		}
	}

	private void streamSettingsCustom(String str) {
		settings.writeSettings(GUISettings.vset, "vcustom");
		settings.writeSettings(GUISettings.vcustom, str);
		String s = "custom stream set to: " + str;
		if (!state.get(State.values.stream).equals(Application.streamstate.stop.toString()) &&
				!state.getBoolean(State.values.autodocking)) {
			publish(streamstate.valueOf(state.get(State.values.stream).toString()));
			s += "<br>restarting stream";
		}
		messageplayer(s, null, null);
		Util.log("stream changed to " + str,this);
	}

	private void streamSettingsSet(String str) {
		settings.writeSettings(GUISettings.vset, "v" + str);
		String s = "stream set to: " + str;
		if (!state.get(State.values.stream).equals(Application.streamstate.stop.toString()) &&
				!state.getBoolean(State.values.autodocking)) {
			publish(streamstate.valueOf(state.get(State.values.stream).toString()));
			s += "<br>restarting stream";
		}
		messageplayer(s, null, null);
		Util.log("stream changed to " + str, this);
	}

	private String streamSettings() {
		String result = "";
		result += settings.readSetting("vset") + "_";
		result += settings.readSetting("vlow") + "_" + settings.readSetting("vmed") + "_";
		result += settings.readSetting("vhigh") + "_";
		result += settings.readSetting("vcustom");
		return result;
	}

    private void shutdownCommonTasks() {

        if(commandServer!=null) {
            commandServer.sendToGroup(TelnetServer.TELNETTAG + " shutdown");
            commandServer.close();
        }

        if (powerport.isConnected()) powerport.writeStatusToEeprom();
        PowerLogger.close();

        if (navigation != null) {
            if (!state.get(State.values.navsystemstatus).equals(Ros.navsystemstate.stopped.toString()))
                navigation.stopNavigation();
        }


        if (state.exists(values.odomlinearpwm)) {
            settings.writeSettings(ManualSettings.odomlinearpwm,
                    String.valueOf((int) comport.unVoltsComp(state.getDouble(values.odomlinearpwm))));
        }
        if (state.exists(values.odomturnpwm)) {
            settings.writeSettings(ManualSettings.odomturnpwm,
                    String.valueOf((int) comport.unVoltsComp(state.getDouble(values.odomturnpwm))));
        }

        if (state.exists(values.driver))  driverCallServer(PlayerCommands.driverexit, null);

        running = false;

    }

	// TODO: ALLOW TO ONLY BE CALLED ONCE?
	// state.set(shuttingdown, true); 
	public void restart() { 

		Util.debug("Restart uptime was: "+ state.getUpTime(), this);	
		messageplayer("restarting server application", null, null);
		
		// write file as restart flag for script
        File f = new File(Settings.tomcathome + Util.sep + RESTARTFILE);
		if (!f.exists()) try { f.createNewFile(); } catch (Exception e) {}
		
		shutdownApplication();
	}
	
	private void reboot() {

		Util.log("rebooting system", this);
		PowerLogger.append("rebooting system", this);

        shutdownCommonTasks();

		Util.delay(1000);

        Util.systemCall(Settings.tomcathome + Util.sep + "systemreboot.sh");
	}

	private void powerdown() { // typically called with powershutdown so has to happen quick, skip usual shutdown stuff
		Util.log("powering down system", this);
		PowerLogger.append("powering down system", this);
		powerport.writeStatusToEeprom();
		Util.delay(1000);

		Util.systemCall(Settings.tomcathome + Util.sep + "systemshutdown.sh");
	}

	private void shutdownApplication() {

        if (!running) return;

        Util.log("shutting down application", this);
		PowerLogger.append("shutting down application", this);

		if(commandServer!=null) {
			commandServer.sendToGroup(TelnetServer.TELNETTAG + " shutdown");
			commandServer.close();
		}

        shutdownCommonTasks();

        Video.killTURNserver();
        video.killSignallingServer();

        File f = new File(Settings.tomcathome + Util.sep + HALTFILE);
        if (!f.exists()) try { f.createNewFile(); } catch (Exception e) {}

	}

	private void move(final String str) {

		if (str.equals(Malg.direction.stop.name())) {
			if (state.getBoolean(State.values.autodocking))
				docker.autoDockCancel();
			state.set(values.calibratingrotation, false);
			state.set(values.odomrotating, false);

			comport.stopGoing();
			moveMacroCancel();

			message("command received: " + str, "motion", "STOPPED");
			return;
		}
		
		if (state.getBoolean(State.values.autodocking)) {
			messageplayer("command dropped, autodocking", null, null);
			return;
		}
		
		if (str.equals(Malg.direction.forward.toString())) {
			if (!state.getBoolean(State.values.motionenabled)) state.set(State.values.motionenabled, true);
			comport.goForward();
		}
	
		if (!state.getBoolean(State.values.motionenabled)) {
			messageplayer("motion disabled (try forward)", "motion", "DISABLED");
			return;
		}
		
		moveMacroCancel();
		
		Malg.direction dir = Malg.direction.valueOf(str);
		switch (dir) {
			case backward: comport.goBackward(); break;
			case right: comport.turnRight(); break;
			case left: comport.turnLeft();
			default: break; 
		}
	
		messageplayer("command received: " + str, "motion", "MOVING");
	}

	private void nudge(String str) {

		if (str == null) return;
		if (!state.getBoolean(State.values.motionenabled)) {
			messageplayer("motion disabled (try forward)", "motion", "disabled");
			return;
		}

		if (state.getBoolean(State.values.autodocking)) {
			messageplayer("command dropped, autodocking", null, null);
			return;
		}

		comport.nudge(Malg.direction.valueOf(str));
		messageplayer("command received: nudge " + str, null, null);
		if (state.getBoolean(State.values.docking)	|| state.getBoolean(State.values.autodocking)) moveMacroCancel();
	}

	private void motionEnableToggle() {
		if (state.getBoolean(State.values.motionenabled)) {
			state.set(State.values.motionenabled, false);
			messageplayer("motion disabled", "motion", "disabled");
		} else {
			state.set(State.values.motionenabled, true);
			messageplayer("motion enabled", "motion", "enabled");
		}
	}
	
	private void clickSteer(String str) {
		
		if (str == null) return;
		
		if (!state.getBoolean(State.values.motionenabled)) {
			messageplayer("motion disabled (try forward)", "motion", "disabled");
			return;
		}

		if (state.getBoolean(State.values.autodocking)) {
			messageplayer("command dropped, autodocking", null, null);
			return;
		}

		moveMacroCancel();
		String[] xy = str.split(" ");
		comport.clickSteer(Integer.parseInt(xy[0]), Integer.parseInt(xy[1]));
	}

    public String logintest(String user, String pass, String remember) {
        String encryptedPassword = (passwordEncryptor.encryptPassword(user + salt + pass)).trim();

        if(logintest(user, encryptedPassword) == null) return null;

        if (remember.equals("remember")) authtoken = encryptedPassword;
        return user;
    }

	public String logintest(String user, String pass) {
		int i;
		String value = "";
		String returnvalue = null;
		if (user.equals("")) {
			i = 0;
			while (true) {
				value = settings.readSetting("pass" + i);
				if (value == null) {
					break;
				} else {
					if (value.equals(pass)) {
						returnvalue = settings.readSetting("user" + i);
						break;
					}
				}
				i++;
			}
		} else {
			i = 0;
			while (true) {
				value = settings.readSetting("user" + i);
				if (value == null) {
					break;
				} else {
					if (value.equals(user)) {
						if ((settings.readSetting("pass" + i)).equals(pass)) {
							returnvalue = user;
						}
						break;
					}
				}
				i++;
			}
		}
		return returnvalue;
	}


	private void account(String fn, String str) {
		if (fn.equals("password_update")) passwordChange(state.get(State.values.driver), str);


		if (fn.equals("new_user_add")) {
			String message = "";
			Boolean oktoadd = true;
			String u[] = str.split(" ");
			if (!u[0].matches("\\w+")) {
				message += "error: username must be letters/numbers only ";
				oktoadd = false;
			}
			if (!u[1].matches("\\w+")) {
				message += "error: password must be letters/numbers only ";
				oktoadd = false;
			}
			int i = 0;
			String s;
			while (true) {
				s = settings.readSetting("user" + i);
				if (s == null) break;
				if ((s.toUpperCase()).equals((u[0]).toUpperCase())) {
					message += "ERROR: user name already exists ";
					oktoadd = false;
				}
				i++;
			}
			// add check for existing user, user loop below to get i while you're at it
			if (oktoadd) {
				message += "added user " + u[0];
				settings.newSetting("user" + i, u[0]);
				String p = u[0] + salt + u[1];
				String encryptedPassword = passwordEncryptor.encryptPassword(p);
				settings.newSetting("pass" + i, encryptedPassword);
			}
			messageplayer(message, null, null);
		}
		if (fn.equals("user_list")) {
			int i = 1;
			String users = "";
			String u;
			while (true) {
				u = settings.readSetting("user" + i);
				if (u == null) {
					break;
				} else {
					users += u + " ";
				}
				i++;
			}
			sendplayerfunction("userlistpopulate", users);
		}
		if (fn.equals("delete_user")) {
			int i = 1;
			int usernum = -1;
			int maxusernum = -1;
			String[] allusers = new String[999];
			String[] allpasswords = new String[999];
			String u;
			while (true) {
				// read & store all users+passwords, note number to be deleted, and max number
				u = settings.readSetting("user" + i);
				if (u == null) {
					maxusernum = i - 1;
					break;
				}
				if (u.equals(str)) {
					usernum = i;
				}
				allusers[i] = u;
				allpasswords[i] = settings.readSetting("pass" + i);
				i++;
			}
			if (usernum > 0) {
				i = usernum;
				while (i <= maxusernum) { // delete user to be delted + all after
					settings.deleteSetting("user" + i);
					settings.deleteSetting("pass" + i);
					i++;
				}
				i = usernum + 1;
				while (i <= maxusernum) { // shuffle remaining past deleted one, down one
					settings.newSetting("user" + (i - 1), allusers[i]);
					settings.newSetting("pass" + (i - 1), allpasswords[i]);
					i++;
				}
			}
			messageplayer(str + " deleted.", null, null);
		}
		if (fn.equals("extrauser_password_update")) {
			String s[] = str.split(" ");
			passwordChange(s[0], s[1]);
		}
		if (fn.equals("username_update")) {
			String u[] = str.split(" ");
			String message = "";
			Boolean oktoadd = true;
			if (!u[0].matches("\\w+")) {
				message += "error: username must be letters/numbers only ";
				oktoadd = false;
			}
			int i = 1;
			String s;
			while (true) {
				s = settings.readSetting("user" + i);
				if (s == null) {
					break;
				}
				if ((s.toUpperCase()).equals(u[0].toUpperCase())) {
					message += "error: user name already exists ";
					oktoadd = false;
				}
				i++;
			}
			String encryptedPassword = (passwordEncryptor.encryptPassword(state.get(State.values.driver) + salt + u[1])).trim();
			if (logintest(state.get(State.values.driver), encryptedPassword) == null) {
				message += "error: wrong password";
				oktoadd = false;
			}
			if (oktoadd) {
				message += "username changed to: " + u[0];
				messageplayer("username changed to: " + u[0], "user", u[0]);
				settings.writeSettings("user0", u[0]);
				state.set(State.values.driver, u[0]);
				String p = u[0] + salt + u[1];
				encryptedPassword = passwordEncryptor.encryptPassword(p);
				settings.writeSettings("pass0", encryptedPassword);
			} else {
				messageplayer(message, null, null);
			}
		}
	}

	private void passwordChange(final String user, final String pass) {
		Util.debug(user + " " + pass, this);
		String message = "password updated";
		if (pass.matches("\\w+")) {
			String p = user + salt + pass;
			String encryptedPassword = passwordEncryptor.encryptPassword(p);
			int i = 0;
			String u;
			while (true) {
				u = settings.readSetting("user" + i);
				if (u == null) {
					break;
				} else {
					if (u.equals(user)) {
						settings.writeSettings("pass" + i, encryptedPassword);
						break;
					}
				}
				i++;
			}
		} else {
			message = "error: password must be alpha-numeric with no spaces";
		}
		messageplayer(message, null, null);
	}

	private void chat(String str) {

		Util.log("chat: " + str,this);
		if(str!=null) if (commandServer != null) {
			str = str.replaceAll("</?i>", "");
			commandServer.sendToGroup(TelnetServer.TELNETTAG+" chat from "+ str);
		}
	}

	private void showlog(String str) {
		int lines = 100; //default	
		if (!str.equals("")) { lines = Integer.parseInt(str); }
		String header = "last "+ Integer.toString(lines)  +" line(s) from "+Settings.stdout+" :<br>";
		sendplayerfunction("showserverlog", header + Util.tail(Settings.stdout, lines));
	}

	private void softwareUpdate(String str) {

		if (str.equals("check")) {
			messageplayer("checking for new software...", null, null);
			Updater updater = new Updater();
			double currver = updater.getCurrentVersion();
			String fileurl = updater.checkForUpdateFile();
			double newver = updater.versionNum(fileurl);
			if (newver > currver) {
				String message = "New version available: v." + newver + "\n";
				if (currver == -1) {
					message += "Current software version unknown\n";
				} else {
					message += "Current software is v." + currver + "\n";
				}
				message += "Do you want to download and install?";
				messageplayer("new version available", "softwareupdate",
						message);
			} else {
				messageplayer("no new version available", null, null);
			}
		}
		if (str.equals("download")) {
			messageplayer("downloading software update...", null, null);
			new Thread(new Runnable() {
				public void run() {
					Updater up = new Updater();
					final String fileurl = up.checkForUpdateFile();
					Util.log("downloading url: " + fileurl,this);
					Downloader dl = new Downloader();
					if (dl.FileDownload(fileurl, "update.zip", "download")) {
						messageplayer("update download complete, unzipping...",
								null, null);

						// this is a blocking call
						if (dl.unzipFolder("download"+Util.sep+"update.zip", "webapps"))
							messageplayer("done.", "softwareupdate",
									"downloadcomplete");

						// not needed now is unpacked
						dl.deleteDir(new File(Settings.tomcathome +Util.sep+"download"));

					} else {
						messageplayer("update download failed", null, null);
					}
				}
			}).start();
		}
		if (str.equals("versiononly")) {
			double currver = new Updater().getCurrentVersion();
			String msg = "";
			if (currver == -1)
				msg = "version unknown";
			else
				msg = "version: v." + currver;
			messageplayer(msg, null, null);
		}
	}

	public void factoryReset() {

		final String backup = "conf"+Util.sep+"backup_oculus_settings.txt";

		// backup
		new File(Settings.settingsfile).renameTo(new File(backup));

		// delete it, build on startup
		new File(Settings.settingsfile).delete();

		restart();
	}


}


