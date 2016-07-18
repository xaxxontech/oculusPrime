package developer.depth;

import java.awt.EventQueue;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JLabel;

import oculusPrime.Application;

public class TestPanel extends JFrame {

	private JPanel contentPane;
	static JPanel panel = new JPanel();
	static JLabel lblNewLabel = new JLabel("");
	static JPanel panel_1 = new JPanel();
	static JLabel lblNewLabel_1 = new JLabel("");

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					TestPanel frame = new TestPanel();
					frame.setVisible(true);
		
//			    	String leader = "Z:\\xaxxon\\oculusPrime\\software\\"; // windows
	    	    	String leader = "/mnt/skyzorg/xaxxon/oculusPrime/software/"; // linux 
//	    	    	short[] frameBefore = ScanUtils.getFrame(new File(leader+"xtion408-2p37-1.raw"));
//	    	    	short[] frameAfter = ScanUtils.getFrame(new File(leader+"xtion408-2p37-2.raw"));
	    	    	short[] frameBefore = ScanUtils.getFrame(new File(leader+"xtion400-1p99-1.raw"));
	    	    	short[] frameAfter = ScanUtils.getFrame(new File(leader+"xtion400-1p99-2.raw"));

	    	    	int res =2;
	    	    	int h = 320;
	    	    	
			    	Mapper.addMove(frameBefore, 0, 0); // new map
			    	BufferedImage img2 = ScanUtils.cellsToImage(Mapper.map);
			    	
			    	lblNewLabel.setIcon(new ImageIcon(img2));
			    	panel.setBounds(5,0,img2.getWidth(),img2.getHeight()+10);
					panel.repaint();


//					byte[][] fp2 = ScanUtils.projectFrameHorizToTopView(frameAfter, h);
//			    	double angle = -1.99;
//			    	int d = ScanUtils.findDistanceTopView(frameBefore, frameAfter, angle, 400);
			    	Mapper.addMove(frameAfter, 408, -2.37);

			    	BufferedImage img1 = ScanUtils.cellsToImage(Mapper.map);

			    	lblNewLabel_1.setIcon(new ImageIcon(img1));
					panel_1.setBounds(340, 0, img1.getWidth(), img1.getHeight()+10);
					panel_1.repaint();
					
// 					System.out.println(ScanUtils.findDistanceTopView(frameBefore, frameAfter, angle, 400));
 					
					
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the frame.
	 */
	public TestPanel() {
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(0, 0, 750, 500);
		contentPane = new JPanel();
//		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		contentPane.setLayout(null);
		
		
		panel.setBounds(0, 0, 320, 240);
		contentPane.add(panel);
//		panel.setLayout(null);
		lblNewLabel.setBounds(160, 0, 0, 0);
		
		panel.add(lblNewLabel);
		
		panel_1.setBounds(340, 0, 320, 240);
		contentPane.add(panel_1);
//		panel_1.setLayout(null);
		lblNewLabel_1.setBounds(160, 0, 0, 0);
		
		panel_1.add(lblNewLabel_1);
	}
}
