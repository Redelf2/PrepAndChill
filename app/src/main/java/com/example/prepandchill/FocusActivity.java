package com.example.prepandchill;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;

import org.json.JSONObject;

import java.util.Locale;

public class FocusActivity extends AppCompatActivity {

    private static final String TAG = "FocusActivity";
    private TextView tvTopicName, tvTimerDisplay, tvSessionCount, tvTotalTime;
    private ProgressBar timerProgressBar;
    private FloatingActionButton btnPausePlay;
    private View btnStop, btnSkip;
    
    private CountDownTimer countDownTimer;
    private long timeLeftInMillis;
    private long initialTimeInMillis;
    private boolean timerRunning = false;
    
    private String subjectName, topicName;
    private int subjectId, topicId, totalDurationMins;
    private String firebaseUid;
    private RequestQueue queue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.d(TAG, "onCreate: Starting FocusActivity");

        try {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );

            setContentView(R.layout.activity_focus);

            // Bind Views
            tvTopicName = findViewById(R.id.tvFocusTopicName);
            tvTimerDisplay = findViewById(R.id.tvTimerDisplay);
            tvSessionCount = findViewById(R.id.tvSessionCount);
            tvTotalTime = findViewById(R.id.tvTotalTime);
            timerProgressBar = findViewById(R.id.timerProgressBar);
            btnPausePlay = findViewById(R.id.btnPausePlay);
            btnStop = findViewById(R.id.btnStop);
            btnSkip = findViewById(R.id.btnSkip);
            ImageView btnClose = findViewById(R.id.btnClose);

            queue = Volley.newRequestQueue(this);
            firebaseUid = FirebaseAuth.getInstance().getUid();

            // Get Data from Intent
            Intent intent = getIntent();
            if (intent != null) {
                subjectId = intent.getIntExtra("subject_id", -1);
                topicId = intent.getIntExtra("topic_id", -1);
                subjectName = intent.getStringExtra("subject_name");
                topicName = intent.getStringExtra("topic_name");
                totalDurationMins = intent.getIntExtra("total_minutes", 25);
                
                // If there's remaining time from backend, use it
                int remainingSeconds = intent.getIntExtra("remaining_seconds", -1);
                if (remainingSeconds > 0) {
                    timeLeftInMillis = (long) remainingSeconds * 1000;
                    initialTimeInMillis = (long) totalDurationMins * 60 * 1000; // Keep original for progress bar
                } else {
                    initialTimeInMillis = (long) totalDurationMins * 60 * 1000;
                    timeLeftInMillis = initialTimeInMillis;
                }
                
                Log.d(TAG, "Data: Sub=" + subjectName + ", Topic=" + topicName + ", Mins=" + totalDurationMins + ", RemainingSec=" + remainingSeconds);
            }

            if (topicName != null) tvTopicName.setText(topicName);
            tvTotalTime.setText(totalDurationMins + "m");
            
            updateCountDownText();
            updateProgressBar();

            btnClose.setOnClickListener(v -> {
                saveProgressToBackend();
                finish();
            });
            
            btnPausePlay.setOnClickListener(v -> {
                if (timerRunning) {
                    pauseTimer();
                    saveProgressToBackend();
                } else {
                    startTimer();
                }
            });

            btnStop.setOnClickListener(v -> stopAndLogSession());
            btnSkip.setOnClickListener(v -> {
                saveProgressToBackend();
                finish();
            });

            getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    saveProgressToBackend();
                    finish();
                }
            });

            setupBottomNav();
            
            // Auto start timer
            startTimer();

        } catch (Exception e) {
            Log.e(TAG, "Error initializing FocusActivity", e);
            Toast.makeText(this, "Failed to start focus session: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void startTimer() {
        if (countDownTimer != null) countDownTimer.cancel();
        
        countDownTimer = new CountDownTimer(timeLeftInMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeftInMillis = millisUntilFinished;
                updateCountDownText();
                updateProgressBar();
            }

            @Override
            public void onFinish() {
                timerRunning = false;
                timeLeftInMillis = 0;
                updateCountDownText();
                updateProgressBar();
                btnPausePlay.setImageResource(R.drawable.ic_pomodoro);
                stopAndLogSession();
            }
        }.start();

        timerRunning = true;
        btnPausePlay.setImageResource(android.R.drawable.ic_media_pause);
    }

    private void pauseTimer() {
        if (countDownTimer != null) countDownTimer.cancel();
        timerRunning = false;
        btnPausePlay.setImageResource(android.R.drawable.ic_media_play);
    }

    private void updateCountDownText() {
        int totalSeconds = (int) (timeLeftInMillis / 1000);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        String timeLeftFormatted = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        tvTimerDisplay.setText(timeLeftFormatted);
    }

    private void updateProgressBar() {
        if (initialTimeInMillis == 0) return;
        int progress = (int) ((timeLeftInMillis * 1000) / initialTimeInMillis);
        timerProgressBar.setProgress(progress);
    }

    private void saveProgressToBackend() {
        if (topicId == -1 || firebaseUid == null) return;

        String url = "http://10.7.28.203:3000/api/subjects/saveRemainingTime";
        try {
            JSONObject body = new JSONObject();
            body.put("firebase_uid", firebaseUid);
            body.put("topic_id", topicId);
            body.put("remaining_seconds", (int) (timeLeftInMillis / 1000));

            queue.add(new JsonObjectRequest(Request.Method.POST, url, body, null, null));
        } catch (Exception e) {
            Log.e(TAG, "Error saving remaining time", e);
        }
    }

    private void stopAndLogSession() {
        pauseTimer();
        
        // Calculate exact seconds spent
        long millisSpent = (initialTimeInMillis - timeLeftInMillis);
        int secondsSpent = (int) (millisSpent / 1000);
        float minutesSpent = secondsSpent / 60.0f;

        if (secondsSpent < 5) {
            finish();
            return;
        }

        saveProgressToBackend(); // Also save the remaining time

        String url = "http://10.7.28.203:3000/api/subjects/saveSession";
        try {
            JSONObject body = new JSONObject();
            body.put("firebase_uid", firebaseUid);
            body.put("subject_id", subjectId);
            body.put("topic_id", topicId);
            body.put("minutes_spent", Math.round(minutesSpent)); // Rounding to nearest minute for session logging
            body.put("performance_score", 90); 

            queue.add(new JsonObjectRequest(Request.Method.POST, url, body,
                response -> {
                    Toast.makeText(this, "Session saved!", Toast.LENGTH_SHORT).show();
                    finish();
                },
                error -> {
                    Log.e(TAG, "Volley Error logging session", error);
                    finish();
                }
            ));
        } catch (Exception e) { 
            Log.e(TAG, "JSON Error", e);
            finish(); 
        }
    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_focus);
            bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    saveProgressToBackend();
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

    @Override
    protected void onPause() {
        super.onPause();
        saveProgressToBackend();
    }
}
