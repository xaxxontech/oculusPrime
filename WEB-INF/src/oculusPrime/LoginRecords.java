package oculusPrime;

import java.util.Date;
import java.util.Vector;

public class LoginRecords {

	public static final String PASSENGER = "passenger";
	public static final String DRIVER = "driver";
//	public static final int MAX_RECORDS = 50;
	
	public static Vector<Record> list = new Vector<Record>();
	public static State state = State.getReference();
	public static Settings settings = Settings.getReference();
	private static Application app = null; 
	
	public LoginRecords(){}
	
	public void setApplication(Application a) {
		app = a;
	}
	
	public void beDriver() { 
		
		list.add(new Record(state.get(State.values.driver), DRIVER)); 
		state.set(State.values.logintime, System.currentTimeMillis());

		Util.debug("beDriver(): " + state.get(State.values.driver), this);
		
//		if(list.size()>MAX_RECORDS) list.remove(0); // push out oldest 
	}
	
	public void bePassenger(String user) {		
	
		list.add(new Record(user, PASSENGER)); 
//		if(list.size()>MAX_RECORDS) list.remove(0); // push out oldest
		Util.debug("bePassenger(): " + user, this);
	}
	
	/** is the driver the admin user? */
	public boolean isAdmin() {
		String user = state.get(State.values.driver);
		if (user == null) return false;
		if (user.equals("")) return false;
		Settings settings = Settings.getReference();
		String admin = settings.readSetting("user0").toLowerCase();
		return admin.equals(user.toLowerCase());
	}
	
	
	public void signoutDriver() {
		
		// try all instances
		for (int i = 0; i < list.size(); i++){
			Record rec = list.get(i);
			if (rec.isActive() && rec.getRole().equals(DRIVER)){
				list.get(i).logout();
			}
		}
		
		state.delete(State.values.driver);
		
//		if(list.size() > MAX_RECORDS) list.remove(0);

	}
	
	/** @return the number of users waiting in line */
	private int getNumPassengers() {
		int passengers = 0;
		for (int i = 0; i < list.size(); i++){
			Record rec = list.get(i);
			if(rec.getRole().equals(PASSENGER))
				passengers++;
		}

		return passengers;
	}

	/** @return the number of users */
	public int getActive() {
		int active = 0;
		for (int i = 0; i < list.size(); i++){
			Record rec = list.get(i);
			if(rec.isActive())
				active++;
		}

		return active;
	}
	
//	/** @return a list of user names waiting in line */
//	public String[] getPassengerList() {
//		String[] passengers = new String[getPassengers()];
//		for (int i = 0; i < list.size(); i++){
//			Record rec = list.get(i);
//			if(rec.isActive() && rec.getRole().equals(PASSENGER))
//				passengers[i] = rec.getUser();
//		}
//
//		return passengers;
//	}
	
//	/** @return a list of user names */
//	public String[] getActiveList() {
//		String[] passengers = new String[getActive()];
//		for (int i = 0; i < list.size(); i++){
//			Record rec = list.get(i);
//			if(rec.isActive())
//				passengers[i] = rec.getUser();
//		}
//
//		return passengers;
//	}

//	public int size() {
//		return list.size();
//	}

	public String toString() {

		String str = "RTMP users login records:<br>";
		if (list.isEmpty()) return null;
		for (int i = 0; i < list.size(); i++)
			str += i + " " + list.get(i).toString() + "<br>";

		return str;
	}
	
	/** 
	 * @return list of connected users 
	 */
	public String who() {
		String result = "";
		result += "active RTMP users: " + getActive()+"<br>" ;
		if (!list.isEmpty()) {
			for (int i = 0; i < list.size(); i++) {
				if (list.get(i).toString().matches(".*ACTIVE$")) {
					result += list.get(i).toString() + "<br>";
				}
			}
		}
		if (app.commandServer!=null) {
			result+="telnet connections: "+app.commandServer.printers.size(); 
		}

		return result;
	}

	/**
	 * store each record in an object 
	 */
	private class Record {

		private long timein = System.currentTimeMillis();
		private long timeout = 0;
		private String user = null;
		private String role = null;
		
		Record(String usr, String role){
			this.user = usr;
			this.role = role;
		}

		public String getUser() {
			return user;
		}
		
		public String getRole() {
			return role;
		}

		public boolean isActive(){
			if (!getRole().equals(PASSENGER)) { return (timeout==0); }
			else { return false; }
		}
		
		@Override
		public String toString() {
			String str = user + " " + role.toUpperCase(); 
			str += " login: " + new Date(timein).toString();
			if(isActive() && getRole().equals(DRIVER)) str += " is ACTIVE";
			else str += " logout: " + new Date(timeout).toString();
			
			return str;
		}

		public void logout() {
			if(timeout==0){
				timeout = System.currentTimeMillis();
				Util.debug("logged out : " + toString(), this);
			} else Util.log("error: trying to logout twice", this);	
		}
	}
}
