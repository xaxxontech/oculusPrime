package developer.terminal;

import java.io.*;
import java.net.*;

import oculus.State;

public abstract class AbstractTerminal  {
	
	State state = State.getReference();

	/** share on read, write threads */
	Socket socket;
	BufferedReader in;
	PrintWriter out;
	
	/** telnet connect */ 
	public AbstractTerminal(String ip, String port, final String user, final String pass) 
		throws NumberFormatException, UnknownHostException, IOException {
	
		socket = new Socket(ip, Integer.parseInt(port));
		if(socket != null){
			
			out = new PrintWriter(new BufferedWriter(
				new OutputStreamWriter(socket.getOutputStream())), true);
			
			// login on connect 
			out.println(user + ":" + pass);
			startReader(socket);
		}
	}
	

	/** each sub class will get notified when data comes in */
	public abstract void parseInput(final String str);
	
	
	/** */
	public void shutdown(){

		try {
			if(in!=null) in.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		if(out!=null) out.close();
		
		try {
			if(socket!=null) socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	
		// System.out.println("-- forced exit --");
		System.exit(0);
	}
	
	public void startReader(final Socket socket) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				} catch (Exception e) {
					shutdown();
				}
			
				String input = null;
				while(true){
					try {
											
						input = in.readLine();
						if(input==null) break;
						else parseInput(input);

						if(out.checkError()){
							System.out.println(".. abstract terminal ..write end closed.");
							shutdown();
						}
					} catch (IOException e) {
						System.out.println(e.getMessage());
						break;
					}
				}				
				
				//try {
					
				/*	in.close();
					in = null;
					out.close();
					out.close();
					socket.close();*/
					System.out.println(".. abstract terminal clean exit...");
					
					
					shutdown();
					
				//} catch (IOException e) {
				//	e.printStackTrace();
				//}
			}
		}).start();
	}
}
