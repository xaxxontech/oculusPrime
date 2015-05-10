package developer.image;

import oculusPrime.Settings;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.IOException;
import java.net.URL;

public class ImageUtils {
	
	public final int matrixres = 10;
	public int imgaverage;

	public ImageUtils() {}

	public static BufferedImage getImageFromStream() {
		BufferedImage img = null;
		try {
            img = ImageIO.read(new URL("http://127.0.0.1:5080/oculusPrime/frameGrabHTTP"));
//			img = ImageIO.read(new URL("http://192.168.0.107:5080/oculusPrime/frameGrabHTTP"));
		} catch (IOException e) { e.printStackTrace(); }
		return img;
	}
	
	public int[] convertToGrey(BufferedImage img) { // convert image to 8bit greyscale int array
		int[] pixelRGB = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
		
		int p; 
		int[] greyimg = new int[img.getWidth()*img.getHeight()];
		int n = 0;
		int runningttl = 0;			
		for (int i=0; i < pixelRGB.length; i++) {
			int  red   = (pixelRGB[i] & 0x00ff0000) >> 16;
			int  green = (pixelRGB[i] & 0x0000ff00) >> 8;
			int  blue  =  pixelRGB[i] & 0x000000ff;
			p = (int) (red*0.3 + green*0.59 + blue*0.11) ;
			greyimg[n]=p;
			n++;
			runningttl += p;
		}
		imgaverage = runningttl/n;
		return greyimg;
	}

	public BufferedImage intToImage(int[] pixelRGB, int width, int height) { // dev tool
		BufferedImage img  = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for(int y=0; y<height; y++) {
			for (int x=0; x<width; x++) {
				int grey = pixelRGB[x + y*width];
				int argb = (grey<<16) + (grey<<8) + grey;
				img.setRGB(x, y, argb);
			}
		}
		return img;
	}
	
	public int[][] convertToMatrix(int[] greyimg, int width, int height) {
//		var result:Array = [];
		int[][] matrix = new int[width/matrixres][height/matrixres]; //TODO: may need to add or subtract 1?
		int n;
		int xx;
		int yy;
		int runningttl;
		for (int x = 0; x < width; x += matrixres) {			
			for (int y=0; y<height; y+=matrixres) {
				
				runningttl = 0;
				for (xx=0; xx<matrixres; xx++) {
					for (yy=0; yy<matrixres; yy++) {
						runningttl += greyimg[x + xx + (y+yy)*width]; 
					}
				}
				
				n = runningttl/(matrixres*matrixres);				
				matrix[x/matrixres][y/matrixres] = n - imgaverage;
														
			}
		}
		return matrix;
	}
	
	/**
	 * Compare matrix with ctrmatrix, overlay one matrix on the other and find offset from center
	 * 
	 * @param matrix current matrix to be compared
	 * @param ctrMatrix previously recorded center matrix
	 * @param width
	 * @param height
	 * @return x,y in pixels
	 */
	public int[] findCenter(int[][] matrix, int[][] ctrMatrix, int width, int height) {
		
		int widthRes = width/matrixres;
		int heightRes = height/matrixres;
		int compared = 0;
		int total = 0;
		int winningx = 0;
		int winningy = 0;
		
		int winningTotal = 0; // debug log only 
		int winningCompared = 0; // debug log only

		double winningRatio = 9999999; 
	
		for (int x=-(widthRes/2); x<=widthRes/2; x++) {
			for (int y=-(heightRes/2); y<=heightRes/2; y++) {
				total = 0;
				compared =0;
				for (int xx=0; xx<matrix.length; xx++) {
					for (int yy=0;yy<matrix[xx].length; yy++) {
						if (xx+x >= 0 && xx+x < widthRes && yy+y >=0 && yy+y <heightRes) { 
							total += Math.abs(matrix[xx+x][yy+y] - ctrMatrix[xx][yy]);
							compared++;
						}
					}						
				}
				if ( (double) total / (double) compared < winningRatio) {
					winningRatio = (double) total/ (double) compared;
					winningTotal = total; // debug log only 
					winningCompared = compared; // debug log only 
					winningx = x;
					winningy = y;
				}
			}
		}
		System.out.print("ctr mxy: "+winningx+", "+winningy+", ");
		winningx = width/2 + (winningx*matrixres) + (matrixres/2);
		winningy = height/2 + (winningy*matrixres) + (matrixres/2);
		System.out.println("ctr pxy: "+winningx+","+winningy+", wttl: "+winningRatio+", ttl: "+winningTotal+", comp: "+ winningCompared);
		return new int[]{winningx, winningy};	
	}
	
	public int[] edges(int[] greypxls, int width, int height) {
		int[] edgeimg = new int[width*height];
		for (int n=0; n<width*height; n++) {
			int darkest = greypxls[n];
			int lightest = darkest;
			int[] closest = new int[]{n-width-1, n-width, n-width+1, n-1, n+1, n+width-1, n+width, n+width+1};
//			int[] closest = new int[]{n-width, n-1, n+1, n+width};
			for (int i=0; i< closest.length; i++) {
				if ( ( closest[i] >=0 && closest[i] < width*height ) && !((double) (n / width) == n/width && closest[i] == n+1)  &&  !((double) ((n-1)  / width) == n/width && closest[i] == n-1) ) {
					if (greypxls[closest[i]] < darkest) {
						darkest = greypxls[closest[i]];
					}
					if (greypxls[closest[i]] > lightest) {
						lightest = greypxls[closest[i]];
					}
				} 
			}
			if (lightest - darkest > imgaverage/4) { //30
				edgeimg[n] = 255;
			}
			else  { edgeimg[n] = 0; }
		}
		return edgeimg;
	}
	
	public BufferedImage blur (BufferedImage img) {
	    float[] matrix = {
	            0.111f, 0.111f, 0.111f, 
	            0.111f, 0.111f, 0.111f, 
	            0.111f, 0.111f, 0.111f, 
	        };

        BufferedImageOp op = new ConvolveOp( new Kernel(3, 3, matrix) );
        img = op.filter(img, new BufferedImage(320, 240, BufferedImage.TYPE_INT_ARGB));
        return img;
	}
	
	public int[] convertToBW(int[] greypxls) {
		int[] bwpxls = new int[greypxls.length];
		int threshold = imgaverage;
		for (int i=0; i<greypxls.length; i++) {
			if (greypxls[i] < threshold) { bwpxls[i] = 0; }
			else { bwpxls[i] = 255; }
		}
		return bwpxls;
	}

	public int[] middleMass(int[] bwpxls, int width, int height, int sensitivty) {
		int[] ctrxy = new int[2];
		int xavg = (width/2) * width*height; // ctr
		int yavg = (height/2) * width*height; // ctr

		for(int y=0; y<height; y++) {
			for (int x=0; x<width; x++) {
				if (bwpxls[x + y*width] != 0) { 
					xavg += (x-(width/2))*sensitivty;
					yavg += (y-(height/2))*sensitivty;
				}

			}
		}
		
		ctrxy[0]= xavg /(width*height);
		ctrxy[1]= yavg /(width*height);
		return ctrxy;
	}
	
	public int[] middleMassGrey(int[] greypxls, int width, int height) {
		int[] restultxy = new int[2];
		
		// find intensity ctr and average for each row
		int[] rowavg = new int[height];
		int[] rowxpos = new int[height];
		for(int y=0; y<height; y++) {
			int avg = 0;
			int max = 0;
			int offset = 0;
			for (int x=0; x<width; x++) {
				int p = greypxls[x + y*width];
				offset += (x-(width/2))*p;
				if (p>max) { max = p; }
				avg += p;
			}
			rowavg[y] = avg/width;
			rowxpos[y] = (width/2)+(offset/max);
		}
		
		// find weighted average of all rows for single x
		int xavg=0;
		for(int y=0; y<height; y++) {
			
		}
		
		restultxy[0]= 0;
		restultxy[1]= 0;
		return restultxy;
	}

//	/*
	public static BufferedImage toBufferedImageOfType(BufferedImage original, int type) {
		if (original == null) {
			throw new IllegalArgumentException("original == null");
		}

		// Don't convert if it already has correct type
		if (original.getType() == type) {
			return original;
		}

		// Create a buffered image
		BufferedImage image = new BufferedImage(original.getWidth(), original.getHeight(), type);

		// Draw the image onto the new buffer
		Graphics2D g = image.createGraphics();
		try {
			g.setComposite(AlphaComposite.Src);
			g.drawImage(original, 0, 0, null);
		}
		finally {
			g.dispose();
		}

		return image;
	}
//	*/
}
