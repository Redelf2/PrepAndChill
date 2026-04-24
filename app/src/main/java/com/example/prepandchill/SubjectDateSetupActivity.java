package com.example.prepandchill;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class SubjectDateSetupActivity extends AppCompatActivity implements SubjectAdapter.OnSubjectClickListener {

    private List<Subject> subjectList;
    private SubjectAdapter adapter;
    private TextView tvSubjectsReady;
    private RecyclerView rvSubjects;
    private String selectedExam;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_date_setup);

        selectedExam = getIntent().getStringExtra("selectedExam");

        ImageView btnBack = findViewById(R.id.btnBack);
        rvSubjects = findViewById(R.id.rvSubjects);
        tvSubjectsReady = findViewById(R.id.tvSubjectsReady);
        View btnAddSubject = findViewById(R.id.btnAddSubject);
        MaterialButton btnSaveContinue = findViewById(R.id.btnSaveContinue);

        btnBack.setOnClickListener(v -> finish());

        subjectList = new ArrayList<>();
        subjectList.add(new Subject("Advanced Mathematics", "Set your exam date", true));
        subjectList.add(new Subject("Quantum Physics", "Set your exam date", true));

        adapter = new SubjectAdapter(subjectList, this);
        rvSubjects.setLayoutManager(new LinearLayoutManager(this));
        rvSubjects.setAdapter(adapter);

        btnAddSubject.setOnClickListener(v -> showAddSubjectDialog());

        btnSaveContinue.setOnClickListener(v -> {
            ArrayList<Subject> selectedSubjects = new ArrayList<>();
            for (Subject s : subjectList) {
                if (s.isSelected()) {
                    selectedSubjects.add(s);
                }
            }

            if (selectedSubjects.isEmpty()) {
                Toast.makeText(this, "Please select at least one subject", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(SubjectDateSetupActivity.this, SubjectAssessmentActivity.class);
            intent.putExtra("selectedSubjects", selectedSubjects);
            intent.putExtra("selectedExam", selectedExam);
            startActivity(intent);
        });

        updateUI();
    }

    private void showAddSubjectDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_subject, null);
        builder.setView(view);

        EditText etSubjectName = view.findViewById(R.id.etSubjectName);
        MaterialButton btnAdd = view.findViewById(R.id.btnAdd);

        AlertDialog dialog = builder.create();

        btnAdd.setOnClickListener(v -> {
            String name = etSubjectName.getText().toString().trim();
            if (!TextUtils.isEmpty(name)) {
                subjectList.add(new Subject(name, "Set your exam date", true));
                adapter.notifyItemInserted(subjectList.size() - 1);
                updateUI();
                dialog.dismiss();
            } else {
                Toast.makeText(this, "Enter subject name", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    @Override
    public void onSubjectClick(int position) {
        Subject subject = subjectList.get(position);
        subject.setSelected(!subject.isSelected());
        adapter.notifyItemChanged(position);
        updateUI();
    }

    @Override
    public void onCalendarClick(int position) {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this, (view, year1, month1, dayOfMonth) -> {
            String date = dayOfMonth + "/" + (month1 + 1) + "/" + year1;
            subjectList.get(position).setExamDate(date);
            subjectList.get(position).setSelected(true);
            adapter.notifyItemChanged(position);
            updateUI();
        }, year, month, day);

        datePickerDialog.show();
    }

    @Override
    public void onDeleteClick(int position) {
        subjectList.remove(position);
        adapter.notifyItemRemoved(position);
        adapter.notifyItemRangeChanged(position, subjectList.size());
        updateUI();
    }

    private void updateUI() {
        int count = 0;
        for (Subject s : subjectList) {
            if (s.isSelected()) count++;
        }
        tvSubjectsReady.setText(count + " subject" + (count == 1 ? "" : "s") + " ready");
    }
}