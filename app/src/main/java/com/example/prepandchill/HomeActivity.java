package com.example.prepandchill;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;

public class HomeActivity extends AppCompatActivity {

    private ArrayList<Subject> selectedSubjects;
    private RecyclerView rvTodayPlan;
    private HomeSubjectAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );

        setContentView(R.layout.activity_home);

        rvTodayPlan = findViewById(R.id.rvTodayPlan);
        selectedSubjects = (ArrayList<Subject>) getIntent().getSerializableExtra("selectedSubjects");

        if (selectedSubjects == null) {
            selectedSubjects = new ArrayList<>();
        }

        adapter = new HomeSubjectAdapter(selectedSubjects);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        rvTodayPlan.setLayoutManager(layoutManager);
        rvTodayPlan.setAdapter(adapter);

        TextView btnViewAll = findViewById(R.id.btnViewAll);
        if (btnViewAll != null) {
            btnViewAll.setOnClickListener(v -> {
                Intent intent = new Intent(HomeActivity.this, TimetableActivity.class);
                startActivity(intent);
            });
        }

        LinearLayout cvConfidenceMap = findViewById(R.id.cvConfidenceMap);
        if (cvConfidenceMap != null) {
            cvConfidenceMap.setOnClickListener(v -> {
                Intent intent = new Intent(HomeActivity.this, ConfidenceMapActivity.class);
                startActivity(intent);
            });
        }
    }
}