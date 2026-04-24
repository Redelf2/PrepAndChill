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
            String latestDate = "";
            
            for (Subject subject : selectedSubjects) {
                totalProficiency += subject.getProficiency();
                addSubjectDetailCard(llSubjectsContainer, subject);
                
                // Simple logic for "latest" date (ideally would parse and compare)
                latestDate = subject.getExamDate(); 
            }
            
            int avg = totalProficiency / selectedSubjects.size();
            tvOverallProgress.setText(avg + "%");
            tvLatestExamDate.setText(latestDate);
        }
    }

    private void addSubjectDetailCard(LinearLayout container, Subject subject) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_subject_detail, container, false);
        
        TextView tvName = card.findViewById(R.id.tvSubjectName);
        TextView tvDate = card.findViewById(R.id.tvSubjectDate);
        TextView tvPercent = card.findViewById(R.id.tvSubjectPercent);
        ProgressBar progressBar = card.findViewById(R.id.pbSubjectProgress);

        tvName.setText(subject.getName());
        tvDate.setText("Exam: " + subject.getExamDate());
        tvPercent.setText(subject.getProficiency() + "%");
        progressBar.setProgress(subject.getProficiency());

        container.addView(card);
    }
}