package developer.image;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import javax.imageio.ImageIO;

import oculusPrime.PlayerCommands;
import oculusPrime.commport.ArduinoPrime;
import org.red5.server.api.IConnection;
import org.red5.server.api.service.IServiceCapableConnection;

import oculusPrime.Application;
import oculusPrime.State;
import oculusPrime.Util;

public class motionDetect {
	private State state = State.getReference();
	private ImageUtils imageUtils = new ImageUtils();
	private IConnection grabber = null;
	private Application app = null;
	private int threshold;
	private int[] lastMassCtr=new int[2];
	
	public motionDetect(Application a, IConnection g, int t) {
		threshold = t;
		this.grabber = g;
		this.app = a;
		start();
	}
	
	private void start() {
		state.delete(State.values.streamactivity);
		state.set(State.values.motiondetect, true);

		
		new Thread(new Runnable() {
			public void run() {
				try {
					int frameno = 0;
					while (state.getBoolean(State.values.motiondetect)) { // TODO: time out after a while
						
						if(state.getBoolean(State.values.framegrabbusy.name()) || 
								 !(state.get(State.values.stream).equals(Application.streamstate.camera.toString()) ||
										 state.get(State.values.stream).equals(Application.streamstate.camandmic.toString()))) {

							app.message("framegrab busy or stream unavailable", null,null);
							state.set(State.values.motiondetect, false);
							return;
						}
						
						state.set(State.values.framegrabbusy.name(), true);
						IServiceCapableConnection sc = (IServiceCapableConnection) grabber;
						sc.invoke("framegrabMedium", new Object[] {});
						
						while (state.getBoolean(State.values.framegrabbusy)) {
							int n = 0;
							Thread.sleep(5);
							n++;
							if (n> 2000) {  // give up after 10 seconds 
								Util.debug("frame grab timed out", this);
								state.set(State.values.framegrabbusy, false);
								state.set(State.values.motiondetect, false);
								return;
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

						int[] greypxls = imageUtils.convertToGrey(img);
						int[] bwpxls = imageUtils.convertToBW(greypxls);

						int sensitivity = 4;
						int[] ctrxy = imageUtils.middleMass(bwpxls, img.getWidth(), img.getHeight(), sensitivity);
						if (frameno >= 1) { // ignore frames 0
							int compared = Math.abs(ctrxy[0]-lastMassCtr[0])+Math.abs(ctrxy[1]-lastMassCtr[1]);

							if (compared> threshold && state.getBoolean(State.values.motiondetect)) { //motion detected above noise level
//								lastMassCtr[0] = -1;
								state.set(State.values.motiondetect, false);
								if (!state.get(State.values.direction).equals(ArduinoPrime.direction.stop.toString())) {
									Util.log("error motion detect attempt while moving", true);
									return;
								}
								state.set(State.values.streamactivity, "video " + compared); // System.currentTimeMillis());
								app.driverCallServer(PlayerCommands.messageclients, "motion detected " + compared);
							}
						}
						lastMassCtr = ctrxy;
						frameno ++;

					}
					
				} catch (Exception e) { e.printStackTrace(); }
			}
		}).start();
	}
	

}
