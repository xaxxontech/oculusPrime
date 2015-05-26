package developer;

import oculusPrime.State;
import oculusPrime.Util;

import javax.servlet.http.Part;
import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class Scratch {



    public static void main(String[] args) {

//        int[] daynums = new int[] {1,2,3,4,5};
//        int nextday = 99;
//        int daynow = 5;
//        for (int i=0; i<daynums.length; i++) {
//            if (daynow <= daynums[i] && daynums[i] < nextday) nextday = daynums[i];
//        }
//
//        if (nextday == 99 ) nextday = 7-daynow + daynums[0];
//        System.out.println(nextday);


//        int[] daynums = new int[] {2,6};
//        int nextday = 99;
//        int daynow = 7;
//        for (int i=0; i<daynums.length; i++) {
//            if (daynow == daynums[i]) {
//                nextday = i; // break;
//            }
//            if (daynow > daynums[i]) nextday = i+1;
//        }
//
//        if (nextday > daynums.length-1 ) nextday = 0;
//        System.out.println(daynums[nextday]);

/*
        int[] daynums = new int[] {2,3,4,5,6,7};
        int starthour = 2;
        int startmin = 0;
        int routedurationhours = 6;

        Calendar calendarnow = Calendar.getInstance();
//        calendarnow.setTime(new Date());
        calendarnow.set(2015, 4, 25, 23, 50);
        System.out.println(calendarnow.getTime());

        int daynow = calendarnow.get(Calendar.DAY_OF_WEEK); // 1-7 (friday is 6)
        System.out.println("daynow day of week: "+daynow);

        boolean startroute = false;
        int nextdayindex = 99;

        for (int i=0; i<daynums.length; i++) {

            // check if need to start run right away
            if (daynums[i] == daynow -1 || daynums[i] == daynow || (daynums[i]==7 && daynow == 1)) { // yesterday or today
                Calendar testday = Calendar.getInstance();
                if (daynums[i] == daynow -1 || (daynums[i]==7 && daynow == 1)) { // yesterday
                    testday.set(calendarnow.get(Calendar.YEAR), calendarnow.get(Calendar.MONTH),
                            calendarnow.get(Calendar.DATE) - 1, starthour, startmin);
                }
                else { // today
                    testday.set(calendarnow.get(Calendar.YEAR), calendarnow.get(Calendar.MONTH),
                            calendarnow.get(Calendar.DATE), starthour, startmin);
                }
                if (calendarnow.getTimeInMillis() >= testday.getTimeInMillis() && calendarnow.getTimeInMillis() <
                        testday.getTimeInMillis() + (routedurationhours * 60 * 60 * 1000)) {
                    startroute = true;
                    break;
                }

            }

        }

        if (startroute) System.out.println("start");
        else System.out.println("not start");
*/


//        State state = State.getReference();
//        state.set(State.values.navigationroute, "zorkulus");
        NavigationLog navlog = new NavigationLog();
        for (int i=0; i<5; i++) {
            navlog.newItem(NavigationLog.COMPLETEDSTATUS, "hi there", System.currentTimeMillis() - 1000000,
                    "glazeem", "zorkpoint", 5);
            Util.delay(500);
            navlog.newItem(NavigationLog.ALERTSTATUS, "hi there", System.currentTimeMillis() - 1000000,
                    "glazeem", null, 5);
            Util.delay(500);
            navlog.newItem(NavigationLog.ERRORSTATUS, "hi there", System.currentTimeMillis() - 1000000,
                    "glazeem", "zorkpoint", 5);
            Util.delay(500);
            navlog.newItem(NavigationLog.INFOSTATUS, "hi there", System.currentTimeMillis() - 1000000,
                    "glazeem", "zorkpoint", 5);
            Util.delay(500);
        }


//            String s = "*0123456789*";
//        System.out.println(s.substring(s.indexOf("567")));

    }
}

