package developer.swingtool;

import javax.swing.*;

import oculusPrime.PlayerCommands;
import oculusPrime.State;
import java.awt.event.*;

public class Input extends JTextField implements KeyListener {

	private static final long serialVersionUID = 1L;
	private String userInput = null;
	private int ptr, stateptr = 0;
	Client client = null;

	public Input(Client client) {
		super();
		this.client = client;

		// listen for key input
		addKeyListener(this);
	}

	// Manager user input
	public void send() {
		try {

			// get keyboard input
			userInput = getText().trim();

			// send the user input to the server if is valid
			if (userInput.length() > 0) client.out.println(userInput);

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

	//	if (out == null) return;
		
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
	}

	
	@Override
	public void keyReleased(KeyEvent e) {}
}
