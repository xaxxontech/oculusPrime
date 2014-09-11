package oculusPrime;

import java.io.*;
import java.net.*;
import java.util.Vector;

import oculusPrime.PlayerCommands.RequiresArguments;
import oculusPrime.State.values;

import org.jasypt.util.password.ConfigurablePasswordEncryptor;


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
	public Vector<PrintWriter> printers = new Vector<PrintWriter>();
	
	private oculusPrime.State state = oculusPrime.State.getReference();
	private oculusPrime.Settings settings = Settings.getReference();
	private BanList banlist = BanList.getRefrence();
	
	private static ServerSocket serverSocket = null;  	
	private static ServerSocket telnetSocket = null;  	
	private static Application app = null;
	
	/** Threaded client handler */
	class ConnectionHandler extends Thread {
	
		private Socket clientSocket = null;
		private BufferedReader in = null;
		private PrintWriter out = null;
		private String user, pass;
		
		public ConnectionHandler(Socket socket) {
		
			clientSocket = socket;  
			
			// check if banned
			if (banlist.isBanned(clientSocket)){ 
				try { socket.close(); } catch (Exception e) {
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
				
			// send banner to terminal
			if(settings.getBoolean(ManualSettings.diagnostic)){
				state.set(values.driver, ManualSettings.diagnostic.name());
				user = ManualSettings.diagnostic.name();
			} else {
				sendToSocket("Welcome to Oculus build " + new Updater().getCurrentVersion(), out); 
				sendToSocket("LOGIN with admin user:password OR user:encrypted_password", out);
				if( ! authenticate(socket)) return; // failure 
			}
			
			printers.add(out);	
			sendToSocket(user + " connected via socket", out);
			Util.log(user+" connected via socket", this);
			this.start();
		}
		
		private boolean authenticate(Socket socket){
			try {	
				
				final String inputstr = in.readLine();
				if(inputstr.indexOf(':')<=0) {
					banlist.failed(socket); // first thing better be user:pass
					shutDown("login failure, formatting incorrect", out, in, clientSocket);
					return false;
				}
				
				user = inputstr.substring(0, inputstr.indexOf(':')).trim();
				pass = inputstr.substring(inputstr.indexOf(':')+1, inputstr.length()).trim();
						
				if(ADMIN_ONLY) {
					if( ! user.equals(settings.readSetting("user0"))) {
						banlist.failed(socket);
						shutDown("must be ADMIN user for telnet", out, in, clientSocket);
					}
				}
				
				// try salted 
				if(app.logintest(user, pass)==null){
				
				    ConfigurablePasswordEncryptor passwordEncryptor = new ConfigurablePasswordEncryptor();
					passwordEncryptor.setAlgorithm("SHA-1");
					passwordEncryptor.setPlainDigest(true);
					String encryptedPassword = (passwordEncryptor.encryptPassword(user 
							+ settings.readSetting("salt") + pass)).trim();
					
					// try plain text 
					if(app.logintest(user, encryptedPassword)==null){
						banlist.failed(socket); 
						shutDown("this is a banned IP address: " + socket.getInetAddress().toString(), out, in, clientSocket);
						return false;
					}
				}		
			} catch (Exception ex) {
				shutDown("command server connection fail: " + ex.getMessage(), out, in, clientSocket);
				return false;
			}
	
			return true;
		}
		
		/** do the client thread */
		@Override
		public void run() {
			
			if(settings.getBoolean(ManualSettings.loginnotify)) app.saySpeech("lawg inn telnet");
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
					
					Util.debug("socket user '"+user+"' sending from "+clientSocket.getInetAddress().toString() + " : " + str, this);	
					if( ! manageCommand(str, out, in, clientSocket)) {			
						
						Util.debug("doPlayer(" + str + ")", this);	
						doPlayer(str, out);
						
					}
				}
			}
		
			// close up, must have a closed socket  
			shutDown("user disconnected", out, in, clientSocket);
		}	
	} // end inner class
	
	// close resources
	private void shutDown(final String reason, PrintWriter out, BufferedReader in, Socket clientSocket) {

		// log to console, and notify other users of leaving
		sendToSocket("shutting down "+reason, out);
		Util.debug("closing socket [" + clientSocket + "] " + reason, this);
		sendToGroup(TELNETTAG+" "+printers.size() + " tcp connections active");
		
		try {

			// close resources
			printers.remove(out);
			if(in!=null) in.close();
			if(out!=null) out.close();
			if(clientSocket!=null) clientSocket.close();
		
		} catch (Exception e) {
			Util.log("shutdown: " + e.getMessage(), this);
		}
	}
		
	
	/**
	 * @param str is a multi word string of commands to pass to Application. 
	 */
	private void doPlayer(final String str, PrintWriter out){
		
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
		
		// test if needs an argument, but is missing. 
		if(player.requiresArgument()){
			
			RequiresArguments req = PlayerCommands.RequiresArguments.valueOf(cmd[0]);
		
			if(cmd.length==1){
				sendToSocket("error: this command requires arguments " + req.getArguments(), out);
				return;
			}
		
			if(req.getValues().size() > 1){
				if( ! req.matchesArgument(cmd[1])){
					sendToSocket("error: this command requires arguments " + req.getArguments(), out);
					return;
				}
			}
				
			if(req.usesBoolean()){
				if( ! PlayerCommands.validBoolean(cmd[1])){
					sendToSocket("error: requires {BOOLEAN}", out);
					return;
				}	
			}
			
			if(req.usesInt()){
				if( ! PlayerCommands.validInt(cmd[1])){
					sendToSocket("error: requires {INT}", out);
					return;
				}
			}
			
			if(req.usesDouble()){
				if( ! PlayerCommands.validDouble(cmd[1])){
					sendToSocket("error: requires {DOUBLE}", out);
					return;
				}
			}

			if(req.requiresParse()){
				
				// do min test, check for the same number of arguments 
				String[] list = req.getArgumentList()[0].split(" ");
				if(list.length != (cmd.length-1)){
					sendToSocket("error: wrong number args, requires [" + list.length + "]", out);
					return;
				}		
			}
		}
	
		// check for null vs string("")
		args = args.trim();
		if(args.length()==0) args = "";
		
		// now send it, assign driver status 1st 
		Application.passengerOverride = true;	
		app.playerCallServer(player, args);
		Application.passengerOverride = false;		
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
		case exit:
		case quit: shutDown("user quit", out, in, clientSocket); return true;
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
			
			if (State.isNonTelnetBroadCast(key)) { return; }
			
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
				if (multiline) { pw.print("<multiline> "); }
				pw.println(str);
				if (multiline) { pw.println("</multiline>"); }
			}
		}

	}

	/** constructor */
	public TelnetServer(oculusPrime.Application a) {
		
		if(app == null) app = a;
		else return;
		
		/** register for updates, share state with all threads */  
		state.addObserver(this);
		
		/** register shutdown hook */ 
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
		}));
		
		/** command connections*/
		new Thread(new Runnable() {
			@Override
			public void run() {
				while(true) startCommandListener();
			}
		}).start();
		
		/** local connections */
		new Thread(new Runnable() {
			@Override
			public void run() {
				while(! settings.readSetting(ManualSettings.telnetport).equals(Settings.DISABLED)) 
					startLocalListener();
			}
		}).start();
	}
	
	/** do forever */ 
	private void startLocalListener(){

		/**/
		final Integer port = settings.getInteger(ManualSettings.telnetport);
		try {
			telnetSocket = new ServerSocket(port);
		} catch (Exception e) {
			Util.log("startLocalListener telnet server sock error: " + e.getMessage(), this);
			return;
		} 
		
		Util.debug("startLocalListener listening with socket: " + telnetSocket.toString(), this);
		
		while (true) {
			
			// new user has connected
			try {
				new LocalConnectionHandler(telnetSocket.accept());
			} catch (IOException e) {
				try {
					telnetSocket.close();
				} catch (IOException e1) {
					Util.log("socket error: " + e1.getMessage());
				}
				
				Util.log("socket error: " + e.getMessage());
				return;	
			}
		}
	}
	
	/** Threaded client handler {*/ 
	class LocalConnectionHandler extends Thread {
	
		private Socket clientSocket = null;
		private BufferedReader in = null;
		private PrintWriter out = null;
		
		public LocalConnectionHandler(Socket socket) {
		
			clientSocket = socket;
			final String ip = socket.getInetAddress().toString().substring(1);
			
			if( ! ip.equals("127.0.0.1")){ 
				Util.log("connection fail on attempt ip: " + ip, this);
				try {
					socket.shutdownInput();
					socket.shutdownOutput();
				} catch (IOException e) {
					Util.log("LocalConnectionHandler: " + e.getMessage(), this);
				}
				return;
			}
				
			/**/
			try {
			
				in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
				out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream())), true);
			
			} catch (IOException e) {	
			    shutDown("fail aquire tcp streams: " + e.getMessage(), out, in, clientSocket);
				banlist.failed(ip); 
				return;
			}
			
			// keep track of all other user sockets output streams			
			printers.add(out);	
			sendToSocket("local user : connected via socket", out);
			Util.log(ip + " connected via socket", this);
			this.start();
		}

		/** do the client thread */
		@Override
		public void run() {
			
			if(settings.getBoolean(ManualSettings.loginnotify)) app.saySpeech("lawg inn telnet");
			sendToGroup(TELNETTAG+" "+printers.size() + " tcp connections active");
			
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
					
					Util.debug("sending from "+clientSocket.getInetAddress().toString() + " : " + str, this);	
					
					if( ! manageCommand(str, out, in, clientSocket)) {				
						Util.debug("doPlayer(" + str + ")", this);	
						doPlayer(str, out);
					}
				}
			}
		
			// close up, must have a closed socket  
			shutDown("user disconnected", out, in, clientSocket);
		}
		

	}// end inner class
	
	/** do forever */ 
	private void startCommandListener(){
		
		try {
			serverSocket = new ServerSocket(settings.getInteger(ManualSettings.commandport));
		} catch (Exception e) {
			Util.log("server sock error: " + e.getMessage(), this);
			return;
		} 
		
		Util.debug("listening with socket: " + serverSocket.toString(), this);
		
		// serve new connections until killed
		while (true) {
			try {

				// new user has connected
				new ConnectionHandler(serverSocket.accept());

			} catch (Exception e) {
				try {				
					serverSocket.close();
				} catch (IOException e1) {
					Util.log("socket error: " + e1.getMessage());
					return;					
				}	
				
				Util.log("failed to open client socket: " + e.getMessage(), this);
			}
		}
	}
}

