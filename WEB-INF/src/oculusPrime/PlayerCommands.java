package oculusPrime;

public enum PlayerCommands { 
	    
    // server
    uptime, restart, quitserver, showlog, writesetting, readsetting, settings, log,
    //undocumented (not typically used by scripts):
    softwareupdate,
    
    // operating system
    reboot, systemshutdown, memory, systemcall, setsystemvolume, cpu,
	waitforcpu, // undocumented
    
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
    battstats, powerreset, powershutdown, powercommand,
	// undocumented (not typically used by scripts):
    erroracknowledged,
    
    // video/audio
    streamsettingscustom, playerbroadcast, videosoundmode, publish,
    streamsettingsset, framegrabtofile,
	// experimental (undocumented):
	jpgstream, streammode, record,
    
    // malg board 
    motorsreset, getdrivingsettings, drivingsettingsupdate, malgcommand,
    // wheels
    clicksteer, motionenabletoggle, speed, move, nudge, forward, backward, left, right, 
    odometrystart, odometryreport, odometrystop, lefttimed, righttimed, forwardtimed,
	arcmove,
    // lights and camera tilt
    strobeflash, spotlight, floodlight, cameracommand, camtilt,
	// undocumented (unused):
    fwdflood,

	// navigation
	roslaunch, savewaypoints, gotowaypoint, startnav, stopnav,
	gotodock, saveroute, runroute, cancelroute, startmapping, savemap,

	// sensing
	getlightlevel, setstreamactivitythreshold,
	objectdetect, objectdetectcancel,
	motiondetect, motiondetectcancel,
	// undocumented:
	objectdetectstream, motiondetectstream, sounddetect,

	// un-categorized
	speech, serverbrowser, email, state, rssadd,

    // experimental (undocumented)
    opennisensor, clearmap, test,

    // deprecated (kept for mobile client compatibility, undocumented)
    spotlightsetbrightness,
    
    // undocumented    
    statuscheck, block, unblock, getemailsettings, emailsettingsupdate,
	deletelogs, truncimages, truncros, truncarchive, archive,
	archiveros, archiveimages, archivelogs, calibraterotation, relayconnect,
	relaydisconnect, relaydisable, networksettings, networkconnect,

    ;
	
	// sub-set that are restricted to "user0"
	private enum AdminCommands {
		docklineposupdate, autodockcalibrate, getemailsettings, emailsettingsupdate,
		getdrivingsettings, drivingsettingsupdate,  
		systemcall, 
		new_user_add, user_list, delete_user, extrauser_password_update, username_update, 
		disconnectotherconnections, showlog, softwareupdate,
		arduinoreset, muterovmiconmovetoggle, 
	    writesetting, holdservo, opennisensor, videosoundmode, restart, shutdown,
	    setstreamactivitythreshold, email, state, uptime, help, memory, who, 
	    loginrecords, settings, messageclients, dockgrabtest, rssaddb, block, 
	    unblock, powershutdown, reboot, systemshutdown, clearmap, erroracknowledged,
		relayconnect, relaydisconnect, relaydisable, networksettings, networkconnect, test,

		;

	}

	// sub-set of commands that are NOT to be passed thru to relay client, if acting as relay server
	private enum nonRelayCommands {
		record, relaydisconnect, chat, beapassenger, assumecontrol,
		;
	}

	// @return true if given command is in the sub-set
	public static boolean nonRelayCommands(final PlayerCommands cmd) {
		try {
			nonRelayCommands.valueOf(cmd.name());
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
