package com.example.prepandchill;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;

public class ExamDetailsActivity extends AppCompatActivity {

    private ArrayList<Subject> selectedSubjects;
    private String selectedExamName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exam_details);

        selectedSubjects = (ArrayList<Subject>) getIntent().getSerializableExtra("selectedSubjects");
        selectedExamName = getIntent().getStringExtra("selectedExam");

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        TextView tvExamTitle = findViewById(R.id.tvExamTitle);
        tvExamTitle.setText(selectedExamName != null ? selectedExamName : "Exam Details");

        LinearLayout llSubjectsContainer = findViewById(R.id.llSubjectsContainer);
        TextView tvOverallProgress = findViewById(R.id.tvOverallProgress);
        TextView tvLatestExamDate = findViewById(R.id.tvLatestExamDate);

        if (selectedSubjects != null && !selectedSubjects.isEmpty()) {
            int totalProficiency = 0;
            String earliestDate = "";
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
            java.util.Date minDate = null;
            
            for (Subject subject : selectedSubjects) {
                totalProficiency += subject.getProficiency();
                addSubjectDetailCard(llSubjectsContainer, subject);
                
                try {
                    String examDateStr = subject.getExamDate();
                    if (examDateStr != null && !examDateStr.isEmpty()) {
                        java.util.Date d = sdf.parse(examDateStr);
                        if (minDate == null || d.before(minDate)) {
                            minDate = d;
                            earliestDate = examDateStr;
                        }
                    }
                } catch (Exception e) {
                    // Ignore parsing errors, keep previous
                    if (earliestDate.isEmpty()) earliestDate = subject.getExamDate();
                }
            }
            
            int avg = totalProficiency / selectedSubjects.size();
            tvOverallProgress.setText(avg + "%");
            if (!earliestDate.isEmpty()) {
                tvLatestExamDate.setText(earliestDate);
            } else {
                tvLatestExamDate.setText("TBD");
            }
        }
    }

    private void addSubjectDetailCard(LinearLayout container, Subject subject) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_subject_detail, container, false);
        
        TextView tvName = card.findViewById(R.id.tvSubjectName);
        TextView tvDate = card.findViewById(R.id.tvSubjectDate);
        TextView tvPercent = card.findViewById(R.id.tvSubjectPercent);
        ProgressBar progressBar = card.findViewById(R.id.pbSubjectProgress);
        TextView tvAnalysis = card.findViewById(R.id.tvAnalysisTag);

        tvName.setText(subject.getName());
        tvDate.setText("Exam: " + subject.getExamDate());
        tvPercent.setText(subject.getProficiency() + "%");
        progressBar.setProgress(subject.getProficiency());
        
        if (tvAnalysis != null) {
            if (subject.getProficiency() >= 70) {
                tvAnalysis.setText("On Track 🚀");
                tvAnalysis.setTextColor(android.graphics.Color.parseColor("#00C853"));
                tvAnalysis.setBackgroundResource(R.drawable.bg_chip_unselected);
            } else if (subject.getProficiency() >= 40) {
                tvAnalysis.setText("Needs Focus ⚠️");
                tvAnalysis.setTextColor(android.graphics.Color.parseColor("#FFC107"));
                tvAnalysis.setBackgroundResource(R.drawable.bg_chip_unselected);
            } else {
                tvAnalysis.setText("At Risk 🚨");
                tvAnalysis.setTextColor(android.graphics.Color.parseColor("#F44336"));
                tvAnalysis.setBackgroundResource(R.drawable.bg_chip_unselected);
            }
        }

        container.addView(card);
    }
}
