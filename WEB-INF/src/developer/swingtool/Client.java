package developer.swingtool;

import java.io.*;
import java.net.*;

import oculusPrime.ManualSettings;

public class Client {
	
	
	public Client(String host, int port) throws IOException {
		try {

			// construct the client socket
			Socket s = new Socket(host, port);

			// create a useful title
			String title = s.getInetAddress().toString();

			// pass socket on to read and write swing components
			Frame frame = new Frame(new Input(s), new Output(s), title);

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
		
		String ip = args[0];
		int port = Integer.parseInt(args[1]);
		
<<<<<<< HEAD
		new Client(ip, port);
=======
		if(args.length==0) {	
			
			// force red5 path 
			oculusPrime.Settings settings = oculusPrime.Settings.getReference();
			
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
>>>>>>> b5da28e6782185644ce22f2ad9c94a4e4506ce95
	}
}