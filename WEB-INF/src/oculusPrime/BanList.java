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
import java.util.Map;
import java.util.Vector;
import java.util.Timer;
import java.util.TimerTask;

public class BanList {
	
	public static final String sep = System.getProperty("file.separator");
	public static final String banfile = System.getenv("RED5_HOME") +sep+"conf"+sep+"banlist.txt";
	public static final String banlog = System.getenv("RED5_HOME") + sep + "log" + sep + "banlist.log";
	
	public static final long BAN_TIME_OUT = Util.FIVE_MINUTES;
	public static final long ROLL_OVER = 15000;
	public static final int BAN_ATTEMPTS = 3;
	public static final int MAX_ATTEMPTS = 5;	
	public static final int MAX_HISTORY = 10;
	
	private HashMap<String, Integer> attempts = new HashMap<String, Integer>();
	private HashMap<String, Long> blocked = new HashMap<String, Long>();
	private Vector<String> history = new Vector<String>();
	private Vector<String> banned = new Vector<String>();
	private RandomAccessFile logfile = null;
	private Timer timer = new Timer();
	private boolean override = false; 
	
	static BanList singleton = new BanList();
	public static BanList getRefrence(){
		return singleton;
	}
	
	private BanList() {		
		try {	
			File ban = new File(banfile);
			if(ban.exists()) {
				String line = null;
				// Util.log("BanList(): reading file: " + banfile, this); 
				BufferedReader br = new BufferedReader(new FileReader(ban));
				while((line = br.readLine()) != null) {
					String addr = line.trim();
					if(Util.validIP(addr)) banned.add(addr); 
					// else Util.log("invalid address: " + addr, this);
				}
				br.close();		
			}
		} catch (Exception e) {
			Util.log(e.getLocalizedMessage(), this);
		}

		File log = new File(banlog);
		if (log.exists()) {
			if (log.length() > ROLL_OVER) {
				Util.log("BanList(): file too large, rolling over: " + log.getAbsolutePath(), this);
				log.delete();
			}
		}
		
		try {
			logfile = new RandomAccessFile(banlog, "rw");
		} catch (Exception e) {
			Util.debug("BanList(): " + e.getMessage());
		}
		
		override = Settings.getReference().getBoolean(ManualSettings.developer);
		if(override) Util.log("BanList(): disabled, developer mode enabled", this);
		else timer.scheduleAtFixedRate(new ClearTimer(), Util.ONE_MINUTE, Util.ONE_MINUTE);
	}
	
	public String tail(int lines){
		int i = 0;
		StringBuffer str = new StringBuffer();
	 	if(history.size() > lines) i = history.size() - lines;
		for(; i < history.size() ; i++) str.append(history.get(i) + "\n<br />"); 
		return str.toString();
	}
	
	private void appendLog(final String str){
		if(history.size() > MAX_HISTORY) history.remove(0);
		history.add(Util.getTime() + ", " +str);
		
		if(logfile==null) return;
		
		try {
			logfile.seek(logfile.length());
			logfile.writeBytes(new Date().toString() + ", " + str + "\r\n");
		} catch (Exception e) {
			Util.debug("BanList.appendLog(): " + e.getMessage() + " " + str);
		}
	}

	public synchronized void removeblockedFile(final String address) {
		appendLog("remove from file: " + address);
		if(banned.contains(address)) banned.remove(address);
		clearAddress(address);
		writeFile();
	}
	
	public synchronized void addBlockedFile(final String ip){
		if(Util.validIP(ip)){
			appendLog("adding to file: " + ip);
			banned.add(ip);	
			writeFile();
		}
	}
	
	private void writeFile(){				
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(new File(banfile)));
			for(int i = 0 ; i < banned.size() ; i++) 
				if(Util.validIP(banned.get(i)))
					bw.append(banned.get(i) + " \n\r");
			bw.close();	
		} catch (Exception e) {
			Util.log("writeFile(): ", e, this);
		}
	}
	
	public synchronized boolean isBanned(Socket socket) {
		return isBanned(socket.getInetAddress().toString().substring(1));
	}
	
	public synchronized boolean isBanned(final String address) {
		
		if(address.equals("127.0.0.1") || override) return false;
			
		if(banned.contains(address)) {
			appendLog("banned address: " + address);
			return true;
		}
		
		if(blocked.containsKey(address)) {
			
			appendLog("blocked address: " + address);
			if(attempts.containsKey(address)) attempts.put(address, attempts.get(address)+1);
			
			if(attempts.get(address) >= MAX_ATTEMPTS){
				appendLog("now banned: " + address);
				addBlockedFile(address);
			}
			
			return true;
		}
		
		appendLog("passed isBanned(): " + address);
		return false;
	}
	
	public synchronized void clearAddress(String address) {
		if(attempts.containsKey(address)) attempts.remove(address);
		if(blocked.containsKey(address)) blocked.remove(address);
	}
	
	public synchronized void loginFailed(final String remoteAddress, final String user) {
		
		if(remoteAddress.equals("127.0.0.1") || override) return;
	
		if(banned.contains(remoteAddress)) Util.log(".. failed sanity check: " + user, this);
		
		if(attempts.containsKey(remoteAddress)) attempts.put(remoteAddress, attempts.get(remoteAddress)+1);
		else attempts.put(remoteAddress, 1);  
			
		appendLog("login failed: " + remoteAddress + " user: " + user + " attempts: " + attempts.get(remoteAddress));

		if(attempts.get(remoteAddress) >= BAN_ATTEMPTS){
			appendLog("now blocked: " + remoteAddress + " user: " + user);
			blocked.put(remoteAddress, System.currentTimeMillis());
		}
	}
	
	private class ClearTimer extends TimerTask {
		@Override
		public void run() {
			
			if(blocked.isEmpty()) return;
						
			try {
				for (Map.Entry<String, Long> entry : blocked.entrySet()) {				    
					if((entry.getValue()+BAN_TIME_OUT) < System.currentTimeMillis()){
				    	appendLog("removed from blocked list: " + entry.getKey());
				    	clearAddress(entry.getKey());
				    }		
				}
			} catch (Exception e) {
				Util.log("ClearTimer(): ", e, this);
			}		
		}
	}
	
	@Override
	public String toString(){
		if(override) return "developer override: " + banned.toString() + " " + attempts.toString();
		return banned.toString() + " " + attempts.toString();
	}
}
