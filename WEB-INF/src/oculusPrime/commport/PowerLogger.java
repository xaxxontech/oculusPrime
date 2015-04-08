package oculusPrime.commport;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.Date;
import java.util.Vector;

import oculusPrime.Util;

public class PowerLogger {

	public final static String sep = System.getProperty("file.separator");
	public final static String redhome = System.getenv("RED5_HOME");
	public final static String powerlog = redhome + sep + "log" + sep + "power.log";

	public static final long ROLLOVER = 250000000; 
	private static final int MAX_HISTORY = 50;

	private static RandomAccessFile logger = null;
	private static Vector<String> history = new Vector<String>();
	
	private static void init() {
		File logfile = new File(powerlog);
		if (logfile.exists()) {
			if (logfile.length() > ROLLOVER) {
				Util.debug("file too large, rolling over: " + powerlog);
				logfile.delete();
			}
		}
		
		try {
			logger = new RandomAccessFile(powerlog, "rw");
		} catch (Exception e) {
			Util.debug("PowerLogger(): " + e.getMessage());
		}
		
		append("------- log file opened ---------");
	}

	public static void append(String data){
		try {
			
			if(logger == null) {
				init();
				return;
			}
			
			logger.seek(logger.length());
			logger.writeBytes(new Date().toString() + ", " + data + "\r\n");
			
			if(history.size() > MAX_HISTORY) history.remove(0);
			history.add(data);
	
		} catch (Exception e) {
			Util.debug("PowerLogger.append(): " + e.getMessage() + " " + data); 
		}
	}
	
	public static void append(String msg, Object c) {
		append(c.getClass().getName().toLowerCase() + ", " + msg);
	}
	
	public static String tail(int lines){
		int i = 0;
		StringBuffer str = new StringBuffer();
	 	if(history.size() > lines) i = history.size() - lines;
		for(; i < history.size() ; i++) str.append(history.get(i) + "\n<br />"); 
		return str.toString();
	}


	
	
	/*	public static void closeLog() {	
		
		if(logger == null) {
			Util.log("closeLog() logger is null", singleton);
			return;
		}
		try {		
			// singleton.append("log file closed"); //, "PowerLogger");
			logger.close();
		} catch (IOException e) {
			
			
			// singleton.append("closeLog(): " + e.getMessage()); // "PowerLogger");
		}
		
	
		try {		
			singleton.append("log file closed"); //, "PowerLogger");
			logger.close();
		} catch (IOException e) {
			singleton.append("closeLog(): " + e.getMessage()); // "PowerLogger");
		}*/
	//}

}
