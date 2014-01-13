package developer.depth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class Mapper {

	public static List<List<Byte>> map = new ArrayList<List<Byte>>();
	public static int startX = 0;
	public static int startY = 0;
	private static int lastDistance = 0;
	private static double lastAngle = 0;
	
	public static void add(byte[][] cells, final int distance, final double angle) {
		int cwidth = cells.length;
		int cheight = cells[0].length;
		cells = rotate(cells, angle);
		for (int x=0; x<cwidth; x++) {
			for (int y=0; y<cheight; y++) {
				
			}
		}
		
		lastDistance = distance;
		lastAngle = angle;
		
	}
	
	public static byte[][] rotate(byte[][] cells, double angle) {
		/* derived from
		 * https://docs.google.com/drawings/d/10mfg_A__ToQV5cz6WLc6cfNmeWf6PxCBcmlSnrU8qm8
		 */
    	angle = Math.toRadians(angle);
		int cwidth = cells.length;
		int cheight = cells[0].length;
		
    	int newW = (int) Math.round( Math.cos(angle)*cwidth + Math.abs(Math.sin(angle))*cheight );
    	int newH = (int) Math.round( Math.cos(angle)*cheight + Math.abs(Math.sin(angle))*cwidth );
		
    	byte[][] result = new byte[newW][newH];

    	// TODO: remove, testing only 
    	for (int xx=0; xx<newW; xx++) {
			for (int yy=0; yy<newH; yy++) {
				result[xx][yy]=(byte) 0b01;
			}
		}

    	int newX;
    	int newY;
    	System.out.println(newW+", "+newH);
    	
    	for (int x=0; x<cwidth; x++) {
			for (int y=1; y<cheight; y++) {
				if (angle>0) {
					newX = (int) Math.round( x/Math.cos(angle) + Math.sin(angle) * 
		        			(cheight-(Math.sin(angle)*(x/Math.cos(angle)))-y) );
					newY = (int) Math.round( Math.sin(angle+Math.atan((double)y/x)) *  
							(y/Math.sin(Math.atan((double)y/x))) );
				}
				else { // negative angle
					newX = newW - (int) Math.round( (cwidth-x)/Math.cos(angle) - Math.sin(angle) * 
		        			(cheight+(Math.sin(angle)*((cwidth-x)/Math.cos(angle)))-y) );
					
					newY = (int) Math.round( Math.sin(-angle+Math.atan((double)y/(cwidth-x))) *  
							(y/Math.sin(Math.atan((double)y/(cwidth-x)))) );
				}
				
//				if (newX>=0 && newX<newW && newY>=0 && newY<newH)
					result[newX][newY]= cells[x][y];

			}
		}
    	
		return result;
		
	}

}
