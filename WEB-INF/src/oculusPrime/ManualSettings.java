package oculusPrime;

import java.util.Properties;

import oculusPrime.commport.Discovery;

/** place extensions to settings here */
public enum ManualSettings {
	
	motorport, powerport, developer, debugenabled, diagnostic, telnetport, commandport, stopdelay, vself, wheeldiameter, loginnotify, gyrocomp, 
	email_smtp_server, email_smtp_port, email_username, email_password, email_from_address; 
	
	 // new counter to see if constantly searching

	/** get basic settings */
	public static Properties createDeaults(){
		Properties config = new Properties();
		config.setProperty(diagnostic.name(), "false");
		config.setProperty(developer.name(), "false");
		config.setProperty(debugenabled.name(), "false");
		config.setProperty(stopdelay.name(), "500");
		config.setProperty(vself.name(), "320_240_8_85");
		config.setProperty(motorport.name(), Discovery.params.discovery.name());
		config.setProperty(powerport.name(), Discovery.params.discovery.name());
		config.setProperty(email_smtp_server.name(), Settings.DISABLED);
		config.setProperty(email_smtp_port.name(), "25");
		config.setProperty(email_username.name(), Settings.DISABLED);
		config.setProperty(email_password.name(), Settings.DISABLED);
		config.setProperty(email_from_address.name(), Settings.DISABLED);
		config.setProperty(commandport.name(), Settings.DISABLED);
		config.setProperty(telnetport.name(), Settings.DISABLED);
		config.setProperty(wheeldiameter.name(), "110");
		config.setProperty(loginnotify.name() , "false");
		config.setProperty(gyrocomp.name() , "1.09");
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
