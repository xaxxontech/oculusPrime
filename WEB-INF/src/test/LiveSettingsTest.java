package test;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

import oculus.ManualSettings;
import oculus.PlayerCommands;
import oculus.Settings;
import oculus.Util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class LiveSettingsTest {

	Socket socket;
	BufferedReader stdin;
	PrintWriter out;
	
	@Before
	public void setUp() throws NumberFormatException, UnknownHostException {
		System.out.println(getClass().toString());
		Settings settings = Settings.getReference();
			if(Settings.settingsfile != null)
				if(Settings.settingsfile.contains("null"))
					fail("no settings file found");
			
		try {
			socket = new Socket("127.0.0.1", settings.getInteger(ManualSettings.commandport));
		} catch (IOException e) {
			fail("can NOT connect");
		}
		
		if(socket != null){
			stdin = new BufferedReader(new InputStreamReader(System.in));
			try {
				out = new PrintWriter(new BufferedWriter(
						new OutputStreamWriter(socket.getOutputStream())), true);
			} catch (IOException e) {
				fail("can NOT connect to output socket");
			}
			
			
			// login on connect 
			String user = settings.readSetting("user0");
			String pass = settings.readSetting("pass0");
			out.println(user + ":" + pass);
			
			// read feedback
			new Thread(new Runnable() {
				@Override
				public void run() {
					BufferedReader in = null;
					try {
						in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
					} catch (Exception e) {
						return;
					}
				
					String input = null;
					while(true){
						try {
												
							input = in.readLine();
							if(input==null) break;
							else System.out.println(input.trim());
						
						} catch (IOException e) {
							System.out.println(e.getMessage());
							break;
						}
					}
					
					System.out.println(".. server closed socket, logged out.");
					
					try {
						in.close();
						socket.close();
					} catch (IOException e) {
						fail("socket close error");
					}
				}
			}).start();
		}		
	}

	@After
	public void tearDown() throws Exception {
		System.out.println("tearDown(), after.. done");	
		
		out.close();
		try {
			stdin.close();
		} catch (IOException e) {
			 fail("socket close error");
		}
		try {
			socket.close();
		} catch (IOException e) {
			 fail("socket close error");
		}
	}
	
	@Test
	public void testReadSetting() {
		
		if(out==null) fail("write error");
		
		Util.delay(3000);
		out.println(PlayerCommands.speech.toString() + " testing testing test 3 2 1");
		
		// send them all 
		for (PlayerCommands factory : PlayerCommands.values()) {
			if( ! factory.equals(PlayerCommands.restart)){
				if( ! PlayerCommands.requiresArgument(factory.toString())){
					out.println(factory.toString());
					System.out.println(factory.toString());
					Util.delay(500);
				}
			}
		}
		
		out.close();
		try {
			stdin.close();
		} catch (IOException e) {
			 fail("socket close error");
		}
		try {
			socket.close();
		} catch (IOException e) {
			 fail("socket close error");
		}
	}
}
