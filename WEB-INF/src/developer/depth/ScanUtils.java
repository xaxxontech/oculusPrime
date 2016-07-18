package developer.depth;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;

import oculusPrime.Application;


public class ScanUtils {
	
	final static int width=320;
	final static int height=240;
	final static int camFOVx = 58; // degrees
	private final static int camFOVy = 45; // degrees
	public final static int maxDepthInMM = 5000; // 3500
	private final static int depthCamVertDistfromFloor = 290;
	static double yAngleCompStart = 0;
	public final static int maxDepthFPTV = 3500;
	public static final int  cameraSetBack = 51; // from rotation center, xoffset is negligible
	
	/**
	 * Load framedata from file. 
	 * @param f file to be loaded
	 * @return short integer array containing each pixel depth
	 */
	public static short[] getFrame(File f) {
		
		ByteBuffer frameData = null;
		short[] result = new short[width*height];

		try {

			FileInputStream file = new FileInputStream(f);
			FileChannel ch = file.getChannel();
			frameData = ByteBuffer.allocate((int) width*height*2);
			ch.read(frameData.order(ByteOrder.LITTLE_ENDIAN));
			ch.close();
			file.close();

		} catch (IOException e) {
			e.printStackTrace();
		}
		
    	int i = 0;
    	for (int y=0; y<height; y++) {
			for (int x=0; x<width; x++) {
		        
		        int p = ((width * y)+x)*2;
		        short depth = frameData.getShort(p);
//		        if (depth !=0)  depth -= ScanUtils.cameraSetBack; // depth is to center of rotation
		        result[i] = depth;
		        i++;
			}	
    	}
    	
		return result; 

	}
	
	/**
	 * Convert full scan into lower resolution 2-dimensional array of closest pixels
	 * @param framePixels short[] array of 16-bit depth data
	 * @return int[][] xy array of closest pixels
	 */
	// as yet unused, average pixel seems better accuracy
	public int[][] resampleClosestPixel(short[] framePixels, int res) {
		
		int[][] result = new int[width/res][height/res];
		
		for (int x = 0; x < width; x += res) {			
			for (int y=0; y<height; y+= res) {

				int closestPixel = maxDepthInMM;
				for (int xx=0; xx<res; xx++) {
					for (int yy=0; yy<res; yy++) {
						int p = framePixels[x + xx + (y+yy)*width];
						if (p !=0 && p < closestPixel) {
							closestPixel = p;
						} 
					}
				}
				if (closestPixel < maxDepthInMM)    result[x/res][y/res] = closestPixel;
				
			}
		}
		
		return result;
	}
	
	
	/**
	 * Convert full scan into lower resolution 2-dimensional array of pixels, averaged.
	 * Declare whole cell 0 if certain percentage is 0
	 * @param framePixels short[] array of 16-bit depth data
	 * @return int[][] xy array of averaged pixels
	 */
	public static int[][] resampleAveragePixel(short[] framePixels, int resX, int resY) {
		
		int[][] result = new int[width/resX][height/resY]; //TODO: may need to add or subtract 1?
		int xx;
		int yy;
		final int zerosmax = (int) (resX*resY*0.25);
		
		for (int x = 0; x < width; x += resX) {			
			for (int y=0; y<height; y+= resY) {

//				int count = 0;
				int zeros = 0;
				int runningTotal = 0;
				for (xx=0; xx<resX; xx++) {
					for (yy=0; yy<resY; yy++) {
						int p = framePixels[x + xx + (y+yy)*width];
						if (p==0)  { zeros ++; }
						runningTotal += p; 
					}
				}
//				if (count !=0)    result[x/res][y/res] = runningTotal / count;
				if (zeros < zerosmax)    result[x/resX][y/resY] = runningTotal / (resX*resY);
				
			}
		}
		
		return result;
	}
	
	
	public static int[][] findFloorPlane(int[][] frameCells) {

		int cWidth = frameCells.length;
		int cHeight = frameCells[0].length;
//		final int yres = (int) Math.round((double)height/cHeight);
		
		final double yAngleTolerance = 1;
		final double maxYangleComp = 3;
		double yAngleComp = yAngleCompStart;
		int winningTotal = 0;
		double yAngleCompIncrement = 0.2;
		double winningYangleComp = yAngleComp;
		int fail = 0;
		ArrayList<int[][]> results = new ArrayList<int[][]>(); 
		int c = 0;
		int index = 0;
		int winningIndex = -1;
		int[][] result;
		
		while ( true ) {
			int total = 0;
			result = new int[cWidth][cHeight];

			for (int x=0; x<cWidth; x++) {
				for (int y=0; y<cHeight; y++) {

					// floor plane angles
					int fpMin = 0;
					int fpMax = 0;
					
					final int yStart = (int) Math.round(cHeight * 0.675 - yAngleComp * (double) cHeight/camFOVy);

					if (y > yStart) { // lower portion FOV only    (y > (int) (height * 0.6)
						double yAngle = (y - (cHeight/2.0)) * camFOVy/cHeight; // horiz=0 degrees, below = positive
						fpMin = (int) ( depthCamVertDistfromFloor / Math.sin((yAngle + yAngleComp + yAngleTolerance)*Math.PI/180) );
						fpMax = (int) ( depthCamVertDistfromFloor / Math.sin((yAngle + yAngleComp - yAngleTolerance)*Math.PI/180) );
					}
					
					int d= frameCells[x][y];
					if (d>fpMin && d<fpMax)  {
						d = 0x10000 + d; // set 17th bit = 1 to indicate floor plane
						total ++;

					}
					result[x][y] = d;
					
				}
			}
			
			if (total > 0 && total > winningTotal) {
				winningTotal = total;
				winningYangleComp = yAngleComp;
				winningIndex = index;
				results.add(result); // only save good ones
				index++;
				fail = 0;
			}
			else  {
				if (winningTotal !=0)  fail++;
			}
			
			if (fail > 3) { 
//				System.out.println("fail: "+yAngleComp);
				break; // getting progressively worse, don't bother checking the rest
			}
			
			yAngleComp = yAngleCompStart + yAngleCompIncrement * c;

			if (yAngleCompIncrement > 0) {
				if (yAngleComp > maxYangleComp) {
					yAngleComp = yAngleCompStart - yAngleCompIncrement * c;
					if (yAngleComp < -maxYangleComp)  break;
				}
			}
			else {
				c++;
				if (yAngleComp < -maxYangleComp)  {
					yAngleComp = yAngleCompStart + yAngleCompIncrement * c;
					if (yAngleComp > maxYangleComp)  break;
				}
			}
			
			yAngleCompIncrement *= -1;
		
		}
		
		if (winningIndex != -1) {
			yAngleCompStart = winningYangleComp;
			return results.get(winningIndex);
		}
		else   return result;
	}
	
	/**
	 * Re-position xy of each cell from 2-dimensional array based on distance moved-guess. Only over-write
	 * cells if closer 
	 * @param frameCells 2-dimensional array, previously generated by resampleClosestPixel()
	 * @param distance in mm of travel (guess)
	 * @return int[][] xy array of closest pixels 
	 */
	public static int[][] scaleResampledPixels(final int[][] frameCells, final int dMoved) {
		//  N=n/(D-d)*D -- derived from: 
		//		https://docs.google.com/drawings/d/1zmwqU5HqGTvd9sBjpN0iLXc7LmAA54QiBJ_AL23HGL4/edit
		int cwidth =  frameCells.length;
		int cheight = frameCells[0].length;
		int[][] result = new int[cwidth][cheight]; 

		for (int x=0; x<cwidth; x++) {
			for (int y=0; y<cheight; y++) {

				int D = frameCells[x][y];
				float fX = (float) (x-cwidth/2) / (D-dMoved) * D + cwidth/2;
				float fY = (float) (y-cheight/2) / (D-dMoved) * D + cheight/2;
				int newX = Math.round(fX);
				int newY = Math.round(fY);
				
				if (newX >= 0 && newX < cwidth && newY >=0 && newY < cheight && !(x== cwidth/2 && y== cheight/2)) { // within range
					 // if overlapping and closer or unsassigned:
					if (D < result[newX][newY] || result[newX][newY]==0 && D>dMoved)  result[newX][newY] = D -dMoved;
//					if (result[newX][newY]==0)  result[newX][newY] = D;
				}
		
			}
		}
		
//		System.out.println(result[cwidth/2][cheight/2]);
		return result;
	}
	
	/**
	 * Convert array of 16-bit pixels to depth image
	 * @param depth 16-bit pixel array
	 * @return width x height argb BufferedImage object
	 */
	public BufferedImage generateDepthFrameImg(short[] depth ) {

		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		
		for(int y=0; y<height; y++) {
			for(int x=0; x<width; x++) {
				
				/* single hue */
				int hue = depth[x + y*width];
				if (hue > maxDepthInMM)	hue = maxDepthInMM;
				if (hue != 0) {
					hue = 255 - (int) ((float) (hue)/maxDepthInMM * 255f);
				}
				int argb = (hue<<16) + (0<<8) + hue;

				
//				/* dual hue */
//				short d = depth[x + y*width];
//				if (d > maxDepthInMM)	d = 0; // d = maxDepthInMM;
//				int red = d  >> 8;
//				red *=8;
//				int blue=0;
//				if (d != 0) blue = 255 - (int) ((float) (d)/maxDepthInMM * 255f);
//				int argb = (red<<16) + (0<<8) + blue;

				
				img.setRGB(width-x-1, y, argb);    // flip horiz
			}
		}
		
		return img;
	}
	
	public static BufferedImage generateDepthFrameImgWithFloorPlane(int[] depth ) {

		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		
		for(int y=0; y<height; y++) {
			for(int x=0; x<width; x++) {
				
				int argb;
//				int red=0;
//				int blue=0;
				int hue = depth[x + y*width];
				
				if (hue > 0xffff) {
					hue = hue & 0xffff;
					
//					if (hue > maxDepthInMM)	hue = maxDepthInMM;
					if (hue != 0) {
						hue = 255 - (int) ((float) (hue)/maxDepthInMM * 255f);
					}
					argb = hue<<8;

				}
//				if (d == fpIndicatorDepth) {
//					argb = 255<<8;
//				}
				else  {

					/* single hue */
//					int hue = depth[x + y*width];
					if (hue > maxDepthInMM)	hue = maxDepthInMM;
					if (hue != 0) {
						hue = 255 - (int) ((float) (hue)/maxDepthInMM * 255f);
					}
					argb = (hue<<16) + (0<<8) + hue;
					
				}
				
				img.setRGB(width-x-1, y, argb);    // flip horiz
			}
		}
		
		return img;
	}
	
	
	/**
	 * Convert resampled matrix back into pixel array.
	 * @param frameCells resampled 2-dimensional array matrix to be converted
	 * @return width x height 16-bit integer array of pixels
	 */
	public static int[] cellsToPixels(int[][] frameCells) {
		int[] result = new int[width*height];

		int resX = width/frameCells.length;
		int resY = height/frameCells[0].length;
		for (int x=0; x<frameCells.length; x++) {
			for (int y=0; y<frameCells[x].length; y++) {
				for (int xx=0; xx<resX; xx++) {
					for (int yy=0; yy<resY; yy++) {
						result[(y*resY+yy)*width+x*resX+xx] = frameCells[x][y];
					}
				}
			}
		}
		
		return result;
	}
	
	/**
	 * Find actual depth traveled by comparing overlaid pixels in various positions.
	 * @param frameBefore 16-bit pixel array
	 * @param frameAfter 16-bit pixel array
	 * @return initial-guess depth in mm
	 * UNUSED 
	 */
	public static double[] findDepthAndAngle(short[] frameBefore, short[] frameAfter, int guessedDistance) {
		/*
		 * convert frameBefore to lower res
		 * compare frameAfter to frameBefore at various depths -- start from guessed distance
		 * don't compare cells if any are 0
		 * start with simple overlay, no iterations, no quitting on worsening 
		 * 
		 */
		
		int resX = 5;
		int resY = 5;
		final int[][] cellsBeforeUnscaled = resampleAveragePixel(frameBefore, resX, resY);
		final int[][] cellsAfter = resampleAveragePixel(frameAfter, resX, resY);
		final int cwidth =  cellsAfter.length;
		final int cheight = cellsAfter[0].length;
		final int xAngleCells = (int) (Math.round((width/(double) camFOVx)/resX * 16 * guessedDistance/1000)/2);
		final int yAngleCells = 0;
		double winningAvg = 9999999; 
		int winningDepth = 0; 
		int winningX = 0;
//		int winningY = 0;
		
		for (int d=guessedDistance-guessedDistance/2; d<guessedDistance+guessedDistance/2; d+=2) {
			int[][] cellsBeforeScaled = scaleResampledPixels(cellsBeforeUnscaled, d);
			for (int xx=-xAngleCells; xx<=xAngleCells; xx++) { // horiz angle
				for (int yy=-yAngleCells;yy<=yAngleCells; yy++) { // vert angle

					int total = 0;
					int compared = 0;
					for (int x=0; x<cellsBeforeScaled.length; x++) {
						for (int y=0; y<cellsBeforeScaled[0].length; y++) {
							if (x+xx>=0 && x+xx <cwidth && y+yy>=0 && y+yy < cheight) {
								if(cellsBeforeScaled[x+xx][y] != 0 && cellsAfter[x][y] != 0 &&
								(cellsBeforeScaled[x+xx][y] & 0xf0000) >> 16 != 1 && (cellsAfter[x][y] & 0xf0000) >> 16 != 1) {

									int diff = Math.abs(cellsBeforeScaled[x+xx][y] - cellsAfter[x][y]);
									total +=  diff;
									compared ++;
								}
							}
						}
					}
					double avgdiff = (double) total /compared;
//					System.out.println("depth: "+d+", avgdiff: "+avgdiff+", compared: "+compared+", x:"+xx+", y:"+yy);
					if ( avgdiff < winningAvg) {
						winningAvg = avgdiff;
						winningDepth = d;
						winningX = xx;
//						winningY = yy;
					}
					
				}
			}
		}

		// TODO: testing only, remove!!!
//		Application.processedImage = generateDepthFrameImg(cellsToPixels(cellsAfter));
		
//		final int xAngleCells = (int) (Math.round((320.0/58)/res * 16 * guessedDistance/1000)/2);
		double half = 0.5;
		if (winningX < 0)  half = -0.5; // negative angle

		double angle = (winningX+half) * ((double) width/camFOVx)/resX;
		return new double[]{(double) winningDepth, angle, winningAvg}; //, winningY};
	}
	
	

	public static int findDepth(short[] frameBefore, short[] frameAfter, int guessedDistance, double angle) {
		/*
		 * convert frameBefore to lower res
		 * compare frameAfter to frameBefore at various depths -- start from guessed distance
		 * don't compare cells if any are 0
		 * start with simple overlay, no iterations, no quitting on worsening 
		 * 
		 */
		
		int resX = 5;
		int resY = 5;
		final int[][] cellsBeforeUnscaled = resampleAveragePixel(frameBefore, resX, resY);
		final int[][] cellsAfter = resampleAveragePixel(frameAfter, resX, resY);
		final int cwidth =  cellsAfter.length;
		final int cheight = cellsAfter[0].length;
		final int xx = (int) (Math.round((width/(double) camFOVx)/resX * angle));
		double winningAvg = 9999999; 
		int winningDepth = 0; 
		
		for (int d=guessedDistance-guessedDistance/2; d<guessedDistance+guessedDistance/2; d+=2) {
			int[][] cellsBeforeScaled = scaleResampledPixels(cellsBeforeUnscaled, d);

				int total = 0;
				int compared = 0;
				for (int x=0; x<cellsBeforeScaled.length; x++) {
					for (int y=0; y<cellsBeforeScaled[0].length; y++) {
						if (x+xx>=0 && x+xx <cwidth && y>=0 && y < cheight) {
							if(cellsBeforeScaled[x+xx][y] != 0 && cellsAfter[x][y] != 0 &&
							(cellsBeforeScaled[x+xx][y] & 0xf0000) >> 16 != 1 && (cellsAfter[x][y] & 0xf0000) >> 16 != 1) {

								int diff = Math.abs(cellsBeforeScaled[x+xx][y] - cellsAfter[x][y]);
								total +=  diff;
								compared ++;
							}
						}
					}
				}
				double avgdiff = (double) total /compared;
//					System.out.println("depth: "+d+", avgdiff: "+avgdiff+", compared: "+compared+", x:"+xx+", y:"+yy);
				if ( avgdiff < winningAvg) {
					winningAvg = avgdiff;
					winningDepth = d;
				}
			
		}

		return winningDepth;
	}
	
	public static int findDistanceTopView(short[] frameBefore, short[] frameAfter, double angle, final int guessedDistance) { 
		final int h = 320;
		final int w = (int) (Math.sin(Math.toRadians(camFOVx/2)) * h) * 2; // narrower for better resuts?
		final byte[][] cellsBefore = projectFrameHorizToTopView(frameBefore, h);
		final byte[][] cellsAfter = projectFrameHorizToTopView(frameAfter, h);
		final double scaledCameraSetback = (double) cameraSetBack* h/maxDepthFPTV; // pixels
		final int scaledGuessedDistance = guessedDistance * h/maxDepthFPTV; // pixels
		angle = -Math.toRadians(angle);

		int winningTtl = 0; 
		int winningDistance = 99999;
	
		 
		for (int d=scaledGuessedDistance-scaledGuessedDistance/2; d<scaledGuessedDistance+scaledGuessedDistance/2; d++) {
//		for (int d=0; d<scaledGuessedDistance*2; d++) {

			int total = 0;

			for (int x=0; x<w; x++ ) {
				for (int y=0; y<h; y++) {
					
					if (cellsBefore[x][y] != 0) {

						double anglexy = Math.atan((w/2-x)/(double)(h-1-y - scaledCameraSetback));
						double hyp = (h-1-y- scaledCameraSetback)/Math.cos(anglexy); // cos a = y/h

						int xx = -(int) Math.round(hyp * Math.sin(anglexy+angle)-w/2);  // sin angleXY+angle = (w/2-xx)/hyp
						int yy = -(int) Math.round(hyp * Math.cos(anglexy+angle) -h+1+scaledCameraSetback) +d; // cos angleXY+angle = (h-1-yy)/hyp
						
						if (xx>=0 && xx<w && yy>=0 && yy<h ) { 
							if (cellsAfter[xx][yy] != 0)   total ++;
						}
						
					}
					
				}
			}
	
			if (total > winningTtl) {
				winningTtl = total;
				winningDistance = d;
			}
		
		}
		
//		System.out.println("winningTtl: "+winningTtl);
//		winningDistance = (int) Math.round((double)winningDistance * maxDepthFPTV/h);
		winningDistance = winningDistance * maxDepthFPTV/h;
		return winningDistance;
	}
	
	/**
	 * Find angle using 1st-person POV
	 * @param frameBefore
	 * @param frameAfter
	 * @param angleGuess
	 * @return
	 */
	public static double findAngle(short[] frameBefore, short[] frameAfter, double angleGuess) {
		// left = positive angle (right hand rule)
		int resX = 4;
		int resY = 4;
		int[][] cellsBefore = resampleAveragePixel(frameBefore, resX, resY);
		int[][] cellsAfter = resampleAveragePixel(frameAfter, resX, resY);
		cellsBefore = findFloorPlane(cellsBefore);
		cellsAfter = findFloorPlane(cellsAfter);
		final int cwidth =  cellsAfter.length;
		final int cheight = cellsAfter[0].length;
		double winningAvg = 9999999; 
		int winningX = 0;
//		int winningAvgDepth = 0;

		for (int xx=-cwidth/2; xx<=cwidth/2; xx++) { // angle: x-only assumed perfectly horiz rotation

			int total = 0;
			int compared = 0;
//			int avgDepthCompared = 0;
//			int avgDepthTotal = 0;
			for (int x=0; x<cwidth; x++) {
				for (int y=0; y<cheight; y++) {
					if (x+xx>=0 && x+xx <cwidth) {
						if(cellsBefore[x+xx][y] != 0 && cellsAfter[x][y] != 0 &&
							(cellsBefore[x+xx][y] & 0xf0000) >> 16 != 1 && (cellsAfter[x][y] & 0xf0000) >> 16 != 1) {
							int diff =0;
//							if ((cellsBefore[x+xx][y] & 0xf0000) >> 16 != 1 && (cellsAfter[x][y] & 0xf0000) >> 16 != 1) {
								diff = Math.abs(cellsBefore[x+xx][y] - cellsAfter[x][y]);
								total +=  diff;
//							}
							compared ++;
//							if (cellsBefore[x+xx][y] + cellsBefore[x+xx][y] < maxDepthFPTV*2) {
//								avgDepthTotal += cellsBefore[x+xx][y] + cellsAfter[x][y] - diff*2;
//								avgDepthCompared++;
//							}
						}
					}
				}
			}
			double avgdiff = (double) total /compared;
//			int avgDepth = avgDepthTotal/compared/2;
			if ( avgdiff < winningAvg) {
				winningAvg = avgdiff;
				winningX = xx;
//				winningAvgDepth = avgDepth- cameraSetBack;
			}
			
		}

		if (Math.abs(winningX)==cwidth/2 || winningX == 0)  return 9999;
		
		double half = 0.5;
		if (winningX < 0)  half = -0.5; // negative angle
		double angle =  (double) (winningX+half)/(width/resX) * camFOVx; 
//		System.out.println("angle start: "+angle);
		int nominalDepth = 800;
		int adj = (int) (Math.cos(Math.toRadians(angle)) * nominalDepth);
		int opp = (int) (Math.sin(Math.toRadians(angle)) * nominalDepth);
//		System.out.println("winningAvgDepth: "+winningAvgDepth);
//		System.out.println("adj: "+adj);
//		System.out.println("opp: "+opp);

		angle = Math.toDegrees(Math.atan((double) opp/(adj-cameraSetBack)));
		return angle;
	}
	
	// TODO: limit range check with timed odometry approximation
	// TODO: evaluate accuracy by looking at total pixels compared (higher = more accurate)
	public static double findAngleTopView(short[] frameBefore, short[] frameAfter, double angleGuess) { 
		final int h = 320;
		final int w = (int) (Math.sin(Math.toRadians(camFOVx/2)) * h) * 2;
		final byte[][] cellsBefore = projectFrameHorizToTopViewForAngle(frameBefore, h);
		final byte[][] cellsAfter = projectFrameHorizToTopViewForAngle(frameAfter, h);
		final double scaledCameraSetback = (double) cameraSetBack* h/maxDepthFPTV;

//		System.out.println("scaledCameraSetback: "+scaledCameraSetback);
		
		int winningTtl = 0; 
//		int winningCompared = 0;
		double winningAngle = 9999;
//		double winningRatio = 0;
		final double fovHalf =  Math.toRadians(camFOVx/2); // radians
		final double angleIncrement = Math.toRadians(0.2); 
		
		 
		for (double angle = -fovHalf; angle <= fovHalf; angle += angleIncrement) {
			
//			double angle = Math.toRadians(15.1);
		
			int total = 0;
//			int compared = 0;

			for (int x=0; x<w; x++ ) {
				for (int y=0; y<h; y++) {
					if (cellsBefore[x][y]==0b01) {
						
						double anglexy = Math.atan((w/2-x)/(double)(h-1-y- scaledCameraSetback));
						double hyp = (h-1-y- scaledCameraSetback)/Math.cos(anglexy); // cos a = y/h

						int xx = -(int) Math.round(hyp * Math.sin(anglexy+angle)-w/2);  // sin angleXY+angle = (w/2-xx)/hyp
						int yy = -(int) Math.round(hyp * Math.cos(anglexy+angle) -h+1+scaledCameraSetback); // cos angleXY+angle = (h-1-yy)/hyp
						
						if (xx>=0 && xx<w && yy>=0 && yy<h ) {
//							compared ++;
							if (cellsAfter[xx][yy]==0b01)   total ++;
						}

					}
					
				}
			}
	
//			double ratio = (double) total/compared;
//			if (ratio > winningRatio) {
			if (total > winningTtl) {
				winningTtl = total;
				winningAngle = angle;
//				winningCompared = compared;
//				winningRatio = ratio;
			}
		
		}
		
//		System.out.println("compared :"+winningCompared);
//		System.out.println("winningTtl :"+winningTtl);
//		System.out.println("ratio: "+(double) winningRatio);
//		System.out.println("winningAngle :"+Math.toDegrees(winningAngle));
		
		if (Math.abs(winningAngle)==fovHalf) return 9999;
		
		winningAngle = -Math.toDegrees(winningAngle);
//		int nominalDepth = 800;
//		int adj = (int) (Math.cos(Math.toRadians(winningAngle)) * nominalDepth);
//		int opp = (int) (Math.sin(Math.toRadians(winningAngle)) * nominalDepth);
//		winningAngle = Math.toDegrees(Math.atan((double) opp/(adj-cameraSetBack)));
		return winningAngle;
	}
	
	// for measuring distance and/or angle moved purposes
	public static byte[][] projectFrameHorizToTopView(short[] frame, int h) { 
		final int w = (int) (Math.sin(Math.toRadians(camFOVx)/2) * h) * 2;
		final double angle = Math.toRadians(camFOVx/2);
		
		byte[][] result = new byte[w][h];

		final int xdctr = w/2;
		int horizoffset = 0; 
		
		for (int y = height/2-horizoffset; y<=height/2+horizoffset; y++) { // TODO: incorporate yAngleCompStart
			for (int x=0; x<width; x++) {
	
				int d = frame[y*width+x];
				int ry = (int) Math.round((double) d/ maxDepthFPTV  * h);
				double xdratio = (x*(double) w/width - xdctr)/ (double) xdctr;
				int rx = (w/2) - (int) Math.round(Math.tan(angle)*(double) ry * xdratio);
				
				if (ry<h && ry>0 && rx>=0 && rx<w) {
					result[rx][h-ry-1] = 0b11;
//					result[rx][h-ry-2] = 0b11;
//					result[rx+1][h-ry-1] = 0b11;
//					result[rx-1][h-ry-1] = 0b11;
				}
			}
		}
		
		return result;
	}
	
	// for measuring distance and/or angle moved purposes
	public static byte[][] projectFrameHorizToTopViewForAngle(short[] frame, int h) { 
		final int w = (int) (Math.sin(Math.toRadians(camFOVx)/2) * h) * 2;
		final double angle = Math.toRadians(camFOVx/2);
		
		byte[][] result = new byte[w][h];

		final int xdctr = w/2;
		
		for (int y = height/2-2; y<=height/2+2; y++) { // middle 5 horiz pixels 
			for (int x=0; x<width; x++) {
	
				int d = frame[y*width+x]; 
				int ry = (int) ((double) d/ maxDepthFPTV  * h);
				double xdratio = (x*(double) w/width - xdctr)/ (double) xdctr;
				int rx = (w/2) - (int) (Math.tan(angle)*(double) ry * xdratio);
				
				if (ry<h && ry>=0 && rx>=0 && rx<w) {
					result[rx][h-ry-1] = 0b01;
				}
			}
		}
		
		return result;
	}
	
	public BufferedImage floorPlaneImg() {

		short[] depth = Application.openNIRead.readFullFrame();
		int[][] frameCells = resampleAveragePixel(depth, 2, 2);
		int[][] frameCellsFP = findFloorPlane(frameCells);
		int[] pixels = cellsToPixels(frameCellsFP);

		return generateDepthFrameImgWithFloorPlane(pixels);
	}
	
	// TODO: unused
	public static void addFrameToMap(short[] depth, int distance, double angle) {
//		int[][] frameCells = resampleAveragePixel(depth, 2, 2);
//		int[][] frameCellsFP = findFloorPlane(frameCells);
//    	byte[][] floorPlaneCells = floorPlaneAndHorizToPlanView(frameCellsFP, depth, 240);
//    	Mapper.addArcPath(floorPlaneCells, distance, angle);
		
//		Mapper.addArcPath(projectFrameHorizToTopView(depth, 240), distance, angle);
	}
	
	public static BufferedImage floorPlaneTopViewImg() {
		short[] depth = Application.openNIRead.readFullFrame();
		int[][] frameCells = resampleAveragePixel(depth, 2, 2);
		int[][] frameCellsFP = findFloorPlane(frameCells);
    	byte[][] floorPlaneCells = floorPlaneAndHorizToPlanView(frameCellsFP, depth, 240);
     	return byteCellsToImage(floorPlaneCells);
	}
	
	/**
	 * Project floorplane from depth view into plan view
	 * @param frameCells two dimensional array with floorplane highlighted by 5th bit
	 * @return two dimensional byte array 00=unknown, 01=floor plane, 11=not floor plane

	 */
	public static byte[][] floorPlaneToPlanView(int[][] frameCells, final int h) {
//		final int yres =(int) ((double) maxDepth /h);
//		final int xres = yres;
//		final int h = (int) ((double)maxDepth /yres);
		final int w = (int) (Math.sin((camFOVx/2)*Math.PI/180) * h) * 2;
//		System.out.println("width: "+w+", height: "+h);
		final int cwidth = frameCells.length;
		final int cheight = frameCells[0].length;
		final double angle =  (double)camFOVx/2*Math.PI/180; // 0.392699082; // 29 deg in radians from ctr, or half included view angle
		
		byte[][] result = new byte[w][h];

		final int xdctr = cwidth/2;

		for (int y=0; y<cheight; y++) {
			for (int x=0; x<cwidth; x++ ) {
				int d=frameCells[x][y]; // depth
				
				byte b = 0;
				if (y>cheight/2-3 && y<cheight/2+3)  b = 0b11; // horiz
				else if ((d & 0xf0000) >> 16 == 1)  { // 17th bit set at 1, is floor plane
					d = d & 0xffff;
					b = 0b01;
				}
//				int y = (int) ((float)xdepth[xd]/(float)maxDepthInMM*(float)h);
				int ry = (int) ((double) d/ (double) maxDepthFPTV  * (double) h);
				double xdratio = (double)(x - xdctr)/ (double) xdctr;
				int rx = (w/2) - ((int) (Math.tan(angle)*(double) ry * xdratio));

//					System.out.println("x: "+x+", y: "+y+", d: "+d+", rx:"+rx+", ry: "+ry);
				
				if (ry<h && ry>=0 && rx>=0 && rx<w) {
					result[rx][h-ry-1] = b;
				}
				
				
			}
		}
		
//		result[3][3]=true;
		return result;
	}
	
	public static byte[][] floorPlaneAndHorizToPlanView(final int[][] frameCells, final short frame[], final int h) {
		final int w = (int) (Math.sin((camFOVx/2)*Math.PI/180) * h) * 2;
		final int cwidth = frameCells.length;
		final int cheight = frameCells[0].length;
		final double angle =  (double)camFOVx/2*Math.PI/180; // 0.392699082; // 29 deg in radians from ctr, or half included view angle
		
		byte[][] result = new byte[w][h];

		final int xdctr = cwidth/2;

		for (int y=0; y<cheight; y++) {
			for (int x=0; x<cwidth; x++ ) {
				int d=frameCells[x][y]; // depth
				
				byte b = 0;
				if ((d & 0xf0000) >> 16 == 1)  {
					d = (d & 0xffff); 
					b = 0b01; 
				}

				int ry = (int) ((double) d/ (double) maxDepthFPTV  * (double) h);
				double xdratio = (double)(x - xdctr)/ (double) xdctr;
				int rx = (w/2) - ((int) (Math.tan(angle)*(double) ry * xdratio));
				
				if (ry<h && ry>=0 && rx>=0 && rx<w && b!=0) {
					result[rx][h-ry-1] = b;
				}
				
				
			}
		}
		
		
		// now overlay horiz
		for (int y = height/2-0; y<=height/2+0; y++) {
			for (int x=0; x<width; x+=1) {
	
				int d = frame[y*width+x]; // -cameraSetBack;
				int ry = (int) ((double) d/ (double) maxDepthFPTV  * (double) h);
				double xdratio = (double)(x*(double) cwidth/width - xdctr)/ (double) xdctr;
				int rx = (w/2) - ((int) (Math.tan(angle)*(double) ry * xdratio));
				
				if (ry<h && ry>=0 && rx>=0 && rx<w) {
					result[rx][h-ry-1] = 0b11;
				}
			}
		}

		result[w/2][h-1] = 0;
		return result;
	}
	
	// unused?
	public static BufferedImage byteCellsToImage(byte[][] cells ) {

		final int cwidth = cells.length;
		int cheight= cells[0].length;
		BufferedImage img = new BufferedImage(cwidth, cheight, BufferedImage.TYPE_INT_RGB);

		for(int y=0; y<cheight; y++) {
//			int intensity = (int) (((double)(cheight-y)/cheight)*255.0);
//			intensity = 100;
				for(int x=0; x<cwidth; x++) {
				
				/* single hue */
//				int argb = 0;
//				switch (cells[x][y]) {
//					case 0b01: argb=0x0000ff; break; // blue
//					case 0b11: argb=0x00ff00; break; // green  // argb=(intensity<<16)+(0<<8)+intensity; break;
//					case 0b10: argb=0xff0000; break; // red
//					default: argb = 0x000000;
//				}

				int hue = cells[x][y];
//				if (hue != 0) System.out.println(hue);
				int argb =  (hue<<8) + 0;

				img.setRGB(x, y, argb);  

			}
		}
//		img = (BufferedImage) img.getScaledInstance((int) Math.round(cwidth*scale),
//				(int)Math.round(height*scale), Image.SCALE_DEFAULT);
		return img;
	}
	
	
	public static BufferedImage cellsToImage(short[][] cells ) {

		final int cwidth = cells.length;
		int cheight= cells[0].length;
		BufferedImage img = new BufferedImage(cwidth, cheight, BufferedImage.TYPE_INT_RGB);

		for(int y=0; y<cheight; y++) {

			for(int x=0; x<cwidth; x++) {

				int hue = cells[x][y];
				int argb  = 0;
				if (hue == -1) argb = 0xff0000; // bright red 
				else if (hue > Stereo.objectMin && hue < Stereo.objectMax) argb =  hue<<8; // green
				else if (hue > Stereo.nonObjectMin && hue < Stereo.nonObjectMax) argb = 256+hue; // blue
				else if (hue > Stereo.fovMin && hue < Stereo.fovMax) argb = hue<<16 | hue<<8 | hue;

				img.setRGB(x, y, argb);  

			}
		}

		return img;
	}

	


}
