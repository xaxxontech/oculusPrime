package developer.swingtool;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import oculusPrime.PlayerCommands;

import java.awt.*;

public class Frame extends JFrame implements Runnable {

	private static final long serialVersionUID = 1L;

	@SuppressWarnings("unchecked")
	public Frame(final JTextField in, final JTextArea out, String title) {

		// do minimal layout
		setTitle(title);
		setDefaultLookAndFeelDecorated(true);
		setLayout(new BorderLayout());

		DefaultListModel<PlayerCommands> listModel = new DefaultListModel<PlayerCommands>();

		PlayerCommands[] cmds = PlayerCommands.values();
		for (int i = 0; i < cmds.length; i++) {
			listModel.addElement(cmds[i]);
		}

		// Create the list and put it in a scroll pane.
		final JList list = new JList(listModel);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
//		list.setSelectedIndex(2);
//		list.getSelectedIndex();
		
		// list.addListSelectionListener(this);
		// list.setVisibleRowCount(5);

		listModel.addListDataListener(new ListDataListener() {

			@Override
			public void intervalRemoved(ListDataEvent e) {
				// TODO Auto-generated method stub
				System.out.println("contentsChanged: " + e.getIndex1());

			}

			@Override
			public void intervalAdded(ListDataEvent e) {
				// TODO Auto-generated method stub
				System.out.println("contentsChanged: " + e.getIndex1());

			}

			@Override
			public void contentsChanged(ListDataEvent e) {

				in.setText(".............. " +		list.getSelectedIndex() );
				System.out.println("contentsChanged: " + e.getIndex1());
				
			}
		});
		
		JScrollPane listScrollPane = new JScrollPane(list);

		JScrollPane chatScroller = new JScrollPane(out);
		JScrollPane cmdsScroller = new JScrollPane(listScrollPane);

		chatScroller.setPreferredSize(new Dimension(300, 400));
		cmdsScroller.setPreferredSize(new Dimension(200, 400));

		getContentPane().add(chatScroller, BorderLayout.LINE_END);
		getContentPane().add(cmdsScroller, BorderLayout.LINE_START);

		getContentPane().add(in, BorderLayout.PAGE_END);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		chatScroller.setFocusable(false);
		in.setFocusable(true);
		in.requestFocus();

		out.setText("'''''''''''''satated: " + listModel.get(9));

	}

	// swing will call us when ready
	public void run() {
//		setResizable(false);
//		setAlwaysOnTop(true);
		pack();
		setVisible(true);
	}
}
