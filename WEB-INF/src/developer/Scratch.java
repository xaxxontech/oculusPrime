package developer;

import oculusPrime.State;
import oculusPrime.Util;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class Scratch {


    public long[] readProcStat() {
        try {

            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/stat")));
            String line = reader.readLine();
            reader.close();

            String[] values = line.split("\\s+");

            long total = 0;
            for (int i=1;i<=4;i++)
                total += Long.valueOf(values[i]);
            long idle = Long.valueOf(values[4]);
            return new long[] { total, idle};

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

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

        Util.delay(2000);

        Scratch scratch = new Scratch();
        long[] procStat = scratch.readProcStat();
        long totproc1st = procStat[0];
        long totidle1st = procStat[1];
        Util.delay(100);
        procStat = scratch.readProcStat();
        long totproc2nd = procStat[0];
        long totidle2nd = procStat[1];
        int percent = (int) ((double) ((totproc2nd-totproc1st) - (totidle2nd - totidle1st))/ (double) (totproc2nd-totproc1st) * 100);
        System.out.println(percent);

        Util.delay(2000);

        System.out.println(Util.getCPUTop());

    }
}

