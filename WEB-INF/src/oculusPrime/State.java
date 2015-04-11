package oculusPrime;

import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import java.util.Vector;

public class State {
	
	public enum values{ 
		
		motionenabled, moving, movingforward, motorport, cameratilt, motorspeed,   // motors

		dockgrabbusy, docking, dockstatus, autodocking, dockfound, dockmetrics, // dock 

		floodlightlevel, spotlightbrightness, strobeflashon, fwdfloodlevel, // lights

		driver, logintime, pendinguserconnected, telnetusers, // users
		
		streamactivityenabled, streamactivitythreshold, videosoundmode, stream, driverstream, //audio video
		volume, framegrabbusy, controlsinverted, lightlevel,

		wallpower, batterylife, powerport, batteryinfo, battvolts,  // power
		powererror, forceundock, // power problems

		boottime, httpport, lastusercommand, // system

		distanceangle, direction, odometry, distanceanglettl, stopbetweenmoves, odometrybroadcast, // odometry
		odomturndpms, odomturnpwm, odomupdated, odomlinearmpms, odomlinearpwm,
		
		rosmapinfo, rosamcl, rosglobalpath, rosscan,  // navigation
		roscurrentgoal, rosmapupdated, rosmapwaypoints, navigationenabled, 
		
		rossetgoal, rosgoalstatus, rosgoalcancel, navigationroute, rosinitialpose,
		navigationrouteid,
		
		localaddress, externaladdress, // network things 
		signalnoise, signalstrength, signalquality, signalspeed, ssid, gateway, ethernetaddress;
	};

	public String rosDashboard(){	
		StringBuffer str = new StringBuffer("<table cellspacing=\"10\" border=\"1\"> \n");
		
		str.append("<tr>" 
				+ "<td><b>distanceangle</b><td>" + get(values.distanceangle)
				+ "<td><b>direction</b><td>" + get(values.direction)
				+ "<td><b>odometry</b><td>" + get(values.odometry) 
				+ "</tr> \n");
		
		str.append("<tr>" 
				+ "<td><b>distanceanglettl</b><td>" + get(values.distanceanglettl) 
				+ "<td><b>stopbetweenmoves</b><td>" + get(values.stopbetweenmoves) 
				+ "<td><b>odometrybroadcast</b><td>" + get(values.odometrybroadcast) 
				+ "<td><b>odomturndpms</b><td>" + get(values.odomturndpms) 
				+ "</tr> \n");
		
		str.append("<tr>" 
				+ "<td><b>odomturnpwm</b><td>" + get(values.odomturnpwm) 
				+ "<td><b>odomupdated</b><td>" + get(values.odomupdated) 
				+ "<td><b>odomlinearmpms</b><td>" + get(values.odomlinearmpms) 
				+ "<td><b>odomlinearpwm</b><td>" + get(values.odomlinearpwm) 
				+ "</tr> \n");
		
		str.append("<tr>"
				+ "<td><b>rosmapinfo</b><td colspan=\"7\">" + get(values.rosmapinfo) 
			// 	+ "<td><b>rosamcl</b><td>" + get(values.rosamcl) 
			//	+ "<td><b>rosglobalpath</b><td>" + get(values.rosglobalpath) 
				+ "</tr> \n");
			
		str.append("<tr><td><b>roscurrentgoal</b><td>" + get(values.roscurrentgoal) 
				+ "<td><b>rosmapupdated</b><td>" + get(values.rosmapupdated) 
				+ "<td><b>rosmapwaypoints</b><td>" + get(values.rosmapwaypoints) 
				+ "<td><b>navigationenabled</b><td>" + get(values.navigationenabled) 
				+ "</tr> \n");
		
		str.append("<tr>" 
				+ "<td><b>rossetgoal</b><td>" + get(values.rossetgoal) 
				+ "<td><b>rosgoalstatus</b><td>" + get(values.rosgoalstatus)
				+ "<td><b>rosgoalcancel</b><td>" + get(values.rosgoalcancel) 
				+ "<td><b>navigationroute</b><td>" + get(values.navigationroute)
				+ "</tr> \n");
		
		str.append("<tr>" 
				+ "<td><b>rosinitialpose</b><td>" + get(values.rosinitialpose) 
				+ "<td><b>navigationrouteid</b><td>" + get(values.navigationrouteid) 
				+ "</tr> \n");
		
	//	str.append("<tr>"
	//			+ "<td><b>rosmapinfo</b><td colspan\"3\">" + get(values.rosmapinfo) 
			//	+ "<td><b>rosamcl</b><td>" + get(values.rosamcl) 
			//	+ "<td><b>rosglobalpath</b><td>" + get(values.rosglobalpath) 
	//			+ "</tr> \n");
		
		str.append("<tr>" // long line
				+ "<td><b>rosglobalpath</b><td colspan=\"10\">" + get(values.rosglobalpath) 
				+ "</tr> \n");
				
		
		
		str.append("\n</table>\n");
		return str.toString();
	}
	
	public String toDashboard(){	
		StringBuffer str = new StringBuffer("<table cellspacing=\"5\" border=\"1\">");
		
		str.append("<tr><td><b>ssid: </b>" + get(values.ssid) + " <b>address: </b>" + get(values.gateway)
				+ "<td><b>lan: </b>" + get(values.localaddress) + " <b>wan: </b>" + get(values.externaladdress)
				+ "</tr>");
			
		str.append("<tr><td><b>quality: </b>" + get(values.signalquality) + "% <b>speed: </b>" + get(values.signalspeed) 
				+ "<td><b>noise: </b>" + get(values.signalnoise) + " dbm <b>strength: </b>" + get(values.signalstrength) 
				+ "</tr>");
		
		str.append("<tr><td><b>video mode: </b>" + get(values.videosoundmode) + " <b>stream: </b>" + get(values.stream)
				+ "<td><b>driverstream: </b>" + get(values.driverstream) + " <b>volume: </b>" + get(values.volume)
			    + "<td><b>busy: </b>" + get(values.framegrabbusy)  
				+ "</tr>");
		
		str.append("<tr>" 
	       	    + "<td><b>booted: </b>" + new Date(getLong(values.boottime)) 
			    + "<td><b>login: </b>" + new Date(getLong(values.logintime)) 
		        + "<td><b>uptime: </b>" + (getUpTime()/1000)/60 + " min <b>driver: </b>" + get(values.driver) + " <b>telnet: </b>" + get(values.telnetusers) 
				+ "</tr>");
	
		str.append("<tr><td><b>motor port: </b>" + get(values.motorport) 
				+ "<td><b>motion: </b>" + get(values.motionenabled) + " <b>moving: </b>" + get(values.moving) 
				+ "<td><b>direction: </b>" + get(values.direction) + " <b>speed: </b>" + get(values.motorspeed) 
				+ "</tr>");
		
		str.append("<tr><td><b>power port: </b>" + get(values.powerport)
				+ "<td><b>volts: </b>" + get(values.battvolts) + " <b>life:</b> " + get(values.batterylife) 
				+ "<td><b>wall power: </b>" + get(values.wallpower) + " <b>status: </b>" + get(values.dockstatus)
				+ "</tr>");
		
		/*
		str.append("<tr><td><b>" + values.gateway    + "</b><td>" + get(values.gateway) 
				+ "<td><b>" + values.localaddress    + "</b><td>" + get(values.localaddress) 
				+ "<td><b>" + values.externaladdress + "</b><td>" + get(values.externaladdress)
			//	+ "<td><b>" + values.ethernetaddress + "</b><td>" + get(values.ethernetaddress) 
				+ "<td><b>" + values.ssid            + "</b><td>" + get(values.ssid) + "</tr>");
		
		str.append("<tr><td><b>" + values.signalquality + "</b><td>" + get(values.signalquality) 
				+ "<td><b>" + values.signalnoise        + "</b><td>" + get(values.signalnoise) 
				+ "<td><b>" + values.signalstrength     + "</b><td>" + get(values.signalstrength) 
				+ "<td><b>" + values.signalspeed        + "</b><td>" + get(values.signalspeed) + "</tr>");
		
		str.append("<tr><td><b>" + values.powerport + "</b><td>" + get(values.powerport)
				+ "<td><b>" + values.battvolts      + "</b><td>" + get(values.battvolts)  
				+ "<td><b>" + values.batterylife    + "</b><td>" + get(values.batterylife)  
		//		+ "<td><b>" + values.wallpower   + "</b><td>" + get(values.wallpower) 
				+ "<td><b>" + values.batterylife    + "</b><td>" + get(values.batterylife) + "</tr>");
		*/
		
		//str.append("\n<tr><td colspan=\"11\">" + get(values.batteryinfo) + "</tr>");
		//if(exists(values.powererror)) str.append("\n<tr><td colspan=\"11\">" + get(values.powererror) + "</tr>");
		
		// if(exists(values.)) str.append("\n<tr><td colspan=\"11\">" + get(values.powererror) + "</tr>");

		str.append("</table>\n");
		return str.toString();
	}
	
/*	
	private boolean exists(values key) {
		return exists(key.name());
	}
*/

	/** not to be broadcast over telnet channel when updated, to reduce chatter */
	public enum nonTelnetBroadcast { batterylife, sysvolts, batteryinfo, odomturnpwm, odomupdatedz ;};	
		
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
	
	/**
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
	
	public String toTable(){	
		StringBuffer str = new StringBuffer("<table>");
		Set<String> keys = props.keySet();
		for(Iterator<String> i = keys.iterator(); i.hasNext(); ){
			String key = i.next();
			String value = props.get(key); 
			str.append("<tr><td>" + key + "<td>" + value + "</tr>");
		}
		str.append("</table>\n");
		return str.toString();
	}
	*/
	
	
	public String toHTML(){ 
		StringBuffer str = new StringBuffer("<table cellspacing=\"5\">");
		for (values key : values.values()) { 
			if(props.containsKey(key.name())) str.append("<tr><td> " + key.name() + "<td>" + props.get(key.name()));
			else str.append("<tr><td> " + key.name() + "<td><b>NULL</b>");
		}	
		
		str.append("</table>\n");
		return str.toString();
	}
	
	
	
	/**
	 * block until timeout or until member == target
	 * 
	 * @param member state key
	 * @param target block until timeout or until member == target
	 * @param timeout is the ms to wait before giving up 
	 * @return true if the member was set to the target in less than the given timeout 
	 */
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
				
			// TODO: FIX with a call back?? 
			Util.delay(10);
			if (System.currentTimeMillis()-start > timeout){ 
//				Util.debug("block() timeout: " + member.name(), this);
				return false;
			}
		}
	} 
	
	/** Put a name/value pair into the configuration */
	public synchronized void set(final String key, final String value) {
		
		if(key==null) return;
		if(value==null) return;
		
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

	
	/** true returns true, anything else returns false */
	public boolean getBoolean(String key) {
		
		boolean value = false;
		
		try {

			value = Boolean.parseBoolean(get(key));

		} catch (Exception e) {
			return false;
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
		return System.currentTimeMillis() - getLong(values.boottime);
	}
	
	/** @return the ms since last user log in */
	public long getLoginSince(){
		return System.currentTimeMillis() - getLong(values.logintime);
	}

	/** */
	public synchronized void set(String key, boolean b) {
		if(b) set(key, "true");
		else set(key, "false");
	}
	
	/** */
	public synchronized boolean exists(values key) {
		return props.containsKey(key.toString());
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

	public boolean contains(values arg) {
		
		boolean exist = false;
		if(get(arg) != null) exist = true;
		

		return exist;
		
	}

	
}