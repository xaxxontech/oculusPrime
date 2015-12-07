package developer;


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

    public static void main(String[] args) {
//        new Scratch().regexp();

        double n = 100;
        double volts = 11; // result upcomp = 114.9
        final double nominalvolts = 12.0;
        final double exponent = 1.6;

        n = n * Math.pow(nominalvolts/volts, exponent);

        System.out.println(n);

        n=n/Math.pow(nominalvolts/volts, exponent);
        System.out.println(n);


    }
}

