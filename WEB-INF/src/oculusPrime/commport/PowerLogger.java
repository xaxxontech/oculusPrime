package oculusPrime.commport;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Date;
import java.util.Vector;

import oculusPrime.Util;

public class PowerLogger {

	public static final long ROLLOVER = 250000000; // max file size for logger
	public final static String sep = System.getProperty("file.separator");
	public final static String redhome = System.getenv("RED5_HOME");
	public final static String powerlog = redhome + sep + "log" + sep + "power666.log";

// 	private static final long BAN_TIME_OUT = 5000;
	
	private static final int MAX_HISTORY = 50;

	static RandomAccessFile logger = null;
	static Vector<String> history = new Vector<String>();

//	Timer timer = new Timer();
	
	private static PowerLogger singleton = new PowerLogger();
	public static PowerLogger getRefrence() {
		return singleton;
	}

	private PowerLogger() {
		File logfile = new File(powerlog);
		if (logfile.exists()) {
			if (logfile.length() > ROLLOVER) {
				Util.log("file too larg, rolling over: " + powerlog, this);
				logfile.delete();
			}
		}
		
		try {
			logger = new RandomAccessFile(powerlog, "rw");
		} catch (Exception e) {
			Util.log("PowerLogger(): " + e.getMessage());
		}
		
		
		// timer.scheduleAtFixedRate(new ClearTimer(), BAN_TIME_OUT, BAN_TIME_OUT);
		append("log file opened");
		
		// Runtime.getRuntime().addShutdownHook(new JVMShutdownHook());
		
	}

	public static void append(String data) {
		try {
		
			if(logger == null) {
				Util.debug("PowerLogger(): null log file: "+ data);
				return;
			}
			
			logger.seek(logger.length());
			logger.writeBytes(new Date().toString() + " " + data + "\r\n");
			
			if(history.size() > MAX_HISTORY) history.remove(0);
			history.add(data);
			
		} catch (Exception e) {
			Util.log("PowerLogger.append() " + e.getMessage() + " " + data);
		}
	}

	public String tail(int lines){
		int i = 0;
		StringBuffer str = new StringBuffer();
	 	if(history.size() > lines) i = history.size() - lines;
		for(; i < history.size() ; i++) str.append(history.get(i) + "\n<br />"); 
		return str.toString();
	}
	
	
	static void closeLog() {	
		
		Util.log("PowerLogger.closeLog() called");
		
		if(logger == null) {
			Util.log("PowerLogger.closeLog() logger is null");
			return;
		}
		
		try {		
			append("log file closed");
			logger.close();
		} catch (IOException e) {
			Util.log("PowerLogger.closeLog(): " + e.getMessage());
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
	
	

	// private static class JVMShutdownHook extends Thread {
		 
		// JVMShutdownHook(){
		//	 PowerLogger p = PowerLogger.getRefrence();
		 //}
		 
	//	 public void run() {
	   
		//	 System.out.println("JVM Shutdown Hook: Thread initiated...");
	  // 	Util.log("JVM Shutdown Hook: Thread initiated...", this);
	  //  	append("JVM Shutdown Hook..");
	  //  	closeLog();
	     
	  //  }
	 // }

	
}
