package oculusPrime;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Map;

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

	public static final String dirName = "C:\\oculusPrime\\webapps\\oculusPrime\\scripts\\startup";
	
	Process proc;

	public static void main(String[] args) {
		
		File[] python = getPythonScripts();

		if (new File(dirName).isDirectory()) {
			System.out.println("python scripts: " + python.length);
			System.out.println("ruby scripts: " + getRubyScripts().length);
		}
		
		for(int i = 0 ; i < python.length ; i++){
			System.out.println("name: " + python[i].getAbsolutePath() + "\n");
			Util.systemCallBlocking( "python " + python[i].getAbsolutePath());
		}
	}
	

	public void launch(){
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
					proc = pb.start();
					
				} catch (Exception e) {
					e.printStackTrace();
				}		
			} 	
		}).start();
	}
	

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

}
