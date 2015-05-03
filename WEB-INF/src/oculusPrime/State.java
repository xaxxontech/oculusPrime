package oculusPrime;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.Vector;

public class State {
	
	public enum values{ 
		
		motionenabled, moving, movingforward, motorport, cameratilt, motorspeed,   // motors

		dockgrabbusy, docking, dockstatus, autodocking, dockfound, dockmetrics, // dock 

		floodlightlevel, spotlightbrightness, strobeflashon, fwdfloodlevel, // lights

		driver, logintime, pendinguserconnected, telnetusers, // users
		
		streamactivityenabled, videosoundmode, stream, driverstream, volume, //audio video
		framegrabbusy, controlsinverted, lightlevel, streamactivitythreshold, streamactivity,
		motiondetect,

		wallpower, batterylife, powerport, batteryinfo, battvolts,  // power
		powererror, forceundock, // power problems

		javastartup, linuxboot, httpport, lastusercommand, // system

		distanceangle, direction, odometry, distanceanglettl, stopbetweenmoves, odometrybroadcast, // odometry
		odomturndpms, odomturnpwm, odomupdated, odomlinearmpms, odomlinearpwm,
		
		rosmapinfo, rosamcl, rosglobalpath, rosscan,  // navigation
		roscurrentgoal, rosmapupdated, rosmapwaypoints, navigationenabled,
		rossetgoal, rosgoalstatus, rosgoalcancel, navigationroute, rosinitialpose,
		navigationrouteid, nextroutetime,
		
		localaddress, externaladdress, // network things 
		signalspeed, ssid, gateway, ethernetaddress, cpu, // ethernetping, externalping, //wifiping, temptest
		
	}

	/** not to be broadcast over telnet channel when updated, to reduce chatter */
	public enum nonTelnetBroadcast { batterylife, sysvolts, batteryinfo, rosscan, rosmapwaypoints, rosglobalpath,
		odomturnpwm, odomlinearpwm, cpu } // , framegrabbusy}


	public String toTableHTML(){
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
			//	+ "<td><b>rosmapwaypoints</b><td>" + get(values.rosmapwaypoints) 
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
		
		str.append("<tr><td><b>rosmapwaypoints</b><td colspan=\"7\">" + get(values.rosmapwaypoints) );
		
		str.append("<tr>" // long line
				+ "<td><b>rosglobalpath</b><td colspan=\"10\">" + get(values.rosglobalpath) 
				+ "</tr> \n");
				
		
		
		str.append("\n</table>\n");
		return str.toString();
	}
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
			//	+ "<td><b>rosmapwaypoints</b><td>" + get(values.rosmapwaypoints) 
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
		
		str.append("<tr><td><b>rosmapwaypoints</b><td colspan=\"7\">" + get(values.rosmapwaypoints) );
		
		str.append("<tr>" // long line
				+ "<td><b>rosglobalpath</b><td colspan=\"10\">" + get(values.rosglobalpath) 
				+ "</tr> \n");
				
		
		
		str.append("\n</table>\n");
		return str.toString();
	}
	
	public String toDashboard(){	
		StringBuffer str = new StringBuffer("<table cellspacing=\"15\" border=\"0\">");
		
		str.append("<tr><td><b>ssid </b>" + get(values.ssid) + "<td><b>ip</b> " + get(values.gateway));
		if(exists(values.ethernetaddress)) str.append("<br /><b>eth </b>" + get(values.ethernetaddress));
		str.append("<td><b>lan </b>" + get(values.localaddress) 
				+ " <b>wan </b>" + get(values.externaladdress)
				+ "<tr><td><b>signal speed </b>" + get(values.signalspeed) 
				+ "<td><b>external ping </b>" + NetworkMonitor.pingValue
				+ "<td><b>last ping ping </b>" + (System.currentTimeMillis()-NetworkMonitor.pingLast)
				+ "</tr>");
		
		str.append("<tr><td><b>motor port </b>" + get(values.motorport) 
				+ "<td><b>linux mins</b> " + (((System.currentTimeMillis() - getLong(values.linuxboot)) / 1000) / 60)
				+ "<td><b>motion </b>" + get(values.motionenabled) + " <b>moving </b>" + get(values.moving)
				// + " <b>direction </b>" + get(values.direction) // + " <td><b>speed </b>" + get(values.motorspeed) 
				+ "</tr>");
				
		str.append("<tr><td><b>power port </b>" + get(values.powerport)
				+ "<td><b>java mins </b>" + (getUpTime()/1000)/60  
			//	+ "<td><b>volts </b>" + get(values.battvolts) + " <b>life </b> " + get(values.batterylife) 
				+ "<td><b>life </b> " + get(values.batterylife) + " <b>cpu </b>" + get(values.cpu) + " % </tr>");
				
	/*			
		str.append("<tr><td><b>video mode </b>" + get(values.videosoundmode) + " <b>stream </b>" + get(values.stream)
				+ "<td><b>driverstream </b>" + get(values.driverstream) + " <b>volume </b>" + get(values.volume)
			    + "<td><b>busy </b>" + get(values.framegrabbusy)    odomturnpwm
			    + "<td><b>driver </b>" + get(values.driver) 
		        + "<td><b>telnet </b>" + get(values.telnetusers) 
				+ "</tr>")
	       	   + "<td><b>booted: </b>" + new Date(getLong(values.boottime)) 
		       + "<td><b>login: </b><td>" + new Date(getLong(values.logintime)) 
				+ "<td><b>linux uptime (minutes) </b>" + (((System.currentTimeMillis() - getLong(values.linuxboot)) / 1000) /60)
		        + "<td><b>java uptime (minutes) </b>" + (getUpTime()/1000)/60 
	*/	
		
		if(exists(values.powererror)) str.append("\n<tr><td colspan=\"11\">" + get(values.powererror) + "</tr>");

		str.append("</table>\n");
		return str.toString();
	}
	
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
		props.put(values.javastartup.name(), String.valueOf(System.currentTimeMillis()));	
		props.put(values.telnetusers.name(), "0");
		getLinuxUptime();
	}

	public void getLinuxUptime(){
		new Thread(new Runnable() {
			@Override
			public void run() {	
				try {
					
					Process proc = Runtime.getRuntime().exec(new String[]{"uptime", "-s"});
					BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));									
					String line = procReader.readLine();
					Date date = new SimpleDateFormat("yyyy-MM-dd h:m:s", Locale.ENGLISH).parse(line);
					set(values.linuxboot, date.getTime());
					
					// Util.delay(5000);
					// Util.log("linux uptime (minutes): "+ (((System.currentTimeMillis() - getLong(values.linuxboot)) / 1000) /60), this);
					 
				} catch (Exception e) {
					Util.debug("getLinuxUptime(): "+ e.getLocalizedMessage());
				}										
			}
		}).start();
	}
	
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
	
	@Override
	public String toString(){	
		String str = "";
		Set<String> keys = props.keySet();
		for(Iterator<String> i = keys.iterator(); i.hasNext(); ){
			String key = i.next();
			str += (key + " " + props.get(key) + "\n\r");
		}
		return str;
	} 
	
	/**
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
			Util.delay(1); // no higher, used by motion, odometry
			if (System.currentTimeMillis()-start > timeout){ 
				Util.debug("block() timeout: " + member.name(), this);
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
		return System.currentTimeMillis() - getLong(values.javastartup);
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
	
	public synchronized boolean exists(values key) {
		return props.containsKey(key.toString().trim());
	}
	
	public synchronized boolean exists(String key) {
		return props.containsKey(key.trim());
	}
	
	public synchronized void delete(String key) {
		
		if( ! props.containsKey(key)) return;
		
		props.remove(key);
		for(int i = 0 ; i < observers.size() ; i++)
			observers.get(i).updated(key);	
	}

	public void set(values key, values value) {
		set(key.name(), value.name());
	}

	public void delete(values key) {
		if(exists(key)) delete(key.name());
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
	
	public void put(values key, values update) {
		put(key, update.name());
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