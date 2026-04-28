package com.example.prepandchill;

import android.content.Context;
import android.content.SharedPreferences;

public final class PlanPrefs {
    private static final String PREFS = "prepandchill_prefs";
    private static final String KEY_PLAN_JSON = "generated_plan_json";

    private PlanPrefs() {}

    public static void savePlanJson(Context context, String json) {
        if (context == null) return;
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_PLAN_JSON, json != null ? json : "[]").apply();
    }

    public static String readPlanJson(Context context) {
        if (context == null) return "[]";
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return sp.getString(KEY_PLAN_JSON, "[]");
    }
}

