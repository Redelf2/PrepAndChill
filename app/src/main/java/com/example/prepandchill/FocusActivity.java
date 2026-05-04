package com.example.prepandchill;

import android.content.Intent;                    // For receiving session data from TimetableActivity/StudyActivity
import android.os.Bundle;                          // For savedInstanceState
import android.os.CountDownTimer;                  // Android's built-in countdown timer class
import android.util.Log;                           // For debug logging with TAG
import android.view.View;                          // Base class for UI components
import android.widget.ImageView;                   // Close button icon
import android.widget.ProgressBar;                 // Circular progress indicator for timer
import android.widget.TextView;                    // Timer display, topic name, session info
import android.widget.Toast;                       // Short popup messages

import androidx.activity.OnBackPressedCallback;    // Modern back button handler
import androidx.appcompat.app.AppCompatActivity;   // Base activity

import com.android.volley.Request;                 // HTTP method constants (POST)
import com.android.volley.RequestQueue;            // Network request queue
import com.android.volley.toolbox.JsonObjectRequest;// POST request sending/receiving JSON objects
import com.android.volley.toolbox.Volley;           // Factory for RequestQueue
import com.google.android.material.bottomnavigation.BottomNavigationView; // Bottom nav bar
import com.google.android.material.floatingactionbutton.FloatingActionButton; // Circular FAB for play/pause
import com.google.firebase.auth.FirebaseAuth;      // Firebase auth manager

import org.json.JSONObject;                        // For building request bodies

import java.util.Locale;                           // For locale-safe String.format

/**
 * FocusActivity — Implements a POMODORO-STYLE FOCUS TIMER.
 *
 * Features:
 * - Counts down from allocated study time (or resumes from saved remaining time)
 * - MM:SS display + circular progress bar
 * - Pause/Play, Stop, Skip, Close buttons
 * - AUTO-SAVES remaining time on: pause, back, close, app minimize (onPause)
 * - Logs completed sessions to backend (minutes_spent + performance_score)
 *
 * Design Decision: Every exit path saves progress first — user's study time is NEVER lost.
 *
 * Flow: TimetableActivity/StudyActivity → [this screen] → back to previous
 */
public class FocusActivity extends AppCompatActivity {

    // ========================
    // CONSTANTS & FIELD VARIABLES
    // ========================

    private static final String TAG = "FocusActivity"; // For Log.d() debug messages in Logcat

    // UI Elements
    private TextView tvTopicName, tvTimerDisplay, tvSessionCount, tvTotalTime;
    private ProgressBar timerProgressBar;             // Circular progress (fills as time passes)
    private FloatingActionButton btnPausePlay;        // Big circular button: ▶ or ⏸
    private View btnStop, btnSkip;                    // Stop (logs session) and Skip (exits)
    
    // Timer State
    private CountDownTimer countDownTimer;            // Android's built-in countdown timer
    private long timeLeftInMillis;                    // Current remaining time in milliseconds
    private long initialTimeInMillis;                 // Original total time (used for progress bar math)
    private boolean timerRunning = false;             // Is the timer currently ticking?
    
    // Session Data (received from Intent)
    private String subjectName, topicName;            // What the user is studying
    private int subjectId, topicId, totalDurationMins;// Database IDs + planned duration
    private String firebaseUid;                      // User's Firebase UID for API calls
    private RequestQueue queue;                      // Volley queue for saving progress

    // ========================
    // onCreate — ENTRY POINT
    // ========================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.d(TAG, "onCreate: Starting FocusActivity");

        try {
            // Draw content behind status bar for immersive look
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );

            setContentView(R.layout.activity_focus);

            // STEP 1: Bind all UI views
            tvTopicName = findViewById(R.id.tvFocusTopicName);   // Shows current topic name
            tvTimerDisplay = findViewById(R.id.tvTimerDisplay);   // "25:00" countdown display
            tvSessionCount = findViewById(R.id.tvSessionCount);   // Session counter
            tvTotalTime = findViewById(R.id.tvTotalTime);         // Total allocated time
            timerProgressBar = findViewById(R.id.timerProgressBar); // Circular progress
            btnPausePlay = findViewById(R.id.btnPausePlay);       // FloatingActionButton ▶/⏸
            btnStop = findViewById(R.id.btnStop);                 // Stops timer + logs session
            btnSkip = findViewById(R.id.btnSkip);                 // Skips session (saves + exits)
            ImageView btnClose = findViewById(R.id.btnClose);     // X button (saves + exits)

            // STEP 2: Initialize networking
            queue = Volley.newRequestQueue(this);
            firebaseUid = FirebaseAuth.getInstance().getUid();
            // ↑ Shortcut for getCurrentUser().getUid()

            // STEP 3: Extract session data from Intent
            Intent intent = getIntent();
            if (intent != null) {
                subjectId = intent.getIntExtra("subject_id", -1);
                topicId = intent.getIntExtra("topic_id", -1);
                subjectName = intent.getStringExtra("subject_name");  // e.g., "DBMS"
                topicName = intent.getStringExtra("topic_name");      // e.g., "ER Diagrams"
                totalDurationMins = intent.getIntExtra("total_minutes", 25); // Default 25 min
                
                // CHECK FOR RESUMED SESSION
                // If user paused before, TimetableActivity/StudyActivity pass remaining_seconds
                int remainingSeconds = intent.getIntExtra("remaining_seconds", -1);
                if (remainingSeconds > 0) {
                    // RESUME MODE: start from where user left off
                    timeLeftInMillis = (long) remainingSeconds * 1000;
                    initialTimeInMillis = (long) totalDurationMins * 60 * 1000;
                    // ↑ Keep original total for progress bar calculation
                } else {
                    // FRESH START: full duration
                    initialTimeInMillis = (long) totalDurationMins * 60 * 1000;
                    timeLeftInMillis = initialTimeInMillis;
                }
                
                Log.d(TAG, "Data: Sub=" + subjectName + ", Topic=" + topicName + ", Mins=" + totalDurationMins + ", RemainingSec=" + remainingSeconds);
            }

            // STEP 4: Set initial UI state
            if (topicName != null) tvTopicName.setText(topicName);
            tvTotalTime.setText(totalDurationMins + "m");
            updateCountDownText();   // Show initial time (e.g., "25:00")
            updateProgressBar();     // Show full progress bar

            // STEP 5: Button handlers

            // CLOSE (X icon) → save remaining time + exit
            btnClose.setOnClickListener(v -> {
                saveProgressToBackend();
                finish();
            });
            
            // PAUSE/PLAY toggle button
            btnPausePlay.setOnClickListener(v -> {
                if (timerRunning) {
                    pauseTimer();                // Cancel the CountDownTimer
                    saveProgressToBackend();      // Save remaining time immediately
                } else {
                    startTimer();                // Start/resume countdown
                }
            });

            // STOP → log the session as completed + exit
            btnStop.setOnClickListener(v -> stopAndLogSession());

            // SKIP → save progress + exit (same as close)
            btnSkip.setOnClickListener(v -> {
                saveProgressToBackend();
                finish();
            });

            // BACK BUTTON (system) → save progress + exit
            // Uses modern OnBackPressedDispatcher (replaces deprecated onBackPressed)
            getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    saveProgressToBackend();
                    finish();
                }
            });

            // STEP 6: Setup bottom navigation bar
            setupBottomNav();
            
            // STEP 7: AUTO-START the timer when screen opens
            startTimer();

        } catch (Exception e) {
            Log.e(TAG, "Error initializing FocusActivity", e);
            Toast.makeText(this, "Failed to start focus session: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    // =====================================================================
    // startTimer() — Creates and starts the CountDownTimer
    // CountDownTimer ticks every 1000ms (1 second), updating display each tick.
    // When finished, it automatically logs the session.
    // =====================================================================
    private void startTimer() {
        if (countDownTimer != null) countDownTimer.cancel(); // Cancel any existing timer
        
        // CountDownTimer(totalMillis, intervalMillis)
        // Ticks every 1000ms, counts down from timeLeftInMillis
        countDownTimer = new CountDownTimer(timeLeftInMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                // Called every 1 second
                timeLeftInMillis = millisUntilFinished; // Update remaining time
                updateCountDownText();                  // Update "25:00" → "24:59" → ...
                updateProgressBar();                    // Update circular progress
            }

            @Override
            public void onFinish() {
                // Called when timer reaches 0:00
                timerRunning = false;
                timeLeftInMillis = 0;
                updateCountDownText();                  // Show "00:00"
                updateProgressBar();                    // Show empty progress
                btnPausePlay.setImageResource(R.drawable.ic_pomodoro);
                stopAndLogSession();                    // Automatically log the completed session
            }
        }.start(); // .start() begins the countdown immediately

        timerRunning = true;
        btnPausePlay.setImageResource(android.R.drawable.ic_media_pause); // Show ⏸ icon
    }

    // =====================================================================
    // pauseTimer() — Stops the countdown without logging the session
    // User can resume later by pressing play
    // =====================================================================
    private void pauseTimer() {
        if (countDownTimer != null) countDownTimer.cancel(); // Stop ticking
        timerRunning = false;
        btnPausePlay.setImageResource(android.R.drawable.ic_media_play); // Show ▶ icon
    }

    // =====================================================================
    // updateCountDownText() — Formats remaining time as MM:SS
    // e.g., 1500000ms → "25:00", 59000ms → "00:59"
    // %02d = pad with leading zero (5 → "05", 25 → "25")
    // =====================================================================
    private void updateCountDownText() {
        int totalSeconds = (int) (timeLeftInMillis / 1000);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        String timeLeftFormatted = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        tvTimerDisplay.setText(timeLeftFormatted);
    }

    // =====================================================================
    // updateProgressBar() — Maps remaining time to progress bar value
    // ProgressBar max defaults to 1000.
    // Full time left → 1000 (full circle). Zero → 0 (empty circle).
    // =====================================================================
    private void updateProgressBar() {
        if (initialTimeInMillis == 0) return; // Avoid division by zero
        int progress = (int) ((timeLeftInMillis * 1000) / initialTimeInMillis);
        timerProgressBar.setProgress(progress);
    }

    // =====================================================================
    // saveProgressToBackend() — Saves remaining time to backend (fire-and-forget)
    // Called on: pause, close, skip, back press, onPause lifecycle, stop
    // POST /api/subjects/saveRemainingTime
    // Backend updates: user_topics SET remaining_seconds = ? WHERE user_id AND topic_id
    // =====================================================================
    private void saveProgressToBackend() {
        if (topicId == -1 || firebaseUid == null) return; // Can't save without topic ID

        String url = "http://10.7.28.203:3000/api/subjects/saveRemainingTime";
        try {
            JSONObject body = new JSONObject();
            body.put("firebase_uid", firebaseUid);
            body.put("topic_id", topicId);
            body.put("remaining_seconds", (int) (timeLeftInMillis / 1000));
            // ↑ Convert milliseconds to seconds for backend storage

            // Fire-and-forget: null callbacks = don't wait for response, don't show errors
            // This keeps the UI responsive — saving happens silently in background
            queue.add(new JsonObjectRequest(Request.Method.POST, url, body, null, null));
        } catch (Exception e) {
            Log.e(TAG, "Error saving remaining time", e);
        }
    }

    // =====================================================================
    // stopAndLogSession() — Logs the completed study session to backend
    // Calculates actual minutes spent, then POSTs to /api/subjects/saveSession.
    // Minimum threshold: 5 seconds (to ignore accidental starts).
    // Also saves remaining time before logging.
    // =====================================================================
    private void stopAndLogSession() {
        pauseTimer(); // Stop the countdown
        
        // Calculate how long the user actually studied
        long millisSpent = (initialTimeInMillis - timeLeftInMillis);
        int secondsSpent = (int) (millisSpent / 1000);
        float minutesSpent = secondsSpent / 60.0f;

        // If user studied less than 5 seconds, don't log (accidental start)
        if (secondsSpent < 5) {
            finish();
            return;
        }

        saveProgressToBackend(); // Also save the remaining time

        // POST to /api/subjects/saveSession → inserts into study_sessions table
        String url = "http://10.7.28.203:3000/api/subjects/saveSession";
        try {
            JSONObject body = new JSONObject();
            body.put("firebase_uid", firebaseUid);
            body.put("subject_id", subjectId);                          // Which subject
            body.put("topic_id", topicId);                              // Which topic
            body.put("minutes_spent", Math.round(minutesSpent));        // Rounded to nearest minute
            body.put("performance_score", 90);                          // Hardcoded score for now

            queue.add(new JsonObjectRequest(Request.Method.POST, url, body,
                response -> {
                    Toast.makeText(this, "Session saved!", Toast.LENGTH_SHORT).show();
                    finish(); // Close FocusActivity → return to previous screen
                },
                error -> {
                    Log.e(TAG, "Volley Error logging session", error);
                    finish(); // Close even on error — don't trap the user
                }
            ));
        } catch (Exception e) { 
            Log.e(TAG, "JSON Error", e);
            finish(); 
        }
    }

    // =====================================================================
    // setupBottomNav() — Configures the bottom navigation bar
    // KEY: Every navigation action saves progress FIRST, then navigates.
    // This ensures the user's study time is never lost when switching screens.
    // =====================================================================
    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_focus); // Highlight "Focus" tab

            bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    saveProgressToBackend();  // ALWAYS save before leaving
                    BottomNavNavigator.open(this, HomeActivity.class);
                    return true;
                } else if (id == R.id.nav_study) {
                    saveProgressToBackend();
                    BottomNavNavigator.open(this, StudyActivity.class);
                    return true;
                } else if (id == R.id.nav_analytics) {
                    saveProgressToBackend();
                    BottomNavNavigator.open(this, ConfidenceMapActivity.class);
                    return true;
                }
                return false;
            });
        }
    }

    // =====================================================================
    // onPause() — Android lifecycle method
    // Called when: user switches app, phone screen turns off, navigates away
    // Ensures remaining time is ALWAYS saved, even if user doesn't explicitly pause
    // =====================================================================
    @Override
    protected void onPause() {
        super.onPause();
        saveProgressToBackend();
    }
}
