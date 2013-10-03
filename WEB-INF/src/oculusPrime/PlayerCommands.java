package oculusPrime;

import java.util.Arrays;
import java.util.List;
import java.util.Vector;


/**
 * JUnit tests will validate the sub-sets player commands and the sub-sets. 
 */
public enum PlayerCommands {
	
	// all valid commands
	publish, floodlight, move, nudge, slide, dockgrab, battstats, docklineposupdate, autodock,  autodockcalibrate, 
	speech, getdrivingsettings, drivingsettingsupdate,  cameracommand, cameratoposition, 
	speedset, dock, relaunchgrabber, clicksteer, chat, statuscheck, systemcall, streamsettingsset, 
	streamsettingscustom, motionenabletoggle, playerexit, playerbroadcast, password_update, 
	new_user_add, user_list, delete_user, extrauser_password_update, username_update, 
	disconnectotherconnections, showlog, assumecontrol, softwareupdate,
	arduinoecho, arduinoreset, setsystemvolume, beapassenger, muterovmiconmovetoggle, spotlight, 
	spotlightsetbrightness, writesetting, holdservo, opennisensor, videosoundmode, pushtotalktoggle, restart, shutdown,
    setstreamactivitythreshold, email, state, uptime, help, framegrabtofile, memory, who, 
    loginrecords, settings, analogwrite, digitalread, messageclients, dockgrabtest, rssadd, rotate,
    getlightlevel, strobeflash;
	
	enum autodockargs { go, cancel, framegrab };
	
	enum dockargs { dock, cancle, undock };
	
	/** get text for any player command */
	public String getHelp(){
		return HelpText.valueOf(this.name()).getText();
	}
	
	// sub-set that are restricted to "user0"
	public enum AdminCommands {
		docklineposupdate, autodockcalibrate, 
		getdrivingsettings, drivingsettingsupdate,  
		systemcall, 
		new_user_add, user_list, delete_user, extrauser_password_update, username_update, 
		disconnectotherconnections, showlog, softwareupdate,
		arduinoecho, arduinoreset, muterovmiconmovetoggle, 
	    writesetting, holdservo, opennisensor, videosoundmode, restart, shutdown,
	    setstreamactivitythreshold, email, state, uptime, help, framegrabtofile, memory, who, 
	    loginrecords, settings, analogwrite, digitalread, messageclients, dockgrabtest, rssadd;	
	}
	
	// sub-set that are require parameters 
	public enum RequiresArguments {
	
		publish("camera", "camandmic", "mic", "stop"), 
		floodlight("{INT}"), 
		move("left", "right", "forward", "backward", "stop"),
		nudge("left", "right", "forward", "backward"),
		slide("left", "right"), 
		docklineposupdate("{INT}"),
		autodock("cancel", "go", "dockgrabbed", "dockgrabbed {STRING}", "calibrate", "getdocktarget"),
		autodockcalibrate("{INT} {INT}"),
		speech("{STRING}"),
		drivingsettingsupdate("[0-255] [0-255] {INT} {INT} {DOUBLE} {INT}"),
		cameracommand("stop", "up", "down", "horiz", "downabit", "upabit", "frontstop", "rearstop"),
		speedset("slow", "med", "fast"), 
		dock("dock", "undock"),
		clicksteer("{INT} {INT}"), 
		chat("{STRING}"), 
		systemcall("{STRING}"), 
		streamsettingsset("low","med","high","full","custom"), 
		playerbroadcast("camera", "camadnmic", "mic", "stop"), 
		password_update("{STRING}"), 
		new_user_add("{STRING} {STRING}"), 
		delete_user("{STRING}"), 
		extrauser_password_update("{STRING} {STRING}"), 
		username_update("{STRING} {STRING}"), 
		assumecontrol("{STRING}"), 
		softwareupdate("check", "download","versiononly"),
		arduinoecho("{BOOLEAN}"),
		setsystemvolume("[0-100]"), 
		beapassenger("{STRING}"), 
		spotlight("0","10","20","30","40","50","60","70","80","90","100"), 
		writesetting("{STRING} {STRING}"), 
		holdservo ("{BOOLEAN}"), 
		opennisensor("on", "off"), 
		videosoundmode("low", "high"), 
		pushtotalktoggle("{BOOLEAN}"),
		setstreamactivitythreshold("[0-100] [0-100]"),
		email("{STRING}"),
		analogread("{INT}"),
		digitalread("{INT}"),
		rssadd("{STRING}"),
		messageclients("{STRING}");
			
		private final List<String> values;

		RequiresArguments(String ...values) {
			this.values = Arrays.asList(values);
		}

		public List<String> getValues() {
			return values;
		}
		
		
			/*
		public boolean vaildRange(final String target){
			try {
				
				String list = this.getValues().toString();
				list = list.substring(list.lastIndexOf("[")+1, list.indexOf("]"));
			
				String start = list.substring(0, list.indexOf("-"));
				String end = list.substring(list.indexOf("-")+1, list.length());
				int s = Integer.parseInt(start);
				int e = Integer.parseInt(end);
				int t = Integer.parseInt(target);
				
				// range check 
				if(((s <= t) && (t <= e))) return true;
				
			} catch (Exception e) {
				Util.log("PlayerCommands.validRange() :" + e.getLocalizedMessage());
			}
			
			return false;
		}
		*/
	
		/** check if this command has complex formating */
		public boolean requiresParse(){
			String[] args = this.getArgumentList();
			if(args.length == 1){
				
				String[] params = args[0].split(" "); 
				if(params.length == 1){
					
					// System.out.println("requiresParse: only one: " + this.name() + " " + args[0]);
					
					if(this.usesString()){
						return false;
					} else if(this.usesBoolean()){
						return false;
					} else if(this.usesInt()){
						return false;
					} else if(this.usesRange()){
						return false;
					} else if(this.usesDouble()){
						return false;
					}
				} 	
			
				// parse me! 
				return true;	
			}
			
			return false;
		}
		
		public boolean usesRange(){
			Object[] list = this.getValues().toArray();
			for(int i = 0 ; i < list.length ; i++)
				if(list[i].toString().contains("["))
					return true;
			
			return false;
		}
		
		public boolean usesBoolean(){
			Object[] list = this.getValues().toArray();
			for(int i = 0 ; i < list.length ; i++)
				if(list[i].toString().contains("{BOOLEAN}"))
					return true;
			
			return false;
		}
			
		public boolean usesInt(){
			Object[] list = this.getValues().toArray();
			for(int i = 0 ; i < list.length ; i++)
				if(list[i].toString().contains("{INT}"))
					return true;
			
			return false;
		}
		
		public boolean usesDouble(){
			Object[] list = this.getValues().toArray();
			for(int i = 0 ; i < list.length ; i++)
				if(list[i].toString().contains("{DOUBLE}"))
					return true;
			
			return false;
		}
		
		public boolean usesString(){
			Object[] list = this.getValues().toArray();
			for(int i = 0 ; i < list.length ; i++)
				if(list[i].toString().contains("{STRING}"))
					return true;
			
			return false;
		}
		
		public boolean matchesArgument(String target) {
			return this.getValues().contains(target);
		}
		
		public String getArguments(){
			String list = this.getValues().toString();
			list = list.substring(1, list.length()-1);
			list = list.replace(",", " | ");
			return list.trim();
		}
		
		public String[] getArgumentList(){
			return (String[]) this.getValues().toArray(); 
		}	
		
		/* get all the commands that require the given argument */
		public static Vector<String> find(String name) {
			Vector<String> match = new Vector<String>();
		    for (RequiresArguments lang : RequiresArguments.values())
		        if (lang.getValues().contains(name)) 
		            match.add(lang.name());
		        
		    // more matches
		    for (RequiresArguments lang : RequiresArguments.values())
		    	if (lang.getArgumentList()[0].contains(name))
		    		match.add(lang.name());
		    	
		    return match;
		}
		
		/* get all that use range */
		public static Vector<String> rangeList() {
			Vector<String> match = new Vector<String>();
		    for (RequiresArguments lang : RequiresArguments.values())
		        if (lang.usesRange()) 
		            match.add(lang.name());
		    
		    return match;
		}

		public static Vector<String> stringList() {
			Vector<String> match = new Vector<String>();
		    for (RequiresArguments lang : RequiresArguments.values())
		        if (lang.usesString()) 
		            match.add(lang.name());
		        
		    return match;
		}
		
		public static Vector<String> parseList() {
			Vector<String> match = new Vector<String>();
		    for (RequiresArguments lang : RequiresArguments.values())
		        if (lang.requiresParse()) 
		            match.add(lang.name());
		        
		    return match;
		}
	}
	
	public enum HelpText{ 
		
		publish("Robot video/audio control"), 
		floodlight("Controls wide angle light"), 
		move("Wheel motors, continuous movement"),
		nudge("Move for amount of milliseconds specified by 'nudgedelay' setting, then stop"),
		slide("Rearward triangular movement macro, that positions robot slightly to the left or right of starting spot"), 
		dockgrab("Find dock target within robots camera view, returns target metrics. Robot camera must be running"),
		battstats("Returns battery charging state and charge remaining"),
		docklineposupdate("Set manual dock line position within camera FOV in +/- pixels offset from center"),
		autodock("Autodocking system control. Camera must be running"),
		autodockcalibrate("Start autodock calibration, by sending xy pixel position of a white area within the dock target"),
		speech("Voice synthesizer"),
		getdrivingsettings("Returns drive motor calibration settings"), 
		drivingsettingsupdate("Set drive motor calibration settings: slow speed, medium speed, nudge delay, maxclicknudgedelay (time in ms for robot to turn 1/2 of screen width), momentum factor, steering compensation"),
		cameracommand("Camera tilt motor movement"),
		speedset("Set drive motor speed"), 
		dock("Start manual dock routine"), 
		relaunchgrabber("Launch server.html browser page on robot"), 
		clicksteer("Camera tilt and drive motor movement macro to re-position center of screen by x,y pixels"), 
		chat("Send text chat to all other connected users"), 
		statuscheck("request current statuses for various settings/modes. Call with 'battstats' to also get battery status"),
		systemcall("Execute OS system command"), 
		streamsettingsset("Set robot camera resolution and quality"), 
		streamsettingscustom("Set values for 'custom' stream: resolutionX_resolutionY_fps_quality"), 
		motionenabletoggle("Enable/disable robot drive motors"),
		playerexit("End rtmp connection with robot"),
		playerbroadcast("Client video/audio control (to be broadcast thru robot screen/speakers)"), 		
		password_update("Set new password for currently connected user"), 
		new_user_add("Add new user with 'username' 'password'"), 
		user_list("Returns list of user accounts"), 
		delete_user("Delete user 'username'"), 
		extrauser_password_update("Set new password for user with 'username' 'password'"), 
		username_update("Change non-connected username with 'oldname' 'newname'"), 
		disconnectotherconnections("Close rtmp connection with all user connections other than current connection"), 
		showlog("Returns partial jvm.stdout"),
		assumecontrol("Assume control from current drive, specify new driver 'username'"), 
		softwareupdate("Robot server software update control"),
		arduinoecho("Set ArduinOculus microcontroller to echo all commands"),
		arduinoreset("Reset ArduinOculus microcontroller"),
		setsystemvolume("Set robot operating system audio volume 0-100"), 
		beapassenger("Be passenger of current driver, specify passenger 'username'"), 
		muterovmiconmovetoggle("Set/unset mute-rov-mic-on-move' setting "),
		spotlight("Set main spotlight brightness. 0=off"), 
		writesetting("Write setting to oculus_settings.txt"), 
		holdservo ("Set/unset use of power break for persicope servo"), 
		opennisensor("Kinect/Xtion Primesense sensor control"), 
		videosoundmode("Set robot video compression codec"), 
		pushtotalktoggle("When broadcasting client mic through robot speakers, always on or mute until keypress"),
		restart("Restart server application on robot"),
		shutdown("Quit server application on robot"),
		setstreamactivitythreshold("Set video motion, audio volume detection threshold, 0-100 (0=off)"),
		email("Send email with params: emailto [subject] body"),
		state("With 1 or 0 args, returns list of one or all non null state key/value pairs, 2 args sets key to value"),
		uptime("Returns server uptime, in milliseconds"),
		help("Returns complete list of available commands. Add COMMAND as argument for extended command info"),
		framegrabtofile("Saves a frame from video stream to JPG in folder Oculus/webapps/oculus/framegrabs"),
		memory("Returns memory in use by the Java Virtual Machine"),
		who("Returns info on current connected users"),
		loginrecords("Returns list of RTMP driver login history for current server session"),
		settings("Returns list of all settings from oculus_settings.txt"),
		analogwrite("Sends command �a� followed by two bytes (pin #, value) to ArduinOculus microcontroller"),
		digitalread("Sends command �d� followed by byte (pin #) to ArduinOculus microcontroller"),
		messageclients("Send text to all other connected users. Similar to �chat,� but without preceding user info"),
		rssadd("Create new rss feed item with params: [title] description");

        private final String message;

        HelpText(String msg) {
        	this.message = msg;
        }
        
        public String getText(){
        	return message;
        }
	}
	
	/** */
	public static boolean validBoolean(final String arg){
		if(arg.equalsIgnoreCase("true") || arg.equalsIgnoreCase("false")) return true;
		
		return false;
	}
	
	/** */
	public static boolean validInt(final String arg){
		try {
			Integer.parseInt(arg);
		} catch (NumberFormatException e) {
			return false;
		}
		return true;
	}
	
	/** */
	public static boolean validDouble(final String arg){
		try {
			Double.parseDouble(arg);
		} catch (NumberFormatException e) {
			return false;
		}
		
		return true;
	}
	
	/** */
	public boolean requiresArgument() {
		try {
			RequiresArguments.valueOf(this.name());
		} catch (Exception e) {
			return false;
		}
		
		return true; 
	}
	
	/** */
	public static boolean requiresArgument(final String cmd) {
		try {
			RequiresArguments.valueOf(cmd);
		} catch (Exception e) {
			return false;
		}
		
		return true; 
	}
	
	/** @return true if given command is in the sub-set */
	public static boolean requiresAdmin(final String str) {
		try {
			AdminCommands.valueOf(str);
		} catch (Exception e) {return false;}
		
		return true; 
	}
	
	/** @return true if given command is in the sub-set */
	public static boolean requiresAdmin(final PlayerCommands cmd) {
		try {
			AdminCommands.valueOf(cmd.name());
		} catch (Exception e) {return false;}
		
		return true; 
	}
	
	/** @return a formated list of the commands */
	public static String getCommands(){
		
		String help = new String();
	
		// print the full list 
		for (PlayerCommands factory : PlayerCommands.values()) {
			
			help += factory.name();
			if(factory.requiresArgument()) {
				
				RequiresArguments req = PlayerCommands.RequiresArguments.valueOf(factory.name());
				help += " " + req.getArguments();
				
				if(req.requiresParse()) help += " (parse required)";
			
			} else help += (" (no arguments)");
				
			if(PlayerCommands.requiresAdmin(factory)) help +=(" (admin only)");
			help += "<br>";
		}
	
		return help;
	}
		
	public static String help(String command) {
		String result = "";
		if(command.matches("\\S+")){ // isn't blank
			
			if(PlayerCommands.requiresArgument(command)){
				
				result += "requires argument: " + 
					PlayerCommands.RequiresArguments.valueOf(command).getValues().toString().replace(",", " | ")
					+ "<br>";
				
			}else{
				String helptxt = null;
				try {
					helptxt = PlayerCommands.HelpText.valueOf(command).getText();
				} catch (Exception e) {}
				
				if(helptxt==null) {
					result += "no match for: " + (command);
					return result;
				}
				
				result +="requires no argument(s)<br>";
			}
			
			// give help, they are doing something wrong 
			result += "description: " + PlayerCommands.HelpText.valueOf(command).getText();
			
	} else { // just puke the list
	
		// print all commands 
		result += PlayerCommands.getCommands();
		
		for (TelnetServer.Commands commands : TelnetServer.Commands.values()) 
			result += commands + " (telnet only)<br>";
		
	}	
		return result;
	}
}
