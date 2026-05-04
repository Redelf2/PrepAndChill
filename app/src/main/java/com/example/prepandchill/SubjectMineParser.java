package com.example.prepandchill;

import org.json.JSONException;
import org.json.JSONObject;

/** Parses rows from {@code GET /api/subjects/mine}. */
public final class SubjectMineParser {
    private SubjectMineParser() {}

    public static Subject fromMineJson(JSONObject o) throws JSONException {
        String name = o.getString("name");
        String exam = o.optString("exam_date", "");
        if (exam == null || exam.isEmpty()) {
            exam = "Set your exam date";
        }
        int sid = o.optInt("id", 0);
        Subject s =
                sid > 0
                        ? new Subject(sid, name, exam, true)
                        : new Subject(name, exam, true);
        s.setProficiency(clampPercent(o.optInt("confidence", 0)));
        int d = o.optInt("difficulty", 2);
        s.setDifficulty(Math.max(1, Math.min(3, d)));
        return s;
    }

    private static int clampPercent(int v) {
        return Math.max(0, Math.min(100, v));
    }
}
