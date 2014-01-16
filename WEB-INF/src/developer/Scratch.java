package developer;

import java.util.ArrayList;
import java.util.List;

import developer.depth.ScanUtils;


public class Scratch {

        public static void main(String[] args) {
  
//        	List<List<Byte>> map = new ArrayList<List<Byte>>();
//        	int[][] map = new int[][] {
//        			{1,2,3},
//        			{4,5,6},
//        			{7,8,9}
//        	};	
//        	int[][] temp = map;
    		int distance = (int) (1000 * ((double) 334/3500)); // scaled
			int dx = -(int) (Math.sin(Math.toRadians(-45.0))*distance*1.0); 

			System.out.println(distance);
        	System.out.println(dx);
	
			
        }
}
