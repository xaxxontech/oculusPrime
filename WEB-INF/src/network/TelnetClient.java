package network;

import java.io.*;
import java.net.*;

public class TelnetClient {

	private PrintWriter out = null;

	public TelnetClient() {
		super();
		
		Socket socket = null;
		try {
			socket = new Socket("127.0.0.1", 4444);
		} catch (Exception e) {
			System.out.println("TelnetClient(): can not connect");
			return;
		}

		try {
			out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
		} catch (Exception e) {
			System.out.println("TelnetCient(): can not connect");
			return;
		}

		// send dummy messages
		new WatchDog().start();
	}

	/** inner class to check if getting responses in timely manor */
	public class WatchDog extends Thread {
		public void run() {
			while (true) {
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				if (out.checkError()) {
		    		System.out.println("send(): error, watchdog error.."); 
		    		close();
					return;
				}
			}
		}
	}
	
	public boolean isOpen(){
		if(out==null) return false;
		return true;
	}
	
	public void close(){
		out.close();
		out = null;
	}

	public void send(final String userInput) {
		if(out == null) return;
		if(userInput == null) return;
		if(userInput.length() <= 0) return;
		
		new Thread(){
		    public void run() {
		    	try{
		    		System.out.println("TelnetClient.send(): " + userInput);
		    		out.println(userInput);
		    	} catch (Exception e) {
		    		System.out.println("send(): exception: " + e.getMessage()); 
		    	}
		    }
		}.start();
	}
}
