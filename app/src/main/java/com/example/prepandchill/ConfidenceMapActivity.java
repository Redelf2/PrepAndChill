package com.example.prepandchill;

import android.content.Intent;                    // For launching SubjectAssessmentActivity from Quiz button
import android.graphics.Color;                     // For Color.parseColor() on delta text
import android.net.Uri;                            // For URL-encoding firebase_uid in API calls
import android.os.Bundle;                          // For savedInstanceState
import android.view.LayoutInflater;                // For inflating subject row XML layouts
import android.view.View;                          // Base class for UI components
import android.widget.ImageView;                   // Back button icon
import android.widget.LinearLayout;                // Container for subject rows and critical cards
import android.widget.ProgressBar;                 // Horizontal progress bar per subject
import android.widget.TextView;                    // Text displays
import androidx.appcompat.app.AppCompatActivity;   // Base activity
import androidx.core.content.ContextCompat;        // Safe way to get colors from resources

import com.android.volley.Request;                 // HTTP method constants (GET)
import com.android.volley.RequestQueue;            // Network request queue
import com.android.volley.toolbox.JsonArrayRequest; // GET request expecting JSON array
import com.android.volley.toolbox.Volley;           // Factory for RequestQueue
import com.google.android.material.button.MaterialButton; // Material Design button for "Quiz"
import com.google.firebase.auth.FirebaseAuth;      // Firebase auth manager
import com.google.firebase.auth.FirebaseUser;      // Current user

import org.json.JSONObject;                        // For parsing subject JSON from backend

import java.util.ArrayList;                        // Dynamic array
import java.util.Collections;                      // For sorting subject list
import java.util.Comparator;                       // For defining sort order
import java.util.List;                             // Interface for lists

/**
 * ConfidenceMapActivity — Confidence ANALYTICS DASHBOARD.
 *
 * Displays:
 * - Average confidence % with delta change from last home visit (+5% / -3%)
 * - Progress bar for average confidence
 * - Total enrolled subject count
 * - Subject list sorted by proficiency (WEAKEST FIRST) with progress bars
 * - Critical Review section: bottom 2 weakest subjects with "Quiz" button
 *
 * Uses LIVE DATA — fetches latest subject data from backend every time screen opens.
 * The "Quiz" button navigates to SubjectAssessmentActivity where user can retake AI quiz.
 *
 * Flow: HomeActivity (bottom nav / card) → [this screen]
 *       [this screen] → SubjectAssessmentActivity (via Quiz button) → back here (auto-refresh)
 */
public class ConfidenceMapActivity extends AppCompatActivity {

    // ========================
    // FIELD VARIABLES
    // ========================

    private static final String BASE_IP = "10.7.28.203";
    private static final String SUBJECTS_BASE = "http://" + BASE_IP + ":3000/api/subjects";

    private RequestQueue queue;                                    // Volley network queue
    private final ArrayList<Subject> subjects = new ArrayList<>(); // Live list of enrolled subjects

    // UI Elements
    private TextView tvMapAvgConfidence;      // Shows average confidence "65%"
    private TextView tvMapAvgDelta;           // Shows change "+5% vs last home refresh"
    private ProgressBar pbMapAvgConfidence;   // Average confidence progress bar
    private TextView tvMapSubjectCount;       // Shows number of subjects "3"
    private LinearLayout llSubjectList;       // Where subject rows are added
    private LinearLayout llCriticalList;      // Where critical review cards are added

    // ========================
    // onCreate — ENTRY POINT
    // ========================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Draw content behind status bar for immersive look
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        setContentView(R.layout.activity_confidence_map);

        // Initialize Volley network queue
        queue = Volley.newRequestQueue(this);

        // Find all UI elements
        tvMapAvgConfidence = findViewById(R.id.tvMapAvgConfidence);
        tvMapAvgDelta = findViewById(R.id.tvMapAvgDelta);
        pbMapAvgConfidence = findViewById(R.id.pbMapAvgConfidence);
        tvMapSubjectCount = findViewById(R.id.tvMapSubjectCount);
        llSubjectList = findViewById(R.id.llConfidenceSubjectList);
        llCriticalList = findViewById(R.id.llCriticalReviewList);

        // Back button → close this Activity
        ImageView btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // Load initial data from Intent (may be slightly stale — onResume will refresh)
        @SuppressWarnings("unchecked")
        ArrayList<Subject> fromIntent =
                (ArrayList<Subject>) getIntent().getSerializableExtra("selectedSubjects");
        subjects.clear();
        if (fromIntent != null) {
            subjects.addAll(fromIntent);
        }
        bindUi(); // Render initial UI with Intent data
    }

    // ========================
    // onResume — Called EVERY TIME user returns to this screen
    // (e.g., after taking a quiz in SubjectAssessmentActivity)
    // Fetches LATEST confidence values from backend
    // ========================
    @Override
    protected void onResume() {
        super.onResume();
        fetchMine(); // Always refresh with latest backend data
    }

    // =====================================================================
    // fetchMine() — GET request to fetch user's latest subject data
    // GET /api/subjects/mine?firebase_uid=...
    // Returns subjects with LATEST confidence values from database
    // On success → replaces local data → re-renders UI
    // =====================================================================
    private void fetchMine() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            bindUi(); // Not logged in — show what we have
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
                            subjects.clear();
                            for (int i = 0; i < response.length(); i++) {
                                try {
                                    JSONObject o = response.getJSONObject(i);
                                    subjects.add(SubjectMineParser.fromMineJson(o));
                                    // ↑ SubjectMineParser creates Subject objects from backend JSON
                                } catch (Exception ignored) {
                                }
                            }
                            bindUi(); // Re-render with fresh data
                        },
                        error -> bindUi()); // On error, render with existing data
        queue.add(req);
    }

    // =====================================================================
    // bindUi() — MAIN RENDER METHOD
    // Builds the entire dashboard: average confidence, delta, sorted subject
    // list with progress bars, and critical review section.
    // =====================================================================
    private void bindUi() {
        if (tvMapAvgConfidence == null || llSubjectList == null) {
            return; // Views not initialized (shouldn't happen)
        }

        // ---- EMPTY STATE ----
        if (subjects.isEmpty()) {
            tvMapAvgConfidence.setText("—");
            if (tvMapAvgDelta != null) {
                tvMapAvgDelta.setVisibility(View.GONE); // Hide delta
            }
            if (pbMapAvgConfidence != null) {
                pbMapAvgConfidence.setProgress(0); // Empty progress bar
            }
            if (tvMapSubjectCount != null) {
                tvMapSubjectCount.setText("0");
            }
            // Show helper message
            llSubjectList.removeAllViews();
            TextView empty = new TextView(this);
            empty.setTextColor(ContextCompat.getColor(this, R.color.text_gray));
            empty.setTextSize(14);
            empty.setText("No subjects yet. Finish onboarding, then take AI quizzes from Assessment.");
            llSubjectList.addView(empty);
            bindCritical(null); // No critical subjects to show
            return;
        }

        // ---- CALCULATE AVERAGE CONFIDENCE ----
        int sum = 0;
        for (Subject s : subjects) {
            sum += s.getProficiency();
        }
        int avg = sum / subjects.size();
        tvMapAvgConfidence.setText(avg + "%");   // e.g., "65%"
        if (pbMapAvgConfidence != null) {
            pbMapAvgConfidence.setProgress(avg); // Fill progress bar to 65%
        }
        if (tvMapSubjectCount != null) {
            tvMapSubjectCount.setText(String.valueOf(subjects.size())); // e.g., "3"
        }

        // ---- DELTA COMPARISON (change since last home visit) ----
        if (tvMapAvgDelta != null) {
            int prev = ConfidencePrefs.getLastAverageConfidence(this);
            // ↑ Reads previously saved average from SharedPreferences
            // This was saved by HomeActivity's bindConfidenceMapStrip()
            if (prev >= 0) {
                int d = avg - prev;
                if (d != 0) {
                    tvMapAvgDelta.setVisibility(View.VISIBLE);
                    tvMapAvgDelta.setText((d > 0 ? "+" : "") + d + "% vs last home refresh");
                    // "+5% vs last home refresh" or "-3% vs last home refresh"
                    tvMapAvgDelta.setTextColor(
                            d > 0 ? Color.parseColor("#00C853")    // Green = improved
                                    : Color.parseColor("#FF5252")); // Red = declined
                } else {
                    tvMapAvgDelta.setVisibility(View.GONE); // No change — hide
                }
            } else {
                tvMapAvgDelta.setVisibility(View.GONE); // First visit — no previous value
            }
        }

        // ---- SUBJECT LIST (sorted weakest first) ----
        // Sort by proficiency ascending: lowest confidence at the top
        List<Subject> sorted = new ArrayList<>(subjects);
        Collections.sort(sorted, Comparator.comparingInt(Subject::getProficiency));
        // ↑ Method reference: compares by getProficiency() return value
        // Result: [35% DBMS, 55% Android, 78% OS] — weakest first

        // Clear and rebuild the subject list
        llSubjectList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Subject s : sorted) {
            // Inflate a row layout for each subject
            View row = inflater.inflate(R.layout.item_confidence_map_subject, llSubjectList, false);
            TextView tvName = row.findViewById(R.id.tvConfSubjectName);    // Subject name
            TextView tvMeta = row.findViewById(R.id.tvConfSubjectMeta);    // Difficulty + exam date
            TextView tvPct = row.findViewById(R.id.tvConfSubjectPercent);  // Confidence %
            ProgressBar pb = row.findViewById(R.id.pbConfSubject);         // Progress bar

            if (tvName != null) {
                tvName.setText(s.getName()); // "DBMS"
            }
            if (tvMeta != null) {
                tvMeta.setText(
                        "Difficulty "
                                + s.getDifficulty()
                                + "/3 · Exam "
                                + (s.getExamDate() != null ? s.getExamDate() : ""));
                // e.g., "Difficulty 2/3 · Exam 2026-06-15"
            }
            if (tvPct != null) {
                tvPct.setText(s.getProficiency() + "%"); // "65%"
            }
            if (pb != null) {
                pb.setProgress(s.getProficiency()); // Fill bar to 65%
            }
            llSubjectList.addView(row);
        }

        // Build critical review section from the same sorted list
        bindCritical(sorted);
    }

    // =====================================================================
    // bindCritical() — Highlights the WEAKEST SUBJECTS (bottom 2)
    // Shows a card per subject with name, confidence %, and a "Quiz" button
    // that navigates to SubjectAssessmentActivity where user can retake AI quiz.
    // Cards are built PROGRAMMATICALLY (no XML layout file).
    // =====================================================================
    /** Lowest-confidence subjects — prompt quiz from Assessment. */
    private void bindCritical(List<Subject> sortedAscending) {
        if (llCriticalList == null) {
            return;
        }
        llCriticalList.removeAllViews();
        if (sortedAscending == null || sortedAscending.isEmpty()) {
            return;
        }

        // Show at most 2 critical subjects (the weakest ones)
        int n = Math.min(2, sortedAscending.size());
        for (int i = 0; i < n; i++) {
            Subject s = sortedAscending.get(i); // Already sorted: index 0 = weakest

            // BUILD CARD PROGRAMMATICALLY (no XML layout needed)
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL); // Left: text, Right: button
            card.setBackgroundResource(R.drawable.bg_streak_card); // Rounded dark card background

            // Padding in pixels (convert from dp using density)
            int pad = (int) (16 * getResources().getDisplayMetrics().density);
            card.setPadding(pad, pad, pad, pad);

            // Layout params with bottom margin for spacing between cards
            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int) (12 * getResources().getDisplayMetrics().density);
            card.setLayoutParams(lp);

            // LEFT SIDE: Subject name + confidence message (vertical column)
            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setLayoutParams(
                    new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            // ↑ weight=1f makes this column take all available space (pushes button to right)

            // Subject name (bold, white)
            TextView title = new TextView(this);
            title.setText(s.getName()); // "DBMS"
            title.setTextColor(ContextCompat.getColor(this, R.color.text_white));
            title.setTextSize(16);
            title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);

            // Confidence message (gray, smaller)
            TextView sub = new TextView(this);
            sub.setText("Confidence " + s.getProficiency() + "% — take AI quiz to update");
            // "Confidence 35% — take AI quiz to update"
            sub.setTextColor(ContextCompat.getColor(this, R.color.text_gray));
            sub.setTextSize(12);

            textCol.addView(title);
            textCol.addView(sub);

            // RIGHT SIDE: "Quiz" button
            MaterialButton btn = new MaterialButton(this);
            btn.setText("Quiz");
            btn.setOnClickListener(
                    v -> {
                        // Navigate to SubjectAssessmentActivity where user can retake AI quiz
                        // When they return, onResume() → fetchMine() refreshes with updated confidence
                        Intent intent = new Intent(this, SubjectAssessmentActivity.class);
                        intent.putExtra("selectedSubjects", new ArrayList<>(subjects));
                        startActivity(intent);
                    });

            // Assemble card: text column + quiz button
            card.addView(textCol);
            card.addView(btn);
            llCriticalList.addView(card);
        }
    }
}
