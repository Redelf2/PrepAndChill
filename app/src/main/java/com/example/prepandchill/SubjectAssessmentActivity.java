package com.example.prepandchill;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;

public class SubjectAssessmentActivity extends AppCompatActivity {

    private LinearLayout container;
    private ArrayList<Subject> selectedSubjects;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_assessment);

        container = findViewById(R.id.llAssessmentContainer);
        ImageView btnBack = findViewById(R.id.btnBack);
        MaterialButton btnGenerate = findViewById(R.id.btnGenerate);

        btnBack.setOnClickListener(v -> finish());

        selectedSubjects = (ArrayList<Subject>) getIntent().getSerializableExtra("selectedSubjects");

        if (selectedSubjects != null) {
            for (Subject subject : selectedSubjects) {
                addSubjectCard(subject);
            }
        }

        btnGenerate.setOnClickListener(v -> {
            Toast.makeText(this, "Generating your personalized study plan...", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(SubjectAssessmentActivity.this, HomeActivity.class);
            intent.putExtra("selectedSubjects", selectedSubjects);
            startActivity(intent);
            finish();
        });
    }

    private void addSubjectCard(Subject subject) {
        View cardView = LayoutInflater.from(this).inflate(R.layout.item_assessment_card, container, false);

        LinearLayout header = cardView.findViewById(R.id.headerSubject);
        LinearLayout details = cardView.findViewById(R.id.layoutDetails);
        ImageView arrow = cardView.findViewById(R.id.ivArrow);
        TextView tvName = cardView.findViewById(R.id.tvSubjectName);
        SeekBar seekBar = cardView.findViewById(R.id.seekBarProficiency);
        TextView tvPercent = cardView.findViewById(R.id.tvPercent);

        tvName.setText(subject.getName());
        
        header.setOnClickListener(v -> {
            if (details.getVisibility() == View.VISIBLE) {
                details.setVisibility(View.GONE);
                arrow.setRotation(270);
            } else {
                details.setVisibility(View.VISIBLE);
                arrow.setRotation(0);
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvPercent.setText(progress + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        container.addView(cardView);
    }
}