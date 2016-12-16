package developer.swingtool;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import oculusPrime.PlayerCommands;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

public class SwingTerminal extends JFrame {

	private static final long serialVersionUID = 1L;
	DefaultListModel<PlayerCommands> listModel = new DefaultListModel<PlayerCommands>();
	JList<PlayerCommands>  list = new JList<PlayerCommands>(listModel) ;
	JTextField in = new JTextField();
	JTextArea messages = new JTextArea();
	BufferedReader reader = null;
	PrintWriter printer = null;
	Socket socket = null;
	String ip;
	int port;
	int rx, tx = 0;
	
	public SwingTerminal(String ip, int port){ 
		this.ip = ip;
		this.port = port;
		setDefaultLookAndFeelDecorated(true);
		setLayout(new BorderLayout());
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.addListSelectionListener(new ListSelectionListener() {	
			@Override
			public void valueChanged(ListSelectionEvent e) {
				if(e.getValueIsAdjusting() == false) {
					if(list.getSelectedIndex() != -1){
						in.setText(list.getSelectedValue().toString() + " ");
						in.setCaretPosition(in.getText().length());
					}
			    }
			}
		});
		
		final PlayerCommands[] cmds = PlayerCommands.values();
		for(int i = 0; i < cmds.length; i++) listModel.addElement(cmds[i]);
		
		JScrollPane listScrollPane = new JScrollPane(list);
		JScrollPane chatScroller = new JScrollPane(messages);
		JScrollPane cmdsScroller = new JScrollPane(listScrollPane);

		chatScroller.setPreferredSize(new Dimension(600, 600));
		cmdsScroller.setPreferredSize(new Dimension(200, 400));
		getContentPane().add(chatScroller, BorderLayout.LINE_END);
		getContentPane().add(cmdsScroller, BorderLayout.LINE_START);
		getContentPane().add(in, BorderLayout.PAGE_END);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		chatScroller.setFocusable(false);
		
		in.addKeyListener(new keyListener());
		in.setFocusable(true);	
		in.requestFocus();

		pack();
		setVisible(true);
		openSocket();
	}
	
	void openSocket(){
		try {
			socket = new Socket(ip, port);
			printer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
			reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			messages.append("connected to: " + socket.getInetAddress().toString());
			setTitle(socket.getInetAddress().toString());
			readSocket();
		}catch (Exception e) {
			messages.append(e.getMessage());
			try {
				socket.close();
				setTitle("dead");
			} catch (IOException e1) {
				messages.append(e.getMessage());
			}
		}
	}
	
	/** block on input and then update text area */
	void readSocket(){	
		new Thread(new Runnable() { public void run() {
			String input = null;
			while (true) {
				try {
					input = reader.readLine();
					if(input == null) break;
					input = input.trim();
					if(input.length() > 0) {
						rx++;
						setTitle(socket.getInetAddress().toString() + " rx: " + rx + " tx: " + tx);
						messages.append(input + "\n");
						messages.setCaretPosition(messages.getDocument().getLength());
					}
				} catch (Exception e) {
					try {
						socket.close();
					} catch (IOException e1) {
						messages.append(e.getMessage());
					}
				}
			}
		}}).start();
	}
	
	public class keyListener implements KeyListener {
		@Override public void keyPressed(KeyEvent arg0) {}
		@Override public void keyReleased(KeyEvent arg0) {}
		@Override
		public void keyTyped(KeyEvent e) {
			final char c = e.getKeyChar();
			if (c == '\n' || c == '\r') {
				final String input = in.getText().trim();
				tx++;
				try {
					printer.println(input);
				} catch (Exception e1) {
					try {
						socket.close();
					} catch (IOException e2) {
						messages.append(e1.getMessage());
					}
				}
			}
		}
	}

	public static void main(String[] args) {
		String ip = args[0];
		int port = Integer.parseInt(args[1]);
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				new SwingTerminal(ip, port);
			}
		});
	}
}
