package developer.depth;

import java.awt.EventQueue;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JLabel;

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
					
					
			    	String leader = "Z:\\xaxxon\\oculusPrime\\software\\"; // windows
//	    	    	String leader = "/mnt/skyzorg/xaxxon/oculusPrime/software/"; // linux 
	    	    	short[] frameBefore = ScanUtils.getFrame(new File(leader+"xtion500-1.raw"));
	    	    	short[] frameAfter = ScanUtils.getFrame(new File(leader+"xtion500-2.raw"));
			    	
	    	    	int res =2;
	    	    	int h = 240;
	    	    	
	    	    	int[][] frameCells1 = ScanUtils.resampleAveragePixel(frameAfter, res, res);
			    	int[][] zork = ScanUtils.findFloorPlane(frameCells1);
			    	byte[][] fp = ScanUtils.floorPlaneToPlanView(zork, h);
			    	Mapper.add(fp, 0, 0); 
			    	
			    	BufferedImage img2 = ScanUtils.byteCellsToImage(Mapper.map);
			    	lblNewLabel.setIcon(new ImageIcon(img2));
			    	panel.setBounds(5,0,img2.getWidth(),img2.getHeight()+5);
					panel.repaint();
			    	System.out.println("img2: "+img2.getWidth()+", "+img2.getHeight());
					
			    	
			    	int[][] frameCells2 = ScanUtils.resampleAveragePixel(frameAfter, res, res);
			    	int[][] blorg = ScanUtils.findFloorPlane(frameCells2);
			    	byte[][] asdf = ScanUtils.floorPlaneToPlanView(blorg, h);
//			    	Mapper.add(asdf, 555, 0);
//			    	Mapper.add(asdf, 0, 35);
//			    	Mapper.add(asdf, 0, 35);
//			    	Mapper.add(asdf, 0, 19);
//			    	Mapper.add(asdf, 999, 0);
//			    	Mapper.add(asdf, 999, 0);
//			    	Mapper.add(asdf, 999, 0);
//			    	Mapper.add(asdf, 999, 0);
//			    	Mapper.add(asdf, 0, 35);
//			    	Mapper.add(asdf, 0, 35);
//			    	Mapper.add(asdf, 0, 19);
//			    	Mapper.add(asdf, 999, 0);
//			    	Mapper.add(asdf, 999, 0);
			    	Mapper.add(asdf,  0, -135);
//			    	Mapper.add(asdf,  0,  1);
			    	Mapper.add(asdf,  999,  0);

			    	BufferedImage img1 = ScanUtils.byteCellsToImage(Mapper.map);

			    	lblNewLabel_1.setIcon(new ImageIcon(img1));
					panel_1.setBounds(340, 0, img1.getWidth(), img1.getHeight()+10);
					panel_1.repaint();
 					System.out.println("img1: "+img1.getWidth()+", "+img1.getHeight());
					
					
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
