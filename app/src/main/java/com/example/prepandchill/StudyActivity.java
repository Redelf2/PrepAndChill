package com.example.prepandchill;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class StudyActivity extends AppCompatActivity {

    private LinearLayout llStudyPlan;
    private RequestQueue queue;
    private String firebaseUid;
    private final String BASE_IP = "10.7.28.203";
    private final String BASE_URL = "http://" + BASE_IP + ":3000/api";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );

        setContentView(R.layout.activity_study);

        ImageView btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        llStudyPlan = findViewById(R.id.llStudyPlan);
        queue = Volley.newRequestQueue(this);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        firebaseUid = (user != null) ? user.getUid() : null;

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });

        renderStudyPlan();

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_study);
            bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    BottomNavNavigator.open(this, HomeActivity.class);
                    return true;
                } else if (id == R.id.nav_study) {
                    return true;
                } else if (id == R.id.nav_analytics) {
                    BottomNavNavigator.open(this, ConfidenceMapActivity.class);
                    return true;
                } else if (id == R.id.nav_focus) {
                    BottomNavNavigator.open(this, FocusActivity.class);
                    return true;
                }
                return false;
            });
        }
    }

    private void renderStudyPlan() {
        if (llStudyPlan == null) return;
        llStudyPlan.removeAllViews();

        String planJson = PlanPrefs.readPlanJson(this);
        JSONArray arr;
        try {
            arr = new JSONArray(planJson != null ? planJson : "[]");
        } catch (Exception e) {
            arr = new JSONArray();
        }

        if (arr.length() == 0) {
            TextView tv = new TextView(this);
            tv.setText("No study plan yet. Generate your plan first.");
            tv.setTextColor(Color.GRAY);
            tv.setPadding(8, 16, 8, 16);
            llStudyPlan.addView(tv);
            return;
        }

        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;

            final String subjectName = o.optString("subject", "Subject");
            final int subjectId = o.optInt("subject_id", -1);
            final int minutes = o.optInt("time_minutes", 25);
            final int spanMinutes = o.optInt("session_span_minutes", minutes);

            JSONObject split = o.optJSONObject("split");
            String splitText = "";
            if (split != null) {
                splitText = "Learn: " + split.optInt("learning_minutes") + "m | Revise: " + split.optInt("revision_minutes") + "m";
            }

            JSONObject pomodoro = o.optJSONObject("pomodoro");
            String pomSummary = pomodoro != null ? pomodoro.optString("summary", "").trim() : "";

            JSONObject insights = o.optJSONObject("insights");
            String insightText = "";
            if (insights != null) {
                insightText = insights.optString("urgency") + " • " + insights.optString("focus");
                String strategy = insights.optString("strategy", "").trim();
                if (!strategy.isEmpty()) {
                    insightText += "\n" + strategy;
                }
                if (!pomSummary.isEmpty()) {
                    insightText += "\n" + pomSummary;
                }
            } else if (!pomSummary.isEmpty()) {
                insightText = pomSummary;
            }
            
            String progress = o.optString("progress", "");

            View row = getLayoutInflater().inflate(R.layout.item_study_plan_row, llStudyPlan, false);
            TextView tvSubject = row.findViewById(R.id.tvSubject);
            TextView tvDuration = row.findViewById(R.id.tvDuration);
            TextView tvProgress = row.findViewById(R.id.tvProgress);
            TextView tvSplit = row.findViewById(R.id.tvSplit);
            TextView tvInsights = row.findViewById(R.id.tvInsights);
            TextView tvNext1 = row.findViewById(R.id.tvNext1);
            TextView tvNext2 = row.findViewById(R.id.tvNext2);
            View btnStart = row.findViewById(R.id.btnStartSession);

            tvSubject.setText(subjectName);
            String durationLine = PlanParser.formatMinutes(minutes) + " focus";
            if (spanMinutes > minutes) {
                durationLine += " · " + PlanParser.formatMinutes(spanMinutes) + " with breaks";
            }
            tvDuration.setText(durationLine);
            tvProgress.setText(progress);
            tvSplit.setText(splitText);
            tvInsights.setText(insightText);

            final List<JSONObject> pendingTopics = new ArrayList<>();
            fetchPendingTopics(subjectName, tvNext1, tvNext2, pendingTopics);

            btnStart.setOnClickListener(v -> {
                if (pendingTopics.isEmpty()) {
                    Toast.makeText(this, "No topics to study!", Toast.LENGTH_SHORT).show();
                } else {
                    showTopicSelectionDialog(subjectId, subjectName, pendingTopics, minutes);
                }
            });

            llStudyPlan.addView(row);
        }
    }

    private void fetchPendingTopics(String subjectName, TextView tv1, TextView tv2, List<JSONObject> topicList) {
        String url = BASE_URL + "/subjects/topics?name=" + android.net.Uri.encode(subjectName) +
                "&firebase_uid=" + android.net.Uri.encode(firebaseUid);

        JsonArrayRequest request = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    topicList.clear();
                    for (int i = 0; i < response.length(); i++) {
                        JSONObject o = response.optJSONObject(i);
                        if (o != null && o.optInt("is_completed", 0) == 0) {
                            topicList.add(o);
                        }
                    }

                    if (topicList.isEmpty()) {
                        tv1.setText("• No pending topics found.");
                        tv2.setText("• ");
                    } else {
                        tv1.setText("• " + topicList.get(0).optString("topic_name"));
                        if (topicList.size() > 1) {
                            tv2.setText("• " + topicList.get(1).optString("topic_name"));
                        } else {
                            tv2.setText("• ");
                        }
                    }
                },
                error -> {
                    tv1.setText("• Couldn’t load syllabus.");
                    tv2.setText("• ");
                }
        );
        queue.add(request);
    }

    private void showTopicSelectionDialog(int subjectId, String subjectName, List<JSONObject> topics, int totalMinutes) {
        // Pick the first topic for simplicity or show a list
        // Inside showTopicSelectionDialog in StudyActivity.java
        JSONObject topic = topics.get(0);
        int topicId = topic.optInt("id");
        String topicName = topic.optString("topic_name");
        int savedRemainingSeconds = topic.optInt("remaining_seconds", -1);

        new AlertDialog.Builder(this)
                .setTitle("Start Pomodoro")
                .setMessage("Studying: " + topicName)
                .setPositiveButton("Start Focus", (dialog, which) -> {
                    Intent intent = new Intent(this, FocusActivity.class);
                    intent.putExtra("subject_id", subjectId);
                    intent.putExtra("topic_id", topicId);
                    intent.putExtra("subject_name", subjectName);
                    intent.putExtra("topic_name", topicName);
                    intent.putExtra("total_minutes", totalMinutes);
                    if (savedRemainingSeconds > 0) {
                        intent.putExtra("remaining_seconds", savedRemainingSeconds);
                    }
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
