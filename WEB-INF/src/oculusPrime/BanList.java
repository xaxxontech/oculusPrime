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

import oculusPrime.State.values;

public class BanList {
	
	public static final String banfile = Settings.redhome+Util.sep+"conf"+Util.sep+"banlist.txt";
	public static final String banlog =  Settings.redhome+Util.sep + "log" + Util.sep + "banlist.log";
	
	public static final long BAN_TIME_OUT = Util.FIVE_MINUTES;
	public static final int BAN_ATTEMPTS = 10;
	public static final int MAX_ATTEMPTS = 12;
//	public static final int MAX_HISTORY = 50;
	
	private HashMap<String, Integer> attempts = new HashMap<String, Integer>();
	private HashMap<String, Long> blocked = new HashMap<String, Long>();
//	private Vector<String> history = new Vector<String>();
	private Vector<String> banned = new Vector<String>();
	private Vector<String> known = new Vector<String>();
	private State state = State.getReference();
	private RandomAccessFile logfile = null;
	private Timer timer = new Timer();
	
	static BanList singleton = new BanList();
	public static BanList getRefrence(){return singleton;}
	
	private BanList() {		
		try {	
			File ban = new File(banfile);
			if(ban.exists()) {
				String line = null;
				BufferedReader br = new BufferedReader(new FileReader(ban));
				while((line = br.readLine()) != null) {
					String addr = line.trim();
					if(Util.validIP(addr)) banned.add(addr); 
				}
				br.close();		
			}
		} catch (Exception e) {
			Util.log(e.getLocalizedMessage(), this);
		}

//		File log = new File(banlog);
//		if (log.exists()) {
//			if (log.length() > ROLL_OVER) {
//				Util.log("BanList(): file too large, rolling over: " + log.getAbsolutePath(), this);
//				log.delete();
//			}
//		}
		
		try {
			logfile = new RandomAccessFile(banlog, "rw");
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		timer.scheduleAtFixedRate(new ClearTimer(), 0, Util.ONE_MINUTE);
	}
	
//	public String tail(int lines){
//		int i = 0;
//		StringBuffer str = new StringBuffer();
//	 	if(history.size() > lines) i = history.size() - lines;
//		for(; i < history.size() ; i++) str.append(history.get(i) + "\n<br />"); 
//		return str.toString();
//	}
	
	public void appendLog(final String str){
//		if(history.size() > MAX_HISTORY) history.remove(0);
//		history.add(Util.getTime() + ", " + str);
		
		if(logfile==null) return;
		
		try {
			logfile.seek(logfile.length());
			logfile.writeBytes(new Date().toString() + ", " + str + "\r\n");
		} catch (Exception e) {
			e.printStackTrace();
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
		
		if(address.equals("127.0.0.1")) return false;
		
		if(!Settings.getReference().getBoolean(ManualSettings.checkaddresses)) return false;

		if(banned.contains(address)) {
			appendLog("banned address: " + address);
			if(known.contains(address)) known.remove(address);
			return true;
		}
		
		if(blocked.containsKey(address)) {
			
			appendLog("blocked address: " + address);
			if(attempts.containsKey(address)) attempts.put(address, attempts.get(address)+1);
			
			if(attempts.get(address) >= MAX_ATTEMPTS){
				appendLog("now banned: " + address);
				if(known.contains(address)) known.remove(address);
				addBlockedFile(address);
			}
			
			return true;
		}
		
		return false;
	}
	
	public synchronized boolean knownAddress(final String address) {

		if( ! Settings.getReference().getBoolean(ManualSettings.checkaddresses)) {
			return true;
		}
				
		if( ! Util.validIP(address)) return false; // basic sanity 
		if(address.equals("0.0.0.0") || address.equals("127.0.0.1") || address.startsWith("10.42")) return true;
		
		if(state.exists(values.localaddress)) {
			String firsttwonums = state.get(values.localaddress).replaceFirst("\\.\\d+\\.\\d+$", "");
			if(address.replaceFirst("\\.\\d+\\.\\d+$", "").equals(firsttwonums)) {
				if( ! known.contains(address) && ! isBanned(address)) {
					appendLog("added LAN IP: " + address);
					known.add(address);
				}
				return true;
			}
		} else appendLog("robot's LAN address unknown yet, system might be booting.. ");
		
		if(known.contains(address)) return true;
		else {
			appendLog("WARN: unknown address rejected: " + address);
			return false;
		}
	}
	
	public synchronized void clearAddress(String address) {
		
		if(address == null) return;
		if(address.equals("null")) return;
		
		if( ! Util.validIP(address)){
			appendLog(address + " is not a valid address?"); 
			return;
		}
		
		if(attempts.containsKey(address)) attempts.remove(address);
		if(blocked.containsKey(address)) blocked.remove(address);
		if( ! known.contains(address)) known.add(address);
	}

	public synchronized void removeAddress(String remoteAddress) {
		if(remoteAddress == null) return;
		if(remoteAddress.equals("null")) return;
		if(remoteAddress.equals("127.0.0.1")) return;
		if( ! Util.validIP(remoteAddress)){
			appendLog(remoteAddress + " is not a valid address?");
			return;
		}
		if(known.contains(remoteAddress)) known.remove(remoteAddress);
	}

		public synchronized void loginFailed(final String remoteAddress, final String user) {
		
		if(remoteAddress.equals("127.0.0.1")) return;
	
		if(banned.contains(remoteAddress)) Util.log("DANGEROUS: failed sanity check: " + user, this);
		
		if(attempts.containsKey(remoteAddress)) attempts.put(remoteAddress, attempts.get(remoteAddress)+1);
		else attempts.put(remoteAddress, 1);  
			
		if(known.contains(remoteAddress)) known.remove(remoteAddress);
		
		appendLog("login failed: " + remoteAddress + " user: " + user + " attempts: " + attempts.get(remoteAddress));

		if(attempts.get(remoteAddress) >= BAN_ATTEMPTS){
			appendLog("now blocked: " + remoteAddress + " user: " + user);
			blocked.put(remoteAddress, System.currentTimeMillis());
		}
	}
	
	private class ClearTimer extends TimerTask {
		@Override
		public void run() {
				
//			if(state.exists(values.gateway)){
//				if( ! known.contains(state.get(values.gateway)))
//					known.add(state.get(values.gateway));
//			}
			
			if(state.exists(values.localaddress)){ 
				if( ! known.contains(state.get(values.localaddress)))
					known.add(state.get(values.localaddress));
			}
			
// was added to let external ip access dashboard without flash logging in 
			if(Settings.getReference().getBoolean(ManualSettings.developer)){
				if(state.exists(values.externaladdress)){ 
					if( ! known.contains(state.get(values.externaladdress)))
						known.add(state.get(values.externaladdress));
				}
			}
			
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
		return "<br>banned: " + banned.toString() + "<br> known:" + known.toString();
	}
	
	public String geHTML(){
		String info = "\n<table cellspacing=\"5\">\n<tbody><tr><th>Known Address<th>Banned Address</tr>\n";
		
		String knw = known.toString().replaceAll(",", "<br>").trim();
		knw = knw.substring(1, knw.length()-1);
		
		String ban = banned.toString().replaceAll(",", "<br>").trim();
		ban = ban.substring(1,  ban.length()-1);
		
		info += "<tr><td>" + knw + "<td>"  + ban+ "</tr> \n";
		info += "\n</tbody></table>\n";
		return info;
	}
}
