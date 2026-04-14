package com.example.prepandchill;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;

public class SubjectAssessmentActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_assessment);

        ImageView btnBack = findViewById(R.id.btnBack);
        MaterialButton btnGenerate = findViewById(R.id.btnGenerate);
        
        LinearLayout headerMath = findViewById(R.id.headerMath);
        LinearLayout layoutDetailsMath = findViewById(R.id.layoutDetailsMath);
        ImageView ivArrowMath = findViewById(R.id.ivArrowMath);

        LinearLayout headerPhysics = findViewById(R.id.headerPhysics);
        LinearLayout layoutDetailsPhysics = findViewById(R.id.layoutDetailsPhysics);
        ImageView ivArrowPhysics = findViewById(R.id.ivArrowPhysics);

        LinearLayout headerAlgorithm = findViewById(R.id.headerAlgorithm);
        LinearLayout layoutDetailsAlgorithm = findViewById(R.id.layoutDetailsAlgorithm);
        ImageView ivArrowAlgorithm = findViewById(R.id.ivArrowAlgorithm);

        SeekBar seekBarLinearAlgebra = findViewById(R.id.seekBarLinearAlgebra);
        TextView tvLinearAlgebraPercent = findViewById(R.id.tvLinearAlgebraPercent);
        SeekBar seekBarMultivariable = findViewById(R.id.seekBarMultivariable);
        TextView tvMultivariablePercent = findViewById(R.id.tvMultivariablePercent);

        btnBack.setOnClickListener(v -> finish());

        headerMath.setOnClickListener(v -> toggleVisibility(layoutDetailsMath, ivArrowMath));
        headerPhysics.setOnClickListener(v -> toggleVisibility(layoutDetailsPhysics, ivArrowPhysics));
        headerAlgorithm.setOnClickListener(v -> toggleVisibility(layoutDetailsAlgorithm, ivArrowAlgorithm));

        seekBarLinearAlgebra.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvLinearAlgebraPercent.setText(progress + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekBarMultivariable.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvMultivariablePercent.setText(progress + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnGenerate.setOnClickListener(v -> {
            Toast.makeText(this, "Generating your personalized study plan...", Toast.LENGTH_SHORT).show();
            
            // Navigate to HomeActivity
            Intent intent = new Intent(SubjectAssessmentActivity.this, HomeActivity.class);
            // Clear activity stack so user doesn't go back to assessment after plan is generated
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void toggleVisibility(LinearLayout layout, ImageView arrow) {
        if (layout.getVisibility() == View.VISIBLE) {
            layout.setVisibility(View.GONE);
            arrow.setRotation(270);
        } else {
            layout.setVisibility(View.VISIBLE);
            arrow.setRotation(0);
        }
    }
}