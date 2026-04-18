package com.example.prepandchill;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;

public class HomeActivity extends AppCompatActivity {

    private ArrayList<Subject> selectedSubjects;
    private RecyclerView rvHomeSubjects;
    private SubjectAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );

        setContentView(R.layout.activity_home);

        rvHomeSubjects = findViewById(R.id.rvHomeSubjects);
        selectedSubjects = (ArrayList<Subject>) getIntent().getSerializableExtra("selectedSubjects");

        if (selectedSubjects == null) {
            selectedSubjects = new ArrayList<>();
        }

        adapter = new SubjectAdapter(selectedSubjects, new SubjectAdapter.OnSubjectClickListener() {
            @Override
            public void onSubjectClick(int position) {}
            @Override
            public void onCalendarClick(int position) {}
        });

        rvHomeSubjects.setLayoutManager(new LinearLayoutManager(this));
        rvHomeSubjects.setAdapter(adapter);
    }
}