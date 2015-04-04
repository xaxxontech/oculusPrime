package oculusPrime.commport;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import oculusPrime.Util;

public class PowerLogger {

	public static final long ROLLOVER = 250000000; // max file size for logger
	public final static String sep = System.getProperty("file.separator");
	public final static String redhome = System.getenv("RED5_HOME");
	public final static String powerlog = redhome + sep + "log" + sep + "power2.log";

// 	private static final long BAN_TIME_OUT = 5000;
	
	private static final int MAX_HISTORY = 50;

	RandomAccessFile logger = null;
	Vector<String> history = new Vector<String>();

	Timer timer = new Timer();
	
	private static PowerLogger singleton = new PowerLogger();
	public static PowerLogger getRefrence() {
		return singleton;
	}

	private PowerLogger() {
		File logfile = new File(powerlog);
		if (logfile.exists()) {
			if (logfile.length() > ROLLOVER) {
				Util.log("file too large, rolling over: " + powerlog, this);
				logfile.delete();
			}
		}
		
		// timer.scheduleAtFixedRate(new ClearTimer(), BAN_TIME_OUT, BAN_TIME_OUT);

		try {
			logger = new RandomAccessFile(powerlog, "rw");
		} catch (Exception e) {
			Util.log("PowerLogger(): " + e.getMessage(), this);
		}
	}

	public void append(String data, String classname) {
		try {

			logger.seek(logger.length());
			logger.writeBytes(new Date().toString() + ", " + classname + ", "+ data + "\r\n");
			
			if(history.size() > MAX_HISTORY) history.remove(0);
			history.add(data);

			// Util.log("PowerLogger(): size = " + history.size());
			
		} catch (Exception e) {
			Util.log("PowerLogger(): " + e.getMessage(), this);
		}
	}

	public String tail(int lines){
		
	/// 	Util.log("PowerLogger(): " + history.size());
		
		int i = 0;
		StringBuffer str = new StringBuffer();
	 	if(history.size() > lines) i = history.size() - lines;
		for(; i < history.size() ; i++) str.append(history.get(i) + "\n<br />"); 
		return str.toString();
	}
	
	private void closeLog() {
		try {
			logger.close();
		} catch (IOException e) {
			Util.log(e.getMessage(), this);
		}
	}

	/*
	private class ClearTimer extends TimerTask {
		private int i = 0;

		@Override
		public void run() {

			Util.log("......." + i++ + "........");
			
		}
	}*/
	
}
