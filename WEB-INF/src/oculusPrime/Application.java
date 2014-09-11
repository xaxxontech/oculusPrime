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

import oculusPrime.commport.ArduinoPower;
import oculusPrime.commport.ArduinoPrime;
import oculusPrime.commport.Discovery;

import org.red5.server.adapter.MultiThreadedApplicationAdapter;
import org.red5.server.api.IConnection;
import org.red5.server.api.Red5;
import org.red5.server.api.service.IServiceCapableConnection;
import org.jasypt.util.password.*;
import org.red5.io.amf3.ByteArray;

import developer.SendMail;
import developer.UpdateFTP;
import developer.depth.Mapper;

/** red5 application */
public class Application extends MultiThreadedApplicationAdapter {

	private static final int STREAM_CONNECT_DELAY = 2000;
	
	private ConfigurablePasswordEncryptor passwordEncryptor = new ConfigurablePasswordEncryptor();
	private String salt = null;
	private String authtoken = null;
	private IConnection grabber = null;
	private IConnection player = null;
	private boolean pendingplayerisnull = true;
	private boolean initialstatuscalled = false; 

	//	private ScriptRunner scriptRunner = new ScriptRunner();	
	private NetworkMonitor networkMonitor = NetworkMonitor.getReference(); 
	// TODO: added to jet is started, could be anywhere, not refrenced in this file yet though.
	
	private LoginRecords loginRecords = new LoginRecords();
	private Settings settings = Settings.getReference();
	private BanList banlist = BanList.getRefrence();
	private State state = State.getReference();
	private IConnection pendingplayer = null;
	private AutoDock docker = null;
	
	public ArduinoPrime comport = null;
	public ArduinoPower powerport = null;
	public TelnetServer commandServer = null;
	
	public static developer.depth.OpenNIRead openNIRead = null;
	public static developer.depth.ScanUtils scanUtils = null;
	public static developer.depth.Stereo stereo = null;
	public static Speech speech = new Speech();
	public static byte[] framegrabimg  = null;
	public static Boolean passengerOverride = false;
	public static BufferedImage processedImage = null;
	
	public Application() {
		super();
		passwordEncryptor.setAlgorithm("SHA-1");
		passwordEncryptor.setPlainDigest(true);
		FrameGrabHTTP.setApp(this);
		RtmpPortRequest.setApp(this);
		AuthGrab.setApp(this);
		initialize();
	}

	@Override
	public boolean appConnect(IConnection connection, Object[] params) {

		String logininfo[] = ((String) params[0]).split(" ");

		// always accept local grabber
		if ((connection.getRemoteAddress()).equals("127.0.0.1") && logininfo[0].equals("")) return true;

		if (logininfo.length == 1) { // test for cookie auth
			String username = logintest("", logininfo[0]);
			if (username != null) {
				state.set(State.values.pendinguserconnected, username);
				return true;
			}
		}
		
		// TODO: test if this IP is brute 
		if(banlist.isBanned(connection.getRemoteAddress())){
			Util.log("appConnect(): " + connection.getRemoteAddress() + " has been banned", this);
			banlist.failed(connection.getRemoteAddress());
			return false;
		}
		
		if (logininfo.length > 1) { // test for user/pass/remember
			String encryptedPassword = (passwordEncryptor.encryptPassword(logininfo[0] + salt + logininfo[1])).trim();
			if (logintest(logininfo[0], encryptedPassword) != null) {
				if (logininfo[2].equals("remember")) {
					authtoken = encryptedPassword;
				}
				state.set(State.values.pendinguserconnected, logininfo[0]);
				return true;
			}
		}
		
		// TODO: record failure
		banlist.failed(connection.getRemoteAddress());			
		String str = "login from: " + connection.getRemoteAddress() + " failed";
		Util.log("appConnect(): " + str);
		messageGrabber(str, "");
		return false;
	}

	@Override
	public void appDisconnect(IConnection connection) {
		if(connection==null) { return; }
		if (connection.equals(player)) {
			String str = state.get(State.values.driver.name()) + " disconnected";
			
			Util.log("appDisconnect(): " + str); 

			messageGrabber(str, "connection awaiting&nbsp;connection");
			loginRecords.signoutDriver();

			if (!state.getBoolean(State.values.autodocking)) { //if autodocking, keep autodocking
				if (state.get(State.values.stream) != null) {
					if (!state.get(State.values.stream).equals("stop")) {
						publish("stop");
					}
				}

				if (comport.isConnected()) { 
					comport.setSpotLightBrightness(0);
					comport.floodLight(0);
					comport.stopGoing();
				}

				if (state.getBoolean(State.values.driverstream)) {
					state.set(State.values.driverstream, false);
					grabberPlayPlayer(0);
					messageGrabber("playerbroadcast", "0");
				}
				
				// this needs to be before player = null
				if (state.get(State.values.pendinguserconnected) != null) {
					assumeControl(state.get(State.values.pendinguserconnected));
					state.delete(State.values.pendinguserconnected);
					return;
				}
			}
			
			player = null;
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
						e.printStackTrace();
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
		if (state.get(State.values.driver.name()) != null) {
			str = state.get(State.values.driver.name()) + "&nbsp;connected";
		}
		str += " stream " + state.get(State.values.stream);
		messageGrabber("connected to subsystem", "connection " + str);
		Util.log("grabber signed in from " + grabber.getRemoteAddress(), this);
		if (state.getBoolean(State.values.driverstream)) {
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
		String videosoundmode=state.get(State.values.videosoundmode.name());
		if (videosoundmode == null) { 
			videosoundmode="high";  
			if (Settings.os.equals("linux")) { // TODO: or motion/sound activity threshold enabled
//				videosoundmode="low";
				videosoundmode="high";
			}
		}
		setGrabberVideoSoundMode(videosoundmode);

		docker = new AutoDock(this, grabber, comport, powerport);
		loginRecords.setApplication(this);

		if (Settings.os.equals("linux")) {
			str = System.getenv("RED5_HOME")+"/flashsymlink.sh";
			Util.systemCall(str);
		}
		
	}
 
	/** */
	public void initialize() {
		settings.writeFile();
		salt = settings.readSetting("salt");

		comport = new ArduinoPrime(this);
		powerport = new ArduinoPower(this);
//		gyroport = new ArduinoGyro(this);
		new Discovery(this);
		
		state.set(State.values.httpPort, settings.readRed5Setting("http.port"));
//		state.set(State.values.muteOnROVmove, settings.getBoolean(GUISettings.muteonrovmove));
		initialstatuscalled = false;
		pendingplayerisnull = true;
		
		if (settings.getBoolean(ManualSettings.developer.name())) {			
			openNIRead = new developer.depth.OpenNIRead();
			scanUtils = new developer.depth.ScanUtils();
			stereo = new developer.depth.Stereo();
		}
	
		if ( ! settings.readSetting(ManualSettings.commandport).equals(Settings.DISABLED)) commandServer = new TelnetServer(this);
			
		if (UpdateFTP.configured()) new developer.UpdateFTP();

		Util.setSystemVolume(settings.getInteger(GUISettings.volume), this);
		state.set(State.values.volume, settings.getInteger(GUISettings.volume));
		
		grabberInitialize();
				
		new SystemWatchdog(); // reboots OS every 2 days
		Util.debug("initialize done", this);

	}

	private void grabberInitialize() {

//		if (Settings.os.equals("linux")) {
//			String str = System.getenv("RED5_HOME")+"/flashsymlink.sh";
//			Util.systemCall(str);
//		}

		if (settings.getBoolean(GUISettings.skipsetup)) {
			grabber_launch();
		} else {
			initialize_launch();
		}
	}

	public void initialize_launch() {
		new Thread(new Runnable() {
			public void run() {
				try {
					// stream = null;
					String address = "127.0.0.1:" + state.get(State.values.httpPort);
					if (Settings.os.equals("linux")) {
						Runtime.getRuntime().exec("xdg-open http://" + address + "/oculusPrime/initialize.html");
					}
					else { // win
						Runtime.getRuntime().exec("cmd.exe /c start http://" + address + "/oculusPrime/initialize.html");
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();
	}

	public void grabber_launch() {
		new Thread(new Runnable() {
			public void run() {
				try {

					// stream = "stop";
					String address = "127.0.0.1:" + state.get(State.values.httpPort);
					if (Settings.os.equals("linux")) {
						Runtime.getRuntime().exec("xdg-open http://" + address + "/oculusPrime/server.html");
					}
					else { // win
						Runtime.getRuntime().exec("cmd.exe /c start http://" + address + "/oculusPrime/server.html");
					}

				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();
	}

	/** */
	public void playersignin() {		
		// set video, audio quality mode in grabber flash, depending on server/client OS
		String videosoundmode="high"; // windows, default
		if (Settings.os.equals("linux")) {
			videosoundmode="low";
		}

		if (player != null) {
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
				str += " someonealreadydriving " + state.get(State.values.driver.name());

				// this has to be last to above variables are already set in java script
				sc.invoke("message", new Object[] { null, "green", "multiple", str });
				str = state.get(State.values.pendinguserconnected) + " pending connection from: "
						+ pendingplayer.getRemoteAddress();
				
				Util.log("playersignin(): " + str);
				messageGrabber(str, null);
				sc.invoke("videoSoundMode", new Object[] { videosoundmode });
			}
		} else {
			player = Red5.getConnectionLocal();
			state.set(State.values.driver.name(), state.get(State.values.pendinguserconnected));
			state.delete(State.values.pendinguserconnected);
			String str = "connection connected user " + state.get(State.values.driver.name());
			if (authtoken != null) {
				str += " storecookie " + authtoken;
				authtoken = null;
			}
			str += " streamsettings " + streamSettings();
			messageplayer(state.get(State.values.driver.name()) + " connected to OCULUS", "multiple", str);
			initialstatuscalled = false;
			
			str = state.get(State.values.driver.name()) + " connected from: " + player.getRemoteAddress();
			messageGrabber(str, "connection " + state.get(State.values.driver.name()) + "&nbsp;connected");
			Util.log("playersignin(), " + str, this);
			loginRecords.beDriver();
			
			if (settings.getBoolean(ManualSettings.loginnotify)) {
				saySpeech("lawg inn " + state.get(State.values.driver));
			}
			
			IServiceCapableConnection sc = (IServiceCapableConnection) player;
			sc.invoke("videoSoundMode", new Object[] { videosoundmode });
			Util.log("player video sound mode = "+videosoundmode, this);
			
			state.delete(State.values.controlsinverted);
		}
	}


//	public void dockGrab() {
//		if (grabber instanceof IServiceCapableConnection) {
//			IServiceCapableConnection sc = (IServiceCapableConnection) grabber;
//			sc.invoke("dockgrab", new Object[] { 0, 0, "find" });
//			state.set(oculus.State.values.dockgrabbusy.name(), true);
//		}
//	}

	/**
	 * distribute commands from pla
	 * 
	 * @param fn
	 *            is the function to call
	 * 
	 * @param str
	 *            is the parameter to pass onto the function
	 */
	public void playerCallServer(final String fn, final String str) {
//		Util.debug("from player flash: "+fn+", "+str, this); 
		
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
		if (cmd != null) playerCallServer(cmd, str);	
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
	public void playerCallServer(final PlayerCommands fn, final String str) {
		
		if (PlayerCommands.requiresAdmin(fn.name()) && !passengerOverride){
			if ( ! loginRecords.isAdmin()){ 
				Util.debug("playerCallServer(), must be an admin to do: " + fn.name() + " curent driver: " + state.get(State.values.driver), this);
				return;
			}
		}
			
		// skip telnet ping broadcast
		if(fn != PlayerCommands.statuscheck) state.put(State.values.lastusercommand, System.currentTimeMillis()); 
		
		String[] cmd = null;
		if(str!=null) cmd = str.split(" ");

		switch (fn) {
			case chat: chat(str) ;return;
			case beapassenger: beAPassenger(str);return;
			case assumecontrol: assumeControl(str); return;
		}
		
		// must be driver/non-passenger for all commands below 
		if(!passengerOverride){
			if (Red5.getConnectionLocal() != player && player != null) {
				Util.log("passenger, command dropped: " + fn.toString(), this);
				return;
			}
		}
		
		switch (fn) {
	
		case battstats: messageplayer(state.get(State.values.batteryinfo),"battery",state.get(State.values.batterylife)); break; // comport.updateBatteryLevel(); break;
		case cameracommand: comport.camCommand(ArduinoPrime.cameramove.valueOf(str));break;
		case cameratoposition: comport.cameraToPosition(Integer.parseInt(str)); break;
		case getdrivingsettings:getDrivingSettings();break;
		case motionenabletoggle:motionEnableToggle();break;
		case drivingsettingsupdate:drivingSettingsUpdate(str);break;
		case clicksteer:clickSteer(str);break;
		case streamsettingscustom:streamSettingsCustom(str);break;
		case streamsettingsset:streamSettingsSet(str);break;
		case playerexit: appDisconnect(player); break;
		case playerbroadcast: playerBroadCast(str); break;
		case password_update: account("password_update", str); break;
		case new_user_add: account("new_user_add", str); break;
		case user_list: account("user_list", ""); break;
		case delete_user: account("delete_user", str); break;
		case statuscheck: statusCheck(str); break;
		case extrauser_password_update: account("extrauser_password_update", str); break;
		case username_update: account("username_update", str); break;
		case disconnectotherconnections: disconnectOtherConnections(); break;
//		case monitor: monitor(str); break;
		case showlog: showlog(str); break;
		case publish: publish(str); break;
		case autodockcalibrate: docker.autoDock("calibrate " + str); break;
		case restart: restart(); break;
		case softwareupdate: softwareUpdate(str); break;
		case muterovmiconmovetoggle: muteROVMicOnMoveToggle(); break;
		case shutdown: quit(); break;
		case setstreamactivitythreshold: setStreamActivityThreshold(str); break;
		case email: new SendMail(str, this); break;
		case uptime: messageplayer(state.getUpTime() + " ms", null, null); break;
		case help: messageplayer(PlayerCommands.help(str),null,null); break;
		case framegrabtofile: FrameGrabHTTP.saveToFile(str); break;
		case memory: messageplayer(Util.memory(), null, null); break;
		case who: messageplayer(loginRecords.who(), null, null); break;
		case loginrecords: messageplayer(loginRecords.toString(), null, null); break;
		case settings: messageplayer(settings.toString(), null, null); break;
		case messageclients: messageplayer(str, null,null); Util.log("messageclients: "+str,this); break;
		case dockgrab: 
			if (str.equals("highres")) docker.lowres = false;
			docker.dockGrab("start", 0, 0);
			docker.lowres = true;
			break;
		case dockgrabtest:
			if (str.equals("highres")) docker.lowres = false;
			docker.dockGrab("test", 0, 0);
			docker.lowres = true;
			break;
//		case digitalread: comport.digitalRead(Integer.parseInt(str)); break;
//		case analogwrite: comport.AnalogWrite(Integer.parseInt(str)); break;
		case rssadd: RssFeed feed = new RssFeed(); feed.newItem(str);
		case move: move(str); break;
		case nudge: nudge(str); break;
		
		case writesetting:
			Util.debug("write setting: " + str, this);
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
			Util.debug("read setting: " + cmd[0], this);
			messageplayer("setting "+cmd[0]+" "+settings.readSetting(cmd[0]),null,null);
			break;

		case speed:
//			comport.speedset(ArduinoPrime.speeds.valueOf(str));
			comport.speedset(str);
			messageplayer("speed set: " + str, "speed", str.toUpperCase());
			break;

		case slide:
			if (!state.getBoolean(State.values.motionenabled.name())) {
				messageplayer("motion disabled", "motion", "disabled");
				break;
			}
			if (state.getBoolean(State.values.autodocking.name())) {
				messageplayer("command dropped, autodocking", null, null);
				break;
			}
			moveMacroCancel();
			comport.slide(ArduinoPrime.direction.valueOf(str));
			messageplayer("command received: " + fn + str, null, null);
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
//			comport.movedistance(ArduinoPrime.direction.valueOf(cmd[0]), Double.parseDouble(cmd[1]));
			comport.movedistance(ArduinoPrime.direction.valueOf(fn.toString()),Double.parseDouble(str));
			messageplayer(ArduinoPrime.direction.valueOf(fn.toString())+" " + str+"m", "motion", "moving");
			break;
			
		case odometrystart:	 	comport.odometryStart(); break;
		case odometryreport: 	comport.odometryReport(); break;
		case odometrystop: 		comport.odometryStop(); break;
			
		case systemcall:
			Util.log("received: " + str);
			messageplayer("system command received", null, null);
			Util.systemCall(str);
			break;

		case relaunchgrabber:
			grabber_launch();
			messageplayer("relaunching grabber", null, null);
			break;

		case docklineposupdate:
			settings.writeSettings("vidctroffset", str);
			messageplayer("vidctroffset set to : " + str, null, null);
			break;

//		case arduinoecho:
//			if (str.equalsIgnoreCase("true"))comport.setEcho(true);
//			else comport.setEcho(false);
//			messageplayer("echo set to: " + str, null, null);
//			break;

		case motorsreset:
			comport.reset();
			messageplayer("resetting arduinoculus", null, null);
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
			if(str.equals("on")) { openNIRead.startDepthCam(); }
			else { openNIRead.stopDepthCam(); }			
			messageplayer("openNI camera "+str, null, null);
			break;
			
		case stereo:
			if (!state.get(State.values.stream).equals("stop")) {
				messageplayer("left camera in use", null, null);
				break;
			}
			if(str.equals("on")) { stereo.startCameras(); }
			else { stereo.stopCameras(); }			
			messageplayer("stereo cameras "+str, null, null);
			break;
			
		case videosoundmode:
			setGrabberVideoSoundMode(str);
			messageplayer("video/sound mode set to: "+str, null, null);
			break;
		
		case pushtotalktoggle:
			settings.writeSettings("pushtotalk", str);
			messageplayer("self mic push T to talk "+str, null, null);
			break;
		
		case state: 
			String s[] = str.split(" ");
			if (s.length == 2) { state.set(s[0], s[1]); }
			else {  
				if (s[0].matches("\\S+")) { 
					messageplayer("<state> "+s[0]+" "+state.get(s[0]), null, null); 
				} else { 
					messageplayer("<state> "+state.toString(), null, null);
				} 
			}
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
			Util.debug("playerCallServer(): autodock: " + str, this);
			docker.autoDock(str); 
			break;
		case getlightlevel:
			docker.getLightLevel(); break;
		case dock:
			Util.debug("playerCallServer(): dock: " + str, this);
//			if(str.equals("undock")) docker.undock();
			if(str.equals("dock")) docker.dock();
			break;
		case strobeflash:
			comport.strobeflash(str);
			messageplayer("strobeflash "+str, null, null);
			break;

		case powerreset:
			powerport.reset();
			break;
			
		case block:
			banlist.addBlockedFile(str);
			break;
			
		case unblock:
			banlist.remove(str);
			break;
		
		case powershutdown:
			powerport.shutdown();
			break;
			
		case reboot:
			Util.reboot();
			break;
		
		case systemshutdown:
			Util.shutdown();
			break;
			
		case clearmap: Mapper.clearMap();
			
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
	 * @param fn
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
					e.printStackTrace();
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
		
		IServiceCapableConnection sc = (IServiceCapableConnection) grabber;
		sc.invoke("videoSoundMode", new Object[] { str });
		state.set(State.values.videosoundmode.name(), str);
		Util.log("grabber video sound mode = "+str, this);
	}
	
	public void publish(String str) {
		
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
			if (grabber instanceof IServiceCapableConnection) {
				IServiceCapableConnection sc = (IServiceCapableConnection) grabber;
				String current = settings.readSetting("vset");
				String vals[] = (settings.readSetting(current)).split("_");
				int width = Integer.parseInt(vals[0]);
				int height = Integer.parseInt(vals[1]);
				int fps = Integer.parseInt(vals[2]);
				int quality = Integer.parseInt(vals[3]);
				sc.invoke("publish", new Object[] { str, width, height, fps, quality });
				messageplayer("command received: publish " + str, null, null);
				Util.log("publish: " + str, this);
			}
		} catch (NumberFormatException e) {
			Util.log("publish() " + e.getMessage());
			e.printStackTrace();
		}
		
		state.delete(State.values.controlsinverted);
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

	private void muteROVMicOnMoveToggle() {
		if (settings.getBoolean(GUISettings.muteonrovmove)) {
//			state.set(State.values.muteOnROVmove, false);
			settings.writeSettings(GUISettings.muteonrovmove.toString(), "false");
			messageplayer("mute ROV onmove off", null, null);
		} else {
//			state.set(State.values.muteOnROVmove, true);
			settings.writeSettings(GUISettings.muteonrovmove.toString(), "true");
			messageplayer("mute ROV onmove on", null, null);
		}
	}

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
		
		Util.debug("framegrab start at: "+System.currentTimeMillis(), this);
		return true;
	}

	/** called by Flash oculusPrime_grabber.swf 
	 * is NOT blocking for some reason ?
	 * */
	public void frameGrabbed(ByteArray _RAWBitmapImage) { 

		int BCurrentlyAvailable = _RAWBitmapImage.bytesAvailable();
		int BWholeSize = _RAWBitmapImage.length(); // Put the Red5 ByteArray
													// into a standard Java
													// array of bytes
		byte c[] = new byte[BWholeSize];
		_RAWBitmapImage.readBytes(c);
		if (BCurrentlyAvailable > 0) {
			state.set(State.values.framegrabbusy.name(), false);
			framegrabimg = c;
		}
	}
	
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

			
		} catch (Exception e) {  e.printStackTrace();  }

		Util.debug("framegrab finished at: "+System.currentTimeMillis(), this);

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
			
		} catch (Exception e) {			e.printStackTrace();		}

		Util.debug("mediumframegrab finished at: "+System.currentTimeMillis(), this);
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
//		if (Settings.os.equals("linux")) {
//			messageplayer("unsupported in linux",null,null);
//			return;
//		}
		//Speech speech = new Speech();   // DONT initialize each time here, takes too long
		Util.debug("SPEECH sayspeech: "+str, this);
		if (Settings.os.equals("linux")) {
			try {
				String strarr[] = {"espeak",str};
				Runtime.getRuntime().exec(strarr);
			} catch (IOException e) { e.printStackTrace(); }
		}
		else { speech.mluv(str); }
		
	}

	private void getDrivingSettings() {
		if (loginRecords.isAdmin()) {
			String str = comport.speedslow + " " + comport.speedmed + " "
					+ comport.nudgedelay + " " + comport.maxclicknudgedelay
					+ " " + comport.clicknudgemomentummult+ " " + comport.maxclickcam
					+ " " + comport.fullrotationdelay + " " + comport.onemeterdelay + " " 
					+ settings.readSetting(GUISettings.steeringcomp.name()) + " "
					+ ArduinoPrime.CAM_HORIZ;
			sendplayerfunction("drivingsettingsdisplay", str);
		}
	}

	private void drivingSettingsUpdate(String str) {
		if (loginRecords.isAdmin()) {
			String comps[] = str.split(" "); 
			comport.speedslow = Integer.parseInt(comps[0]);
			settings.writeSettings("speedslow", Integer.toString(comport.speedslow));
			
			comport.speedmed = Integer.parseInt(comps[1]);
			settings.writeSettings("speedmed", Integer.toString(comport.speedmed));
			
			comport.nudgedelay = Integer.parseInt(comps[2]);
			settings.writeSettings("nudgedelay", Integer.toString(comport.nudgedelay));
			
			comport.maxclicknudgedelay = Integer.parseInt(comps[3]);
			settings.writeSettings("maxclicknudgedelay", Integer.toString(comport.maxclicknudgedelay));
			
			comport.clicknudgemomentummult = Double.parseDouble(comps[4]);
			settings.writeSettings("clicknudgemomentummult", Double.toString(comport.clicknudgemomentummult));
			
			comport.maxclickcam = Integer.parseInt(comps[5]);
			settings.writeSettings("maxclickcam",Integer.toString(comport.maxclickcam));
			
			comport.fullrotationdelay = Integer.parseInt(comps[6]);
			settings.writeSettings(GUISettings.fullrotationdelay.name(), Integer.toString(comport.fullrotationdelay));

			comport.onemeterdelay = Integer.parseInt(comps[7]);
			settings.writeSettings(GUISettings.onemeterdelay.name(), Integer.toString(comport.onemeterdelay));
			
			comport.setSteeringComp(comps[8]);
			settings.writeSettings(GUISettings.steeringcomp.name(), comps[8]);
			
//			ArduinoPrime.CAM_HORIZ = Integer.parseInt(comps[9]);
			comport.setCameraStops(Integer.parseInt(comps[9]));
//			settings.writeSettings(GUISettings.camhoriz.name(), comps[9]);

			String s = comport.speedslow + " " + comport.speedmed + " " 
					+ comport.nudgedelay + " " + comport.maxclicknudgedelay
					+ " " + comport.clicknudgemomentummult +  " "  + comport.maxclickcam
					+ " " + comport.fullrotationdelay 
					+ " " + comport.onemeterdelay +  " "  + comport.steeringcomp + " "
					+ ArduinoPrime.CAM_HORIZ;
			
			messageplayer("driving settings set to: " + s, null, null);
		}
	}

	public void message(String str, String status, String value) {
		messageplayer(str, status, value);
	}

	private void moveMacroCancel() {
		if (state.getBoolean(State.values.docking.name())) {
			String str = "";
            if (!state.equals(State.values.dockstatus.name(), AutoDock.DOCKED)) {
                state.set(State.values.dockstatus, AutoDock.UNDOCKED);
                str += "dock " + AutoDock.UNDOCKED;
            }
			messageplayer("docking cancelled", "multiple", str);
			state.set(State.values.docking, false);
//			powerport.manualSetBatteryUnDocked();
		}
		
		// if (state.getBoolean(State.values.sliding) 
		// TODO: just call it, let port class check it's local 		
		comport.slidecancel();
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
			str += " stream " + state.get(State.values.stream) + " selfstream stop";
			str += " pushtotalk " + settings.readSetting("pushtotalk");
			
			if (loginRecords.isAdmin()) str += " admin true";
			
			if (state.get(State.values.dockstatus) != null) str += " dock "+ state.get(State.values.dockstatus);
			
			
			if (settings.getBoolean(ManualSettings.developer)) str += " developer true";

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
		settings.writeSettings("vset", "vcustom");
		settings.writeSettings("vcustom", str);
		String s = "custom stream set to: " + str;
		if (!state.get(State.values.stream).equals("stop") && !state.getBoolean(State.values.autodocking)) {
			publish(state.get(State.values.stream));
			s += "<br>restarting stream";
		}
		messageplayer(s, null, null);
		Util.log("stream changed to " + str);
	}

	private void streamSettingsSet(String str) {
		Util.debug("streamSettingsSet: "+str, this);
		settings.writeSettings("vset", "v" + str);
		String s = "stream set to: " + str;
		if (!state.get(State.values.stream).equals("stop") && !state.getBoolean(State.values.autodocking)) {
			publish(state.get(State.values.stream));
			s += "<br>restarting stream";
		}
		messageplayer(s, null, null);
		Util.log("stream changed to " + str);
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
//		if (Settings.os.equals("linux")) { 
//			messageplayer("unsupported in linux",null,null);
//			messageGrabber("unsupported in linux", null);
//			return;
//		}

		messageplayer("restarting server application", null, null);
		messageGrabber("restarting server application", null);
		if(commandServer!=null) { commandServer.sendToGroup(TelnetServer.TELNETTAG+" shutdown"); }
		File f;
//		f = new File(System.getenv("RED5_HOME") + "\\restart"); // windows
		f = new File(Settings.redhome + Settings.sep + "restart"); // windows & linux
		try {
			if (!f.exists()) {
				f.createNewFile();
			}
			if (Settings.os.equals("linux")) {
				Runtime.getRuntime().exec(Settings.redhome+Settings.sep+"red5-shutdown.sh");
			}
			else { Runtime.getRuntime().exec("red5-shutdown.bat"); }
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void quit() { 
		messageplayer("server shutting down",null,null);
		if(commandServer!=null) { commandServer.sendToGroup(TelnetServer.TELNETTAG+" shutdown"); }
		try {
			if (Settings.os.equalsIgnoreCase("linux")) {
				Runtime.getRuntime().exec(Settings.redhome+Settings.sep+"red5-shutdown.sh");
			}
			else { Runtime.getRuntime().exec("red5-shutdown.bat"); }
		} catch (Exception e) { e.printStackTrace(); }
	}

//	public void monitor(String str) {
//		// uses nircmd.exe from http://www.nirsoft.net/utils/nircmd.html
////		if (Settings.os.equals("linux")) {
////			// messageplayer("unsupported in linux",null,null);
////			return;
////		}
//		messageplayer("monitor " + str, null, null);
//		str = str.trim();
//		try {
//
//			if (str.equals("on")) {
//				if (Settings.os.equals("linux")) {
//					str = "xset -display :0 dpms force on";
//					Runtime.getRuntime().exec(str);
//					str = "gnome-screensaver-command -d";
//				}
//				else { str = "cmd.exe /c start monitoron.bat"; }
//			} else {
//				if (Settings.os.equals("linux")) {
//					str = "xset -display :0 dpms force off";
//				}
//				else { str = "nircmdc.exe monitor async_off"; }
//			}
//			Runtime.getRuntime().exec(str);
//			
//		} catch (Exception e) { e.printStackTrace(); }
//	}

	@SuppressWarnings("incomplete-switch")
	public void move(final String str) {

		if (str.equals("stop")) {
			if (state.getBoolean(State.values.autodocking))
				docker.autoDock("cancel");

			comport.stopGoing();
			moveMacroCancel();
			message("command received: " + str, "motion", "STOPPED");
			return;
		}
		
		if (state.getBoolean(State.values.autodocking)) {
			messageplayer("command dropped, autodocking", null, null);
			return;
		}
		
		if (str.equals("forward")) {
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
		
		Util.debug("+++ clickSteer(): " + str, this);

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
		Util.log("assumeControl(), " + str);
		messageGrabber(str, null);
		initialstatuscalled = false;
		pendingplayerisnull = true;
		loginRecords.beDriver();
		
		if (settings.getBoolean(ManualSettings.loginnotify)) {
			saySpeech("lawg inn " + state.get(State.values.driver));
		}
	}

	/** */
	private void beAPassenger(String user) {
		String stream = state.get(State.values.stream);
		pendingplayerisnull = true;
		String str = user + " added as passenger";
		messageplayer(str, null, null);
		Util.log(str);
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
		
		if (settings.getBoolean(ManualSettings.loginnotify)) {
			saySpeech("passenger lawg inn " + user);
		}
	}

	private void playerBroadCast(String str) {
		if (player instanceof IServiceCapableConnection) {
			IServiceCapableConnection sc = (IServiceCapableConnection) player;
			if (!str.equals("off")) {
				String vals[] = (settings.readSetting("vself")).split("_");
				int width = Integer.parseInt(vals[0]);
				int height = Integer.parseInt(vals[1]);
				int fps = Integer.parseInt(vals[2]);
				int quality = Integer.parseInt(vals[3]);
				boolean pushtotalk = settings.getBoolean(GUISettings.pushtotalk);
				sc.invoke("publish", new Object[] { str, width, height, fps, quality, pushtotalk });
				new Thread(new Runnable() {
					public void run() {
						try {
							Thread.sleep(STREAM_CONNECT_DELAY);
						} catch (Exception e) {
							e.printStackTrace();
						}
						grabberPlayPlayer(1);
						state.set(State.values.driverstream, true);
					}
				}).start();
//				if (str.equals("camera") || str.equals("camandmic")) {
//					monitor("on");
//					Util.debug("monitor on", this);
//				}
				Util.log("OCULUS: player broadcast start", this);
			} else {
				sc.invoke("publish", new Object[] { "stop", null, null, null,null,null });
				grabberPlayPlayer(0);
				state.set(State.values.driverstream, false);
				Util.log("OCULUS: player broadcast stop",this);
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
		Util.log("chat: " + str);
		messageGrabber("<CHAT>" + str, null);
		if(str!=null) if (commandServer != null) { 
			str = str.replaceAll("</?i>", "");
			commandServer.sendToGroup(TelnetServer.TELNETTAG+" chat from "+ str);
		}
	}

	private void showlog(String str) {
		int lines = 100; //default	
		if (!str.equals("")) { lines = Integer.parseInt(str); }
		String header = "latest "+ Integer.toString(lines)  +" line(s) from "+Settings.stdout+" :<br>";
		sendplayerfunction("showserverlog", header + Util.tail(lines));
	}

	private void saveAndLaunch(String str) {
		Util.log("saveandlaunch: " + str);
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
			// skipsetup
			if (skipsetup != null) {
				settings.writeSettings("skipsetup", skipsetup);
			}

			message = "launch server";
			if (restartrequired) {
				message = "shutdown";
				// admin = true;
				restart();
			}
		}
		messageGrabber(message, null);
	}

	/** */
	private void populateSettings() {
		settings.writeSettings("skipsetup", "no");
		String result = "populatevalues ";

		// username
		String str = settings.readSetting("user0");
		if(str != null) result += "username " + str + " ";

		// commport
		if(ArduinoPrime.motorsReady()) result += "comport " + state.get(State.values.motorport) + " ";
		else result += "comport nil ";
		
		// TODO: 
		// lights
		
		// if(ArduinoPrime.motorsAvailable()) result += "lightport " + settings.readSetting(ManualSettings.lightport) + " ";
		result += "lightport nil ";

		
		// law and wan
		String lan = state.get(State.values.localaddress);
		if(lan == null) result += "lanaddress error ";
		else result += "lanaddress " + lan + " ";

		String wan = state.get(State.values.externaladdress);
		if(wan == null) result += "wanaddress error ";
		else result += "wanaddress " + wan + " ";

		// http port
		result += "httpport " + settings.readRed5Setting("http.port") + " ";

		// rtmp port
		result += "rtmpport " + settings.readRed5Setting("rtmp.port") + " ";

		messageGrabber(result, null);
		
		Util.log("___populate settings: " + result, this);
		
	}

	public void softwareUpdate(String str) {

		if (str.equals("check")) {
			messageplayer("checking for new software...", null, null);
			Updater updater = new Updater();
			int currver = updater.getCurrentVersion();
			String fileurl = updater.checkForUpdateFile();
			int newver = updater.versionNum(fileurl);
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
					Util.log("downloading url: " + fileurl);
					Downloader dl = new Downloader();
					if (dl.FileDownload(fileurl, "update.zip", "download")) {
						messageplayer("update download complete, unzipping...",
								null, null);

						// this is a blocking call
						if (dl.unzipFolder("download"+Settings.sep+"update.zip", "webapps"))
							messageplayer("done.", "softwareupdate",
									"downloadcomplete");

						// not needed now is unpacked
						dl.deleteDir(new File(Settings.redhome+Settings.sep+"download"));

					} else {
						messageplayer("update download failed", null, null);
					}
				}
			}).start();
		}
		if (str.equals("versiononly")) {
			int currver = new Updater().getCurrentVersion();
			String msg = "";
			if (currver == -1)
				msg = "version unknown";
			else
				msg = "version: v." + currver;
			messageplayer(msg, null, null);
		}
	}

	public void factoryReset() {

		final String backup = "conf"+Settings.sep+"backup_oculus_settings.txt";

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
		// Util.debug("threshold vals: "+videoThreshold+","+audioThreshold, this);
		state.set(State.values.streamActivityThreshold.name(), str);
		
		if (videoThreshold != 0 || audioThreshold != 0) {
			if (state.get(State.values.videosoundmode.name()).equals("high")) {
				setGrabberVideoSoundMode("low"); // videosoundmode needs to be low to for activity threshold to work
				if (stream != null) {
					if (!stream.equals("stop")) { // if stream already running,
						publish(stream); // restart, in low mode
					}
				}
			}
			
			if (stream != null) { 
				if (stream.equals("stop")) {
					if (audioThreshold == 0 && videoThreshold > 0) { publish("camera"); }
					else if (audioThreshold > 0 && videoThreshold == 0) { publish("mic"); }
					else { publish("camandmic"); }
				}
			}
			state.set(State.values.streamActivityThresholdEnabled.name(), System.currentTimeMillis());
		}
		else { 
			state.delete(State.values.streamActivityThresholdEnabled);
			state.delete(State.values.streamActivityThreshold);
		}

		IServiceCapableConnection sc = (IServiceCapableConnection) grabber;
		sc.invoke("setActivityThreshold", new Object[] { videoThreshold, audioThreshold });
		messageplayer("stream activity set to: "+str, null, null);

	}
	
	private void streamActivityDetected(String str) {
		if (System.currentTimeMillis() > state.getLong(State.values.streamActivityThresholdEnabled) + 5000.0) { 
			messageplayer("streamactivity: "+str, "streamactivity", str);
			setStreamActivityThreshold("0 0"); // disable
		}
	}

//	@Override
//	public void updated(String key) {
		
		// Util.debug("updated(): " + key, this);
		
//		if(key.equals(State.values.cameratilt.name())){
//			if(state.getInteger(State.values.cameratilt) > 150 //(ArduinoPrime.CAM_MAX /2) 
//					&! state.getBoolean(State.values.controlsinverted)){
//				if (player!=null) {
//					IServiceCapableConnection sc = (IServiceCapableConnection) player;
//					sc.invoke("flipVideo", new Object[] { true });
//					
//					messageplayer("inverting video and controls", null,null);
//				}
//				state.set(State.values.controlsinverted, true);
//			}
//			if(state.getInteger(State.values.cameratilt) < (ArduinoPrime.CAM_MAX /2) && 
//					state.getBoolean(State.values.controlsinverted)){
//				if (player!=null) {
//					IServiceCapableConnection sc = (IServiceCapableConnection) player;
//					sc.invoke("flipVideo", new Object[] { false });
//					
//					messageplayer("un-inverting video and controls", null,null);
//				}
//				state.delete(State.values.controlsinverted);
//			}
//		}
		
//	}
}


