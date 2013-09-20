package developer.swingtool;

import java.io.*;
import java.net.*;

import javax.swing.*;

import oculusPrime.PlayerCommands;
import oculusPrime.State;
import oculusPrime.Util;

import java.awt.event.*;

public class Input extends JTextField implements KeyListener {

	private static final long serialVersionUID = 1L;
	private Socket socket = null;
	private PrintWriter out = null;
	private String userInput = null;
	private int ptr, stateptr = 0;

	public Input(Socket s, final String usr, final String pass) {
		super();
		socket = s;

		try {
			out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
		} catch (Exception e) {
			Util.log("can not connect", this);
			System.exit(-1);
		}

		// if connected, login now
		out.println(usr + ":" + pass);

		// listen for key input
		addKeyListener(this);

		// send dummy messages
		new WatchDog().start();
	}

	/** inner class to check if getting responses in timely manor */
	public class WatchDog extends Thread {
		public WatchDog() {
			this.setDaemon(true);
		}

		public void run() {
			Util.delay(2000);
			while (true) {
				Util.delay(2000);
				if (out.checkError()) {
					Util.log("watchdog closing", this);
					System.exit(-1);
				}

				// send dummy
				out.println("\t\t\n");
			}
		}
	}

	// Manager user input
	public void send() {
		try {

			// get keyboard input
			userInput = getText().trim();

			// send the user input to the server if is valid
			if (userInput.length() > 0) out.println(userInput);

			if (out.checkError()) System.exit(-1);

			if (userInput.equalsIgnoreCase("quit")) System.exit(-1);

			if (userInput.equalsIgnoreCase("bye")) System.exit(-1);

		} catch (Exception e) {
			System.exit(-1);
		}
	}

	@Override
	public void keyTyped(KeyEvent e) {
		final char c = e.getKeyChar();
		if (c == '\n' || c == '\r') {
			final String input = getText().trim();
			if (input.length() > 2) {
				send();
				setText("");
			}
		}
	}

	@Override
	public void keyPressed(KeyEvent e) {

		if (out == null) return;
		
		PlayerCommands[] cmds = PlayerCommands.values();
        State.values[] state = State.values.values();
		
        if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
			
			if (stateptr++ >= (state.length-1)) stateptr = 0;

			setText("state " + state[stateptr].name() + " ");

			setCaretPosition(getText().length());
		
		} else if (e.getKeyCode() == KeyEvent.VK_LEFT) {
			
			if (stateptr-- <= 0) stateptr = (state.length-1);
			
			setText("state " + state[stateptr].name() + " ");

			setCaretPosition(getText().length());
			
		} else if (e.getKeyCode() == KeyEvent.VK_UP) {

			if (ptr++ >= (cmds.length-1)) ptr = 0;

			setText(cmds[ptr].toString() + " ");

			setCaretPosition(getText().length());

		} else if (e.getKeyCode() == KeyEvent.VK_DOWN) {

			if (ptr-- <= 0) ptr = (cmds.length-1);

			setText(cmds[ptr].toString() + " ");
			
			setCaretPosition(getText().length());
			
		} 
        
        /* else if (e.getKeyChar() == '*') {
			
			
			setText("");
			new Thread(new Runnable() {
				@Override
				public void run() {
			
					for (PlayerCommands factory : PlayerCommands.values()) {
						if (!factory.equals(PlayerCommands.restart)) {
							out.println(factory.toString());
							Util.log("sending: " + factory.toString());
							Util.delay(500);
						}
					}
				}}).start();
			
		} */
        
	}

	
	@Override
	public void keyReleased(KeyEvent e) {}
}
