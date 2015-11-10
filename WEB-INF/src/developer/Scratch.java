package developer;


import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Scratch {



    public static void main(String[] args) {

        Pattern quality = Pattern.compile("^\\s*asdf asdf");
        Matcher mat;
        mat = quality.matcher("fdsaf"); //    /^\s*Quality=/
        if (mat.find()) {
            mat.find();
        }

        System.out.println("update required");

        if ("Gableem".matches(".*ei.*"))
            System.out.println("match");

    }
}

