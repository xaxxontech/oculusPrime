package oculusPrime;

import java.util.Properties;

public enum GUISettings {

	/** these settings must be available in basic configuration */
	speedslow, speedmed, nudgedelay, fullrotationdelay, onemeterdelay,
	docktarget, vidctroffset, vlow, vmed, vhigh, vcustom, vset, maxclicknudgedelay, steeringcomp,
	maxclickcam, loginnotify, redock, navigation, telnetport,
	email_smtp_server, email_smtp_port, email_username, email_password, email_from_address, email_to_address,
	volume, reboot, camhoriz, camreverse,
	relayserver, relayserverauth, // undocumented
	;
	
	/** get basic settings */
	public static Properties createDeaults() {
		Properties config = new Properties();
		config.setProperty(speedslow.name() , "50");
		config.setProperty(speedmed.name() , "150");
		config.setProperty(docktarget.name() , "1.6666666_0.27447918_0.22083333_0.28177083_125_115_80_48_-0.041666668");
		config.setProperty(vidctroffset.name() , "0");
        config.setProperty(vlow.name() , "320_240_30_64");
        config.setProperty(vmed.name() , "640_480_15_256");
        config.setProperty(vhigh.name() , "1280_720_15_512");
		config.setProperty(vcustom.name() , "1280_720_8_85");
		config.setProperty(vset.name() , "vmed");
		config.setProperty(maxclicknudgedelay.name() , "180");
		config.setProperty(nudgedelay.name() , "80");
		config.setProperty(maxclickcam.name() , "30");
		config.setProperty(volume.name() , "100");
		config.setProperty(reboot.name() , Settings.FALSE);
		config.setProperty(fullrotationdelay.name(), "2700");
		config.setProperty(onemeterdelay.name(), "2400");
		config.setProperty(steeringcomp.name(), "L20");
		config.setProperty(camhoriz.name(), "70");
		config.setProperty(camreverse.name(), "138");
		config.setProperty(loginnotify.name() , Settings.FALSE);
		config.setProperty(redock.name() , Settings.FALSE);
		config.setProperty(navigation.name() , Settings.FALSE);
		config.setProperty(email_smtp_server.name(), Settings.DISABLED);
		config.setProperty(email_smtp_port.name(), "25");
		config.setProperty(email_username.name(), Settings.DISABLED);
		config.setProperty(email_password.name(), Settings.DISABLED);
		config.setProperty(email_from_address.name(), Settings.DISABLED);
		config.setProperty(email_to_address.name(), Settings.DISABLED);
		config.setProperty(telnetport.name(), TelnetServer.DEFAULTPORT);
		config.setProperty(relayserver.name(), Settings.DISABLED);
		config.setProperty(relayserverauth.name(), Settings.DISABLED);

		return config;
	}
	
	public static String getDefault(GUISettings factory) {
		Properties defaults = createDeaults();
		return defaults.getProperty(factory.name() );	
	}
	
}
