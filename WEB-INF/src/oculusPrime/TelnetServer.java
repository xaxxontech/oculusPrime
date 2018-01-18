package oculusPrime;

import java.io.*;
import java.net.*;
import java.util.Vector;

/**
 * Start the terminal server. Start a new thread for a each connection. 
 */
public class TelnetServer implements Observer {
	
	public static enum Commands {chat, exit, bye, quit, state};
	
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
			
			if (banlist.isBanned(clientSocket)){ 
				try { 
					Util.debug("banned ip: " + clientSocket.toString(), this);
					socket.close();
					return;
				} catch (Exception e) {
					Util.log("ConnectionHandler(), banned IP error", e, this);
				}		
			}

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
					break;
				}
					
				try {
	
					// parse and run it 
					str = str.trim();
					if(str.length()>=1){
						
						if( ! manageCommand(str, out, in, clientSocket)) {
							doPlayer(str, out);
						}
					}	
							
				} catch (Exception e) {
					Util.log("parse error: " + e.getLocalizedMessage(), this);
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
		// Util.debug("closing socket [" + clientSocket + "] " + reason, this);
		
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
//		Util.debug(str, this);
		
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

// TODO: maybe in developer mode? 
//		if (player.equals(PlayerCommands.systemcall)) {
//			sendToSocket("forbidden command, " + cmd[0], out);
//			return;
//		}

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
				if(args.length()>1) app.driverCallServer(PlayerCommands.chat, args);
				return true;

			case bye:
			case quit:
			case exit: shutDown("user left", out, in, clientSocket);
				return true;

			case state:
				if (cmd.length == 3) { // two args
					if (cmd[1].equals("delete")) state.delete(cmd[2]);
					else state.set(cmd[1], cmd[2]);
				}
				else if (cmd.length > 3) { // 2nd arg has spaces
					String stateval = "";
					for (int i=2; i<cmd.length; i++) stateval += cmd[i]+" ";
					state.set(cmd[1], stateval.trim());
				}
				else if (cmd.length == 2) {
					sendToSocket("<state> " + cmd[1] + " " + state.get(cmd[1]), out);
				}
				else {  // no args
					sendToSocket("<state> " + state.toString(), out);
				}

				return true;

		}
		
		// command was not managed 
		return false;	
	}
	
	private void sendToSocket(String str, PrintWriter out) {
		Boolean multiline = false;
//		if (str.matches(".*<br>.*")) {
//			multiline = true;
//			str = (str.replaceAll("<br>", "\r\n")).trim();
//		}
		if (str.contains("<br>")) {
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
		String value = state.get(key); // State.values.valueOf(key));
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

	/**  register for updates, share state with all threads  */
	public TelnetServer(oculusPrime.Application a) {
		app = a;
		state.addObserver(this);
		Util.debug("telnet server started", this);
		
		/** command connections*/
		new Thread(new Runnable() {
			@Override
			public void run() {
				while(!settings.readSetting(GUISettings.telnetport).equals(Settings.DISABLED.toString()))
					startCommandListener();
			}
		}).start();
	}
	
	/** do forever */ 
	private void startCommandListener(){
		
		try {
			serverSocket = new ServerSocket(settings.getInteger(GUISettings.telnetport));
		} catch (Exception e) {
			Util.log("server sock error: " + e.getMessage(), this);
			return;
		} 
		

		// serve new connections until killed
		while (true) {
			try {

				// new user has connected
				new ConnectionHandler(serverSocket.accept());

			} catch (Exception e) {
				try {				
					if(serverSocket.isBound())
						serverSocket.close();
				} catch (IOException ee) {
					Util.log("socket error: ", ee, this);
					return;					
				}	
				
				return;
			}
		}
	}

	public void close() {
		
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
			if(serverSocket.isBound()) 
				serverSocket.close();
		} catch (IOException e) {
			Util.log("failed to close server socket: " + e.getMessage(), this);
		}
	}
}

