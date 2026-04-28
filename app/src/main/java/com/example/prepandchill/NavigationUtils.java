package com.example.prepandchill;

import android.app.Activity;
import android.content.Intent;

import java.util.ArrayList;

public final class NavigationUtils {
    private NavigationUtils() {}

    public static void openConfidenceMap(Activity activity, ArrayList<Subject> selectedSubjects) {
        Intent intent = new Intent(activity, ConfidenceMapActivity.class);
        if (selectedSubjects != null) {
            intent.putExtra("selectedSubjects", selectedSubjects);
        }
        // Add current date so the Activity knows which day it's displaying
        intent.putExtra("displayDate", DateUtils.getTodayDate()); 
        activity.startActivity(intent);
    }
}
