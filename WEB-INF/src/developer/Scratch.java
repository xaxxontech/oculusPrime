package developer;

import java.util.ArrayList;
import java.util.List;

import developer.depth.ScanUtils;


public class Scratch {

        public static void main(String[] args) {
  
        	int winningX = 20;
        	int width=320;
        	int resX =4;
        	int camFOVx = 58;
    		double angle =  (double) (winningX)/(width/resX) * camFOVx; // -1 cell comp...?
    		
    		
			System.out.println(angle);
//        	System.out.println(dx);
	
			
        }
}
