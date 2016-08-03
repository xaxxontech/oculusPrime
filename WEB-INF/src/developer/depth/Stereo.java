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
	public StereoSGBM sbmTopView;
	public StereoSGBM sbmImage;
	public static final int yoffset = 22; // 22 for 360, 25 for 480, 27 for 448
	public static final int xoffset = 2; // 5 for 640x480
	public static final double leftRotation = -0.7; 
	private BufferedImage disp = null;
	public static final int xres = 640;
	public static final int yres = 360;

	public Mat rotImage;
	public boolean generating = false;

	// lifecam cinema measured angles (may not reflect average)
	final static double camFOVx169 = 68.46;
	static final double camFOVy169 = 41.71;
	public static final double camFOVx43 = 58.90;
	static final double camFOVy43 = 45.90;

	final static int maxDepthTopView = 3500; // 3500;
	final static int minDepthTopView = 750;
//	final double scaleMult = 275*2032; // disparity*mm (based on xoffset=2)!
//	final static double scaleMult = 430*1350; // mode 2 -- 412 measured - disparity*mm (based on xoffset=2)! (1346mm = from docked to corner of TV stand)
											  // 435 = trial and error, best map meshing
	final static double scaleMult = 415*1350; // mode 1  xoffset=2

	// camera offsets negligible on linear moves
	public static final int  cameraSetBack = 0; // -20; // is forward from rotation center TODO: use this?
	public static final int cameraOffsetLeft = 25; // to bot's left from rotation center
	
	static final int objectMax = 255;
	static final int objectMin = 0;
	static final int nonObjectMax = 511;
	static final int nonObjectMin = 256; 
	static final int fovMin = 512;
	static final int fovMax = 767;
	public static short[][] tvr;

	
	public void DeleteThis() {
//	public void Stereo() {
		System.loadLibrary( Core.NATIVE_LIBRARY_NAME );
//		cv = new OpenCVUtils();

		sbmImage = new StereoSGBM();
//		/* mode 1: cleanest depth image, though top view innacuracies
		sbmImage.set_SADWindowSize(3); 
		sbmImage.set_numberOfDisparities(48);  
		sbmImage.set_preFilterCap(63); 
		sbmImage.set_minDisparity(4); 
		sbmImage.set_uniquenessRatio(10);  // 10
		sbmImage.set_speckleWindowSize(50); 
		sbmImage.set_speckleRange(32);
		sbmImage.set_disp12MaxDiff(1);
		sbmImage.set_fullDP(false);
		sbmImage.set_P1(216);	// 216
		sbmImage.set_P2(864);   // 864
//        */
		
		sbmTopView = new StereoSGBM();
//		/* mode 2: more accurate, way less info in depth image
		sbmTopView.set_SADWindowSize(9); // 3-11. higher = more blobular 
		sbmTopView.set_numberOfDisparities(48);  // 32-256 similar top view results, lower shortens time
		sbmTopView.set_preFilterCap(63);  // lower seems to lessen noise
		sbmTopView.set_minDisparity(4); // no change between 4,0, higher = innacurate
		sbmTopView.set_uniquenessRatio(80); // % - higher = less noise, still not that accurate
		sbmTopView.set_speckleWindowSize(50);  // 0- disabled. 50-200 normal 
		sbmTopView.set_speckleRange(32);  // 1-200, no diff. 0 = blank
		sbmTopView.set_disp12MaxDiff(1); // -1-200 no diff
		sbmTopView.set_fullDP(false); // true = longer time, very slight noise reduction
		sbmTopView.set_P1(1); // 1 lower = messier looking but less assumptions
		sbmTopView.set_P2(250); // 250 lower = messier looking but less assumptions
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
	
	public Mat generateDisparity(Mat L, Mat R, StereoSGBM sgbm) {
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

        sgbm.compute(L, R, disparity);

		long duration = System.currentTimeMillis() - start;
//    	System.out.println("time: "+ String.valueOf(duration));
        
        return disparity;
	}
	
	public BufferedImage getDepthImage(String mode) {
		if (!stereoCamerasOn || generating) return null;
		
		generating = true;
//		long start = System.currentTimeMillis();

		Mat right = new Mat();
		Mat left = new Mat();
		captureRight.retrieve(right);
		captureLeft.retrieve(left);

		Mat disparity;
		if (mode.equals("mode2")) disparity = generateDisparity(left,right, sbmTopView);
		else disparity = generateDisparity(left,right, sbmImage);

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

    	Mat disparity = generateDisparity(left,right,sbmTopView);
    	short[][] topView = projectStereoHorizToTopViewFiltered(disparity, 320);
    	topView = topViewProbabilityRendering(topView);
    	
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
//				m.put(y, x, new byte[] {(byte) 0,0,0});
				if (frame[x][y] > objectMin && frame[x][y] <= objectMax) m.put(y, x, new byte[] {0,(byte) frame[x][y],0}); // green
				else if (frame[x][y] >= nonObjectMin && frame[x][y] <= nonObjectMin) m.put(y, x, new byte[] {(byte) frame[x][y], 0, 0} ); // blue
				else if (frame[x][y] >= fovMin && frame[x][y] <= fovMax) m.put(y, x, new byte[] {(byte) frame[x][y],(byte) frame[x][y], (byte) frame[x][y]} ); // white
				else  m.put(y, x, new byte[] {(byte) frame[x][y], 0, 0});
			}
		}
		return m;
	}
	
	public static short[][] projectStereoHorizToTopViewFiltered(Mat frame, int h) { 
		final int w = (int) (Math.sin(Math.toRadians(camFOVx169/2)) * h * 2); // WRONG .. ?
		final int width = frame.width();
		final int mid = frame.height()/2-10; // offset so not including floor plane points
		short[][] result = new short[w][h];

//		final int horizoffset = 4; // mode 1 (pixels= *2+1)
		final int horizoffset = 3; // mode 2 (pixels= *2+1)
		final int horizoffsetinc = 2;

//		final double YdepthRatioThreshold = 0.02; // mode 1 percent
		final double YdepthRatioThreshold = 0.05; // mode 2 percent

		final double XdepthRatioThreshold = 0.01; // percent

//		final int levels = 2; // mode 1 (pixels= *2+1 * horizoffset*2+1)
		final int levels = 2; // mode 2 (pixels= *2+1 * horizoffset*2+1)

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
				
				dx[x] = maxDepthTopView;
				double maxDiff = 0;
	
				if (add) {
					for (i=1; i<d.length; i++) {
						if (Math.abs(d[0]-d[i]) > maxDiff) maxDiff = Math.abs(d[0]-d[i]);
						if (d[i] < dx[x]) dx[x] = d[i]; // closest
					}
				}
	
				if (!add || maxDiff/dx[x] > YdepthRatioThreshold) dx[x] = maxDepthTopView; // discard
				

			}
			
			// loop again, with x filtering & adding: 
			for (int x=1; x<width-1; x++) {
				if (dx[x]<maxDepthTopView && dx[x]>minDepthTopView) {
					// x-filter test:
					if (Math.abs(dx[x]-dx[x-1])/dx[x]<XdepthRatioThreshold && Math.abs(dx[x]-dx[x+1])/dx[x]<XdepthRatioThreshold ) {
						//project:
						double dscaled = dx[x]*h/(double) maxDepthTopView; // distance from bot, in pixels
						double a = Math.toRadians( camFOVx169 * (width/2-x) / width  ) ; // angle from center, radians
						int rx = w/2 - (int) (dscaled * Math.sin(a));
						int ry = (int) (dscaled * Math.cos(a));

						// add to results:
//							if (ry<h && ry>0 && rx>=0 && rx<w)   result[rx][h-ry-1] = (short) (150+(YrgbDiff*lvl));
//							if (ry<h && ry>0 && rx>=0 && rx<w)   result[rx][h-ry-1] = (short) ( (maxDepthTopView-dx[x])/maxDepthTopView*
//									depthPixelIntensitySpread + depthPixelIntensityOffset); // TODO: try disabling this check!
						result[rx][h-ry-1] = 255; 
						
					}
				}
			}
			

		}
		
//		 loop again, eliminate orphan pixels:
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
	
	public static short[][] projectStereoHorizToTopViewFilteredLess(Mat frame, int h) { 
		final int w = (int) (Math.sin(Math.toRadians(camFOVx169/2)) * h * 2); // WRONG .. ?
		final int width = frame.width();
		final int mid = frame.height()/2-10; // offset so not including floor plane points
		short[][] result = new short[w][h];

		for (int y = mid-20; y<=mid+20; y+=5) {
//		for (int y = mid-10; y<=mid+10; y+=3) {
			
			for (int x=1; x<width-1; x++) {
				double d = scaleMult/frame.get(y, x)[0];
				if (d<maxDepthTopView && d>minDepthTopView) {
					
						//project:
						double dscaled = d*h/(double) maxDepthTopView; // distance from bot, in pixels
						double a = Math.toRadians( camFOVx169 * (width/2-x) / width  ) ; // angle from center, radians
						int rx = w/2 - (int) (dscaled * Math.sin(a));
						int ry = (int) (dscaled * Math.cos(a));
						result[rx][h-ry-1] = 254; 
					
				}
			}
		}
		
//		loop again, eliminate orphan pixels:
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
	
	public static short[][] topViewProbabilityRendering(short[][] tv) {
		int w  = tv.length;
		int h = tv[0].length;
		tvr = new short[w][h];
		final double xaconst = 4/320.0*h;
		final double yaconst  = 2/320.0*h;
		for (int y=0; y<h; y++) {
			for (int x=0; x<w; x++) {
				double d = Math.sqrt(Math.pow(w/2-x, 2)+Math.pow(h-y,2));
				double a = Math.asin((w/2-x)/d);
//				short i = (short) (depthPixelIntensityOffset + depthPixelIntensitySpread-depthPixelIntensitySpread*d/h);
				short i = (short) (objectMin+objectMax-(objectMax-objectMin)*d/h);

				// render baseline if pixel empty
				if (Math.abs(a) <= Math.toRadians(camFOVx169/2) && d < h && tvr[x][y] == 0) // && d > (double)h/maxDepthTopView*minDepthTopView)  
						tvr[x][y]=(short) (fovMin + 10+(i/255.0*80));

				if (tv[x][y] != 0) { // object!
					double mult = (5-Math.abs(a))/5; 
					int xa = (int) Math.round(d/h*xaconst*mult);
					int ya = (int) Math.round(Math.pow(d/h*yaconst/mult, 3.5)+0.5); // added 0.5 so always 1 or higher
					double incr = (x-w/2) / (double) (h-y);
					
//					ellipse(x,y,i,xa,ya, incr, w, h);					
					tvr[x][y]=254; // TODO: testing (note: 255 comes up blank, 254 is bright green)
				}

			}
		}	
		return tvr;
	}
	
	private static void ellipse(int ctrx, int ctry, short i, int xa, int ya, double incr, int w, int h) {
		lineaway((int) Math.round(ctrx-incr*ya), ctry+ya, ya*2, w, h, incr, i);
//		short bg = (short) (-(depthPixelIntensityOffset+ depthPixelIntensitySpread)+i);
		short bg = (short) (nonObjectMin+i);
//		line(ctrx, ctry, h, w, h, incr, bg);

		for (double x=1-Math.abs(incr); x<=xa; x+= 1-Math.abs(incr)) {
			int yl = (int) Math.round(ya - (ya*Math.pow(x/(double) xa, 3)));

			lineaway((int) Math.round(ctrx+x), ctry, h, w, h, incr, (short) 0);
			lineaway((int) Math.round(ctrx-x), ctry, h, w, h, incr, (short) 0);

			linetoward((int) Math.round(ctrx+x), ctry, h-ctry, w, h, incr, bg);
			linetoward((int) Math.round(ctrx-x), ctry, h-ctry, w, h, incr, bg);

			lineaway((int) Math.round(ctrx+x-incr*yl), (int) Math.round(ctry+x*incr)+yl, yl*2, w, h, incr, i);
			lineaway((int) Math.round(ctrx-x-incr*yl), (int) Math.round(ctry+x*incr)+yl, yl*2, w, h, incr, i);
		}
	}
	
	private static void lineaway(int startx, int starty, int length, int w, int h, double incr, short intensity) {
		double x = startx;
		int y = starty;
		int endy = y-length;
		while (true) {
			x += incr;
			y --;
			if ( x < 0 || x >= w || y<endy || y<0)  break;
			int e =  tvr[(int) x][y];
			double i=intensity;

			if (e>objectMin && e<=objectMax && i!=0) i += e/5;
			if (!(i==0 && (e<fovMin || e>fovMax)))
					tvr[(int) x][y] = (short) i;
		}
	}
	
	private static void linetoward(int startx, int starty, int length, int w, int h, double incr, short intensity) {
		double x = startx;
		int y = starty;
		int endy = y+length;
		while (true) {
			x -= incr;
			y ++;
			if (y>endy || y>=h)  break;
			int e =  tvr[(int) x][y]; // existing pixel
			
			if (e==0 || e>=nonObjectMin) {
				tvr[(int) x][y] = (short) intensity;
			}
			
		}
	}
	
	public short[][] captureTopViewShort(int h) {
		if (!stereoCamerasOn) return null;
        generating = true;
		Mat right = new Mat();
		Mat left = new Mat();
		captureRight.retrieve(right);
		captureLeft.retrieve(left);

    	Mat disparity = generateDisparity(left,right,sbmTopView); // accurate
//    	Mat disparity = generateDisparity(left,right,sbmImage);  // looks nicer
//        short[][] result = projectStereoHorizToTopViewFiltered(disparity, h);
        short[][] result = projectStereoHorizToTopViewFilteredLess(disparity, h);
        result = topViewProbabilityRendering(result);
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
								int distanceIntensityDiff = 99999999 ; // d/h*depthPixelIntensitySpread+depthPixelIntensityOffset;
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
