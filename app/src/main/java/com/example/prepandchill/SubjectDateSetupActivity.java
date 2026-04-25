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

import org.json.JSONObject;

import java.util.*;

public class SubjectDateSetupActivity extends AppCompatActivity implements SubjectAdapter.OnSubjectClickListener {

    private List<Subject> subjectList;
    private SubjectAdapter adapter;
    private TextView tvSubjectsReady;
    private RecyclerView rvSubjects;
    private String selectedExam;
    private RequestQueue queue;

    private final String BASE_URL = "http://10.7.28.203:3000/api/subjects";

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

        subjectList = new ArrayList<>();
        adapter = new SubjectAdapter(subjectList, this);
        rvSubjects.setLayoutManager(new LinearLayoutManager(this));
        rvSubjects.setAdapter(adapter);
        
        // Ensure RecyclerView doesn't conflict with ScrollView
        rvSubjects.setNestedScrollingEnabled(false);

        //  Load subjects from DB
        fetchSubjects(false);

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

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        
        updateUI();
    }


    private void fetchSubjects(boolean keepScrollPosition) {
        JsonArrayRequest request = new JsonArrayRequest(
                Request.Method.GET,
                BASE_URL,
                null,
                response -> {
                    // Preserve current scroll position so items don't "disappear" from view on refresh.
                    int firstVisible = -1;
                    RecyclerView.LayoutManager lm = rvSubjects.getLayoutManager();
                    if (keepScrollPosition && lm instanceof LinearLayoutManager) {
                        firstVisible = ((LinearLayoutManager) lm).findFirstVisibleItemPosition();
                    }
                    final int firstVisibleFinal = firstVisible;

                    subjectList.clear();

                    for (int i = 0; i < response.length(); i++) {
                        try {
                            JSONObject obj = response.getJSONObject(i);
                            subjectList.add(new Subject(obj.getString("name"), "Set your exam date", true));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    // If RecyclerView is inside a ScrollView, data changes don't always trigger a re-measure.
                    // Posting ensures we re-layout on the UI thread after adapter updates.
                    rvSubjects.post(() -> {
                        adapter.notifyDataSetChanged();
                        rvSubjects.requestLayout();
                        rvSubjects.invalidate();
                        if (keepScrollPosition && firstVisibleFinal >= 0) {
                            rvSubjects.scrollToPosition(firstVisibleFinal);
                        }
                        updateUI();
                    });
                },
                error -> Toast.makeText(this, "Fetch error: " + error.toString(), Toast.LENGTH_LONG).show()
        );

        queue.add(request);
    }

    // ADD SUBJECT TO DB + REFRESH
    private void addSubjectToDB(String name) {

        try {
            JSONObject body = new JSONObject();
            body.put("name", name);

            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.POST,
                    BASE_URL + "/add",
                    body,
                    response -> {
                        Toast.makeText(this, "Added to DB", Toast.LENGTH_SHORT).show();
                        // Append locally so the list keeps stacking without jumping.
                        rvSubjects.post(() -> {
                            int insertAt = subjectList.size();
                            subjectList.add(new Subject(name, "Set your exam date", true));
                            adapter.notifyItemInserted(insertAt);
                            rvSubjects.requestLayout();
                            rvSubjects.invalidate();
                            updateUI();
                        });
                    },
                    error -> Toast.makeText(this, "Add error: " + error.toString(), Toast.LENGTH_LONG).show()
            );

            queue.add(request);

        } catch (Exception e) {
            e.printStackTrace();
        }
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
                addSubjectToDB(name); // 🔥 SAVE TO DATABASE
                dialog.dismiss();
            } else {
                Toast.makeText(this, "Enter subject name", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    // Backwards-compatible call site (if you later add more callers).
    private void fetchSubjects() {
        fetchSubjects(false);
    }

    @Override
    public void onSubjectClick(int position) {
        if (position >= 0 && position < subjectList.size()) {
            Subject subject = subjectList.get(position);
            subject.setSelected(!subject.isSelected());
            adapter.notifyItemChanged(position);
            updateUI();
        }
    }

    @Override
    public void onCalendarClick(int position) {
        if (position >= 0 && position < subjectList.size()) {
            Calendar c = Calendar.getInstance();

            new DatePickerDialog(this, (view, y, m, d) -> {
                String date = d + "/" + (m + 1) + "/" + y;
                subjectList.get(position).setExamDate(date);
                subjectList.get(position).setSelected(true);
                adapter.notifyItemChanged(position);
                updateUI();
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        }
    }

    @Override
    public void onDeleteClick(int position) {
        if (position >= 0 && position < subjectList.size()) {
            subjectList.remove(position);
            adapter.notifyItemRemoved(position);
            // After removing, update positions of remaining items to avoid index issues
            adapter.notifyItemRangeChanged(position, subjectList.size());
            updateUI();
        }
    }

    private void updateUI() {
        int count = 0;
        for (Subject s : subjectList) if (s.isSelected()) count++;
        tvSubjectsReady.setText(count + " subject" + (count == 1 ? "" : "s") + " ready");
    }
}
