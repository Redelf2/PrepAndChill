package com.example.prepandchill;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;

public class SubjectDateSetupActivity extends AppCompatActivity {

    // Track checked state for each subject
    boolean[] checked = {true, true, false, false};

    View checkbox1, checkbox2, checkbox3, checkbox4;
    LinearLayout subjectRow1, subjectRow2, subjectRow3, subjectRow4;
    TextView tvSubjectsReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_date_setup);

        // Back button
        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        // Checkboxes
        checkbox1 = findViewById(R.id.checkbox1);
        checkbox2 = findViewById(R.id.checkbox2);
        checkbox3 = findViewById(R.id.checkbox3);
        checkbox4 = findViewById(R.id.checkbox4);

        // Subject rows (clicking the whole row toggles checkbox)
        subjectRow1 = findViewById(R.id.subjectRow1);
        subjectRow2 = findViewById(R.id.subjectRow2);
        subjectRow3 = findViewById(R.id.subjectRow3);
        subjectRow4 = findViewById(R.id.subjectRow4);

        // Subject ready count label
        tvSubjectsReady = findViewById(R.id.tvSubjectsReady);

        // Set click listeners on each row
        subjectRow1.setOnClickListener(v -> toggleCheckbox(0, checkbox1, subjectRow1));
        subjectRow2.setOnClickListener(v -> toggleCheckbox(1, checkbox2, subjectRow2));
        subjectRow3.setOnClickListener(v -> toggleCheckbox(2, checkbox3, subjectRow3));
        subjectRow4.setOnClickListener(v -> toggleCheckbox(3, checkbox4, subjectRow4));

        // Add New Subject button
        LinearLayout btnAddSubject = findViewById(R.id.btnAddSubject);
        btnAddSubject.setOnClickListener(v -> {
            // TODO: open add subject dialog
        });

        // Save and Continue button
        MaterialButton btnSaveContinue = findViewById(R.id.btnSaveContinue);
        btnSaveContinue.setOnClickListener(v -> {

        });

        // Set initial UI state
        updateUI();
    }

    private void toggleCheckbox(int index, View checkbox, LinearLayout row) {
        checked[index] = !checked[index];

        if (checked[index]) {
            checkbox.setBackgroundResource(R.drawable.bg_checkbox_checked);
            row.setAlpha(1.0f);
        } else {
            checkbox.setBackgroundResource(R.drawable.bg_checkbox_unchecked);
            row.setAlpha(0.7f);
        }

        updateUI();
    }

    private void updateUI() {
        int count = 0;
        for (boolean b : checked) {
            if (b) count++;
        }
        tvSubjectsReady.setText(count + " subject" + (count == 1 ? "" : "s") + " ready");
    }
}