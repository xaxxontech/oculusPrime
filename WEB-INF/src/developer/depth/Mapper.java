package developer.depth;

import java.util.ArrayList;
import java.util.List;


public class Mapper {

//	public static List<List<Byte>> map = new ArrayList<List<Byte>>();
	public static short[][] map = new short[0][0];
	public static List<short[]> move =  new ArrayList<short[]>();
	public static int startX = 0; // bot starting point within map
	public static int startY = 0; 
	private static int botX = 0; // position of bot within map
	private static int botY = 0; 
	private static double lastAngle = 0;
	private static int originX; // position of bot within current cell matrix
	private static int originY;
	private static int cw; // cells width
	private static int ch; // cells height
	private static int dx; // change in bot position within map (before shift)
	private static int dy; 
	private static int cornerX =0; 
	private static int cornerY =0;
	private static final int maxProb = 220;
	public static final int mapSingleHeight = 240;
	
	public static void clearMap() {
		map = new short[0][0];
		startX = 0; // bot starting point within map
		startY = 0; 
		botX = 0; // position of bot within map
		botY = 0; 
		lastAngle = 0;
	}
	
	/**
	 * 
	 * @param cells
	 * @param distance in mm
	 * @param angle in degrees
	 */
	public static void addArcPath(final short[][] cells, int distance, final double angle) {
//    	double arcPathX = Math.cos(Math.toRadians((180-angle)/2))*distance;
		short[][] newcells;
		if (distance != 0) {
	    	int arcPathY = (int) Math.round(Math.sin(Math.toRadians((180-angle)/2))*distance);
	    	newcells = transFormMap(cells, arcPathY, 90-((180-angle)/2)); // arcpath distances, partial rotation
	    	newcells = transFormMap(cells, 0, angle-(90-((180-angle)/2)) ); // remaining rotation
		}
		else newcells = transFormMap(cells, 0, angle);
    	
    	add(newcells);
	}
	
	public static void add(short[][] cells) {
		// prep map for new entry
		if (map.length == 0) { // 1st entry into map, distance must be 0
			map = new short[cw][ch];
			botX = originX;
			botY = originY;
	    	for (int x=0; x<cw; x++) {
				for (int y=0; y<ch; y++) {
					map[x][y] = cells[x][y];
				}
	    	}
	    	map[originX][originY]=-1; // TODO: testing only, highlight bot location
	    	return;
		}
		
		System.out.println("map: "+map.length+", "+map[0].length);
		System.out.println("c: "+cw+", "+ch);
		// all other entries
    	for (int x=0; x<cw; x++) {
			for (int y=0; y<ch; y++) {

//				 write to map if contents are something, and don't overwrite bot locations, higher probability locations
//				if (cells[x][y] != 0 && map[cornerX + x][cornerY + y] >= 0  && cells[x][y] > map[cornerX + x][cornerY + y]) {   
//				map[cornerX + x][cornerY + y] = cells[x][y]; }


//				short entry = cells[x][y];
//				short existing = map[cornerX + x][cornerY + y]; // barfs for some reason
//				if ( (entry > 0 && entry > existing && existing != -1) || (entry < -1 && (entry > existing || existing ==0) && existing != -1)
//						|| entry == -1) {
//					map[cornerX + x][cornerY + y] = entry;
//				}
				

				short entry = cells[x][y];

//				short existing = 0;
				if (cornerX+x < map.length-1 && cornerY+y < map[0].length)  {
					short existing = map[cornerX + x][cornerY + y];
					boolean oktoadd = false;
					if (existing == 0 || entry == -1) oktoadd = true;
					else if (entry > Stereo.objectMin && entry <= Stereo.objectMax) { // object
						if (existing > Stereo.objectMin && existing <= Stereo.objectMax) { // object over object
							if (entry > existing) oktoadd = true;
						}
						else if (existing >= Stereo.nonObjectMin && existing <= Stereo.nonObjectMax) { // object over non object
							if (entry-Stereo.objectMin > existing-Stereo.nonObjectMin) oktoadd = true;
						}
						else if (existing >= Stereo.fovMin && existing <= Stereo.fovMax) oktoadd = true; // non object over fov
					}
					else if (entry >= Stereo.nonObjectMin && entry <= Stereo.nonObjectMax) { // non object
						if (existing > Stereo.objectMin && existing <= Stereo.objectMax) { // non object over object
							if (entry-Stereo.nonObjectMin > existing - Stereo.objectMin + 0) oktoadd = true; 
						}
						else if (existing >= Stereo.nonObjectMin && existing <= Stereo.nonObjectMax) { //non object over non object
							if (entry > existing ) oktoadd = true;
						}
						else if (existing >= Stereo.fovMin && existing <= Stereo.fovMax) oktoadd = true; // non object over fov
					}
					else if (entry >= Stereo.fovMin && entry <= Stereo.fovMax) { // fov cone 
						if (existing >= Stereo.fovMin && existing <= Stereo.fovMax) { // fov cone over fov cone 
							if (entry > existing ) oktoadd = true; 
						}
						else if (existing > Stereo.objectMin && existing <= Stereo.objectMax) { // fov cone over object
							if (entry - Stereo.fovMin > existing - Stereo.objectMin + 100 && Stereo.objectMin + 100 < Stereo.objectMax) oktoadd=true; // blank space overwrite far away object
						}
						else if (existing >= Stereo.nonObjectMin && existing <= Stereo.nonObjectMax) { // fov cone over object
							if (entry - Stereo.fovMin > existing - Stereo.nonObjectMin + 100 && Stereo.nonObjectMin +100<Stereo.nonObjectMax) oktoadd=true; // blank space overwrite far away object
						}
					}
					if (oktoadd) map[cornerX + x][cornerY + y] = entry;
				}
				
//				if ( (entry > 0 && entry > map[cornerX + x][cornerY + y] && 
//						map[cornerX + x][cornerY + y] != -1) || 
//						(entry < -1 && (entry > map[cornerX + x][cornerY + y] || map[cornerX + x][cornerY + y] ==0) &&
//								map[cornerX + x][cornerY + y] != -1)
//						|| entry == -1) {
//					map[cornerX + x][cornerY + y] = entry;
//				}

					// now nuke nearby lower probability points (hopefully due to far distance scan error)
					// 5 pixels = approx 5cm with 240 resolution and 3500 max
//					for (int xx=-5; xx<=5; xx++) {
//						for (int yy=-5; yy<=5; yy++) {
//							if (cornerX + x + xx > 0 && cornerX + x + xx < map.length &&
//									cornerY + y + yy > 0 && cornerY + y +yy <map[0].length) {
//								if (map[cornerX + x + xx][cornerY + y + yy]>0 && 
//										cells[x][y]-map[cornerX + x + xx][cornerY + y + yy] > Math.abs(xx)*1) {
//									map[cornerX + x + xx][cornerY + y + yy] = 0;
//								}
//							}
//						}
//					}
					
//				}
				
			}
    	}
    	map[cornerX+originX][cornerY+originY]=-1; // TODO: testing only, highlight bot location

	}
	
	public static short[][] transFormMap(short[][] cells, int distance, final double angle) {
//		final int lastOriginX = originX;
//		final int lastOriginY = originY;
//		final int lastCW = cw;
//		final int lastCH = ch;
		
		cw = cells.length;
		ch = cells[0].length;
		originX = cw/2;
//		final int scaledCameraSetback = (int) ((double)ScanUtils.cameraSetBack* ch/ScanUtils.maxDepthFPTV);
		final int scaledCameraSetback = (int) ((double)Stereo.cameraSetBack* ch/Stereo.maxDepthTopView);
		originY = ch-1-scaledCameraSetback; 
		if (map.length == 0) return cells;
		
//		distance = (int) (distance * (double) ch/ScanUtils.maxDepthFPTV); // scaled
		distance = (int) (distance * (double) ch/Stereo.maxDepthTopView); // scaled
		
		dx=0;
		dy=-distance;
		
		double newangle =angle + lastAngle;

		if (newangle > 360) newangle -= 360;
		else if (newangle < -360) newangle += 360;

		lastAngle = newangle;

		// hopefully solves negativearraysize error in rotate():
//		if (lastAngle > 360) lastAngle -= 360;
//		else if (lastAngle < 0) lastAngle = 360 - lastAngle;
		if (newangle > 180)  newangle = -360+newangle;
		else if (newangle < -180 ) newangle = 360+newangle;
		
		if (newangle != 0)  {
			if (newangle==90 || newangle==-90 || newangle==180 || newangle==-180) {
				cells = rotateRightAngle(cells, (int) newangle);
			}
			else if (newangle>90) {
				cells = rotateRightAngle(cells, 90);
				cells = rotate(cells, newangle-90);
					dy = (int) (Math.sin(Math.toRadians(newangle-90))*distance); 
					dx = (int) (Math.cos(Math.toRadians(newangle-90))*distance);
			}
			else if (newangle<-90) {
				cells = rotateRightAngle(cells, -90);
				cells = rotate(cells, newangle+90);
					dy = -(int) (Math.sin(Math.toRadians(newangle+90))*distance); 
					dx = -(int) (Math.cos(Math.toRadians(newangle+90))*distance);
			}
			else {
				cells = rotate(cells, newangle);
					dx = (int) (Math.sin(Math.toRadians(newangle))*distance); 
					dy = -(int) (Math.cos(Math.toRadians(newangle))*distance);
			}
		}
		
		cornerX = botX - dx - originX;
		cornerY = botY + dy - originY;

		int mapWidth = map.length;
		int mapHeight = map[0].length;
		int shiftMapX = 0;
		int shiftMapY = 0;

		// shift required
		if (cornerX < 0) { 
			shiftMapX = -cornerX;
			mapWidth = map.length + shiftMapX;
			cornerX = 0;
		}
		if (cornerY < 0) {
			shiftMapY = -cornerY;
			mapHeight = map[0].length + shiftMapY;
			cornerY = 0;
		}
		
		// enlarge required
		if (map.length < cornerX+cw && shiftMapX==0)    mapWidth = cornerX + cw;
		if (map[0].length < cornerY+ch && shiftMapY==0)   mapHeight = cornerY + ch; // + shiftMapY;
		
//			System.out.println("map.length: "+map.length);
//			System.out.println("mapWidth: "+mapWidth);
//			System.out.println("cornerX: "+cornerX);
//			System.out.println("cornerY: "+cornerY);
//			System.out.println("shiftMapX: "+shiftMapX);
//			System.out.println("shiftMapY: "+shiftMapY);
//			System.out.println("cw: "+cw);
//			System.out.println("ch: "+ch);
//			System.out.println("dx: "+dx);
//			System.out.println("dy: "+dy);
//			System.out.println("newangle: "+newangle);
//			System.out.println(" ");
		
		// if map needs enlarging and maybe shifting:
		if (mapWidth > map.length || mapHeight > map[0].length || shiftMapX > 0 || shiftMapY >0 ) { 
			short[][] temp = map;
			map = new short[mapWidth][mapHeight];
	    	for (int x=0; x<temp.length; x++) {
				for (int y=0; y<temp[0].length; y++) {
//						System.out.println("mapsize: "+mapWidth+", "+mapHeight+
//							"   map: "+(x+shiftMapX)+", "+(y+shiftMapY)+"   xy:"+x+", "+y+
//							"   temp: "+temp.length+", "+temp[0].length+
//							"   c: "+cw+", "+ch);
						
					map[x + shiftMapX][y + shiftMapY] = temp[x][y];
				}
	    	}
		}
//			cells[positionX][positionY]=0b01; // TODO: testing only 
		botX = cornerX + originX;
		botY = cornerY + originY;

		return cells;
		
	}
	
	private static short[][] rotate(short[][] cells, double angle) {
		/* derived from
		 * https://docs.google.com/drawings/d/10mfg_A__ToQV5cz6WLc6cfNmeWf6PxCBcmlSnrU8qm8
		 */
		
		int cwidth = cw;
		int cheight = ch;
    	angle = Math.toRadians(angle);
		
    	int newW =  (int) Math.round( Math.cos(angle)*cwidth + Math.abs(Math.sin(angle))*cheight );
    	int newH = (int) Math.round( Math.cos(angle)*cheight + Math.abs(Math.sin(angle))*cwidth ) ;
		
    	short[][] result = new short[0][0];
    	try {
    		result = new short[newW][newH];
    	}
    	catch( Exception e) {  // trying to debug occasional NegativeArraySizeException
    		e.printStackTrace();
    		System.out.println("****ERROR VARS");
    		System.out.println("cwidth: "+cwidth);
    		System.out.println("cheight: "+cheight);
    		System.out.println("newW: "+newW);
    		System.out.println("newH: "+newH);
    		System.out.println("angle: "+Math.toDegrees(angle));
    		System.out.println("lastAngle: "+Math.toDegrees(lastAngle));
    		
    		/*
    		 * DEBUG: Sat Jan 25 10:46:32 PST 2014, oculusPrime.commport.ArduinoGyro, serial in: angle 17.32
****ERROR VARS
cwidth: 240
cheight: 232
newW: -46
newH: -35
angle: 218.06965517241417
lastAngle: 38277.57166213958

DEBUG: Sat Jan 25 10:46:32 PST 2014, oculusPrime.commport.ArduinoGyro, serial in: angle 17.32
****ERROR VARS
cwidth: 240
cheight: 232
newW: -46
newH: -35
angle: 218.06965517241417
lastAngle: 38277.57166213958

cwidth: 240
cheight: 326
newW: -93
newH: -205
angle: -203.15
lastAngle: -37422.738388969716

    		 */
    		
    	}

    	int newX;
    	int newY;
    	int tempOriginX=originX;
    	int tempOriginY=originY;

    	angle = -angle;
    	for (int x=0; x<cwidth; x++) {
			for (int y=0; y<cheight; y++) {
				if (angle>0) {
					newX = (int) Math.round( x/Math.cos(angle) + Math.sin(angle) * 
		        			(cheight-(Math.sin(angle)*(x/Math.cos(angle)))-y) );
					newY = (int) Math.round( Math.sin(angle+Math.atan((double)y/x)) *  
							(y/Math.sin(Math.atan((double)y/x))) );
				}
				else { // negative angle
					newX = newW - (int) Math.round( (cwidth-x-1)/Math.cos(angle) - Math.sin(angle) * 
		        			(cheight+(Math.sin(angle)*((cwidth-x-1)/Math.cos(angle)))-y) );
					newY = (int) Math.round( Math.sin(-angle+Math.atan((double)y/(cwidth-x-1))) *  
							(y/Math.sin(Math.atan((double)y/(cwidth-x-1)))) );
				}
				
				if (newX>=0 && newX<newW && newY>=0 && newY<newH)
					result[newX][newY]= cells[x][y];
				
				if (x==originX && y==originY) {
					tempOriginX = newX;
					tempOriginY = newY;
				}

			}
		}
    	
    	originX=tempOriginX;
    	originY=tempOriginY;

    	cw = newW;
    	ch= newH;
		return result;
	}
	
	private static short[][] rotateRightAngle(short[][] cells, int angle) {
		short[][] result = new short[ch][cw]; // 90 or -90 deg 
		if (angle == 180 || angle==-180) {
			result = new short[cw][ch];
			for (int y=0; y<ch; y++) {
				for (int x=0; x<cw; x++) {
					result[cw-x-1][ch-y-1] = cells[x][y];
				}
			}
			originY = 0;
			originX = cw/2-1;
			dy = -dy;
		}
		else if (angle==90) {
			for (int y=0; y<ch; y++) {
				for (int x=0; x<cw; x++) {
					result[y][cw-x-1] = cells[x][y];
				}
			}
			int temp = cw;
			cw = ch;
			ch = temp;
			originX = cw-1;
			originY = ch/2-1;
			temp = dy;
			dy =0;
			dx=-temp;
		}
		else if (angle==-90) {

			for (int y=0; y<ch; y++) {
				for (int x=0; x<cw; x++) {
					result[y][x] = cells[x][ch-y-1];
				}
			}
			int temp = cw;
			cw = ch;
			ch = temp;
			originX = 0;
			originY = ch/2;
			temp = dy;
			dy =0;
			dx=temp;
		}
		
		return result;
	}

	/**
	 * 
	 * @param frame
	 * @param distance
	 * @param angle
	 * Add move to database of all cummulative moves, incl. distance, angle, depth data
	 */
	public static void addMove(short[] frame, int distance, double angle) { // TODO: convert move to object
		/*	List<short[]> move =  new ArrayList<short[]>();
		 * format: {distance mm, angle, angle, framedata....} 
		*/ 
		short[] a = new short[frame.length + 3];
		a[0] =(short) distance;
    	int aint = (int) (angle * 1000); // 3 decimal precision
    	a[1] = (short) ((aint & 0xffff00) >> 8);
    	a[2] = (short) (aint & 0xff);
    	System.arraycopy(frame, 0, a, 3, frame.length);
		move.add(a);
		
		addArcPath(projectFrameHorizToTopView(frame), distance, angle);
	}
	
	private static double moveAngle(short[] singleMove) {
		int newint = (singleMove[1] << 8) + singleMove[2];
		return (double) newint /1000;
	}
	
	// openni specific
	public static short[][] projectFrameHorizToTopView(short[] frame) {
		final int h = mapSingleHeight; 
		final int w = (int) (Math.sin(Math.toRadians(ScanUtils.camFOVx)/2) * h) * 2;
		final double angle = Math.toRadians(ScanUtils.camFOVx/2);
		
		short[][] result = new short[w][h];

		final int xdctr = w/2;
		
		int y = ScanUtils.height/2; 
		for (int x=0; x<ScanUtils.width; x+=1) { // increment = simulate scan resolution

			int d = frame[y*ScanUtils.width+x];
//				if (d>ScanUtils.maxDepthFPTV) d=0;
			int ry = (int) Math.round((double) d/ ScanUtils.maxDepthFPTV  * h);
			double xdratio = (x*(double) w/ScanUtils.width - xdctr)/ (double) xdctr;
			int rx = (w/2) - (int) Math.round(Math.tan(angle)*(double) ry * xdratio);
			
			if (ry<h && ry>0 && rx>=0 && rx<w) {
				short n =(short) ((double) d/ScanUtils.maxDepthFPTV * maxProb);
				if (d !=0) n=(short) (255-n);
				result[rx][h-ry-1] = n;
			}
		}
		
		
//		private static final int probabilityMax = 255;
//		private static final double depthAccuracyPercent = 0.03;

		
		return result;
	}

	
}
