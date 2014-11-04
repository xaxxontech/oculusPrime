package developer;


public class Scratch {

        public static void main(String[] args) {

        	String result = "false";
        	String name = "/dev/ttyUSB1";
			if (name.matches("/dev/ttyUSB.+"))  result = "true";
			System.out.println(result);    		

        }
}
