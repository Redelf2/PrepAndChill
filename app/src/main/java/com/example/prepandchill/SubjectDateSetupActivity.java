package com.example.prepandchill;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.*;
import com.android.volley.toolbox.*;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONObject;

import java.util.*;

public class SubjectDateSetupActivity extends AppCompatActivity
        implements SubjectAdapter.OnSubjectClickListener {

    private List<Subject> subjectList;
    private SubjectAdapter adapter;
    private TextView tvSubjectsReady;
    private RecyclerView rvSubjects;
    private RequestQueue queue;

    private final String BASE_URL = "http://10.7.28.203:3000/api/subjects";

    private String firebaseUid;
    private String selectedExam;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_date_setup);

        selectedExam = getIntent().getStringExtra("selectedExam");

        rvSubjects = findViewById(R.id.rvSubjects);
        tvSubjectsReady = findViewById(R.id.tvSubjectsReady);
        View btnAddSubject = findViewById(R.id.btnAddSubject);
        MaterialButton btnSaveContinue = findViewById(R.id.btnSaveContinue);

        queue = Volley.newRequestQueue(this);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        firebaseUid = (user != null) ? user.getUid() : null;

        subjectList = new ArrayList<>();
        adapter = new SubjectAdapter(subjectList, this);

        rvSubjects.setLayoutManager(new LinearLayoutManager(this));
        rvSubjects.setAdapter(adapter);

        fetchSubjects();

        btnAddSubject.setOnClickListener(v -> showAddSubjectDialog());

        btnSaveContinue.setOnClickListener(v -> {

            ArrayList<Subject> selectedSubjects = new ArrayList<>();

            for (Subject s : subjectList) {
                if (s.isSelected()) selectedSubjects.add(s);
            }

            if (selectedSubjects.isEmpty()) {
                Toast.makeText(this, "Select at least one subject", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(this, SubjectAssessmentActivity.class);
            intent.putExtra("selectedSubjects", selectedSubjects);
            intent.putExtra("selectedExam", selectedExam);
            startActivity(intent);
        });

        updateUI();
    }

    // ========================
    // FETCH SUBJECTS (KEEP STATE)
    // ========================
    private void fetchSubjects() {

        Map<String, Subject> oldMap = new HashMap<>();
        for (Subject s : subjectList) {
            oldMap.put(s.getName(), s);
        }

        JsonArrayRequest request = new JsonArrayRequest(
                Request.Method.GET,
                BASE_URL + "?firebase_uid=" + firebaseUid,
                null,
                response -> {

                    subjectList.clear();

                    for (int i = 0; i < response.length(); i++) {
                        try {
                            JSONObject obj = response.getJSONObject(i);

                            String name = obj.getString("name");
                            String examDate = obj.optString("exam_date", "");

                            if (examDate == null || examDate.isEmpty()) {
                                examDate = "Set your exam date";
                            }

                            Subject old = oldMap.get(name);

                            boolean selected = old != null && old.isSelected();
                            String date = old != null ? old.getExamDate() : examDate;

                            subjectList.add(new Subject(name, date, selected));

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    adapter.notifyDataSetChanged();
                    updateUI();
                },
                error -> Toast.makeText(this, "Fetch error", Toast.LENGTH_SHORT).show()
        );

        queue.add(request);
    }

    // ========================
    // ADD SUBJECT (DB MASTER ONLY)
    // ========================
    private void addSubjectToDB(String name) {
        try {
            JSONObject body = new JSONObject();
            body.put("name", name);

            queue.add(new JsonObjectRequest(
                    Request.Method.POST,
                    BASE_URL + "/add",
                    body,
                    response -> fetchSubjects(),
                    error -> Toast.makeText(this, "Add failed", Toast.LENGTH_SHORT).show()
            ));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showAddSubjectDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_subject, null);
        builder.setView(view);

        EditText et = view.findViewById(R.id.etSubjectName);
        MaterialButton btn = view.findViewById(R.id.btnAdd);

        AlertDialog dialog = builder.create();

        btn.setOnClickListener(v -> {
            String name = et.getText().toString().trim();

            if (!TextUtils.isEmpty(name)) {
                addSubjectToDB(name);
                dialog.dismiss();
            } else {
                Toast.makeText(this, "Enter subject name", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    // ========================
    // SELECT / UNSELECT (LOCAL)
    // ========================
    @Override
    public void onSubjectClick(int position) {
        Subject s = subjectList.get(position);

        s.setSelected(!s.isSelected()); // toggle

        // optional: reset date if unselected
        if (!s.isSelected()) {
            s.setExamDate("Set your exam date");
        }

        adapter.notifyItemChanged(position);
        updateUI();
    }

    // ========================
    // SET DATE (LOCAL ONLY)
    // ========================
    @Override
    public void onCalendarClick(int position) {

        Calendar c = Calendar.getInstance();

        new DatePickerDialog(this, (view, y, m, d) -> {

            String date = String.format(Locale.US, "%04d-%02d-%02d", y, (m + 1), d);

            Subject s = subjectList.get(position);
            s.setExamDate(date);
            s.setSelected(true);

            adapter.notifyItemChanged(position);
            updateUI();

        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    // ========================
    // DELETE (LOCAL ONLY)
    // ========================
    @Override
    public void onDeleteClick(int position) {
        subjectList.remove(position);
        adapter.notifyItemRemoved(position);
        updateUI();
    }

    // ========================
    // UI UPDATE
    // ========================
    private void updateUI() {
        int count = 0;

        for (Subject s : subjectList) {
            if (s.isSelected()) count++;
        }

        tvSubjectsReady.setText(count + " subject" + (count == 1 ? "" : "s") + " ready");
    }
}