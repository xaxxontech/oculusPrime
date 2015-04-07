package oculusPrime;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;

public class BanList {
	
	public static final  String sep = System.getProperty("file.separator");
	public static String banfile = System.getenv("RED5_HOME") +sep+"conf"+sep+"banlist.txt";

	// private static final int BAN = 3; // how many log ins to add to bad 
	private static final int BLOCK = 500; // how many log ins to add to block file 
	private static final long BAN_TIME_OUT = 3000; // time to ban 

	static final int MAX_HISTORY = 50;
	static Vector<String> history = new Vector<String>();
	
	public HashMap<String, Integer> list = new HashMap<String, Integer>();
	private static BanList singleton = new BanList();
	private Timer timer = new Timer();
	
	public static BanList getRefrence(){
		return singleton;
	}
	
	private BanList() {
		
		if( ! new File(banfile).exists()){
			try { // create file if missing 
				new File(banfile).createNewFile();
			} catch (IOException e) {
				Util.log(e.getLocalizedMessage(), this);
			}
		}
		
		try {
			String line = null; // import from file
			BufferedReader br = new BufferedReader(new FileReader(new File(banfile)));
			while((line = br.readLine()) != null) list.put(line.trim(), BLOCK ); // Integer.MAX_VALUE/2);		
			br.close();		
		} catch (Exception e) {
			Util.log(e.getLocalizedMessage(), this);
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
	
	public void appendLog(String str){
		if(history.size() > MAX_HISTORY) history.remove(0);
		history.add(Util.getTime() + ", " +str);
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
			
			appendLog("....... isBanned: " + address + " value: " + list.get(address));
	
		//	if(list.get(address) >= BLOCK) addBlockedFile(address);
			
		//	if(list.get(address) >= BAN){
				// appendLog("....... isBanned: failed: " + address, this);
		//		failed(address);
		//		return true; 
		//	}
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
	

//	public void failed(Socket socket) {
//		final String address = socket.getInetAddress().toString().substring(1);
//		failed(address);
//	}

	/**/
	public synchronized void failed(String remoteAddress) {
		
		if(remoteAddress.equals("127.0.0.1")) return;
	
		if(list.containsKey(remoteAddress)) list.put(remoteAddress, list.get(remoteAddress)+1);
		else list.put(remoteAddress, 1);
			
	}
	
	private class ClearTimer extends TimerTask {
		@Override
		public void run() {
			if(list.isEmpty()) return;
			
			/// Util.log("Banned list: " + list.toString());
			
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
