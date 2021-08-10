package developer;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import oculusPrime.*;


public class Ros {
	
	private static State state = State.getReference();
    private static Settings settings = Settings.getReference();
	
	// State keys
	public enum navsystemstate { stopped, starting, running, mapping, stopping };

	public static final long ROSSHUTDOWNDELAY = 15000;

	public static final String ROSGOALSTATUS_SUCCEEDED = "succeeded";

	private static File lockfile = new File("/run/shm/map.raw.lock");
	private static BufferedImage map = null;
	private static double lastmapupdate = 0f;
	
	public static File waypointsfile = new File( Settings.tomcathome +"/conf/waypoints.txt");
	public static String mapfilename = "map.pgm";
	public static String mapyamlname = "map.yaml";

	public static String rospackagedir;
	public static final String ROSPACKAGE = "oculusprime";

	// launch file name constants:
	public static final String REMOTE_NAV = "remote_nav"; // nav
	public static final String MAKE_MAP = "make_map"; // mapping
	public static final String REMOTE_NAV_LIDAR = "remote_nav_lidar"; // nav launch file with lidar
	public static final String MAKE_MAP_LIDAR = "make_map_lidar"; // mapping launch file with lidar
	public static final String CAMERA = "camera"; // usb cam
	public static final String WEBRTC = "webrtc"; // gstreamer webrtc

    // node file name constants
    public static final String IMAGE_TO_SHM = "image_to_shm.py";

	public static final String ROS1CMD = Settings.tomcathome +Util.sep+"ros1.sh";
	public static final String ROS2CMD = Settings.tomcathome +Util.sep+"ros2.sh";


	public static BufferedImage rosmapImg() {	
		if (!state.exists(State.values.rosmapinfo)) return null;
		
		String mapinfo[] = state.get(State.values.rosmapinfo).split(",");

		if (map == null || Double.parseDouble(mapinfo[6]) > lastmapupdate) {
			Util.log("Ros.rosmapImg(): fetching new map", "");
			map = updateMapImg();
			lastmapupdate = Double.parseDouble(mapinfo[6]);
		}
		
		return map;
	}
	
	private static BufferedImage updateMapImg() {
		String mapinfo[] = state.get(State.values.rosmapinfo).split(",");
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
    			return null; // 5 sec timeout
    		}
			Util.delay(1);
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

		if (state.exists(State.values.rosmapinfo)) 
			str += State.values.rosmapinfo.toString()+"_" +	state.get(State.values.rosmapinfo);
		
		if (state.get(State.values.dockstatus).equals(AutoDock.DOCKED)) {
			str += " " + State.values.rosamcl.toString()+"_" + "0,0,0,0,0,0";
		}
		else if (state.exists(State.values.rosamcl)) 
			str += " " + State.values.rosamcl.toString()+"_" + state.get(State.values.rosamcl);
		
		if (state.exists(State.values.rosscan)) 
			str += " " + State.values.rosscan.toString()+"_" + state.get(State.values.rosscan);

		if (state.exists(State.values.rosglobalpath)) 
			str += " " + State.values.rosglobalpath.toString()+"_" + state.get(State.values.rosglobalpath);
		
		if (state.exists(State.values.roscurrentgoal)) 
			str += " " + State.values.roscurrentgoal.toString()+"_" + state.get(State.values.roscurrentgoal);

		if (state.exists(State.values.rosmapupdated)) {
			str += " " + State.values.rosmapupdated.toString() +"_" + state.get(State.values.rosmapupdated);
			state.delete(State.values.rosmapupdated);
		}

		if (state.exists(State.values.rosmapwaypoints)) 
			str += " " + State.values.rosmapwaypoints.toString() +"_" + state.get(State.values.rosmapwaypoints);

		if (state.exists(State.values.navsystemstatus))
			str += " " + State.values.navsystemstatus.toString() +"_"+ state.get(State.values.navsystemstatus);

		if (state.exists(State.values.navigationroute))
			str += " " + State.values.navigationroute.toString() + "_" +
					state.get(State.values.navigationroute).replaceAll(" ", "&nbsp;");

		if (state.exists(State.values.nextroutetime)) {
			long now = System.currentTimeMillis();
			if (state.getLong(State.values.nextroutetime) > now) {
				str += " " + State.values.nextroutetime.toString() + "_" +
						String.valueOf((int) ((state.getLong(State.values.nextroutetime)-now)/1000));
			}
			else state.delete(State.values.nextroutetime);
		}

		str += " lidar_"+settings.getBoolean(ManualSettings.lidar);

		return str;
	}


	public static String launch(String arg) {
		ArrayList <String> str = new ArrayList<>();
		str.add(arg);
		return launch(str);
	}


	public static String launch(List <String> strarray) {

		List<String> args = new ArrayList<>(strarray); // to prevent modifying original

		ProcessBuilder processBuilder = new ProcessBuilder();

		if (settings.getBoolean(ManualSettings.ros2)) {
			args.set(0, args.get(0) + ".launch.py");
			args.add(0, ROSPACKAGE); // 4th
			args.add(0, "launch"); // 3rd
			args.add(0, "ros2"); // 2nd
		}
		else{
			args.set(0, args.get(0) + ".launch");
			args.add(0, ROSPACKAGE); // 3rd
			args.add(0, "roslaunch"); // 2nd
		}

		// return string should be file.launch + args
		String pstring = "";
		int i = 0;
		while (i < args.size()) {
			pstring += args.get(i)+" ";
			i++;
		}
		pstring = pstring.trim();

		if (settings.getBoolean(ManualSettings.ros2))
			args.add(0, ROS2CMD); // 1st
		else
			args.add(0, ROS1CMD); // 1st

		processBuilder.command(args);

		try {
			Process proc = processBuilder.start();
		} catch (Exception e) { e.printStackTrace(); }

		Util.debug("Ros.launch PString: "+pstring, "Ros.launch()");
		return pstring;
	}


	public static void killlaunch(String str) {

		List<String> items = new ArrayList<>();

		items.add("pkill");
		items.add("-2");  // signal 2=SIGINT instead of default 15=SIGTERM
		items.add("-f");
		items.add(str);
		Util.debug(items.get(3), "Ros.killlaunch()");

		ProcessBuilder processBuilder = new ProcessBuilder();
		processBuilder.command(items);
		try { processBuilder.start(); }
		catch (Exception e) { e.printStackTrace(); }

	}


	public static void roscommand(String str) {
		Util.debug("Ros.java roscommand: "+str, null);

		String cmd = ROS1CMD;
		if (settings.getBoolean(ManualSettings.ros2))
			cmd = ROS2CMD;

		cmd += " " + str;
		Util.systemCall(cmd);
	}


	public static void savewaypoints(String str) {
		if (str.trim().equals(""))
			state.delete(State.values.rosmapwaypoints);
		else
			state.set(State.values.rosmapwaypoints, str);

		try {
			FileWriter fw = new FileWriter(waypointsfile);						
			fw.append(str+"\r\n");
			fw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void loadwaypoints() {
		state.delete(State.values.rosmapwaypoints);
//			if (!state.exists(State.values.rosmapinfo)) return;

		BufferedReader reader;
		String str = "";
		try {
			reader = new BufferedReader(new FileReader(waypointsfile));
			str = reader.readLine();
			reader.close();

		} catch (FileNotFoundException e) {
			return;
		} catch (IOException e) {
			e.printStackTrace();
		}
		str = str.trim();
		if (!str.equals("")) state.set(State.values.rosmapwaypoints, str);
	}
	
	public static boolean setWaypointAsGoal(String str) {
		boolean result = false;
		state.delete(State.values.roscurrentgoal);
		str=str.trim();
		
		// try matching name
		if (state.exists(State.values.rosmapwaypoints)) {
			String waypoints[] = state.get(State.values.rosmapwaypoints).split(",");
			for (int i = 0; i < waypoints.length - 3; i += 4) {
				if (waypoints[i].replaceAll("&nbsp;", " ").trim().equals(str)) {
					state.set(State.values.rossetgoal, waypoints[i + 1] + "," + waypoints[i + 2] + "," + waypoints[i + 3]);
					result = true;
					state.set(State.values.roswaypoint, str);
					break;
				}
			}
		}
		
		// if no name match, try coordinates
		if (!result) {
			String coordinates[] = str.split(",");
			if (coordinates.length == 3) {
				state.set(State.values.rossetgoal, coordinates[0]+","+coordinates[1]+","+coordinates[2]);
				result = true;
			}
		}
		
		return result;
	}

	public static String getRosPackageDir() {
		try {

			String[] cmd = { "bash", "-ic", "roscd "+ROSPACKAGE+" ; pwd" };
			Process proc = Runtime.getRuntime().exec(cmd);
			BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			String str = procReader.readLine();
			return str;

		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public static String getMapFilePath() {
		return Ros.rospackagedir + Util.sep + "maps" + Util.sep ;
	}

	public static void backUpMappgm() {
		String mapfilepath = getMapFilePath();
		File oldname = new File(mapfilepath+mapfilename);
		if (oldname.exists()) {
			File newname = new File(mapfilepath + Util.getDateStamp() + "_" + mapfilename);
			oldname.renameTo(newname);
		}
	}

	public static void backUpYaml() {
		String mapfilepath = getMapFilePath();
		File oldname = new File(mapfilepath+mapyamlname);
		if (oldname.exists()) {
			File newname = new File(mapfilepath + Util.getDateStamp() + "_" + mapyamlname);
			oldname.renameTo(newname);
		}
	}

	public static boolean saveMap() {
		try {
			// nuke any existing files in root dir
			Path sourcepath = Paths.get( Settings.tomcathome +Util.sep+mapfilename);
			if (Files.exists(sourcepath)) Files.delete(sourcepath);
			sourcepath = Paths.get( Settings.tomcathome +Util.sep+mapyamlname);
			if (Files.exists(sourcepath)) Files.delete(sourcepath);

			// call ros map_saver
			String cmd =  ROS1CMD; // setup ros environment
			cmd += " rosrun map_server map_saver";
			Util.systemCall(cmd);

			// backup existing map files in ros map dir
			backUpMappgm();
			backUpYaml();

			// move files from root to ros map dir
			sourcepath = Paths.get( Settings.tomcathome +Util.sep+mapfilename);
			Path destinationpath = Paths.get(getMapFilePath()+mapfilename);

			long timeout = System.currentTimeMillis() + 10000;
			while (!Files.exists(sourcepath) && System.currentTimeMillis()< timeout) Util.delay(10);
			if (!Files.exists(sourcepath)) {
				Util.log("error, map not saved", null);
				return false;
			}
			Files.move(sourcepath, destinationpath, StandardCopyOption.REPLACE_EXISTING);
//			Files.delete(sourcepath);

			sourcepath = Paths.get( Settings.tomcathome +Util.sep+mapyamlname);
			destinationpath = Paths.get(getMapFilePath()+mapyamlname);
			timeout = System.currentTimeMillis() + 10000;
			while (!Files.exists(sourcepath) && System.currentTimeMillis()< timeout) Util.delay(10);
			if (!Files.exists(sourcepath)) {
				Util.log("error, map not saved", null);
				return false;
			}
			Files.move(sourcepath, destinationpath, StandardCopyOption.REPLACE_EXISTING);
//			Files.delete(sourcepath);

		} catch (Exception e) {
			e.printStackTrace();
		}
		return true;
	}


}
