package com.example.prepandchill;

import android.os.Bundle;
import android.widget.ImageView;
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
        
        SeekBar seekBarLinearAlgebra = findViewById(R.id.seekBarLinearAlgebra);
        TextView tvLinearAlgebraPercent = findViewById(R.id.tvLinearAlgebraPercent);
        
        SeekBar seekBarMultivariable = findViewById(R.id.seekBarMultivariable);
        TextView tvMultivariablePercent = findViewById(R.id.tvMultivariablePercent);

        btnBack.setOnClickListener(v -> finish());

        seekBarLinearAlgebra.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvLinearAlgebraPercent.setText(progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekBarMultivariable.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvMultivariablePercent.setText(progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnGenerate.setOnClickListener(v -> {
            Toast.makeText(this, "Generating your personalized study plan...", Toast.LENGTH_LONG).show();
        });
    }
}