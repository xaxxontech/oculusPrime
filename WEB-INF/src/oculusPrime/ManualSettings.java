 package oculusPrime;

import java.util.Properties;

public enum ManualSettings {
	
	motorport, powerport, developer, debugenabled, wheeldiameter,
	gyrocomp, alertsenabled, odomturnpwm, odomlinearpwm, checkaddresses,
	soundthreshold, motionthreshold, redockifweakconnection,

	useflash, arcmovecomp, usearcmoves, arcpwmthreshold, restarted, // undocumented
	;
	
	/** get basic settings, set defaults for all */
	public static Properties createDeaults(){
		Properties config = new Properties();
		config.setProperty(developer.name(), Settings.FALSE);
		config.setProperty(debugenabled.name(), Settings.FALSE);
		config.setProperty(motorport.name(), Settings.ENABLED);
		config.setProperty(powerport.name(), Settings.ENABLED);
		config.setProperty(checkaddresses.name(), Settings.TRUE);
		config.setProperty(wheeldiameter.name(), "106");
		config.setProperty(gyrocomp.name() , "1.095");
		config.setProperty(alertsenabled.name() , Settings.TRUE);
		config.setProperty(soundthreshold.name(), "10");
		config.setProperty(motionthreshold.name(), "0.003");
		config.setProperty(odomlinearpwm.name(), "150");
		config.setProperty(odomturnpwm.name(), "150");
		config.setProperty(redockifweakconnection.name(), Settings.TRUE);
		config.setProperty(useflash.name(), Settings.TRUE);
		config.setProperty(arcmovecomp.name(), "0.8");
		config.setProperty(usearcmoves.name(), Settings.TRUE);
		config.setProperty(restarted.name(), "0");
		config.setProperty(arcpwmthreshold.name(), "150");
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
