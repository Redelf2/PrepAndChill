package com.example.prepandchill;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;

public class SubjectAssessmentActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.acitivity_subject_assesment);

        ImageView btnBack = findViewById(R.id.btnBack);
        MaterialButton btnGenerate = findViewById(R.id.btnGenerate);

        btnBack.setOnClickListener(v -> finish());

        btnGenerate.setOnClickListener(v -> {
            Toast.makeText(this, "Generating your personalized study plan...", Toast.LENGTH_LONG).show();
        });
    }
}