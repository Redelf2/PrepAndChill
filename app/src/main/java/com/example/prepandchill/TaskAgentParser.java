package com.example.prepandchill;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class TaskAgentParser {

    public static TaskAgentResponse parseResponse(String rawResponse) throws JSONException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw new JSONException("Empty response");
        }

        JSONObject root = parseToJson(rawResponse.trim());

        if (root.has("generated_text")) {
            return parseResponse(root.optString("generated_text"));
        }

        String action = root.optString("action", "");
        List<TaskItem> tasks = new ArrayList<>();

        JSONArray taskArray = root.optJSONArray("tasks");
        if (taskArray != null) {
            for (int i = 0; i < taskArray.length(); i++) {
                JSONObject item = taskArray.optJSONObject(i);
                if (item == null) {
                    continue;
                }

                String title = item.optString("title", "").trim();
                String duration = item.optString("duration", "").trim();
                if (!title.isEmpty() && !duration.isEmpty()) {
                    tasks.add(new TaskItem(title, duration));
                }
            }
        }

        return new TaskAgentResponse(action, tasks);
    }

    private static JSONObject parseToJson(String text) throws JSONException {
        if (text.startsWith("{")) {
            return new JSONObject(text);
        }

        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start == -1 || end == -1 || end <= start) {
            throw new JSONException("No JSON object found in response");
        }

        String candidate = text.substring(start, end + 1);
        return new JSONObject(candidate);
    }
}
