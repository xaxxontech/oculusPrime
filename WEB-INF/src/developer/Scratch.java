package developer;

import java.util.ArrayList;
import java.util.List;

import oculusPrime.State;
import oculusPrime.commport.ArduinoPrime.direction;


public class Scratch {

        public static void main(String[] args) {

        	System.out.println("asdfasdf");
			new Thread(new Runnable() {public void run() {
				long stopwaiting = System.currentTimeMillis()+1000;
				while(System.currentTimeMillis() < stopwaiting) {} // wait
				System.out.println("asdf");
				
			} }).start();
    		

        }
}
