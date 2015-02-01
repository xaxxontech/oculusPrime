package developer;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import oculusPrime.State;
import oculusPrime.Util;

public class ros {
	
	private static State state = State.getReference();
	public static final String ROSMAPINFO = "rosmapinfo";
	public static final String ROSAMCL = "rosamcl";
	public static final String ROSGLOBALPATH = "rosglobalpath";
	public static final String ROSSCAN = "rosscan";
//	public static final String ROSGOAL = "rosgoal";
	private static File lockfile = new File("/run/shm/map.raw.lock");
	private static BufferedImage map = null;
	private static double lastmapupdate = 0f;
	
	public static BufferedImage rosmapImg() {		 
		String mapinfo[] = state.get(ROSMAPINFO).split("_");
//		// width height res originx originy originth updatetime	
//		int width = Integer.parseInt(mapinfo[0]);
//		int height = Integer.parseInt(mapinfo[1]);

		if (map == null || Double.parseDouble(mapinfo[6]) > lastmapupdate) {
			Util.log("fetching new map");
			map = updateMapImg();
			lastmapupdate = Double.parseDouble(mapinfo[6]);
		}
		
		return map;
		
//		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
//		Graphics g = img.getGraphics();
//    	g.drawImage(map, 0, 0, null);
//	    g.dispose();
//		
//	    String odom[] = state.get(ROSODOM).split("_");  // x_y_th
//	    
//		double x = Double.parseDouble(mapinfo[3]) - Double.parseDouble(odom[0]);
//		x /= -Double.parseDouble(mapinfo[2]);
//		double y = Double.parseDouble(mapinfo[4]) - Double.parseDouble(odom[1]);
//		y /= -Double.parseDouble(mapinfo[2]);
//		y = height - y;
//		
//		Graphics2D g2d = img.createGraphics();
//		g2d.setColor(new Color(255,0,0));  
//		g2d.fill(new Rectangle2D.Double((int) x-5, (int) y-5, 10, 10));
//
//		return img;
	}
	
	private static BufferedImage updateMapImg() {
		String mapinfo[] = state.get(ROSMAPINFO).split("_");
		// width height res originx originy originth updatetime	
		int width = Integer.parseInt(mapinfo[0]);
		int height = Integer.parseInt(mapinfo[1]);
		
		int size = width * height;
		ByteBuffer frameData = null;
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

		//read file
		long start = System.currentTimeMillis();
		
		while (true) {
    		if (!lockfile.exists())  break;
       		long now = System.currentTimeMillis();
    		if (now - start > 5000) {
    			Util.debug("lockfile timeout");
    			return null; // 5 sec timeout
    		}
		}
		
    	try {
    		lockfile.createNewFile();
    		FileInputStream file = new FileInputStream("/run/shm/map.raw");
			FileChannel ch = file.getChannel();
			if (ch.size() == size) {
				frameData = ByteBuffer.allocate(size);
				ch.read(frameData);
				ch.close();
				file.close();
				lockfile.delete();
			}
			else { 
				Util.debug("frame size not matching");
				ch.close();
				file.close();
				lockfile.delete();
				return null;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

//    	Util.debug(Integer.toString(frameData.limit()));

    	// generate image
    	frameData.rewind();
		for(int y=height-1; y>=0; y--) {
			for(int x=0; x<width; x++) {
				
				int i = frameData.get();
//				Util.debug("POSITION: "+Integer.toString(frameData.position())+", x: "+Integer.toString(x)+
//					", y: "+Integer.toString(y));
				int argb = 0x000000;  // black
				if (i==0) argb = 0x555555; // grey 
				else if (i==100) argb = 0x00ff00; // green 
				
				img.setRGB(x, y, argb);    // flip horiz
				
			}
		}
		
		return img;
		
	}

	public static String mapinfo() {
		String str = "";
		if (state.exists(ROSMAPINFO)) str += state.get(ROSMAPINFO);
		if (state.exists(ROSAMCL)) str += " " + state.get(ROSAMCL);
		if (state.exists(ROSSCAN)) str += " " + state.get(ROSSCAN);
		if (state.exists(ROSGLOBALPATH)) str += " " + state.get(ROSGLOBALPATH);
		
		return str;
	}
}
