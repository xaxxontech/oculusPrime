package developer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.InputStreamReader;
import java.util.HashMap;

import oculusPrime.Util;

/*
 * function to auto run all scripts under /oculusPrime/webapps/scripts/startup
 * function to return filenames of all startup scripts
 * function to run particular startup script
 * function to return all available normal scripts filenames
 * function to run particular normal script filenames
 * 
 * run each script with args: host, username (user0), password (hashed pass0), port
 * support for python, ruby, others?
 * choose language by filename extension
 * manual settings for linux/windows language interpreter locations
 * 
 */
public class ScriptRunner {
	
	public static final HashMap<String, Process> pids = new HashMap<String, Process>();
	
	public static final String sep = System.getProperty("file.separator");
	public static String dirName = System.getenv("RED5_HOME") +sep+"webapps"+sep+"oculusPrime"+sep+"scripts"+sep+"startup";
	
	// public static void main(String[] args) { runScripts(); }
	
	public static void runScripts(){
		
		File[] python = getPythonScripts();
		File[] batch = getBatchScripts();
		File[] shell = getShellScripts();
		File[] ruby = getRubyScripts();

		if (new File(dirName).isDirectory()) {
			System.out.println("python scripts: " + python.length);
			System.out.println(" batch scripts: " + batch.length);
			System.out.println("shell scripts: " + shell.length);
			System.out.println("ruby scripts: " + ruby.length);
		}
				
		for(int i = 0 ; i < batch.length ; i++) launchWindowsBatch(batch[i]);
	
	//	for(int i = 0 ; i < python.length ; i++) launchPython(python[i]);
		
		//for(int i = 0 ; i < shell.length ; i++) launchPython(python[i]);
		
	}
	
	public static void launchPython(final File script){
		new Thread(new Runnable() { 
			public void run() {
				try {
					
					System.out.println("___ launch python script: " + script.getAbsolutePath());
					
					ProcessBuilder pb = new ProcessBuilder("C:\\Python27\\python.exe", script.getAbsolutePath());   
					pb.directory(new File(script.getParent()));
					
					// pb.environment().put(key, value);
					Process proc = pb.start();
					
					// record launched scripts
					pids.put(script.getName(), proc);
					
					BufferedReader err = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
					BufferedReader in = new BufferedReader(new InputStreamReader(proc.getInputStream()));
					
					String line = null;
					while((line = in.readLine()) != null){
						if(line.length() > 0){
							Util.log("[" + script.getName() + "] output: " + line, this);
						}
					}
					
					while((line = err.readLine()) != null){
						if(line.length() > 0){
							Util.log("[" + script.getName() + "] error: " + line, this);
						}
					}
					
					err.close();
					in.close();
					
				} catch (Exception e) {
					Util.log("[" + script.getName() + "] exception: " + e.getLocalizedMessage(),this);
				}		
			} 	
		}).start();
	}
	
	public static void launchWindowsBatch(final File script){
		new Thread(new Runnable() { 
			public void run() {
				try {
					
					// System.out.println("___ launch script: " + script.getAbsolutePath());
					
					ProcessBuilder pb = new ProcessBuilder(script.getAbsolutePath()); 
					pb.directory(new File(script.getParent()));
					
					// pb.environment().put(key, value);
					Process proc = pb.start();
					
					// record launched scripts
					pids.put(script.getName(), proc);
					
					BufferedReader err = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
					BufferedReader in = new BufferedReader(new InputStreamReader(proc.getInputStream()));
					
					String line = null;
					while((line = in.readLine()) != null){
						if(line.length() > 0){
							Util.log("[" + script.getName() + "] output: " + line,this);
						}
					}
					
					while((line = err.readLine()) != null){
						if(line.length() > 0){
							Util.log("[" + script.getName() + "] error: " + line,this);
						}
					}
					
					err.close();
					in.close();
					
				} catch (Exception e) {
					Util.log(e.getLocalizedMessage(), this);
				}		
			} 	
		}).start();
	}
/*
	public static void launch_(){
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
					Process proc = pb.start();
					
				} catch (Exception e) {
					e.printStackTrace();
				}		
			} 	
		}).start();
	}
	*/

	public static File[] getPythonScripts() {

		File dir = new File(dirName);
		File[] files = dir.listFiles(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.toLowerCase().endsWith(".py");
			}
		});

		return files;
	}
	
	public static File[] getRubyScripts() {

		File dir = new File(dirName);
		File[] files = dir.listFiles(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.toLowerCase().endsWith(".rb");
			}
		});

		return files;
	}
	
	public static File[] getBatchScripts() {

		File dir = new File(dirName);
		File[] files = dir.listFiles(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.toLowerCase().endsWith(".bat");
			}
		});

		return files;
	}
	
	public static File[] getShellScripts() {

		File dir = new File(dirName);
		File[] files = dir.listFiles(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.toLowerCase().endsWith(".sh");
			}
		});

		return files;
	}

}
