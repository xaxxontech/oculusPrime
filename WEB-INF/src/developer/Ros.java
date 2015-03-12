package developer;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import oculusPrime.State;
import oculusPrime.Util;

public class Ros {
	
	private static State state = State.getReference();
	
	// State keys
	public static final String ROSMAPINFO = "rosmapinfo";
	public static final String ROSAMCL = "rosamcl";
	public static final String ROSGLOBALPATH = "rosglobalpath";
	public static final String ROSSCAN = "rosscan";
	public static final String ROSCURRENTGOAL = "roscurrentgoal";
	public static final String ROSMAPUPDATED = "rosmapupdated";
	public static final String ROSMAPWAYPOINTS = "rosmapwaypoints";
	public static final String NAVIGATIONENABLED ="navigationenabled";
	public static final String ROSSETGOAL = "rossetgoal";
	public static final String ROSGOALSTATUS = "rosgoalstatus";
	public static final String ROSGOALCANCEL = "rosgoalcancel";
	public static final String NAVIGATIONROUTE = "navigationroute";
	
	public static final String REMOTE_NAV = "remote_nav"; // nav launch file 
	public static final String ROSGOALSTATUS_SUCCEEDED = "succeeded";

	private static File lockfile = new File("/run/shm/map.raw.lock");
	private static BufferedImage map = null;
	private static double lastmapupdate = 0f;
	
	private static final String redhome = System.getenv("RED5_HOME");
	public static File waypointsfile = new File(redhome+"/conf/waypoints.txt");
	
	public static BufferedImage rosmapImg() {	
		if (!state.exists(ROSMAPINFO)) return null;
		
		String mapinfo[] = state.get(ROSMAPINFO).split(",");

		if (map == null || Double.parseDouble(mapinfo[6]) > lastmapupdate) {
			Util.log("fetching new map");
			map = updateMapImg();
			lastmapupdate = Double.parseDouble(mapinfo[6]);
		}
		
		return map;
		
	}
	
	private static BufferedImage updateMapImg() {
		String mapinfo[] = state.get(ROSMAPINFO).split(",");
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

    	// generate image
    	frameData.rewind();
		for(int y=height-1; y>=0; y--) {
			for(int x=0; x<width; x++) {
				
				int i = frameData.get();
				int argb = 0x000000;  // black
				if (i==0) argb = 0x555555; // grey 
				else if (i==100) argb = 0x00ff00; // green 
				
				img.setRGB(x, y, argb);    // flip horiz
				
			}
		}
		
		return img;
		
	}

	public static String mapinfo() { // send info to javascript
		String str = "";

		if (state.exists(ROSMAPINFO)) str += ROSMAPINFO+"_" + state.get(ROSMAPINFO);
		if (state.exists(ROSAMCL)) str += " " + ROSAMCL+"_" + state.get(ROSAMCL);
		if (state.exists(ROSSCAN)) str += " " + ROSSCAN+"_" + state.get(ROSSCAN);
		if (state.exists(ROSGLOBALPATH)) str += " " + ROSGLOBALPATH+"_" + state.get(ROSGLOBALPATH);
		if (state.exists(ROSCURRENTGOAL)) str += " " + ROSCURRENTGOAL+"_" + state.get(ROSCURRENTGOAL);

		if (state.exists(ROSMAPUPDATED)) {
			str += " " + ROSMAPUPDATED +"_" + state.get(ROSMAPUPDATED);
			state.delete(ROSMAPUPDATED);
		}
		if (state.exists(ROSMAPWAYPOINTS)) {
			str += " " + ROSMAPWAYPOINTS +"_" + state.get(ROSMAPWAYPOINTS);
			state.delete(ROSMAPWAYPOINTS);
		}
		
		return str;
	}
	
	public static void launch(String launch) {
		String sep = System.getProperty("file.separator");
		String cmd = System.getenv("RED5_HOME")+sep+"ros.sh"; // setup ros environment
		cmd += " roslaunch oculusprime "+launch+".launch";
		Util.systemCall(cmd);
	}
	
	public static void savewaypoints(String str) {
		try {
			FileWriter fw = new FileWriter(waypointsfile);						
			fw.append(str+"\r\n");
			fw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void loadwaypoints() {
			state.delete(ROSMAPWAYPOINTS);
			if (!state.exists(ROSMAPINFO)) return;
			
			BufferedReader reader;
			String str = "";
			try {
				reader = new BufferedReader(new FileReader(waypointsfile));
				str = reader.readLine();
				reader.close();

			} catch (FileNotFoundException e) {
				Util.debug("no waypoints file yet");
				return;
			} catch (IOException e) {
				e.printStackTrace();
			}
			str = str.trim();
			if (!str.equals("")) state.set(ROSMAPWAYPOINTS, str.trim());			
	}
	
	public static boolean setWaypointAsGoal(String str) {
		loadwaypoints();
		if (!state.exists(ROSMAPWAYPOINTS)) return false;
		
		boolean result = false;
		
		state.delete(ROSCURRENTGOAL);
		
		// try matching name
		String waypoints[] = state.get(ROSMAPWAYPOINTS).split(",");
		for (int i = 0 ; i < waypoints.length -3 ; i+=4) {
			if (waypoints[i].replaceAll("&nbsp;", " ").equals(str)) {
				state.set(ROSSETGOAL, waypoints[i+1]+","+waypoints[i+2]+","+waypoints[i+3]);
				result = true;
				break;
			}
		}
		
		// if no name match, try coordinates
		if (!result) {
			String coordinates[] = str.split(",");
			if (coordinates.length == 3) {
				state.set(ROSSETGOAL, coordinates[0]+","+coordinates[1]+","+coordinates[2]);
				result = true;
			}
		}
		
		state.delete(ROSMAPWAYPOINTS);
		return result;
	}
	
}
