package developer.swingtool;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import oculusPrime.PlayerCommands;
import oculusPrime.Util;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
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
	JList<PlayerCommands> list = new JList<PlayerCommands>(listModel) ;
	JTextArea messages = new JTextArea();	
	JTextField in = new JTextField();
	BufferedReader reader = null;
	PrintWriter printer = null;
	Socket socket = null;
	int rx, tx = 0;
	String ip;
	int port;
	
	public SwingTerminal(String ip, int port){ 
		this.ip = ip;
		this.port = port;
		setDefaultLookAndFeelDecorated(true);
		
//		messages.setFont(new Font("serif", Font.PLAIN, 25));
//		list.setFont(new Font("serif", Font.PLAIN, 25));
//		in.setFont(new Font("serif", Font.PLAIN, 25));
//		setFont(new Font("serif", Font.PLAIN, 25));
		
		setLayout(new BorderLayout());
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.addListSelectionListener(new ListSelectionListener() {	
			@Override // update text in input window 
			public void valueChanged(ListSelectionEvent e) {
				if(e.getValueIsAdjusting() == false) {
					if(list.getSelectedIndex() != -1){
						in.setText(list.getSelectedValue().toString() + " ");
						in.setCaretPosition(in.getText().length());
					}
			    }
			}
		});
		
		list.addKeyListener(new KeyListener() {
			@Override public void keyTyped(KeyEvent arg) {}
			@Override public void keyReleased(KeyEvent arg) {}
			@Override // enter key 
			public void keyPressed(KeyEvent arg) {
				if(arg.getKeyCode() == 10){				
					if(list.getSelectedIndex() != -1){
						sendCommand(in.getText().trim());
					}
				}
			}
		});
		
		list.addMouseListener( new MouseListener() {
			@Override public void mouseReleased(MouseEvent arg) {}
			@Override public void mousePressed(MouseEvent arg) {}
			@Override public void mouseExited(MouseEvent arg) {}
			@Override public void mouseEntered(MouseEvent arg) {}
			@Override // double clicked 
			public void mouseClicked(MouseEvent arg) {
				if(arg.getClickCount() == 2){			
					if(list.getSelectedIndex() != -1){
						sendCommand(in.getText().trim());
					}
				}
			}
		});
		
		final PlayerCommands[] cmds = PlayerCommands.values();
		for(int i = 0; i < cmds.length; i++) listModel.addElement(cmds[i]);
		
		JScrollPane listScrollPane = new JScrollPane(list);
		JScrollPane chatScroller = new JScrollPane(messages);
		JScrollPane cmdsScroller = new JScrollPane(listScrollPane);

		chatScroller.setPreferredSize(new Dimension(500, 700));
		cmdsScroller.setPreferredSize(new Dimension(200, 700));
		getContentPane().add(chatScroller, BorderLayout.LINE_END);
		getContentPane().add(cmdsScroller, BorderLayout.LINE_START);
		getContentPane().add(in, BorderLayout.PAGE_END);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		chatScroller.setFocusable(false);
		
		in.addKeyListener(new KeyListener() {
			@Override public void keyPressed(KeyEvent arg) {}
			@Override public void keyReleased(KeyEvent arg) {}
			@Override // input with parameter 
			public void keyTyped(KeyEvent e) {
				final char c = e.getKeyChar();
				if(c == '\n' || c == '\r') 
					sendCommand(in.getText().trim());
			}
		});
		
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
						messages.append(Util.getDateStampShort() + " " + input + "\n");
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
	
	void sendCommand(final String input){
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
