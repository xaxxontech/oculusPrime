package developer.swingtool;

import java.io.IOException;
import java.net.Socket;

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
		
		new Client(ip, port);
	}
}