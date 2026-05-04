package com.example.prepandchill;

import android.content.Intent;                    // For launching FocusActivity
import android.os.Bundle;                          // For savedInstanceState in onCreate
import android.view.View;                          // Base class for UI components
import android.widget.ImageView;                   // Back button icon
import android.widget.LinearLayout;                // Container for date chips and session cards
import android.widget.TextView;                    // Text display
import androidx.appcompat.app.AppCompatActivity;   // Base activity with backward-compat features

import com.android.volley.Request;                 // HTTP method constants (GET)
import com.android.volley.RequestQueue;            // Queue managing network requests
import com.android.volley.toolbox.JsonArrayRequest; // GET request expecting JSON array response
import com.android.volley.toolbox.Volley;           // Factory to create RequestQueue
import com.google.android.material.button.MaterialButton; // Material Design button
import com.google.firebase.auth.FirebaseAuth;      // Firebase authentication manager
import com.google.firebase.auth.FirebaseUser;      // Currently signed-in user

import org.json.JSONArray;                         // For parsing plan JSON array
import org.json.JSONObject;                        // For parsing individual plan items

import java.text.SimpleDateFormat;                 // For formatting dates ("EEE", "d", "hh:mm a")
import java.util.ArrayList;                        // Dynamic array
import java.util.Calendar;                         // Date/time calculations
import java.util.Date;                             // Date objects for formatting
import java.util.List;                             // Interface for topic list
import java.util.Locale;                           // For locale-safe date formatting

/**
 * TimetableActivity — Displays a day-by-day study schedule.
 *
 * Shows:
 * - 7 date chips (today + next 6 days) — tap to switch day
 * - Session cards for each subject from the generated plan
 * - Time ranges (e.g., "09:00 AM - 11:30 AM")
 * - 2 focus topics fetched from backend (pending/uncompleted ones)
 * - "Start Pomodoro" button that launches FocusActivity
 *
 * Flow: HomeActivity → [this screen] → FocusActivity
 */
public class TimetableActivity extends AppCompatActivity {

    // ========================
    // FIELD VARIABLES
    // ========================

    private LinearLayout llDateSelector;     // Container for the 7 date chips (horizontal row)
    private LinearLayout llUpcomingSessions; // Container where session cards are dynamically added
    private int selectedOffset = 0;          // Which day is selected (0 = today, 1 = tomorrow, etc.)

    private RequestQueue queue;              // Volley network queue for API calls
    private String firebaseUid;             // Current user's Firebase UID
    private final String BASE_IP = "10.7.28.203";
    private final String SUBJECTS_BASE_URL = "http://" + BASE_IP + ":3000/api/subjects";

    private String planJson;                // The study plan JSON string (from SharedPreferences)

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
        
        setContentView(R.layout.activity_timetable);

        // Back button → close this screen and return to HomeActivity
        ImageView btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // Find container views
        llDateSelector = findViewById(R.id.llDateSelector);       // Row of date chips
        llUpcomingSessions = findViewById(R.id.llUpcomingSessions); // Where session cards go

        // Initialize Firebase + Volley
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        firebaseUid = (user != null) ? user.getUid() : null;
        queue = Volley.newRequestQueue(this);

        // If plan JSON was passed via Intent, save it to SharedPreferences
        // This ensures the plan is always available locally
        Intent intent = getIntent();
        String fromIntent = intent != null ? intent.getStringExtra("generatedPlanJson") : null;
        if (fromIntent != null && !fromIntent.trim().isEmpty()) {
            try {
                new JSONArray(fromIntent);                  // Validate it's valid JSON
                PlanPrefs.savePlanJson(this, fromIntent);    // Persist to SharedPreferences
            } catch (Exception ignored) {
            }
        }
        // Always read from SharedPreferences (single source of truth)
        planJson = PlanPrefs.readPlanJson(this);

        // Build the UI
        bindDateChips();                  // Create 7 date chips (today + 6 days)
        renderSessionsForSelectedDay();   // Show sessions for today (offset 0)
    }

    // =====================================================================
    // bindDateChips() — Creates 7 clickable date chips (one per day)
    // Each chip shows day-of-week (MON) and day number (15).
    // Tapping a chip selects that day and re-renders sessions.
    // =====================================================================
    private void bindDateChips() {
        if (llDateSelector == null) return;
        llDateSelector.removeAllViews(); // Clear any previous chips

        for (int offset = 0; offset < 7; offset++) {
            // Inflate a single chip view from the item_date_chip XML layout
            View chip = getLayoutInflater().inflate(R.layout.item_date_chip, llDateSelector, false);
            TextView tvDow = chip.findViewById(R.id.tvDow);   // Day of week text ("MON")
            TextView tvDay = chip.findViewById(R.id.tvDay);   // Day number text ("15")
            View vDot = chip.findViewById(R.id.vDot);         // Orange dot (shown when selected)

            // Calculate the date for this offset using Calendar
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_YEAR, offset); // 0 = today, 1 = tomorrow, etc.
            Date d = c.getTime();

            // Format day-of-week and day number
            String dow = new SimpleDateFormat("EEE", Locale.getDefault()).format(d).toUpperCase(Locale.getDefault());
            // "EEE" → "MON", "TUE", "WED", etc.
            String dayNum = new SimpleDateFormat("d", Locale.getDefault()).format(d);
            // "d" → "15", "16", "17", etc.
            tvDow.setText(dow);
            tvDay.setText(dayNum);

            // Apply visual state: highlighted if selected, gray if not
            boolean selected = (offset == selectedOffset);
            applyChipSelectedState(chip, selected, vDot, tvDow, tvDay);

            // Click handler: select this day, update all chips, re-render sessions
            int finalOffset = offset; // Must be final/effectively-final for lambda
            chip.setOnClickListener(v -> {
                selectedOffset = finalOffset;
                // Loop through ALL chips to update their visual state
                for (int i = 0; i < llDateSelector.getChildCount(); i++) {
                    View child = llDateSelector.getChildAt(i);
                    TextView cdow = child.findViewById(R.id.tvDow);
                    TextView cday = child.findViewById(R.id.tvDay);
                    View cdot = child.findViewById(R.id.vDot);
                    applyChipSelectedState(child, i == selectedOffset, cdot, cdow, cday);
                }
                renderSessionsForSelectedDay(); // Refresh session cards for new day
            });

            llDateSelector.addView(chip);
        }
    }

    // =====================================================================
    // applyChipSelectedState() — Toggles visual state of a date chip
    // Selected: orange text, larger font, dot visible, highlighted background
    // Unselected: gray text, normal font, dot hidden, transparent background
    // =====================================================================
    private void applyChipSelectedState(View chip, boolean selected, View dot, TextView tvDow, TextView tvDay) {
        if (selected) {
            chip.setBackgroundResource(R.drawable.bg_date_selected);    // Highlighted background
            if (dot != null) dot.setVisibility(View.VISIBLE);           // Show orange dot
            if (tvDow != null) tvDow.setTextColor(getResources().getColor(R.color.accent_orange));
            if (tvDay != null) {
                tvDay.setTextColor(getResources().getColor(R.color.accent_orange));
                tvDay.setTextSize(18); // Larger text for selected day
            }
        } else {
            chip.setBackgroundColor(android.graphics.Color.TRANSPARENT); // No background
            if (dot != null) dot.setVisibility(View.GONE);              // Hide dot
            if (tvDow != null) tvDow.setTextColor(getResources().getColor(R.color.text_gray));
            if (tvDay != null) {
                tvDay.setTextColor(getResources().getColor(R.color.text_gray));
                tvDay.setTextSize(16); // Normal text for unselected
            }
        }
    }

    // =====================================================================
    // renderSessionsForSelectedDay() — CORE METHOD
    // Reads the plan JSON and creates one session card per subject.
    // Each card shows: subject name, time range, progress, 2 focus topics,
    // and a "Start Pomodoro" button.
    // =====================================================================
    private void renderSessionsForSelectedDay() {
        if (llUpcomingSessions == null) return;
        llUpcomingSessions.removeAllViews(); // Clear previous session cards

        // Parse plan JSON string into a list of JSONObjects
        ArrayList<JSONObject> planItems = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(planJson != null ? planJson : "[]");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) planItems.add(o);
            }
        } catch (Exception ignored) {
        }

        // If no plan exists, show placeholder message
        if (planItems.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("No study plan yet. Generate your plan first.");
            tv.setTextColor(getResources().getColor(R.color.text_gray));
            tv.setPadding(8, 16, 8, 16);
            llUpcomingSessions.addView(tv);
            return;
        }

        // Calculate session start time based on selected day
        // Day 0 → 9:00 AM, Day 1 → 10:00 AM, Day 2 → 11:00 AM, Day 3+ → 12:00 PM
        Calendar start = Calendar.getInstance();
        start.set(Calendar.HOUR_OF_DAY, 9 + Math.min(3, selectedOffset));
        start.set(Calendar.MINUTE, 0);

        // Create one card per subject in the plan
        for (JSONObject item : planItems) {
            String subject = item.optString("subject", "Subject");   // e.g., "DBMS"
            int subjectId = item.optInt("subject_id", -1);
            int minutes = item.optInt("time_minutes", 60);            // Allocated study time
            int spanMinutes = item.optInt("session_span_minutes", minutes);
            String progress = item.optString("progress", "");

            // Extract pomodoro summary if available
            JSONObject pomodoro = item.optJSONObject("pomodoro");
            String pomSummary = pomodoro != null ? pomodoro.optString("summary", "").trim() : "";

            // Calculate end time = start + session duration (minimum 25 min)
            Calendar end = (Calendar) start.clone();
            end.add(Calendar.MINUTE, Math.max(25, spanMinutes));

            // Format time range: "09:00 AM - 11:30 AM"
            String timeRange = formatTime(start.getTime()) + " - " + formatTime(end.getTime());

            // Inflate session card from XML layout
            View card = getLayoutInflater().inflate(R.layout.item_timetable_session, llUpcomingSessions, false);
            TextView tvTitle = card.findViewById(R.id.tvSessionTitle);   // Subject name
            TextView tvTime = card.findViewById(R.id.tvSessionTime);     // Time range
            TextView tvMeta = card.findViewById(R.id.tvSessionMeta);     // Progress / pomodoro info
            TextView tvTopic1 = card.findViewById(R.id.tvTopic1);        // First focus topic
            TextView tvTopic2 = card.findViewById(R.id.tvTopic2);        // Second focus topic
            MaterialButton btnStart = card.findViewById(R.id.btnStartPomodoro); // Start button

            tvTitle.setText(subject);
            tvTime.setText(timeRange);

            // Build metadata line: progress info + pomodoro summary
            String metaLine = progress != null && !progress.isEmpty() ? progress : ("Planned: " + PlanParser.formatMinutes(minutes));
            if (!pomSummary.isEmpty()) {
                metaLine += (metaLine.isEmpty() ? "" : " · ") + pomSummary;
            }
            tvMeta.setText(metaLine);

            // Fetch 2 pending (uncompleted) topics from backend for this subject
            // topicList is populated asynchronously — the button click reads it later
            final List<JSONObject> topicList = new ArrayList<>();
            fetchTwoFocusTopics(subject, tvTopic1, tvTopic2, topicList);

            // "Start Pomodoro" → launch FocusActivity with session data
            btnStart.setOnClickListener(v -> {
                Intent focusIntent = new Intent(TimetableActivity.this, FocusActivity.class);
                focusIntent.putExtra("subject_name", subject);
                focusIntent.putExtra("subject_id", subjectId);
                focusIntent.putExtra("total_minutes", minutes);

                if (!topicList.isEmpty()) {
                    // Use the first pending topic
                    JSONObject topTopic = topicList.get(0);
                    focusIntent.putExtra("topic_name", topTopic.optString("topic_name"));
                    focusIntent.putExtra("topic_id", topTopic.optInt("id"));

                    // If user paused a previous session, pass saved remaining time
                    // so FocusActivity can RESUME instead of starting fresh
                    if (!topTopic.isNull("remaining_seconds")) {
                        focusIntent.putExtra("remaining_seconds", topTopic.optInt("remaining_seconds"));
                    }
                } else {
                    // No topics available — use generic session
                    focusIntent.putExtra("topic_name", "General Study");
                    focusIntent.putExtra("topic_id", -1);
                }
                startActivity(focusIntent);
            });

            llUpcomingSessions.addView(card); // Add card to the session list
            start = end; // Next session starts where this one ended (sequential scheduling)
        }
    }

    // =====================================================================
    // formatTime() — Formats a Date into "hh:mm a" format
    // e.g., 9:00 AM → "09:00 AM", 2:30 PM → "02:30 PM"
    // =====================================================================
    private String formatTime(Date date) {
        return new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date);
    }

    // =====================================================================
    // fetchTwoFocusTopics() — GET request to load pending topics for a subject
    // Filters out completed topics and shows first 2 uncompleted ones on the card.
    // Also populates outList (passed by reference) so the Start button can use it.
    // =====================================================================
    private void fetchTwoFocusTopics(String subjectName, TextView tv1, TextView tv2, List<JSONObject> outList) {
        if (queue == null || firebaseUid == null || subjectName == null) {
            tv1.setText("• Add syllabus topics for this subject.");
            tv2.setText("• ");
            return;
        }

        // GET /api/subjects/topics?name=DBMS&firebase_uid=abc123
        String url = SUBJECTS_BASE_URL + "/topics?name=" + android.net.Uri.encode(subjectName) +
                "&firebase_uid=" + android.net.Uri.encode(firebaseUid);

        JsonArrayRequest request = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    outList.clear();
                    for (int i = 0; i < response.length(); i++) {
                        JSONObject o = response.optJSONObject(i);
                        if (o == null) continue;
                        boolean done = o.optInt("is_completed", 0) == 1;
                        if (done) continue;    // Skip completed topics
                        outList.add(o);        // Keep only PENDING topics
                    }

                    // Display first 2 pending topics on the session card
                    if (outList.isEmpty()) {
                        tv1.setText("• No pending topics found.");
                        tv2.setText("• ");
                    } else {
                        tv1.setText("• " + outList.get(0).optString("topic_name"));
                        if (outList.size() > 1) {
                            tv2.setText("• " + outList.get(1).optString("topic_name"));
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
}
