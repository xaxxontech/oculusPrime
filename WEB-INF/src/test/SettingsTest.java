package test;

import static org.junit.Assert.*;

import java.util.Properties;

import oculusPrime.GUISettings;
import oculusPrime.ManualSettings;
import oculusPrime.PlayerCommands;
import oculusPrime.Settings;

import org.junit.Before;
import org.junit.Test;

public class SettingsTest {

	// force red5 path 
	oculusPrime.Settings settings = null; // new oculusPrime.Settings.getReference();// "../../");

	@Before
	public void setUp() {
		System.out.println("running: " + getClass().toString());
		
		if(settings==null) fail("no settings file found");
		
		if(Settings.settingsfile != null)
			if(Settings.settingsfile.contains("null"))
				fail("no settings file found");
		
		if(settings.readSetting("salt").equals("null")) fail("no salt!"); 
	}

	@Test
	public void testReadSetting() {	
		for (GUISettings factory : GUISettings.values()){ 
			if(settings.readSetting(factory.toString())==null)
				fail("setting missing in file: " + factory.toString());
		}
		
		for (ManualSettings factory : ManualSettings.values()) {
			if(settings.readSetting(factory.toString())==null)
				fail("setting missing in file: " + factory.toString());
		}
	}

	@Test 
	public void playerCommands(){
		
		// make sure no duplicates in Telnet and Player Commands 
		for (PlayerCommands factory : PlayerCommands.values()) {
			String val = factory.toString();
			for (oculusPrime.TelnetServer.Commands cmd : oculusPrime.TelnetServer.Commands.values()){
				if(cmd.toString().equals(val)) 
					fail("player commands overlap telnet commands: " + val);				
			}
		}
		
		// make sure no duplicates in Telnet and Player Commands 
		for (oculusPrime.TelnetServer.Commands factory : oculusPrime.TelnetServer.Commands.values()) {
			String val = factory.toString();
			for (PlayerCommands cmd : PlayerCommands.values()){
				if(cmd.toString().equals(val)) 
					fail("player commands overlap telnet commands: " + val);				
			}
		}
		
		// make sure is a subset of player commands
		for (PlayerCommands.RequiresArguments command : PlayerCommands.RequiresArguments.values()) {
			PlayerCommands ply = null;
			try {
				ply = PlayerCommands.valueOf(command.toString());
			} catch (Exception e) {}
			if(ply==null) fail(" not a sub-set of playerCommands: "+command.toString());
		}
	
		// make sure is a subset of player commands
		for (PlayerCommands.AdminCommands command : PlayerCommands.AdminCommands.values()) {
			PlayerCommands ply = null;
			try {
				ply = PlayerCommands.valueOf(command.toString());
			} catch (Exception e) {}
			if(ply==null) fail(" not a sub-set of PlayerCommand: "+command.toString());
		}	
		
		// make sure is a subset of player commands
		for (PlayerCommands.HelpText command : PlayerCommands.HelpText.values()) {
			PlayerCommands ply = null;
			try {
				ply = PlayerCommands.valueOf(command.toString());
			} catch (Exception e) {}
			if(ply==null) fail(" not a sub-set of playerCommands: "+command.toString());
		}
	
		System.out.println("BOOLEAN: " + PlayerCommands.RequiresArguments.find("{BOOLEAN}"));
		System.out.println("INT: " + PlayerCommands.RequiresArguments.find("{INT}"));
		System.out.println("STRING: " + PlayerCommands.RequiresArguments.find("{STRING}"));
		System.out.println("DOUBLE: " + PlayerCommands.RequiresArguments.find("{DOUBLE}"));
		System.out.println("[0-100]: " + PlayerCommands.RequiresArguments.find("[0-100]"));
		System.out.println("[0-255]: " + PlayerCommands.RequiresArguments.find("[0-255]"));
		System.out.println("USE RANGE: " + PlayerCommands.RequiresArguments.rangeList());
		System.out.println("NEEDS PARSE: " + PlayerCommands.RequiresArguments.parseList());
		System.out.println("USE STRING: " + PlayerCommands.RequiresArguments.stringList());
		
		/*	if(PlayerCommands.RequiresArguments.tilttest.vaildRange("100"))
			System.out.println("tiltest is 100 in range");
		else fail("RANGE TEST ERROR");
		
		if( ! PlayerCommands.RequiresArguments.tilttest.vaildRange("-100"))
			System.out.println("tilttest -100 is NOT in range");
		else fail("RANGE TEST ERROR");
		
	
		if( PlayerCommands.RequiresArguments.drivingsettingsupdate.usesDouble() ){
			if(PlayerCommands.RequiresArguments.drivingsettingsupdate.matchesArgument("1.4")){
				System.out.println("drivingsettingsupdate requires double");
			} else fail("can't detect double argument");
		}*/
		
	}
	
	
	@Test
	public void validateDefaultSetting() {
		Properties defaults = GUISettings.createDeaults();
		for (GUISettings factory : GUISettings.values()) {
			String val = factory.toString();
			if (!defaults.containsKey(val))
				fail("default setting missing: " + factory.toString());
		}
		
		if(defaults.getProperty(GUISettings.vlow.toString()).split("_").length != 4) 
			 fail("vlow default values are invalid");
		if(defaults.getProperty(GUISettings.vmed.toString()).split("_").length != 4) 
			 fail("vmed default values are invalid");
		if(defaults.getProperty(GUISettings.vhigh.toString()).split("_").length != 4) 
			 fail("vhigh default values are invalid");
		if(defaults.getProperty(GUISettings.vfull.toString()).split("_").length != 4) 
			 fail("vfull default values are invalid");
		
		if(settings.readSetting(GUISettings.vlow.toString()).split("_").length != 4) 
			 fail("vlow settings are invalid");
		if(settings.readSetting(GUISettings.vmed.toString()).split("_").length != 4) 
			 fail("vmed settings are invalid");
		if(settings.readSetting(GUISettings.vhigh.toString()).split("_").length != 4) 
			 fail("vhigh settings are invalid");
		if(settings.readSetting(GUISettings.vfull.toString()).split("_").length != 4) 
			 fail("vfull settings are invalid");
		
	}

}
