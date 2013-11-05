package oculusPrime;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;

public class BanList {
	
	public static final  String sep = System.getProperty("file.separator");
	public static String banfile = System.getenv("RED5_HOME") +sep+"conf"+sep+"banlist.txt";

	private static final int BAN = 3; // how many log ins to add to bad 
	private static final int BLOCK = 10; // how many log ins to add to block file 
	private static final long BAN_TIME_OUT = 20000; // time to ban 
	
	private HashMap<String, Integer> list = new HashMap<String, Integer>();
	private static BanList singleton = new BanList();
	private Timer timer = new Timer();
	
	public static BanList getRefrence(){
		return singleton;
	}
	
	private BanList() {
	
		// create file if missing 
		if( ! new File(banfile).exists()){
			try {
				new File(banfile).createNewFile();
			} catch (IOException e) {
				Util.log(e.getLocalizedMessage(), this);
			}
		}
		
		// import from file
		try {
				
			String line = null;
			BufferedReader br = new BufferedReader(new FileReader(new File(banfile)));
			while((line = br.readLine()) != null) list.put(line.trim(), Integer.MAX_VALUE/2);		
			br.close();
					
		} catch (Exception e) {
			Util.log(e.getLocalizedMessage(), this);
		}
		
		Util.log("..... Banned List: " + list.toString(), this);
		
		timer.scheduleAtFixedRate(new ClearTimer(), BAN_TIME_OUT, BAN_TIME_OUT);
	}
	
	public void addBlockedFile(String ip){
		
		Util.log("....... adding to file: " + ip, this);
		
		list.put(ip, Integer.MAX_VALUE/2);	
		
		if(new File(banfile).exists()){
			try {
					
				BufferedWriter bw = new BufferedWriter(new FileWriter(new File(banfile)));
				Iterator<Entry<String, Integer>> i = list.entrySet().iterator(); 
				while(i.hasNext()) bw.append(i.next().getKey() + "\n"); 
				bw.close();
						
			} catch (Exception e) {
				Util.log(e.getLocalizedMessage(), this);
			}
		}	
	}
	
	public synchronized boolean isBanned(String address) {
	
		if(list.isEmpty()) return false;
	
		if(list.containsKey(address)) {
			
			Util.log("....... isBanned: " + address + " value: " + list.get(address), this);
	
			if(list.get(address) >= BLOCK) addBlockedFile(address);
			
			if(list.get(address) >= BAN){
				Util.log("....... isBanned: failed: " + address, this);
				failed(address);
				return true; 
			}
		}
		
		return false;
	}

	public synchronized void failed(String remoteAddress) {
		
		if(remoteAddress.equals("127.0.0.1")) return;
	
		if(list.containsKey(remoteAddress)) list.put(remoteAddress, list.get(remoteAddress)+1);
		else list.put(remoteAddress, 1);
			
	}

	private class ClearTimer extends TimerTask {
		@Override
		public void run() {
			if(list.isEmpty()){
				Util.log("Banned list is empty..", this);
				return;
			}
		
			// if(list.size() <= 1) return;
			
			Util.log("Banned list: " + list.toString(), this);
			
			try {
				Iterator<Entry<String, Integer>> i = list.entrySet().iterator(); 
				while(i.hasNext()){
				    String key = i.next().getKey();
				    if(list.get(key) > 0) list.put(key, list.get(key)-1);
				    if(list.get(key) == 0) list.remove(key);
				}
			} catch (Exception e) {
				Util.log(".......deleted entry: " + e.getLocalizedMessage(), this);
			}		
		}
	}
}
