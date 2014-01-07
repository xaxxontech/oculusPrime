package developer.depth;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.Map;

import developer.image.ImageUtils;
import oculusPrime.Util;


public class OpenNIRead  {
	
	private boolean depthCamInit = false;
	public  boolean depthCamGenerating = false;
	private ByteBuffer frameData;
	private int[] lastresult;
	Process camproc;
	File lockfile = new File("/run/shm/xtion.raw.lock");
	ImageUtils imageUtils = new ImageUtils();
	private int width = 320;
	private int height = 240;
	
//	public OpenNIRead()  {
//	}
	
	public void startDepthCam() {
		if (depthCamInit) return;
		oculusPrime.Util.log("start depth cam", this);

		lockfile.delete();
		
		new Thread(new Runnable() { 
			public void run() {
				try {
					
					String sep = System.getProperty("file.separator");
					String dir = System.getenv("RED5_HOME")+sep+"xtionread";
					String javadir = System.getProperty("java.home");
					String cmd = javadir+sep+"bin"+sep+"java"; 
					String arg = dir+sep+"xtion.jar";
					ProcessBuilder pb = new ProcessBuilder(cmd, "-jar", arg);
					Map<String, String> env = pb.environment();
					env.put("LD_LIBRARY_PATH", dir);
					camproc = pb.start();
					
				} catch (Exception e) {
					e.printStackTrace();
				}		
			} 	
		}).start();

		depthCamGenerating = true;
		depthCamInit = true;
		
	}
	
	public void stopDepthCam()  {
		if (!depthCamInit) return;
		oculusPrime.Util.log("stop depth cam", this);

		camproc.destroy();
		
		depthCamInit = false;
		depthCamGenerating = false;
	}
	
	public int[] readHorizDepth(int y) {

		int[]result = new int[width];
		int size = width*height*2;
		
		getFrame(size);

    	boolean blank=true;
		for (int x=0; x<width; x++) {
	        
	        int p = ((width * y)+x)*2;
	        int depth = (int) frameData.getShort(p);
	        result[x] = depth;
	        
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
		int size = width*height*2;

		boolean blank=true;
    	while (true) {
    		boolean rcpt = getFrame(size);
	    	int i = 0;
	    	for (int y=0; y<height; y++) {
				for (int x=0; x<width; x++) {
			        
			        int p = ((width * y)+x)*2;
			        short depth = frameData.getShort(p);
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
	
	public short[] readFullFrameAveraged() {
		short[] result = new short[width*height];
		int max = 5;
		int size = width*height*2;

		short[][] frames = new short[width*height][max];
    	for (int f=0; f<max; f++) {
    		getFrame(size);
	    	int i = 0;
			for (int y=0; y<height; y++) {
				for (int x=0; x<width; x++) {
			        
			        int p = ((width * y)+x)*2;
			        short depth = frameData.getShort(p);
			        frames[i][f] = depth;
			        i++;
				}	
	    	}
			Util.delay(100);
    	}

    	for (int i=0; i<width*height; i++) {
			int total = 0;
			int compared = 0;
			for (int f=0;f<max;f++) {
				if (frames[i][f] != 0) {
					total += frames[i][f];
					compared ++;
				}
			}
			if (compared != 0) {
				result[i] = (short) (total/compared);
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
    		if (now - start > 5000) return false; // 5 sec timeout
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
			ch.close();
			file.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
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
//				int hue = depth[x + y*width];
//				if (hue > maxDepthInMM)	hue = maxDepthInMM;
//				if (hue != 0) {
//					hue = 255 - (int) ((float) (hue)/maxDepthInMM * 255f);
//				}
//				int argb = (hue<<16) + (0<<8) + hue;
				
				
				short d = depth[x + y*width];
				if (d > maxDepthInMM)	d = 0;
				int red = d  >> 8;
				red *=8;
				int blue=0;
				if (d != 0) blue = 255 - (int) ((float) (d)/maxDepthInMM * 255f);
				int argb = (red<<16) + (0<<8) + blue;
				
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
	
}
