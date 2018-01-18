package oculusPrime;

import java.util.Date;
import java.util.Vector;

public class LoginRecords {

	public static final String PASSENGER = "passenger";
	public static final String DRIVER = "driver";
	
	public static Vector<Record> list = new Vector<Record>();
	public static Settings settings = Settings.getReference();
	public static State state = State.getReference();
	
	private LoginRecords(){} 
	private static LoginRecords singleton = new LoginRecords();
	public static LoginRecords getReference() { return singleton; }

	public void beDriver() { 
		list.add(new Record(state.get(State.values.driver), DRIVER)); 
		state.set(State.values.logintime, System.currentTimeMillis());
	}
	
	public void bePassenger(String user) {			
		list.add(new Record(user, PASSENGER)); 
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
	
	public  void signoutDriver() {
		for (int i = 0; i < list.size(); i++){
			Record rec = list.get(i);
			if (rec.isActive() && rec.getRole().equals(DRIVER)){
				list.get(i).logout();
			}
		}
		
		state.delete(State.values.driver);
	}
	
	/** @return the number of users */
	public  int getActive() {
		int active = 0;
		for (int i = 0; i < list.size(); i++){
			Record rec = list.get(i);
			if(rec.isActive())
				active++;
		}

		return active;
	}
	
	// TODO: REFORMAT BETTER FOR TELNET 
	public String toString() {
		String str = "RTMP users login records:<br>";
		if (list.isEmpty()) return null;
		for (int i = 0; i < list.size(); i++)
			str += list.get(i).toString() + "<br>";

		return str;
	}
	
	public String geHTML(){
		
		String info = "\n<table cellspacing=\"5\">\n<tbody><tr><th>User<th>Login<th>Logout</tr>\n";
		
		for (int i = 0; i < list.size(); i++){
			String out = "Active"; 
			if(list.get(i).timeout != 0) out = new Date(list.get(i).timeout).toString();
			info += "<tr><td>" + list.get(i).user + "<td>" + new Date(list.get(i).timein).toString() + "<td>"+out+"</tr> \n";
		}
		
		info += "\n</tbody></table>\n";
		return info;
	}
	
	/** @return list of connected users */
	public  String who() {
		String result = "";
		result += "active RTMP users: " + getActive()+"<br>" ;
		if (!list.isEmpty()) {
			for (int i = 0; i < list.size(); i++) {
				//if(list.get(i).toString().matches(".*ACTIVE$")) {
					result += list.get(i).toString() + "<br>";
			}
		}

// nuke 
// read from state
//		if (app.commandServer!=null) {
//			result+="telnet connections: "+state.get(State.values.telnetusers); // +app.commandServer.printers.size();
//		}

		return result;
	} 
	
	/** store each record in an object */
	class Record {

		private long timein = System.currentTimeMillis();
		private long timeout = 0;
		private String user = null;
		private String role = null;
		
		@Override
		public String toString() {
			String str = user + " " + role.toUpperCase(); 
			str += " login: " + new Date(timein).toString();
			if(isActive() && getRole().equals(DRIVER)) str += " is ACTIVE";
			else str += " logout: " + new Date(timeout).toString();
			
			return str;
		}
		
		Record(String usr, String role){
			this.user = usr;
			this.role = role;
		}
		
		public String getRole() {
			return role;
		}

		public boolean isActive(){
			if (!getRole().equals(PASSENGER)) { return (timeout==0); }
			else { return false; }
		}
		
		public void logout() {
			if(timeout==0){
				timeout = System.currentTimeMillis();
				Util.debug(toString(), this);
			} // else Util.log("error: trying to logout twice", this);	
		}
	}
}
