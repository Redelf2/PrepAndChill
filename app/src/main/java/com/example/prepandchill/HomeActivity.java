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
import java.util.List;

public class HomeActivity extends AppCompatActivity {

    private RecyclerView rvTodayPlan;
    private HomeSubjectAdapter adapter;
    private List<Subject> displayedSubjects;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Make the layout go behind the status bar for a modern look
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );

        setContentView(R.layout.activity_home);

        rvTodayPlan = findViewById(R.id.rvTodayPlan);
        displayedSubjects = new ArrayList<>();

        // Add default subjects if needed, or get from intent
        ArrayList<Subject> incomingSubjects = (ArrayList<Subject>) getIntent().getSerializableExtra("selectedSubjects");
        
        if (incomingSubjects != null && !incomingSubjects.isEmpty()) {
            displayedSubjects.addAll(incomingSubjects);
        } else {
            // Default subjects to show if none are passed
            displayedSubjects.add(new Subject("Mobile App Development", "2024-12-20", true));
            displayedSubjects.add(new Subject("Data Communication", "2024-12-21", true));
        }

        adapter = new HomeSubjectAdapter(displayedSubjects);
        rvTodayPlan.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvTodayPlan.setAdapter(adapter);

        TextView btnViewAll = findViewById(R.id.btnViewAll);
        if (btnViewAll != null) {
            btnViewAll.setOnClickListener(v -> {
                Intent intent = new Intent(HomeActivity.this, TimetableActivity.class);
                startActivity(intent);
            });
        }

        // Link the Confidence Map graph container to the ConfidenceMapActivity
        LinearLayout chartContainer = findViewById(R.id.chartContainer);
        if (chartContainer != null) {
            chartContainer.setOnClickListener(v -> {
                Intent intent = new Intent(HomeActivity.this, ConfidenceMapActivity.class);
                startActivity(intent);
            });
        }
    }
}