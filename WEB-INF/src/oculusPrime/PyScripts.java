package oculusPrime;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.InputStreamReader;
import java.util.Vector;

public class PyScripts {
	
	final static String NONE = "none";
	
	String logFile = NONE;
	String pyFile = NONE;
	String ppid = NONE;
	String user = NONE;
	String name = NONE;
	String pid = NONE;
	
	// TODO: 
	// lookup proc
	// cat /proc/pid/cmdline
	
	public static Vector<PyScripts> getRunningPythonScripts() {
		Vector<PyScripts> scripts = new Vector<PyScripts>();
		try {	
			String[] cmd = new String[]{"/bin/sh", "-c", "ps -fC python"};
			Process proc = Runtime.getRuntime().exec(cmd);
			proc.waitFor();
			String line = null;
			BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));					
			while ((line = procReader.readLine()) != null) {
				if(line.trim().length() > 0) {
					if( ! line.startsWith("UID")) scripts.add( new PyScripts(line));
				}
			}
		} catch (Exception e) { Util.printError(e); }		
		return scripts;
	}	
	
	static File[] getScriptFiles(){
		File telnet = new File(Settings.telnetscripts);
		if( ! telnet.exists()) telnet.mkdir();
		File[] names = telnet.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) { 
				if(pathname.getName().endsWith("oculusprimesocket.py")) return false;
				if(pathname.getName().startsWith("startup_")) return false;
				return pathname.getName().endsWith(".py"); 
			}
		});
		return names;
	}
	
	static File[] getAutoStartScriptFiles(){
		File telnet = new File(Settings.telnetscripts);
		if( ! telnet.exists()) telnet.mkdir();
		File[] names = telnet.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) { 
				if(pathname.getName().endsWith("oculusprimesocket.py")) return false;
				if(pathname.getName().startsWith("startup_") && pathname.getName().endsWith(".py")) return true;
				return false;	
			}
		});
		return names;
	}
	
	@Override
	public String toString(){
		return name;	
	}
	
//	@Override
//	public boolean contains(){
		
//		String ans = ""; 
//		Vector<PyScripts> scripts = getRunningPythonScripts();
//		for( int i = 0 ; i < scripts.size() ; i++ ) ans += scripts.get(i).name + " ---- ";
//		return ans;
//		return "";	
//	}
	
	public static boolean isRunning(final String name){
		Vector<PyScripts> scripts = getRunningPythonScripts();

		for( int i = 0 ; i < scripts.size() ; i++ )	Util.log("active: " + scripts.get(i).name);
		
		return true; // jumper off 
	}

	public static void autostartPyScripts() {
		new Thread() {
			@Override
			public void run() {
				
				Util.delay(20000); 
				// TODO: LOOK AT THIS AGAIN? 
				Util.systemCall("pkill python");
				SystemWatchdog.waitForCpu();		
	//			final Vector<PyScripts> running = getRunningPythonScripts();;	 
	//			Util.log("**** "+running);
					
				File[] scripts = getAutoStartScriptFiles();
				for( int i = 0 ; i < scripts.length ; i++ ){	
					
					// TODO: LOOK AT THIS AGAIN..... 
					// don't start dups if still running? .. or kill all python scripts on boot?
					
//					if( ! isRunning(scripts[i].getName()) ) {
						
						Util.log("py startup: " + scripts[i].getName());
						Util.systemCall("python telnet_scripts/" + scripts[i].getName());	
						
//					} else {
		
//						Util.log("autostarting === running: " + running);
//						Util.log("autostarting === scripts: " + scripts[i].getName());
						
						// Util.systemCall("python telnet_scripts/" + scripts[i].getName());	
						
//					} 
				}
			}
		}.start();
	}
	
	// TODO: 
	// lookup proc
	// cat /proc/pid/cmdline
	
	PyScripts(String line){
		
		String tokens[] = line.trim().split("\\s+");
		
		// sanity 
		if(tokens.length < 8) { 
			Util.log("PID type? " + tokens[7], this);  
			return; 
		}
		
		if( ! tokens[7].contains("python")) {
			Util.log("ERROR -- PID type? " + tokens[7], this); 
			return;
		}
		
		if( ! Util.isInteger(tokens[1])){
			Util.log("ERROR -- PID is not valid? " + tokens[7], this); 
			return;
		}
		
		if( ! Util.isInteger(tokens[2])){
			Util.log("ERROR -- PPID is not valid? " + tokens[7], this); 
			return;
		}
	
//		Util.log(tokens.length + " tokens:" + line);
//		for(int i = 6 ; i < tokens.length ; i++ ) Util.log(i+" == " +tokens[i]);
//		Util.log(tokens.length + " = " +tokens[tokens.length-1]);
		
		user = tokens[0];
		pid = tokens[1];
		ppid = tokens[2];
				
		if(tokens.length >= 9) {
			pyFile = tokens[8];
			name = tokens[8];
			if(name.contains("/")) name = name.substring(name.lastIndexOf("/")+1);//, name.indexOf(".py"));
		}
		
		if(tokens.length >= 11) {	
			logFile = tokens[10].replaceAll("__log:=", "");
//			Util.log(logFile, this);
		}
		
		if(logFile.contains("/")) {
			logFile = logFile.substring(logFile.lastIndexOf("/")+1); // , logFile.lastIndexOf(".log"));
		}
		
//		if(pyFile.equals(NONE)) pyFile = "terminal user";
//		if(logFile.equals(NONE)) logFile = "terminal user";
//		Util.log("log: "+logFile, this);
	}


/* example 

1486537347049, static, 0 = brad 
1486537347050, static, 1 = 4197 
1486537347050, static, 2 = 4170 
1486537347050, static, 3 = 7 
1486537347050, static, 4 = 23:01 
1486537347050, static, 5 = ? 
1486537347050, static, 6 = 00:00:05 
1486537347050, static, 7 = python 
1486537347050, static, 8 = /home/brad/catkin_ws/src/oculusprime_ros/src/arcmove_globalpath_follower.py 
1486537347050, static, 9 = __name:=arcmove_globalpath_follower 
1486537347050, static, 10 = __log:=/home/brad/.ros/log/5c59de64-edcc-11e6-8465-b803054ce181/arcmove_globalpath_follower-2.log 
1486537347051, static, str tokens = 11 
1486537347051, static, 0 = brad 
1486537347051, static, 1 = 4466 
1486537347051, static, 2 = 4170 
1486537347051, static, 3 = 8 
1486537347051, static, 4 = 23:01 
1486537347051, static, 5 = ? 
1486537347051, static, 6 = 00:00:05 
1486537347052, static, 7 = python 
1486537347052, static, 8 = /home/brad/catkin_ws/src/oculusprime_ros/src/remote_nav.py 
1486537347052, static, 9 = __name:=remote_nav 
1486537347052, static, 10 = __log:=/home/brad/.ros/log/5c59de64-edcc-11e6-8465-b803054ce181/remote_nav-17.log 

	
1486602981623, static, 0 = brad 
1486602981624, static, 1 = 5749 
1486602981624, static, 2 = 1261 
1486602981625, static, 3 = 27 
1486602981625, static, 4 = 16:23 
1486602981625, static, 5 = ? 
1486602981625, static, 6 = 00:14:19 
1486602981626, static, 7 = python
1486602981626, static, 8 = telnet_scripts/block.py 

*/

	
	
}