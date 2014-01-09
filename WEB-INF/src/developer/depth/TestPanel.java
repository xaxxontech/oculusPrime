package developer.depth;

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
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
					
//					int res =5;
					
			    	ScanMatch s = new ScanMatch();
			    	String leader = "Z:\\temp\\"; // windows
//	    	    	String leader = "/mnt/skyzorg/temp/"; // linux 
//	    	    	short[] frameBefore = s.getFrame(new File(leader+"xtion610-1.raw"));
//	    	    	short[] frameBefore = s.getFrame(new File(leader+"xtion610-2.raw"));
//	    	    	short[] frameBefore = s.getFrame(new File(leader+"xtion350-1.raw"));
//	    	    	short[] frameBefore = s.getFrame(new File(leader+"xtion350-2.raw"));
//	    	    	short[] frameBefore = s.getFrame(new File(leader+"xtion500-1.raw"));
//	    	    	short[] frameBefore = s.getFrame(new File(leader+"xtion500-2.raw"));
//	    	    	short[] frameBefore = s.getFrame(new File(leader+"xtion517-1.raw"));
	    	    	short[] frameBefore = s.getFrame(new File(leader+"xtion517-2.raw"));
//	    	    	short[] frameBefore = s.getFrame(new File(leader+"xtion25deg-1.raw"));
//	    	    	short[] frameBefore = s.getFrame(new File(leader+"xtion25deg-2.raw"));
//	    	    	short[] frameBefore = s.getFrame(new File(leader+"xtion450-1.raw"));
//	    	    	short[] frameAfter = s.getFrame(new File(leader+"xtion450-2.raw")); // INCOMPLETE!!
			    				    	
			    	
			    	int[][] frameCells1 = s.resampleAveragePixelWithFloorPlane(frameBefore, 2, 2);
//			    	int[][] scaledFrameCells = s.scaleResampledPixels(frameCells1, 450);
//			    	System.out.println(frameCells1[40][30]);
			    	int[] pixels1 = s.cellsToPixels(frameCells1);
			    	
//			    	frameCells1 = s.resampleClosestPixel(pixels1, 10);
//			    	pixels1 = s.cellsToPixels(frameCells1, 10);
			    	
			    	BufferedImage img2 = s.generateDepthFrameImgWithFloorPlane(pixels1);
			    	lblNewLabel.setIcon(new ImageIcon(img2));
					panel.repaint();

					
					int[][] frameCells = s.resampleAveragePixel(frameBefore, 2, 2);
			    	int[][] frameCellsFP = s.findFloorPlane(frameCells);
			    	int[] pixels = s.cellsToPixels(frameCellsFP);
			    	
//			    	int[][] frameCells = s.resampleAveragePixelWithFloorPlane(frameBefore, 2, 2);
//			    	int[] pixels = s.cellsToPixels(frameCells);
			    	
			    	BufferedImage img1 = s.generateDepthFrameImgWithFloorPlane(pixels);

			    	lblNewLabel_1.setIcon(new ImageIcon(img1));
					panel_1.repaint();
										
					
					
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
		setBounds(100, 100, 694, 322);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		contentPane.setLayout(null);
		
		
		panel.setBounds(5, 25, 320, 240);
		contentPane.add(panel);
		
		panel.add(lblNewLabel);
		
		panel_1.setBounds(340, 25, 320, 240);
		contentPane.add(panel_1);
		
		panel_1.add(lblNewLabel_1);
	}
}
