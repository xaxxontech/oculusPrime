package oculusPrime;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.InputStreamReader;
import java.util.Vector;

public class PyScripts {
		
	PyScriptsEvents events = PyScriptsEvents.getRefrence(); // state state event listener 

	final static String NONE = "none";
	String logFile = NONE;
	String pyFile = NONE;
	String ppid = NONE;
	String user = NONE;
	String name = NONE;
	String pid = NONE;
	
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
					if( ! line.startsWith("UID")) 
						scripts.add( new PyScripts(line));
				}
			}
		} catch (Exception e) { Util.printError(e); }		
		return scripts;
	}	
	
	/**
	 * @return names of regular python scripts, event driven ones shouldn't be shown in dashboard
	 */
	static File[] getScriptFiles(){ 
		File telnet = new File(Settings.telnetscripts);
		if( ! telnet.exists()) telnet.mkdir();
		File[] names = telnet.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) { 
				if(pathname.getName().endsWith("oculusprimesocket.py")) return false;
				if(pathname.getName().startsWith("startup_")) return false;
				if(pathname.getName().startsWith("shutdown_")) return false;
				if(pathname.getName().startsWith("docked_")) return false;
				return pathname.getName().endsWith(".py"); 
			}
		});
		return names;
	}
	
	static File[] getScriptFiles(final String pre){
		File auto = new File(Settings.telnetscripts);
		if( ! auto.exists()) auto.mkdir();
		File[] names = auto.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) { 
				if(pathname.getName().endsWith("oculusprimesocket.py")) return false;
				if(pathname.getName().startsWith(pre) && pathname.getName().endsWith(".py")) return true;
				return false;	
			}
		});
		return names;
	}
	
	static File[] getAutoStartScriptFiles(){
		File auto = new File(Settings.telnetscripts);
		if( ! auto.exists()) return null; // auto.mkdir();
		File[] names = auto.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) { 
				if(pathname.getName().endsWith("oculusprimesocket.py")) return false;
				if(pathname.getName().startsWith("startup_") && pathname.getName().endsWith(".py")) return true;
				return false;
			}
		});
		return names;
	}

	static File[] getShutdownScriptFiles(){
		File shut = new File(Settings.telnetscripts);
		if( ! shut.exists()) shut.mkdir();
		File[] names = shut.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) { 
				if(pathname.getName().endsWith("oculusprimesocket.py")) return false;
				if(pathname.getName().startsWith("shutdown_") && pathname.getName().endsWith(".py")) return true;
				return false;	
			}
		});
		return names;
	}
	
	@Override
	public String toString(){
		return name;	
	}
	
	public static void autostartPyScripts() {
		PyScriptsEvents.getRefrence();
		new Thread() {
			@Override
			public void run() {
				
				// system settle 
				// Util.delay(BOOTUP_DELAY);
				// TODO: LOOK AT THIS AGAIN? don't let deamon scripts duplicate 
				// if(Settings.getReference().getBoolean(ManualSettings.developer)) Util.systemCall("pkill python");
				// remember the scripts will wait on telnet opening  
				
				File[] scripts = getAutoStartScriptFiles();
				for( int i = 0 ; i < scripts.length ; i++ ){	
//					Util.log("py startup: " + scripts[i].getName());
					Util.systemCall("python telnet_scripts/" + scripts[i].getName());	
				}
			}
		}.start();
	}
	
	public static void runShutdownPyScripts() {
		new Thread() {
			@Override
			public void run() {
				
				// let scripts run shutdown handlers 
				final Vector<PyScripts> running = getRunningPythonScripts();
				for(int c = 0 ; c < running.size() ; c++){
//					Util.log("runShutdownPyScripts: kill pid: " + running.get(c).pid);
					Util.systemCall("kill -SIGINT " + running.get(c).pid + " " + running.get(c).pid);	
				}
				
				File[] scripts = getShutdownScriptFiles();
				for( int i = 0 ; i < scripts.length ; i++ ){	
//					Util.log("runShutdownPyScripts: " + scripts[i].getName());
					Util.systemCall("python telnet_scripts/" + scripts[i].getName());	
				}
			}
		}.start();
	}
	
	// parse process info 
	PyScripts(String line){
		
		String tokens[] = line.trim().split("\\s+");
		if(tokens.length < 8) { // sanity 
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
			if(name.contains("/")) name = name.substring(name.lastIndexOf("/")+1);
		}
		
		if(tokens.length >= 11) logFile = tokens[10].replaceAll("__log:=", "");
		if(logFile.contains("/")) logFile = logFile.substring(logFile.lastIndexOf("/")+1); 
	}
}