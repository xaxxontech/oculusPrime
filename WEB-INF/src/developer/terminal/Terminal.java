package developer.terminal;

import java.io.*;
import java.net.*;

/**
 * Bare bones telnet terminal client for Oculus. Stdin and Stdout get mapped to socket of given parameters.
 */
public class Terminal {

	/** share on read, write threads */
	Socket socket = null;
	
	/** */ 
	public Terminal(String ip, String port, final String user, final String pass, final String[] commands) 
		throws NumberFormatException, UnknownHostException, IOException {
	
		socket = new Socket(ip, Integer.parseInt(port));
		if(socket != null){
			
			String input = null;
			BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
			PrintWriter out = new PrintWriter(new BufferedWriter(
					new OutputStreamWriter(socket.getOutputStream())), true);
			
			// login on connect 
			out.println(user + ":" + pass);
				
			// send commands if are commands buffered 
			for(int i = 0 ; i < commands.length ; i++)
				out.println(commands[i]);
			
			startReader(socket);
			
			while (!socket.isClosed()){ 	
				try {
					
					// don't send nulls
					input = stdin.readLine().trim();
					if(input.length()>1) out.println(input);
					
				} catch (Exception e) {
					stdin.close();
					socket.close();
				}
			}
		}
	}

	public void startReader(final Socket socket) {
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
						else System.out.println(input);
					
					} catch (IOException e) {
						System.out.println(e.getMessage());
						break;
					}
				}
				
				// System.out.println(".. server closed socket, logged out.");
				
				try {
					in.close();
					socket.close();
					System.exit(-1);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}).start();
	}

	/** parameters: ip, port, user name, password [commands] */ 
	public static void main(String args[]) throws IOException {
		String[] cmd = new String[args.length-4];
		for(int i = 0 ; i < (args.length-4); i++)
			cmd[i] = args[i+4];
		
		new Terminal(args[0], args[1], args[2], args[3], cmd);
	}
}