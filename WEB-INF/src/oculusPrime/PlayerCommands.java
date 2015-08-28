package oculusPrime;

public enum PlayerCommands { 
	    
    // server
    uptime, restart, quitserver, showlog, writesetting, readsetting, settings, log,
    //undocumented (not typically used by scripts):
    softwareupdate, 
    
    // system
    reboot, systemshutdown, memory, systemcall, setsystemvolume, cpu,
    
    //user, accounts
    who, chat, disconnectotherconnections, driverexit, loginrecords, messageclients,  
    //undocumented (not typically used by scripts):
    password_update, beapassenger, assumecontrol,  
    new_user_add, user_list, delete_user, extrauser_password_update, username_update,
        
    //docking
    dock, dockgrab, autodock,  redock,
    //undocumented (not typically used by scripts):
    docklineposupdate, autodockcalibrate, dockgrabtest,  

    // power board
    battstats, powerreset, powershutdown,   
	// undocumented (not typically used by scripts):
    erroracknowledged, powercommand,
    
    // video/audio (flash)
    streamsettingscustom, playerbroadcast, setstreamactivitythreshold, videosoundmode, publish, 
    streamsettingsset, motiondetect, motiondetectcancel, motiondetectstream, framegrabtofile,
	jpgstream,
    
    // malg board 
    motorsreset, cameracommand, camtilt, getdrivingsettings, drivingsettingsupdate,
    // wheels
    clicksteer, motionenabletoggle, speed, move, nudge, forward, backward, left, right, 
    odometrystart, odometryreport, odometrystop, lefttimed, righttimed, forwardtimed,
    // lights
    strobeflash, spotlight, floodlight,
    // undocumented (unused):
    fwdflood,
    
	// un-categorized
	speech, serverbrowser, email, state, getemailsettings, emailsettingsupdate,
    rssadd, getlightlevel, objectdetectstream, objectdetect, objectdetectcancel,
    
	// undocumented    
    statuscheck, block, unblock, // help, 
    
    // experimental (undocumented)
    opennisensor, clearmap, error,
    
    // navigation
    roslaunch, savewaypoints, gotowaypoint, startnav, stopnav,
    gotodock, saveroute, runroute, cancelroute, startmapping, savemap,
    
    // deprecated (kept for mobile client compatibility, undocumented)
    spotlightsetbrightness;
	
	// sub-set that are restricted to "user0"
	public enum AdminCommands {
		docklineposupdate, autodockcalibrate, getemailsettings, emailsettingsupdate,
		getdrivingsettings, drivingsettingsupdate,  
		systemcall, 
		new_user_add, user_list, delete_user, extrauser_password_update, username_update, 
		disconnectotherconnections, showlog, softwareupdate,
		arduinoreset, muterovmiconmovetoggle, 
	    writesetting, holdservo, opennisensor, videosoundmode, restart, shutdown,
	    setstreamactivitythreshold, email, state, uptime, help, memory, who, 
	    loginrecords, settings, messageclients, dockgrabtest, rssaddb, block, 
	    unblock, powershutdown, reboot, systemshutdown, clearmap, erroracknowledged;	

	}
	
	// @return true if given command is in the sub-set
	public static boolean requiresAdmin(final String str) {
		try {
			AdminCommands.valueOf(str);
		} catch (Exception e) {return false;}
		
		return true; 
	} 
	
	// @return true if given command is in the sub-set 
	public static boolean requiresAdmin(final PlayerCommands cmd) {
		try {
			AdminCommands.valueOf(cmd.name());
		} catch (Exception e) {return false;}
		
		return true; 
	}	
}
