package oculusPrime;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
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

		// audio video
		videosoundmode, stream, driverstream, volume,
		framegrabbusy, controlsinverted, lightlevel,
		streamactivitythreshold, streamactivity,
		motiondetect, objectdetect, streamactivityenabled, jpgstream,
		writingframegrabs, record, // undocumented

		// power
		wallpower, batterylife, powerport, batteryinfo, batteryvolts,
		powererror, forceundock,
		redockifweakconnection, // undocumented

		javastartup, linuxboot, httpport, lastusercommand, cpu, // system
		localaddress, externaladdress, ssid, guinotify,
		osarch,

		relayserver, relayclient, // to be documented

		// network
		networksinrange, networksknown, gatewayaddress, // to be documented

		// odometry
		distanceangle, direction, odometry, distanceanglettl, stopbetweenmoves, odometrybroadcast,
		odomturndpms, odomturnpwm, odomupdated, odomlinearmpms, odomlinearpwm,
		calibratingrotation,

		// navigation
		rosmapinfo, rosamcl, rosglobalpath, rosscan,
		roscurrentgoal, rosmapupdated, rosmapwaypoints, navsystemstatus,
		rossetgoal, rosgoalstatus, rosgoalcancel, navigationroute, rosinitialpose,
		roswaypoint, nextroutetime, //navigationrouteid
		
		rosarcmove, routeoverdue, recoveryrotation, waitingforcpu, sounddetect, waypointbusy, // to be documented

	}

	/** not to be broadcast over telnet channel when updated, to reduce chatter */
	public enum nonTelnetBroadcast { batterylife, sysvolts, batteryinfo, rosscan, rosmapwaypoints, rosglobalpath,
		odomturnpwm, odomlinearpwm, framegrabbusy, lastusercommand, cpu, odomupdated, lastodomreceived,
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
	
	/** reference to this singleton class */
	private static State singleton = new State();

	/** properties object to hold configuration */
	private HashMap<String, String> props = new HashMap<String, String>(); 
	
	public static State getReference(){ return singleton; }

	@SuppressWarnings("unchecked")
	public HashMap<String, String> getState(){ return (HashMap<String, String>) props.clone(); }
	
	private State() {
		props.put(values.javastartup.name(), String.valueOf(System.currentTimeMillis()));	
		props.put(values.telnetusers.name(), "0");
		getLinuxUptime();
	}
	
	@Override
	public String toString(){
		String str = "";
		final java.util.Set<String> keys = props.keySet();
		for(final java.util.Iterator<String> i = keys.iterator(); i.hasNext(); ){
			final String key = i.next();
			str += (key + " " + props.get(key) + "<br>\n"); 
		}
		return str;
	}
	
	/** @return the ms since last app start */
	public long getUpTime(){ return System.currentTimeMillis() - getLong(values.javastartup); }
	private void getLinuxUptime(){
		new Thread(new Runnable() {
			@Override
			public void run() {	
				try {
					Process proc = Runtime.getRuntime().exec(new String[]{"uptime", "-s"});
					BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));									
					String line = procReader.readLine();
					Date date = new SimpleDateFormat("yyyy-MM-dd h:m:s", Locale.ENGLISH).parse(line);
					set(values.linuxboot, date.getTime());
				} catch (Exception e) {
					Util.debug("getLinuxUptime(): "+ e.getLocalizedMessage());
				}										
			}
		}).start();
	}
	
	/** register class for call backs */ 
	public void addObserver(Observer obs){
		observers.add(obs); 
		// for(int i = 0 ; i < observers.size() ; i++) Util.log(i + " add observer: " + observers.get(i).getClass().getName(), this);
	}
	
	/**
	public void removeObserver(Observer obs){ 

		Util.log(" ---- observers: " + observers.size(), this);
		Util.log(" ---- remove observer: " + obs.getClass().getName() + " " + obs.getClass().hashCode(), this);

		observers.remove(obs);
		for(int i = 0 ; i < observers.size() ; i++) 
			Util.log(i + " remove observer: " + observers.get(i).getClass().getName() + " " + observers.get(i).hashCode(), this);
	}
	*/
	
	/**
	 * block until timeout or until member == target
	 * 
	 * @param member state key
	 * @param target block until timeout or until member == target
	 * @param timeout is the ms to wait before giving up 
	 * @return true if the member was set to the target in less than the given timeout 
	 */
	public boolean block(final values member, final String target, int timeout){
		return block(member, target, timeout, 1); 
	} 
	
	public boolean block(final values member, final String target, int timeout, int delay){
		long start = System.currentTimeMillis();
		String current = null;
		while(true){	
			current = get(member); 
			if(current!=null){
				if(target.equalsIgnoreCase(current)){
					Util.debug("block() cleared in:  " + ((System.currentTimeMillis()-start)) + " ms for member: " + member.name(), this);
					return true;
				}
			}
	
			Util.delay(delay); 
			if (System.currentTimeMillis()-start > timeout){ 
				Util.debug("block() timeout: " + member.name(), this);
				return false;
			}
		}
	} 
	
	/**	block, wait for a change in given member */
	public boolean block(final values member, int timeout){
		return block(member.name(), timeout);
	} 
	
	public boolean block(final String member, int timeout){
		long start = System.currentTimeMillis();
		final String starting = get(member);
		while(true){	
			String current = get(member); 
			if(current!=null){
				if(starting != current){	
					// Util.debug("block() updated: " + member, this);
					Util.debug("block() changed in: " + ((System.currentTimeMillis()-start))/1000 + " seconds for member: " + member, this);
					// Util.debug("block() was: [" + starting + "] now: [" + current + "]", this);
					return true;
				}
			}
	
			Util.delay(1); 
			if (System.currentTimeMillis()-start > timeout){ 
				Util.debug("block() timeout: " + member, this);
				return false;
			}
		}
	} 
	
	/** test for string equality. any nulls will return false */ 
	public boolean equals(State.values value, String b){ return equals(value.name(), b); }
	public boolean equals(values a, navsystemstate b){ return equals(a.name(), b.name()); }
	private boolean equals(final String a, final String b){
		String aa = get(a);
		if(aa==null) return false; 
		if(b==null) return false; 
		if(aa.equals("")) return false;
		if(b.equals("")) return false;
		
		return aa.equalsIgnoreCase(b);
	}
	
	/** Put a name/value pair into the configuration */	
	public void set(values key, long data){ set(key.name(), data); }
	public void set(values key, String value){ set(key.name(), value); }	
	public void set(values key, boolean value){ set(key.name(), value); }
	public void set(values key, double d){ set(key.name(), String.valueOf(d)); }
	public void set(values key, values value){ set(key.name(), value.name()); }
	public void set(final String key, final long value){ set(key, Long.toString(value)); }
	
	public synchronized void set(String key, boolean b){
		if(b) set(key, "true");
		else set(key, "false");
	}
	
	public synchronized void set(final String key, final String value) {
		
		if(key==null) {
			Util.log("set() null key!", this);
			return;
		}
		if(value==null) {
			Util.log("set("+key+", null): is dangerous and ignored.. use delete()", this);
			return;
		}

		try {
			props.put(key.trim(), value.trim());
		} catch (Exception e) {
			Util.printError(e);
		}

		for(int i = 0 ; i < observers.size() ; i++) observers.get(i).updated(key.trim());
	}
	
	/** get as different types  */
	public int getInteger(values key){ return getInteger(key.name());}
	public long getLong(values key){ return getLong(key.name());}
	public double getDouble(values key){ return getDouble(key.name()); }
	public String get(values key){ return get(key.name()); }	

	public synchronized String get(final String key) {
		String ans = null;
		try {
			ans = props.get(key.trim());
		} catch (Exception e){}
		return ans;
	}
	
	public double getDouble(String key) {
		double value = ERROR;
		try {
			value = Double.valueOf(get(key));
		} catch(Exception e){}
		return value;
	}
	
	/** true returns true, anything else returns false */	
	public boolean getBoolean(values key){ return getBoolean(key.name()); }
	public boolean getBoolean(String key) {
		boolean value = false;
		try {
			value = Boolean.parseBoolean(get(key));
		} catch (Exception e){}
		return value;
	}

	public int getInteger(final String key) {
		int value = ERROR;
		try { value = Integer.parseInt(get(key)); } catch (Exception e){}
		return value;
	}
	
	public long getLong(final String key) {
		long value = ERROR;
		try { value = Long.parseLong(get(key)); } catch (Exception e){}
		return value;
	}
	
	/** remove */ 
	public void delete(PlayerCommands cmd){ delete(cmd.name()); }
	public void delete(values key){ delete(key.name()); }
	public synchronized void delete(String key) {	
		// Util.debug("delete(): String key = " + key, this);
		if(props.containsKey(key)){
			props.remove(key); // delete it then notify all registered classes 
			for(int i = 0 ; i < observers.size() ; i++) observers.get(i).updated(key);
		}
	}
	
	/** check if in state */ 
	public synchronized boolean exists(values key) {
		return props.containsKey(key.toString().trim());
	}
	
	public synchronized boolean exists(String key) {
		return props.containsKey(key.trim());
	}
		

	/*
	public String dumpFile(){	
		return dumpFile(" no message ");
	}
	
	public String dumpFile(final String msg) {
		if (!Settings.getReference().getBoolean(ManualSettings.debugenabled)) return null;

		File dump = new File(Settings.logfolder + Util.sep + "state_" +  System.currentTimeMillis() + ".log");
		Util.log("file created: "+dump.getAbsolutePath(), this);
		
		try {
			FileWriter fw = new FileWriter(dump);	
			fw.append("# "+ new Date().toString() + " " + msg +"\r\n");
			final Set<String> keys = props.keySet();
			for(final Iterator<String> i = keys.iterator(); i.hasNext(); ){
				final String key = i.next();
				fw.append(key + " " + props.get(key) + "\r\n"); 
			}
			fw.append("# state history \r\n");
			Vector<String> snap = Util.history;
			for(int i = 0; i < snap.size() ; i++) fw.append(snap.get(i)+"\r\n");
			fw.close();

		} catch (Exception e) { Util.printError(e); }
	
		return dump.getAbsolutePath();
	}*/
	
}