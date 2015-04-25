package developer.swingtool;

import javax.swing.*;
import java.awt.*;

public class Frame extends JFrame implements Runnable {
	
	private static final long serialVersionUID = 1L;

	public Frame(JTextField in, JTextArea out, String title) {

		// do minimal layout
		setTitle(title);
		setDefaultLookAndFeelDecorated(true);
		setLayout(new BorderLayout());
		JScrollPane chatScroller = new JScrollPane(out);
		chatScroller.setPreferredSize(new Dimension(400, 700));
		getContentPane().add(chatScroller, BorderLayout.NORTH);
		getContentPane().add(in, BorderLayout.PAGE_END);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		
		chatScroller.setFocusable(false);
		in.setFocusable(true);
		in.requestFocus();
		
	}

	// swing will call us when ready 
	public void run() {
		setResizable(false);
		setAlwaysOnTop(true);
		pack();
		setVisible(true);
	}
}
