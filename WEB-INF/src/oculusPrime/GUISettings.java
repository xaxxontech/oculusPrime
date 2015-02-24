package oculusPrime;

import java.util.Properties;

public enum GUISettings {

	/** these settings must be available in basic configuration */
	skipsetup, speedslow, speedmed, nudgedelay, fullrotationdelay, onemeterdelay,
	docktarget, vidctroffset, vlow, vmed, vhigh, vfull, vcustom, vset, maxclicknudgedelay, steeringcomp, 
	maxclickcam, loginnotify, redock, navigation,
	volume, reboot, camhoriz, camreverse; 
	
	/** get basic settings */
	public static Properties createDeaults() {
		Properties config = new Properties();
		config.setProperty(skipsetup.name() , "no");
		config.setProperty(speedslow.name() , "50");
		config.setProperty(speedmed.name() , "150");
		config.setProperty(docktarget.name() , "1.6666666_0.27447918_0.22083333_0.28177083_125_115_80_48_-0.041666668");
		config.setProperty(vidctroffset.name() , "0");
		config.setProperty(vlow.name() , "320_240_4_85");
		config.setProperty(vmed.name() , "320_240_8_95");
		config.setProperty(vhigh.name() , "640_480_8_85");
		config.setProperty(vfull.name() , "640_480_8_95");
		config.setProperty(vcustom.name() , "1024_768_8_85");
		config.setProperty(vset.name() , "vmed");
		config.setProperty(maxclicknudgedelay.name() , "180");
		config.setProperty(nudgedelay.name() , "80");
		config.setProperty(maxclickcam.name() , "30");
		config.setProperty(volume.name() , "100");
//		config.setProperty(muteonrovmove.name() , "true"); 
		config.setProperty(reboot.name() , "false");
//		config.setProperty(pushtotalk.name() , "false");
		config.setProperty(fullrotationdelay.name(), "2700");
		config.setProperty(onemeterdelay.name(), "2400");
		config.setProperty(steeringcomp.name(), "L20");
		config.setProperty(camhoriz.name(), "70");
		config.setProperty(camreverse.name(), "138");
		config.setProperty(loginnotify.name() , "false");
		config.setProperty(redock.name() , "false");
		config.setProperty(navigation.name() , "false");
		
		return config;
	}
	
	public static String getDefault(GUISettings factory) {
		Properties defaults = createDeaults();
		return defaults.getProperty(factory.name() );	
	}
	
}
