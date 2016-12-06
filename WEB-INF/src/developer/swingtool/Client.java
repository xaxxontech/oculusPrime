package developer.swingtool;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

import javax.swing.JTextArea;

public class Client {

	int inCount, outCount = 0;
	JTextArea messages = new JTextArea();
	PrintWriter out = null;
	BufferedReader in = null;


	public Client(String host, int port) throws IOException {

		Socket socket = null;

		try {

			// construct the client socket
			socket = new Socket(host, port);
			out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
	
			// create a useful title
			String title = socket.getInetAddress().toString();

			// pass socket on to read and write swing components
			Frame frame = new Frame(new Input(this), messages, title + " in: " + inCount + " out: " + outCount);

			// create and show this application's GUI.
			javax.swing.SwingUtilities.invokeLater(frame);

			// loop on input from socket
			String input = null;
			while (true) {
				try {

					// block on input and then update text area
					input = in.readLine();

					if (input == null) break;

					input = input.trim();

					if (input.length() > 0) {

						// System.out.println(""+input);

						inCount++;
						
						frame.setTitle("in: " + inCount + " out: " + outCount);

						messages.append(input + "\n");

						// move focus to it new line we just added
						messages.setCaretPosition(messages.getDocument().getLength());

					}
				} catch (Exception e) {
					socket.close();
					title = "dead";
//					System.exit(0);
				}
			}

		} catch (Exception e) {
//			socket.close();
//			System.out.println(e.getMessage());
//			System.exit(-1);
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