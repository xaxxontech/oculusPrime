package developer;

import oculusPrime.Util;

import java.util.Calendar;
import java.util.Date;

public class Scratch {

    public static void main(String[] args) {

        int code = -50;
        String str = "asdf,-50,asdf";
        if (str.matches(".*"+code+"$") ) System.out.println("match");

    }
}

