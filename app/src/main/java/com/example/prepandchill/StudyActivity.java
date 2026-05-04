package com.example.prepandchill;

import android.app.AlertDialog;                   // For showing confirmation dialog before starting focus
import android.content.Intent;                    // For launching FocusActivity
import android.graphics.Color;                     // For Color.GRAY placeholder text
import android.os.Bundle;                          // For savedInstanceState in onCreate
import android.view.View;                          // Base class for UI components
import android.widget.ImageView;                   // Back button icon
import android.widget.TextView;                    // Text display
import android.widget.LinearLayout;                // Container for study plan rows
import android.widget.Toast;                       // Short popup messages

import androidx.activity.OnBackPressedCallback;    // Modern replacement for deprecated onBackPressed()
import androidx.appcompat.app.AppCompatActivity;   // Base activity

import com.android.volley.Request;                 // HTTP method constants
import com.android.volley.RequestQueue;            // Network request queue
import com.android.volley.toolbox.JsonArrayRequest; // GET request expecting JSON array
import com.android.volley.toolbox.Volley;           // Factory for RequestQueue
import com.google.android.material.bottomnavigation.BottomNavigationView; // Material bottom navigation bar
import com.google.firebase.auth.FirebaseAuth;      // Firebase auth manager
import com.google.firebase.auth.FirebaseUser;      // Current user

import org.json.JSONArray;                         // For parsing plan JSON
import org.json.JSONObject;                        // For parsing individual plan items

import java.util.ArrayList;                        // Dynamic array
import java.util.List;                             // Interface for topic list

/**
 * StudyActivity — Shows the FULL STUDY PLAN with detailed info per subject.
 *
 * Each row displays: subject name, total duration, learning/revision split,
 * urgency insights from the planner, 2 pending topics, and a "Start Session" button.
 * The start button shows an AlertDialog confirmation, then launches FocusActivity.
 *
 * Uses BottomNavigationView for switching between Home, Study, Analytics, Focus screens.
 *
 * Flow: HomeActivity (bottom nav) → [this screen] → FocusActivity
 */
public class StudyActivity extends AppCompatActivity {

    // ========================
    // FIELD VARIABLES
    // ========================

    private LinearLayout llStudyPlan;   // Container where study plan rows are dynamically added
    private RequestQueue queue;         // Volley network queue for fetching topics
    private String firebaseUid;        // Current user's Firebase UID
    private final String BASE_IP = "10.7.28.203";
    private final String BASE_URL = "http://" + BASE_IP + ":3000/api";

    // ========================
    // onCreate — ENTRY POINT
    // ========================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Draw content behind status bar for immersive look
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );

        setContentView(R.layout.activity_study);

        // Back button → close this Activity
        ImageView btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // Initialize views + networking
        llStudyPlan = findViewById(R.id.llStudyPlan);
        queue = Volley.newRequestQueue(this);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        firebaseUid = (user != null) ? user.getUid() : null;

        // Handle system back button with modern API (OnBackPressedDispatcher)
        // This replaces the deprecated onBackPressed() method
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish(); // Just close this Activity
            }
        });

        // Build the study plan UI from cached plan JSON
        renderStudyPlan();

        // Setup bottom navigation bar
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_study); // Highlight "Study" tab as active

            bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    // Navigate to HomeActivity using BottomNavNavigator
                    // Uses FLAG_ACTIVITY_REORDER_TO_FRONT + SINGLE_TOP to avoid duplicate Activities
                    BottomNavNavigator.open(this, HomeActivity.class);
                    return true;
                } else if (id == R.id.nav_study) {
                    return true; // Already here — do nothing
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

    // =====================================================================
    // renderStudyPlan() — CORE METHOD
    // Reads plan JSON from SharedPreferences and creates one row per subject.
    // Each row shows: name, duration (with breaks), learning/revision split,
    // insights (urgency, focus, strategy), pending topics, and Start button.
    // =====================================================================
    private void renderStudyPlan() {
        if (llStudyPlan == null) return;
        llStudyPlan.removeAllViews(); // Clear previous rows

        // Read plan from SharedPreferences (saved by HomeActivity or TimetableActivity)
        String planJson = PlanPrefs.readPlanJson(this);
        JSONArray arr;
        try {
            arr = new JSONArray(planJson != null ? planJson : "[]");
        } catch (Exception e) {
            arr = new JSONArray(); // Invalid JSON → treat as empty plan
        }

        // If no plan exists, show placeholder message
        if (arr.length() == 0) {
            TextView tv = new TextView(this);
            tv.setText("No study plan yet. Generate your plan first.");
            tv.setTextColor(Color.GRAY);
            tv.setPadding(8, 16, 8, 16);
            llStudyPlan.addView(tv);
            return;
        }

        // Create one row for each subject in the plan
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;

            final String subjectName = o.optString("subject", "Subject"); // e.g., "DBMS"
            final int subjectId = o.optInt("subject_id", -1);
            final int minutes = o.optInt("time_minutes", 25);             // Pure study time
            final int spanMinutes = o.optInt("session_span_minutes", minutes); // Includes breaks

            // LEARNING/REVISION SPLIT from the smart planner
            // The planner divides time into learning (new content) and revision (review)
            JSONObject split = o.optJSONObject("split");
            String splitText = "";
            if (split != null) {
                splitText = "Learn: " + split.optInt("learning_minutes") + "m | Revise: " + split.optInt("revision_minutes") + "m";
                // e.g., "Learn: 120m | Revise: 60m"
            }

            // POMODORO summary from the planner (e.g., "5 sessions of 25m")
            JSONObject pomodoro = o.optJSONObject("pomodoro");
            String pomSummary = pomodoro != null ? pomodoro.optString("summary", "").trim() : "";

            // INSIGHTS from the smart planner algorithm
            // Contains urgency, focus area, strategy recommendation
            JSONObject insights = o.optJSONObject("insights");
            String insightText = "";
            if (insights != null) {
                insightText = insights.optString("urgency") + " • " + insights.optString("focus");
                // e.g., "15 day(s) to exam • New learning"
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
            
            // Progress text (e.g., "3/10 topics completed")
            String progress = o.optString("progress", "");

            // Inflate a row layout for this subject from XML
            View row = getLayoutInflater().inflate(R.layout.item_study_plan_row, llStudyPlan, false);
            TextView tvSubject = row.findViewById(R.id.tvSubject);     // Subject name
            TextView tvDuration = row.findViewById(R.id.tvDuration);   // Duration text
            TextView tvProgress = row.findViewById(R.id.tvProgress);   // Progress text
            TextView tvSplit = row.findViewById(R.id.tvSplit);         // Learn/Revise split
            TextView tvInsights = row.findViewById(R.id.tvInsights);   // Urgency + strategy
            TextView tvNext1 = row.findViewById(R.id.tvNext1);        // First pending topic
            TextView tvNext2 = row.findViewById(R.id.tvNext2);        // Second pending topic
            View btnStart = row.findViewById(R.id.btnStartSession);   // "Start Session" button

            // Populate the row with data
            tvSubject.setText(subjectName);

            // Build duration line: "2h 30m focus" or "2h 30m focus · 3h with breaks"
            String durationLine = PlanParser.formatMinutes(minutes) + " focus";
            if (spanMinutes > minutes) {
                durationLine += " · " + PlanParser.formatMinutes(spanMinutes) + " with breaks";
            }
            tvDuration.setText(durationLine);
            tvProgress.setText(progress);
            tvSplit.setText(splitText);
            tvInsights.setText(insightText);

            // Fetch pending (uncompleted) topics from backend for this subject
            // pendingTopics list is populated asynchronously — Start button reads it later
            final List<JSONObject> pendingTopics = new ArrayList<>();
            fetchPendingTopics(subjectName, tvNext1, tvNext2, pendingTopics);

            // "Start Session" button → shows confirmation dialog → launches FocusActivity
            btnStart.setOnClickListener(v -> {
                if (pendingTopics.isEmpty()) {
                    Toast.makeText(this, "No topics to study!", Toast.LENGTH_SHORT).show();
                } else {
                    showTopicSelectionDialog(subjectId, subjectName, pendingTopics, minutes);
                }
            });

            llStudyPlan.addView(row); // Add row to the scrollable container
        }
    }

    // =====================================================================
    // fetchPendingTopics() — GET request to load uncompleted topics
    // Same pattern as TimetableActivity's fetchTwoFocusTopics().
    // Filters out completed topics and displays first 2 on the row.
    // Also populates topicList for the Start button to use.
    // =====================================================================
    private void fetchPendingTopics(String subjectName, TextView tv1, TextView tv2, List<JSONObject> topicList) {
        // GET /api/subjects/topics?name=DBMS&firebase_uid=abc123
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
                        // Only keep UNCOMPLETED topics (is_completed == 0)
                        if (o != null && o.optInt("is_completed", 0) == 0) {
                            topicList.add(o);
                        }
                    }

                    // Display first 2 pending topics on the row
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
                    tv1.setText("• Couldn't load syllabus.");
                    tv2.setText("• ");
                }
        );
        queue.add(request);
    }

    // =====================================================================
    // showTopicSelectionDialog() — AlertDialog confirmation before focus
    // Picks the FIRST pending topic, shows a dialog, and on confirm
    // launches FocusActivity with all needed session data.
    // Passes remaining_seconds if user had a paused session.
    // =====================================================================
    private void showTopicSelectionDialog(int subjectId, String subjectName, List<JSONObject> topics, int totalMinutes) {
        // Pick the first pending topic
        JSONObject topic = topics.get(0);
        int topicId = topic.optInt("id");
        String topicName = topic.optString("topic_name");
        int savedRemainingSeconds = topic.optInt("remaining_seconds", -1);
        // ↑ If user paused a previous focus session, this has the leftover time

        // Show confirmation dialog
        new AlertDialog.Builder(this)
                .setTitle("Start Pomodoro")
                .setMessage("Studying: " + topicName)
                .setPositiveButton("Start Focus", (dialog, which) -> {
                    // Launch FocusActivity with full session data
                    Intent intent = new Intent(this, FocusActivity.class);
                    intent.putExtra("subject_id", subjectId);
                    intent.putExtra("topic_id", topicId);
                    intent.putExtra("subject_name", subjectName);
                    intent.putExtra("topic_name", topicName);
                    intent.putExtra("total_minutes", totalMinutes);
                    if (savedRemainingSeconds > 0) {
                        intent.putExtra("remaining_seconds", savedRemainingSeconds);
                        // ↑ FocusActivity will RESUME from this point instead of starting fresh
                    }
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null) // null = just dismiss the dialog
                .show();
    }
}
