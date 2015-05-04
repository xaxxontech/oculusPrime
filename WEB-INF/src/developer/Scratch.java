package developer;

import oculusPrime.Util;

import java.util.Calendar;
import java.util.Date;

public class Scratch {

    public static void main(String[] args) {
//        calendar.setTime(new Date());
//        int hours = calendar.get(Calendar.HOUR_OF_DAY);
//        int minutes = calendar.get(Calendar.MINUTE);
//        int seconds = calendar.get(Calendar.SECOND);
//        int day = calendar.get(Calendar.DAY_OF_WEEK);

        Calendar calendarnow = Calendar.getInstance();
        calendarnow.setTime(new Date());

        Calendar calendar = Calendar.getInstance();
//        public final void set(int year, int month, int date, int hourOfDay, int minute)
//        calendar.set(calendarnow.get(Calendar.YEAR), calendarnow.get(Calendar.MONTH), calendar.get(Calendar.DATE));

        calendar.set(2015, 0, 1);

        Calendar test = Calendar.getInstance();
        test.set(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DATE)-1);



//        Date date = new Date();
        System.out.println(calendar.getTime());
        System.out.println(test.getTime());
//        System.out.println(day);

//        byte b= (int) 13;
//        if (b==13) b=12;
//        System.out.println(b);

    }
}

