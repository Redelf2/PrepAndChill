package com.example.prepandchill;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public final class PlanParser {
    private PlanParser() {}

    public static Map<String, String> subjectToDuration(String planJson) {
        Map<String, String> map = new HashMap<>();
        if (planJson == null || planJson.trim().isEmpty()) return map;

        try {
            JSONArray arr = new JSONArray(planJson);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String subject = o.optString("subject", "").trim();
                int minutes = o.optInt("time_minutes", 0);
                if (subject.isEmpty()) continue;
                map.put(subject, formatMinutes(minutes));
            }
        } catch (Exception ignored) {
        }
        return map;
    }

    public static String formatMinutes(int minutes) {
        if (minutes <= 0) return "—";
        int h = minutes / 60;
        int m = minutes % 60;
        if (h <= 0) return m + "m";
        if (m == 0) return h + "h";
        return h + "h " + m + "m";
    }
}

