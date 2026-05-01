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
import java.util.Locale;

public class SubjectDateSetupActivity extends AppCompatActivity implements SubjectAdapter.OnSubjectClickListener {

    private List<Subject> subjectList;
    private SubjectAdapter adapter;
    private TextView tvSubjectsReady;
    private RecyclerView rvSubjects;
    private String selectedExam;
    private RequestQueue queue;

    private final String BASE_URL = "http://10.7.28.203:3000/api/subjects";
    private String firebaseUid;
    private String firebaseEmail;
    private String firebaseUsername;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_date_setup);

        selectedExam = getIntent().getStringExtra("selectedExam"); //GET SELECTED EXAM FROM PREVIOUS ACTIVITY


        rvSubjects = findViewById(R.id.rvSubjects);
        tvSubjectsReady = findViewById(R.id.tvSubjectsReady);
        View btnAddSubject = findViewById(R.id.btnAddSubject);
        MaterialButton btnSaveContinue = findViewById(R.id.btnSaveContinue);

        queue = Volley.newRequestQueue(this); //SETUP VOLLEY REQUEST QUEUE

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser(); //GET CURRENT USER
        firebaseUid = (user != null) ? user.getUid() : null; //GET UID
        firebaseEmail = (user != null) ? user.getEmail() : null; //GET EMAIL
        firebaseUsername = (user != null) ? user.getDisplayName() : null; //GET USERNAME


        subjectList = new ArrayList<>();
        adapter = new SubjectAdapter(subjectList, this); //SETUP ADAPTER
        rvSubjects.setLayoutManager(new LinearLayoutManager(this)); //SETUP RECYCLER VIEW WITH LINEAR LAYOUT
        rvSubjects.setAdapter(adapter); //ADAPTER IS SETUPED
        rvSubjects.setNestedScrollingEnabled(true); //DISABLE SCROLLING

        //  Load subjects from DB
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

            Intent intent = new Intent(this, SubjectAssessmentActivity.class); //NEXT ACTIVITY
            intent.putExtra("selectedSubjects", selectedSubjects); //SEND SELECTED SUBJECTS TO NEXT ACTIVITY
            intent.putExtra("selectedExam", selectedExam); //SEND SELECTED EXAM TO NEXT ACTIVITY
            startActivity(intent); //START NEXT ACTIVITY
        });

        updateUI(); //UPDATE UI AFTER FETCHING SUBJECTS FROM DB
    }


    private void fetchSubjects() {
        if (firebaseUid == null) {
            Toast.makeText(this, "Please login again (missing user).", Toast.LENGTH_LONG).show();
            return;
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
                            String examDate = obj.optString("exam_date", "");
                            if (examDate == null || examDate.equals("null") || examDate.isEmpty()) {
                                examDate = "Set your exam date";
                            }
                            subjectList.add(new Subject(obj.getString("name"), examDate, true));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    adapter.notifyDataSetChanged();
                },
                error -> Toast.makeText(this, "Fetch error: " + error.toString(), Toast.LENGTH_LONG).show()
        );

        queue.add(request); //ADD REQUEST TO QUEUE
    }

    // ADD SUBJECT TO DB + REFRESH
    private void addSubjectToDB(String name) {

        try {
            if (firebaseUid == null) {
                Toast.makeText(this, "Please login again (missing user).", Toast.LENGTH_LONG).show();
                return;
            }

            JSONObject body = new JSONObject();
            body.put("name", name);
            body.put("firebase_uid", firebaseUid);
            if (firebaseEmail != null) body.put("email", firebaseEmail);
            if (firebaseUsername != null) body.put("username", firebaseUsername);

            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.POST,
                    BASE_URL + "/add",
                    body,
                    response -> {
                        Toast.makeText(this, response.optString("message", "Saved"), Toast.LENGTH_SHORT).show();
                        fetchSubjects(); // refresh list
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
                addSubjectToDB(name); //  SAVE TO DATABASE
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
        Calendar c = Calendar.getInstance();

        new DatePickerDialog(this, (view, y, m, d) -> {
            // Save in DB format: YYYY-MM-DD
            String dbDate = String.format(Locale.US, "%04d-%02d-%02d", y, (m + 1), d);
            subjectList.get(position).setExamDate(dbDate);
            subjectList.get(position).setSelected(true);
            adapter.notifyItemChanged(position);
            updateUI();

            saveExamDateToDB(subjectList.get(position).getName(), dbDate);
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void saveExamDateToDB(String subjectName, String examDate) {
        if (firebaseUid == null) {
            Toast.makeText(this, "Please login again (missing user).", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            JSONObject body = new JSONObject();
            body.put("firebase_uid", firebaseUid);
            body.put("subject_name", subjectName);
            body.put("exam_date", examDate);

            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.POST,
                    BASE_URL + "/updateExamDate",
                    body,
                    response -> Toast.makeText(this, response.optString("message", "Exam date saved"), Toast.LENGTH_SHORT).show(),
                    error -> Toast.makeText(this, "Save date error: " + error.toString(), Toast.LENGTH_LONG).show()
            );

            queue.add(request);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void deleteSubjectFromDB(String subjectName) {
        if (firebaseUid == null) {
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
            return;
        }

        String url = BASE_URL + "/delete?firebase_uid=" + firebaseUid + "&subject_name=" + subjectName;

        StringRequest request = new StringRequest(
                Request.Method.DELETE,
                url,
                response -> {
                    Toast.makeText(this, "Deleted successfully", Toast.LENGTH_SHORT).show();
                    fetchSubjects(); // 🔥 refresh from DB
                },
                error -> Toast.makeText(this, "Delete error: " + error.toString(), Toast.LENGTH_LONG).show()
        );

        queue.add(request);
    }
    @Override
    public void onDeleteClick(int position) {
        Subject subject = subjectList.get(position);

        deleteSubjectFromDB(subject.getName());
    }

    private void updateUI() {
        int count = 0;
        for (Subject s : subjectList) if (s.isSelected()) count++;
        tvSubjectsReady.setText(count + " subject" + (count == 1 ? "" : "s") + " ready");
    }
}
