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
import java.util.Timer;
import java.util.TimerTask;

public class SwingTerminal extends JFrame {

	private static final long serialVersionUID = 1L;
	final long DELAY = 5000; 

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
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setLayout(new BorderLayout());

//		messages.setFont(new Font("serif", Font.PLAIN, 25));
//		list.setFont(new Font("serif", Font.PLAIN, 25));
//		in.setFont(new Font("serif", Font.PLAIN, 25));
//		setFont(new Font("serif", Font.PLAIN, 25));
		
		
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

		chatScroller.setPreferredSize(new Dimension(500, 800));
		cmdsScroller.setPreferredSize(new Dimension(250, 800));
		getContentPane().add(chatScroller, BorderLayout.LINE_END);
		getContentPane().add(cmdsScroller, BorderLayout.LINE_START);
		getContentPane().add(in, BorderLayout.PAGE_END);
		// chatScroller.setFocusable(false);
		
		in.addKeyListener(new KeyListener() {
			@Override public void keyPressed(KeyEvent arg) {}
			@Override public void keyReleased(KeyEvent arg) {}
			@Override // input with parameter 
			public void keyTyped(KeyEvent e) {
				if(e.getKeyChar() == '\n' || e.getKeyChar() == '\r') 
					sendCommand(in.getText().trim());
			}
		});
		
		// show the Swing gui 
		pack();
		setVisible(true);
	//	setResizable(false);
		
		// start timer watch dog 
		new Timer().scheduleAtFixedRate(new Task(), 0, DELAY);
	}
	
	private class Task extends TimerTask {
		public void run(){
			if(printer == null || socket.isClosed()){
				
				openSocket();
				try { Thread.sleep(5000); } catch (InterruptedException e) {}
				if(socket != null) if(socket.isConnected()) readSocket();
					
			} else {
				try {
					printer.checkError();
					printer.flush();
					// printer.println("\n\n\n"); 
					// send dummy message to test the connection
				} catch (Exception e) {
					appendMessages("TimerTask(): "+e.getMessage());
					closeSocket();
				}
			}
		}
	}
	
	void openSocket(){	
		try {	
			setTitle("trying to connect");
			socket = new Socket(ip, port);
			printer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
			reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			appendMessages("openSocket(): connected to: " + socket.getInetAddress().toString());
			setTitle(socket.getInetAddress().toString());
		} catch (Exception e) {
			setTitle("disconnected");
			appendMessages("openSocket(): " + e.getMessage());
			closeSocket();
		}
	}
	
	void closeSocket(){
		if(printer != null){
			printer.close();
			printer = null;
		}
		if(reader != null){
			try {
				reader.close();
				reader = null;
			} catch (IOException e) {
				appendMessages("closeSocket(): " + e.getLocalizedMessage());
			}
		}
		try { 
			if(socket != null) socket.close(); 
		} catch (IOException ex) {
			appendMessages("closeSocket(): " + ex.getLocalizedMessage());
		}
	}

	void readSocket(){	
		new Thread(new Runnable() { public void run() {
			String input = null;
			while(printer != null) {
				try {
					input = reader.readLine();
					if(input == null) {
						appendMessages("readSocket(): closing..");
						try { Thread.sleep(5000); } catch (InterruptedException e) {}
						closeSocket();
						break;
					}
					
					// ignore dummy messages 
					input = input.trim();
					if(input.length() > 0) {
						setTitle(socket.getInetAddress().toString() + " rx: " + rx++ + " tx: " + tx);
						appendMessages( input );
					}
				} catch (Exception e) {
					appendMessages("readSocket(): "+e.getMessage());
					closeSocket();
				}
			}
		}}).start();
	}
	
	void sendCommand(final String input){		
		if(printer == null){
			appendMessages("sendCommand(): not connected");
			return;
		}
	
		tx++;
		try {
			printer.checkError();
			printer.println(input);
		} catch (Exception e) {
			appendMessages("sendCommand(): "+e.getMessage());
			closeSocket();
		}
		
		// in.setText(""); // reset text input field 
	}
	
	void appendMessages(final String input){
		messages.append(Util.getDateStampShort() + " " + input + "\n");
		messages.setCaretPosition(messages.getDocument().getLength());
	}
	
	public static void main(String[] args) {
		final String ip = args[0];
		final int port = Integer.parseInt(args[1]);
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				new SwingTerminal(ip, port);
			}
		});
	}
}
