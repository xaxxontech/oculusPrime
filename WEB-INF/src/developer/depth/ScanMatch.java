package developer.depth;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Vector;

import oculusPrime.Application;


public class ScanMatch {
	
	/*
	 * read 320x240 frame before move, store in variable: short[] frameBefore
	 * then, move bot -- linear or rotation
	 * read another 320x340 frame, store in variable: short[] frameAfter
	 * if linear move forward 0.6m:
	 * 		resample frameBefore (lower resolution)...? 32x24 (instead of averaging, take *closest* pixel??)
	 * 		resample frameAfter, ctr-scale up to match distance checking for		
	 * 			-tricky float operation or what? closest pixel as well?
	 * 		overlay frameBefore over frameAfter in various positions, choose closest match
	 * 		check for range: +/- 50% guessed distance (2mm increments), +/- 10 deg X, +/- 10 deg Y (resolution incrm.)
	 * 			-check for each range starting from *centers* - if progress is getting consistently WORSE, quit
	 * 		scale-resampled frameAfters are good for +/-5mm (ie., 1 resampled frameAfter for 10 possible positions)
	 * 
	 * 
	 */
	
	final int width=320;
	final int height=240;
	private final int camFOVx = 58; // degrees
	private final int camFOVy = 45; // degrees
	public final int maxDepthInMM = 5000; // 3500
//	int res = 10; // re-samples 320x240 to 32x24
//	short[] frameBeforeMove = null;
//	short[] frameAfterMove = null;
	private final int fpIndicatorDepth = 9999; 
	private final int depthCamVertDistfromFloor = 290;

	
	/**
	 * Load framedata from file. 
	 * @param f file to be loaded
	 * @return short integer array containing each pixel depth
	 */
	public short[] getFrame(File f) {
		
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
	 * Declare whole cell 0 is certain percentage is 0
	 * @param framePixels short[] array of 16-bit depth data
	 * @return int[][] xy array of averaged pixels
	 */
	public int[][] resampleAveragePixel(short[] framePixels, int resX, int resY) {
		
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
//						if (p!=0)   count ++;
						if (p==0)  { zeros ++; }
//						if (p<maxDepthInMM)   runningTotal += p;
						runningTotal += p;
					}
				}
//				if (count !=0)    result[x/res][y/res] = runningTotal / count;
				if (zeros < zerosmax)    result[x/resX][y/resY] = runningTotal / (resX*resY);
				
			}
		}
		
		return result;
	}
	
	
	public int[][] resampleAveragePixelWithFloorPlane(short[] framePixels, int resX, int resY) {
		
		int xx;
		int yy;
		final int zerosmax = (int) (resX*resY*0.25);
		final double yAngleTolerance = 1;
		final double maxYangleComp = 3;
		double yAngleCompStart = 0;
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

			result = new int[width/resX][height/resY];
			for (int x = 0; x < width; x += resX) {			
				for (int y=0; y<height; y+= resY) {
					
					// floor plane angles
					int fpMin = 0;
					int fpMax = 0;
					
					final int yStart = height/2 + (int) ((double) height/camFOVy * (8 -yAngleComp)); 
					if (y > yStart) { // lower portion FOV only    (y > (int) (height * 0.6)
						double yAngle = (y - (height/2.0)) * camFOVy/height; // horiz=0 degrees, below = positive
						fpMin = (int) ( depthCamVertDistfromFloor / Math.sin((yAngle + yAngleComp + yAngleTolerance)*Math.PI/180) );
						fpMax = (int) ( depthCamVertDistfromFloor / Math.sin((yAngle + yAngleComp - yAngleTolerance)*Math.PI/180) );
	//					if (fpMax > 2500) fpMax = 2500;
					}
					
					int zeros = 0;
					int runningTotal = 0;
					for (xx=0; xx<resX; xx++) {
						for (yy=0; yy<resY; yy++) {
							int p = framePixels[x + xx + (y+yy)*width];
							if (p==0)  { zeros ++; }
							runningTotal += p;
						}
					}
					if (zeros < zerosmax) {
						int d= runningTotal / (resX*resY);
						if (d>fpMin && d<fpMax)  {
//							d = fpIndicatorDepth;
							d = 0x10000 + d;
							total ++;

						}
						result[x/resX][y/resY] = d;
					}
					
				}
			}
			
			if (total > 0 && total > winningTotal) {
				winningTotal = total;
				winningYangleComp = yAngleComp;
				winningIndex = index;
				results.add(result); // only save good ones
				index++;
//				fail--;
				if (fail <0) fail = 0;
			}
//			else  fail++;
			 
//			if (fail > 2) {
//				System.out.println("fail");
//				break; // getting progressively worse, don't bother checking the rest
//			}
			
			yAngleComp = yAngleCompStart + yAngleCompIncrement * c;

			if (yAngleCompIncrement > 0) {
				if (yAngleComp > yAngleCompStart + maxYangleComp) {
					yAngleComp = yAngleCompStart - yAngleCompIncrement * c;
					if (yAngleComp < yAngleCompStart - maxYangleComp)  break;
				}
			}
			else {
				c++;
				if (yAngleComp < yAngleCompStart - maxYangleComp)  {
					yAngleComp = yAngleCompStart + yAngleCompIncrement * c;
					if (yAngleComp > yAngleCompStart + maxYangleComp)  break;
				}
			}
			
			yAngleCompIncrement *= -1;

		}

//		System.out.println(winningYangleComp);
		if (winningIndex != -1)    return results.get(winningIndex);
		else   return result;
	}
	
	/**
	 * Re-position xy of each cell from 2-dimensional array based on distance moved-guess. Only over-write
	 * cells if closer 
	 * @param frameCells 2-dimensional array, previously generated by resampleClosestPixel()
	 * @param distance in mm of travel (guess)
	 * @return int[][] xy array of closest pixels 
	 */
	public int[][] scaleResampledPixels(final int[][] frameCells, final int dMoved) {
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
	
	public BufferedImage generateDepthFrameImgWithFloorPlane(int[] depth ) {

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
	public int[] cellsToPixels(int[][] frameCells) {
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
	 */
	public double[] findDepth(short[] frameBefore, short[] frameAfter, int guessedDistance) {
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
								if(cellsBeforeScaled[x+xx][y] != 0 && cellsAfter[x][y] != 0) {
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
		double angle = winningX * (320.0/58)/resX;
		return new double[]{(double) winningDepth, angle, winningAvg}; //, winningY};
	}
	
	public double[] findAngle(short[] frameBefore, short[] frameAfter) { //, int guessedRotation) {
		// left = positive angle (right hand rule)
		int resX = 4;
		int resY = 4;
		final int[][] cellsBefore = resampleAveragePixel(frameBefore, resX, resY);
		final int[][] cellsAfter = resampleAveragePixel(frameAfter, resX, resY);
		final int cwidth =  cellsAfter.length;
		final int cheight = cellsAfter[0].length;
		double winningAvg = 9999999; 
		int winningX = 0;

//		int startX = -cwidth/2; // left
//		int endX = 0;
//		if (guessedRotation <= 0) {  
//			startX = 0;
//			endX = cwidth/2; // right
//		}

		for (int xx=-cwidth/2; xx<=cwidth/2; xx++) { // angle

			int total = 0;
			int compared = 0;
			for (int x=0; x<cwidth; x++) {
				for (int y=0; y<cheight; y++) {
					if (x+xx>=0 && x+xx <cwidth) {
						if(cellsBefore[x+xx][y] != 0 && cellsAfter[x][y] != 0) {
							int diff = Math.abs(cellsBefore[x+xx][y] - cellsAfter[x][y]);
							total +=  diff;
							compared ++;
						}
					}
				}
			}
			double avgdiff = (double) total /compared;
//			System.out.println("x: "+xx+", avgdiff: "+avgdiff+", compared: "+compared);
			if ( avgdiff < winningAvg) {
				winningAvg = avgdiff;
				winningX = xx;
			}
			
		}

		//		System.out.println(winningX);
		double angle =  (double) winningX/(width/2/resX) * camFOVx/2;
		return new double[] {angle, winningAvg};
	}
	
	
	public BufferedImage floorPlaneImg(short[] depth) {

		int[][] frameCells = resampleAveragePixelWithFloorPlane(depth, 2, 2);
		int[] pixels = cellsToPixels(frameCells);

		return generateDepthFrameImgWithFloorPlane(pixels);
	}

		
	
    public static void main(String[] args) {
    	
    	ScanMatch s = new ScanMatch();
    	String leader = "Z:\\temp\\"; // windows
//    	String leader = "/mnt/skyzorg/temp/"; // linux 
//    	short[] frameBefore = s.getFrame(new File(leader+"xtion610-1.raw"));
//    	short[] frameAfter = s.getFrame(new File(leader+"xtion610-2.raw"));
//    	short[] frameBefore = s.getFrame(new File(leader+"xtion350-1.raw"));
//    	short[] frameAfter = s.getFrame(new File(leader+"xtion350-2.raw"));
//    	short[] frameBefore = s.getFrame(new File(leader+"xtion500-1.raw"));
//    	short[] frameAfter = s.getFrame(new File(leader="xtion500-2.raw"));
//    	short[] frameBefore = s.getFrame(new File(leader+"xtion517-1.raw"));
//    	short[] frameAfter = s.getFrame(new File(leader+"xtion517-2.raw"));
//    	short[] frameBefore = s.getFrame(new File(leader+"xtion25deg-1.raw"));
//    	short[] frameAfter = s.getFrame(new File(leader+"xtion25deg-2.raw"));
    	short[] frameBefore = s.getFrame(new File(leader+"xtion450-1.raw"));
    	short[] frameAfter = s.getFrame(new File(leader+"xtion450-2.raw"));

    	
//    	int[][] zork =  s.resampleClosestPixel(frameBefore, 4);
//    	System.out.println(zork[12][12]);
//    	System.out.println(zork[4][4]);

    	double[] d = s.findDepth(frameBefore, frameAfter, 500);
    	System.out.println("winner d: "+(int) d[0]+", xɵ:"+d[1]); //+", y: "+d[2]);
    	
//    	double a = s.findAngle(frameBefore, frameAfter);
//    	System.out.println("winner angle: "+a);
    }

}
