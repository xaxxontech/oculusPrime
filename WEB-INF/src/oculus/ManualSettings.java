package oculus;

import java.util.Properties;

import oculus.commport.Discovery;

/** place extensions to settings here */
public enum ManualSettings {
	
	lastboot, attempts, firmware, serialport, developer, debugenabled, commandport, stopdelay, vself,
	email_smtp_server, email_smtp_port, email_username, email_password, email_from_address; 
	
	 // new counter to see if constantly searching

	/** get basic settings */
	public static Properties createDeaults(){
		Properties config = new Properties();
		config.setProperty(developer.toString(), "false");
		config.setProperty(debugenabled.toString(), "false");
		config.setProperty(stopdelay.toString(), "500");
		config.setProperty(vself.toString(), "320_240_8_85");
		config.setProperty(serialport.name(), Discovery.params.discovery.name());
		config.setProperty(email_smtp_server.name(), Settings.DISABLED);
		config.setProperty(email_smtp_port.name(), "25");
		config.setProperty(email_username.name(), Settings.DISABLED);
		config.setProperty(email_password.name(), Settings.DISABLED);
		config.setProperty(email_from_address.name(), Settings.DISABLED);
		config.setProperty(commandport.name(), Settings.DISABLED);
		config.setProperty(attempts.name(), "0");
		return config;
	}
	
	public static String getDefault(ManualSettings setting){
		Properties defaults = createDeaults();
		return defaults.getProperty(setting.name());
	}
	
	public static boolean isDefault(ManualSettings manual){
		Settings settings = Settings.getReference();
		if(settings.readSetting(manual).equals(getDefault(manual))) return true;
		
		return false;
	}
}
