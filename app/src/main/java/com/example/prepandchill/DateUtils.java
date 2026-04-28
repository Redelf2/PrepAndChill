package com.example.prepandchill;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DateUtils {
    // Returns today's date (e.g., "Monday, Oct 27")
    public static String getTodayDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMM d", Locale.getDefault());
        return sdf.format(new Date());
    }

    // Returns a list of dates for the current week (useful for Timetables)
    public static String getDateForOffset(int daysFromNow) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, daysFromNow);
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, d MMM", Locale.getDefault());
        return sdf.format(calendar.getTime());
    }
}
