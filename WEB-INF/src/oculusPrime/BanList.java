package oculusPrime;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;

public class BanList {
	
	public static final  String sep = System.getProperty("file.separator");
	public static String banfile = System.getenv("RED5_HOME") +sep+"conf"+sep+"banlist.txt";
	public final static String banlog = System.getenv("RED5_HOME") + sep + "log" + sep + "banlist.log";

	private static RandomAccessFile logfile = null;
	public static final long ROLLOVER = 5000; 

	private static final long BAN_TIME_OUT = 60000;
//	private static final int BAN = 3; 
	private static final int BLOCK = 5; 
	static final int MAX_HISTORY = 50;
	
	private Vector<String> history = new Vector<String>();
	private HashMap<String, Integer> list = new HashMap<String, Integer>();
	private Timer timer = new Timer();
	
	static BanList singleton = new BanList();
	public static BanList getRefrence(){
		return singleton;
	}
	
	private BanList() {

		try {
			if(new File(banfile).exists()) {
				String line = null; // import from file
				BufferedReader br = new BufferedReader(new FileReader(new File(banfile)));
				while((line = br.readLine()) != null) {
					String addr = line.trim();
					if(Util.validIP(addr))
						list.put(addr, BLOCK ); 
						//TODO: Integer.MAX_VALUE/2);		
				}
				br.close();		
			}
		} catch (Exception e) {
			Util.log(e.getLocalizedMessage(), this);
		}
		
		File log = new File(banlog);
		if (log.exists()) {
			if (log.length() > ROLLOVER) {
				Util.debug("BanList(): file too large, rolling over: " + banlog);
				log.delete();
			}
		}
		
		try {
			logfile = new RandomAccessFile(banlog, "rw");
		} catch (Exception e) {
			Util.debug("BanList(): " + e.getMessage());
		}
		
		timer.scheduleAtFixedRate(new ClearTimer(), BAN_TIME_OUT, BAN_TIME_OUT);
	}
	
	public String tail(int lines){
		int i = 0;
		StringBuffer str = new StringBuffer();
	 	if(history.size() > lines) i = history.size() - lines;
		for(; i < history.size() ; i++) str.append(history.get(i) + "\n<br />"); 
		return str.toString();
	}
	
	public void appendLog(final String str){
		
		if(history.size() > MAX_HISTORY) history.remove(0);
		history.add(Util.getTime() + ", " +str);
		
		try {
			logfile.seek(logfile.length());
			logfile.writeBytes(new Date().toString() + ", " + str + "\r\n");
		} catch (Exception e) {
			Util.debug("BanList.appendLog(): " + e.getMessage() + " " + str);
		}
	}
	
	public void addBlockedFile(String ip){
		
		appendLog("....... adding to file: " + ip);
		
		list.put(ip, Integer.MAX_VALUE/2);	
		
		if(new File(banfile).exists()){
			try {
					
				BufferedWriter bw = new BufferedWriter(new FileWriter(new File(banfile)));
				Iterator<Entry<String, Integer>> i = list.entrySet().iterator(); 
				while(i.hasNext()) bw.append(i.next().getKey() + "\n"); 
				bw.close();
						
			} catch (Exception e) {
				Util.log("addBlockedFile(): ", e, this);
			}
		}	
	}
	
	public synchronized boolean isBanned(Socket socket) {
		final String address = socket.getInetAddress().toString().substring(1);
		return isBanned(address);
	}
	
	public synchronized boolean isBanned(String address) {
		
		if(list.isEmpty()) return false;
	
		if(list.containsKey(address)) {
			
			if(list.get(address) >= BLOCK){
				appendLog("login rejected banned address: " + address);
				return true;
			}
			
			return true;
		}
		
		return false;
	}

	public synchronized void remove(String address) {
		
		appendLog("....... remove from file: " + address);
		
		if(list.containsKey(address)) {
		
			list.remove(address);
		
			if(new File(banfile).exists()){
				try {
						
					BufferedWriter bw = new BufferedWriter(new FileWriter(new File(banfile)));
					Iterator<Entry<String, Integer>> i = list.entrySet().iterator(); 
					while(i.hasNext()) bw.append(i.next().getKey() + "\n"); 
					bw.close();
							
				} catch (Exception e) {
					Util.log("remove()", e, this);
				}
			}		
		}
	}
	
	public synchronized void loginFailed(final String remoteAddress, final String user) {
		
		if(remoteAddress.equals("127.0.0.1")) return;
	
		appendLog("login failed: " + remoteAddress + " user: " + user);

		// add to list if not there 
		list.put(remoteAddress, BLOCK);
			
		// increment if there 
		if(list.containsKey(remoteAddress)) list.put(remoteAddress, list.get(remoteAddress)+1);
		else list.put(remoteAddress, BLOCK);
		
		//TODO: add to file
		// list.put(remoteAddress, BLOCK);
			
	}
	
	@Override
	public String toString(){
		return list.toString();
	}
	
	private class ClearTimer extends TimerTask {
		@Override
		public void run() {
			if(list.isEmpty()) return;
			
		//	appendLog("Banned list: " + list.toString());
			
			try {
				Iterator<Entry<String, Integer>> i = list.entrySet().iterator(); 
				while(i.hasNext()){
				    String key = i.next().getKey();
				    if(list.get(key) > 0) list.put(key, list.get(key)-1);
				    if(list.get(key) == 0) list.remove(key);
				}
			} catch (Exception e) {
				Util.log("deleted entry: " + e.getLocalizedMessage(), this);
			}		
		}
	}
}
