package com.example.prepandchill;

import android.content.Context;

/**
 * Stores last home-screen average confidence so we can show a simple delta vs quiz/setup updates.
 */
public final class ConfidencePrefs {
    private static final String PREF = "prep_confidence_snap";
    private static final String KEY_LAST_AVG = "last_avg_confidence";

    private ConfidencePrefs() {}

    public static int getLastAverageConfidence(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(KEY_LAST_AVG, -1);
    }

    public static void setLastAverageConfidence(Context c, int avgPercent) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_LAST_AVG, avgPercent)
                .apply();
    }
}
