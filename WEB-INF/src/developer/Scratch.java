package developer;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import oculusPrime.Util;

public class Scratch {

        public static void main(String[] args) {

    		File lockfile = new File("C://temp//test.txt");

    		try {
				lockfile.createNewFile();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		lockfile.delete();
        }

}
