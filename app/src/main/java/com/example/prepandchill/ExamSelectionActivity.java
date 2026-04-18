package com.example.prepandchill;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;

public class ExamSelectionActivity extends AppCompatActivity {

    private List<ExamOption> examList;
    private ExamAdapter adapter;
    private String selectedExam = "GATE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exam_selection);

        ImageView btnBack = findViewById(R.id.btnBack);
        RecyclerView rvExamOptions = findViewById(R.id.rvExamOptions);
        MaterialButton btnContinue = findViewById(R.id.btnContinue);

        btnBack.setOnClickListener(v -> finish());

        examList = new ArrayList<>();
        examList.add(new ExamOption("🎓", "GATE", true));
        examList.add(new ExamOption("📖", "Semester", false));
        examList.add(new ExamOption("💼", "Placements", false));
        examList.add(new ExamOption("···", "Other", false));

        adapter = new ExamAdapter(examList, position -> {
            for (int i = 0; i < examList.size(); i++) {
                examList.get(i).setSelected(i == position);
            }
            selectedExam = examList.get(position).getName();
            adapter.notifyDataSetChanged();
        });

        rvExamOptions.setLayoutManager(new GridLayoutManager(this, 2));
        rvExamOptions.setAdapter(adapter);

        btnContinue.setOnClickListener(v -> {
            Intent intent = new Intent(ExamSelectionActivity.this, SubjectDateSetupActivity.class);
            startActivity(intent);
        });
    }
}