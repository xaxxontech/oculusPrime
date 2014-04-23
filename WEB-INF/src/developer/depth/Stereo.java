package developer.depth;

import java.awt.image.BufferedImage;

import oculusPrime.Util;

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

import developer.image.OpenCVUtils;

public class Stereo {
	private static VideoCapture captureLeft;
	private static VideoCapture captureRight;
	private final int camLeft = 1;
	private final int camRight = 0;
//	private static Mat left = new Mat();
//	private static Mat right = new Mat();
//	private Mat disparity = null;
	private developer.image.OpenCVUtils cv;
	public boolean stereoCamerasOn = false;
	public StereoSGBM sbm;
	public static final int yoffset = 22; // 22 for 360, 25 for 480, 27 for 448
	public static final int xoffset = 2; // 5 for 640x480
	public static final double leftRotation = -0.7; 
	private BufferedImage disp = null;
	public static final int xres = 640;
	public static final int yres = 360;

	public Mat rotImage;
	public boolean generating = false;	 
	final static double camFOVx169 = 68.46;
	static final double camFOVy169 = 41.71;
	static final double camFOVx43 = 58.90;
	static final double camFOVy43 = 45.90;
	final static int maxDepthTopView = 3500;
//	final double scaleMult = 275*2032; // disparity*mm (based on xoffset=2)!
	final static double scaleMult = 440*1350; // 412 measured - disparity*mm (based on xoffset=2)! (1346mm = from docked to corner of TV stand)
											  // 435 = trial and error, best map meshing

	// camera offsets negligible on linear moves
	public static final int  cameraSetBack = 0; // -20; // is forward from rotation center TODO: use this?
	public static final int cameraOffsetLeft = 25; // to bot's left from rotation center
	
	static final int depthPixelIntensitySpread = 230;
	static final int depthPixelIntensityOffset = 25;

	
	public Stereo() {
		System.loadLibrary( Core.NATIVE_LIBRARY_NAME );
		cv = new OpenCVUtils();

		sbm = new StereoSGBM();
		
		/* mode 1: cleanest depth image, though top view innacuracies
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
//        */
		
//		/* mode 2: more accurate, way less info in depth image
        sbm.set_SADWindowSize(3); // 3-11. higher = more blobular 
        sbm.set_numberOfDisparities(48);  // 32-256 similar top view results, lower shortens time
        sbm.set_preFilterCap(63);  // lower seems to lessen noise
        sbm.set_minDisparity(4); // no change between 4,0, higher = innacurate
        sbm.set_uniquenessRatio(40); // % - higher = less noise, still not that accurate
        sbm.set_speckleWindowSize(50);  // 0- disabled. 50-200 normal 
        sbm.set_speckleRange(32);  // 1-200, no diff. 0 = blank
        sbm.set_disp12MaxDiff(1); // -1-200 no diff
        sbm.set_fullDP(false); // true = longer time, very slight noise reduction
        sbm.set_P1(1); // 1 lower = messier looking but less assumptions
        sbm.set_P2(250); // 250 lower = messier looking but less assumptions
//        */
		
		Point center = new Point((xres-xoffset)/2, (yres-yoffset)/2);
		rotImage = Imgproc.getRotationMatrix2D(center, leftRotation, 1.0);
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
//				captureRight.retrieve(right);
			}
					
		} catch (Exception e) { e.printStackTrace(); } } }).start();	
		
		new Thread(new Runnable() { public void run() { try {

			while(true && stereoCamerasOn) {
	    		captureLeft.grab();
//	    		captureLeft.retrieve(left);
			}
			
		} catch (Exception e) { e.printStackTrace(); } } }).start();	

	}
	
	public void stopCameras() {
		if (!stereoCamerasOn) return;
		
		new Thread(new Runnable() { public void run() { try {
			
			while (generating) {} // wait
			stereoCamerasOn = false;
			Thread.sleep(500); // allow grabs to finish
			captureRight.release();
			captureLeft.release();
			captureRight = null;
			captureLeft = null;
    	
		} catch (Exception e) { e.printStackTrace(); } } }).start();	

	}
	
	public Mat generateDisparity(Mat L, Mat R) {
		Rect rect;
		
		rect = new Rect(xoffset,yoffset,L.width()-xoffset,L.height()-yoffset);
		L = new Mat(L,rect);
		Imgproc.cvtColor(L, L, Imgproc.COLOR_BGR2GRAY);
		Imgproc.warpAffine(L, L, new Stereo().rotImage, L.size());
		Imgproc.equalizeHist(L, L);
//		Photo.fastNlMeansDenoising(R, R);
		
		rect = new Rect(0,0,R.width()-xoffset,R.height()-yoffset);
		R = new Mat(R,rect);
		Imgproc.cvtColor(R, R, Imgproc.COLOR_BGR2GRAY);
		Imgproc.equalizeHist(R, R);
//		Photo.fastNlMeansDenoising(R, R);
		
        Mat disparity = new Mat();

        long start = System.currentTimeMillis();

		sbm.compute(L, R, disparity);

		long duration = System.currentTimeMillis() - start;
//    	System.out.println("time: "+ String.valueOf(duration));

        
        return disparity;
	}
	
	public BufferedImage getDepthImage() {
		if (!stereoCamerasOn || generating) return null;
		
		generating = true;
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
		generating = false;	
		return disp;
	}
	
	public BufferedImage getTopView() {
		if (!stereoCamerasOn || generating) return null;
		generating = true;
		
		Mat right = new Mat();
		Mat left = new Mat();
		captureRight.retrieve(right);
		captureLeft.retrieve(left);

    	Mat disparity = generateDisparity(left,right);
    	short[][] topView = projectStereoHorizToTopView(disparity, 320);
    	
		Imgproc.resize(left, left, new Size(120, 68));
		Mat mtv = Stereo.convertShortToMat(topView);
		left.copyTo(new Mat(mtv, new Rect(0,mtv.height()-68-1, 120,68)));
    	
		BufferedImage img = cv.matToBufferedImage(mtv);
		generating = false;
		return img;
	}
	
	public BufferedImage leftCameraFeed() {
//        while (generating) {} // wait
		Mat left = new Mat();
		captureLeft.retrieve(left);
		Imgproc.resize(left, left, new Size(xres/2, yres/2));
		return cv.matToBufferedImage(left);
	}
	
	public static Mat convertShortToMat(short[][] frame) {
		int w = frame.length;
		int h = frame[0].length;
		Mat m = new Mat(h, w, CvType.CV_8UC3);
		for (int x = 0; x<w; x++) {
			for (int y=0; y<h; y++) {
				m.put(y, x, new byte[] {0,(byte) frame[x][y],0});
			}
		}
		return m;
	}
	
//	final static int YrgbDiff = 50;
	public static short[][] projectStereoHorizToTopView(Mat frame, int h) { 
//		final int w = (int) (Math.cos(Math.toRadians(camFOVx169)/2) * h) * 2; // WRONG .. ?
//		final int w = (int) (Math.tan(Math.toRadians(camFOVx169/2)) * h * 2); // WRONG .. ?
		final int w = (int) (Math.sin(Math.toRadians(camFOVx169/2)) * h * 2); // WRONG .. ?
		final double angle = Math.toRadians(camFOVx169/2);
		final int width = frame.width();
		final int mid = frame.height()/2-10; // offset so not including floor plane points
//		System.out.println("w: "+w+", h: "+h);
		short[][] result = new short[w][h];

		final int xdctr = w/2;
		
		boolean filtering = true;

		if (!filtering) {
		
			/* unfiltered */
			final int horizoffset = 0; 
			for (int x=0; x<width; x++) {
				for (int y = mid-horizoffset; y<=mid+horizoffset; y++) {
					double d = frame.get(y, x)[0];
					d = scaleMult/d;
					int ry = (int) Math.round(d/ maxDepthTopView  * h);
					double xdratio = (x*(double) w/width - xdctr)/ (double) xdctr;
					int rx = (w/2) + (int) Math.round(Math.tan(angle)*(double) ry * xdratio);
					if (ry<h && ry>0 && rx>=0 && rx<w)   result[rx][h-ry-1] = 0xff; 
				}
			}
			
		}
		
		else {
		
			/* vertical noise filtering + averaging */

//			final int horizoffset = 7; // mode 1 (pixels= *2+1)
			final int horizoffset = 3; // mode 2 (pixels= *2+1)
			final int horizoffsetinc = 2;

			final double YdepthRatioThreshold = 0.05; // percent
			final double XdepthRatioThreshold = 0.01; // percent

//			final int levels = 1; // mode 1 (pixels= *2+1 * horizoffset*2+1)
			final int levels = 3; // mode 1 (pixels= *2+1 * horizoffset*2+1)

			for (int lvl=-levels; lvl<=levels; lvl++) {
				double[] dx = new double[width];
				for (int x=0; x<width; x++) {
		
					int i = 0;
					double[] d = new double[horizoffset*(2/horizoffsetinc)+1]; 
					boolean add = true;
					
					for (int y = (mid+lvl*(horizoffset*2+1))-horizoffset; 
							y<=(mid+lvl*(horizoffset*2+1))+horizoffset; y+=horizoffsetinc) {
						d[i] = scaleMult/frame.get(y, x)[0];
						if (d[i] >= maxDepthTopView ) add = false; // discard far away points
						i++;
					}
					
		//			double dx = 0;
					dx[x] = maxDepthTopView;
					double maxDiff = 0;
		
					if (add) {
						for (i=1; i<d.length; i++) {
							if (Math.abs(d[0]-d[i]) > maxDiff) maxDiff = Math.abs(d[0]-d[i]);
		//					dx += d[i];
							if (d[i] < dx[x]) dx[x] = d[i]; // closest
						}
		//				dx = dx/d.length; // average
					}
		
					if (!add || maxDiff/dx[x] > YdepthRatioThreshold) dx[x] = maxDepthTopView;
					

				}
				
				// x filtering & adding
				for (int x=1; x<width-1; x++) {
					if (dx[x]<maxDepthTopView) {
						if (Math.abs(dx[x]-dx[x-1])/dx[x]<XdepthRatioThreshold && Math.abs(dx[x]-dx[x+1])/dx[x]<XdepthRatioThreshold ) {
//						if (true) {
							
							/* original code:
							int ry = (int) Math.round(dx[x]/ maxDepthTopView  * h);
							double xdratio = (x*(double) w/width - xdctr)/ (double) xdctr;
							int rx = (w/2) + (int) Math.round(Math.tan(angle)*(double) ry * xdratio);
//							*/
							
//							/* new projection code:
							// y = sqrt(dscaled^2 - x^2)
							// x = xdctr - x*w/width
							// dscaled = dx[x]*h/maxDepthTopView
//							int ry = (int) Math.sqrt(Math.pow(dx[x]*h/(double) maxDepthTopView, 2) 
//														- Math.pow(xdctr - x*w/(double) width, 2));
							
							double dscaled = dx[x]*h/(double) maxDepthTopView;
							double a = Math.toRadians( camFOVx169 * (width/2-x) / width  ) ;
							int rx = w/2 - (int) (dscaled * Math.sin(a));
							int ry = (int) (dscaled * Math.cos(a));
							
//							*/
							
//							if (ry<h && ry>0 && rx>=0 && rx<w)   result[rx][h-ry-1] = (short) (150+(YrgbDiff*lvl));
							if (ry<h && ry>0 && rx>=0 && rx<w)   result[rx][h-ry-1] = (short) ( (maxDepthTopView-dx[x])/maxDepthTopView*
									depthPixelIntensitySpread + depthPixelIntensityOffset); // TODO: try disabling this check!
						}
					}
				}
				
	
			}

		}
		
		// eliminate orphan pixels
		for (int x=1; x<w-1; x++) {
			for (int y=1; y<h-1; y++) {
				if (result[x][y] !=0) { 
					if (result[x-1][y-1]==0 && result[x][y-1]==0 && result[x+1][y-1]==0 &&
							result[x-1][y]==0 && result[x+1][y]==0 &&
							result[x-1][y+1]==0 && result[x][y+1]==0 && result[x+1][y+1]==0) {
						result[x][y] = 0;
					}
				}
			}
		}
		
		return result;
	}
	
	public short[][] captureTopViewShort(int h) {
		if (!stereoCamerasOn) return null;
        generating = true;
		Mat right = new Mat();
		Mat left = new Mat();
		captureRight.retrieve(right);
		captureLeft.retrieve(left);

    	Mat disparity = generateDisparity(left,right);
        short[][] result = projectStereoHorizToTopView(disparity, h);
        generating = false;
    	return result;
	}
	
	
	/*
	 * this is basically useless
	 */
	public static int[] findDistanceTopView(short[][] cellsBefore, short[][] cellsAfter, 
				double angle, final int guessedDistance) { 
		final int h = cellsBefore[0].length;
//		final int w = (int) (Math.sin(Math.toRadians(camFOVx169/2)) * h) * 2; // narrower for better resuts?
		final int w = (int) (Math.tan(Math.toRadians(camFOVx169/2)) * h * 2);
		final double scaledCameraSetback = (double) cameraSetBack* h/maxDepthTopView; // pixels, negligible
		final int scaledGuessedDistance = guessedDistance * h/maxDepthTopView; // pixels
		angle = -Math.toRadians(angle);

		double winningTtl = 0; 
		int winningDistance = 99999;
		int[] result=new int[2];
	
		 
		for (int d=scaledGuessedDistance-scaledGuessedDistance/2; d<scaledGuessedDistance+scaledGuessedDistance/2; d++) {
//		for (int d=0; d<scaledGuessedDistance*2; d++) {

			double total = 0;

			for (int x=0; x<w; x++ ) {
				for (int y=0; y<h; y++) {
					
					if (cellsBefore[x][y] != 0) {

						double anglexy = Math.atan((w/2-x)/(double)(h-1-y - scaledCameraSetback));
						double hyp = (h-1-y- scaledCameraSetback)/Math.cos(anglexy); // cos a = y/h

						int xx = -(int) Math.round(hyp * Math.sin(anglexy+angle)-w/2);  // sin angleXY+angle = (w/2-xx)/hyp
						int yy = -(int) Math.round(hyp * Math.cos(anglexy+angle) -h+1+scaledCameraSetback) +d; // cos angleXY+angle = (h-1-yy)/hyp
						
						if (xx>=0 && xx<w && yy>=0 && yy<h ) { 
//							if (cellsAfter[xx][yy] > 0 && cellsAfter[xx][yy] < maxDepthTopView)   total ++;
//							if (cellsAfter[xx][yy] == cellsBefore[x][y])   total ++;
//							if (Math.abs(cellsAfter[xx][yy] - cellsBefore[x][y]) <= 20 ) total ++;
							if (cellsAfter[xx][yy] !=0 ) {
	//							total ++;	
								int distanceIntensityDiff = d/h*depthPixelIntensitySpread+depthPixelIntensityOffset;
								int diff = Math.abs(cellsAfter[xx][yy]- cellsBefore[x][y]);
								// diff - distanceIntensityDiff should be close to zero
								int proximity = Math.abs(diff - distanceIntensityDiff);
//								total += Math.abs(diff - distanceIntensityDiff)/(double) diff;
								if (proximity<20) total += 20-proximity;
							}
						}
						
					}
					
				}
			}
	
			if (total > winningTtl) {
				winningTtl = total;
				winningDistance = d;
			}
		
		}
		
		System.out.println("winningTtl: "+winningTtl);
		winningDistance = winningDistance * maxDepthTopView/h;
		return new int[]{winningDistance, (int) winningTtl};
	}

}
