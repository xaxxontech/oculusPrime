package developer.swingtool;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Vector;

public class DummyServer {
	
	static final int PORT = 4444;
	static ServerSocket serverSocket = null;  	
	private Vector<PrintWriter> printers = new Vector<PrintWriter>();

	/** threaded client handler */
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
			
			System.out.println("ConnectionHandler(): " + socket.toString() + " connected");
			out.println("\n..welcome.. active connections: " + printers.size());		
			printers.add(out);
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
					sendGroup(clientSocket.toString() + " exited");	
					break;
				}

				if(str.trim().length() > 1){
					System.out.println("INPUT: " + clientSocket.toString() + " " + str);
					sendGroup(clientSocket.toString() + " " + str);	
				}
			}
		}	
	} 

	void sendGroup(String str){
		PrintWriter pw = null;
		for (int c = 0; c < printers.size(); c++) {
			pw = printers.get(c);
			if(pw.checkError()) {	
				printers.remove(pw);
				pw.close();
			} else {
				pw.println(str);
			}
		}			
	}
	
	public DummyServer() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				startCommandListener();
			}
		}).start();
	}
	
	private void startCommandListener(){
		try {
			serverSocket = new ServerSocket(PORT);
		} catch (Exception e) {
			System.out.println("server sock error: " + e.getMessage());
			return;
		} 
		
		System.out.println("listening with socket: " + serverSocket.toString());
		
		while(true) {
			try {
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
