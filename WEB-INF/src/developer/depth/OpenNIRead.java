package developer.depth;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.Map;

import developer.Ros;
import developer.image.ImageUtils;
import oculusPrime.Application;
import oculusPrime.PlayerCommands;
import oculusPrime.Util;


public class OpenNIRead  {
	
	private boolean depthCamInit = false;
	public  boolean depthCamGenerating = false;
	private static ByteBuffer frameData;
	private int[] lastresult;
	Process camproc;
	File lockfile = new File("/run/shm/xtion.raw.lock");
	ImageUtils imageUtils = new ImageUtils();
	private static int width = 320;
	private static int height = 240;
	private final static int BYTEDEPTH = 2;
	
	public boolean startDepthCam() {
		if (depthCamInit) return false;
		oculusPrime.Util.log("START ros openni 320x240", this);

		lockfile.delete();
		
//		new Thread(new Runnable() { 
//			public void run() {
//				try {
//					
//					String sep = System.getProperty("file.separator");
//					String dir = System.getenv("RED5_HOME")+sep+"xtionread";
//					String javadir = System.getProperty("java.home");
//					String cmd = javadir+sep+"bin"+sep+"java"; 
//					String arg = dir+sep+"xtion.jar";
//					ProcessBuilder pb = new ProcessBuilder(cmd, "-jar", arg);
//					Map<String, String> env = pb.environment();
//					env.put("LD_LIBRARY_PATH", dir);
//					camproc = pb.start();
//					
//				} catch (Exception e) {
//					e.printStackTrace();
//				}		
//			} 	
//		}).start();
		
//		String cmd = Application.RED5_HOME+Util.sep+"ros.sh"; // setup ros environment
//		cmd += " roslaunch oculusprime depthcam_to_java.launch";
//		Util.systemCall(cmd);

		if (!Ros.launch("depthcam_to_java")) return false;

		depthCamGenerating = true;
		depthCamInit = true;
		return true;
		
	}
	
	public void stopDepthCam()  {
		if (!depthCamInit) return;
		oculusPrime.Util.log("STOP ros openni 320x240", this);

		Util.systemCall("pkill roslaunch");
		
		depthCamInit = false;
		depthCamGenerating = false;
	}
	
	public int[] readHorizDepth(int y) {

		int[]result = new int[width];
		int size = width*height*BYTEDEPTH;
		
		getFrame(size);

    	boolean blank=true;
//		for (int x=0; x<width; x++) {
    	int i = 0;
		for (int x=width-1; x>=0; x--) {
	        
	        int p = ((width * y)+x)*BYTEDEPTH;

//	        float depth = frameData.getFloat(p); // reads 4 bytes
//	        result[i] = (short) (depth*1000);
	        
	        short depth = frameData.getShort(p); // reads 2 bytes
	        result[i] = depth;
	        
	        i++;
	        
	        if (depth != 0) { blank = false; }
		}
		

		if (blank) { return lastresult; }
		else { 
			lastresult = result;
			return result; 
		}
		
		
	}
	
	public short[] readFullFrame() {
		
		short[] result = new short[width*height];
		int size = width*height*BYTEDEPTH;

		boolean blank=true;
    	while (true) {
    		boolean rcpt = getFrame(size);
	    	int i = 0;
	    	for (int y=0; y<height; y++) {
				for (int x=width-1; x>=0; x--) {
			        
			        int p = ((width * y)+x)*BYTEDEPTH;
//			        float depth = frameData.getFloat(p); // reads 4 bytes
//			        result[i] = (short) (depth*1000); // convert to mm
			        short depth = frameData.getShort(p); // reads 2 bytes
			        result[i] = depth;
			        i++;
			        if (depth != 0) { blank = false; }
				}	
	    	}
	    	if (!blank && rcpt) break;
	    	else {
	    		if (blank) Util.debug("depth frame blank", this);
	    		if (!rcpt) Util.debug("depth frame not rcvd", this);
	    	}
    	}
    	
		return result; 
		
	}

	private boolean getFrame(int size) {
		long start = System.currentTimeMillis();
		
		while (true) {
    		if (!lockfile.exists()) {
    			break;
    		}
    		long now = System.currentTimeMillis();
    		if (now - start > 5000) {
    			Util.debug("lockfile timeout", this);
    			return false; // 5 sec timeout
    		}
		}
		
    	try {
    		lockfile.createNewFile();
    		FileInputStream file = new FileInputStream("/run/shm/xtion.raw");
			FileChannel ch = file.getChannel();
			if (ch.size() == size) {
				frameData = ByteBuffer.allocate((int) size);
				ch.read(frameData.order(ByteOrder.LITTLE_ENDIAN));
				ch.close();
				file.close();
				lockfile.delete();
				return true;
			}
			else Util.debug("frame size not matching", this);
			ch.close();
			file.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public BufferedImage generateDepthFrameImg() {
		short[] depth = readFullFrame();
		final int maxDepthInMM = 5000; // 3500
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
//		Graphics2D g2d = img.createGraphics();
		
		for(int y=0; y<height; y++) {
//			for (int x=width-1; x>=0; x--) { 
			for(int x=0; x<width; x++) {
				
				int hue = depth[x + y*width];
				if (hue > maxDepthInMM)	hue = maxDepthInMM;
				if (hue != 0) {
					hue = 255 - (int) ((float) (hue)/maxDepthInMM * 255f);
				}
				int argb = (hue<<16) + (0<<8) + hue;
				
				
//				short d = depth[x + y*width];
//				if (d > maxDepthInMM)	d = 0;
//				int red = d  >> 8;
//				red *=8;
//				int blue=0;
//				if (d != 0) blue = 255 - (int) ((float) (d)/maxDepthInMM * 255f);
//				int argb = (red<<16) + (0<<8) + blue;
				
				img.setRGB(width-x-1, y, argb);    // flip horiz
			}
		}
		
//		int[] greypxls = imageUtils.convertToGrey(img);
//		int[] edgepxls = imageUtils.edges(greypxls, img.getWidth(), img.getHeight());
//		BufferedImage edgeimg = imageUtils.intToImage(edgepxls, img.getWidth(), img.getHeight());
//		
//		return edgeimg;
		return img;
	}
	
	
	public static void main(String s[]) {
		OpenNIRead read = new OpenNIRead();
		read.startDepthCam();
		int size = width*height*BYTEDEPTH;
		read.getFrame(size);
		System.out.println(frameData.capacity());
	}
	
}

