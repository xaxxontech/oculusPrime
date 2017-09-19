package developer.swingtool;

import java.awt.Dimension;
import java.awt.GridLayout;
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

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;

import oculusPrime.State;
import oculusPrime.State.values;
 
public class NavStateMonitor extends JFrame {
	
	private static final long serialVersionUID = 1L;
	
//	rosmapinfo, rosamcl, rosglobalpath, rosscan,
//	roscurrentgoal, rosmapupdated, rosmapwaypoints, navsystemstatus,
//	rossetgoal, rosgoalstatus, rosgoalcancel, navigationroute, rosinitialpose,
	
	
	final values[] stateValues = { values.navigationrouteid, values.nextroutetime, 
			values.roswaypoint, values.rossetgoal, values.rosgoalstatus,
			values.rosgoalcancel, values.navigationroute };
	//State.values.values();
	
	TableModel model = new StateTableModel();
	BufferedReader reader = null;
	PrintWriter printer = null;
	Socket socket = null;
	long rx = 0;
	String ip;
	int port;
    
    public NavStateMonitor(String ip, int port) {

		this.ip = ip;
		this.port = port;
	
		final JTable table = new JTable(model);
        table.setPreferredScrollableViewportSize(new Dimension(500, 800));
        //table.getColumnModel().getColumn(0).setPreferredWidth(150); 
        //table.getColumnModel().getColumn(0).setMinWidth(100); 
        //table.getColumnModel().getColumn(0).setMaxWidth(250); 
        //table.getColumnModel().getColumn(2).setMaxWidth(60); 
        // table.repaint();
        // table.setEnabled(false);
        
        table.addMouseListener(new MouseListener() {
        	@Override public void mouseReleased(MouseEvent arg) {}
			@Override public void mousePressed(MouseEvent arg) {}
			@Override public void mouseExited(MouseEvent arg) {}
			@Override public void mouseEntered(MouseEvent arg) {}
			@Override // double clicked, force update 
			public void mouseClicked(MouseEvent arg) {
				if(arg.getClickCount() == 2){			
					 int row = table.rowAtPoint(arg.getPoint());
				     // int col = table.columnAtPoint(arg.getPoint());
				     // System.out.println("["+row+", "+col+"] "+ table.getValueAt(row, 0));
				     printer.println("state "+ table.getValueAt(row, 0));
				}
			}
		});

        table.addKeyListener(new KeyListener() {
			@Override public void keyTyped(KeyEvent e) {}
			@Override public void keyPressed(KeyEvent e) {}
			@Override
			public void keyReleased(KeyEvent e) {
				if(e.getKeyCode() == 10){
					String update = "";
					if(table.getValueAt(table.getSelectedRow(), 1) != null) update = (String) table.getValueAt(table.getSelectedRow(), 1);
					// System.out.println(" ... typed: " + table.getValueAt(table.getSelectedRow(), 0) + " [" + update + "]");
					if(update.length() == 0) printer.println("state delete " + table.getValueAt(table.getSelectedRow(), 0));
					else printer.println("state " + table.getValueAt(table.getSelectedRow(), 0) + " " + update);
				}	
			}
		});
  
        setDefaultCloseOperation(EXIT_ON_CLOSE);
		setDefaultLookAndFeelDecorated(true);
		setLayout(new GridLayout(1,0));
        JScrollPane scrollPane = new JScrollPane(table);
        getContentPane().add(scrollPane);
        pack();
        setVisible(true);
		new Timer().scheduleAtFixedRate(new Task(), 2000, 10000);
	
	//	new Timer().scheduleAtFixedRate(new nullTask(), Util.TWO_MINUTES, Util.TWO_MINUTES);

    }
 
    class StateTableModel extends AbstractTableModel {
    	
		private static final long serialVersionUID = 1L;
		String[] columnNames = {"state", "value", "count"};
        Object[][] data = new Object[State.values.values().length][3];
		
		StateTableModel(){
			for(int i = 0; i < stateValues.length; i++){
				data[i][0] = stateValues[i].name();
			//	data[i][1] = "null";
				data[i][2] = 0;
			}	
			
            System.out.println("length " + stateValues.length );//+ "," + col + "] value = " + value + " " + data[row][0]);    	

		}
		
        public int getColumnCount() { return columnNames.length; }
        public int getRowCount() { return data.length; }
        public String getColumnName(int col) { return columnNames[col]; }
        public Object getValueAt(int row, int col) { return data[row][col]; }

		@SuppressWarnings("unchecked")
		public Class getColumnClass(int c) {
            if(c == 0 || c == 1) return String.class;
			return Integer.class;
        }
		
        public boolean isCellEditable(int row, int col) {
            if(col == 0 || col == 2) return false;
            return true;
        }
    
        public void setValueAt(Object value, int row, int col) {
             System.out.println("[" + row + "," + col + "] value = " + value + " " + data[row][0]);    	
              data[row][col] = value;
              fireTableCellUpdated(row, col);
        }       
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
					printer.println("state"); 
				} catch (Exception e) {
					System.out.println("TimerTask(): "+e.getMessage());
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
			System.out.println("openSocket(): connected to: " + socket.getInetAddress().toString());
			setTitle(socket.getInetAddress().toString());
//			rx = 0;
//			printer.println("state"); 
		} catch (Exception e) {
			setTitle("disconnected");
			System.out.println("openSocket(): " + e.getMessage());
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
				System.out.println("closeSocket(): " + e.getLocalizedMessage());
			}
		}
		try { 
			if(socket != null) socket.close(); 
		} catch (IOException ex) {
			System.out.println("closeSocket(): " + ex.getLocalizedMessage());
		}
	}

	void readSocket(){	
		new Thread(new Runnable() { public void run() {
			String input = null;
			while(printer != null) {
				try {
					input = reader.readLine();
					if(input == null) {
						System.out.println("readSocket(): closing..");
						try { Thread.sleep(5000); } catch (InterruptedException e) {}
						closeSocket();
						break;
					}
					
					// ignore dummy messages 
					input = input.trim();
					if(input.length() > 0) {
						
						setTitle(socket.getInetAddress().toString() + " rx: " + rx++);
/*			
						input = input.replace("<telnet>", "");
						input = input.replace("=", "");
						input = input.replace("  ", " ");
						input = input.trim();
	*/					
						String[] tokens = input.split(" ");	
						System.out.println("[" + input + "] tokens:" + tokens.length);
					/*	
						if(input.contains("deleted")){
							for( int i = 0 ; i < model.getRowCount() ; i++ )
								if(model.getValueAt(i, 0).equals(tokens[tokens.length-1]))
									model.setValueAt( null, i, 1);		
						}
					
						if(input.contains("<state>")){
							System.out.println(input);
							for( int i = 0 ; i < model.getRowCount() ; i++ ){
								if(model.getValueAt(i, 0).equals(tokens[1])){
									String value = input.substring(input.indexOf(tokens[2]), input.length());
									if( ! value.equals("null")){
										model.setValueAt(value, i, 1);
										model.setValueAt((int)model.getValueAt(i, 2)+1, i, 2);
									}
								}
							}
						}
							*/
						if(tokens.length == 2){
							System.out.println("[" + input + "] tokens:" + tokens.length);
							for( int i = 0 ; i < model.getRowCount() ; i++ ){
								if(model.getValueAt(i, 0).equals(tokens[0])){
									model.setValueAt(tokens[1], i, 1);
									model.setValueAt((int)model.getValueAt(i, 2)+1, i, 2);
								}
							}
						}
					
						
					}
				} catch (Exception e) {
					System.out.println("readSocket(): input = "+input);
					System.out.println("readSocket(): "+e.getMessage());
				//	closeSocket();
				}
			}
		}}).start();
	}

    public static void main(String[] args) {
    	final String ip = args[0];
		final int port = Integer.parseInt(args[1]);
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
            	new NavStateMonitor(ip, port);
            }
        });
    }
}