package oculusPrime;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;

import developer.Ros.navsystemstate;

public class State {
	
	public enum values{

		// motors
		motionenabled, moving, movingforward, motorport, cameratilt, motorspeed,

		// dock
		dockgrabbusy, docking, dockstatus, autodocking, dockfound, dockmetrics,

		// lights
		floodlightlevel, spotlightbrightness, strobeflashon, fwdfloodlevel,

		// users
		driver, logintime, pendinguserconnected, telnetusers,
        // not typically used by scripts, undocumented:
        driverclientid,

		// audio video
		stream, driverstream, volume,
		framegrabbusy, controlsinverted, lightlevel,
		streamactivitythreshold, streamactivity,
		motiondetect, objectdetect, streamactivityenabled, jpgstream,
		record, sounddetect,
		// not typically used by scripts, undocumented:
		writingframegrabs, webrtcstatus,

		// power
		wallpower, batterylife, powerport, batteryinfo, batteryvolts,
		powererror, forceundock,
		// not typically used by scripts, undocumented:
		redockifweakconnection, // undocumented

		// system
		javastartup, linuxboot, httpport, lastusercommand, cpu, guinotify, waitingforcpu, osarch,


		// network
		localaddress, externaladdress, ssid,
		networksinrange, networksknown, gatewayaddress, relayserver, relayclient,

		// odometry
		distanceangle, direction, odometry, distanceanglettl, stopbetweenmoves, odometrybroadcast,
		odomturndpms, odomturnpwm, odomlinearmpms, odomlinearpwm,
		// not typically used by scripts, undocumented:
		calibratingrotation, odomrotating, rotatetolerance,

		// navigation
		rosmapinfo, rosamcl, rosglobalpath, rosscan,
		roscurrentgoal, rosmapupdated, rosmapwaypoints, navsystemstatus,
		rossetgoal, rosgoalstatus, rosgoalcancel, navigationroute, rosinitialpose,
		navigationrouteid, nextroutetime, roswaypoint, rosarcmove,
		waypointbusy, lidar, // to be documented

	}

	/** not to be broadcast over telnet channel when updated, to reduce chatter */
	public enum nonTelnetBroadcast { batterylife, sysvolts, batteryinfo, rosscan, rosmapwaypoints, rosglobalpath,
		odomturnpwm, odomlinearpwm, framegrabbusy, lastusercommand, odomupdated, lastodomreceived,
		redockifweakconnection, networksinrange,
	}

	/** @return true if given command is in the sub-set */
	public static boolean isNonTelnetBroadCast(final String str) {
		try { nonTelnetBroadcast.valueOf(str); } catch (Exception e) {return false;}
		return true; 
	}

	public static final int ERROR = -1;
	
	/** notify these on change events */
	public Vector<Observer> observers = new Vector<Observer>();
	private static State singleton = new State();

	/** properties object to hold configuration */
	private HashMap<String, String> props = new HashMap<String, String>(); 
	public void addObserver(Observer obs){ observers.add(obs); }
	public static State getReference() { return singleton; }

	private State() {
		props.put(values.osarch.name(), System.getProperty("os.arch"));
		props.put(values.javastartup.name(), String.valueOf(System.currentTimeMillis()));	
		props.put(values.telnetusers.name(), "0");
	}
	
	@SuppressWarnings("unchecked")
	public HashMap<String, String> getState(){
		return (HashMap<String, String>) props.clone();
	}
	
	/** test for string equality. any nulls will return false */ 
	private boolean equals(final String a, final String b){
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
		String str = "number of observers: " + observers.size();
		final Set<String> keys = props.keySet();
		for(final Iterator<String> i = keys.iterator(); i.hasNext(); ){
			final String key = i.next();
			str += (key + " " + props.get(key) + "<br>"); 
		}
		return str;
	}
	
	public boolean equals(values a, navsystemstate b) {
		return equals(a.name(), b.name());
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
			
			current = get(member); 
			if(current!=null){
				if(target.equals(current)) return true;
				if(target.startsWith(current)) return true;
			}
	
			Util.delay(1); // no higher, used by motion, odometry
			if (System.currentTimeMillis()-start > timeout){ 
//				Util.debug("block() timeout: " + member.name(), this);
				return false;
			}
		}
	} 
	
	/** Put a name/value pair into the configuration */
	synchronized void set(final String key, final String value) {
		
		if(key==null) {
			Util.log("set() null key!", this);
			return;
		}
		if(value==null) {
			Util.log("set() use delete() instead", this);
			Util.log("set() null valu for key: " + key, this);
			return;
		}
		try {
			props.put(key.trim(), value.trim());
		} catch (Exception e) {
			Util.printError(e);
		}

		for(int i = 0 ; i < observers.size() ; i++) observers.get(i).updated(key.trim());
	}
	
	synchronized String get(final String key) {

		String ans = null;
		try {

			ans = props.get(key.trim());

		} catch (Exception e) {
			System.err.println(e.getStackTrace());
			return null;
		}

		return ans;
	}

	public void set(final String key, final long value) {
		set(key, Long.toString(value));
	}
	
	public String get(values key){
		return get(key.name());
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
	
	/** @return the ms since last app start */
	public long getUpTime(){
		return System.currentTimeMillis() - getLong(values.javastartup);
	}
	
	/** @return the ms since last user log in */
	public long getLoginSince(){
		return System.currentTimeMillis() - getLong(values.logintime);
	}

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
	
	synchronized void delete(String key) {
		if(!exists(key)) return;
		if(props.containsKey(key)) props.remove(key);
		for(int i = 0 ; i < observers.size() ; i++) observers.get(i).updated(key);	
//		Util.debug("delete: " + key, this);
	}
	
	public void delete(values key) {
		if(exists(key)) delete(key.name());
	}
	
	public void set(values key, values value) {
		set(key.name(), value.name());
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

	public void set(values key, double d) {
		set(key.name(), String.valueOf(d));
	}
	
	public void delete(PlayerCommands cmd) {
		delete(cmd.name());
	}
	
	public double getDouble(String key) {
		double value = ERROR;
		
		if(get(key) == null) return value;
		
		try {
			value = Double.valueOf(get(key));
		} catch (NumberFormatException e) {
			return value;
		}
		
		return value;
	}

	public double getDouble(values key) {
		return getDouble(key.name());
	}


}