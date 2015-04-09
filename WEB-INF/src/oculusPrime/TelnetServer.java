package oculusPrime;

import java.io.*;
import java.net.*;
import java.util.Vector;

/**
 * Start the terminal server. Start a new thread for a each connection. 
 */
public class TelnetServer implements Observer {
	
	public static enum Commands {chat, exit, bye, quit};
	
	public static final boolean ADMIN_ONLY = true;
	public static final String MSGPLAYERTAG = "<messageclient>";
	public static final String MSGGRABBERTAG = "<messageserverhtml>";
	public static final String TELNETTAG = "<telnet>";
	public static final String STATETAG = "<state>";		
	
	private Vector<PrintWriter> printers = new Vector<PrintWriter>();
	private Vector<Socket> socks = new Vector<Socket>();
	
	private oculusPrime.State state = oculusPrime.State.getReference();
	private oculusPrime.Settings settings = Settings.getReference();
	private BanList banlist = BanList.getRefrence();
	
	private static ServerSocket serverSocket = null;  	
	private static Application app = null;
	
	/** Threaded client handler */
	class ConnectionHandler extends Thread {
	
		private Socket clientSocket = null;
		private BufferedReader in = null;
		private PrintWriter out = null;
		
		public ConnectionHandler(Socket socket) {
		
			clientSocket = socket;  
			
			// check if banned
			if (banlist.isBanned(clientSocket)){ 
				try { 
					socket.close(); 
				} catch (Exception e) {
					Util.log("ConnectionHandler(), banned IP error", e, this);
				}		
				return;
			}
		
			// connect 
			try {
			
				in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
				out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream())), true);
			
			} catch (IOException e) {	
				shutDown("fail aquire tcp streams: " + e.getMessage(), out, in, clientSocket);
				return;
			}
				
			printers.add(out);
			socks.add(clientSocket);
			
			// send banner to terminal
			sendToSocket("Welcome to Oculus Prime v" + new Updater().getCurrentVersion(), out); 
			String ip_address = clientSocket.getInetAddress().toString().substring(1);
			sendToSocket(ip_address + " connected via socket", out);
			Util.log(ip_address+" connected via socket", this);
			this.start();
		}
		
		/** do the client thread */
		@Override
		public void run() {
			
			if(settings.getBoolean(GUISettings.loginnotify)) app.saySpeech("lawg inn telnet");
			sendToGroup(TELNETTAG+" "+printers.size() + " tcp connections active");
			state.set(oculusPrime.State.values.telnetusers, printers.size());
			
			// loop on input from the client
			String str = null;
			while (true) {
				try {
					str = in.readLine();
				} catch (Exception e) {
					Util.debug("readLine(): " + e.getMessage(), this);
					break;
				}

				// client is terminating?
				if (str == null) {
					Util.debug("read thread, closing.", this);
					break;
				}
						
				// parse and run it 
				str = str.trim();
				if(str.length()>=1){
					
					if( ! manageCommand(str, out, in, clientSocket)) {
						Util.debug("doPlayer(" + str + ")", this);	
						doPlayer(str, out);
					}
				}
			}
		
			// close up, must have a closed socket  
			if (printers.contains(out)) shutDown("user disconnected", out, in, clientSocket);
		}	
	} // end inner class
	
	// close resources
	private void shutDown(final String reason, PrintWriter out, BufferedReader in, Socket clientSocket) {

		// log to console, and notify other users of leaving
		sendToSocket("shutting down, "+reason, out);
		Util.debug("closing socket [" + clientSocket + "] " + reason, this);
		
		try {

			// close resources
			printers.remove(out);
			if(in!=null) in.close();
			if(out!=null) out.close();
			if(clientSocket!=null) clientSocket.close();
		
		} catch (Exception e) {
			Util.log("shutdown: " + e.getMessage(), this);
		}
		
		sendToGroup(TELNETTAG+" "+printers.size() + " tcp connections active");
		state.set(oculusPrime.State.values.telnetusers, printers.size());
	}
		
	private void doPlayer(final String str, PrintWriter out){
		
		if(str == null || out == null) return;
		
		final String[] cmd = str.split(" ");
		String args = new String(); 			
		for(int i = 1 ; i < cmd.length ; i++) args += " " + cmd[i].trim();
		
		PlayerCommands player = null; 
		try { // create command from input 
			player = PlayerCommands.valueOf(cmd[0]);
		} catch (Exception e) {
			sendToSocket("error: unknown command, " + cmd[0], out);
			return;
		}
		
		// check for null vs string("")
		args = args.trim();
		if(args.length()==0) args = "";
		app.driverCallServer(player, args);
	}
	
	
	/** add extra commands, macros here. Return true if the command was found */ 
	private boolean manageCommand(final String str, PrintWriter out, BufferedReader in, Socket clientSocket){
		
		final String[] cmd = str.split(" ");
		Commands telnet = null;
		try {
			telnet = Commands.valueOf(cmd[0]);
		} catch (Exception e) {
			return false;
		}
		
		switch (telnet) {
		
		case chat: // overrides playercommands chat
			String args = new String(); 		
			for(int i = 1 ; i < cmd.length ; i++) args += " " + cmd[i].trim();
			if(args.length()>1)
				app.playerCallServer(PlayerCommands.chat, args);
			return true;
		
		case bye:
		case quit:
		case exit: shutDown("user left", out, in, clientSocket); return true;
		}
		
		// command was not managed 
		return false;	
	}
	
	private void sendToSocket(String str, PrintWriter out) {
		Boolean multiline = false;
		if (str.matches(".*<br>.*")) { 
			multiline = true;
			str = (str.replaceAll("<br>", "\r\n")).trim();
		}
		if (multiline) { out.print("<multiline> "); }
		out.println("<telnet> " + str+"\r");
		if (multiline) { out.println("</multiline>"); }
	}
	
	@Override
	/** send to socket on state change */ 
	public void updated(String key) {
		String value = state.get(key);
		if(value==null)	sendToGroup(STATETAG + " deleted: " + key); 
		else {
			
			if (State.isNonTelnetBroadCast(key)) return; 
			
			sendToGroup(STATETAG + " " + key + " " + value); 
		}
	}
	
	/** send input back to all the clients currently connected */
	public void sendToGroup(String str) {
		Boolean multiline = false;
		if (str.contains("<br>")) {
			multiline = true;
			str = (str.replaceAll("<br>", "\r\n")).trim();
		}
		PrintWriter pw = null;
		for (int c = 0; c < printers.size(); c++) {
			pw = printers.get(c);
			if (pw.checkError()) {	
				printers.remove(pw);
				pw.close();
			} else {
				if (multiline) pw.print("<multiline> "); 
				pw.println(str);
				if (multiline) pw.println("</multiline>"); 
			}
		}
	}

	/** constructor */
	public TelnetServer(oculusPrime.Application a) {
		
		if(app == null) app = a;
		else return;
		
		/** register for updates, share state with all threads */  
		state.addObserver(this);
		
		//TODO: doesn't seem to work???
		/** register shutdown hook -------------------- 
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					
					if(serverSocket!=null) serverSocket.close();
					
					if(printers!=null)
						for(int i = 0 ; i < printers.size() ; i++)
							printers.get(i).close();
					
				} catch (IOException e) {
					Util.debug(e.getMessage(), this);
				}
			}
		}));*/ 
		
		/** command connections*/
		new Thread(new Runnable() {
			@Override
			public void run() {
				while(true) startCommandListener();
			}
		}).start();
	}
	
	/** do forever */ 
	private void startCommandListener(){
		
		try {
			serverSocket = new ServerSocket(settings.getInteger(ManualSettings.telnetport));
		} catch (Exception e) {
			Util.log("server sock error: " + e.getMessage(), this);
			return;
		} 
		
		Util.debug("listening with socket: " + serverSocket.toString());
		
		// serve new connections until killed
		while (true) {
			try {

				// new user has connected
				new ConnectionHandler(serverSocket.accept());

			} catch (Exception e) {
				
				try {				
					serverSocket.close();
				} catch (IOException e1) {
					Util.log("socket error: " + e1.getMessage(),this);
					return;					
				}	
				
				Util.debug("failed to open client socket: " + e.getMessage());
			}
		}
	}

	public void close() {
		
		Util.log("closing resources... ", this);
		
		for (int c = 0; c < printers.size(); c++) printers.get(c).close();
		
		for (int c = 0; c < socks.size(); c++){
			try {
				if(socks.get(c).isConnected()) 
					socks.get(c).close();
			} catch (IOException e) {
				Util.log("failed to close client socket: " + e.getMessage(), this);
			}
		}
		
		try {
			serverSocket.close();
		} catch (IOException e) {
			Util.log("failed to close server socket: " + e.getMessage(), this);
		}
	
	}
}

