package developer.swingtool;

import java.io.*;
import java.net.*;

import oculusPrime.ManualSettings;

public class Client {
	
	
	public Client(String host, int port, final String usr, final String pass) throws IOException {
		try {

			// construct the client socket
			Socket s = new Socket(host, port);

			// create a useful title
			String title = usr + s.getInetAddress().toString();

			// pass socket on to read and write swing components
			Frame frame = new Frame(new Input(s, usr, pass), new Output(s), title);

			// create and show this application's GUI.
			javax.swing.SwingUtilities.invokeLater(frame);

		} catch (Exception e) {
			System.out.println(e.getMessage());
			System.exit(-1);
		}
	}

	// driver
	public static void main(String args[]) throws Exception {
		
		System.out.println("argv: " + args.length);
		
		String user = null;
		String pass = null;
		String ip = null;
		int port = 4444;
		
		if(args.length==0) {	
			
			// force red5 path 
			oculusPrime.Settings settings = new oculusPrime.Settings("../../");
			
			// login info from settings
			ip = "127.0.0.1";
			user = settings.readSetting("user0");
			pass = settings.readSetting("pass0");
			port = settings.getInteger(ManualSettings.telnetport); 
		
		}
		
		// use params off command line 
		if(args.length==4){
			ip = args[0];
			port = Integer.parseInt(args[1]);
			user = args[2];
			pass = args[3];
		} 
		
		new Client(ip, port, user, pass);
	}
}