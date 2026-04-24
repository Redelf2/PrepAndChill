package com.example.prepandchill;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
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
            if (examList.get(position).getName().equals("Other")) {
                showCustomExamDialog(position);
            } else {
                updateSelection(position);
            }
        });

        rvExamOptions.setLayoutManager(new GridLayoutManager(this, 2));
        rvExamOptions.setAdapter(adapter);

        btnContinue.setOnClickListener(v -> {
            Intent intent = new Intent(ExamSelectionActivity.this, SubjectDateSetupActivity.class);
            intent.putExtra("selectedExam", selectedExam);
            startActivity(intent);
        });
    }

    private void updateSelection(int position) {
        for (int i = 0; i < examList.size(); i++) {
            examList.get(i).setSelected(i == position);
        }
        selectedExam = examList.get(position).getName();
        adapter.notifyDataSetChanged();
    }

    private void showCustomExamDialog(int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_custom_exam, null);
        builder.setView(view);

        EditText etCustomExam = view.findViewById(R.id.etCustomExam);
        MaterialButton btnDone = view.findViewById(R.id.btnDone);

        AlertDialog dialog = builder.create();

        btnDone.setOnClickListener(v -> {
            String name = etCustomExam.getText().toString().trim();
            if (!TextUtils.isEmpty(name)) {
                examList.get(position).setName(name);
                updateSelection(position);
                dialog.dismiss();
            } else {
                Toast.makeText(this, "Please enter exam name", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }
}