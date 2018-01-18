package oculusPrime;

public enum PlayerCommands { 
	    
    // server
    uptime, restart, quitserver, showlog, writesetting, readsetting, settings, log,
	relayconnect, relaydisable,
    // not typically used by scripts, undocumented:
    softwareupdate, relaydisconnect,

	// operating system
    reboot, systemshutdown, memory, systemcall, setsystemvolume, cpu, waitforcpu, 
	networkconnect,
	// not typically used by scripts, undocumented:
	networksettings,

	//user, accounts
    who, chat, disconnectotherconnections, driverexit, messageclients,
	logout, // undocumented
    
    //undocumented (not typically used by scripts):
    password_update, beapassenger, assumecontrol,  
    new_user_add, user_list, delete_user, extrauser_password_update, username_update,

    //docking
    dock, dockgrab, autodock,  redock,
    // not typically used by scripts, undocumented:
    docklineposupdate, autodockcalibrate, dockgrabtest,


	// power board
    battstats, powerreset, powercommand,
	// not typically used by scripts, undocumented:
    erroracknowledged,
	// undocumented
	powershutdown, // added timed interval option
    
    // video/audio
    streamsettingscustom, playerbroadcast, videosoundmode, publish,
    streamsettingsset, framegrabtofile,	record,
	// experimental (undocumented):
	jpgstream, streammode,
    
    // malg board 
    motorsreset, getdrivingsettings, drivingsettingsupdate, malgcommand,
    // wheels
    clicksteer, motionenabletoggle, speed, move, nudge, forward, backward, left, right, 
    odometrystart, odometryreport, odometrystop, lefttimed, righttimed, forwardtimed,
	arcmove, calibraterotation,
    // lights and camera tilt
    strobeflash, spotlight, floodlight, cameracommand, camtilt,
	// undocumented (unused):
    fwdflood, rotate,

	// navigation
	roslaunch, savewaypoints, gotowaypoint, startnav, stopnav,
	gotodock, saveroute, runroute, cancelroute, startmapping, savemap,

	// sensing
	getlightlevel, setstreamactivitythreshold,
	objectdetect, objectdetectcancel,
	motiondetect, motiondetectcancel, sounddetect,
	// not typically used by scripts, undocumented:
	objectdetectstream, motiondetectstream,

	// un-categorized
	speech, serverbrowser, email, state, rssadd,

    // experimental (undocumented)
    opennisensor, clearmap, test,

    // deprecated (kept for mobile client compatibility, undocumented)
    spotlightsetbrightness,
    
    // undocumented    
    statuscheck, block, unblock, getemailsettings, emailsettingsupdate,
    
    // file manage 
	deletelogs, truncmedia, archivelogs, archivenavigation, 
	
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
