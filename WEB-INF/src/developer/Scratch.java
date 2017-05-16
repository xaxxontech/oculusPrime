package developer;


import oculusPrime.Util;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Scratch {

    public void regexp() {
        Pattern quality = Pattern.compile("^\\s*asdf asdf");
        Matcher mat;
        mat = quality.matcher("fdsaf"); //    /^\s*Quality=/
        if (mat.find()) {
            mat.find();
        }

        System.out.println("192.168.0.107".substring(0,4));

        if ("Gableem".matches(".*ei.*"))
            System.out.println("match");

        System.out.println("ok here we go");
        System.out.println("192.168.0.107".replaceFirst("\\.\\d+\\.\\d+$", ""));
    }

    public void zork(Object c) {

        System.out.println(c);
        if (c instanceof String)
              System.out.println(c.toString());
    }

    public static void main(String[] args) {
//        new Scratch().regexp();

        long r = 0;
        long n= 1;
        r = n = System.currentTimeMillis();

//        new Scratch().zork(null);

        System.out.println("r: "+r+", n: "+n);

    }
}

