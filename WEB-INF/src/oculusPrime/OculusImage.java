package oculusPrime;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;

public class OculusImage {	
	
	private int[] parr; // working pixels, whole image, 8-bit greyscale OR 1 bit B&W
	private int width;
	private int height;
	public int lastThreshhold = -1;
	private float threshholdMult;  //  = 0.65;
	private float lastBlobRatio;
	private float lastTopRatio;
	private float lastBottomRatio;
	private float lastMidRatio;
	private int[] parrorig;
	private int imgaverage;
	
	public OculusImage() { }
	
	public void dockSettings(String str) { 
		String[] a = str.split("_");
		lastBlobRatio = Float.parseFloat(a[0]);
		lastTopRatio = Float.parseFloat(a[1]);
		lastMidRatio = Float.parseFloat(a[2]);
		lastBottomRatio = Float.parseFloat(a[3]);
	}
	
	private void convertToGrey(int[] pixelRGB) {
		// uses 30-59-11 RGB weighting from: http://en.wikipedia.org/wiki/Grayscale#Converting_color_to_grayscale
		int p; 
		parr = new int[width*height];
		int n = 0;
		int runningttl = 0;			
		for (int i=0; i < pixelRGB.length; i++) {
			int  red   = (pixelRGB[i] & 0x00ff0000) >> 16;
			int  green = (pixelRGB[i] & 0x0000ff00) >> 8;
			int  blue  =  pixelRGB[i] & 0x000000ff;
			p = (int) (red*0.3 + green*0.59 + blue*0.11) ;
			parr[n]=p;
			n++;
			runningttl += p;
		}
		imgaverage = runningttl/n;
		threshholdMult = (float) (0.65 - 0.2 + (0.40*( imgaverage/255)));
		
	}
	
	private void sendToImage(int[] pixelRGB) { // dev tool
//		Util.debug("sendtoImage "+pixelRGB.length,this);
//		Application.processedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
//		for(int y=0; y<height; y++) {
//			for (int x=0; x<width; x++) {
//				int grey = pixelRGB[x + y*width];
//				if (grey==1) {grey=255;} // only if psuedo-boolean parr
//				int argb = (grey<<16) + (grey<<8) + grey;
//				Application.processedImage.setRGB(x, y, argb);
//			}
//		}
	}
	
	private void sendToImage(Boolean[] pixelRGB) { // dev tool
		Util.debug("sendtoImageBoolean "+pixelRGB.length,this);

		Application.processedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for(int y=0; y<height; y++) {
			for (int x=0; x<width; x++) {
				int p = x + y*width;
				int argb;
				if (pixelRGB[p]==true) { argb = (255<<16) + (0<<8) + 0; } // fillsize ++; } // red
				else {
					int grey = parrorig[p];
					argb = (grey<<16) + (grey<<8) + grey;
				}
				Application.processedImage.setRGB(x, y, argb);

			}
		}

	}
	
	private Boolean[] floodFill(int[] ablob, int start) {  
		ArrayList<Integer> q= new ArrayList<Integer>();
		q.add(start);
		Boolean[] blob = new Boolean[0];
		try {
			blob = new Boolean[width*height];
		} catch (Exception e) { // probably heap overflow CATCH NOT WORKING
			Util.log("floodFill()", e, this);
//			e.printStackTrace();
			return new Boolean[0];
		}
		Arrays.fill(blob, false);
		int n;
		int w;
		int e;
		int i;

		while (q.size() > 0) {
			n = q.remove(q.size()-1);
			if (ablob[n]==1) {
				w = n;
				e = n;
				while (ablob[w]==1) { w --; if(w<0 || w>=ablob.length) {break;} }
				while (ablob[e]==1) { e ++; if(e<0 || e>=ablob.length) {break;}}
				for (i=w+1; i<=e-1; i++) { 
					ablob[i]=0; 
					blob[i]=true;
					if (i-width<ablob.length && i-width>0) {
						if (ablob[i-width]==1) { q.add(i-width); }
					}
					if (i+width<ablob.length && i+width>0) {
						if (ablob[i+width]==1) { q.add(i+width); }
					}
				}
			}
		}

		return blob;
	}
	
	public String[] findBlobStart(int x, int y, int w, int h, int[] bar) { // calibrate only...
		lastThreshhold = 0;
		String r[];
//		findBlobStartSub(x, y, w, h, bar);
		r = findBlobStartSub(x,y,w,h,bar); // do it again, with contrast averaged
		return r;
	}
	
	private String[] findBlobStartSub(int x, int y, int w, int h, int[] bar) { // calibrate sub
		width = w;
		height = h;
		convertToGrey(bar); 
		parrorig = parr.clone(); // save original image for re-threshholding after
		int start = x + y*width; 
		String[] result = new String[]{"0","0","0","0","0","0","0","0","0"};
		
		int startavg = (parr[start-1]+parr[start]+parr[start+1])/3; //includes 2 adjacent pixels in contract threshhold to counteract grainyness a bit
		int threshhold = (int) (startavg*threshholdMult);
		
		if (lastThreshhold !=0) {
			threshhold = lastThreshhold;
		}
		int i;
		for (i=0;i<parr.length;i++){
			if (parr[i]>threshhold) { parr[i]=1; } 
			else { parr[i]=0; }
		}
		Boolean[] blob = floodFill(parr, start);
		int blobSize =0;
		int r[] = getRect(blob,start);
		int minx = r[0];
		int maxx = r[1];
		int miny = r[2];
		int maxy = r[3];
		blobSize = r[4];
		int	blobBox = (maxx-minx)*(maxy-miny);
		lastTopRatio = (float) getPixelEqTrueCount(blob, minx, (int) (minx+(maxx-minx)*0.333), miny, maxy) / (float) blobBox; // left
		lastMidRatio = (float) getPixelEqTrueCount(blob, (int) (minx+(maxx-minx)*0.333), (int) (minx+(maxx-minx)*0.666), miny, maxy) / (float) blobBox;
		lastBottomRatio = (float) getPixelEqTrueCount(blob, (int) (minx+(maxx-minx)*0.666), maxx, miny, maxy) / (float) blobBox; // left
		lastBlobRatio = (float)(maxx-minx)/(float)(maxy-miny);
		float slope =  getBottomSlope(blob,minx,maxx,miny,maxy)[0];
		//result = x,y,width,height,slope,lastBlobRatio,lastTopRatio,lastMidRatio,lastBottomRatio
		result = new String[]{Integer.toString(minx), Integer.toString(miny), Integer.toString(maxx-minx),
				Integer.toString(maxy-miny), Float.toString(slope), Float.toString(lastBlobRatio),
				Float.toString(lastTopRatio), Float.toString(lastMidRatio), Float.toString(lastBottomRatio)};

		if (lastThreshhold==0) {
			int runningttl = 0;
			for (i=0; i<width*height; i++) { // zero to end
				if (blob[i]) {
					runningttl += parrorig[i];
				}
			}
			lastThreshhold = (int) ((runningttl/blobSize)*threshholdMult); // adaptive threshhold
		}
		sendToImage(blob);
		return result;
	}
	
	public String[] findBlobs(int[] bar, int w, int h) {
		width = w;
		height = h;
		int attemptnum = 0;
		int dir = -1;
		int inc = 10;
		int n = inc;
		int deleteddir = 0;
		String[] result = new String[]{"0","0","0","0","0"}; //x,y,width,height,slope

		while (attemptnum < 15) { // was 15 
			result = findBlobsSub(bar);
			if (result[2].equals("0")) {
				if (deleteddir != 0) {
					n = inc;
					if (deleteddir == -1) {
						dir = 1;
					}
					else { dir =-1; }
				}
				else {
					dir = dir*(-1);						
				}
				lastThreshhold = lastThreshhold + (n*dir);
				if (lastThreshhold < 0) {
					dir = 1;
					deleteddir = -1;
					lastThreshhold = n;
				}
				if (lastThreshhold > 255) {
					dir = -1;
					deleteddir = 1;
					lastThreshhold = 255-n;
				}
				n += inc;
			}
			else { break; }
			attemptnum ++;
		}
		return result;
	}
	
	public String[] findBlobsSub(int[] bar) {
		String[] result = new String[]{"0","0","0","0","0"}; //x,y,width,height,slope
		convertToGrey(bar);
		parrorig = parr.clone();
		int minimumsize = (int) (width*height*0.002);
		
		if (lastThreshhold == -1)  {lastThreshhold = imgaverage; } 
		int threshhold = lastThreshhold;
		
		int i;
		int[] parrinv = new int[width*height]; // inverse, used to check for inner black blob
		for (i=0; i<parr.length; i++){ //convert to B&W
			if (parr[i]>threshhold) { 
				parr[i]=1;
				parrinv[i]=0;
			}
			else { 
				parr[i]=0;
				parrinv[i]=1; 
			}
		}
		int blobnum = 0;
		float maxdiff = 99.0f;
		float diff;
		float slope = -1;
		int winner =-1;
		int[] winRect = new int[]{0,0,0,0,0};
		int minx = 0;
		int miny = 0;
		int maxx = 0;
		int maxy = 0; 
		float topRatio;
		float bottomRatio;
		float midRatio;
		int blobSize;
		int[] r;
		int pixel;
		ArrayList<Boolean[]> blobs = new ArrayList<Boolean[]>();
		
		int blobBox;
		ArrayList<Integer> blobstarts = new ArrayList<Integer>();
		for (pixel=0; pixel<width*height; pixel++) { // zero to end, find all blobs
			if (parr[pixel]==1) { // finds a white one
				Boolean[] temp = floodFill(parr, pixel);
				if (temp.length > minimumsize) { // discard tiny blobs  was 150
					blobs.add(temp);
					blobstarts.add(pixel);
					if (blobs.size() > 255) { // probably noisy img, avoid heap overflow
						Util.log("error, too many blobs", this);
						return result;
					}
				}
					
			}
		}		
		
		ArrayList<Integer> rejectedBlobs = new ArrayList<Integer>();
		while (rejectedBlobs.size() < blobs.size()) {
			for (blobnum=0; blobnum<blobs.size(); blobnum++) { // go thru and eval each blob
				if (rejectedBlobs.indexOf(blobnum) == -1) {
					r = getRect(blobs.get(blobnum),blobstarts.get(blobnum)); 
					blobSize = r[4];

					minx = r[0];
					maxx = r[1];
					miny = r[2];
					maxy = r[3];  
					blobBox = (maxx-minx)*(maxy-miny);
					topRatio =  (float) getPixelEqTrueCount(blobs.get(blobnum), minx, (int) (minx+(maxx-minx)*0.333), miny, maxy) / (float) blobBox; 
					midRatio = (float) getPixelEqTrueCount(blobs.get(blobnum), (int) (minx+(maxx-minx)*0.333),
							(int) (minx+(maxx-minx)*0.666), miny, maxy) / (float) blobBox;
					bottomRatio = (float) getPixelEqTrueCount(blobs.get(blobnum), (int) (minx+(maxx-minx)*0.666), 
							maxx, miny, maxy) / (float) blobBox;
					float blobRatio = (float) (maxx-minx)/(float)(maxy-miny);
					diff = Math.abs(topRatio - lastTopRatio) + Math.abs(bottomRatio- lastBottomRatio) + Math.abs(midRatio- lastMidRatio);
					if (diff < maxdiff && blobRatio <= lastBlobRatio*1.1) {
						winner=blobnum;
						maxdiff = diff;
						winRect = r.clone();
					}

				}
			}
			if (winner == -1) { break; }
			else { // best looking blob chosen, now check if it has ctr blob AND bottom slope extents wider than rest 
				minx = winRect[0];
				maxx = winRect[1];
				miny = winRect[2];
				maxy = winRect[3];
				int ctrx = minx+((maxx-minx)/2);
				int ctry = miny+((maxy-miny)/2);
				i = ctrx + ctry*width;  // dead center of winner blob
				if (parrinv[i]==1) { // if ctr blob start exists
					Boolean[] ctrblob = floodFill(parrinv,i);
					r = getRect(ctrblob,i);
					if (minx<r[0] && maxx>r[1] && miny<r[2] && maxy>r[3] && r[4] > 10 && r[4]<winRect[4]*0.5 && r[4]>winRect[4]*0.2 ) { // ctrblob completely within blob
						float[] sl = getBottomSlope(blobs.get(winner),minx,maxx,miny,maxy);
						slope = sl[0];
						if (sl[1]<=minx*0.9 || sl[2]>=maxx*0.9) { // bottom slope is widest on at least one side
							break;
						} // else { Util.debug("failed slope test",this); sendToImage(blobs.get(winner));  }
					}
				}

				rejectedBlobs.add(winner);
				winner = -1;


			}
		}

		if (winner != -1) {

			result = new String[]{Integer.toString(minx),Integer.toString(miny),Integer.toString(maxx-minx),
					Integer.toString(maxy-miny),Float.toString(slope)}; //x,y,width,height,slope
	
			blobSize = winRect[4];		
			int runningttl = 0;
			for (pixel=0; pixel<width*height; pixel++) { // zero to end
				if (blobs.get(winner)[pixel]) {
					runningttl += parrorig[pixel];
				}
			}
			lastThreshhold = (int) ((runningttl/blobSize)*threshholdMult); // adaptive threshhold
			
//			sendToImage(blobs.get(winner)); // testing
		}
		else {
//			sendToImage(parrorig);  // testing
		}
		return result;
	}
	
	private int[] getRect(Boolean[] blob, int start) {
		int y = start/width;
		int x = start - (y*width); 	
		int minx = x;
		int miny = y;
		int maxx = x;
		int maxy = y;
		int p;
		int tempy; 
		int tempx;
		int size = 0;
		for (p=0; p<width*height; p++) {
			if (blob[p]) {
				tempy = p/width;
				tempx = p - (tempy*width); 
				if (tempx < minx) { minx = tempx; }
				if (tempx > maxx) { maxx = tempx; }
				if (tempy < miny) { miny = tempy; }
				if (tempy > maxy) { maxy = tempy; }
				size++;
			}
		}
		int[] result = {minx,maxx,miny,maxy,size};
		return result;
	}
	
	private int getPixelEqTrueCount(Boolean[] blob, int startx, int endx, int starty, int endy) {
		int result = 0;
		for (int yy = starty; yy<endy; yy++) {
			for (int xx = startx; xx<=endx; xx++) {
				if (blob[yy*width + xx]==true) {
					result++;
				}
			}
		}
//		Util.debug("result: "+result, this);
		return result;
	}
	
	private float[] getBottomSlope(Boolean[] blob, int minx, int maxx, int miny, int maxy) {
		int start = -1;
		for (int i = maxx+maxy*width; i>=minx+miny*width; i-=1) {
			if (blob[i]) {
				start = i;
				break;
			}
		}
		int starty = start/width;
		int startx = start-(starty*width);
		int direction = 1;
		if (startx > minx+(maxx-minx)/2) { direction = -1; }
		if (direction == -1) {
			while (blob[start+1]) { start ++; }
		}
		else {
			while (blob[start-1]) { start -= 1; }
		}
		int end = start;
		while (blob[end + direction] || blob[end-width+direction]) { //crawl up diagonally or flat until hit vert wall
			end += direction;
			if (!blob[end]) { end -= width; }
		}
		int endy = end/width;
		int endx = end-(endy*width);
		float rightx = endx;
		float leftx = startx;
		if (direction == -1)  { rightx = startx; leftx = endx; }
		float[] result = new float[]{(float) (endy-starty)/(float)(endx-startx), leftx, rightx };
		return result;		
	}


}