package oculusPrime;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;

public class State {
	
	public enum values{ 
		motionenabled, moving, movingforward, motorport,  // motors
		dockgrabbusy, docking, dockstatus, autodocking, dockxsize,  dockslope, dockxpos, dockypos,  // dock 
		floodlightlevel, spotlightbrightness, strobeflashon, fwdfloodlevel, // lights
		driver, logintime, pendinguserconnected,  // rtmp users
		streamActivityThresholdEnabled, streamActivityThreshold, videosoundmode, stream, driverstream, //audio video
		volume, framegrabbusy, //audio video
		batterycharging, batterylife, powerport, batteryinfo, sysvolts, // battery
		boottime, httpPort, // system
		cameratilt, motorspeed, lastusercommand, controlsinverted, telnetusers,  
		distanceangle, direction, odometry, distanceanglettl, stopbetweenmoves,   
		
		localaddress, externaladdress, // network things 
		signalnoise, signalstrength, singlalquality, signalSpeed, ssid, gateway,
		// TODO: brad just added 
		
		;
	};
	
	/** throw error, or warning only, is trying to input of read any of these keys in the state object */
	public enum booleanValues{ moving, movingforward, autodocking, docking, batterycharging, framegrabbusy, dockgrabbusy, motionenabled, 
		floodlighton, driverstream, muteOnROVmove, controlsinverted, strobeflashon,
		odometry, stopbetweenmoves, diagnosticmode};

	/** not to be broadcast over telnet channel when updated, to reduce chatter */
	public enum nonTelnetBroadcast { batterycharging, batterylife, sysvolts, batteryinfo; };	
		
	public static final int ERROR = -1;

	/** notify these on change events */
	public Vector<Observer> observers = new Vector<Observer>();
	
	/** reference to this singleton class */
	private static State singleton = new State();

	/** properties object to hold configuration */
	private HashMap<String, String> props = new HashMap<String, String>(); 
	
	public static State getReference() {
		return singleton;
	}

	/** private constructor for this singleton class */
	private State() {
		props.put(values.boottime.name(), String.valueOf(System.currentTimeMillis()));	
	}
	
	
	public Properties getProperties(){
		return (Properties) props.clone();
	}

	/** */
	public void addObserver(Observer obs){
		observers.add(obs);
	}
	
	/** test for string equality. any nulls will return false */ 
	public boolean equals(final String a, final String b){
		String aa = get(a);
		if(aa==null) return false; 
		if(b==null) return false; 
		if(aa.equals("")) return false;
		if(b.equals("")) return false;
		
		return aa.equalsIgnoreCase(b);
	}
	
	public boolean equals(State.values value, String b) {
		return equals(value.name(), b);
	}
	
	
	/** */
//	public String dump(){
////		System.out.println("state number of listeners: " + observers.size());
////		for(int i = 0 ; i < observers.size() ; i++) 
////			System.out.println(i + " " + observers.get(i).getClass().getName() + "\n");
////		
////		try {
////			Set<String> keys = props.keySet();
////			for(Iterator<String> i = keys.iterator(); i.hasNext(); ){
////				String key = i.next();
////				System.out.println( key + "<> " + props.get(key));
////			}
////			
////		} catch (Exception e) {
////			Util.log(e.getLocalizedMessage(), this);
////		}
//		return
//	}
	
	/** */
	@Override
	public String toString(){	
		String str = "";
		Set<String> keys = props.keySet();
		for(Iterator<String> i = keys.iterator(); i.hasNext(); ){
			String key = i.next();
			str += (key + " " + props.get(key) + "<br>");
		}
		return str;
	}
	
//	public boolean block(final values member, final boolean target, int timeout){
//		return block(member, Boolean.valueOf(target), timeout);
//	}
	
	/** */
	public boolean block(final values member, final String target, int timeout){
		
		long start = System.currentTimeMillis();
		String current = null;
		while(true){
			
			// keep checking 
			current = get(member); 
			
			if(current!=null){
				if(target.equals(current)) return true;
				if(target.startsWith(current)) return true;
			}
				
			//
			// TODO: FIX with a call back?? 
			//
			Util.delay(10);
			if (System.currentTimeMillis()-start > timeout){ 
				Util.debug("block() timeout: " + member.name(), this);
				return false;
			}
		}
	}

	public boolean isBoolean(final String text){
		Object[] list = booleanValues.values();
		for(int i = 0 ; i < list.length ; i++)
			if(list[i].toString().equals(text)) 
				return true;
		
		return false;
	}
	
	/** Put a name/value pair into the configuration */
	public synchronized void set(final String key, final String value) {
		
		if(key==null) return;
		if(value==null) return;
		
		// avoid unnecessary state updates
		/*if(get(key).equals(value)){
			Util.log("WARN: adding a state value that is NOT in the enum!", this);
			return;
		}*/
		
		// TODO: enforce these checks with fatal error ?
		try {
			values.valueOf(key);
		} catch (Exception e) {
			Util.log("DANGEROUS: adding a state value that is NOT in the enum!", this);
			return;
		}
			
		// TODO: enforce these checks with fatal error ?
		if(isBoolean(key)){
			if( ! (value.equals("true") || value.equals("false"))){
				Util.log("DANGEROUS: can't add because is a boolean type: " + key + " = " + value, this);
				return;
			}
		}
		
		try {
			props.put(key.trim(), value.trim());
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		for(int i = 0 ; i < observers.size() ; i++) observers.get(i).updated(key.trim());	
	}

	/** Put a name/value pair into the config */
	public void set(final String key, final long value) {
		set(key, Long.toString(value));
	}
	
	public String get(values key){
		return get(key.name());
	}
	
	/** */
	public synchronized String get(final String key) {

		String ans = null;
		try {

			ans = props.get(key.trim());

		} catch (Exception e) {
			System.err.println(e.getStackTrace());
			return null;
		}

		return ans;
	}


	/** */
	public boolean getBoolean(ManualSettings setting) {
		return getBoolean(setting);
	}

	
	/** */
	public boolean getBoolean(String key) {
		
		boolean value = false;
		
		if( ! isBoolean(key)){ // TODO: testing .... 
			Util.log("___DANGEROUS: asking for a NON-boolean type: " + key + " = " + value, this);
			///return false;
		}
		
		try {

			value = Boolean.parseBoolean(get(key));

		} catch (Exception e) {
			if(key.equals("yes")) { // TODO: testing .... 
				Util.log("____DANGEROUS: using _yes_ for a boolean: " + key + " = " + value, this);
				return true;
			}
			else return false;
		}

		return value;
	}

	
	/** */
	public int getInteger(final String key) {

		String ans = null;
		int value = ERROR;

		try {

			ans = get(key);
			value = Integer.parseInt(ans);

		} catch (Exception e) {
			return ERROR;
		}

		return value;
	}
	
	
	/** */
	public long getLong(final String key) {

		String ans = null;
		long value = ERROR;

		try {

			ans = get(key);
			value = Long.parseLong(ans);

		} catch (Exception e) {
			return ERROR;
		}

		return value;
	}
	
	/** @return the ms since last boot */
	public long getUpTime(){
		return System.currentTimeMillis() - getLong(values.boottime.name());
	}
	
	/** @return the ms since last user log in */
	public long getLoginSince(){
		return System.currentTimeMillis() - getLong(values.logintime.name());
	}

	/** */
	public synchronized void set(String key, boolean b) {
		if(b) set(key, "true");
		else set(key, "false");
	}
	
	/** */
	public synchronized boolean exists(String key) {
		return props.containsKey(key);
	}
	
	/** */ 
	public synchronized void delete(String key) {
		props.remove(key);
		for(int i = 0 ; i < observers.size() ; i++)
			observers.get(i).updated(key);	
	}

	public void set(values key, values value) {
		set(key.name(), value.name());
	}

	public void delete(values key) {
		delete(key.name());
	}

	public int getInteger(values key) {
		return getInteger(key.name());
	}
	
	public long getLong(values key){
		return getLong(key.name());
	}
	
	public boolean getBoolean(values key){
		return getBoolean(key.name());
	}
	
	public void set(values key, long data){
		set(key.name(), data);
	}
	
	public void set(values key, String value){
		set(key.name(), value);
	}
	
	public void set(values key, boolean value){
		set(key.name(), value);
	}

	public void put(values value, String str) {
		set(value.name(), str);
	}

	public void put(values value, int b) {
		put(value, String.valueOf(b));
	}

	public void put(values value, boolean b) {
		put(value, String.valueOf(b));
	}

	public void delete(PlayerCommands cmd) {
		delete(cmd.name());
	}

	public void put(values value, long b) {
		put(value, String.valueOf(b));
	}
	
	public void put(values value, double b) {
		put(value, String.valueOf(b));
	}

	public double getDouble(String key) {
		double value = ERROR;
		
		try {
			value = Double.valueOf(get(key));
		} catch (NumberFormatException e) {
			Util.log("getDouble(): " + e.getMessage(), this);
		}
		
		return value;
	}

	/** @return true if given command is in the sub-set */
	public static boolean isNonTelnetBroadCast(final String str) {
		try {
			nonTelnetBroadcast.valueOf(str);
		} catch (Exception e) {return false;}
		
		return true; 
	}
	
}