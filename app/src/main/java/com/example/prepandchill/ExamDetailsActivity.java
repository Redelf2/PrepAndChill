package com.example.prepandchill;

import android.os.Bundle;                          // For savedInstanceState in onCreate
import android.view.LayoutInflater;                // For inflating XML card layouts
import android.view.View;                          // Base class for UI components
import android.widget.ImageView;                   // Back button icon
import android.widget.LinearLayout;                // Container for subject cards
import android.widget.ProgressBar;                 // Horizontal progress bar per subject
import android.widget.TextView;                    // Text displays
import androidx.appcompat.app.AppCompatActivity;   // Base activity
import java.util.ArrayList;                        // Dynamic array for subject list

/**
 * ExamDetailsActivity — Displays a READ-ONLY per-subject breakdown of exam prep status.
 *
 * Shows:
 * - Exam title, overall average proficiency %, earliest exam date
 * - For each subject: name, exam date, proficiency %, progress bar, analysis tag
 * - Analysis tags: "On Track 🚀" (≥70%), "Needs Focus ⚠️" (≥40%), "At Risk 🚨" (<40%)
 *
 * This screen makes NO API calls — it purely displays data from Intent extras.
 * All data comes from the selectedSubjects ArrayList passed by HomeActivity.
 *
 * Flow: HomeActivity → [this screen] (read-only, press back to return)
 */
public class ExamDetailsActivity extends AppCompatActivity {

    // ========================
    // FIELD VARIABLES
    // ========================

    private ArrayList<Subject> selectedSubjects;  // Subjects with proficiency + exam date data
    private String selectedExamName;              // e.g., "Semester" or "GATE" — shown as title

    // ========================
    // onCreate — ENTRY POINT
    // ========================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exam_details);

        // Extract data from the Intent that launched this Activity
        // These were put by HomeActivity's "View Details" button click
        selectedSubjects = (ArrayList<Subject>) getIntent().getSerializableExtra("selectedSubjects");
        selectedExamName = getIntent().getStringExtra("selectedExam");

        // Back button → close this Activity and return to HomeActivity
        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        // Set exam title (e.g., "Semester" or fallback "Exam Details")
        TextView tvExamTitle = findViewById(R.id.tvExamTitle);
        tvExamTitle.setText(selectedExamName != null ? selectedExamName : "Exam Details");

        // Find container views for dynamic content
        LinearLayout llSubjectsContainer = findViewById(R.id.llSubjectsContainer);
        TextView tvOverallProgress = findViewById(R.id.tvOverallProgress);   // Average % display
        TextView tvLatestExamDate = findViewById(R.id.tvLatestExamDate);     // Earliest exam date

        if (selectedSubjects != null && !selectedSubjects.isEmpty()) {

            int totalProficiency = 0;
            String earliestDate = "";
            // SimpleDateFormat for parsing "2026-06-15" string into Date objects
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
            java.util.Date minDate = null;
            
            // Loop through all subjects to:
            // 1. Sum proficiencies for average calculation
            // 2. Add a detail card for each subject
            // 3. Find the earliest exam date
            for (Subject subject : selectedSubjects) {
                totalProficiency += subject.getProficiency();

                // Create and add a visual card for this subject
                addSubjectDetailCard(llSubjectsContainer, subject);
                
                // Find the EARLIEST exam date among all subjects
                try {
                    String examDateStr = subject.getExamDate();  // e.g., "2026-06-15"
                    if (examDateStr != null && !examDateStr.isEmpty()) {
                        java.util.Date d = sdf.parse(examDateStr); // Parse string → Date object
                        if (minDate == null || d.before(minDate)) {
                            // d.before(minDate) = true if d is earlier than current minimum
                            minDate = d;
                            earliestDate = examDateStr;
                        }
                    }
                } catch (Exception e) {
                    // If date parsing fails (bad format), use raw string as fallback
                    if (earliestDate.isEmpty()) earliestDate = subject.getExamDate();
                }
            }
            
            // Calculate and display AVERAGE proficiency
            int avg = totalProficiency / selectedSubjects.size();
            tvOverallProgress.setText(avg + "%");  // e.g., "65%"

            // Display the earliest upcoming exam date
            if (!earliestDate.isEmpty()) {
                tvLatestExamDate.setText(earliestDate);  // e.g., "2026-06-15"
            } else {
                tvLatestExamDate.setText("TBD"); // No dates set yet
            }
        }
    }

    // =====================================================================
    // addSubjectDetailCard() — Inflates and populates ONE subject's detail card
    // Shows: name, exam date, proficiency %, progress bar, and a color-coded
    // analysis tag based on proficiency level.
    // =====================================================================
    private void addSubjectDetailCard(LinearLayout container, Subject subject) {
        // Inflate the card layout from XML
        View card = LayoutInflater.from(this).inflate(R.layout.item_subject_detail, container, false);
        
        // Find views inside this card
        TextView tvName = card.findViewById(R.id.tvSubjectName);
        TextView tvDate = card.findViewById(R.id.tvSubjectDate);
        TextView tvPercent = card.findViewById(R.id.tvSubjectPercent);
        ProgressBar progressBar = card.findViewById(R.id.pbSubjectProgress);
        TextView tvAnalysis = card.findViewById(R.id.tvAnalysisTag);

        // Populate with subject data
        tvName.setText(subject.getName());                      // "DBMS"
        tvDate.setText("Exam: " + subject.getExamDate());       // "Exam: 2026-06-15"
        tvPercent.setText(subject.getProficiency() + "%");       // "65%"
        progressBar.setProgress(subject.getProficiency());      // Fill bar to 65%
        
        // ANALYSIS TAG — Color-coded assessment based on proficiency thresholds
        // ≥70% = "On Track 🚀" (Green)  — student is well-prepared
        // ≥40% = "Needs Focus ⚠️" (Yellow) — needs more study time
        // <40% = "At Risk 🚨" (Red)    — critically behind
        if (tvAnalysis != null) {
            if (subject.getProficiency() >= 70) {
                tvAnalysis.setText("On Track 🚀");
                tvAnalysis.setTextColor(android.graphics.Color.parseColor("#00C853")); // Green
                tvAnalysis.setBackgroundResource(R.drawable.bg_chip_unselected);
            } else if (subject.getProficiency() >= 40) {
                tvAnalysis.setText("Needs Focus ⚠️");
                tvAnalysis.setTextColor(android.graphics.Color.parseColor("#FFC107")); // Amber/Yellow
                tvAnalysis.setBackgroundResource(R.drawable.bg_chip_unselected);
            } else {
                tvAnalysis.setText("At Risk 🚨");
                tvAnalysis.setTextColor(android.graphics.Color.parseColor("#F44336")); // Red
                tvAnalysis.setBackgroundResource(R.drawable.bg_chip_unselected);
            }
        }

        // Add the completed card to the scrollable container
        container.addView(card);
    }
}
