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

public class TestPanel extends JFrame {

	private JPanel contentPane;
	static JLabel lblNewLabel = new JLabel("");
	static JPanel panel = new JPanel();


	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					TestPanel frame = new TestPanel();
					frame.setVisible(true);
					
			    	ScanMatch s = new ScanMatch();
			    	s.frameAfter = s.getFrame(new File("C:\\temp\\xtion2.raw"));
			    	s.frameBefore = s.getFrame(new File("C:\\temp\\xtion1.raw")); 

//			    	BufferedImage img = s.generateDepthFrameImg(s.frameBefore);
					
//			    	int[][] frameCells = s.resampleClosestPixel(s.frameBefore);
//			    	short[] pixels = s.cellsToPixels(frameCells);
//			    	BufferedImage img = s.generateDepthFrameImg(pixels);
			    	
//			    	BufferedImage img = s.generateDepthFrameImg(s.frameAfter);
			    	
			    	int[][] frameCells = s.resampleClosestPixel(s.frameBefore, 2);
			    	int[][] scaledFrameCells = s.scaleClosestPixels(frameCells, 500f, 2);
			    	short[] pixels = s.cellsToPixels(scaledFrameCells, 2);
			    	
			    	frameCells = s.resampleClosestPixel(pixels, 10);
			    	pixels = s.cellsToPixels(frameCells, 10);
			    	
			    	BufferedImage img = s.generateDepthFrameImg(pixels);
			    	
			    	
			    	lblNewLabel.setIcon(new ImageIcon(img));
					panel.repaint();

					
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
		setBounds(100, 100, 450, 300);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		contentPane.setLayout(new BorderLayout(0, 0));
		setContentPane(contentPane);
		
		contentPane.add(panel, BorderLayout.CENTER);
		
		
		panel.add(lblNewLabel);
	}

}
