package developer;

import java.util.ArrayList;
import java.util.List;


public class Scratch {

        public static void main(String[] args) {
        	int h=320;
        	double camFOVx169 = 68.46;
//    		final int w = (int) (Math.cos(Math.toRadians(camFOVx169)/2) * h) * 2;
        	final int w = (int) (Math.tan(Math.toRadians(camFOVx169/2)) * h * 2);
    		System.out.println(w );
//    		System.out.println(0x7fff);
        }
}
