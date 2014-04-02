package developer.depth;

import java.awt.image.BufferedImage;

import oculusPrime.Util;

import org.opencv.calib3d.StereoSGBM;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.highgui.VideoCapture;
import org.opencv.imgproc.Imgproc;

import developer.image.OpenCVUtils;

public class Stereo {
	private static VideoCapture captureLeft;
	private static VideoCapture captureRight;
	private final int camLeft = 1;
	private final int camRight = 0;
//	private static Mat left;
//	private static Mat right;
//	private Mat disparity = null;
	private developer.image.OpenCVUtils cv;
	public boolean stereoCamerasOn = false;
	public boolean stereoBusy = true;
	private StereoSGBM sbm;
	public static final int yoffset = 21; // 25 for 480
	public static final int xoffset = 0; // 5 for 640x480
	private BufferedImage disp = null;
	private final int xres = 640;
	private final int yres = 360;
	
	public Stereo() {
		System.loadLibrary( Core.NATIVE_LIBRARY_NAME );
		cv = new OpenCVUtils();
//		left = new Mat();
//		right = new Mat();
		
		sbm = new StereoSGBM();
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
	}
	
	public void startCameras() {
		if (stereoCamerasOn) return;
		
		stereoCamerasOn = true;
		
		captureLeft = new VideoCapture(camLeft);
		captureRight = new VideoCapture(camRight);
		
		captureLeft.set(Highgui.CV_CAP_PROP_FRAME_WIDTH, xres);
		captureLeft.set(Highgui.CV_CAP_PROP_FRAME_HEIGHT, yres);
		captureRight.set(Highgui.CV_CAP_PROP_FRAME_WIDTH, xres);
		captureRight.set(Highgui.CV_CAP_PROP_FRAME_HEIGHT, yres);

		new Thread(new Runnable() { public void run() { try {

			while(true && stereoCamerasOn) {
				captureRight.grab();
			}
					
		} catch (Exception e) { e.printStackTrace(); } } }).start();	
		
		new Thread(new Runnable() { public void run() { try {

			while(true && stereoCamerasOn) {
	    		captureLeft.grab();
			}
			
		} catch (Exception e) { e.printStackTrace(); } } }).start();	

	}
	
	public void stopCameras() {
		if (!stereoCamerasOn) return;
		
		stereoCamerasOn = false;
    	captureRight.release();
    	captureLeft.release();
    	captureRight = null;
    	captureLeft = null;
	}
	
	public Mat generateDisparity(Mat L, Mat R) {
		Rect rect;
		
		rect = new Rect(xoffset,yoffset,L.width()-xoffset,L.height()-yoffset);
		L = new Mat(L,rect);
		Imgproc.cvtColor(L, L, Imgproc.COLOR_BGR2GRAY);
		Imgproc.equalizeHist(L, L);
		
		rect = new Rect(0,0,R.width()-xoffset,R.height()-yoffset);
		R = new Mat(R,rect);
		Imgproc.cvtColor(R, R, Imgproc.COLOR_BGR2GRAY);
		Imgproc.equalizeHist(R, R);
		
        Mat disparity = new Mat();
        sbm.compute(L, R, disparity);
        
        return disparity;
	}
	
	public BufferedImage getImage() {
		if (!stereoCamerasOn) return null;
		
//		long start = System.currentTimeMillis();

		Mat right = new Mat();
		Mat left = new Mat();
		captureRight.retrieve(right);
		captureLeft.retrieve(left);

    	Mat disparity = generateDisparity(left,right);
    	
        Core.normalize(disparity, disparity, 0, 255, Core.NORM_MINMAX, CvType.CV_8U); 
        
        int insetW=160;
        int insetH= yres*insetW/xres;
		Imgproc.cvtColor(left, left,Imgproc.COLOR_BGR2GRAY);
		Imgproc.resize(left, left, new Size(insetW, insetH));
//		Imgproc.cvtColor(disparity, disparity,Imgproc.COLOR_GRAY2BGR);
		left.copyTo(new Mat(disparity, new Rect(0,0,insetW,insetH)));
    	
    	disp = cv.matToBufferedImage(disparity);
    	
//    	long duration = System.currentTimeMillis() - start;
//    	Util.debug( String.valueOf(duration), this);
    	
		return disp;
	}
	
	public BufferedImage getTopView() {
		if (!stereoCamerasOn) return null;
		Mat right = new Mat();
		Mat left = new Mat();
		captureRight.retrieve(right);
		captureLeft.retrieve(left);

    	Mat disparity = generateDisparity(left,right);
    	byte[][] topView = projectStereoHorizToTopView(disparity, 320);
    	
		Imgproc.resize(left, left, new Size(120, 68));
		Mat mtv = Stereo.convertByteToMat(topView);
		left.copyTo(new Mat(mtv, new Rect(0,mtv.height()-68-1, 120,68)));
    	
		return cv.matToBufferedImage(mtv);
	}
	
	public static Mat convertByteToMat(byte[][] frame) {
		int w = frame.length;
		int h = frame[0].length;
		Mat m = new Mat(h, w, CvType.CV_8UC3);
		for (int x = 0; x<w; x++) {
			for (int y=0; y<h; y++) {
				m.put(y, x, new byte[] {0,frame[x][y],0});
			}
		}
		return m;
	}
	
	public static byte[][] projectStereoHorizToTopView(Mat frame, int h) { 
		final double camFOVx169 = 68.46;
		final int maxDepthTopView = 5500;
		final double maxDispVal = 816;
		final double minDispVal = 48;
//		final double mmPerDispVal = 2286.0/(maxDispVal-275);// measured mm/disparity value
		final double multiplier = (275/16.0)*2286; // disparity*mm
		
		final int w = (int) (Math.sin(Math.toRadians(camFOVx169)/2) * h) * 2;
		final double angle = Math.toRadians(camFOVx169/2);
		final int height = frame.height();
		final int width = frame.width();
		
		byte[][] result = new byte[w][h];

		final int xdctr = w/2;
		int horizoffset = 0; 
		
		for (int y = height/2-horizoffset; y<=height/2+horizoffset; y++) {
			for (int x=0; x<width; x++) {
	
				double d = frame.get(y, x)[0];
				d = multiplier/(d/16);
				//				System.out.print(d+", ");
				int ry = (int) Math.round(d/ maxDepthTopView  * h);
				double xdratio = (x*(double) w/width - xdctr)/ (double) xdctr;
				int rx = (w/2) + (int) Math.round(Math.tan(angle)*(double) ry * xdratio);
				
				if (ry<h && ry>0 && rx>=0 && rx<w)   result[rx][h-ry-1] = (byte) 0xff; 
			}
		}
		
		return result;
	}
}
