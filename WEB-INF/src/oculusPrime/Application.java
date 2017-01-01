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

import developer.*;
import org.jasypt.util.password.ConfigurablePasswordEncryptor;
import org.opencv.core.Core;
import org.red5.server.adapter.MultiThreadedApplicationAdapter;
import org.red5.server.api.IConnection;
import org.red5.server.api.Red5;
import org.red5.server.api.service.IServiceCapableConnection;
import org.red5.server.stream.ClientBroadcastStream;

import developer.Navigation;
import developer.NavigationLog;
import developer.NavigationUtilities;
import developer.Ros;
import developer.depth.Mapper;
import developer.image.OpenCVMotionDetect;
import developer.image.OpenCVObjectDetect;
import developer.image.OpenCVUtils;
import oculusPrime.State.values;
import oculusPrime.commport.ArduinoPower;
import oculusPrime.commport.ArduinoPrime;
import oculusPrime.commport.PowerLogger;

/** red5 application */
public class Application extends MultiThreadedApplicationAdapter {

	public enum streamstate { stop, camera, camandmic, mic }
	public enum camquality { low, med, high, custom }
	public enum driverstreamstate { stop, mic, pending, disabled }
	public static final String VIDEOSOUNDMODELOW = "low";
	public static final String VIDEOSOUNDMODEHIGH = "high";
	public static final int STREAM_CONNECT_DELAY = 2000;
	private static final int GRABBERRELOADTIMEOUT = 5000;
	public static final int GRABBERRESPAWN = 8000;
	public static final String ARM = "arm";
	public static final String LOCALHOST = "127.0.0.1";

	private ConfigurablePasswordEncryptor passwordEncryptor = new ConfigurablePasswordEncryptor();
	protected boolean initialstatuscalled = false;
	private boolean pendingplayerisnull = true;
	public IConnection grabber = null;   // flash client on robot, camera capture (optional)
	private IConnection player = null;   // client, typically remote flash plugin or air app
	private String authtoken = null;
	private String salt = null;
	
	private Settings settings = Settings.getReference();
	private BanList banlist = BanList.getRefrence();
	private State state = State.getReference();
	protected LoginRecords loginRecords = null;
	private IConnection pendingplayer = null;
	protected SystemWatchdog watchdog = null;
	private AutoDock docker = null;
	public Video video = null;

	public ArduinoPrime comport = null;
	public ArduinoPower powerport = null;
	public TelnetServer commandServer = null;
	
	public static developer.depth.OpenNIRead openNIRead = null;
	public static developer.depth.ScanUtils scanUtils = null;
	
	public static byte[] framegrabimg  = null;
	public static BufferedImage processedImage = null;
	public static BufferedImage videoOverlayImage = null;

	// for fine debugging 
	private static final boolean DEBUG_PLAYER = false;
	
	private Red5Client red5client = null;
	public IConnection relayclient = null;
	public Network network = null;

	public Application() {
		super();
		state.set(values.osarch, System.getProperty("os.arch"));
		Util.log("\n==============Oculus Prime Java Start Arch:"+state.get(values.osarch)+"===============", this);
		PowerLogger.append("\n==============Oculus Prime Java Start===============", this);

		passwordEncryptor.setAlgorithm("SHA-1");
		passwordEncryptor.setPlainDigest(true);
		loginRecords = new LoginRecords(this);
		FrameGrabHTTP.setApp(this);
		initialize();		
		
		// do last always 
		if(settings.getBoolean(ManualSettings.developer.name())) DashboardServlet.setApp(this);
	}

	@Override
	public boolean appConnect(IConnection connection, Object[] params) {

		authtoken = null;

		// TODO: testing avconv/ffmpeg stream accept all non-auth LAN connections
//		if (banlist.knownAddress(connection.getRemoteAddress()) && params.length==0) {
//			Util.log("localhost/LAN/known netstream connect, no params", this);
//			return true;
//		}

		// always accept local avconv/ffmpeg
		if (params.length==0 && connection.getRemoteAddress().equals("127.0.0.1") ) {
			grabber = Red5.getConnectionLocal();
			return true;
		}

		// always accept relayclient avconv/ffmpeg
		if (params.length==0 && state.exists(values.relayclient)) {
			if (connection.getRemoteAddress().equals(state.get(values.relayclient)) ) {
				grabber = Red5.getConnectionLocal();

				if (settings.getBoolean(ManualSettings.useflash)) {
					driverCallServer(PlayerCommands.messageclients, "relay server in use, setting &quot;useflash&quot; set to false");
					Util.log("setting useflash false", this);
					settings.writeSettings(ManualSettings.useflash, Settings.FALSE);
				}

				return true;
			}
		}

		if (params.length==0) return false;

		String logininfo[] = ((String) params[0]).split(" ");

		// always accept local grabber (flash)
		if ((connection.getRemoteAddress()).equals("127.0.0.1") && logininfo[0].equals("")) return true;

		// disallow normal connection if relay server active
		if (state.exists(values.relayserver)) return false;

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
	
					if (!state.get(State.values.driverstream).equals(driverstreamstate.stop.toString())
							&& !state.get(values.driverstream).equals(driverstreamstate.disabled.toString())) {
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

			if (state.exists(values.relayclient)) {
				IServiceCapableConnection t = (IServiceCapableConnection) relayclient;
				t.invoke("playerDisconnect");
			}
		}
		
		if (connection.equals(grabber)) {
			grabber = null;

			if (!settings.getBoolean(ManualSettings.useflash))  return;

			// flash only
			// wait a bit, see if still no grabber, THEN reload
			new Thread(new Runnable() {
				public void run() {
					try {
						Thread.sleep(GRABBERRESPAWN);
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

		if (connection.equals(relayclient)) {
			relayclient = null;
			state.delete(values.relayclient);
			Util.log("relay client disconnected", this);
			if (state.exists(values.driver)) //messageplayer("relay client disconnected", "connection", "connected");
				driverCallServer(PlayerCommands.driverexit, null);
			if (!state.get(values.stream).equals(streamstate.stop.toString()))
				driverCallServer(PlayerCommands.streammode, streamstate.stop.toString());
			return;
		}
		
		state.delete(State.values.pendinguserconnected);
		//TODO: extend IConnection class, associate loginRecord  (to get passenger info)
		// currently no username info when passenger disconnects
	}

	// called by flash
	public void grabbersignin(String mode) {
		if (mode.equals("init")) {
			state.delete(State.values.stream);
		} else {
//			state.set(State.values.stream, Application.streamstate.stop.toString());
			driverCallServer(PlayerCommands.state, State.values.stream.toString() + " " +
					Application.streamstate.stop.toString());
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

	public void killGrabber() {
		if (settings.getBoolean(ManualSettings.useflash)) Util.systemCall("pkill chrome");    // TODO: use PID
	}
 
	/** */
	public void initialize() {
		settings.writeFile();
		salt = settings.readSetting("salt");

		if (settings.readSetting("user0") == null) {
			driverCallServer(PlayerCommands.new_user_add, "oculus robot");
//			String p = "oculus" + salt + "robot"; // default
//			String encryptedPassword = passwordEncryptor.encryptPassword(p);
//			settings.newSetting("user0", "oculus");
//			settings.newSetting("pass0", encryptedPassword);
		}

		comport = new ArduinoPrime(this);   // note: blocking
		powerport = new ArduinoPower(this); // note: blocking

		state.set(State.values.httpport, settings.readRed5Setting("http.port"));
		initialstatuscalled = false;
		pendingplayerisnull = true;

		if (!settings.readSetting(GUISettings.telnetport).equals(Settings.DISABLED.toString()))
			commandServer = new TelnetServer(this);

		if (settings.getBoolean(ManualSettings.developer.name())) {
			openNIRead = new developer.depth.OpenNIRead();
			scanUtils = new developer.depth.ScanUtils();
		}

		if (!state.get(values.osarch).equals(ARM)) {
			try {
				System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
			} catch (UnsatisfiedLinkError e) {
				Util.log("opencv native lib not available", this);
			}
		}

		Util.setSystemVolume(settings.getInteger(GUISettings.volume));
		state.set(State.values.volume, settings.getInteger(GUISettings.volume));

		if(state.get(values.osarch).equals(ARM)) settings.writeSettings(ManualSettings.useflash, Settings.FALSE);
		if( ! settings.getBoolean(ManualSettings.useflash)) state.set(values.driverstream, driverstreamstate.disabled.toString());
		else state.set(State.values.driverstream, driverstreamstate.stop.toString());

		// use relay server if set
		if( !settings.readSetting(GUISettings.relayserver).equals(Settings.DISABLED)) {
			red5client = new Red5Client(this); // connects to remote server
			red5client.connectToRelay();
		}

		if(state.get(values.osarch).equals(ARM)) settings.writeSettings(ManualSettings.useflash, Settings.FALSE);
		
		if( ! settings.getBoolean(ManualSettings.useflash)) state.set(values.driverstream, driverstreamstate.disabled.toString());
		else state.set(State.values.driverstream, driverstreamstate.stop.toString());
		if(state.get(values.osarch).equals(ARM)) settings.writeSettings(ManualSettings.useflash, Settings.FALSE);
		
		grabberInitialize();
		state.set(State.values.lastusercommand, System.currentTimeMillis()); // must be before watchdog
		docker = new AutoDock(this, comport, powerport);
		network = new Network(this);
		watchdog = new SystemWatchdog(this);
		if(settings.getBoolean(GUISettings.navigation)) new developer.Navigation(this);
		
		if(settings.getBoolean(ManualSettings.developer.name())){
			
			// extra logging info 
			Util.log("java restarts since last boot: " + settings.getInteger(ManualSettings.restarted), this);
			Util.logLinuxRelease(); 
			
			// developer debugging info, warning to reboot after many restarts
			if(settings.getInteger(ManualSettings.restarted) > 5)
				NavigationLog.warning("restarts since last boot: " + settings.getInteger(ManualSettings.restarted));
			
			// try re-docking, then start routes again 
			// if(state.equals(values.dockstatus, AutoDock.UNDOCKED)) redockWaitRunRoute();
		}
		
		// start active route or try to dock
		if(state.equals(values.dockstatus, AutoDock.DOCKED)) Navigation.runActiveRoute();
		else watchdog.redock(SystemWatchdog.NOFORWARD);
		Util.log("java application initialized", this);		
	}
	
	/* useful ??
	private void redockWaitRunRoute(){
		new Thread(new Runnable() { public void run() {
			try {
				
				Util.delay(5000); // system settle 	
				
				// Util.debug(".... booted undocked, trying redock, waiting....", this);		
				watchdog.redock(SystemWatchdog.NOFORWARD); // re-dock and block 
				if(state.block(values.dockstatus, AutoDock.DOCKED,(int)Util.TWO_MINUTES)){
					if(( state.equals(values.dockstatus, AutoDock.DOCKED))){
								
						// Util.debug(".... booted undocked, trying redock, done waiting....", this);		
	
						Util.delay(15000); // system settle 	
						
						// Util.debug(".... redockWaitRunRoute(): run active route: " + NavigationUtilities.getActiveRoute());
						Navigation.runActiveRoute();	
						
					}				
				} else {
					
					// this is bad 
					NavigationLog.newItem(NavigationLog.ALERTSTATUS, "redockWaitRunRoute(): failed to dock");
					new SendMail("Oculus failed to dock", "redockWaitRunRoute(): called but failed. booted un-docked andd ccan't find dock by spining");
					//state.toString().replaceAll("<br>", "\n"));
					
				}
			} catch (Exception e){Util.printError(e);}
		} }).start();
	}
*/

	// called by remote relay client
	public void setRelayClient() {
		IConnection c = Red5.getConnectionLocal();
		if (c instanceof IServiceCapableConnection) {
			relayclient = c;
			state.set(values.relayclient, c.getRemoteAddress());
			Util.log("relayclient connected from: " + state.get(values.relayclient), this);

			if (authtoken != null) {
				IServiceCapableConnection sc = (IServiceCapableConnection) relayclient;
				sc.invoke("relayCallClient", new Object[] { "writesetting",
						GUISettings.relayserverauth.toString()+" "+authtoken });
			}

			if (state.exists(values.driver)) {
				player.close();
				player = null;
				loginRecords.signoutDriver();
				driverCallServer(PlayerCommands.publish, streamstate.stop.toString());
			}

		}
	}

	// called by remote relayclient
	public void relayPing() {
		Util.debug("ping from relayclient", this); // TODO: testing
		if (relayclient == null) {
			Util.log("error,relayclient null", this); // TODO: testing
			return;
		}
		IServiceCapableConnection sc = (IServiceCapableConnection) relayclient;
		sc.invoke("relayPong", new Object[] { });
	}

	// called by remote Red5Client.sendToRelay()
	public void fromRelayClient(Object[] params) {
		if ( !Red5.getConnectionLocal().equals(relayclient)) return;
		String[] s= new String[params.length-1];

		switch (params[0].toString()) {
			case "messageplayer":
				for (int i=1; i<params.length; i++) if (params[i]!=null) s[i-1]=params[i].toString();
				messageplayer(s[0],s[1],s[2]);
				break;
			case "sendplayerfunction":
				for (int i=1; i<params.length; i++) if (params[i]!=null) s[i-1]=params[i].toString();
				sendplayerfunction(s[0], s[1]);
				break;
			case "grabberSetStream":
				grabberSetStream(params[1].toString());
		}
	}

	private void grabberInitialize() {

//		String host = LOCALHOST;
//		if (!settings.readSetting(ManualSettings.relayserver).equals(Settings.DISABLED))
//			host = settings.readSetting(ManualSettings.relayserver);

		video = new Video(this);

		// non flash, no gui
		if (!settings.getBoolean(ManualSettings.useflash))   video.initAvconv();
		else {
//			if (host.equals(LOCALHOST)) grabber_launch("");
//			else grabber_launch("?host="+host);

			grabber_launch("");
		}

	}

	public void grabber_launch(final String str) {
		new Thread(new Runnable() {
			public void run() {
				try {

					// stream = "stop";
					String address = "127.0.0.1:" + state.get(State.values.httpport);
					Runtime.getRuntime().exec("google-chrome " + address + "/oculusPrime/server.html"+str);

				} catch (Exception e) {
					Util.printError(e);
				}
			}
		}).start();
	}

	/**
	 * called by remote flash
	 * */
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
			String conn = "connected";
			if (state.exists(values.relayclient)) conn="relay";
			String str = "connection "+conn+" user " + state.get(values.driver);
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

			if (state.exists(values.relayclient)) {
				IServiceCapableConnection t = (IServiceCapableConnection) relayclient;
				t.invoke("playerSignIn", new Object[] { state.get(values.driver)});
			}

		}
	}

	public void driverCallServer(PlayerCommands fn, String str) {
		playerCallServer(fn, str, true);
	}

	/** called by remote flash */
	public void playerCallServer(String fn, String str) {
		
		if (fn == null) return;
		if (fn.equals("")) return;

		PlayerCommands cmd = null;
		try {
			cmd = PlayerCommands.valueOf(fn);
		} catch (Exception e) {
			Util.log("playerCallServer() command not found:" + fn, this);
			messageplayer("error: unknown command, "+fn,null,null);
			return;
		}
		if (cmd != null) playerCallServer(cmd, str, false);	
	}
	
	public void playerCallServer(PlayerCommands fn, String str) {
		playerCallServer(fn, str, false);
	}

	@SuppressWarnings("incomplete-switch")
	public void playerCallServer(PlayerCommands fn, String str, boolean passengerOverride) {

		if (PlayerCommands.requiresAdmin(fn) && !passengerOverride) {
			if ( ! loginRecords.isAdmin()){ 
				Util.debug("playerCallServer(), must be an admin to do: " + fn.name() + " curent driver: " + state.get(State.values.driver), this);
				return;
			}
		}
			
		// skip telnet ping broadcast
		if(fn != PlayerCommands.statuscheck) state.set(State.values.lastusercommand, System.currentTimeMillis());

		// if acting as relay server, forward commands
		if (state.exists(values.relayclient) && !PlayerCommands.nonRelayCommands(fn)) {
			IServiceCapableConnection sc = (IServiceCapableConnection) relayclient;
			sc.invoke("relayCallClient", new Object[] { fn, str });
			return;
		}

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
		
		/*
		if( ! fn.name().equals(PlayerCommands.statuscheck ) &&
				 ! fn.name().equals(PlayerCommands.arcmove )	) 
			Util.log("---playerCallServer: " + fn.name() + " argument: " + str, this);
		*/
		
		switch (fn) {
	
		case move: {

			if(settings.getBoolean(GUISettings.navigation)) Navigation.navdockactive = false;

			if(state.exists(State.values.navigationroute) && !passengerOverride && 
					str.equals(ArduinoPrime.direction.stop.toString())){
				
				messageplayer("navigation route "+state.get(State.values.navigationroute)+" cancelled by stop", null, null);
				NavigationLog.newItem("Route cancelled by user");
				Navigation.cancelAllRoutes();
			
			}  else if (state.exists(State.values.roscurrentgoal) && !passengerOverride && str.equals(ArduinoPrime.direction.stop.toString())) {
				Navigation.goalCancel();
				messageplayer("navigation goal cancelled by stop", null, null);
			} 

			if( ! passengerOverride && watchdog.redocking) watchdog.redocking = false;
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
		case streamsettingscustom: streamSettingsCustom(str);break;
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
		case record: // record [true | false] optionalfilename
			if(str == null) break;
			if(str.startsWith("true ")) video.record("true", str.substring(4).trim().replace(" ", "_")); 
			if(str.equals("true") || str.equals("false")) video.record(str); 
			break;  
		case autodockcalibrate: docker.autoDock("calibrate " + str); break;
		case redock: watchdog.redock(str); break;
		case restart: restart(); break;
		case powershutdown: powerport.shutdown(); break;
		case reboot: reboot(); break;
		case systemshutdown: powerdown(); break;
		case softwareupdate: softwareUpdate(str); break;
//		case muterovmiconmovetoggle: muteROVMicOnMoveToggle(); break;
		case quitserver: shutdownApplication(); break;
		case setstreamactivitythreshold: setStreamActivityThreshold(str); break;
		case email: new SendMail(str, this); break;
		case uptime: messageplayer(state.getUpTime() + " ms", null, null); break;
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
			if (str.equals(AutoDock.HIGHRES)) docker.lowres = false; // ?
			docker.dockGrab(AutoDock.dockgrabmodes.test, 0, 0);
			docker.lowres = true; // ?
			break;
		case rssadd: RssFeed feed = new RssFeed(); feed.newItem(str); break;
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

		case nudge: nudge(str); break;
		
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
			Util.setSystemVolume(Integer.parseInt(str));
			messageplayer("ROV volume set to "+str+"%", null, null); 
			state.set(State.values.volume, str);
			break;		
			
		case opennisensor:
			if(str.equals("on")) { 
				if (openNIRead.startDepthCam()) {
//					if (!state.getBoolean(State.values.odometry)) comport.odometryStart();
				 }
				else
						messageplayer("roslaunch already running, abort", null, null);
			}
			else { 
				openNIRead.stopDepthCam(); 
//				if (state.getBoolean(State.values.odometry)) comport.odometryStop();
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

		case malgcommand:
				messageplayer("malgcommand: "+str, null, null);
				comport.malgcommand(str);
				break;

		case erroracknowledged:
			if (str.equals("true")) {
				Util.log("power error acknowledged",this);
				if (watchdog.powererrorwarningonly) { 
					powerport.clearWarningErrors(); 
					watchdog.lastpowererrornotify = null; 
				}
			}
			else { 
				Util.log("power error purposefully dismissed",this);
			}
			break;
			
		case block:
			banlist.addBlockedFile(str);
			break;
			
		case unblock:
			banlist.removeblockedFile(str);
			break;

		case roslaunch:
			if (Ros.launch(str)) messageplayer("roslaunch "+str+".launch", null, null);
			else messageplayer("roslaunch already running", null, null);
			break;
		
		case savewaypoints:
			Ros.savewaypoints(str);
			messageplayer("waypoints saved", null, null);
			break;
			
		case gotowaypoint:
			Navigation.gotoWaypoint(str);
			break;
		
		case startnav:
			Navigation.startNavigation(); 
			break;
		
		case stopnav:
			Navigation.stopNavigation();
			break;
		
		case gotodock:
			Navigation.dock(); 
			break;
			
		case saveroute: 
			NavigationUtilities.saveRoute(str);
			messageplayer("route saved", null, null);			
			break;
		
		case routedata:
			String r = "<routename>" + str + "</routename>" 
						+ "<count>" + NavigationUtilities.getRouteCountString(str) + "</count>"
			 			+ "<fail>" + NavigationUtilities.getRouteFailsString(str) + "</fail>"
			 			+ "<meters>" + NavigationUtilities.getRouteDistanceEstimate(str) + "</meters>"
			 			+ "<second>" + NavigationUtilities.getRouteTimeEstimate(str) + "</seconds>";
			commandServer.sendToGroup("route: " + str + " " + r);
			break;
			
		case resetroutedata: 
			Util.log("User reset route stats for: "+str, this);
			messageplayer("User reset route status for: "+str, null, null);
			NavigationUtilities.setRouteFails(str, 0);
			NavigationUtilities.setRouteCount(str, 0);
			NavigationUtilities.setRouteDistanceEstimate(str, 0);
			NavigationUtilities.setRouteTimeEstimate(str, 0);
			break;
			
		case runroute:
			if(str == null) return;
			if(str.equals("")) return;
			if( ! NavigationUtilities.routeExists(str)){
				Util.log("runroute(): route does not exist: " + str);
				return;
			}
			if( ! state.equals(values.dockstatus, AutoDock.DOCKED)){
				Util.log("playerCallServer(): must be docked to start new route, skipped", this);
				messageplayer("must be docked to start new route, skipped", null, null);
				return;					
			}
			// TODO ?? 
			NavigationUtilities.setActiveRoute(str);	
			Navigation.runRoute(str);
			break;

		case cancelroute:
			Navigation.cancelAllRoutes(); // state changes 
			NavigationUtilities.deactivateAllRoutes(); // edit file 
			break;

		case startmapping: Navigation.startMapping(); break;
		case savemap: Navigation.saveMap(); break;
		case clearmap: Mapper.clearMap(); break;
		case motiondetect: new OpenCVMotionDetect(this).motionDetectGo(); break;
		case motiondetectcancel: state.delete(State.values.motiondetect); break;
		case motiondetectstream: new OpenCVMotionDetect(this).motionDetectStream(); break;
		case sounddetect: video.sounddetect(str); break;
		case objectdetect: new OpenCVObjectDetect(this).detectGo(str); break;
		case objectdetectcancel: state.delete(values.objectdetect); break;
		case objectdetectstream: new OpenCVObjectDetect(this).detectStream(str); break;

		// case framegrabtofile: messageplayer(FrameGrabHTTP.saveToFile(str), null, null); break;
		case framegrabtofile: // allow extra name to be added 
			final String c = str.trim(); // split(" ");
			new Thread(new Runnable(){	
				public void run(){	
					
					String url = null; 
					if(c.length() > 1) { 
						url = FrameGrabHTTP.saveToFile(c); // ?mode=processedImgJPG 
						Util.log("framegrabtofile(mode): "+c, this);
					}
					if(c.length() == 0){
						FrameGrabHTTP.saveToFile(null); // default filename
						Util.log("framegrabtofile(default): ", this);
					}
					
					// try again? FrameGrabHTTP.saveToFile(null);
					if(url == null) Util.log("framegrabtofile(): ERROR downloading", this);
					else messageplayer("framegrab "+url, null, null); 
				}
			}).start();
			break;
			
			
		case log: Util.log("log: "+str, this); break;
		case settings: messageplayer(settings.toString(), null, null); break;
		
		case cpu: 
			String cpu = String.valueOf(Util.getCPU());
			if(cpu != null) state.set(values.cpu, cpu);
			commandServer.sendToGroup("cpu " + cpu);
			break;
		
		case waitforcpu: watchdog.waitForCpuThread();  break;

		// dev tool only
		case test:
			try {

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
		
		case deletelogs:
			if( !state.equals(values.dockstatus, AutoDock.DOCKED)) {
				Util.log("archiving busy, must be docked, skipping.. ", null);
				break;
			}
			Util.deleteLogFiles();
			break;
	
		/** testing block on state change */
		case wait:
			Util.log("wait on: " + str, this);

			final String input[] = str.split(" ");
			new Thread(new Runnable() { public void run(){
				
				if(state.block(input[0], (int) Util.TEN_MINUTES)){ 
					commandServer.sendToGroup("... wait: " + input[0] + " == " + input[1]);
				} else { 
					commandServer.sendToGroup("... wait: time out: " + input[0]);
				}

			}}).start();
			break;
	
			
		case archivelogs: 
//			if( !state.equals(values.dockstatus, AutoDock.DOCKED)) {
//				Util.log("archiving busy, must be docked, skipping.. ", null);
//				break;
//			}
			Util.archiveLogs();
			break;

		case streammode: // TODO: testing ffmpeg/avconv streaming
			grabberSetStream(str);
			break;

		case calibraterotation:
			new Calibrate(this).calibrateRotation();
			break;

		case relayconnect:
			if (red5client == null) red5client = new Red5Client(this);
			if (!str.equals("")) { red5client.connectToRelay(str); break; }
			red5client.connectToRelay();
			// TODO: stop any running streams
			break;

		case relaydisable:
			driverCallServer(PlayerCommands.writesetting, GUISettings.relayserver.toString()+" "+Settings.DISABLED);
			driverCallServer(PlayerCommands.writesetting, GUISettings.relayserverauth.toString()+" "+Settings.DISABLED);
			// break omitted on purpose

		case relaydisconnect:
			driverCallServer(PlayerCommands.publish, Application.streamstate.stop.toString()); // TODO: server doesn't get stream stop
			if (state.exists(values.relayserver))
				red5client.relayDisconnect();
			else if (state.exists(values.relayclient)) {
				IServiceCapableConnection rc = (IServiceCapableConnection) relayclient;
				rc.invoke("disconnect");
				state.delete(values.relayclient);
				driverCallServer(PlayerCommands.publish, Application.streamstate.stop.toString()); // TODO: server doesn't get stream stop
			}
			break;

		case networksettings:
			network.getNetworkSettings();
			break;

		case networkconnect:
//			if (state.exists(values.relayserver) || state.exists(values.relayclient))
//				driverCallServer(PlayerCommands.relaydisconnect, null);
			network.connectNetwork(str);
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
//			case autodock:
//				docker.autoDock(str);
//				break;
			case restart:
				restart();
				break;
			case shutdown:
				shutdownApplication();
				break;
			case streamactivitydetected:
				streamActivityDetected(str);
				break;
		}
	}

	/**
	 * set state and message all connected clients with stream status
	 * @param str
	 */
	private void grabberSetStream(String str) {
		state.set(State.values.stream, str);
		if (!settings.getBoolean(ManualSettings.useflash) && str.equals(streamstate.camandmic.toString()))
			str = str+"_2";
		final String stream = str;

		messageGrabber("streaming " + stream, "stream " + stream);
		Util.log("streaming " + stream, this);
		new Thread(new Runnable() {
			public void run() {
				try {
					// notify all passengers and driver
					Thread.sleep(STREAM_CONNECT_DELAY);
//					if (stream.equals(streamstate.camandmic)); Thread.sleep(STREAM_CONNECT_DELAY*2); // longer delay required doesn't help
					Collection<Set<IConnection>> concollection = getConnections();
					for (Set<IConnection> cc : concollection) {
						for (IConnection con : cc) {
							if (con instanceof IServiceCapableConnection
									&& con != grabber
									&& !(con == pendingplayer && !pendingplayerisnull)) {
								IServiceCapableConnection n = (IServiceCapableConnection) con; // all CLIENTS
								n.invoke("message", new Object[] { "streaming " + stream, "green", "stream", stream });
								Util.debug("message all players: streaming " + stream +" stream " +stream,this);
							}
						}
					}

					// set stream on relayserver if necessary
					if (state.exists(values.relayserver)) {
						Util.delay(1000); // allow extra time for avconv to connect to remote server
						red5client.sendToRelay("grabberSetStream", new Object[]{state.get(values.stream)});
					}

				} catch (Exception e) {
					Util.printError(e);
				}
			}
		}).start();
	}

	private void setGrabberVideoSoundMode(String str) {

		if (!settings.getBoolean(ManualSettings.useflash)) return;
		
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
		sc.invoke("videoSoundMode", new Object[]{str});
		state.set(State.values.videosoundmode, str);
		Util.log("grabber video sound mode = " + str, this);
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

		if (state.get(State.values.record) == null)
			state.set(State.values.record, Application.streamstate.stop.toString());

		// if recording and mode changing, kill recording
		if (state.exists(values.stream)) {
			if (!mode.equals(streamstate.valueOf(state.get(values.stream))) && !state.get(values.record).equals(streamstate.stop.toString()))
				video.record(Settings.FALSE);
		}

		ArduinoPrime.checkIfInverted();

		String current = settings.readSetting(GUISettings.vset);
		String vals[] = (settings.readSetting(current)).split("_");
		int width = Integer.parseInt(vals[0]);
		int height = Integer.parseInt(vals[1]);
		int fps = Integer.parseInt(vals[2]);
		int quality = Integer.parseInt(vals[3]);

		if (!settings.getBoolean(ManualSettings.useflash)) {
			video.publish(mode, width, height, fps);
			return;
		}

		// flash
		try {
			// commands: camandmic camera mic stop

			long timeout = System.currentTimeMillis() + GRABBERRELOADTIMEOUT;
			while (!(grabber instanceof IServiceCapableConnection) && System.currentTimeMillis() < timeout ) { Util.delay(10); }
			if (!(grabber instanceof IServiceCapableConnection))
				Util.log("publish() error grabber reload timeout", this);
			IServiceCapableConnection sc = (IServiceCapableConnection) grabber;
			sc.invoke("publish", new Object[] { mode.toString(), width, height, fps, quality });
			messageplayer("command received: publish " + mode.toString(), null, null);
			Util.log("publish: " + mode.toString(), this);

		} catch (NumberFormatException e) {
			Util.log("publish() error " + e.getMessage(),this);
			Util.printError(e);
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

	public boolean frameGrab() {
		return frameGrab("");
	}

	/**  */
	public boolean frameGrab(String res) {

		 if(state.getBoolean(State.values.framegrabbusy.name()) || 
				 !(state.get(State.values.stream).equals(Application.streamstate.camera.toString()) ||
						 state.get(State.values.stream).equals(Application.streamstate.camandmic.toString()))) {
			 messageplayer("stream unavailable or framegrab busy", null, null);
			 return false;
		 }

		if (settings.getBoolean(ManualSettings.useflash)) {
			if (grabber instanceof IServiceCapableConnection) {
				IServiceCapableConnection sc = (IServiceCapableConnection) grabber;
				if (res.equals(AutoDock.LOWRES)) sc.invoke("framegrabMedium", new Object[]{});
				else sc.invoke("framegrab", new Object[]{});
				state.set(State.values.framegrabbusy.name(), true);
			}
		}
		else video.framegrab(res);

//		Util.debug("framegrab start at: "+System.currentTimeMillis(), this);
		return true;
	}

	/**
	 * for compatibility with legacy grabber swfs
	 */
//	public void frameGrabbed() {
//		String current = settings.readSetting(GUISettings.vset);
//		String vals[] = (settings.readSetting(current)).split("_");
//		int width = Integer.parseInt(vals[0]);
//		int height = Integer.parseInt(vals[1]);
//		frameGrabbed(width, height);
//	}

	/** called by Flash oculusPrime_grabber.swf after writing data to shared object file
	 * linux only for now
	 **/
	public void frameGrabbed(int width, int height) {
	
		try {
			
			// read file into bytebuffer
			FileInputStream file = new FileInputStream("/run/shm/oculusPrimeFlashSO/framegrab.sol");
			FileChannel ch = file.getChannel();
			int size = (int) ch.size();
			ByteBuffer frameData = ByteBuffer.allocate( size );
			ch.read(frameData.order(ByteOrder.BIG_ENDIAN));
			ch.close();
			file.close();
			
//			int width=640;
//			int height=480;
			
			if (settings.readSetting(GUISettings.vset).equals("vmed") || 
					settings.readSetting(GUISettings.vset).equals("vlow")) {  // failed, switch to highres if avail and try again 
				width=320;
				height=240;
			}
			
//			int headersize = 1228843 - (640*480*4);
			int headersize = size - (width*height*4)-1;

			frameData.position(headersize); // skip past header

			boolean invalid = true;
			processedImage  = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			for(int y=0; y<height; y++) {
				for (int x=0; x<width; x++) {
					int rgb = frameData.getInt();    // argb ok for png only
					if (rgb != 0) invalid = false;
					rgb = rgb & 0x00ffffff;  // can't have alpha channel if want to jpeg out
					processedImage.setRGB(x, y, rgb);
				}
			}

			if (invalid) Util.log("error, framegrab invalid", this);
			
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

			boolean invalid = true;
			processedImage  = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			for(int y=0; y<height; y++) {
				for (int x=0; x<width; x++) {
					int rgb = frameData.getInt();    // argb ok for png only
					if (rgb != 0) invalid = false;
					rgb = rgb & 0x00ffffff;  // can't have alpha channel if want to jpeg out
					processedImage.setRGB(x, y, rgb);
				}
			}
			if (invalid) Util.log("error, framegrab empty", this);

			state.set(State.values.framegrabbusy.name(), false);
			
		} catch (Exception e) {			Util.printError(e);		}

//		Util.debug("mediumframegrab finished at: "+System.currentTimeMillis(), this);
	}
	
	public void messageplayer(String str, String status, String value) {

		if (state.exists(values.relayserver)) {
			red5client.sendToRelay("messageplayer", new Object[] {str, status, value});
		}
		
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
				if(DEBUG_PLAYER) Util.debug("messageplayer: "+str+", "+status+", "+value);
		}

	}

	public void sendplayerfunction(String fn, String params) {
		if (player instanceof IServiceCapableConnection) {
			IServiceCapableConnection sc = (IServiceCapableConnection) player;
			sc.invoke("playerfunction", new Object[] { fn, params });
		}

		if (state.exists(values.relayserver)) {
			red5client.sendToRelay("sendplayerfunction", new Object[]{fn, params});
		}
	}

	public void saySpeech(String str) {
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

			str += " vidctroffset " + settings.readSetting(GUISettings.vidctroffset);
			str += " rovvolume " + settings.readSetting(GUISettings.volume);
			str += " stream " + state.get(State.values.stream);
			if (!state.get(values.driverstream).equals(driverstreamstate.disabled.toString()))
				str += " selfstream " + state.get(State.values.driverstream);
//			str += " pushtotalk " + settings.readSetting("pushtotalk");
			
			if (loginRecords.isAdmin()) str += " admin true";
			
			if (state.get(State.values.dockstatus) != null) str += " dock "+ state.get(State.values.dockstatus);
			
			
			if (settings.getBoolean(ManualSettings.developer)) str += " developer true";
			if (settings.getBoolean(GUISettings.navigation)) str += " navigation true";

			String videoScale = settings.readSetting("videoscale");
			if (videoScale != null) str += " videoscale " + videoScale;

			str += " battery " + state.get(State.values.batterylife);

			if (state.exists(values.record)) str += " record " + state.get(values.record);
			
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
		result += settings.readSetting("vhigh") + "_" + settings.readSetting("vfull") + "_";
		result += settings.readSetting("vcustom");
		return result;
	}

	public void restart() {
		messageplayer("restarting server application", null, null);
		
		if(settings.getBoolean(ManualSettings.developer.name())){
			int b = settings.getInteger(ManualSettings.restarted);
			settings.writeSettings(ManualSettings.restarted, Integer.toString(b+1));
			
// THIS in developer mode, or just warning? 	
//			if(settings.getInteger(ManualSettings.restarted) > 10){
//			Util.log("restart called but reboot neededd, going down..", this);
			
		}
		
		// write file as restart flag for script
		File f = new File(Settings.redhome + Util.sep + "restart");
		if (!f.exists()){
			try {
				f.createNewFile();
			} catch (IOException e) {
				Util.printError(e);
			}
		}
		
		shutdownApplication();
	}
	
	public void reboot(){
		Util.log("rebooting system", this);
		PowerLogger.append("rebooting system", this);
		powerport.writeStatusToEeprom();
		killGrabber(); // prevents error dialog on chrome startup
		settings.writeSettings(ManualSettings.restarted, "0");

		if(state.exists(values.odomlinearpwm)){
			settings.writeSettings(ManualSettings.odomlinearpwm,
				String.valueOf((int) comport.unVoltsComp(state.getDouble(values.odomlinearpwm))));
		}
		
		if(state.exists(values.odomturnpwm)) {
			settings.writeSettings(ManualSettings.odomturnpwm,
				String.valueOf((int) comport.unVoltsComp(state.getDouble(values.odomturnpwm))));
		}

		Util.delay(1000);

//		if (!state.get(values.osarch).equals(ARM)) {
//			Util.systemCall(Settings.redhome + Util.sep + "systemreboot.sh");
//		}
//		else Util.systemCall("/usr/bin/sudo /sbin/shutdown -r now");
		
		Util.systemCall(Settings.redhome + Util.sep + "systemreboot.sh");

	}

	public void powerdown() { // typically called with powershutdown so has to happen quick, skip usual shutdown stuff
		Util.log("powering down system", this);
		PowerLogger.append("powering down system", this);
		powerport.writeStatusToEeprom();
		killGrabber(); // prevents error dialog on chrome startup
		Util.delay(1000);

//		if (!state.get(values.osarch).equals(ARM)) {
//			Util.systemCall(Settings.redhome + Util.sep + "systemshutdown.sh");
//		}
//		else Util.systemCall("/usr/bin/sudo /sbin/shutdown -h now");
		Util.systemCall(Settings.redhome + Util.sep + "systemshutdown.sh");

	}

	public void shutdownApplication() {
		
		Util.log("shutting down application", this);
		PowerLogger.append("shutting down application", this);

		if(commandServer!=null) {
			commandServer.sendToGroup(TelnetServer.TELNETTAG + " shutdown");
			commandServer.close();
		}
		
		if (powerport.isConnected()) powerport.writeStatusToEeprom();
		PowerLogger.close();
		
		if( ! state.equals(values.navsystemstatus, Ros.navsystemstate.stopped.toString())) Navigation.stopNavigation();

		if(state.exists(values.odomlinearpwm)) settings.writeSettings(ManualSettings.odomlinearpwm,
			String.valueOf((int) comport.unVoltsComp(state.getDouble(values.odomlinearpwm))));
			
		if (state.exists(values.odomturnpwm)) settings.writeSettings(ManualSettings.odomturnpwm,
			String.valueOf((int) comport.unVoltsComp(state.getDouble(values.odomturnpwm))));
			
		/*if( ! settings.getBoolean(ManualSettings.debugenabled))*/ killGrabber();

		Util.systemCall(Settings.redhome + Util.sep + "red5-shutdown.sh");
	}

	public void move(final String str) {

		if (str.equals(ArduinoPrime.direction.stop.name())) {
			if (state.getBoolean(State.values.autodocking))
				docker.autoDockCancel();
			state.set(values.calibratingrotation, false);

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

// doesn't work
//		Util.log("..... move(): user taking over, cancel route........................ ");
//		Navigation.cancelAllRoutes(); 
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
		
// doesn't work
//		Util.log("..... nudge(): user taking over, cancel route........................ ");
//		Navigation.cancelAllRoutes(); 
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

	public void messageGrabber(String str, String status) {
		Util.debug("TO grabber flash: " + str + ", " + status, this);

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

		String con = "connected";

		if (state.exists(values.relayclient)) {
			IServiceCapableConnection t = (IServiceCapableConnection) relayclient;
			t.invoke("playerSignIn", new Object[]{state.get(values.driver)});
			con = "relay";
		}

		IConnection tmp = player;
		player = pendingplayer;
		pendingplayer = tmp;
		state.set(State.values.driver, user);
		String str = "connection "+con+" streamsettings " + streamSettings();
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

		String val[] = str.split("\\D+");
		if (val.length != 2) { return; } 
		Integer videoThreshold = Integer.parseInt(val[0]);
		Integer audioThreshold = Integer.parseInt(val[1]);

		String stream = state.get(State.values.stream);
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
		sc.invoke("setActivityThreshold", new Object[]{videoThreshold, audioThreshold});
		messageplayer("stream activity set to: " + str, null, null);

	}
	
	private void streamActivityDetected(String str) {
		if (! state.exists(State.values.streamactivityenabled)) return;

		// to catch false audio detect on driver login... TODO: find root cause, not just this patch
		if (str.split(" ")[0].equals("audio")) { // note: video deprecated
			int audiodetected = Integer.valueOf(str.split(" ")[1]);
			int audiothreshold = Integer.valueOf(state.get(values.streamactivitythreshold).split(" ")[1]);
//			Util.log(audiodetected+ " "+audiothreshold, this);
			if (audiodetected < audiothreshold) {
				setStreamActivityThreshold(state.get(values.streamactivitythreshold)); // restarts stream
				return;
			}
		}

		if (System.currentTimeMillis() > state.getLong(State.values.streamactivityenabled) + 5000.0) {
			messageplayer("streamactivity: "+str, "streamactivity", str);
			setStreamActivityThreshold("0 0"); // disable
			state.set(State.values.streamactivity, str); // needs to be after disable, method deletes state val
		}
	}
}


