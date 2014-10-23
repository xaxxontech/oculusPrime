package oculusPrime;

import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayInputStream;

import javax.imageio.ImageIO;

import oculusPrime.commport.ArduinoPower;
import oculusPrime.commport.ArduinoPrime;

import org.red5.server.api.IConnection;
import org.red5.server.api.service.IServiceCapableConnection;

public class AutoDock { // implements Observer {

	public static final String UNDOCKED = "un-docked";
	public static final String DOCKED = "docked";
	public static final String DOCKING = "docking";
	public static final String UNKNOWN = "unknown";

	private Settings settings = Settings.getReference();
	private String docktarget = settings.readSetting(GUISettings.docktarget);;
	private State state = State.getReference();
	private boolean autodockingcamctr = false;
	private ArduinoPrime comport = null;
	private IConnection grabber = null;
	private int autodockctrattempts = 0;
	private Application app = null;
	private OculusImage oculusImage = new OculusImage();
	private int rescomp; // (multiplier - javascript sends clicksteer based on 640x480, autodock uses 320x240 images)
	private final int allowforClickSteer = 500;
	private int dockattempts = 0;
	private final int maxdockattempts = 3;
	private int imgwidth;
	private int imgheight;
	public boolean lowres = true;
//	private ArduinoPower powerport = null;
	private final int FLHIGH = 25;
	private final int FLLOW = 7;
	
	
	public AutoDock(Application theapp, IConnection thegrab, ArduinoPrime com, ArduinoPower powercom) {
		this.app = theapp;
		this.grabber = thegrab;
		this.comport = com;
//		this.powerport = powercom;
		// state.addObserver(this);
//		docktarget = settings.readSetting(GUISettings.docktarget);
		oculusImage.dockSettings(docktarget);
		state.set(State.values.autodocking, false);
	}

	public void autoDock(String str){//PlayerCommands.autodockargs arg) {

		Util.debug("autoDock(): " + str, this);
		String cmd[] = str.split(" ");
		Util.debug(str, this);
		
		if (cmd[0].equals("cancel")) {
			autoDockCancel();
		}
		
	
		if (cmd[0].equals("go")) {
			if (state.getBoolean(State.values.motionenabled)) { 
				if(state.getBoolean(State.values.autodocking)){
					app.message("auto-dock in progress", null, null);
					return;
				}
				
				lowres=true;
				
				new Thread(new Runnable() {
					public void run() {
						try {

							if (state.getInteger(State.values.spotlightbrightness) > 20 && 
									!state.getBoolean(State.values.controlsinverted)) {
								comport.setSpotLightBrightness(20);
								Thread.sleep(500); 
							} 
							
							if (state.getInteger(State.values.floodlightlevel) == 0) comport.floodLight(FLHIGH); 
							
							dockGrab("start", 0, 0);
							state.set(State.values.autodocking, true);
							autodockingcamctr = false;
							//autodockgrabattempts = 0;
							autodockctrattempts = 0;
							dockattempts = 1;
							app.message("auto-dock in progress", "motion", "moving");
							System.out.println("OCULUS: autodock started");
				
						} catch (Exception e) { e.printStackTrace(); }
					}
				}).start();
				

				
			}
			else { app.message("motion disabled","autodockcancelled", null); }
		}
		if (cmd[0].equals("dockgrabbed")) { 
			
			state.set(State.values.dockxpos.name(), cmd[2]);
			state.set(State.values.dockypos.name(), cmd[3]);
			state.set(State.values.dockxsize.name(), cmd[4]);
			state.set(State.values.dockslope.name(), cmd[6]);
			if (cmd[1].equals("find") && state.getBoolean(State.values.autodocking)) { // x,y,width,height,slope
				int width = Integer.parseInt(cmd[4]);
				if (width < (int) (0.02*imgwidth) || width > (int) (0.875*imgwidth) || cmd[5].equals("0")) { // unrealistic widths,failed to find target
					if (lowres && !(settings.readSetting(GUISettings.vset).equals("vmed") || 
								settings.readSetting(GUISettings.vset).equals("vlow"))) {  // failed, switch to highres if avail and try again 
						lowres = false;
						dockGrab("start", 0, 0);
						Util.debug("trying again in higher resolution",this);
					}
					else {
						state.set(State.values.autodocking, false);
						state.set(State.values.docking, false);
						app.message("auto-dock target not found, try again", "multiple", "autodockcancelled blank");
						Util.log("autoDock():  target lost", this);
						lowres = true;
					}
				} else {
					// autodockgrabattempts++;

					int x = Integer.parseInt(cmd[2]);
					int y = Integer.parseInt(cmd[3]);

					int guix = Integer.parseInt(cmd[2])/(2/rescomp);
					int guiy = Integer.parseInt(cmd[3])/(2/rescomp);
					int guiw = Integer.parseInt(cmd[4])/(2/rescomp);
					int guih = Integer.parseInt(cmd[5])/(2/rescomp);
//					String s = cmd[2] + " " + cmd[3] + " " + cmd[4] + " " + cmd[5] + " " + cmd[6];

					String s = guix + " " + guiy + " " + guiw + " " + guih + " " + cmd[6];
					
					if (!state.getBoolean(State.values.controlsinverted)) { // need to face backwards
			
						comport.setSpotLightBrightness(0);
						
						app.message(null, "autodocklock", s);
						state.set(State.values.autodocking, false);
						comport.clickSteer((x - imgwidth/2) * rescomp, (y - imgheight/2) * rescomp);
						
						new Thread(new Runnable() {
							public void run() {
								try {

									Thread.sleep(allowforClickSteer); 
//									int pos = ArduinoPrime.CAM_MAX - 10;
									comport.camCommand(ArduinoPrime.cameramove.reverse);
									Thread.sleep(50); // sometimes above command being ignore, maybe this will help
									comport.rotate(ArduinoPrime.direction.left, 180);
//									state.set(State.values.cameratilt, 0); // arbitrary value, to 	wait for actual position reached
//									state.block(oculusPrime.State.values.cameratilt, Integer.toString(pos), 10000); 
									Thread.sleep(2500);
//									autoDock("go");
									state.set(State.values.autodocking, true);
									dockGrab("find", 0, 0);
									
								} catch (Exception e) { e.printStackTrace(); }
							}
						}).start();
						return;
					}
					
					lowres = true;
					
					app.message(null, "autodocklock", s);
					autoDockNav(x, y, Integer.parseInt(cmd[4]), Integer.parseInt(cmd[5]), new Float(cmd[6]));
				}
			}
			
			if (cmd[1].equals("calibrate")) {
				// x,y,width,height,slope,lastBlobRatio,lastTopRatio,lastMidRatio,lastBottomRatio
				// write to:
				// lastBlobRatio, lastTopRatio,lastMidRatio,lastBottomRatio,
				// x,y,width,height,slope
				docktarget = cmd[7] + "_" + cmd[8] + "_" + cmd[9] + "_"
						+ cmd[10] + "_" + cmd[2] + "_" + cmd[3] + "_" + cmd[4]
						+ "_" + cmd[5] + "_" + cmd[6];
				settings.writeSettings("docktarget", docktarget);
				String s = cmd[2] + " " + cmd[3] + " " + cmd[4] + " " + cmd[5]
						+ " " + cmd[6];
				// messageplayer("dock"+cmd[1]+": "+s,"autodocklock",s);
				app.message("auto-dock calibrated", "autodocklock", s);
			}
		}
		if (cmd[0].equals("calibrate")) {
			int x = Integer.parseInt(cmd[1]) / 2; // assuming 320x240
			int y = Integer.parseInt(cmd[2]) / 2; // assuming 320x240
			lowres = true;
			dockGrab("calibrate", x, y);
		}	
	}
	
/*
	@Override
	public void updated(String key) {
		Util.debug("updated(): " + key, this);
		
		if(state.equals(State.values.batterycharging, key)){
			Util.debug("updated(): battery charging.....", this);
		}
		
	}
*/	
	public void autoDockStart(final String str) {
		Util.debug("autoDockStart(): " + str, this);
	}

	public void autoDockCancel() {
		Util.debug("autoDockCancel(): ", this);
		state.set(State.values.autodocking, false);
		app.message("auto-dock ended", "multiple", "cameratilt " + state.get(State.values.cameratilt) + " autodockcancelled blank motion stopped");
	}

	public void autoDockcalibrate(final String str) {
		Util.debug("autoDockcalibrate(): " + str, this);
	}
	
/*
	private class WatchdogTask extends Thread {
		@Override
		public void run(){
			
			
			Util.debug("dock(): battery level: " + state.get(oculus.State.values.batterylife), this);
			// state.block(State.values., target, timeout)
		
		}
	}
*/
	
	// drive the bot in to charger watching for battery change with a blocking thread 
	public void dock() {
		
		if (state.getBoolean(State.values.docking)) {
			Util.debug("dock(): already docking", this);
			return;
		}

		if (!state.getBoolean(State.values.motionenabled)) {
			app.message("motion disabled", null, null);
			return;
		}

		app.message("docking initiated", "multiple", "speed slow motion moving dock docking");
		state.set(State.values.docking, true);
		state.set(State.values.dockstatus, DOCKING);
		comport.speedset(ArduinoPrime.speeds.slow.toString());
		state.set(State.values.movingforward, false);
//		powerport.prepareBatteryToDock();
		
		
		new Thread(new Runnable() {	
			public void run() {		
				int inchforward = 0;
				while (inchforward < 15 && !state.getBoolean(State.values.batterycharging) && 
						state.getBoolean(State.values.docking)) {
					comport.goForward();
					Util.delay(150);
					comport.stopGoing();
					
					state.block(oculusPrime.State.values.batterycharging, "true", 400);
					inchforward ++;
				}
				
				if(state.getBoolean(State.values.batterycharging)) { // dock successful
					
					state.set(State.values.docking, false);
					state.set(State.values.motionenabled, false);
					state.set(State.values.dockstatus, DOCKED);
					comport.speedset(ArduinoPrime.speeds.fast.toString());

					String str = "";
					
					if (state.getBoolean(State.values.autodocking)) {
						state.set(State.values.autodocking, false);
						str += " cameratilt "+state.get(State.values.cameratilt)+" autodockcancelled blank";
						if (!state.get(State.values.stream).equals("stop") && state.get(State.values.driver)==null) { 
							app.publish("stop"); 
						}
						
						comport.floodLight(0);	
						comport.setSpotLightBrightness(0);
						comport.camCommand(ArduinoPrime.cameramove.horiz);
					}
					
					app.message("docked successfully", "multiple", "motion disabled dock docked battery charging"+str);
					Util.debug("run(): " + state.get(State.values.driver) + " docked successfully", this);

				} else { // dock fail
					
					if (state.getBoolean(State.values.docking)) {
						state.set(State.values.docking, false); 
						state.set(State.values.dockstatus, UNDOCKED);
						app.message("docking timed out", "multiple", "dock un-docked motion stopped");
						Util.debug("dock(): " + state.get(State.values.driver) + " docking timed out", this);
//						powerport.manualSetBatteryUnDocked();						
						
						if (dockattempts < maxdockattempts && state.getBoolean(State.values.autodocking)) {
							// back up and retry
							dockattempts ++;
							comport.speedset(ArduinoPrime.speeds.fast.toString());
							comport.goBackward();
							try {
//								Thread.sleep(400);
								comport.delayWithVoltsComp(400);
								comport.stopGoing();
								Thread.sleep(settings.getInteger(ManualSettings.stopdelay)); // let deaccelerate							
							} catch (InterruptedException e) { e.printStackTrace(); }
							dockGrab("start", 0, 0);
							state.set(State.values.autodocking, true);
							autodockingcamctr = false;
						}
						else { // give up
							state.set(State.values.autodocking, false);
							if (!state.get(State.values.stream).equals("stop") && state.get(State.values.driver)==null) { 
								app.publish("stop"); 
							}
							
							comport.floodLight(0);	
							comport.setSpotLightBrightness(0);
							String str = "motion disabled dock "+UNDOCKED+" battery draining cameratilt "
									+state.get(State.values.cameratilt)+" autodockcancelled blank";
							app.message("autodock completely failed", "multiple", str);
						}
					}
						
				}
			}
		}).start();
	}


	/*
	 * notes
	 * 
	 * boolean autodocking = false; String docktarget; // calibration values s[]
	 * = 0 lastBlobRatio,1 lastTopRatio,2 lastMidRatio,3 lastBottomRatio,4 x,5
	 * y,6 width,7 height,8 slope UP CLOSE 85x70
	 * 1.2143_0.23563_0.16605_0.22992_124_126_85_70_0.00000 FAR AWAY 18x16
	 * 1.125_0.22917_0.19792_0.28819_144_124_18_16_0.00000
	 * 
	 * 
	 * 
	 * 1st go click: dockgrab_findfromxy MODE1 if autodocking = true: if size <=
	 * S1, if not centered: clicksteer to center, dockgrab_find [BREAK] else go
	 * forward CONST time, dockgrab_find [BREAK] if size > S1 && size <=S2
	 * determine N based on slope and blobsize magnitude if not centered +- N:
	 * clicksteer to center +/- N, dockgrab_find [BREAK] go forward N time if
	 * size > S2 if slope and XY not within target: backup, dockgrab_find else :
	 * dock END MODE1
	 * 
	 * events: dockgrabbed_find => enter MODE1 dockgrabbed_findfromxy => enter
	 * MODE1
	 */
	private void autoDockNav(int x, int y, int w, int h, float slope) {

		x = x + (w / 2); // convert to center from upper left
		y = y + (h / 2); // convert to center from upper left
		String s[] = docktarget.split("_");
		// s[] = 0 lastBlobRatio,1 lastTopRatio,2 lastMidRatio,3
		// lastBottomRatio,4 x,5 y,6 width,7 height,8 slope
		// 0.71053_0.27940_0.16028_0.31579_123_93_81_114_0.014493
		// neg slope = approaching from left
		int dockw = (int) (Integer.parseInt(s[6])/(rescomp/2f));
		int dockh = (int) (Integer.parseInt(s[7])/(rescomp/2f));
		int dockx = (int) (Integer.parseInt(s[4])/(rescomp/2f)) + dockw / 2;
		float dockslope = new Float(s[8]);
		float slopedeg = (float) ((180 / Math.PI) * Math.atan(slope));
		float dockslopedeg = (float) ((180 / Math.PI) * Math.atan(dockslope));
//		int s1 = dockw * dockh * 12 / 100 * w / h;  // (area) legacy, for non-mirrored cam
//		int s2 = (int) (dockw * dockh * 65.5 / 100 * w / h); // (area) legacy, for non-mirrored cam
		int s1 = (int) (dockw * dockh * 0.15  * w / h);  // (area) 
		int s2 = (int) (dockw * dockh * 69.0 / 100 * w / h); // (area)

		// optionally set breaking delay longer for fast bots
		int bd = settings.getInteger(ManualSettings.stopdelay.toString());
		if (bd == Settings.ERROR) bd = 500;
		final int stopdelay = bd;
		final int s1FWDmilliseconds = 700; // 400
		final int s2FWDmilliseconds = 250; // 100
		
		if (w * h < s1) { // mode: quite far away yet, approach only
			Util.debug("autodock stage 1", this);

			if (state.getInteger(State.values.spotlightbrightness) > 0) {
				comport.setSpotLightBrightness(0);
//				comport.floodLight(FLHIGH);
			} 
			
			if (state.getInteger(State.values.floodlightlevel) == 0) comport.floodLight(FLHIGH);
			if (Math.abs(x - imgwidth/2) > (int) (imgwidth*0.03125) || Math.abs(y - imgheight/2) > (int) (imgheight*0.104167)) { // clicksteer and go (y was >50)
				comport.clickSteer((x - imgwidth/2) * rescomp, (y - imgheight/2) * rescomp);
				new Thread(new Runnable() {

					public void run() {
						try {
//							Thread.sleep(allowforClickSteer); // was 1500 w/ dockgrab following
							comport.delayWithVoltsComp(allowforClickSteer);
							comport.speedset(ArduinoPrime.speeds.fast.toString());
							comport.goForward();
//							Thread.sleep(s1FWDmilliseconds);
							comport.delayWithVoltsComp(s1FWDmilliseconds);
							comport.stopGoing();
							Thread.sleep(stopdelay);
							dockGrab("find", 0, 0);

						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}).start();
			} else { // go only
				new Thread(new Runnable() {
					public void run() {
						try {
							comport.speedset(ArduinoPrime.speeds.fast.toString());
							comport.goForward();
//							Thread.sleep(s1FWDmilliseconds);
							comport.delayWithVoltsComp(s1FWDmilliseconds);
							comport.stopGoing();
							Thread.sleep(stopdelay); // let deaccelerate
							dockGrab("find", 0, 0);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}).start();
			}
		} // end of S1 check
		if (w * h >= s1 && w * h < s2) { // medium distance, detect slope when centered and approach
			int fl = state.getInteger(State.values.floodlightlevel);
			if (fl > 0 && fl != 15) comport.floodLight(FLLOW); 
			
			if (autodockingcamctr) { // if cam centered do check and comps below
				autodockingcamctr = false;
				int autodockcompdir = 0;
				if (Math.abs(slopedeg - dockslopedeg) > 1.7) {
					autodockcompdir = (int) (imgwidth/2 - w - (int) (imgwidth*0.0625) - Math.abs(imgwidth/2 - x)); // was 160 - w - 25 -Math.abs(160-x)
				}
				if (slope > dockslope) {
					autodockcompdir *= -1;
				} // approaching from left
				autodockcompdir += x + (dockx - imgwidth/2);
				// System.out.println("comp: "+autodockcompdir);
				if (Math.abs(autodockcompdir - dockx) > (int) (imgwidth*0.03125) || Math.abs(y - imgheight/2) > (int) (imgheight*0.125)) { // steer and go
					comport.clickSteer((autodockcompdir - dockx) * rescomp,
							(y - imgheight/2) * rescomp);
					new Thread(new Runnable() {
						public void run() {
							try {
								comport.delayWithVoltsComp(allowforClickSteer);
								comport.speedset(ArduinoPrime.speeds.fast.toString());
								comport.goForward();
//								Thread.sleep(s2FWDmilliseconds);
								comport.delayWithVoltsComp(s2FWDmilliseconds);
								comport.stopGoing();
								Thread.sleep(stopdelay); // let deaccelerate
								dockGrab("find", 0, 0);
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}).start();
				} else { // go only
					new Thread(new Runnable() {
						public void run() {
							try {
								comport.speedset(ArduinoPrime.speeds.fast.toString());
								comport.goForward();
//								Thread.sleep(s2FWDmilliseconds);
								comport.delayWithVoltsComp(s2FWDmilliseconds);
								comport.stopGoing();
								Thread.sleep(stopdelay); // let deaccelerate
								dockGrab("find", 0, 0);
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}).start();
				}
			} else { // !autodockingcamctr
				autodockingcamctr = true;
				if (Math.abs(x - dockx) > (int) (0.03125*imgwidth) || Math.abs(y - imgheight/2) > (int) (0.0625*imgheight)) { // (y
																			// was
																			// >30)
					comport.clickSteer((x - dockx) * rescomp, (y - imgheight/2)
							* rescomp);
					new Thread(new Runnable() {
						public void run() {
							try {
								comport.delayWithVoltsComp(allowforClickSteer);
								dockGrab("find", 0, 0);
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}).start();
				} else {
					dockGrab("find", 0, 0);
				}
			}
		} 
		if (w * h >= s2) { // right in close, centering camera only, backup and try again if position wrong
			if ((Math.abs(x - dockx) > 5) && autodockctrattempts <= 10) {
				autodockctrattempts++;

//				comport.clickSteer((x - dockx) * rescomp, (y - 120) * rescomp);
				int minimum_clicksteerMovement = (int) (0.025*imgwidth); //pixels out of 320 //TODO: this will vary with floor type, make settable
				int movex = (x - dockx);
				if (Math.abs(movex) < minimum_clicksteerMovement) {
					if (movex > 0) { movex = minimum_clicksteerMovement; }
					else { movex = -minimum_clicksteerMovement; }
				}
				comport.clickSteer(movex * rescomp, (y-imgheight/2) * rescomp); 
				// 
				new Thread(new Runnable() {
					public void run() {
						try {
							comport.delayWithVoltsComp(allowforClickSteer);
							dockGrab("find", 0, 0);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}).start();
			} else {
				if (Math.abs(slopedeg - dockslopedeg) > 1.6
						|| autodockctrattempts > 10) { // rotate a bit, then backup and try again
					// System.out.println("backup "+dockslopedeg+" "+slopedeg+" ctrattempts:"+autodockctrattempts);
					autodockctrattempts = 0;
					int comp = imgwidth/4;
					if (slope < dockslope) {
						comp = -comp;
					}
					x += comp;

					comport.clickSteer((x - dockx) * rescomp, (y - imgheight/2) * rescomp);
					new Thread(new Runnable() {
						public void run() {
							try {
								comport.delayWithVoltsComp(allowforClickSteer);
								comport.speedset(ArduinoPrime.speeds.fast.toString());
								comport.goBackward();
//								Thread.sleep(s1FWDmilliseconds);
								comport.delayWithVoltsComp(s1FWDmilliseconds);
								comport.stopGoing();
								Thread.sleep(stopdelay); // let deaccelerate
								dockGrab("find", 0, 0);
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}).start();
					Util.debug("autodock backup", this);
				} else {
					// System.out.println("dock "+dockslopedeg+" "+slopedeg);
					new Thread(new Runnable() {
						public void run() {
							try {
								Thread.sleep(100);
								dock();
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}).start();
				}
			}
		}
	}

	public void getLightLevel() {

		if (state.getBoolean(State.values.framegrabbusy.name())
				|| !(state.get(State.values.stream).equals("camera") || state
						.get(State.values.stream).equals("camandmic"))) {
			app.message("framegrab busy or stream unavailable", null, null);
			return;
		}

		if (grabber instanceof IServiceCapableConnection) {
			Application.framegrabimg = null;
			Application.processedImage = null;
			state.set(State.values.framegrabbusy.name(), true);
			IServiceCapableConnection sc = (IServiceCapableConnection) grabber;
			sc.invoke("framegrabMedium", new Object[] {});
			app.message("getlightlevel command received", null, null);
		}

		new Thread(new Runnable() {
			public void run() {
				try {
					int n = 0;
					while (state.getBoolean(State.values.framegrabbusy)) {
						try {
							Thread.sleep(5);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						n++;
						if (n > 2000) { // give up after 10 seconds
							state.set(State.values.framegrabbusy, false);
							break;
						}
					}

					Util.debug("img received, processing...", this);
					
					
					BufferedImage img = null;
					if (Application.framegrabimg != null) {
						Util.debug("framegrabimg received, processing...", this);

						// convert bytes to image
						ByteArrayInputStream in = new ByteArrayInputStream(Application.framegrabimg);
						img = ImageIO.read(in);
						in.close();
						
					}
						
					else if (Application.processedImage != null) {
						Util.debug("frame received, processing processedImage", this);
						img = Application.processedImage;
					}
					
					else { Util.log("dockgrab failure", this); return; }

					n = 0;
					int avg = 0;
					for (int y = 0; y < img.getHeight(); y++) {
						for (int x = 0; x < img.getWidth(); x++) {
							int rgb = img.getRGB(x, y);
							int red = (rgb & 0x00ff0000) >> 16;
							int green = (rgb & 0x0000ff00) >> 8;
							int blue = rgb & 0x000000ff;
							avg += red * 0.3 + green * 0.59 + blue * 0.11; // grey
																			// using
																			// 39-59-11
																			// rule
							n++;
						}
					}
					avg = avg / n;
					app.message("getlightlevel: " + Integer.toString(avg),
							null, null);
					

				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();
	}

	public void dockGrab(final String mode, final int x, final int y) {
		
//		final OculusImage oculusImage = new OculusImage();
//		oculusImage.dockSettings(docktarget); // can't set this every time dockGrab is called, only app start
		
		state.set(oculusPrime.State.values.dockgrabbusy, true);

		if (state.getBoolean(State.values.framegrabbusy.name())
				|| ! (state.get(State.values.stream).equals("camera") 
				|| state.get(State.values.stream).equals("camandmic"))) {
			app.message("framegrab busy or stream unavailable", null, null);
			return;
		}

		if (grabber instanceof IServiceCapableConnection) {
			state.set(State.values.framegrabbusy.name(), true);
			Application.framegrabimg = null;
			Application.processedImage = null;
			IServiceCapableConnection sc = (IServiceCapableConnection) grabber;
			String resolution; 
			if (lowres) { resolution = "framegrabMedium"; }
			else { resolution = "framegrab"; }
			
			sc.invoke(resolution, new Object[] {});
		}

		new Thread(new Runnable() {
			public void run() {
				try {
					int n = 0;
					while (state.getBoolean(State.values.framegrabbusy)) {
						try {
							Thread.sleep(5);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						n++;
						if (n > 2000) { // give up after 10 seconds
							Util.debug("frame grab timed out", this);
							state.set(State.values.framegrabbusy, false);
							break;
						}
					}

					BufferedImage img = null;
					if (Application.framegrabimg != null) {
						Util.debug("dockGrab(): img received, processing...", this);

						// convert bytes to image
						ByteArrayInputStream in = new ByteArrayInputStream(Application.framegrabimg);
						img = ImageIO.read(in);
						in.close();
						
					}
						
					else if (Application.processedImage != null) {
						Util.debug("frame received, processing processedImage", this);
						img = Application.processedImage;
					}
					
					else { Util.log("dockgrab failure", this); return; }
						
					imgwidth= img.getWidth();
					Util.debug("image width: "+imgwidth, this);
					imgheight= img.getHeight();
					rescomp = 640/imgwidth; // for clicksteer gui 640 window

					float[] matrix = { 0.111f, 0.111f, 0.111f, 0.111f,
							0.111f, 0.111f, 0.111f, 0.111f, 0.111f, };

					BufferedImageOp op = new ConvolveOp(new Kernel(3, 3, matrix));
					img = op.filter(img, new BufferedImage(imgwidth, imgheight, BufferedImage.TYPE_INT_ARGB));
					
					int[] argb = img.getRGB(0, 0, imgwidth, imgheight, null, 0, imgwidth);
					
//					if (state.getBoolean(State.values.controlsinverted)) { 
//						for (int yy=0; yy<imgheight/2; yy++) {
//							for (int xx=0; xx<imgwidth; xx++) {
//								int temp = argb[xx+yy*imgwidth];
//								argb[xx+yy*imgwidth] = argb[xx+(imgheight-yy-1)*imgwidth];
//								argb[xx+(imgheight-yy-1)*imgwidth] = temp;
//							}
//						}
//					}
		
					if (mode.equals("calibrate")) {
						String[] results = oculusImage.findBlobStart(x, y, img.getWidth(), img.getHeight(), argb);
						autoDock("dockgrabbed calibrate " + results[0]
								+ " " + results[1] + " " + results[2] + " "
								+ results[3] + " " + results[4] + " "
								+ results[5] + " " + results[6] + " "
								+ results[7] + " " + results[8]);
					}
					if (mode.equals("start")) {
						oculusImage.lastThreshhold = -1;
					}
					if (mode.equals("find") || mode.equals("start")) {
						String results[] = oculusImage.findBlobs(argb, imgwidth, imgheight);
                        String str = results[0] + " " + results[1] + " " + results[2] + " " +
                        		results[3] + " " + results[4];
						// results = x,y,width,height,slope
						autoDock("dockgrabbed find " + str);
					}

					if (mode.equals("test")) {
						oculusImage.lastThreshhold = -1;
						String results[] = oculusImage.findBlobs(argb, imgwidth, imgheight);
						int guix = Integer.parseInt(results[0])/(2/rescomp);
						int guiy = Integer.parseInt(results[1])/(2/rescomp);
						int guiw = Integer.parseInt(results[2])/(2/rescomp);
						int guih = Integer.parseInt(results[3])/(2/rescomp);
						String str = guix + " " + guiy + " " + guiw + " " + guih + " " + results[4];
						// results = x,y,width,height,slope
						
//						autoDock("dockgrabbed find " + str);
//						Util.debug(str, this);
						app.message(str, "autodocklock", str);
//						app.sendplayerfunction("processedImg", "load");

					}

					state.set(State.values.dockgrabbusy.name(), false);

					
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();
	}

}
