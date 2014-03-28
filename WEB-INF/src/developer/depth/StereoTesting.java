package developer.depth;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.JLabel;

import org.opencv.calib3d.StereoSGBM;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import oculusPrime.OculusImage;
import developer.image.OpenCVUtils;

public class StereoTesting extends JFrame {

	static JPanel panel = new JPanel();
	static JPanel panel_1 = new JPanel();
	static JPanel panel_2 = new JPanel();

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
//			private JPanel contentPane;

			public void run() {
				try {
					StereoTesting frame = new StereoTesting();
					frame.setVisible(true);
					
//					frame.grabDockInStereo();
					
					frame.sgbmTest();
					
					System.out.println("done!");
					
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	
	private void sgbmTest() {
		System.loadLibrary( Core.NATIVE_LIBRARY_NAME );
		developer.image.OpenCVUtils cv = new OpenCVUtils();
		
		Rect rect;
		int yoffset = 25; // calibration said 27, but 25 seems a wee bit better
		int xoffset = 5;
		
		Mat left = cv.getWebCamImg(1);
		rect = new Rect(xoffset,yoffset,left.width()-xoffset,left.height()-yoffset);
		left = new Mat(left,rect);
		Imgproc.cvtColor(left, left, Imgproc.COLOR_BGR2GRAY);
		Imgproc.equalizeHist(left, left);
//		Imgproc.blur(left, left, new Size(5,5));
		
		
		Mat right = cv.getWebCamImg(0);
		rect = new Rect(0,0,right.width()-xoffset,right.height()-yoffset);
		right = new Mat(right,rect);
		Imgproc.cvtColor(right, right, Imgproc.COLOR_BGR2GRAY);
		Imgproc.equalizeHist(right, right);
//		Imgproc.blur(right, right, new Size(5,5));
		
		StereoSGBM sbm = new StereoSGBM();
        sbm.set_SADWindowSize(3); 
        sbm.set_numberOfDisparities(48);  
        sbm.set_preFilterCap(63); 
        sbm.set_minDisparity(4); 
        sbm.set_uniquenessRatio(10); 
        sbm.set_speckleWindowSize(50); 
        sbm.set_speckleRange(32);
        sbm.set_disp12MaxDiff(1);
        sbm.set_fullDP(false);
        sbm.set_P1(216);
        sbm.set_P2(864);
//        Mat temp = new Mat();
        Mat disparity = new Mat();
        sbm.compute(left, right, disparity);
        Core.normalize(disparity, disparity, 0, 255, Core.NORM_MINMAX, CvType.CV_8U); 
        
		JLabel pic0 = new JLabel(new ImageIcon(cv.matToBufferedImage(left)));
		JLabel pic1 = new JLabel(new ImageIcon(cv.matToBufferedImage(right)));
		JLabel pic2 = new JLabel(new ImageIcon(cv.matToBufferedImage(disparity)));

		panel.add(pic0);
		panel_1.add(pic1);
		panel_2.add(pic2);

		panel.repaint(); 
		panel_1.repaint(); 
		panel_2.repaint(); 
		
	}
	
	private void grabDockInStereo() {
		developer.image.OpenCVUtils cv = new OpenCVUtils();
		BufferedImage img;
		OculusImage oculusImage = new OculusImage();
		int[] argb;
		String results[];
		String str;
		int x, y, w, h, ctrx, ctry;
		Graphics2D g2d;
		oculusImage.dockSettings("1.2162162_0.21891892_0.18708709_0.24039039_135_134_90_74_-0.0");

		img = cv.webcamCapture(1);
		oculusImage.lastThreshhold = -1;
		argb = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
		results = oculusImage.findBlobs(argb, img.getWidth(), img.getHeight());
		x = Integer.parseInt(results[0]);
		y = Integer.parseInt(results[1]);
		w = Integer.parseInt(results[2]);
		h = Integer.parseInt(results[3]);
		ctrx = x+w/2;
		ctry = y+h/2;
//		img.setRGB(x, y, 0xff0000);
		g2d = img.createGraphics();
		g2d.setColor(new Color(255,0,0));
		g2d.drawRect(x, y, w, h);
		JLabel pic0 = new JLabel(new ImageIcon(img));
		panel.add(pic0);
		panel.repaint(); 
		str = x+" "+y+" "+w+" "+h+" "+results[4];
		System.out.println(str);

		
		img = cv.webcamCapture(0);
		oculusImage.lastThreshhold = -1;
		argb = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
		results = oculusImage.findBlobs(argb, img.getWidth(), img.getHeight());
		x = Integer.parseInt(results[0]);
		y = Integer.parseInt(results[1]);
		w = Integer.parseInt(results[2]);
		h = Integer.parseInt(results[3]);
		ctrx = x+w/2;
		ctry = y+h/2;
		img.setRGB(x, y, 0xff0000);
		g2d = img.createGraphics();
		g2d.setColor(new Color(255,0,0));
		g2d.drawRect(x, y, w, h);
		JLabel pic1 = new JLabel(new ImageIcon(img));
		panel_1.add(pic1);
		panel_1.repaint(); 
		str = x+" "+y+" "+w+" "+h+" "+results[4];
		System.out.println(str);
	}

	/**
	 * Create the frame.
	 */
	public StereoTesting() {
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//		setBounds(200, 400, 700, 500);
		setBounds(50, 50, 1300, 950);
		JPanel contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		contentPane.setLayout(null);


		
//		panel.setBounds(5, 0, 320, 245);
		panel.setBounds(5, 0, 640, 465);
		
//		panel_1.setBounds(330, 0, 320, 245);
		panel_1.setBounds(650, 0, 640, 465);
		
		panel_2.setBounds(5, 450, 640, 465);
		
		contentPane.add(panel);
		contentPane.add(panel_1);
		contentPane.add(panel_2);

		
	}

}
