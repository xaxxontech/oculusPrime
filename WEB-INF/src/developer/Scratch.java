package developer;

import oculusPrime.State;
import oculusPrime.Util;

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

        List<Integer> numbers = new ArrayList<Integer>();
        numbers.add(4);
        numbers.add(5);
        numbers.add(6);

        numbers.remove(1);
        System.out.println(numbers.size());
        System.out.println(numbers.get(0));
        System.out.println(numbers.get(1));

    }
}

