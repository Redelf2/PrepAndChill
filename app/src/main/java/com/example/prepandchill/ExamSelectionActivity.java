package com.example.prepandchill;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;

public class ExamSelectionActivity extends AppCompatActivity {

    String selectedOption = "GATE"; // default

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exam_selection);

        ImageView btnBack = findViewById(R.id.btnBack);
        FrameLayout optGate = findViewById(R.id.optionGate);
        FrameLayout optSemester = findViewById(R.id.optionSemester);
        FrameLayout optPlacements = findViewById(R.id.optionPlacements);
        FrameLayout optOther = findViewById(R.id.optionOther);
        MaterialButton btnContinue = findViewById(R.id.btnContinue);

        btnBack.setOnClickListener(v -> finish());

        View.OnClickListener optionClick = v -> {
            // Reset all to unselected
            optGate.setBackgroundResource(R.drawable.bg_option_unselected);
            optSemester.setBackgroundResource(R.drawable.bg_option_unselected);
            optPlacements.setBackgroundResource(R.drawable.bg_option_unselected);
            optOther.setBackgroundResource(R.drawable.bg_option_unselected);

            // Highlight selected
            v.setBackgroundResource(R.drawable.bg_option_selected);

            if (v.getId() == R.id.optionGate) selectedOption = "GATE";
            else if (v.getId() == R.id.optionSemester) selectedOption = "SEMESTER";
            else if (v.getId() == R.id.optionPlacements) selectedOption = "PLACEMENTS";
            else selectedOption = "OTHER";
        };

        optGate.setOnClickListener(optionClick);
        optSemester.setOnClickListener(optionClick);
        optPlacements.setOnClickListener(optionClick);
        optOther.setOnClickListener(optionClick);

        btnContinue.setOnClickListener(v -> {
            Intent intent = new Intent(ExamSelectionActivity.this, SubjectDateSetupActivity.class);
            startActivity(intent);
        });
    }
}
