package developer.swingtool;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class DummyServer {
	
	private static final int PORT = 4444;
	private static ServerSocket serverSocket = null;  	

	/** Threaded client handler */
	class ConnectionHandler extends Thread {
	
		private Socket clientSocket = null;
		private BufferedReader in = null;
		private PrintWriter out = null;
		
		public ConnectionHandler(Socket socket) {
		
			clientSocket = socket;  

			try {
			
				in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
				out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream())), true);
			
			} catch (IOException e) {	
				System.out.println("ConnectionHandler(): " + e.getMessage());
				return;
			}
		
			out.println("welcome");
			
			this.start();
		}
		
		@Override
		public void run() {
			String str = null;
			while (true) {
				
				try {
					str = in.readLine();
				} catch (Exception e) {
					System.out.println("readLine(): " + clientSocket.toString() + " " + e.getMessage());
					break;
				}

				// client is terminating?
				// if(str == null) {
				//	System.out.println("read thread, closing");
				//	break;
				//}
		
				System.out.println("IMPUT: " + clientSocket.toString() + " " + str);
						
			}
		}	
	} 

	/** register for updates, share state with all threads  */
	public DummyServer() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				startCommandListener();
			}
		}).start();
	}
	
	/** do forever */ 
	private void startCommandListener(){
		
		try {
			serverSocket = new ServerSocket(PORT);
		} catch (Exception e) {
			System.out.println("server sock error: " + e.getMessage());
			return;
		} 
		
		System.out.println("listening with socket: " + serverSocket.toString());
		
		// serve new connections until killed
		while (true) {
			try {

				// new user has connected
				new ConnectionHandler(serverSocket.accept());

			} catch (Exception e) {
				try {				
					if(serverSocket.isBound()) serverSocket.close();
				} catch (IOException ee) {
					System.out.println("socket error: " + ee.getMessage());
					return;					
				}	
				
				return;
			}
		}
	}

	public static void main(String[] args) {
		new DummyServer();
	}
}
