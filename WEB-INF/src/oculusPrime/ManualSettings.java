package oculusPrime;

import java.util.Properties;

public enum ManualSettings {
	motorport, powerport, developer, debugenabled, telnetport, wheeldiameter,
	gyrocomp, alertsenabled,
	email_smtp_server, email_smtp_port, email_username, email_password, email_from_address, email_to_address,
	soundthreshold, motionthreshold;

	/** get basic settings */
	public static Properties createDeaults(){
		Properties config = new Properties();
		config.setProperty(developer.name(), "false");
		config.setProperty(debugenabled.name(), "false");
		config.setProperty(motorport.name(), Settings.ENABLED);
		config.setProperty(powerport.name(), Settings.ENABLED);
		config.setProperty(email_smtp_server.name(), Settings.DISABLED);
		config.setProperty(email_smtp_port.name(), "25");
		config.setProperty(email_username.name(), Settings.DISABLED);
		config.setProperty(email_password.name(), Settings.DISABLED);
		config.setProperty(email_from_address.name(), Settings.DISABLED);
		config.setProperty(email_to_address.name(), Settings.DISABLED);
		config.setProperty(telnetport.name(), Settings.DISABLED);
		config.setProperty(wheeldiameter.name(), "106");
		config.setProperty(gyrocomp.name() , "1.095");
		config.setProperty(alertsenabled.name() , "true");
		config.setProperty(soundthreshold.name(), "10");
		config.setProperty(motionthreshold.name(), "10");
		config.setProperty(motionthreshold.name(), "0.003");
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
