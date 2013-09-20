package test;

import static org.junit.Assert.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import oculusPrime.Settings;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import developer.FTP;
import developer.UpdateFTP;

public class FTPTest {
	
	private String host, port, user, pass;

	@Before
	public void setUp() throws Exception {
		if(UpdateFTP.configured()){
			System.out.println("ftp configured");
			configure();
		}
	}

	@After
	public void tearDown() throws Exception {
		System.out.println("..done");
	}

	public void configure() {
				
		Properties props = new Properties();
		
		try {

			FileInputStream propFile = new FileInputStream(Settings.ftpconfig);
			props.load(propFile);
			propFile.close();
			
		} catch (Exception e) {
			return;
		}	
		
		user = (String) props.getProperty("user", System.getProperty("user.name"));
		host = (String) props.getProperty("host", "localhost");
		port = (String) props.getProperty("port", "21");
		pass = props.getProperty("password");	
	}

	@Test
	public void testConnect() {
		if(UpdateFTP.configured()){
			
			FTP ftp = new FTP();
			
			try {
				ftp.connect(host, port, user, pass);
			} catch (IOException e) {
				fail("connect fail: " + e.getMessage()); 
			}
			
			System.out.println("ftp connected to: " + host);
			
			try {
				ftp.disconnect();
			} catch (IOException e) {
				fail("disconnect fail: " + e.getMessage());
			}
			
			System.out.println("ftp disconnected");
		}
	}
}
