package oculusPrime;

import java.io.*;
import java.util.HashMap;
import java.util.UUID;

import oculusPrime.State.values;

public class Settings {

	public final static String tomcathome = System.getenv("CATALINA_HOME");
	public final static String settingsfile = tomcathome +Util.sep+"conf"+Util.sep+"oculus_settings.txt";
	public final static String telnetscripts = tomcathome +"/telnet_scripts"; // this folder needs to be moved or protected from update operation!
	public final static String appsubdir = "webapps/oculusPrime";
	public final static String streamfolder = tomcathome + "/webapps/oculusPrime/streams/";
	public final static String framefolder = tomcathome +Util.sep+appsubdir+"/framegrabs";
	public final static String streamsfolder = tomcathome +Util.sep+appsubdir+"/streams";
	public final static String stdout = tomcathome +Util.sep+"log/jvm.stdout";
	public final static String logfolder = tomcathome +Util.sep+"log";
	
	public final static String DISABLED= "disabled";
	public final static String ENABLED = "enabled";
	public static final String FALSE = "false";
	public static final String TRUE = "true";	
	public static final int ERROR = -1;

	private static Settings singleton = null;
	public static Settings getReference() {
		if (singleton == null) singleton = new Settings();
		return singleton;
	}
	
	private HashMap<String, String> settings = new HashMap<String, String>(); 
	
	private Settings(){
		
		// be sure of basic configuration 
		if(! new File(settingsfile).exists()) createFile(settingsfile);
		
		importFile();
	}
	
	private void importFile(){
		try {
			String line;
			FileInputStream filein = new FileInputStream(settingsfile);
			BufferedReader reader = new BufferedReader(new InputStreamReader(filein));
			while ((line = reader.readLine()) != null) {
				String items[] = line.split(" ");
				settings.put(items[0], items[1]);
			}
			reader.close();
			filein.close();
		} catch (Exception e) {
			Util.log("importFile: " + e.getMessage(), this);
		}
		
		// test for missing
		
		for (GUISettings setting : GUISettings.values()) {
			if (readSetting(setting.name()) == null) {
				Util.log("missing setting, changed to default: " + setting.name(), this);
				writeFile();
				break;
			} else {
				if(readSetting(setting.name()).equalsIgnoreCase("null")){
					Util.log("missing setting, changed to default: " + setting.name(), this);
					writeFile();
					break;
				}
			}
		}
		
		for (ManualSettings setting : ManualSettings.values()) {
			if (readSetting(setting.name()) == null) {
				Util.log("missing setting, changed to default: " + setting.name(), this);
				writeFile();
				break;
			} else {
				if(readSetting(setting.name()).equalsIgnoreCase("null")){
					Util.log("missing setting, changed to default: " + setting.name(), this);
					writeFile();
					break;
				}
			}
		}
	}
		
	public boolean getBoolean(String key) {
		if (key == null) return false;
		String str = readSetting(key);
		if (str == null) return false;
		if (str.toUpperCase().equals("YES")) return true;
		else if (str.equalsIgnoreCase(TRUE)) return true;
		return false;
	}

	public int getInteger(String key) {

		String ans = null;
		int value = ERROR;

		try {
			ans = readSetting(key);
			value = Integer.parseInt(ans);
		} catch (Exception e) {
			return ERROR;
		}

		return value;
	}

	public double getDouble(String key) {

		String ans = null;
		double value = ERROR;

		try {
			ans = readSetting(key);
			value = Double.parseDouble(ans);
		} catch (Exception e) {
			return ERROR;
		}

		return value;
	}

	public String readSetting(String str) {
		
		if(settings.containsKey(str)) return settings.get(str);
	
		// reads++;
		FileInputStream filein;
		String result = null;
		try {

			filein = new FileInputStream(settingsfile);
			BufferedReader reader = new BufferedReader(new InputStreamReader(filein));
			String line = "";
			while ((line = reader.readLine()) != null) {
				String items[] = line.split(" ");
				if(items.length>=2){
					if ((items[0].toUpperCase()).equals(str.toUpperCase())) {
						result = items[1];
					}
				} 
			}
			reader.close();
			filein.close();
		} catch (Exception e) {
			Util.log("readSetting: " + e.getMessage(), this);
			return null; 
		}
		
		// don't let string "null" be confused for actually a null, error state  
		if(result!=null) if(result.equalsIgnoreCase("null")) result = null;
		
		return result;
	}

	@Override
	public String toString(){
		
		String result = new String();
		for (GUISettings factory : GUISettings.values()) {
			String val = readSetting(factory.toString());
			if (val != null) { // never send out plain text passwords 
				if( ! factory.equals(GUISettings.email_password))
					result += factory.toString() + " " + val + "<br>"; 
			}
		}
	
		for (ManualSettings ops : ManualSettings.values()) {
			String val = readSetting(ops.toString());
			if (val != null) result += ops.toString() + " " + val + "<br>"; 
		}
		
		return result;
	}
	
	public synchronized void createFile(String path) {
		try {
			
			Util.log("creating "+path.toString(), this);
			
			FileWriter fw = new FileWriter(new File(path));
			
			fw.append("# GUI settings \r\n");
			for (GUISettings factory : GUISettings.values()) {
				fw.append(factory.toString() + " " + GUISettings.getDefault(factory) + "\r\n");
			}
			
			fw.append("# manual settings \r\n");
			for (ManualSettings ops : ManualSettings.values()) {
				fw.append(ops.toString() + " " + ManualSettings.getDefault(ops) + "\r\n");
			}
			
			fw.append("# user list \r\n");
			fw.append("salt "+UUID.randomUUID().toString() + "\r\n");
			fw.close();

		} catch (Exception e) {
			e.printStackTrace(System.out);
		}
	}
	
	/** Organize the settings file into 3 sections. Use Enums's to order the file */
	public synchronized void writeFile(){
		try {
			
			final String temp = tomcathome + Util.sep+"conf"+Util.sep+"oculus_created.txt";
			FileWriter fw = new FileWriter(new File(temp));
			
			fw.append("# gui settings \r\n");
			for (GUISettings factory : GUISettings.values()) {
				String val = readSetting(factory.toString());
				if (val != null) {
					fw.append(factory.toString() + " " + val + "\r\n");
				} 
				else {
					fw.append(factory.toString() + " " + GUISettings.getDefault(factory) + "\r\n");
				}
			}
			
			fw.append("# manual settings \r\n");
			for (ManualSettings ops : ManualSettings.values()) {
				String val = readSetting(ops.toString());
				if (val != null){
					fw.append(ops.toString() + " " + val + "\r\n");
				} 
				else {
					fw.append(ops.toString() + " " + ManualSettings.getDefault(ops) + "\r\n");
				}
			}

			fw.append("# user list \r\n");
			if(readSetting("salt") != null) { fw.append("salt " + readSetting("salt") + "\r\n"); }
			else fw.append("salt "+UUID.randomUUID().toString() + "\r\n");

			if(readSetting("user0")!=null){
				String[][] users = getUsers();
				for (int j = 0; j < users.length; j++) {
					fw.append("user" + j + " " + users[j][0] + "\r\n");
					fw.append("pass" + j + " " + users[j][1] + "\r\n");
				}
			} 
			fw.close();
			
			// now swap temp for real file
			new File(settingsfile).delete();
			new File(temp).renameTo(new File(settingsfile));
			new File(temp).delete();

			importFile();
			
		} catch (Exception e) {
			Util.log("Settings.writeFile(): " + e.getMessage(), this);
		}
	}

	private String[][] getUsers() {

		int i = 0;
		for (;; i++)
			if (readSetting("user" + i) == null)
				break;
		
		String[][] users = new String[i][2];

		for (int j = 0; j < i; j++) {
			users[j][0] = readSetting("user" + j);
			users[j][1] = readSetting("pass" + j);
		}

		return users;
	}
 
	public void writeSettings(String setting, int value) {
		String str = null;
		try {
			str = Integer.toString(value);
		} catch (Exception e) {
			return;
		}

		if (str != null) writeSettings(setting, str);
	}
	
	public void writeSettings(ManualSettings setting, String str) {
		writeSettings(setting.name(), str);
	}

	public void writeSettings(GUISettings setting, String str) {
		writeSettings(setting.name(), str);
	}
	
	/**
	 * Modify value of existing setting. read whole file, replace line while
	 * you're at it, write whole file
	 */
	public synchronized void writeSettings(String setting, String value) {
		
		if(setting == null) return;
		if(value == null) return;
		if(value.equalsIgnoreCase("null")) return;
				
		setting = setting.trim();
		value = value.trim();

		if(settings.get(setting).equals(value)) {
			// Util.debug("setting rejected, "+setting+" already set to: " + value, this);
			return;
		}
		
		settings.put(setting, value);
		FileInputStream filein;
		String[] lines = new String[999];
		try {

			filein = new FileInputStream(settingsfile);
			BufferedReader reader = new BufferedReader(new InputStreamReader(filein));
			int i = 0;
			while ((lines[i] = reader.readLine()) != null) {
				String items[] = lines[i].split(" ");
				if(items.length==2){ //TODO: SHOULD SETTINGS BE CASE SENSITIVE? 
					if ((items[0].toUpperCase()).equals(setting.toUpperCase())) {
						lines[i] = setting + " " + value;
					}
				}
				i++;
			}
			filein.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		FileOutputStream fileout;
		try {
			fileout = new FileOutputStream(settingsfile);
			for (int n = 0; n < lines.length; n++) {
				if (lines[n] != null) {
					new PrintStream(fileout).println(lines[n]);
				}
			}
			fileout.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void newSetting(String setting, String value) {
		setting = setting.trim();
		value = value.trim();

		FileInputStream filein;
		String[] lines = new String[999];
		try {
			filein = new FileInputStream(settingsfile);
			BufferedReader reader = new BufferedReader(new InputStreamReader(filein));
			int i = 0;
			while ((lines[i] = reader.readLine()) != null) {
				lines[i] = lines[i].replaceAll("\\s+$", ""); 
				if (!lines[i].equals("")) {
					i++;
				}
			}
			filein.close();
			settings.put(setting, value);
		} catch (Exception e) {
			e.printStackTrace();
		}

		FileOutputStream fileout;
		try {
			fileout = new FileOutputStream(settingsfile);
			for (int n = 0; n < lines.length; n++) {
				if (lines[n] != null) {
					new PrintStream(fileout).println(lines[n]);
				}
			}
			new PrintStream(fileout).println(setting + " " + value);
			fileout.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void deleteSetting(String setting) {
		// read whole file, remove offending line, write whole file
		setting = setting.replaceAll("\\s+$", ""); 
		FileInputStream filein;
		String[] lines = new String[999];
		try {
			filein = new FileInputStream(settingsfile);
			BufferedReader reader = new BufferedReader(new InputStreamReader(filein));
			int i = 0;
			while ((lines[i] = reader.readLine()) != null) {
				String items[] = lines[i].split(" ");
				if ((items[0].toUpperCase()).equals(setting.toUpperCase())) {
					lines[i] = null;
				}
				i++;
			}
			filein.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		FileOutputStream fileout;
		try {
			fileout = new FileOutputStream(settingsfile);
			for (int n = 0; n < lines.length; n++) {
				if (lines[n] != null) {
					new PrintStream(fileout).println(lines[n]);
				}
			}
			fileout.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

    public String readHTTPport() {

        return "5080"; // TODO:

// 		String filenm = System.getenv("RED5_HOME") + Util.sep+"conf"+Util.sep+"red5.properties";
//		FileInputStream filein;
//		String result = null;
//		try {
//			filein = new FileInputStream(filenm);
//			BufferedReader reader = new BufferedReader(new InputStreamReader(
//					filein));
//			String line = "";
//			while ((line = reader.readLine()) != null) {
//				String s[] = line.split("=");
//				if (s[0].equals(str)) {
//					result = s[1];
//				}
//			}
//			filein.close();
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		return result;
	}

	public boolean getBoolean(GUISettings setting) {
		return getBoolean(setting.toString());
	}

	public boolean getBoolean(ManualSettings setting) {
		return getBoolean(setting.toString());
	}
	
	public String readSetting(ManualSettings setting) {
		return readSetting(setting.toString());
	}
	
	public String readSetting(GUISettings setting) {
		return readSetting(setting.toString());
	}
	
	public int getInteger(ManualSettings setting) {
		return getInteger(setting.toString());
	}	
	
	public int getInteger(GUISettings setting) {
		return getInteger(setting.toString());
	}

	public boolean getBoolean(values key) {
		return getBoolean(key.name());
	}

	public double getDouble(GUISettings setting) { return getDouble(setting.name()); }

	public double getDouble(ManualSettings setting) { return getDouble(setting.name()); }

	public long getLong(ManualSettings setting) { return Long.valueOf(readSetting(setting)); }

	public long getLong(GUISettings setting) { return Long.valueOf(readSetting(setting)); }

}
