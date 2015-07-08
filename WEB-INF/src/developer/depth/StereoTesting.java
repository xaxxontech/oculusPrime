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
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.highgui.VideoCapture;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.Photo;

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
//	static final int xres = 640;
//	static final int yres = 360;
//	static final int xres = 800; 
//	static final int yres = 448; 
	// camera metrics, degrees TODO: from gucview, NOT measured with openCV capture
//	static final double camFOVx169 = 68.46;
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

                    System.loadLibrary( Core.NATIVE_LIBRARY_NAME );
//                    System.load("C:\\stuff\\opencv248\\build\\java\\x86\\opencv_java248.dll");

//					frame.grabDockInStereo();
					
					frame.sgbmTest();
//                    frame.saveImages();
//                    frame.loadImages();
//					frame.streamTwoCameras();
//					frame.distanceScanMatchTest();
					
					System.out.println("done!");
					
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	
	private void sgbmTest() {
//		developer.image.OpenCVUtils cv = new OpenCVUtils();

//        Mat[] mats = captureImages();
        Mat[] mats = loadImages();
        Mat left = mats[0];
        Mat right = mats[1];

        Rect rect = new Rect(Stereo.xoffset, Stereo.yoffset,left.width() -
					Stereo.xoffset,left.height()-Stereo.yoffset);
		left = new Mat(left,rect);
		Mat leftc = new Mat();
		left.copyTo(leftc);
		Imgproc.cvtColor(left, left, Imgproc.COLOR_BGR2GRAY);
		Imgproc.warpAffine(left, left, new Stereo().rotImage, left.size()); // rotate
		Imgproc.equalizeHist(left, left);
//		Photo.fastNlMeansDenoising(right, right);
		
		rect = new Rect(0,0,right.width()-Stereo.xoffset,right.height()-Stereo.yoffset);
		right = new Mat(right,rect);
		Imgproc.cvtColor(right, right, Imgproc.COLOR_BGR2GRAY);
		Imgproc.equalizeHist(right, right);
//		Photo.fastNlMeansDenoising(right, right);
		
		Stereo stereo = new Stereo();
        Mat disparity = new Mat();
        Mat disparityTopView = new Mat();

		long start = System.currentTimeMillis();

//        stereo.sbmImage.compute(left, right, disparity);
        stereo.sbmTopView.compute(left, right, disparity);
        stereo.sbmTopView.compute(left, right, disparityTopView);

    	long duration = System.currentTimeMillis() - start;
    	System.out.println("time: "+ String.valueOf(duration));
    	
    	int y = disparity.height()/2;
    	for (int x = 0; x<disparity.width(); x++) {
    		double d = disparity.get(y, x)[0];
    		System.out.printf("%4d, ",(int) d);
    	}
    	System.out.println("");
        
//        short[][] topView  = Stereo.projectStereoHorizToTopViewFiltered(disparity, 240);
//        short[][] topView  = Stereo.projectStereoHorizToTopViewFiltered(disparityTopView, 320);
        short[][] topView  = Stereo.projectStereoHorizToTopViewFilteredLess(disparity, 240);
        topView = Stereo.topViewProbabilityRendering(topView);

		
		JLabel pic0 = new JLabel(new ImageIcon(OpenCVUtils.matToBufferedImage(left)));
		JLabel pic1 = new JLabel(new ImageIcon(OpenCVUtils.matToBufferedImage(right)));

        Core.normalize(disparity, disparity, 0, 255, Core.NORM_MINMAX, CvType.CV_8U); 
//		Imgproc.cvtColor(left, left,Imgproc.COLOR_BGR2GRAY);
//		Imgproc.resize(leftc, leftc, new Size(120, 68));
//		Imgproc.cvtColor(disparity, disparity,Imgproc.COLOR_GRAY2BGR);
//		leftc.copyTo(new Mat(disparity, new Rect(0,0,120,68)));
		JLabel pic2 = new JLabel(new ImageIcon(OpenCVUtils.matToBufferedImage(disparity)));

		Mapper.addArcPath(topView, 0, 0);
//		Mapper.addArcPath(topView, 10, -0.6);
//		Mapper.addArcPath(topView, 0, 45);
		BufferedImage img = ScanUtils.cellsToImage(Mapper.map);
		JLabel pic3 = new JLabel(new ImageIcon(img));
		
//		Mat mtv = Stereo.convertShortToMat(topView);
//		leftc.copyTo(new Mat(mtv, new Rect(0,mtv.height()-68-1, 120,68)));
//		JLabel pic3 = new JLabel(new ImageIcon(cv.matToBufferedImage(mtv)));
//		System.out.println("mtv width: "+mtv.width()+", height: "+mtv.height());

		panel.add(pic0);
		panel_1.add(pic1);
		panel_2.add(pic2);
		panel_3.add(pic3);

		panel.repaint(); 
		panel_1.repaint(); 
		panel_2.repaint(); 
		panel_3.repaint();
		
//		System.out.println(disparity.width()+", "+disparity.height());
	}

    private void saveImages() {

        Mat[] mats = captureImages();
        Mat left = mats[0];
        Mat right = mats[1];

        String folder = "/home/colin/temp/";
        Highgui.imwrite(folder+"left.png", left);
        Highgui.imwrite(folder+"right.png", right);
    }

    private Mat[] loadImages() {
        String folder = "Z:\\xaxxon\\oculusPrime\\software\\scans-dev-temp\\stereo\\";
//        String folder = "/mnt/skyzorg/xaxxon/oculusPrime/software/scans-dev-temp/stereo/";
        Mat left = Highgui.imread(folder+"left0.png");
        Mat right = Highgui.imread(folder+"right0.png");
//		Mat left = Highgui.imread(folder+"left500_1-12.png");
//		Mat right = Highgui.imread(folder+"right500_1-12.png");
        
//		Mat left = Highgui.imread(folder+"left1.png");
//		Mat right = Highgui.imread(folder+"right1.png");
//		Mat left = Highgui.imread(folder+"left500_3-25.png");
//		Mat right = Highgui.imread(folder+"right500_3-25.png");

        return new Mat[] {left, right};
    }
    
    private void distanceScanMatchTest() {
    	int h = 320;
    	Stereo stereo = new Stereo();
//    	developer.image.OpenCVUtils cv = new OpenCVUtils();
    	
        String folder = "Z:\\xaxxon\\oculusPrime\\software\\scans-dev-temp\\stereo\\";
//    	String folder = "/mnt/skyzorg/xaxxon/oculusPrime/software/scans-dev-temp/stereo/";
//		Mat left = Highgui.imread(folder+"left2.png");
//		Mat right = Highgui.imread(folder+"right2.png");
//		Mat left = Highgui.imread(folder+"left1.png");
//		Mat right = Highgui.imread(folder+"right1.png");
		Mat left = Highgui.imread(folder+"left0.png");
		Mat right = Highgui.imread(folder+"right0.png");

		Mat disparity = stereo.generateDisparity(left, right, stereo.sbmTopView);
		short[][] topViewBefore = Stereo.projectStereoHorizToTopViewFiltered(disparity, h);
		
		Mat m = Stereo.convertShortToMat(topViewBefore);
		panel.setBounds(5, 5, m.width(), m.height()+5);
		JLabel pic0 = new JLabel(new ImageIcon(OpenCVUtils.matToBufferedImage(m)));
		panel.add(pic0);
		panel.repaint();
		
//		left = Highgui.imread(folder+"left2-415-0_6.png");
//		right = Highgui.imread(folder+"right2-415-0_6.png");
		left = Highgui.imread(folder+"left500_3-25.png");
		right = Highgui.imread(folder+"right500_3-25.png");
//		left = Highgui.imread(folder+"left500_1-12.png");
//		right = Highgui.imread(folder+"right500_1-12.png");

		disparity = stereo.generateDisparity(left, right, stereo.sbmTopView);
		short[][] topViewAfter = Stereo.projectStereoHorizToTopViewFiltered(disparity, h);
		
		m = Stereo.convertShortToMat(topViewAfter);
		panel_1.setBounds(640, 5, m.width(), m.height()+5);
		JLabel pic1 = new JLabel(new ImageIcon(OpenCVUtils.matToBufferedImage(m)));
		panel_1.add(pic1);
		panel_1.repaint();
		
		double angle = 3.25;
		int d= Stereo.findDistanceTopView(topViewBefore, topViewAfter, angle, 500)[0];
		Mapper.addArcPath(topViewBefore, 0, 0);
		Mapper.addArcPath(topViewAfter, d, angle);
		BufferedImage img = ScanUtils.cellsToImage(Mapper.map);
		JLabel pic2 = new JLabel(new ImageIcon(img));
		panel_2.setBounds(5, 340, img.getWidth(), img.getHeight()+10);
		panel_2.add(pic2);
		panel_2.repaint();

		System.out.println(d);
		
    }

    private Mat[] captureImages() {
        VideoCapture capture1 = new VideoCapture(1);
        VideoCapture capture0 = new VideoCapture(0);

        capture1.set(Highgui.CV_CAP_PROP_FRAME_WIDTH, Stereo.xres);
        capture1.set(Highgui.CV_CAP_PROP_FRAME_HEIGHT, Stereo.yres);
        capture0.set(Highgui.CV_CAP_PROP_FRAME_WIDTH, Stereo.xres);
        capture0.set(Highgui.CV_CAP_PROP_FRAME_HEIGHT, Stereo.yres);

        Util.delay(1000);

        Mat left = new Mat();
        capture1.grab(); // discard 1st frame
        capture1.read(left);

        Mat right = new Mat();
        capture0.grab(); // discard 1st frame
        capture0.read(right);

        return new Mat[]{left, right};
    }


	private void streamTwoCameras() {
		System.loadLibrary( Core.NATIVE_LIBRARY_NAME );
		System.out.println(Core.NATIVE_LIBRARY_NAME);

//		final developer.image.OpenCVUtils cv = new OpenCVUtils();
		
		final VideoCapture capture1 = new VideoCapture(1);

		final VideoCapture capture0 = new VideoCapture(0);
		
		capture1.set(Highgui.CV_CAP_PROP_FRAME_WIDTH, Stereo.xres);
		capture1.set(Highgui.CV_CAP_PROP_FRAME_HEIGHT, Stereo.yres);
		capture0.set(Highgui.CV_CAP_PROP_FRAME_WIDTH, Stereo.xres);
		capture0.set(Highgui.CV_CAP_PROP_FRAME_HEIGHT, Stereo.yres);

//		capture1.set(Highgui.CV_CAP_PROP_FRAME_WIDTH, 800);
//		capture1.set(Highgui.CV_CAP_PROP_FRAME_HEIGHT, 448);
//		capture0.set(Highgui.CV_CAP_PROP_FRAME_WIDTH, 800);
//		capture0.set(Highgui.CV_CAP_PROP_FRAME_HEIGHT, 448);
		
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
				    		pic0.setIcon(new ImageIcon(OpenCVUtils.matToBufferedImage(left)));
				    		panel.repaint(); 
				    	}
				    	
//				    	Thread.sleep(200);
				    	
				    	if( capture0.isOpened()) {
				    		Mat right = new Mat();
				    		capture0.read(right);
				    		pic1.setIcon(new ImageIcon(OpenCVUtils.matToBufferedImage(right)));
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
		panel_3.setBounds(650, 375-Stereo.yoffset, 435, 325);
		
		contentPane.add(panel);
		contentPane.add(panel_1);
		contentPane.add(panel_2);
		contentPane.add(panel_3);
		
	}

}
