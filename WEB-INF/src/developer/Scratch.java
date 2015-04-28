package developer;

import oculusPrime.Util;

public class Scratch {

    public static void main(String[] args) {


            byte[] b = new byte[] {(byte) 155, (byte) 200, 13};
        String str = "";
        for (int i = 0; i < b.length; i++) str += String.valueOf((int) b[i] );

        System.out.println(str);




    }
}

