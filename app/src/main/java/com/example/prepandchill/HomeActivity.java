package com.example.prepandchill;

import android.content.Intent;                    // For navigating between Activities
import android.graphics.Color;                     // For Color.parseColor() in delta text coloring
import android.net.Uri;                            // For URL-encoding firebase_uid in API calls
import android.os.Bundle;                          // For savedInstanceState in onCreate
import android.util.TypedValue;                    // For converting SP to pixel values for text size
import android.view.Gravity;                       // For centering bar chart labels
import android.view.View;                          // Base class for all UI components
import android.view.ViewGroup;                     // For LayoutParams and iterating child views
import android.widget.EditText;                    // Text input field for AI command
import android.widget.FrameLayout;                 // Container for bar chart bars (stacks children)
import android.widget.LinearLayout;                // Vertical/horizontal layout container
import android.widget.ProgressBar;                 // Circular/horizontal progress indicator
import android.widget.TextView;                    // Displays text
import android.widget.Toast;                       // Short popup message
import androidx.appcompat.app.AppCompatActivity;   // Base activity with backward-compat features
import androidx.core.content.ContextCompat;        // Safe way to get colors from resources
import androidx.recyclerview.widget.LinearLayoutManager; // Lays out RecyclerView items in a line
import androidx.recyclerview.widget.RecyclerView;  // Efficient scrolling list widget
import com.android.volley.Request;                 // HTTP method constants (GET, POST)
import com.android.volley.RequestQueue;            // Queue managing network requests on background threads
import com.android.volley.toolbox.JsonArrayRequest; // HTTP request expecting JSON array [] response
import com.android.volley.toolbox.Volley;           // Factory to create RequestQueue
import com.google.android.material.button.MaterialButton; // Material Design styled button
import com.google.firebase.auth.FirebaseAuth;      // Firebase authentication manager (singleton)
import com.google.firebase.auth.FirebaseUser;      // Represents the currently signed-in user
import org.json.JSONArray;                         // For parsing/building JSON arrays
import org.json.JSONObject;                        // For parsing/building JSON objects

import java.util.ArrayList;                        // Dynamic-size array
import java.util.Locale;                           // For locale-safe string operations
import java.util.Map;                              // Interface for key-value storage

/**
 * HomeActivity — The MAIN DASHBOARD of the app.
 * This is the central hub the user sees after plan generation.
 *
 * Displays:
 * - User's name, exam title, average proficiency %
 * - Horizontal scrollable subject cards with allocated study time
 * - A confidence map mini-chart (programmatic bar graph)
 * - An AI Task Agent (type natural language → HuggingFace AI → task list)
 * - Navigation to Timetable, ExamDetails, ConfidenceMap
 *
 * Flow: SubjectAssessmentActivity → [this screen] → Timetable/Study/Focus/ExamDetails
 */
public class HomeActivity extends AppCompatActivity {

    // ========================
    // FIELD VARIABLES
    // ========================

    private static final String BASE_IP = "10.7.28.203";
    private static final String SUBJECTS_BASE = "http://" + BASE_IP + ":3000/api/subjects";
    // ↑ Backend endpoint for fetching user's enrolled subjects

    private ArrayList<Subject> selectedSubjects;       // All subjects user has enrolled in
    private RecyclerView rvTodayPlan;                  // Horizontal list of subject cards with study time
    private RecyclerView rvTaskAgent;                  // Vertical list of AI-generated tasks
    private HomeSubjectAdapter adapter;                // Adapter for rvTodayPlan (connects Subject data → card UI)
    private TaskAgentAdapter taskAgentAdapter;          // Adapter for rvTaskAgent (connects TaskItem data → row UI)
    private ArrayList<TaskItem> taskAgentTasks;         // Live list of tasks from AI agent
    private EditText etAiCommand;                      // Text input for AI command
    private MaterialButton btnAiSend;                  // "Send" button for AI agent
    private String selectedExamName;                   // e.g., "Semester" — displayed as title
    private Map<String, String> subjectToDuration;     // e.g., {"DBMS": "2h 30m", "Android": "1h 45m"}
    private RequestQueue queue;                        // Volley network request queue

    // ========================
    // onCreate — ENTRY POINT (runs once when Activity is created)
    // ========================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Draw content behind status bar for immersive look
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );

        setContentView(R.layout.activity_home);

        // Initialize Volley network request queue (manages background threading for HTTP calls)
        queue = Volley.newRequestQueue(this);

        // STEP 1: Find all UI elements from the layout
        rvTodayPlan = findViewById(R.id.rvTodayPlan);   // Horizontal RecyclerView for subject cards
        rvTaskAgent = findViewById(R.id.rvTaskAgent);   // Vertical RecyclerView for AI tasks
        etAiCommand = findViewById(R.id.etAiCommand);   // AI command text input
        btnAiSend = findViewById(R.id.btnAiSend);       // AI "Send" button

        // STEP 2: Receive data from the Intent that launched this Activity
        Intent intent = getIntent();
        selectedSubjects = (ArrayList<Subject>) intent.getSerializableExtra("selectedSubjects");
        // ↑ Subjects passed from SubjectAssessmentActivity (via Serializable)
        selectedExamName = intent.getStringExtra("selectedExam");  // e.g., "Semester"
        String generatedPlanJson = intent.getStringExtra("generatedPlanJson"); // Plan JSON from planner

        // Safety: never let selectedSubjects be null
        if (selectedSubjects == null) {
            selectedSubjects = new ArrayList<>();
        }

        // STEP 3: Initialize AI Task Agent (empty list + adapter)
        taskAgentTasks = new ArrayList<>();
        taskAgentAdapter = new TaskAgentAdapter(taskAgentTasks);

        // STEP 4: Populate dashboard with user data (name, proficiency, chart)
        updateDynamicUI();

        // STEP 5: Persist plan JSON to SharedPreferences
        // So TimetableActivity and StudyActivity can read it without receiving via Intent
        if (generatedPlanJson != null && !generatedPlanJson.trim().isEmpty()) {
            try {
                new JSONArray(generatedPlanJson); // Validate it's valid JSON before saving
                PlanPrefs.savePlanJson(this, generatedPlanJson);
            } catch (Exception ignored) {
            }
        }

        // STEP 6: Parse plan JSON into a subject → duration map
        // PlanParser converts "[{subject:'DBMS', time_minutes:150}, ...]" → {"DBMS": "2h 30m"}
        subjectToDuration = PlanParser.subjectToDuration(PlanPrefs.readPlanJson(this));

        // STEP 7: Setup HORIZONTAL RecyclerView for subject cards (Today's Plan)
        adapter = new HomeSubjectAdapter(selectedSubjects, subjectToDuration);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        // ↑ HORIZONTAL = cards scroll left/right instead of up/down
        rvTodayPlan.setLayoutManager(layoutManager);
        rvTodayPlan.setAdapter(adapter);

        // STEP 8: Setup VERTICAL RecyclerView for AI agent tasks
        rvTaskAgent.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        rvTaskAgent.setAdapter(taskAgentAdapter);

        // STEP 9: Wire up the AI Send button
        btnAiSend.setOnClickListener(v -> submitAiCommand());

        // STEP 10: Setup navigation buttons

        // "View All" → Opens TimetableActivity with subjects + plan
        TextView btnViewAll = findViewById(R.id.btnViewAll);
        if (btnViewAll != null) {
            btnViewAll.setOnClickListener(v -> {
                Intent tIntent = new Intent(HomeActivity.this, TimetableActivity.class);
                tIntent.putExtra("selectedSubjects", selectedSubjects);
                tIntent.putExtra("generatedPlanJson", PlanPrefs.readPlanJson(HomeActivity.this));
                startActivity(tIntent);
            });
        }

        // Confidence Map card → ConfidenceMapActivity
        LinearLayout cvConfidenceMap = findViewById(R.id.cvConfidenceMap);
        if (cvConfidenceMap != null) {
            cvConfidenceMap.setOnClickListener(v -> {
                Intent cIntent = new Intent(HomeActivity.this, ConfidenceMapActivity.class);
                cIntent.putExtra("selectedSubjects", selectedSubjects);
                startActivity(cIntent);
            });
        }

        // "View Details" → ExamDetailsActivity with subjects + exam name
        MaterialButton btnViewDetails = findViewById(R.id.btnViewDetails);
        if (btnViewDetails != null) {
            btnViewDetails.setOnClickListener(v -> {
                Intent dIntent = new Intent(HomeActivity.this, ExamDetailsActivity.class);
                dIntent.putExtra("selectedSubjects", selectedSubjects);
                dIntent.putExtra("selectedExam", selectedExamName);
                startActivity(dIntent);
            });
        }

        // Bottom nav "Study" → TimetableActivity
        LinearLayout navStudy = findViewById(R.id.navStudy);
        if (navStudy != null) {
            navStudy.setOnClickListener(v -> {
                Intent tIntent = new Intent(HomeActivity.this, TimetableActivity.class);
                tIntent.putExtra("selectedSubjects", selectedSubjects);
                tIntent.putExtra("generatedPlanJson", PlanPrefs.readPlanJson(HomeActivity.this));
                startActivity(tIntent);
            });
        }

        // Bottom nav "Analytics" → ConfidenceMapActivity
        LinearLayout navAnalytics = findViewById(R.id.navAnalytics);
        if (navAnalytics != null) {
            navAnalytics.setOnClickListener(v -> {
                Intent cIntent = new Intent(HomeActivity.this, ConfidenceMapActivity.class);
                cIntent.putExtra("selectedSubjects", selectedSubjects);
                startActivity(cIntent);
            });
        }
    }

    // ========================
    // onResume — Called every time user returns to this screen
    // (e.g., after quiz, focus session, or pressing back)
    // Re-fetches subjects from backend to get LATEST confidence values
    // ========================
    @Override
    protected void onResume() {
        super.onResume();
        fetchEnrolledSubjectsAndRefreshUi();
    }

    // =====================================================================
    // submitAiCommand() — AI Task Agent
    // Sends user's natural language command to HuggingFace flan-t5-small model
    // via OkHttp (not Volley). The AI parses it into structured tasks.
    // =====================================================================
    private void submitAiCommand() {
        String command = etAiCommand.getText().toString().trim();
        if (command.isEmpty()) {
            showToast("Please enter a task command.");
            return;
        }

        btnAiSend.setEnabled(false); // Disable button while AI is processing

        // TaskAgentApiClient uses OkHttp (not Volley) for HuggingFace API call
        TaskAgentApiClient.sendTaskAgentCommand(command, new TaskAgentApiClient.TaskAgentCallback() {
            @Override
            public void onSuccess(TaskAgentResponse taskAgentResponse) {
                // OkHttp callback runs on background thread → must switch to UI thread
                runOnUiThread(() -> {
                    btnAiSend.setEnabled(true);
                    if ("add_tasks".equals(taskAgentResponse.getAction()) && !taskAgentResponse.getTasks().isEmpty()) {
                        int startPosition = taskAgentTasks.size();
                        taskAgentTasks.addAll(taskAgentResponse.getTasks()); // Add new tasks to list
                        taskAgentAdapter.notifyItemRangeInserted(startPosition, taskAgentResponse.getTasks().size());
                        // ↑ Efficient: only notifies RecyclerView about NEW items, not entire list
                        etAiCommand.setText(""); // Clear input
                    } else {
                        showToast("AI returned no tasks or invalid action.");
                    }
                });
            }

            @Override
            public void onFailure(String errorMessage) {
                runOnUiThread(() -> {
                    btnAiSend.setEnabled(true);
                    showToast(errorMessage);
                });
            }
        });
    }

    private void showToast(String message) {
        Toast.makeText(HomeActivity.this, message, Toast.LENGTH_SHORT).show();
    }

    // =====================================================================
    // resolveUserDisplayName() — Extracts a displayable name from FirebaseUser
    // Priority: displayName (Google accounts) → email prefix → "User"
    // =====================================================================
    private static String resolveUserDisplayName(FirebaseUser user) {
        if (user == null) {
            return "User";
        }
        // Try Firebase display name first (set by Google accounts)
        String display = user.getDisplayName();
        if (display != null && !display.trim().isEmpty()) {
            return display.trim();
        }
        // Fallback: extract name from email. "john@gmail.com" → "john"
        String email = user.getEmail();
        if (email != null && email.contains("@")) {
            return email.substring(0, email.indexOf('@')).trim();
        }
        return "User";
    }

    // =====================================================================
    // updateDynamicUI() — Populates the dashboard with user data
    // Sets: username, exam title, average proficiency %, progress bar, chart
    // =====================================================================
    private void updateDynamicUI() {
        // Set username greeting
        TextView tvUserName = findViewById(R.id.tvUserName);
        if (tvUserName != null) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            tvUserName.setText(resolveUserDisplayName(user));
        }

        // Set exam title (e.g., "Semester")
        TextView tvExamTitle = findViewById(R.id.tvExamTitle);
        if (tvExamTitle != null && selectedExamName != null) {
            tvExamTitle.setText(selectedExamName);
        }

        // Calculate AVERAGE proficiency across all enrolled subjects
        if (selectedSubjects != null && !selectedSubjects.isEmpty()) {
            int totalProficiency = 0;
            for (Subject s : selectedSubjects) {
                totalProficiency += s.getProficiency();
            }
            int averageProficiency = totalProficiency / selectedSubjects.size();

            // Display average proficiency percentage
            TextView tvProgressPercent = findViewById(R.id.tvProgressPercent);
            if (tvProgressPercent != null) {
                tvProgressPercent.setText(averageProficiency + "%");
            }

            // Find and update the ProgressBar inside the progress container
            // Uses ViewGroup iteration because ProgressBar is nested inside a RelativeLayout
            View rlProgress = findViewById(R.id.rlProgress);
            if (rlProgress instanceof android.view.ViewGroup) {
                android.view.ViewGroup group = (android.view.ViewGroup) rlProgress;
                for (int i = 0; i < group.getChildCount(); i++) {
                    View child = group.getChildAt(i);
                    if (child instanceof ProgressBar) {
                        ((ProgressBar) child).setProgress(averageProficiency);
                    }
                }
            }
        }

        // Build the confidence map mini bar chart
        bindConfidenceMapStrip(selectedSubjects);
    }

    // =====================================================================
    // fetchEnrolledSubjectsAndRefreshUi() — Re-fetches subjects from backend
    // Called in onResume() to get LATEST confidence values after quiz/study
    // GET /api/subjects/mine?firebase_uid=... → returns user's subjects
    // =====================================================================
    private void fetchEnrolledSubjectsAndRefreshUi() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            updateDynamicUI();
            return;
        }

        String url = SUBJECTS_BASE + "/mine?firebase_uid=" + Uri.encode(user.getUid());
        JsonArrayRequest req =
                new JsonArrayRequest(
                        Request.Method.GET,
                        url,
                        null,
                        response -> {
                            // Parse backend response into Subject objects
                            ArrayList<Subject> next = new ArrayList<>();
                            for (int i = 0; i < response.length(); i++) {
                                try {
                                    JSONObject o = response.getJSONObject(i);
                                    next.add(SubjectMineParser.fromMineJson(o));
                                    // ↑ SubjectMineParser converts backend JSON → Subject objects
                                } catch (Exception ignored) {
                                }
                            }
                            // Replace old data with fresh backend data
                            selectedSubjects.clear();
                            selectedSubjects.addAll(next);

                            // Re-parse plan durations
                            subjectToDuration =
                                    PlanParser.subjectToDuration(
                                            PlanPrefs.readPlanJson(HomeActivity.this));

                            // Refresh the horizontal RecyclerView
                            if (adapter != null) {
                                adapter.notifyDataSetChanged();
                            }
                            // Refresh dashboard numbers + chart
                            updateDynamicUI();
                        },
                        error -> updateDynamicUI()); // On error, still update with existing data
        queue.add(req);
    }

    // =====================================================================
    // bindConfidenceMapStrip() — Builds a PROGRAMMATIC bar chart
    // No charting library used — creates Views in code.
    // Each subject gets a vertical bar proportional to its proficiency.
    // The weakest subject is highlighted with a different color.
    // =====================================================================
    private void bindConfidenceMapStrip(ArrayList<Subject> subjects) {
        LinearLayout llCols = findViewById(R.id.llHomeConfidenceColumns);
        TextView tvOverall = findViewById(R.id.tvHomeConfidenceOverall);
        TextView tvDelta = findViewById(R.id.tvHomeConfidenceDelta);
        TextView tvHint = findViewById(R.id.tvHomeConfidenceHint);
        if (llCols == null || tvOverall == null) {
            return;
        }

        llCols.removeAllViews(); // Clear previous chart bars

        // Calculate pixel dimensions based on screen density
        float density = getResources().getDisplayMetrics().density;
        int colW = (int) (48 * density);      // Each column width = 48dp
        int maxBarH = (int) (120 * density);  // Maximum bar height = 120dp

        // Empty state: no subjects enrolled
        if (subjects == null || subjects.isEmpty()) {
            tvOverall.setText("—");
            if (tvDelta != null) {
                tvDelta.setVisibility(View.GONE);
            }
            if (tvHint != null) {
                tvHint.setText("Complete setup, then use AI quiz on each subject to build this map.");
            }
            TextView empty = new TextView(this);
            empty.setTextColor(ContextCompat.getColor(this, R.color.text_gray));
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            empty.setText("No enrolled subjects yet.");
            llCols.addView(empty);
            return;
        }

        if (tvHint != null) {
            tvHint.setText("Per-subject confidence from setup & AI quiz");
        }

        // Find the WEAKEST subject (lowest proficiency) to highlight it differently
        int minIdx = 0;
        for (int i = 1; i < subjects.size(); i++) {
            if (subjects.get(i).getProficiency() < subjects.get(minIdx).getProficiency()) {
                minIdx = i;
            }
        }

        // Build one vertical bar + label for each subject
        for (int i = 0; i < subjects.size(); i++) {
            Subject subject = subjects.get(i);
            int prof = subject.getProficiency();
            boolean weakest = i == minIdx;

            // FrameLayout allows us to position the bar at the BOTTOM of the container
            FrameLayout frame = new FrameLayout(this);
            LinearLayout.LayoutParams frameLp =
                    new LinearLayout.LayoutParams(colW, maxBarH);
            frame.setLayoutParams(frameLp);

            // Bar height proportional to proficiency: 65% → 65% of maxBarH
            // Minimum 8dp so even 0% subjects have a visible sliver
            int barH = Math.max((int) (maxBarH * prof / 100.0), (int) (8 * density));
            FrameLayout.LayoutParams barLp =
                    new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, barH);
            barLp.gravity = Gravity.BOTTOM; // Anchor bar to bottom of frame
            View bar = new View(this);
            bar.setLayoutParams(barLp);
            // Weakest subject gets highlighted color, others get default
            bar.setBackgroundColor(
                    ContextCompat.getColor(
                            this,
                            weakest ? R.color.chart_bar_highlight : R.color.chart_bar_default));
            frame.addView(bar);

            // Label below the bar: abbreviated subject name (first 3 chars)
            // "DBMS" → "DBM", "Android Development" → "AND"
            TextView label = new TextView(this);
            label.setText(abbrevSubjectName(subject.getName()));
            label.setTextColor(ContextCompat.getColor(this, R.color.text_gray));
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            label.setGravity(Gravity.CENTER_HORIZONTAL);

            // Stack: bar frame + label in a vertical column
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams colLp =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            int m = (int) (4 * density);
            colLp.setMargins(m, 0, m, 0);
            col.setLayoutParams(colLp);
            col.addView(frame);
            col.addView(label);

            llCols.addView(col); // Add column to the chart row
        }

        // Calculate and display overall average confidence
        int total = 0;
        for (Subject s : subjects) {
            total += s.getProficiency();
        }
        int avg = total / subjects.size();
        tvOverall.setText(avg + "%");

        // Show DELTA (change since last visit) using ConfidencePrefs
        if (tvDelta != null) {
            int prev = ConfidencePrefs.getLastAverageConfidence(this);
            // ↑ Reads previously saved average from SharedPreferences
            if (prev >= 0) {
                int d = avg - prev;
                if (d != 0) {
                    tvDelta.setVisibility(View.VISIBLE);
                    tvDelta.setText((d > 0 ? "+" : "") + d + "%");
                    // "+5%" for improvement, "-3%" for decline
                    tvDelta.setTextColor(
                            d > 0 ? Color.parseColor("#00C853")    // Green = improved
                                    : Color.parseColor("#FF5252")); // Red = declined
                } else {
                    tvDelta.setVisibility(View.GONE); // No change — hide delta
                }
            } else {
                tvDelta.setVisibility(View.GONE); // First visit — no previous value
            }
        }
        // Save current average for next comparison
        ConfidencePrefs.setLastAverageConfidence(this, avg);
    }

    // =====================================================================
    // abbrevSubjectName() — Abbreviates subject name to 3 uppercase chars
    // "DBMS" → "DBM", "Android Development" → "AND", "" → "?"
    // Used for bar chart labels to save horizontal space
    // =====================================================================
    private static String abbrevSubjectName(String name) {
        if (name == null) {
            return "?";
        }
        String t = name.trim();
        if (t.isEmpty()) {
            return "?";
        }
        if (t.length() <= 3) {
            return t.toUpperCase(Locale.US);
        }
        return t.substring(0, 3).toUpperCase(Locale.US);
    }
}
