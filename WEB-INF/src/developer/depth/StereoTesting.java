package developer.depth;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.net.URL;

import javax.imageio.ImageIO;
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
import org.opencv.highgui.Highgui;
import org.opencv.highgui.VideoCapture;
import org.opencv.imgproc.Imgproc;

import oculusPrime.Application;
import oculusPrime.OculusImage;
import oculusPrime.Util;
import developer.image.OpenCVUtils;

public class StereoTesting extends JFrame {

	static JPanel panel = new JPanel();
	static JPanel panel_1 = new JPanel();
	static JPanel panel_2 = new JPanel();
	static JPanel panel_3 = new JPanel();
	static final int yoffset = 21; // calibration said 27, but 25 seems a wee bit better
	static final int xoffset = 0;
	static final int xres = 640;
	static final int yres = 360;
	// camera metrics, degrees TODO: from gucview, NOT measured with openCV capture
//	static final double camFOVx169 = 68.46;
	static final double camFOVy169 = 41.71;
	static final double camFOVx43 = 58.90;
	static final double camFOVy43 = 45.90;
//	final static int maxDepthTopView = 5500;
//	final static double maxDispVal = 816;
//	final static double mmPerDispVal = 2286.0/(maxDispVal-255);// mm/disparity value

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
					
//					frame.streamTwoCameras();
					
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
		VideoCapture capture1 = new VideoCapture(1);
		VideoCapture capture0 = new VideoCapture(0);
		
		capture1.set(Highgui.CV_CAP_PROP_FRAME_WIDTH, xres);
		capture1.set(Highgui.CV_CAP_PROP_FRAME_HEIGHT, yres);
		capture0.set(Highgui.CV_CAP_PROP_FRAME_WIDTH, xres);
		capture0.set(Highgui.CV_CAP_PROP_FRAME_HEIGHT, yres);
		
		Util.delay(1000);

		Mat left = new Mat();
		capture1.grab();
		capture1.read(left);

//		rect = new Rect(xoffset,yoffset,left.width()-xoffset,left.height()-yoffset);
//		left = new Mat(left,rect);
//		Imgproc.cvtColor(left, left, Imgproc.COLOR_BGR2GRAY);
//		Imgproc.equalizeHist(left, left);
		
		Mat right = new Mat();
		capture0.grab();
		capture0.read(right);
//		rect = new Rect(0,0,right.width()-xoffset,right.height()-yoffset);
//		right = new Mat(right,rect);
//		Imgproc.cvtColor(right, right, Imgproc.COLOR_BGR2GRAY);
//		Imgproc.equalizeHist(right, right);
		
//		StereoSGBM sbm = new StereoSGBM();
//        sbm.set_SADWindowSize(3); 
//        sbm.set_numberOfDisparities(48);  
//        sbm.set_preFilterCap(63); 
//        sbm.set_minDisparity(4); 
//        sbm.set_uniquenessRatio(10); 
//        sbm.set_speckleWindowSize(50); 
//        sbm.set_speckleRange(32);
//        sbm.set_disp12MaxDiff(1);
//        sbm.set_fullDP(false);
//        sbm.set_P1(216);
//        sbm.set_P2(864);
		
		Stereo stereo = new Stereo();
		Mat disparity = stereo.generateDisparity(left, right);
		
//        Mat disparity = new Mat();
//        sbm.compute(left, right, disparity);
        
        byte[][] topView  = Stereo.projectStereoHorizToTopView(disparity, 320);
//		System.out.println("DISP: "+disparity.cols()+",  "+disparity.rows());
//		System.out.println("elemSize:"+disparity.elemSize()+", elemSize1:"+disparity.elemSize1()+
//					", type:"+disparity.type()+", depth:"+disparity.depth()+" channels:"+disparity.channels());
		
//        int highest = -1;
//        int lowest = 999999;
//        int secondLowest = 999999; 
//
//        for (int x=0; x<disparity.width(); x++) {
//			for (int y=0; y<disparity.height(); y++) {
//			int n = (int) disparity.get(y,x)[0];
//			
//			if (n > highest) highest = n; 
//			if (n < lowest) lowest = n; 
//			if (n < secondLowest && n > lowest) secondLowest = n; 
//			
//			System.out.printf("%4d, ",n);
//			}
//		}
//		System.out.println("\nlowest:"+lowest+", 2nd lowest: "+secondLowest+", highest:"+highest);
		
//		for (int x=0; x<disparity.width(); x++) {
//			System.out.printf("%4d",(int) disparity.get(180,x)[0]);
//		}
//		System.out.println("");
//		System.out.println(disparity.width());
		
        Core.normalize(disparity, disparity, 0, 255, Core.NORM_MINMAX, CvType.CV_8U); 
        
//		for (int x=0; x<disparity.width(); x++) {
//			int n = (int) disparity.get(y,x)[0];
//			System.out.printf("%4d, ",n);
//		}
//		System.out.println("");
		
		JLabel pic0 = new JLabel(new ImageIcon(cv.matToBufferedImage(left)));
		JLabel pic1 = new JLabel(new ImageIcon(cv.matToBufferedImage(right)));

//		Imgproc.cvtColor(left, left,Imgproc.COLOR_BGR2GRAY);
		Imgproc.resize(left, left, new Size(120, 68));
		Imgproc.cvtColor(disparity, disparity,Imgproc.COLOR_GRAY2BGR);
		left.copyTo(new Mat(disparity, new Rect(0,0,120,68)));
		JLabel pic2 = new JLabel(new ImageIcon(cv.matToBufferedImage(disparity)));

		Mat mtv = Stereo.convertByteToMat(topView);
		left.copyTo(new Mat(mtv, new Rect(0,mtv.height()-68-1, 120,68)));
		JLabel pic3 = new JLabel(new ImageIcon(cv.matToBufferedImage(mtv)));

		panel.add(pic0);
		panel_1.add(pic1);
		panel_2.add(pic2);
		panel_3.add(pic3);

		panel.repaint(); 
		panel_1.repaint(); 
		panel_2.repaint(); 
		panel_3.repaint();
	}
	
	private void streamTwoCameras() {
		System.loadLibrary( Core.NATIVE_LIBRARY_NAME );
		System.out.println(Core.NATIVE_LIBRARY_NAME);

		final developer.image.OpenCVUtils cv = new OpenCVUtils();
		
		final VideoCapture capture1 = new VideoCapture(1);

		final VideoCapture capture0 = new VideoCapture(0);
		
		capture1.set(Highgui.CV_CAP_PROP_FRAME_WIDTH, xres);
		capture1.set(Highgui.CV_CAP_PROP_FRAME_HEIGHT, yres);
		capture0.set(Highgui.CV_CAP_PROP_FRAME_WIDTH, xres);
		capture0.set(Highgui.CV_CAP_PROP_FRAME_HEIGHT, yres);

		capture1.set(Highgui.CV_CAP_PROP_FRAME_WIDTH, 800);
		capture1.set(Highgui.CV_CAP_PROP_FRAME_HEIGHT, 448);
		capture0.set(Highgui.CV_CAP_PROP_FRAME_WIDTH, 800);
		capture0.set(Highgui.CV_CAP_PROP_FRAME_HEIGHT, 448);
		
		final JLabel pic0 = new JLabel();
		panel.add(pic0);
		final JLabel pic1 = new JLabel();
		panel_1.add(pic1);
		
		new Thread(new Runnable() {
			public void run() {
				try {
					while(true) {
						
				    	if( capture1.isOpened()) {
				    		Mat left = new Mat();
				    		capture1.read(left);
				    		pic0.setIcon(new ImageIcon(cv.matToBufferedImage(left)));
				    		panel.repaint(); 
				    	}
				    	
//				    	Thread.sleep(200);
				    	
				    	if( capture0.isOpened()) {
				    		Mat right = new Mat();
				    		capture0.read(right);
				    		pic1.setIcon(new ImageIcon(cv.matToBufferedImage(right)));
				    		panel_1.repaint(); 
				    	}
					}
					
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();	
		
	}
	
	/*
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
	*/

	
	/**
	 * Create the frame.
	 */
	public StereoTesting() {
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(50, 50, 1300, 800-Stereo.yoffset*2);
		JPanel contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		contentPane.setLayout(null);

		panel.setBounds(5, 0, 640, 365-Stereo.yoffset);
		panel_1.setBounds(650, 0, 640, 365-Stereo.yoffset);
		panel_2.setBounds(5, 375-Stereo.yoffset, 640, 365-Stereo.yoffset);
		panel_3.setBounds(650, 375-Stereo.yoffset, 360, 325);
		
		contentPane.add(panel);
		contentPane.add(panel_1);
		contentPane.add(panel_2);
		contentPane.add(panel_3);
		
	}

}
