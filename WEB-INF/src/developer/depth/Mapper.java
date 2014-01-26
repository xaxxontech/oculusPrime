package developer.depth;

import java.util.ArrayList;
import java.util.List;


public class Mapper {

//	public static List<List<Byte>> map = new ArrayList<List<Byte>>();
	public static byte[][] map = new byte[0][0];
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
	
	public static void clearMap() {
		map = new byte[0][0];
		startX = 0; // bot starting point within map
		startY = 0; 
		botX = 0; // position of bot within map
		botY = 0; 
		lastAngle = 0;
	}
	
	public static void add(byte[][] cells, int distance, final double angle) {
		final int lastOriginX = originX;
		final int lastOriginY = originY;
//		final int lastCW = cw;
//		final int lastCH = ch;
		
		cw = cells.length;
		ch = cells[0].length;
		originX = cw/2;
		final int scaledCameraSetback = (int) ((double)ScanUtils.cameraSetBack* ch/ScanUtils.maxDepthFPTV);
		originY = ch-1-scaledCameraSetback; //+ (int) (ScanUtils.cameraSetBack * (double) ch/ScanUtils.maxDepthFPTV);
		dx=0; //perfectly straight default
		distance = (int) (distance * (double) ch/ScanUtils.maxDepthFPTV); // scaled
		dy=-distance; //perfectly straight default
		
		double newangle =angle + lastAngle;
		lastAngle = newangle;
		
		if (newangle > 180)  newangle = -360+newangle;
		else if (newangle < -180 ) newangle = 360+newangle;
		
		// TODO: calculate arcpath drift on straight moves before rotate, add result to dx,dy
		// easiest initially: just run this thru as single rotate 1st, then do linear move
		
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
		

//		cells[originX][originY]=0b01; // TODO: testing only 
		int cornerX =0; 
		int cornerY =0;
		
		// prep map for new entry
		if (map.length == 0) { // 1st entry into map, distance must be 0
			map = new byte[cw][ch];
			botX = originX;
			botY = originY;
		}
		else {
			
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
				byte[][] temp = map;
				map = new byte[mapWidth][mapHeight];
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

		}
		

		// add new entry	
    	for (int x=0; x<cw; x++) {
			for (int y=0; y<ch; y++) {
				// write to map if contents are something, and don't overwrite wall or origins
				if (cells[x][y] != 0 && map[cornerX + x][cornerY + y] != 0b10  && map[cornerX + x][cornerY + y] != 0b11) {   
					map[cornerX + x][cornerY + y] = cells[x][y];
				}
			}
    	}
    	map[cornerX+originX][cornerY+originY]=0b10; // TODO: testing only

	}
	
	private static byte[][] rotate(byte[][] cells, double angle) {
		/* derived from
		 * https://docs.google.com/drawings/d/10mfg_A__ToQV5cz6WLc6cfNmeWf6PxCBcmlSnrU8qm8
		 */
		
		int cwidth = cw;
		int cheight = ch;
    	angle = Math.toRadians(angle);
		
    	int newW =  (int) Math.round( Math.cos(angle)*cwidth + Math.abs(Math.sin(angle))*cheight );
    	int newH = (int) Math.round( Math.cos(angle)*cheight + Math.abs(Math.sin(angle))*cwidth ) ;
		
    	byte[][] result = new byte[0][0];
    	try {
    		result = new byte[newW][newH];
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

    		 */
    		
    	}

    	// TODO: remove, testing only 
//    	for (int xx=0; xx<newW; xx++) { for (int yy=0; yy<newH; yy++) {
//				result[xx][yy]=(byte) 0b01;  } }

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
	
	private static byte[][] rotateRightAngle(byte[][] cells, int angle) {
		byte[][] result = new byte[ch][cw]; // 90 or -90 deg 
		if (angle == 180 || angle==-180) {
			result = new byte[cw][ch];
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


}
