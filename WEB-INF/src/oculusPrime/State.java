package oculusPrime;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.Vector;

import developer.Ros.navsystemstate;

public class State {
	
	public enum values{ 
		
		motionenabled, moving, movingforward, motorport, cameratilt, motorspeed,   // motors

		dockgrabbusy, docking, dockstatus, autodocking, dockfound, dockmetrics,   // dock

		floodlightlevel, spotlightbrightness, strobeflashon, fwdfloodlevel,  // lights

		driver, logintime, pendinguserconnected, telnetusers,  // users
		
		videosoundmode, stream, driverstream, volume,  // audio video
		framegrabbusy, controlsinverted, lightlevel,
		streamactivitythreshold, streamactivity,
		motiondetect, objectdetect, streamactivityenabled, jpgstream,
		writingframegrabs, // undocumented

		wallpower, batterylife, powerport, batteryinfo, batteryvolts,  // power
		powererror, forceundock, 
		recoveryrotations, redockifweakconnection, // undocumented


		javastartup, linuxboot, httpport, lastusercommand, cpu, // system
		localaddress, externaladdress, ssid, guinotify,

		distanceangle, direction, odometry, distanceanglettl, stopbetweenmoves, odometrybroadcast, // odometry
		odomturndpms, odomturnpwm, odomupdated, odomlinearmpms, odomlinearpwm,

		rosmapinfo, rosamcl, rosglobalpath, rosscan,  // navigation
		roscurrentgoal, rosmapupdated, rosmapwaypoints, navsystemstatus,
		rossetgoal, rosgoalstatus, rosgoalcancel, navigationroute, rosinitialpose,
		navigationrouteid, nextroutetime, roswaypoint,
		rosarcmove, // to be documented

	}

	/** not to be broadcast over telnet channel when updated, to reduce chatter */
	public enum nonTelnetBroadcast { batterylife, sysvolts, batteryinfo, rosscan, rosmapwaypoints, rosglobalpath,
		odomturnpwm, odomlinearpwm, framegrabbusy, lastusercommand, cpu, odomupdated, lastodomreceived}
	
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
	
	public static State getReference() {
		return singleton;
	}

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
	
	/*	*/
	@Override
	public String toString(){	
		String str = "";
		final Set<String> keys = props.keySet();
		for(final Iterator<String> i = keys.iterator(); i.hasNext(); ){
			final String key = i.next();
			str += (key + " " + props.get(key) + "<br>\n"); 
		}
		return str;
	}

	public boolean equals(values a, navsystemstate b) {
		return equals(a.name(), b.name());
	}
	
	public String toHTML(){ 
		StringBuffer str = new StringBuffer("<table>"); 
		Set<String> keys = props.keySet();
		for(Iterator<String> i = keys.iterator(); i.hasNext();){
			try {
				if( !i.hasNext()) break;
				
				String key = i.next();
				if(key.equals(values.rosamcl.name())) key = i.next();
				if(key.equals(values.rosglobalpath.name())) key = i.next();
				if(key.equals(values.rosmapinfo.name())) key = i.next();
				if(key.equals(values.rosscan.name())) key = i.next();
				if(key.equals(values.rosmapwaypoints.name())) key = i.next();
				if(key.equals(values.batteryinfo.name())) key = i.next();
				str.append("<tr><td><b>" + key + "</b><td> " + props.get(key));

				if( !i.hasNext()) break;
				
				key = i.next();
				if(key.equals(values.rosamcl.name())) key = i.next();
				if(key.equals(values.rosglobalpath.name())) key = i.next();
				if(key.equals(values.rosmapinfo.name())) key = i.next();
				if(key.equals(values.rosscan.name())) key = i.next();
				if(key.equals(values.rosmapwaypoints.name())) key = i.next();
				if(key.equals(values.batteryinfo.name())) key = i.next();
				str.append("<td><b>" + key + "</b><td> " + props.get(key));
				
				if( !i.hasNext()) break;
				
				key = i.next();
				if(key.equals(values.rosamcl.name())) key = i.next();
				if(key.equals(values.rosglobalpath.name())) key = i.next();
				if(key.equals(values.rosmapinfo.name())) key = i.next();
				if(key.equals(values.rosscan.name())) key = i.next();
				if(key.equals(values.rosmapwaypoints.name())) key = i.next();
				if(key.equals(values.batteryinfo.name())) key = i.next();
				str.append("<td><b>" + key + "</b><td> " + props.get(key));
			} catch (Exception e) {
				break;
			}
		}
			
		if(props.containsKey(values.rosmapwaypoints.name())) {
			String names = "";
			String[] way =  props.get(values.rosmapwaypoints.name()).split(",");
			for(int i = 0 ; i < way.length ; i++) 
				if(way[i].matches("[a-zA-Z]+"))
					names += way[i].trim() + ", "; 
					
			str.append("<tr><td><b>rosmapwaypoints</b><td colspan=\"9\"> " + names + " </tr> \r");
		}
	
		if(props.containsKey(values.rosamcl.name())) 
			str.append("<tr><td><b>rosamcl</b><td colspan=\"9\"> " + props.get(values.rosamcl.name()) + " </tr> \r");
	
		if(props.containsKey(values.batteryinfo.name())) 
			str.append("<tr><td colspan=\"9\"><b>bateryinfo</b> " + props.get(values.batteryinfo.name()) + " </tr> \r");
		
		str.append("<tr><td colspan=\"9\"><b>NULL:</b>");
		for (values key : values.values()) if(! props.containsKey(key.name())) str.append(" " + key.name() + " ");
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
			
			current = get(member); 
			if(current!=null){
				if(target.equals(current)) return true;
				if(target.startsWith(current)) return true;
			}
	
			Util.delay(1); // no higher, used by motion, odometry
			if (System.currentTimeMillis()-start > timeout){ 
				Util.debug("block() timeout: " + member.name(), this);
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
			e.printStackTrace();
		}

		for(int i = 0 ; i < observers.size() ; i++) observers.get(i).updated(key.trim());
	}
	
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
		
		// Util.log("delete: " + key, this);
		
		if( ! props.containsKey(key)) return;
		
		props.remove(key);
		for(int i = 0 ; i < observers.size() ; i++)
			observers.get(i).updated(key);	
	}
	
	public void delete(values key) {
		// Util.log("delete: " + key, this);
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

	public void increment(String key) {
		Util.log("increment: " + key, this);
		if(exists(key)) set(key, getInteger(key)+1);
		else set(key, 1);
	}
	
	public double getDouble(String key) {
		double value = ERROR;
		
		if(get(key) == null) return value;
		
		try {
			value = Double.valueOf(get(key));
		} catch (NumberFormatException e) {
			Util.log("getDouble(): " + e.getMessage(), this);
		}
		
		return value;
	}

	public double getDouble(values key) {
		return getDouble(key.name());
	}
	
	public String dumpFile(final String msg) {
		if (!Settings.getReference().getBoolean(ManualSettings.debugenabled)) return null;

		File dump = new File(Settings.logfolder + Util.sep + "state_" +  System.currentTimeMillis() + ".log");
		Util.log("file created: "+dump.getAbsolutePath(), this);
		
		try {
			FileWriter fw = new FileWriter(dump);	
			fw.append("# "+ new Date().toString() + " " + msg +"\r\n");
			fw.append("# state values \r\n");
			final Set<String> keys = props.keySet();
			for(final Iterator<String> i = keys.iterator(); i.hasNext(); ){
				final String key = i.next();
				fw.append(key + " " + props.get(key) + "\r\n"); 
			}
			fw.append("# state history \r\n");
			Vector<String> snap = Util.history;
			for(int i = 0; i < snap.size() ; i++) fw.append(snap.get(i)+"\r\n");
			
			// TODO: ADD 50 LINES FROM ROS 
			// fw.append("# ros tail \r\n");
			// fw.append(Util.rosTail());
		
			fw.close();

		} catch (Exception e) { Util.printError(e); }
	
		return dump.getAbsolutePath();
	}
}