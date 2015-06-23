package oculusPrime;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.Collection;
import java.util.Set;

import developer.NavigationLog;
import developer.image.OpenCVMotionDetect;
import developer.image.OpenCVObjectDetect;
import oculusPrime.State.values;
import oculusPrime.commport.ArduinoPower;
import oculusPrime.commport.ArduinoPrime;
import oculusPrime.commport.PowerLogger;

import org.opencv.core.Core;
import org.red5.server.adapter.MultiThreadedApplicationAdapter;
import org.red5.server.api.IConnection;
import org.red5.server.api.Red5;
import org.red5.server.api.service.IServiceCapableConnection;
import org.jasypt.util.password.*;

import developer.Navigation;
import developer.UpdateFTP;
import developer.Ros;
import developer.depth.Mapper;

/** red5 application */
public class Application extends MultiThreadedApplicationAdapter {

	public enum streamstate { stop, camera, camandmic, mic };
	public enum camquality { low, med, high, custom };
	public enum driverstreamstate { stop, mic, pending };
	public static final String VIDEOSOUNDMODELOW = "low";
	public static final String VIDEOSOUNDMODEHIGH = "high";
	private static final int STREAM_CONNECT_DELAY = 2000;
	private static final int GRABBERRELOADTIMEOUT = 5000;
	public static final String RED5_HOME = System.getenv("RED5_HOME");
	public static final String APPFOLDER = "webapps" + Util.sep + "oculusPrime";
	
	private ConfigurablePasswordEncryptor passwordEncryptor = new ConfigurablePasswordEncryptor();
	private boolean initialstatuscalled = false; 
	private boolean pendingplayerisnull = true;
	public IConnection grabber = null;
	private IConnection player = null;
	private String authtoken = null;
	private String salt = null;
	
	private Settings settings = null;
	private State state = null;
	private BanList banlist = null;
	private LoginRecords loginRecords = null;
	private IConnection pendingplayer = null;
	private AutoDock docker = null;
	private SystemWatchdog watchdog;

	public ArduinoPrime comport = null;
	public ArduinoPower powerport = null;
	public TelnetServer commandServer = null;
	
	public static developer.depth.OpenNIRead openNIRead = null;
	public static developer.depth.ScanUtils scanUtils = null;
	private developer.Navigation navigation = null;

	public static byte[] framegrabimg  = null;
	public static BufferedImage processedImage = null;
	public static BufferedImage videoOverlayImage = null;
	
	public Application() {
		super();
		Util.log("\n==============Oculus Prime Java Start Ach:"+ System.getProperty("sun.arch.data.model")  +"===============", this);
		PowerLogger.append("\n==============Oculus Prime Java Start Ach:"+ System.getProperty("sun.arch.data.model")  +"===============", this);

		passwordEncryptor.setAlgorithm("SHA-1");
		passwordEncryptor.setPlainDigest(true);
		loginRecords = new LoginRecords(this);
		settings = Settings.getReference();
		state = State.getReference();
		NetworkMonitor.setApp(this);
		banlist = BanList.getRefrence();
		FrameGrabHTTP.setApp(this);
		RtmpPortRequest.setApp(this);
		
		initialize();
	}

	@Override
	public boolean appConnect(IConnection connection, Object[] params) {

		String logininfo[] = ((String) params[0]).split(" ");

		// always accept local grabber
		if ((connection.getRemoteAddress()).equals("127.0.0.1") && logininfo[0].equals("")) return true;

		// TODO: if banned, but cookie exists?? 
		if(banlist.isBanned(connection.getRemoteAddress())) return false;
		
		// test for cookie auth
		if (logininfo.length == 1) { 
			String username = logintest("", logininfo[0]);
			if (username != null) {
				state.set(State.values.pendinguserconnected, username);
				banlist.clearAddress(connection.getRemoteAddress());
				return true;
			}
		}
	
		 // test for user/pass/remember
		if (logininfo.length > 1) {
			String encryptedPassword = (passwordEncryptor.encryptPassword(logininfo[0] + salt + logininfo[1])).trim();
			if (logintest(logininfo[0], encryptedPassword) != null) {
				if (logininfo[2].equals("remember")) {
					authtoken = encryptedPassword;
				}
				state.set(State.values.pendinguserconnected, logininfo[0]);
				banlist.clearAddress(connection.getRemoteAddress());
				return true;
			}
		}
		
		banlist.loginFailed(connection.getRemoteAddress(), logininfo[0]);			
		return false;
	}

	@Override
	public void appDisconnect(IConnection connection) {
		if(connection==null) { return; }
		if (connection.equals(player)) {
			String str = state.get(State.values.driver) + " disconnected";
			
			Util.log("appDisconnect(): " + str,this); 

			messageGrabber(str, "connection awaiting&nbsp;connection");
			loginRecords.signoutDriver();

			//if autodocking, keep autodocking
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
	
					if (!state.get(State.values.driverstream).equals(driverstreamstate.stop.toString())) {
						state.set(State.values.driverstream, driverstreamstate.stop.toString());
						grabberPlayPlayer(0);
						messageGrabber("playerbroadcast", "0");
					}
					
				}
				
				// this needs to be before player = null
				if (state.get(State.values.pendinguserconnected) != null) {
					assumeControl(state.get(State.values.pendinguserconnected));
					state.delete(State.values.pendinguserconnected);
					return;
				}
			}
			
			player = null;
			connection.close();
		}
		
		if (connection.equals(grabber)) {
			grabber = null;
			// wait a bit, see if still no grabber, THEN reload
			new Thread(new Runnable() {
				public void run() {
					try {
						Thread.sleep(8000);
						if (grabber == null) {
							grabberInitialize();
						}
					} catch (Exception e) {
						Util.printError(e);
					}
				}
			}).start();
			return;
		}
		
		state.delete(State.values.pendinguserconnected);
		//TODO: extend IConnection class, associate loginRecord  (to get passenger info)
		// currently no username info when passenger disconnects
	}

	public void grabbersignin(String mode) {
		if (mode.equals("init")) {
			state.delete(State.values.stream);
		} else {
			state.set(State.values.stream, "stop");
		}
		grabber = Red5.getConnectionLocal();
		String str = "awaiting&nbsp;connection";
		if (state.get(State.values.driver) != null) {
			str = state.get(State.values.driver) + "&nbsp;connected";
		}
		str += " stream " + state.get(State.values.stream);
		messageGrabber("connected to subsystem", "connection " + str);
		Util.log("grabber signed in from " + grabber.getRemoteAddress(), this);
		
		if (state.get(State.values.driverstream).equals(driverstreamstate.mic.toString())) {
			grabberPlayPlayer(1);
			messageGrabber("playerbroadcast", "1");
		}

		// eliminate any other grabbers
		Collection<Set<IConnection>> concollection = getConnections();
		for (Set<IConnection> cc : concollection) {
			for (IConnection con : cc) {
				if (con instanceof IServiceCapableConnection && con != grabber && con != player
						&& (con.getRemoteAddress()).equals("127.0.0.1")) { 
					con.close();
				}
			}
		}
		
		// set video, audio quality mode in grabber flash, depending on server/client OS
		String videosoundmode=state.get(State.values.videosoundmode);
		if (videosoundmode == null)	videosoundmode=VIDEOSOUNDMODEHIGH;  
		
		setGrabberVideoSoundMode(videosoundmode);
		Util.systemCall(System.getenv("RED5_HOME")+"/flashsymlink.sh");		
	}
 
	/** */
	public void initialize() {
		settings.writeFile();
		salt = settings.readSetting("salt");

		comport = new ArduinoPrime(this); // note: blocking
		powerport = new ArduinoPower(this); // note: blocking

		state.set(State.values.httpport, settings.readRed5Setting("http.port"));
//		state.set(State.values.muteOnROVmove, settings.getBoolean(GUISettings.muteonrovmove));
		initialstatuscalled = false;
		pendingplayerisnull = true;
		
		if (settings.getBoolean(ManualSettings.developer.name())) {
			openNIRead = new developer.depth.OpenNIRead();
			scanUtils = new developer.depth.ScanUtils();
		}
	
		if ( ! settings.readSetting(ManualSettings.telnetport).equals(Settings.DISABLED)) {
			commandServer = new TelnetServer(this);
			Util.debug("telnet server started", this);
		}

		try {
			System.loadLibrary( Core.NATIVE_LIBRARY_NAME ); // opencv
		} catch (Exception e) {
			e.printStackTrace();
			Util.log("opencv native lib not availabe", this);
		}

		if (settings.getBoolean(GUISettings.navigation)) {

			navigation = new developer.Navigation(this);
			navigation.runAnyActiveRoute();
		}
		
		if (UpdateFTP.configured()) new developer.UpdateFTP();

		Util.setSystemVolume(settings.getInteger(GUISettings.volume), this);
		state.set(State.values.volume, settings.getInteger(GUISettings.volume));
		state.set(State.values.driverstream, driverstreamstate.stop.toString());

		grabberInitialize();	
		state.set(State.values.lastusercommand, System.currentTimeMillis());  
		docker = new AutoDock(this, comport, powerport);

		state.set(State.values.lastusercommand, System.currentTimeMillis()); // must be before watchdog
		watchdog = new SystemWatchdog(this); 
		
		new Thread(new Runnable() { public void run() {
			Util.delay(10000);  // arduino takes 10 sec to reach full power?
			comport.strobeflash(ArduinoPrime.mode.on.toString(), 200, 30);
		} }).start();
				
		Util.debug("application initialize done", this);

	}

	private void grabberInitialize() {

		if (settings.getBoolean(GUISettings.skipsetup)) {
			grabber_launch("");
		} else {
			initialize_launch();
		}
	}

	public void initialize_launch() {
		new Thread(new Runnable() {
			public void run() {
				try {
					// stream = null;
					String address = "127.0.0.1:" + state.get(State.values.httpport);

//					Runtime.getRuntime().exec("xdg-open http://" + address + "/oculusPrime/initialize.html");
					Runtime.getRuntime().exec("google-chrome " + address + "/oculusPrime/initialize.html");

				} catch (Exception e) {
					Util.printError(e);
				}
			}
		}).start();
	}

	public void grabber_launch(final String str) {
		new Thread(new Runnable() {
			public void run() {
				try {

					// stream = "stop";
					String address = "127.0.0.1:" + state.get(State.values.httpport);
//					Runtime.getRuntime().exec("xdg-open http://" + address + "/oculusPrime/server.html");
					Runtime.getRuntime().exec("google-chrome " + address + "/oculusPrime/server.html"+str);

				} catch (Exception e) {
					Util.printError(e);
				}
			}
		}).start();
	}

	/** */
	public void playersignin() {		
		// set video, audio quality mode in grabber flash, depending on server/client OS
		String videosoundmode=VIDEOSOUNDMODELOW;

		if (player != null) { // pending connection
			pendingplayer = Red5.getConnectionLocal();
			pendingplayerisnull = false;

			if (pendingplayer instanceof IServiceCapableConnection) {
				IServiceCapableConnection sc = (IServiceCapableConnection) pendingplayer;
				String str = "connection PENDING user " + state.get(State.values.pendinguserconnected);
				if (authtoken != null) {
					// System.out.println("sending store cookie");
					str += " storecookie " + authtoken;
					authtoken =  null;
				}
				str += " someonealreadydriving " + state.get(State.values.driver);

				// this has to be last to above variables are already set in java script
				sc.invoke("message", new Object[] { null, "green", "multiple", str });
				str = state.get(State.values.pendinguserconnected) + " pending connection from: "
						+ pendingplayer.getRemoteAddress();
				
				Util.log("playersignin(): " + str,this);
				messageGrabber(str, null);
				sc.invoke("videoSoundMode", new Object[] { videosoundmode });
			}
		} else { // driver connected
			player = Red5.getConnectionLocal();
			state.set(State.values.driver, state.get(State.values.pendinguserconnected));
			state.delete(State.values.pendinguserconnected);
			String str = "connection connected user " + state.get(values.driver);
			if (authtoken != null) {
				str += " storecookie " + authtoken;
				authtoken = null;
			}
			str += " streamsettings " + streamSettings();
			messageplayer(state.get(State.values.driver) + " connected to OCULUS PRIME", "multiple", str);
			initialstatuscalled = false;
			
			str = state.get(State.values.driver) + " connected from: " + player.getRemoteAddress();
			messageGrabber(str, "connection " + state.get(State.values.driver) + "&nbsp;connected");
			Util.log("playersignin(), " + str, this);
			loginRecords.beDriver();
			
			if (settings.getBoolean(GUISettings.loginnotify)) {
				saySpeech("lawg inn " + state.get(State.values.driver));
			}
			
			IServiceCapableConnection sc = (IServiceCapableConnection) player;
			sc.invoke("videoSoundMode", new Object[] { videosoundmode });
			Util.log("player video sound mode = "+videosoundmode, this);
			
//			state.delete(State.values.controlsinverted);
			watchdog.lastpowererrornotify = null; // new driver not notified of any errors yet
		}
	}
	
	public void driverCallServer(PlayerCommands fn, String str) {
		playerCallServer(fn, str, true);
	}

	/**
	 * distribute commands 
	 * 
	 * @param fn
	 *            is the function to call
	 * 
	 * @param str
	 *            is the parameter to pass onto the function
	 */
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
	
	public void playerCallServer(PlayerCommands fn, String str) {
		playerCallServer(fn, str, false);
	}

	/**
	 * distribute commands from player
	 * 
	 * @param fn
	 *            to call in flash player [file name].swf
	 * @param str
	 *            is the argument string to pass along
	 */
	@SuppressWarnings("incomplete-switch")
	public void playerCallServer(PlayerCommands fn, String str, boolean passengerOverride) {
		
		if (PlayerCommands.requiresAdmin(fn.name()) && !passengerOverride){
			if ( ! loginRecords.isAdmin()){ 
				Util.debug("playerCallServer(), must be an admin to do: " + fn.name() + " curent driver: " + state.get(State.values.driver), this);
				return;
			}
		}
			
		// skip telnet ping broadcast
		if(fn != PlayerCommands.statuscheck) state.set(State.values.lastusercommand, System.currentTimeMillis());
		
		String[] cmd = null;
		if(str!=null) cmd = str.split(" ");

		switch (fn) {
			case chat: chat(str) ;return;
			case beapassenger: beAPassenger(str);return;
			case assumecontrol: assumeControl(str); return;
		}
		
		// must be driver/non-passenger for all commands below 

		if (Red5.getConnectionLocal() != player && player != null && !passengerOverride) {
			Util.log("passenger, command dropped: " + fn.toString(), this);
			return;
		}
		
		switch (fn) {
	
		case move: {
			if (state.exists(State.values.navigationroute) && !passengerOverride && 
					str.equals(ArduinoPrime.direction.stop.toString())) {
				messageplayer("navigation route "+state.get(State.values.navigationroute)+" cancelled by stop", null, null);
				navigation.navlog.newItem(NavigationLog.INFOSTATUS, "Route cancelled by user",
						navigation.routestarttime, null, state.get(values.navigationroute),
						navigation.consecutiveroute);
				navigation.cancelAllRoutes();
			}
			else if (state.exists(State.values.roscurrentgoal) && !passengerOverride && str.equals(ArduinoPrime.direction.stop.toString())) {
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
			comport.camCommand(ArduinoPrime.cameramove.valueOf(str));
			break;
//		case camtiltfast: comport.cameraToPosition(Integer.parseInt(str)); break;
		case camtilt: comport.camtilt(Integer.parseInt(str)); break;
		case getdrivingsettings:getDrivingSettings();break;
		case drivingsettingsupdate:drivingSettingsUpdate(str);break;
		case getemailsettings: getEmailSettings(); break;
		case emailsettingsupdate: emailSettingsUpdate(str); break;
		case motionenabletoggle:motionEnableToggle();break;
		case clicksteer:clickSteer(str);break;
		case streamsettingscustom:streamSettingsCustom(str);break;
		case streamsettingsset:streamSettingsSet(str);break;
		case driverexit: appDisconnect(player); break;
		case playerbroadcast: playerBroadCast(str); break;
		case password_update: account("password_update", str); break;
		case new_user_add: account("new_user_add", str); break;
		case user_list: account("user_list", ""); break;
		case delete_user: account("delete_user", str); break;
		case statuscheck: statusCheck(str); break;
		case extrauser_password_update: account("extrauser_password_update", str); break;
		case username_update: account("username_update", str); break;
		case disconnectotherconnections: disconnectOtherConnections(); break;
		case showlog: showlog(str); break;
		case publish: publish(streamstate.valueOf(str)); break;
		case autodockcalibrate: docker.autoDock("calibrate " + str); break;
		case redock: watchdog.redock(str); break;
		case restart: restart(); break;
		case softwareupdate: softwareUpdate(str); break;
//		case muterovmiconmovetoggle: muteROVMicOnMoveToggle(); break;
		case quitserver: quit(); break;
		case setstreamactivitythreshold: setStreamActivityThreshold(str); break;
		case email: new SendMail(str, this); break;
		case uptime: messageplayer(state.getUpTime() + " ms", null, null); break;
// 		case help: messageplayer(PlayerCommands.help(str),null,null); break;
//		case framegrabtofile: FrameGrabHTTP.saveToFile(str); break;
		case memory: messageplayer(Util.memory(), null, null); break;
		case who: messageplayer(loginRecords.who(), null, null); break;
		case loginrecords: messageplayer(loginRecords.toString(), null, null); break;
		case messageclients: messageplayer(str, null,null); Util.log("messageclients: "+str,this); break;
		case dockgrab: 
			if (str!=null) if (str.equals(AutoDock.HIGHRES)) docker.lowres = false;
			docker.dockGrab(AutoDock.dockgrabmodes.start, 0, 0);
			docker.lowres = true;
			break;
		case dockgrabtest:
			if (str.equals("highres")) docker.lowres = false; // ?
			docker.dockGrab(AutoDock.dockgrabmodes.test, 0, 0);
			docker.lowres = true; // ?
			break;
		case rssadd: RssFeed feed = new RssFeed(); feed.newItem(str); break;
		case nudge: nudge(str); break;
		
		case state: 
			
			String s[] = str.split(" ");
			if (s.length == 2) { // two args
				if (s[0].equals("delete")) state.delete(State.values.valueOf(s[1]));
				else state.set(s[0], s[1]); // State.values.valueOf(s[0]), s[1]); 
			}
			else {  
				if (s[0].matches("\\S+")) { // one arg 
					messageplayer("<state> "+s[0]+" "+state.get(State.values.valueOf(s[0])), null, null); 
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
			comport.rotate(ArduinoPrime.direction.valueOf(fn.toString()), Integer.parseInt(str));
			messageplayer(ArduinoPrime.direction.valueOf(fn.toString())+" " + str+"&deg;", "motion", "moving");
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
			comport.movedistance(ArduinoPrime.direction.valueOf(fn.toString()),Double.parseDouble(str));
			messageplayer(ArduinoPrime.direction.valueOf(fn.toString())+" " + str+"m", "motion", "moving");
			break;
			
		case odometrystart:	 	comport.odometryStart(); break;
		case odometryreport: 	comport.odometryReport(); break;
		case odometrystop: 		comport.odometryStop(); break;
			
		case lefttimed: comport.turnLeft(Integer.parseInt(str)); break;
		case righttimed: comport.turnRight(Integer.parseInt(str)); break;
		
		case systemcall:
			Util.log("received: " + str,this);
			messageplayer("system command received", null, null);
			Util.systemCall(str);
			break;

		case serverbrowser:
			grabber_launch(str);
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
			messageGrabber("synth voice: " + str, null);
			saySpeech(str);
			break;
		
		case setsystemvolume:
			Util.setSystemVolume(Integer.parseInt(str), this);
			messageplayer("ROV volume set to "+str+"%", null, null); 
			state.set(State.values.volume, str);
			break;		
			
		case opennisensor:
			if(str.equals("on")) { 
				openNIRead.startDepthCam(); 
				if (!state.getBoolean(State.values.odometry)) comport.odometryStart();
			}
			else { 
				openNIRead.stopDepthCam(); 
				if (state.getBoolean(State.values.odometry)) comport.odometryStop();
			}			
			messageplayer("openNI camera "+str, null, null);
			break;

		case videosoundmode:
			setGrabberVideoSoundMode(str);
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
				String[] STR = str.split(" ");
				mode = STR[0];
				if (STR.length >= 3) {
					duration = Integer.parseInt(STR[1]);
					intensity = Integer.parseInt(STR[2]);
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
				Util.log("power error purposefully dismissed","Application_power");
			}
			break;
			
		case block:
			banlist.addBlockedFile(str);
			break;
			
		case unblock:
			banlist.removeblockedFile(str);
			break;
		
		case powershutdown:
			powerport.shutdown();
			break;
			
		case reboot:
			powerport.writeStatusToEeprom();
			Util.systemCall("pkill chrome"); // prevents error dialog on chrome startup
			Util.delay(1000);
			Util.reboot();
			break;
		
		case systemshutdown:
			powerport.writeStatusToEeprom();
			Util.systemCall("pkill chrome"); // prevents error dialog on chrome startup
			Util.delay(1000);
			Util.shutdown();
			break;
		
		case roslaunch:
			Ros.launch(str);
			messageplayer("roslaunch "+str+".launch", null, null);
			break;
		
		case savewaypoints:
			Ros.savewaypoints(str);
			messageplayer("waypoints saved", null, null);
			break;
			
		case gotowaypoint:
			if (navigation != null) navigation.gotoWaypoint(str);
			break;
		
		case startnav:
			if (navigation != null) navigation.startNavigation(); 
			break;
		
		case stopnav:
			if (navigation != null) navigation.stopNavigation();
			break;
		
		case gotodock:
			if (navigation != null) navigation.dock(); 
			break;
			
		case saveroute: 
			if (navigation != null) navigation.saveRoute(str);
			messageplayer("route saved", null, null);
			break;
			
		case runroute:
			if (navigation != null) {
				navigation.navlog.newItem(NavigationLog.INFOSTATUS, "Route activated by user",
						System.currentTimeMillis(), null, str,
						navigation.consecutiveroute);
				navigation.runRoute(str);
			}
			break;

		case cancelroute:
			if (navigation != null && state.exists(values.navigationroute)) {
				navigation.navlog.newItem(NavigationLog.INFOSTATUS, "Route cancelled by user",
						navigation.routestarttime, null, state.get(values.navigationroute),
						navigation.consecutiveroute);
				navigation.cancelAllRoutes();
			}
			break;

		case startmapping:
			if (navigation != null) navigation.startMapping();
			break;

		case savemap:
			if (navigation != null) navigation.saveMap();
			break;

		case clearmap: Mapper.clearMap();
			break;
		
			// TODO: WHAT IS THIS DOING?? 
			/*
		case error:
			try {
				if(state.get("nonexistentkey").equals("")) {} // throws null pointer
			} catch (Exception e)  { Util.printError(e); }

			break;

		*/
			
//		case motiondetectgo: new motionDetect(this, grabber, Integer.parseInt(str)); break;
		case motiondetect: new OpenCVMotionDetect(this).motionDetectGo(); break;
		case motiondetectcancel: state.delete(State.values.motiondetect); break;
		case motiondetectstream: new OpenCVMotionDetect(this).motionDetectStream(); break;

		case objectdetect: new OpenCVObjectDetect(this).detectGo(str); break;
		case objectdetectcancel: state.delete(values.objectdetect); break;
		case objectdetectstream: new OpenCVObjectDetect(this).detectStream(str); break;

		case framegrabtofile: messageplayer(FrameGrabHTTP.saveToFile(null), null, null); break;
		case log: Util.log(str, this); break;
		case settings: messageplayer(settings.toString(), null, null); break;
		
		case cpu: 
			String cpu = String.valueOf(Util.getCPU());
			if(cpu != null) state.set(values.cpu, cpu);
			break;
			
		}
	}

	/** put all commands here */
	public enum grabberCommands {
		streammode, saveandlaunch, populatesettings, systemcall, chat, dockgrabbed, autodock, 
		restart, checkforbattery, factoryreset, shutdown, streamactivitydetected;
		@Override
		public String toString() {
			return super.toString();
		}
	}

	/**
	 * turn string input to command id
	 * 
	 * @param fn
	 *            is the funct ion to call
	 * @param str
	 *            is the parameters to pass on to the function.
	 */
	public void grabberCallServer(String fn, String str) {
		grabberCommands cmd = null;
		try {
			cmd = grabberCommands.valueOf(fn);
		} catch (Exception e) {
			return;
		}

		if (cmd == null) return;
		else grabberCallServer(cmd, str);
	}

	/**
	 * distribute commands from grabber
	 * 
	 * @param cmd
	 *            is the function to call in xxxxxx.swf ???
	 * @param str
	 *            is the parameters to pass on to the function.
	 */
	@SuppressWarnings("incomplete-switch")
	public void grabberCallServer(final grabberCommands cmd, final String str) {
		
		switch (cmd) {
		case streammode:
			grabberSetStream(str);
			break;
		case saveandlaunch:
			saveAndLaunch(str);
			break;
		case populatesettings:
			populateSettings();
			break;
			case systemcall:
			Util.systemCall(str);
			break;
		case chat:
			chat(str);
			break;
		case dockgrabbed: 
			docker.autoDock("dockgrabbed " + str);
			state.set(State.values.dockgrabbusy.name(), false);
			break;
			case autodock:
			docker.autoDock(str);
			break;
			case factoryreset:
			factoryReset();
				break;
		case restart:
			restart();
			break;
		case shutdown:
			quit();
			break;
		case streamactivitydetected:
			streamActivityDetected(str);
			break;

		}
	}

	private void grabberSetStream(String str) {
		final String stream = str;
		state.set(State.values.stream, str);

		messageGrabber("streaming " + stream, "stream " + stream);
		Util.log("streaming " + stream, this);
		new Thread(new Runnable() {
			public void run() {
				try {
					Thread.sleep(STREAM_CONNECT_DELAY);
//					if (stream.equals(streamstate.camandmic)); Thread.sleep(STREAM_CONNECT_DELAY*2); // longer delay required doesn't help
					Collection<Set<IConnection>> concollection = getConnections();
					for (Set<IConnection> cc : concollection) {
						for (IConnection con : cc) {
							if (con instanceof IServiceCapableConnection
									&& con != grabber
									&& !(con == pendingplayer && !pendingplayerisnull)) {
								IServiceCapableConnection n = (IServiceCapableConnection) con;
								n.invoke("message", new Object[] { "streaming " + stream, "green", "stream", stream });
								Util.debug("message all players: streaming " + stream +" stream " +stream,this);
							}
						}
					}
				} catch (Exception e) {
					Util.printError(e);
				}
			}
		}).start();
	}

	private void setGrabberVideoSoundMode(String str) {
		
		if (state.getBoolean(State.values.autodocking.name())) {
			messageplayer("command dropped, autodocking", null, null);
			return;
		}

		if (state.get(State.values.stream) == null) {
			messageplayer("stream control unavailable, server may be in setup mode", null, null);
			return;
		}

		long timeout = System.currentTimeMillis() + GRABBERRELOADTIMEOUT;
		while (!(grabber instanceof IServiceCapableConnection) && System.currentTimeMillis() < timeout ) { Util.delay(10); }
		if (!(grabber instanceof IServiceCapableConnection))
			Util.log("setGrabberVideoSoundMode() error grabber reload timeout", this);

		IServiceCapableConnection sc = (IServiceCapableConnection) grabber;
		sc.invoke("videoSoundMode", new Object[] { str });
		state.set(State.values.videosoundmode, str);
		Util.log("grabber video sound mode = "+str, this);
	}
	
	public void publish(streamstate mode) {
		
		if (state.getBoolean(State.values.autodocking.name())) {
			messageplayer("command dropped, autodocking", null, null);
			return;
		}

		if (state.get(State.values.stream)  == null) {
			messageplayer("stream control unavailable, server may be in setup mode", null, null);
			return;
		}

		try {
			// commands: camandmic camera mic stop

			long timeout = System.currentTimeMillis() + GRABBERRELOADTIMEOUT;
			while (!(grabber instanceof IServiceCapableConnection) && System.currentTimeMillis() < timeout ) { Util.delay(10); }
			if (!(grabber instanceof IServiceCapableConnection))
				Util.log("publish() error grabber reload timeout", this);

			IServiceCapableConnection sc = (IServiceCapableConnection) grabber;
			String current = settings.readSetting("vset");
			String vals[] = (settings.readSetting(current)).split("_");
			int width = Integer.parseInt(vals[0]);
			int height = Integer.parseInt(vals[1]);
			int fps = Integer.parseInt(vals[2]);
			int quality = Integer.parseInt(vals[3]);
			sc.invoke("publish", new Object[] { mode.toString(), width, height, fps, quality });
			messageplayer("command received: publish " + mode.toString(), null, null);
			Util.log("publish: " + mode.toString(), this);

		} catch (NumberFormatException e) {
			Util.log("publish() error " + e.getMessage(),this);
			Util.printError(e);
		}
		
		if (state.exists(State.values.controlsinverted.toString())) state.delete(State.values.controlsinverted);
	}

	public void muteROVMic() {
		String stream = state.get(State.values.stream);
		if (grabber == null) return;
		if (stream == null) return;
		if (grabber instanceof IServiceCapableConnection
				&& (stream.equals("camandmic") || stream.equals("mic"))) {
			IServiceCapableConnection sc = (IServiceCapableConnection) grabber;
			sc.invoke("muteROVMic", new Object[] {});
		}
	}

	public void unmuteROVMic() {
		String stream = state.get(State.values.stream);
		if (grabber == null) return;
		if (stream == null) return;
		if (grabber instanceof IServiceCapableConnection
				&& (stream.equals("camandmic") || stream.equals("mic"))) {
			IServiceCapableConnection sc = (IServiceCapableConnection) grabber;
			sc.invoke("unmuteROVMic", new Object[] {});
		}
	}

//	private void muteROVMicOnMoveToggle() {
//		if (settings.getBoolean(GUISettings.muteonrovmove)) {
////			state.set(State.values.muteOnROVmove, false);
//			settings.writeSettings(GUISettings.muteonrovmove.toString(), "false");
//			messageplayer("mute ROV onmove off", null, null);
//		} else {
////			state.set(State.values.muteOnROVmove, true);
//			settings.writeSettings(GUISettings.muteonrovmove.toString(), "true");
//			messageplayer("mute ROV onmove on", null, null);
//		}
//	}

	/**  */
	public boolean frameGrab() {

		 if(state.getBoolean(State.values.framegrabbusy.name()) || 
				 !(state.get(State.values.stream).equals("camera") || 
						 state.get(State.values.stream).equals("camandmic"))) {
			 messageplayer("stream unavailable or framegrab busy, command dropped", null, null);
			 return false;
		 }

		if (grabber instanceof IServiceCapableConnection) {
			IServiceCapableConnection sc = (IServiceCapableConnection) grabber;
			sc.invoke("framegrab", new Object[] {});
			state.set(State.values.framegrabbusy.name(), true);
		}

//		Util.debug("framegrab start at: "+System.currentTimeMillis(), this);
		return true;
	}

	/** called by Flash oculusPrime_grabber.swf 
	 * is NOT blocking for some reason ?
	 * */
//	public void frameGrabbed(ByteArray _RAWBitmapImage) {
//
//		int BCurrentlyAvailable = _RAWBitmapImage.bytesAvailable();
//		int BWholeSize = _RAWBitmapImage.length(); // Put the Red5 ByteArray
//													// into a standard Java
//													// array of bytes
//		byte c[] = new byte[BWholeSize];
//		_RAWBitmapImage.readBytes(c);
//		if (BCurrentlyAvailable > 0) {
//			state.set(State.values.framegrabbusy.name(), false);
//			framegrabimg = c;
//		}
//	}
	
	/** called by Flash oculusPrime_grabber.swf after writing data to shared object file 
	 * linux only for now
	 **/
	public void frameGrabbed() {
	
		try {
			
			// read file into bytebuffer
			FileInputStream file = new FileInputStream("/run/shm/oculusPrimeFlashSO/framegrab.sol");
			FileChannel ch = file.getChannel();
			int size = (int) ch.size();
			ByteBuffer frameData = ByteBuffer.allocate( size );
			ch.read(frameData.order(ByteOrder.BIG_ENDIAN));
			ch.close();
			file.close();
			
			int width=640;
			int height=480;
			
			if (settings.readSetting(GUISettings.vset).equals("vmed") || 
					settings.readSetting(GUISettings.vset).equals("vlow")) {  // failed, switch to highres if avail and try again 
				width=320;
				height=240;
			}
			
//			int headersize = 1228843 - (640*480*4);
			int headersize = size - (width*height*4)-1;

			frameData.position(headersize); // skip past header
			
			processedImage  = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			for(int y=0; y<height; y++) {
				for (int x=0; x<width; x++) {
//					int rgb = frameData.getInt();    // argb ok for png only 
					int rgb = frameData.getInt() & 0x00ffffff;  // can't have alpha channel if want to jpeg out
					processedImage.setRGB(x, y, rgb);
				}
			}
			
			state.set(State.values.framegrabbusy.name(), false);

			
		} catch (Exception e) {  Util.printError(e);  }

//		Util.debug("framegrab finished at: "+System.currentTimeMillis(), this);

	}
	
	/** called by Flash oculusPrime_grabber.swf after writing data to shared object file 
	 * linux only for now
	 **/
	public void mediumFrameGrabbed() {
		try {
			
			// read file into bytebuffer
			FileInputStream file = new FileInputStream("/run/shm/oculusPrimeFlashSO/framegrabMedium.sol");
			FileChannel ch = file.getChannel();
			int size = (int) ch.size();
			ByteBuffer frameData = ByteBuffer.allocate( size );
			ch.read(frameData.order(ByteOrder.BIG_ENDIAN));
			ch.close();
			file.close();

			int width = 320;
			int height = 240;
//			int headersize = 307248 - (width*height*4);
			int headersize = size - (width*height*4) -1;
			frameData.position(headersize); // skip past header
			
			processedImage  = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			for(int y=0; y<height; y++) {
				for (int x=0; x<width; x++) {
//					int rgb = frameData.getInt();    // argb ok for png only 
					int rgb = frameData.getInt() & 0x00ffffff;  // can't have alpha channel if want to jpeg out
					processedImage.setRGB(x, y, rgb);
				}
			}
			
			state.set(State.values.framegrabbusy.name(), false);
			
		} catch (Exception e) {			Util.printError(e);		}

//		Util.debug("mediumframegrab finished at: "+System.currentTimeMillis(), this);
	}
	
	public void messageplayer(String str, String status, String value) {
		
		if (player instanceof IServiceCapableConnection) {
			IServiceCapableConnection sc = (IServiceCapableConnection) player;
			sc.invoke("message", new Object[] { str, "green", status, value });
		}
		
		if(commandServer!=null) {
			if(str!=null){
				if(! str.equals("status check received")) // basic ping from client, ignore
				commandServer.sendToGroup(TelnetServer.MSGPLAYERTAG + " " + str);
			}
			if (status !=null) {
				commandServer.sendToGroup(TelnetServer.MSGPLAYERTAG + " <status> " + status + " " + value);
			}
		}
		
		if(str!=null){
			if(! str.equals("status check received")) // basic ping from client, ignore
				Util.debug("messageplayer: "+str+", "+status+", "+value, this);
		}

	}

	public void sendplayerfunction(String fn, String params) {
		if (player instanceof IServiceCapableConnection) {
			IServiceCapableConnection sc = (IServiceCapableConnection) player;
			sc.invoke("playerfunction", new Object[] { fn, params });
		}
		if(commandServer!=null) {
			commandServer.sendToGroup(TelnetServer.MSGPLAYERTAG + " javascript function: " + fn + " "+ params);
		}
	}

	public void saySpeech(String str) {

		Util.debug("SPEECH sayspeech: "+str, this);
		
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
					+ ArduinoPrime.CAM_HORIZ + " " + ArduinoPrime.CAM_REVERSE;
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
					+ ArduinoPrime.CAM_HORIZ + " " + ArduinoPrime.CAM_REVERSE;
			
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

			str += " vidctroffset " + settings.readSetting("vidctroffset");
			str += " rovvolume " + settings.readSetting(GUISettings.volume);
			str += " stream " + state.get(State.values.stream);
			str += " selfstream " + state.get(State.values.driverstream);
			str += " pushtotalk " + settings.readSetting("pushtotalk");
			
			if (loginRecords.isAdmin()) str += " admin true";
			
			if (state.get(State.values.dockstatus) != null) str += " dock "+ state.get(State.values.dockstatus);
			
			
			if (settings.getBoolean(ManualSettings.developer)) str += " developer true";
			if (settings.getBoolean(GUISettings.navigation)) str += " navigation true";

			String videoScale = settings.readSetting("videoscale");
			if (videoScale != null) str += " videoscale " + videoScale;

			str += " battery " + state.get(State.values.batterylife);
			
			messageplayer("status check received", "multiple", str.trim());

		} else { 
			if (s.equals("battcheck")) { 
				messageplayer("status check received", "battery", state.get(State.values.batterylife));
			} else { // ping only
				messageplayer("status check received",null, null); 
			}
		}
	}

	private void streamSettingsCustom(String str) {
		settings.writeSettings(GUISettings.vset, "vcustom");
		settings.writeSettings(GUISettings.vcustom, str);
		String s = "custom stream set to: " + str;
		if (!state.get(State.values.stream).equals("stop") && !state.getBoolean(State.values.autodocking)) {
			publish(streamstate.valueOf(state.get(State.values.stream).toString()));
			s += "<br>restarting stream";
		}
		messageplayer(s, null, null);
		Util.log("stream changed to " + str,this);
	}

	private void streamSettingsSet(String str) {
		settings.writeSettings(GUISettings.vset, "v" + str);
		String s = "stream set to: " + str;
		if (!state.get(State.values.stream).equals("stop") && !state.getBoolean(State.values.autodocking)) {
			publish(streamstate.valueOf(state.get(State.values.stream).toString()));
			s += "<br>restarting stream";
		}
		messageplayer(s, null, null);
		Util.log("stream changed to " + str,this);
	}

	private String streamSettings() {
		String result = "";
		result += settings.readSetting("vset") + "_";
		result += settings.readSetting("vlow") + "_"
				+ settings.readSetting("vmed") + "_";
		result += settings.readSetting("vhigh") + "_"
				+ settings.readSetting("vfull") + "_";
		result += settings.readSetting("vcustom");
		return result;
	}

	public void restart() {
		messageplayer("restarting server application", null, null);

		// write file as restart flag for script
		File f = new File(Settings.redhome + Util.sep + "restart");
		if (!f.exists())
			try {
				f.createNewFile();
			} catch (IOException e) {
				Util.printError(e);
			}
		
		shutdown();
	}
	
	public void quit() { 
		messageplayer("server shutting down", null, null);
		shutdown();
	}

	private void shutdown() {
		
		Util.log("shutting down application", this);
		PowerLogger.append("shutting down application", this);
		
		if(commandServer!=null) { 
			commandServer.sendToGroup(TelnetServer.TELNETTAG + " shutdown");
			commandServer.close();
		}
		
		powerport.writeStatusToEeprom();
		PowerLogger.close();
		
		if (navigation != null) {
			if (!state.get(State.values.navsystemstatus).equals(Ros.navsystemstate.stopped.toString()))
				navigation.stopNavigation();
			if (state.exists(values.odomlinearpwm))
				settings.writeSettings(ManualSettings.odomlinearpwm, state.get(values.odomlinearpwm));
			if (state.exists(values.odomturnpwm))
				settings.writeSettings(ManualSettings.odomturnpwm, state.get(values.odomturnpwm));
		}

		if (! settings.getBoolean(ManualSettings.debugenabled))
			Util.systemCall("pkill chrome");  // TODO: use PID

		Util.systemCall(Settings.redhome + Util.sep + "red5-shutdown.sh");
	}

	public void move(final String str) {

		if (str.equals(ArduinoPrime.direction.stop.name())) {
			if (state.getBoolean(State.values.autodocking))
				docker.autoDockCancel();

			comport.stopGoing();
			moveMacroCancel();

			message("command received: " + str, "motion", "STOPPED");
			return;
		}
		
		if (state.getBoolean(State.values.autodocking)) {
			messageplayer("command dropped, autodocking", null, null);
			return;
		}
		
		if (str.equals(ArduinoPrime.direction.forward.toString())) {
			if (!state.getBoolean(State.values.motionenabled)) state.set(State.values.motionenabled, true);
			comport.goForward();
		}
	
		if (!state.getBoolean(State.values.motionenabled)) {
			messageplayer("motion disabled (try forward)", "motion", "DISABLED");
			return;
		}
		
		moveMacroCancel();
		
		ArduinoPrime.direction dir = ArduinoPrime.direction.valueOf(str);
		switch (dir) {
			case backward: comport.goBackward(); break;
			case right: comport.turnRight(); break;
			case left: comport.turnLeft();
			default: break; 
		}
	
		messageplayer("command received: " + str, "motion", "MOVING");
	}

	public void nudge(String str) {

		if (str == null) return;
		if (!state.getBoolean(State.values.motionenabled)) {
			messageplayer("motion disabled (try forward)", "motion", "disabled");
			return;
		}

		if (state.getBoolean(State.values.autodocking)) {
			messageplayer("command dropped, autodocking", null, null);
			return;
		}

		comport.nudge(ArduinoPrime.direction.valueOf(str));
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

	/** */
	public void messageGrabber(String str, String status) {
		Util.debug("TO grabber flash: "+str+", "+status, this);  

		if (grabber instanceof IServiceCapableConnection) {
			IServiceCapableConnection sc = (IServiceCapableConnection) grabber;
			sc.invoke("message", new Object[] { str, status });
		}
		
		if(commandServer != null) {
			if(str!=null) commandServer.sendToGroup(TelnetServer.MSGGRABBERTAG + " " + str);
			if (status != null) commandServer.sendToGroup(TelnetServer.MSGGRABBERTAG + " <status> " + status );
		}
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

	/** */
	private void assumeControl(String user) { 
		messageplayer("controls hijacked", "hijacked", user);
		if(player==null) return;
		if(pendingplayer==null) { pendingplayerisnull = true; return; }
			
		IConnection tmp = player;
		player = pendingplayer;
		pendingplayer = tmp;
		state.set(State.values.driver, user);
		String str = "connection connected streamsettings " + streamSettings();
		messageplayer(state.get(State.values.driver) + " connected to OCULUS", "multiple", str);
		str = state.get(State.values.driver) + " connected from: " + player.getRemoteAddress();
		Util.log("assumeControl(), " + str,this);
		messageGrabber(str, null);
		initialstatuscalled = false;
		pendingplayerisnull = true;
		loginRecords.beDriver();
		
		if (settings.getBoolean(GUISettings.loginnotify)) {
			saySpeech("lawg inn " + state.get(State.values.driver));
		}
	}

	/** */
	private void beAPassenger(String user) {
		String stream = state.get(State.values.stream);
		pendingplayerisnull = true;
		String str = user + " added as passenger";
		messageplayer(str, null, null);
		Util.log(str,this);
		messageGrabber(str, null);
		if (!stream.equals("stop")) {
			Collection<Set<IConnection>> concollection = getConnections();
			for (Set<IConnection> cc : concollection) {
				for (IConnection con : cc) {
					if (con instanceof IServiceCapableConnection
							&& con != grabber && con != player) {
						IServiceCapableConnection sc = (IServiceCapableConnection) con;
						sc.invoke("message", new Object[] { "streaming " + stream, "green", "stream", stream }); }
				}
			}
		}
		loginRecords.bePassenger(user);
		
		if (settings.getBoolean(GUISettings.loginnotify)) {
			saySpeech("passenger lawg inn f" + user);
		}
	}

	/**
	 * Broadcast remote web client microphone through robot speaker
	 * @param str mode (stop, mic, pending)
	 * 
	 * need to reload webcam capture webpage on robot, and remote web page to enable 
	 * enhanced mic, then reload both again when turning off mic
	 * 
	 * reload grabber, restart video stream if necessary
	 * wait
	 * reload remote client
	 * 
	 */
	private void playerBroadCast(final String str) {
		if (player instanceof IServiceCapableConnection) {
			if (str.equals(driverstreamstate.mic.toString())) { // player mic
				
				if (state.get(State.values.driverstream).equals(driverstreamstate.mic.toString())) return;
				
				String vals[] = "320_240_8_85".split("_"); // TODO: nuke this, for audio only 
				final int width = Integer.parseInt(vals[0]);
				final int height = Integer.parseInt(vals[1]);
				final int fps = Integer.parseInt(vals[2]);
				final int quality = Integer.parseInt(vals[3]);

				final streamstate mode = streamstate.valueOf(state.get(State.values.stream));
				state.set(State.values.driverstream,  driverstreamstate.pending.toString());
				
				new Thread(new Runnable() {
					public void run() {
						try {
							messageplayer("starting self mic, reloading page", null, null);

							// reload grabber page with enhanced mic
							messageGrabber("loadpage", "server.html?broadcast"); // reload page
							Thread.sleep(STREAM_CONNECT_DELAY);
							// restart stream if necessary
							if (!mode.equals(streamstate.stop)) { 
								publish(mode); 
								Thread.sleep(STREAM_CONNECT_DELAY);
							}
														
							// reload driver page with enhanced mic
							messageplayer(null, "loadpage","?broadcast");
							Thread.sleep(STREAM_CONNECT_DELAY*3);
							
							// start driver mic
							IServiceCapableConnection sc = (IServiceCapableConnection) player;
							sc.invoke("publish", new Object[] { str, width, height, fps, quality, false });
							state.set(State.values.driverstream, driverstreamstate.mic.toString());
							Thread.sleep(50);
							messageplayer("self mic on", "selfstream",state.get(State.values.driverstream));
							Thread.sleep(STREAM_CONNECT_DELAY);
							grabberPlayPlayer(1);
							
							
						} catch (Exception e) {
							Util.printError(e);
						}
					}
				}).start();
				
			} else { // player broadcast stop/off
				
				if (state.get(State.values.driverstream).equals(driverstreamstate.stop.toString())) return;

				final streamstate mode = streamstate.valueOf(state.get(State.values.stream));
				state.set(State.values.driverstream,  driverstreamstate.pending.toString());
				
				new Thread(new Runnable() {
					public void run() {
						try {

							messageplayer("stopping self mic, reloading page", null, null);
							
							messageGrabber("loadpage", "server.html"); // reload page normal mic
							Thread.sleep(STREAM_CONNECT_DELAY);
							// restart stream if necessary
							if (!mode.equals(streamstate.stop)) { 
								publish(mode); 
								Thread.sleep(STREAM_CONNECT_DELAY);
							}
							
							messageplayer(null, "loadpage","?");
							Thread.sleep(STREAM_CONNECT_DELAY);
							state.set(State.values.driverstream, driverstreamstate.stop.toString());
							messageplayer("self mic off", "selfstream",state.get(State.values.driverstream));

				
						} catch (Exception e) {
							Util.printError(e);
						}
					}
				}).start();
			}
		}
	}

	private void grabberPlayPlayer(int nostreams) {
		if (grabber instanceof IServiceCapableConnection) {
			IServiceCapableConnection sc = (IServiceCapableConnection) grabber;
			sc.invoke("play", new Object[] { nostreams });
		}
	}

	private void account(String fn, String str) {
		if (fn.equals("password_update")) passwordChange(state.get(State.values.driver), str);
		
		if (loginRecords.isAdmin()){ 
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
	}

	private void passwordChange(final String user, final String pass) {
		Util.debug(user+" "+pass, this);
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

	private void disconnectOtherConnections() {
		if (loginRecords.isAdmin()) {
			int i = 0;
			Collection<Set<IConnection>> concollection = getConnections();
			for (Set<IConnection> cc : concollection) {
				for (IConnection con : cc) {
					if (con instanceof IServiceCapableConnection
							&& con != grabber && con != player) {
						con.close();
						i++;
					}
				}
			}
			messageplayer(i + " passengers eliminated", null, null);
		}
	}

	private void chat(String str) {
		Collection<Set<IConnection>> concollection = getConnections();
		for (Set<IConnection> cc : concollection) {
			for (IConnection con : cc) {
				if (con instanceof IServiceCapableConnection && con != grabber
						&& !(con == pendingplayer && !pendingplayerisnull)) {
					IServiceCapableConnection n = (IServiceCapableConnection) con;
					n.invoke("message", new Object[] { str, "yellow", null, null });
				}
			}
		}
		Util.log("chat: " + str,this);
		messageGrabber("<CHAT>" + str, null);
		if(str!=null) if (commandServer != null) { 
			str = str.replaceAll("</?i>", "");
			commandServer.sendToGroup(TelnetServer.TELNETTAG+" chat from "+ str);
		}
	}

	private void showlog(String str) {
		int lines = 100; //default	
		if (!str.equals("")) { lines = Integer.parseInt(str); }
		String header = "last "+ Integer.toString(lines)  +" line(s) from "+Settings.stdout+" :<br>";
		sendplayerfunction("showserverlog", header + Util.tail(lines));
	}

	private void saveAndLaunch(String str) {
		Util.log("saveandlaunch: " + str,this);
		String message = "";
		Boolean oktoadd = true;
		Boolean restartrequired = false;
		String user = null;
		String password = null;
		String httpport = null;
		String rtmpport = null;
		String skipsetup = null;

		String s[] = str.split(" ");
		for (int n = 0; n < s.length; n = n + 2) {
			// user password comport httpport rtmpport skipsetup developer
			if (s[n].equals("user")) {
				user = s[n + 1];
			}
			if (s[n].equals("password")) {
				password = s[n + 1];
			}
			if (s[n].equals("httpport")) {
				httpport = s[n + 1];
			}
			if (s[n].equals("rtmpport")) {
				rtmpport = s[n + 1];
			}
			if (s[n].equals("skipsetup")) {
				skipsetup = s[n + 1];
			}
		}

		// user & password
		if (user != null) {
			if (!user.matches("\\w+")) {
				message += "Error: username must be letters/numbers only ";
				oktoadd = false;
			}
			if (!password.matches("\\w+")) {
				message += "Error: password must be letters/numbers only ";
				oktoadd = false;
			}
			int i = 1; // admin user = 0, start from 1 (non admin)
			String name;
			while (true) {
				name = settings.readSetting("user" + i);
				if (name == null) {
					break;
				}
				if ((name.toUpperCase()).equals((user).toUpperCase())) {
					message += "Error: non-admin user name already exists ";
					oktoadd = false;
				}
				i++;
			}
			if (oktoadd) {
				String p = user + salt + password;
				String encryptedPassword = passwordEncryptor.encryptPassword(p);
				if (settings.readSetting("user0") == null) {
					settings.newSetting("user0", user);
					settings.newSetting("pass0", encryptedPassword);
				} else {
					settings.writeSettings("user0", user);
					settings.writeSettings("pass0", encryptedPassword);
				}
			}
		} else {
			if (settings.readSetting("user0") == null) {
				oktoadd = false;
				message += "Error: admin user not defined ";
			}
		}

		// httpport
		if (httpport != null) {
			if (!(settings.readRed5Setting("http.port")).equals(httpport)) {
				restartrequired = true;
			}
			settings.writeRed5Setting("http.port", httpport);
		}
		// rtmpport
		if (rtmpport != null) {
			if (!(settings.readRed5Setting("rtmp.port")).equals(rtmpport)) {
				restartrequired = true;
			}
			settings.writeRed5Setting("rtmp.port", rtmpport);
		}

		if (oktoadd) {
			if (skipsetup != null) settings.writeSettings(GUISettings.skipsetup, skipsetup);
			message = "launch server";
			if (restartrequired) {
				message = "shutdown";
				restart();
			}
		}
		messageGrabber(message, null);
	}

	/** */
	private void populateSettings() {
		settings.writeSettings(GUISettings.skipsetup, Settings.FALSE);
		String result = "populatevalues ";

		String str = settings.readSetting("user0");
		if(str != null) result += "username " + str + " ";

		messageGrabber(result, null);
		
		Util.log("populate settings: " + result, this);
		
	}

	public void softwareUpdate(String str) {

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
						dl.deleteDir(new File(Settings.redhome+Util.sep+"download"));

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
	
	private void setStreamActivityThreshold(String str) { 
		String stream = state.get(State.values.stream);
		String val[] = str.split("\\D+");
		if (val.length != 2) { return; } 
		Integer videoThreshold = Integer.parseInt(val[0]);
		Integer audioThreshold = Integer.parseInt(val[1]);

		state.delete(State.values.streamactivity);
		state.set(State.values.streamactivitythreshold, str);
		
		if (videoThreshold != 0 || audioThreshold != 0) {
			if (state.get(State.values.videosoundmode).equals(VIDEOSOUNDMODEHIGH)) {
				setGrabberVideoSoundMode(VIDEOSOUNDMODELOW); // videosoundmode needs to be low to for activity threshold to work
				if (stream != null) {
					if (!stream.equals(streamstate.stop.toString())) { // if stream already running,
						publish(streamstate.valueOf(stream)); // restart, in low mode
					}
				}
			}
			
			if (stream != null) { 
				if (stream.equals(streamstate.stop.toString())) {
					if (audioThreshold == 0 && videoThreshold != 0) { publish(streamstate.camera); }
					else if (audioThreshold != 0 && videoThreshold == 0) { publish(streamstate.mic); }
					else { publish(streamstate.camandmic); }
				}
			}
			state.set(State.values.streamactivityenabled.name(), System.currentTimeMillis());
		}
		else { // 0 0, disable streamActivityDetected()
			state.delete(State.values.streamactivityenabled);
			state.delete(State.values.streamactivitythreshold);
		}

		long timeout = System.currentTimeMillis() + GRABBERRELOADTIMEOUT;
		while (!(grabber instanceof IServiceCapableConnection) && System.currentTimeMillis() < timeout ) { Util.delay(10); }
		if (!(grabber instanceof IServiceCapableConnection))
			Util.log("setStreamActivityThreshold() error grabber reload timeout", this);

		IServiceCapableConnection sc = (IServiceCapableConnection) grabber;
		sc.invoke("setActivityThreshold", new Object[] { videoThreshold, audioThreshold });
		messageplayer("stream activity set to: "+str, null, null);

	}
	
	private void streamActivityDetected(String str) {
		if (! state.exists(State.values.streamactivityenabled)) return;
		if (System.currentTimeMillis() > state.getLong(State.values.streamactivityenabled) + 5000.0) {
			messageplayer("streamactivity: "+str, "streamactivity", str);
			setStreamActivityThreshold("0 0"); // disable
			state.set(State.values.streamactivity, str); // needs to be after disable, method deletes state val
		}
	}

}


