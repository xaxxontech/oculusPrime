package oculusPrime;

import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayInputStream;

import javax.imageio.ImageIO;

import oculusPrime.commport.ArduinoPower;
import oculusPrime.commport.ArduinoPrime;
import oculusPrime.commport.PowerLogger;

import org.red5.server.api.IConnection;
import org.red5.server.api.service.IServiceCapableConnection;

public class AutoDock { 
	
	public static final String UNDOCKED = "un-docked";
	public static final String DOCKED = "docked";
	public static final String DOCKING = "docking";
	public static final String UNKNOWN = "unknown";
	public static final String HIGHRES = "highres";
	public enum autodockmodes{ go, dockgrabbed, calibrate, cancel};
	public enum dockgrabmodes{ calibrate, start, find, test };

	private Settings settings = Settings.getReference();
	private String docktarget = settings.readSetting(GUISettings.docktarget);;
	private State state = State.getReference();
	private boolean autodockingcamctr = false;
	private int lastcamctr = 0;
	private ArduinoPrime comport = null;
	private IConnection grabber = null;
	private int autodockctrattempts = 0;
	private Application app = null;
	private OculusImage oculusImage = new OculusImage();
	private int rescomp; // (multiplier - javascript sends clicksteer based on 640x480, autodock uses 320x240 images)
	private final int allowforClickSteer = 500;
	private int dockattempts = 0;
	private final int maxdockattempts = 5;
	private int imgwidth;
	private int imgheight;
	public boolean lowres = true;
	public static final int FLHIGH = 25;
	public static final int FLLOW = 7;
	private final int FLCALIBRATE = 2;
	
	public AutoDock(Application theapp, IConnection thegrab, ArduinoPrime com, ArduinoPower powercom) {
		this.app = theapp;
		this.grabber = thegrab;
		this.comport = com;
		oculusImage.dockSettings(docktarget);
		state.set(State.values.autodocking, false);
	}

	public void autoDock(String str){

		String cmd[] = str.split(" ");
		
		if (cmd[0].equals(autodockmodes.cancel.toString())) {
			autoDockCancel();
		}
		
	
		if (cmd[0].equals(autodockmodes.go.toString())) {
			if (state.getBoolean(State.values.motionenabled)) { 
				if(state.getBoolean(State.values.autodocking)){
					app.message("auto-dock in progress", null, null);
					return;
				}
				
				lowres=true;

				dockGrab(dockgrabmodes.start, 0, 0);
				state.set(State.values.autodocking, true);
				autodockingcamctr = false;
				autodockctrattempts = 0;
				dockattempts = 1;
				app.message("auto-dock in progress", "motion", "moving");
				Util.log("autodock go", this);
				PowerLogger.append("autodock go", this);
			}
			else { app.message("motion disabled","autodockcancelled", null); }
		}
		if (cmd[0].equals(autodockmodes.dockgrabbed.toString())) { 
			
			if (cmd[1].equals(dockgrabmodes.find.toString()) ) { // x,y,width,height,slope
				
 				if (!state.getBoolean(State.values.dockfound)) {
					
					if (lowres && !(settings.readSetting(GUISettings.vset).equals("vmed") || 
								settings.readSetting(GUISettings.vset).equals("vlow"))) {  // failed, switch to highres if avail and try again 
						lowres = false;
						dockGrab(dockgrabmodes.start, 0, 0);
						Util.debug("trying again higher res",this);
					}
					else {
						
						if (!autodockingcamctr) { // maybe occluded by frame, turn back the other way and try again 
							comport.clickSteer(-lastcamctr , 0); 
							autodockingcamctr = true;
							new Thread(new Runnable() {
								public void run() {
									try {
										comport.delayWithVoltsComp(allowforClickSteer);
										dockGrab(dockgrabmodes.find, 0, 0);
									} catch (Exception e) {
										e.printStackTrace();
									}
								}
							}).start();
							return;
						}
						
						state.set(State.values.autodocking, false);
						state.set(State.values.docking, false);
						app.message("auto-dock target not found, try again", "multiple", "autodockcancelled blank");
						Util.log("autoDock():  target lost", this);
						PowerLogger.append("autoDock():  target lost", this);
						lowres = true;
					}
				}
				else { // found target!
					state.set(State.values.dockfound, true);

					int x = Integer.parseInt(cmd[2]);
					int y = Integer.parseInt(cmd[3]);

					int guix = Integer.parseInt(cmd[2])/(2/rescomp);
					int guiy = Integer.parseInt(cmd[3])/(2/rescomp);
					int guiw = Integer.parseInt(cmd[4])/(2/rescomp);
					int guih = Integer.parseInt(cmd[5])/(2/rescomp);
//					String s = cmd[2] + " " + cmd[3] + " " + cmd[4] + " " + cmd[5] + " " + cmd[6];

					String s = guix + " " + guiy + " " + guiw + " " + guih + " " + cmd[6];
					
					if (!state.getBoolean(State.values.controlsinverted)) { // need to face backwards
									
						app.message(null, "autodocklock", s);
//						state.set(State.values.autodocking, false);
						comport.clickSteer((x - imgwidth/2) * rescomp, 0);
						
						new Thread(new Runnable() {
							public void run() {
								try {
									Util.delay(50);
									comport.setSpotLightBrightness(0);
									comport.delayWithVoltsComp(allowforClickSteer); 
									comport.camCommand(ArduinoPrime.cameramove.reverse);
									Thread.sleep(25); // sometimes above command being ignored, maybe this will help
									if (state.getInteger(State.values.floodlightlevel) == 0) comport.floodLight(FLHIGH); 
									Thread.sleep(25); // sometimes above command being ignored, maybe this will help
									comport.rotate(ArduinoPrime.direction.left, 180);
									Thread.sleep(comport.fullrotationdelay/2+2000);
									dockGrab(dockgrabmodes.find, 0, 0);
									
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
			
			if (cmd[1].equals(autodockmodes.calibrate.toString())) {
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
		if (cmd[0].equals(autodockmodes.calibrate.toString())) {
			final int x = Integer.parseInt(cmd[1]) / 2; // assuming 320x240
			final int y = Integer.parseInt(cmd[2]) / 2; // assuming 320x240
			lowres = true;
			comport.floodLight(FLCALIBRATE); 

			new Thread(new Runnable() {
				public void run() {
					try {

						Thread.sleep(2000); // allow light to adjust
						dockGrab(dockgrabmodes.calibrate, x, y);
						
					} catch (Exception e) { e.printStackTrace(); }
				}
			}).start();
			
			

		}	
	}


	public void autoDockCancel() {
		state.set(State.values.autodocking, false);
		app.message("auto-dock ended", "multiple", "cameratilt " + state.get(State.values.cameratilt) + " autodockcancelled blank motion stopped");
	}

	
	// drive the bot in to charger watching for battery change with a blocking thread 
	public void dock() {
		
		if (state.getBoolean(State.values.docking))  return;
 
		if (!state.getBoolean(State.values.motionenabled)) {
			app.message("motion disabled", null, null);
			return;
		}

		app.message("docking initiated", "multiple", "speed slow motion moving dock docking");
		state.set(State.values.docking, true);
		state.set(State.values.dockstatus, DOCKING);
		comport.speedset(ArduinoPrime.speeds.slow.toString());
		state.set(State.values.movingforward, false);
		Util.log("docking initiated", this);
		PowerLogger.append("docking initiated", this);
		
		new Thread(new Runnable() {	
			public void run() {
				comport.goForward();
				Util.delay(300);
				comport.stopGoing();
				int inchforward = 0;
				while (inchforward < 20 && !state.getBoolean(State.values.wallpower) && 
						state.getBoolean(State.values.docking)) {
					comport.goForward();
					Util.delay(150);
					comport.stopGoing();
					
					state.block(oculusPrime.State.values.wallpower, "true", 400);
					inchforward ++;
				}
				
				if(state.getBoolean(State.values.wallpower)) { // dock maybe successful
					comport.goForward(); // on more nudge
					Util.delay(150);
					comport.stopGoing();
					
					comport.strobeflash("on", 120, 20);
					// allow time for charger to get up to voltage 
				     // and wait to see if came-undocked immediately (fairly commmon)
					Util.delay(7000);
				}
				
				if(state.get(State.values.dockstatus).equals(DOCKED)) { // dock successful
					
					state.set(State.values.docking, false);
					comport.speedset(ArduinoPrime.speeds.fast.toString());

					String str = "";
					
					if (state.getBoolean(State.values.autodocking)) {
						state.set(State.values.autodocking, false);
						str += "cameratilt "+state.get(State.values.cameratilt)+" speed fast autodockcancelled blank";
						if (!state.get(State.values.stream).equals("stop") && state.get(State.values.driver)==null) { 
							app.publish(Application.streamstate.stop); 
						}
						
						comport.floodLight(0);	
						comport.camCommand(ArduinoPrime.cameramove.horiz);
//						app.driverCallServer(PlayerCommands.camtiltslow, Integer.toString(ArduinoPrime.CAM_HORIZ));
					}
					
					app.message("docked successfully", "multiple", str);
					Util.log(state.get(State.values.driver) + " docked successfully", this);
					PowerLogger.append(state.get(State.values.driver) + " docked successfully", this);

				} else { // dock fail
					
					if (state.getBoolean(State.values.docking)) {
						state.set(State.values.docking, false); 

						app.message("docking timed out", null, null);
						Util.log("dock(): " + state.get(State.values.driver) + " docking timed out", this);
						PowerLogger.append("dock(): " + state.get(State.values.driver) + " docking timed out", this);

						// back up and retry
						if (dockattempts < maxdockattempts && state.getBoolean(State.values.autodocking)) {
							dockattempts ++;
							
							comport.speedset(ArduinoPrime.speeds.fast.toString());
							comport.goBackward();
							try {
								comport.delayWithVoltsComp(800);
								comport.stopGoing();
								Thread.sleep(ArduinoPrime.LINEAR_STOP_DELAY); // let deaccelerate							
							} catch (InterruptedException e) { e.printStackTrace(); }
							
							dockGrab(dockgrabmodes.start, 0, 0);
							state.set(State.values.autodocking, true);
							autodockingcamctr = false;
						}
						else { // give up
							state.set(State.values.autodocking, false);
							if (!state.get(State.values.stream).equals(Application.streamstate.stop.toString()) && 
										state.get(State.values.driver)==null) { 
								app.publish(Application.streamstate.stop); 
							}
							
							// back away from dock to avoid sketchy contact
							comport.speedset(ArduinoPrime.speeds.med.toString());
							comport.goBackward();
							Util.delay(400);
							comport.stopGoing();

							comport.floodLight(0);	
							
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

		int dockw = (int) (Integer.parseInt(s[6])/(rescomp/2f));
		int dockh = (int) (Integer.parseInt(s[7])/(rescomp/2f));
		int dockx = (int) (Integer.parseInt(s[4])/(rescomp/2f)) + dockw / 2;
		float dockslope = new Float(s[8]);
		float slopedeg = (float) ((180 / Math.PI) * Math.atan(slope));
		float dockslopedeg = (float) ((180 / Math.PI) * Math.atan(dockslope));
		
		// relative-to-calibration target sizes for modes, constants
		// 6 in calibration:
//		final int s1 = (int) (dockw * dockh * 0.12  * w / h);  // (area) medium range start
//		final int s2 = (int) (dockw * dockh * 0.55 * w / h); // (area) close range start
//		final double slopetolerance = 0.8; // +/-
		// 2 in calibration:
		final int s1 = (int) (dockw * dockh * 0.07  * w / h);  // (area) medium range start
		final int s2 = (int) (dockw * dockh * 0.40 * w / h); // (area) close range start
		final double s2slopetolerance = 1.2; // 1.2
		final double s1slopetolerance = 1.3; // 1.3
		
		// optionally set breaking delay longer for fast bots

		final int s1FWDmilliseconds = 500; // 400
		final int s2FWDmilliseconds = 250; // 100
		
		if (w * h < s1) { // mode: quite far away yet, approach only

			if (state.getInteger(State.values.spotlightbrightness) > 0) {
				comport.setSpotLightBrightness(0);
			} 
			
			if (state.getInteger(State.values.floodlightlevel) == 0) comport.floodLight(FLHIGH);
//			if (Math.abs(x - imgwidth/2) > (int) (imgwidth*0.03125) || Math.abs(y - imgheight/2) > (int) (imgheight*0.104167)) { // clicksteer and go (y was >50)
			if (Math.abs(x - imgwidth/2) > (int) (imgwidth*0.07) )  { // clicksteer and go 
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
							Thread.sleep(ArduinoPrime.LINEAR_STOP_DELAY);
							dockGrab(dockgrabmodes.find, 0, 0);

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
							Thread.sleep(ArduinoPrime.LINEAR_STOP_DELAY); // let deaccelerate
							dockGrab(dockgrabmodes.find, 0, 0);
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

 				final double slopeDiffMax = 7.0;
				double slopeDiff = Math.abs(slopedeg - dockslopedeg);
				if (slopeDiff > slopeDiffMax)   slopeDiff = slopeDiffMax;

				if (slopeDiff > s1slopetolerance) {
					final double magicRatioMin = 0.04;
					final double magicRatioMax = 0.22;
					double magicRatio = magicRatioMax - (slopeDiff/slopeDiffMax)*(magicRatioMax-magicRatioMin);
					autodockcompdir = (int) (imgwidth/2 - w - (int) (imgwidth*magicRatio) - 
								Math.abs(imgwidth/2 - x)); // was 160 - w - 25 -Math.abs(160-x)
				}
				
				if (slope > dockslope) {
					autodockcompdir *= -1;
				} // approaching from left
				autodockcompdir += x + (dockx - imgwidth/2);
				// System.out.println("comp: "+autodockcompdir);
				lastcamctr = 0;
				if (Math.abs(autodockcompdir - dockx) > (int) (imgwidth*0.03125)) { // steer and go
					lastcamctr = (autodockcompdir - dockx) * rescomp;
					
					comport.clickSteer(lastcamctr , 0);
					new Thread(new Runnable() {
						public void run() {
							try {
								comport.delayWithVoltsComp(allowforClickSteer);
								comport.speedset(ArduinoPrime.speeds.fast.toString());
								comport.goForward();
//								Thread.sleep(s2FWDmilliseconds);
								comport.delayWithVoltsComp(s2FWDmilliseconds);
								comport.stopGoing();
								Thread.sleep(ArduinoPrime.LINEAR_STOP_DELAY); // let deaccelerate
								if (Math.abs(lastcamctr) > imgwidth/4) { // correct in case dock occluded by frame after large move
									comport.clickSteer(-lastcamctr , 0);
									comport.delayWithVoltsComp(allowforClickSteer);
								}
								dockGrab(dockgrabmodes.find, 0, 0);
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
								Thread.sleep(ArduinoPrime.LINEAR_STOP_DELAY); // let deaccelerate
								dockGrab(dockgrabmodes.find, 0, 0);
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}).start();
				}
			} else { // !autodockingcamctr
				autodockingcamctr = true;
				if (Math.abs(x - dockx) > (int) (0.03125*imgwidth) ) { 

					comport.clickSteer((x - dockx) * rescomp, (y - imgheight/2)
							* rescomp);
					new Thread(new Runnable() {
						public void run() {
							try {
								comport.delayWithVoltsComp(allowforClickSteer);
								dockGrab(dockgrabmodes.find, 0, 0);
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}).start();
				} else {
					dockGrab(dockgrabmodes.find, 0, 0);
				}
			}
		} 
		if (w * h >= s2) { // right in close, centering camera only, backup and try again if position wrong
			if ((Math.abs(x - dockx) > 3) && autodockctrattempts <= 10) {
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
							dockGrab(dockgrabmodes.find, 0, 0);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}).start();
			} else {
				if (Math.abs(slopedeg - dockslopedeg) > s2slopetolerance
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
								Thread.sleep(ArduinoPrime.LINEAR_STOP_DELAY); // let deaccelerate
								dockGrab(dockgrabmodes.find, 0, 0);
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}).start();
					
					Util.log("autodock backup", this);
					PowerLogger.append("autodock backup", this);
					
				} else { // all good, let er rip
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
					
					BufferedImage img = null;
					if (Application.framegrabimg != null) {

						// convert bytes to image
						ByteArrayInputStream in = new ByteArrayInputStream(Application.framegrabimg);
						img = ImageIO.read(in);
						in.close();
						
					}
						
					else if (Application.processedImage != null) {
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
					app.message("getlightlevel: " + Integer.toString(avg), null, null);
					
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();
	}

	public void dockGrab(final dockgrabmodes mode, final int x, final int y) {
		
		state.set(oculusPrime.State.values.dockgrabbusy, true);
		state.delete(oculusPrime.State.values.dockfound);
		state.delete(oculusPrime.State.values.dockmetrics);

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

						// convert bytes to image
						ByteArrayInputStream in = new ByteArrayInputStream(Application.framegrabimg);
						img = ImageIO.read(in);
						in.close();
						
					}
						
					else if (Application.processedImage != null) {
						img = Application.processedImage;
					}
					
					else { Util.log("dockgrab failure", this); return; }
						
					imgwidth= img.getWidth();
					imgheight= img.getHeight();
					rescomp = 640/imgwidth; // for clicksteer gui 640 window

					float[] matrix = { 0.111f, 0.111f, 0.111f, 0.111f,
							0.111f, 0.111f, 0.111f, 0.111f, 0.111f, };

					BufferedImageOp op = new ConvolveOp(new Kernel(3, 3, matrix));
					img = op.filter(img, new BufferedImage(imgwidth, imgheight, BufferedImage.TYPE_INT_ARGB));
					
					int[] argb = img.getRGB(0, 0, imgwidth, imgheight, null, 0, imgwidth);
					
		
					if (mode.equals(dockgrabmodes.calibrate)) {
						String[] results = oculusImage.findBlobStart(x, y, img.getWidth(), img.getHeight(), argb);
						autoDock(autodockmodes.dockgrabbed.toString()+" "+dockgrabmodes.calibrate.toString()+" " + results[0]
								+ " " + results[1] + " " + results[2] + " "
								+ results[3] + " " + results[4] + " "
								+ results[5] + " " + results[6] + " "
								+ results[7] + " " + results[8]);
					}
					if (mode.equals(dockgrabmodes.start)) {
						oculusImage.lastThreshhold = -1;
					}
					if (mode.equals(dockgrabmodes.find) || mode.equals(dockgrabmodes.start)) {
						String results[] = oculusImage.findBlobs(argb, imgwidth, imgheight);
                        String str = results[0] + " " + results[1] + " " + results[2] + " " +
                        		results[3] + " " + results[4];
						// results = x,y,width,height,slope
                        int width = Integer.parseInt(results[2]);

                        // interpret results
                        if (width < (int) (0.02*imgwidth) || width > (int) (0.875*imgwidth) || results[3].equals("0")) 
        					state.set(State.values.dockfound, false); // failed to find target! unrealistic widths
        				else { 
        					state.set(State.values.dockfound, true); // success!
        					state.set(State.values.dockmetrics, str);
        				}

                        if (state.getBoolean(State.values.autodocking))
//                        	autoDock("dockgrabbed find " + str);
                        	autoDock(autodockmodes.dockgrabbed.toString()+" "+dockgrabmodes.find.toString()+" "+str);
					}

					if (mode.equals(dockgrabmodes.test)) {
						oculusImage.lastThreshhold = -1;
						String results[] = oculusImage.findBlobs(argb, imgwidth, imgheight);
						int guix = Integer.parseInt(results[0])/(2/rescomp);
						int guiy = Integer.parseInt(results[1])/(2/rescomp);
						int guiw = Integer.parseInt(results[2])/(2/rescomp);
						int guih = Integer.parseInt(results[3])/(2/rescomp);
						String str = guix + " " + guiy + " " + guiw + " " + guih + " " + results[4];
						// results = x,y,width,height,slope
						
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
