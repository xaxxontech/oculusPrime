package oculusPrime.commport;

import java.io.RandomAccessFile;
import java.util.Date;

import oculusPrime.Util;

public class PowerLogger {

	// public final static String sep = System.getProperty("file.separator");
	public final static String redhome = System.getenv("RED5_HOME");
	public final static String powerlog = redhome + "/log/power.log";
	private static RandomAccessFile logger = null;
	
	// nuke 
	// public static final long ROLLOVER = 250000000; 
	// private static final int MAX_HISTORY = 10;
	// private static Vector<String> history = new Vector<String>();
	
	private static void init() {
		
		// nuke? 
		/*File logfile = new File(powerlog);
		if (logfile.exists()) {
			if (logfile.length() > ROLLOVER) {
				Util.debug("file too large, rolling over: " + powerlog);
				logfile.delete();
			}
		}*/
		
		try {
			logger = new RandomAccessFile(powerlog, "rw");
			// history.add("start up");
		} catch (Exception e) {
			Util.debug("PowerLogger(): " + e.getMessage());
		}
	}

	public static void append(String msg, Object c) {
		String data = c.getClass().getName().toLowerCase() + ", " + msg;
		try {

			if(logger == null) init();

			logger.seek(logger.length());
			logger.writeBytes(new Date().toString() + ", " + data + "\r\n");

//			if( ! history.get(history.size()-1).equals(msg)){
//				if(history.size() > MAX_HISTORY) history.remove(0);
//				history.add(Util.getDateStampShort() + " " + msg.replace("serial in:", "")); 
//			}
		} catch (Exception e) {
			Util.debug("PowerLogger.append(): " + e.getMessage() + " " + data);
		}
	}
	
// replaced by battery status 	
//	public static String tail(int lines){
//		int i = 0;
//		StringBuffer str = new StringBuffer();
//	 	if(history.size() > lines) i = history.size() - lines;
//		for(; i < history.size() ; i++) str.append(history.get(i) + "\n<br>"); 
//		return str.toString();
//	}

	public static void close() {	
		try {		
			logger.close();
			logger = null;
		} catch (Exception e) {
			Util.debug("PowerLogger.close(): " + e.getMessage());
		}
	}
}
