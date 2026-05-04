package com.example.prepandchill;

import android.app.Activity;
import android.content.Intent;

public final class BottomNavNavigator {
    private BottomNavNavigator() {}

    public static void open(Activity activity, Class<? extends Activity> target) {
        if (activity.getClass().equals(target)) return; //if im on same screen , doesnt refresh the page

        Intent intent = new Intent(activity, target);
        // Keeps back behavior natural and avoids duplicate stacks.
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(intent);
    } //bRINGS ON TOP OF STACK , IF ALREADY AT TOP DONT START AGAIN
}

