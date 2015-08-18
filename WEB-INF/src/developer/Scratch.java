package developer;

import oculusPrime.State;
import oculusPrime.Util;

import javax.servlet.http.Part;
import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class Scratch {



    public static void main(String[] args) {
        double v = 1;

        String response = "version:0.127";
        String versionstr = response.substring(response.indexOf("version:") + 8, response.length());
        double version = Double.valueOf(versionstr);

        System.out.println(version);
        if (version < v) System.out.println("update required");

    }
}

