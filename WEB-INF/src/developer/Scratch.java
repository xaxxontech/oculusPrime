package developer;


public class Scratch {

        public static void main(String[] args) {
        	
        	int cwidth = 2591;
        	int cheight = 3419;
        	double angle = 24.8679;
        	angle = angle *Math.PI/180;
        	int x = 0;
        	int y = 1727;
        	
			// WRONG: 
//        	int newX = (int) Math.round( Math.cos(angle+Math.atan((double)y/x)) *  
//					(y/Math.sin(Math.atan((double)y/x))) + Math.sin(angle)*(cheight-y)  );

        	//WORKS
        	// newX = x/cos a + sin a * (h-(sin a * (x/cos a))-y)
        	int newX = (int) Math.round( x/Math.cos(angle) + Math.sin(angle) * 
        			(cheight-(Math.sin(angle)*(x/Math.cos(angle)))-y) );
        	
        	
			// WORKS:
			int newY = (int) Math.round( Math.sin(angle+Math.atan((double)y/x)) *  
					(y/Math.sin(Math.atan((double)y/x))) );
			
			x=0;
			System.out.println("x1y1: " +x+", "+y+"   x2y2:" +newX+", "+newY);
//			System.out.println(Math.atan(1/x));
			// answer >> x2y2: 1493, 1930
			
        }
}
