package developer;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;

import oculus.Application;
import oculus.Observer;
import oculus.Settings;
import oculus.State;
import oculus.Util;

import java.io.IOException;
import java.nio.ShortBuffer;
import java.util.Date;
import java.util.Vector;

import org.OpenNI.*;

public class MotionTracker implements IObserver<ErrorStateEventArgs>, Observer {

	protected static final int MAX_SIZE = 5;
	protected static final long START_UP_DELAY = 5000;
	protected static final long POLL_DELAY = 300;

	//private static final Integer TOO_CLOSE = 400;
	private static final Integer THRESHOLD = 5;
	private static MotionTracker singleton = null;
	private State state = State.getReference();

	// keep a first in, lasts out buffer of frames
	private Vector<byte[]> frames = new Vector<byte[]>(MAX_SIZE);
	private Vector<Long> meta = new Vector<Long>(MAX_SIZE);

	private boolean running = false;
	private Context context;
	private DepthGenerator depth;
	private DepthMetaData depthMD;
	private int xRes = 0;
	private int yRes = 0;

	private int pollDelay = 300;
	
	private Application app = null;
	private int center = 0;

	/** */
	public static MotionTracker getReference() {
		if (singleton == null) singleton = new MotionTracker();
		return singleton;
	}
	
	/** */
	public void setApp(Application a) {
		app = a;
	}
	
	/** */
	private MotionTracker() {

		String sep = "\\";
		if (Settings.os.equals("linux")) sep = "/";
		String SAMPLES_XML = System.getenv("RED5_HOME") + sep + "webapps" + sep + "oculus" + sep + "openNIconfig.xml";

		try {

			OutArg<ScriptNode> scriptNodeArg = new OutArg<ScriptNode>();
			context = Context.createFromXmlFile(SAMPLES_XML, scriptNodeArg);
			context.getErrorStateChangedEvent().addObserver(this);
			depth = (DepthGenerator) context.findExistingNode(NodeType.DEPTH);
			depthMD = new DepthMetaData();

		} catch (Throwable e) {
			Util.debug("constructor: " + e.getLocalizedMessage(), this);
			return;
		}

		if (depth == null) {
			Util.log("can't get dept cam, fatal", this);
			return;
		}

		// setup must be done before any reads
		depth.getMetaData(depthMD);
		xRes = depthMD.getXRes();
		yRes = depthMD.getYRes();
		Util.debug("depth cam start up, xRes: " + xRes + " yRes: " + yRes, this);
		start();
	}

	/** */
	public void stop() {
		try {
			running = false;
			depth.stopGenerating();
		} catch (StatusException e) {
			Util.debug("stop(): " + e.getLocalizedMessage(), this);
		}
	}
	
	/** */
	public boolean isRunning() {
		return running;
	}

	/** */
	public void setPollDelay(int delay) {
		if (delay > 100)//// TODO: SET MIN? ...  POLL_DELAY)
			pollDelay = delay;
	}

	/** */
	public void start() {
		
		if (depth == null) {
			Util.log("can't get dept cam, fatal", this);
			return;
		}

		state.addObserver(this);
		running = true;
		new Thread(new Runnable() {

			@Override
			public void run() {

				Util.delay(START_UP_DELAY);
				
				// Util.debug("depth cam start up, xRes: " + xRes + " yRes: " + yRes, this);

				// read at fixed rate
				while (running) {

					Util.delay(pollDelay);

					updateCenter();
					
					// if(i++ % 5 == 0) save("test.png");
					
					/*
					int[][] frame = getFrame();
					
					pixel close = getNext(frame, 9999); 
					if(close.x != -1){
						
						pixel two = getNext(frame, close.z+1);
						
						Util.log(" close, x: " + close.x + " y: " + close.y + " z: " + close.z, this);
						Util.log(" two,   x: " + two.x   + " y: " + two.y   + " z: " + two.z, this);
					}

*/
					
				//	if( close.z > 400 ) save("_test.png");

					// push out oldest record
					if (frames.size() == frames.capacity()) frames.removeElementAt(0);
					if (meta.size() == meta.capacity()) meta.removeElementAt(0);

					// get new frame
					frames.add(getDepth());
					meta.add(System.currentTimeMillis());

				}
			}
		}).start();
	}

	/** send current center point to state. (distance in mm) */
	private void updateCenter() {
		int center = depthMD.getData().readPixel(xRes / 2, yRes / 2);
		if (center != state.getInteger(State.values.centerpoint))
			state.set(State.values.centerpoint, center);
	}

	/** */
	private float[] calcHist() {

		float histogram[] = new float[xRes * yRes];
		for (int i = 0; i < histogram.length; ++i) histogram[i] = 0;
		ShortBuffer depth = depthMD.getData().createShortBuffer();
		depth.rewind();

		int points = 0;
		while (depth.remaining() > 0) {
			short depthVal = depth.get();
			if (depthVal != 0) {
				histogram[depthVal]++;
				points++;
			}
		}

		for (int i = 1; i < histogram.length; i++)
			histogram[i] += histogram[i - 1];

		if (points > 0) {
			for (int i = 1; i < histogram.length; i++) {
				histogram[i] = (int) (256 * (1.0f - (histogram[i] / (float) points)));
			}
		}

		return histogram;
	}

	/** */
	private byte[] getDepth() {

		byte[] imgbytes = new byte[xRes * yRes];
		try {

			context.waitAnyUpdateAll();
			float[] histogram = calcHist();
			ShortBuffer depth = depthMD.getData().createShortBuffer();
			depth.rewind();

			while (depth.remaining() > 0) {
				int pos = depth.position();
				short pixel = depth.get();
				imgbytes[pos] = (byte) histogram[pixel];
			}
		} catch (GeneralException e) {
			Util.log("updateDepth(): " + e.getLocalizedMessage(), this);
		}

		return imgbytes;
	}

	
	/**
	 * used to test, send images via servlett
	 * 
	 * @param takes
	 *            an integer to index into stored frames in buffer
	 * @return an image created from the buffer
	 */
	public BufferedImage getHistogram(final int i) {
		
		// +35 pixels to make room for data overlay. 
		BufferedImage bimg = new BufferedImage((Integer) xRes, (Integer)yRes/*+38*/, BufferedImage.TYPE_BYTE_GRAY);
		
		// sanity test
		// if(frames.size() - i >= 0) return bimg;
		
		if(frames.size() == (MAX_SIZE-1)) return bimg;
		
		
		DataBufferByte dataBuffer = new DataBufferByte(frames.get(frames.size() - i), xRes * yRes /*+38*/ );
		Raster raster = Raster.createPackedRaster(dataBuffer, xRes, yRes, 8, null);
		bimg.setData(raster);

		// TODO:
		Graphics2D g = bimg.createGraphics();
		
		// g.setFont(new Font("SansSerif", Font.BOLD, 16));
		// g.drawString("[" + i + "] " + new Date(meta.get(meta.size() - 1)) + " delay: " + pollDelay, 15, 15);
		// g.setColor(java.awt.Color.red);
		
		
		g.drawString("[" + i + "] " + new Date(meta.get(meta.size() - 1)), 15, yRes); //+15);
		g.drawString("center: " + state.get(State.values.centerpoint) + " delay: " + pollDelay , 15, yRes); // +30);
		 
		return bimg;
	}

	/** 
	 * 
	 * @param filename
	 */
	 public void save(final String filename){
	  
	 
		 // TODO: remove later.. 
		 long start = System.currentTimeMillis();
	  
		  BufferedImage bimg = new BufferedImage((Integer) xRes, yRes,
		  BufferedImage.TYPE_BYTE_GRAY); DataBufferByte dataBuffer = new
		  DataBufferByte(getDepth(), xRes*yRes); Raster raster =
		  Raster.createPackedRaster(dataBuffer, xRes, yRes, 8, null);
		  bimg.setData(raster);
		  
		  java.io.File outputfile = new java.io.File(filename); 
		  try {
			  javax.imageio.ImageIO.write(bimg, "png", outputfile); 
		  } catch(IOException e) {
			  Util.log("save(): " + e.getLocalizedMessage(), this); 
		  }
	  
		  // takes under 100ms 
		  Util.log("saved to file: " + outputfile.getAbsolutePath() + " took: "+ (System.currentTimeMillis()-start)+" ms");
	  
	  }
	 
	
	

	/** @return the current frame as an array of distance values in mm */
	public int[][] getFrame() {

		// TODO: remove later.. range is 8ms to 20m on my dell
		// long start = System.currentTimeMillis();

		int frame[][] = new int[xRes][yRes];
		for (int x = 0; x < xRes; x++) {
			for (int y = 0; y < yRes; y++) {
				frame[x][y] = depthMD.getData().readPixel(x, y);
			}
		}

		// TODO: use enum
		// /state.set("readTime", (System.currentTimeMillis()-start)+" ms");

		return frame;
	}

	
	
	
	
	/**
	 * subtract pixel for pixel public int[][] diff(final int[][] a, final
	 * int[][] b){ int frame[][] = new int[xRes][yRes]; for(int x = 0; x < xRes
	 * ; x++){ for(int y = 0; y < yRes ; y++){ frame[x][y] = a[x][y] - b[x][y];
	 * } } return frame; }
	 */

	/**
	 * subtract pixel for pixel public Vector difference(final int[][] a, final
	 * int[][] b){ //int frame[][] = new int[xRes][yRes]; Vector section = new
	 * Vector(); for(int x = 0; x < xRes ; x++){ for(int y = 0; y < yRes ; y++){
	 * section.add(new pixel(x,y, a[x][y] - b[x][y])); } } return section; }
	 */

	
	/**
	 * 
	 * @param frame
	 * @param close
	 * @return
	 */
	public pixel getNext(){
		/*
		pixel result = new pixel(0, 0, 0);
		byte[] frame = getDepth();
		byte max = 0;
		for(int i = 0 ; i < frame.length ; i++){
			if(frame[i] > max){
				max = frame[i];
				result = new pixel(i, (i%8), max);
			}
		}
		Util.log("close, x: " + result.x + " y: " + result.y + " z: " + result.x, this);
		return result;
		*/
		
		int zero = 0;
		int value = 0;
		int[][] frame = getFrame();
		pixel result = new pixel(0, 0, 9999);
		for (int x = 0; x < xRes; x++) {
			for (int y = 0; y < yRes; y++) {
				if(frame[x][y] != 0){
					
					value++;
					if(frame[x][y] < result.z) { 
						result = new pixel(x, y, frame[x][y]);
						//Util.log("larger found: " + frame[x][y], this);
						
					}
				
				} else zero++;
			}
		}
		
		if(value>0) Util.log("zeros: " + zero + " values: " + value + " close, x: " + result.x + " y: " + result.y + " z: " + result.z, this);
		
		return result;
		
		
	}
	
	public pixel getNext(final int[][] frame, final int start ){
		
		pixel result = new pixel(-1, -1, start);
		for (int x = 0; x < xRes; x++) {
			for (int y = 0; y < yRes; y++) {
				if(frame[x][y] != 0){
					if(frame[x][y] < result.z) { 
						result = new pixel(x, y, frame[x][y]);
					}
				} 
			}
		}
		
		// if(value>0) Util.log("zeros: " + zero + " values: " + value + " close, x: " + result.x + " y: " + result.y + " z: " + result.x, this);
		
		return result;
		
		
	}
	
	
	
	/**use when holding an array of coords and values  */
	public class pixel { 
	
		public int z = 0; 
		public int x = 0; 
		public int y = 0;
		
	  
		public pixel(int xval, int yval, int zval){ 
			z = zval; y = yval; x = xval;
		}
	}
	 

	/**
	 * public void printFrame(final int[][] frame){ for(int y = 0; y < yRes ;
	 * y+=10) Util.log("y: " + y + " " + getRow(frame, y), this); }
	 */

	/**
	 * public String getRow(final int[][] frame, final int y){ String line = "";
	 * for(int x = 0; x < xRes ; x++) line += frame[x][y] + " ";
	 * 
	 * return line; }
	 */

	
	
	@Override
	public void updated(final String key) {
		
		// Util.log("state changed: " + key + " value: " + state.get(key),
		// this); if(key.equals(State.values.centerpoint.name())){
		/*
		if (key.equals(State.values.motioncommand.name())) {
			
			Util.log("motion command in..", this);
			
		}
		*/
		
		if (key.equals(State.values.centerpoint.name())) {
			
			Integer distance = state.getInteger(key);

			if (distance == 0) {
				if(app!=null) app.message("..TOO CLOSE", null, null);
				return;
			}

			// if (distance > TOO_CLOSE) {
				if (Math.abs(center - distance) > THRESHOLD) {

					String dir;
					if (center > distance) dir = " <b>_closer_</b>";
					else dir = " _backing up_";
					
					
					// TODO: COLIN... write to js (text line on flash output) 
					app.message("center: " + distance + " delta: " + Math.abs(center - distance) + dir, null, null);

					center = distance;
				}
			// }
		}
	}

	@Override
	public void update(IObservable<ErrorStateEventArgs> arg0, ErrorStateEventArgs arg1) {
		Util.log("Global error state has changed: " + arg1.getCurrentError(),this);
		System.exit(1);
	}
}
