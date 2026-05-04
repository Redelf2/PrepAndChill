package com.example.prepandchill;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SubjectAssessmentActivity extends AppCompatActivity {

    private static final class QuizCardRefs {
        Subject subject;
        SeekBar seekBar;
        TextView tvPercent;
    }

    private LinearLayout container;
    private ArrayList<Subject> selectedSubjects;
    private RequestQueue queue;
    private String firebaseUid;
    private String selectedExamName;

    private final Map<String, QuizCardRefs> quizCardRefsBySubject = new HashMap<>();
    private ActivityResultLauncher<Intent> quizLauncher;

    private final String BASE_IP = "10.7.28.203";
    private final String SUBJECTS_BASE_URL = "http://" + BASE_IP + ":3000/api/subjects";
    private final String PLAN_URL = "http://" + BASE_IP + ":3000/api/plan/generatePlan";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        quizLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                        return;
                    }
                    String name = result.getData().getStringExtra(SubjectQuizActivity.RESULT_SUBJECT_NAME);
                    int conf = result.getData().getIntExtra(SubjectQuizActivity.RESULT_CONFIDENCE, -1);
                    int diff = result.getData().getIntExtra(SubjectQuizActivity.RESULT_DIFFICULTY, -1);
                    if (name == null || conf < 0) return;

                    QuizCardRefs refs = quizCardRefsBySubject.get(name);
                    if (refs == null) return;

                    refs.subject.setProficiency(conf);
                    refs.seekBar.setProgress(conf);
                    refs.tvPercent.setText(conf + "%");
                    if (diff >= 1 && diff <= 3) {
                        refs.subject.setDifficulty(diff);
                    }

                    Toast.makeText(this,
                            "Proficiency updated from quiz for " + name,
                            Toast.LENGTH_SHORT).show();
                });

        setContentView(R.layout.activity_subject_assessment);

        container = findViewById(R.id.llAssessmentContainer);
        MaterialButton btnGenerate = findViewById(R.id.btnGenerate);

        ImageView btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        queue = Volley.newRequestQueue(this);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        firebaseUid = (user != null) ? user.getUid() : null;

        selectedSubjects = (ArrayList<Subject>) getIntent().getSerializableExtra("selectedSubjects");
        selectedExamName = getIntent().getStringExtra("selectedExam");

        if (selectedSubjects == null || selectedSubjects.isEmpty()) {
            Toast.makeText(this, "No subjects found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        for (Subject s : selectedSubjects) {
            addSubjectCard(s);
        }

        btnGenerate.setOnClickListener(v -> completeSetup());
    }

    // ========================
    // SUBJECT CARD UI
    // ========================
    private void addSubjectCard(Subject subject) {

        View cardView = LayoutInflater.from(this)
                .inflate(R.layout.item_assessment_card, container, false);

        LinearLayout header = cardView.findViewById(R.id.headerSubject);
        LinearLayout details = cardView.findViewById(R.id.layoutDetails);
        ImageView arrow = cardView.findViewById(R.id.ivArrow);

        TextView tvName = cardView.findViewById(R.id.tvSubjectName);
        SeekBar seekBar = cardView.findViewById(R.id.seekBarProficiency);
        TextView tvPercent = cardView.findViewById(R.id.tvPercent);
        LinearLayout topicsContainer = cardView.findViewById(R.id.llTopicsContainer);

        tvName.setText(subject.getName());

        // collapse by default
        details.setVisibility(View.GONE);
        arrow.setRotation(270);

        // expand / collapse
        header.setOnClickListener(v -> {
            if (details.getVisibility() == View.VISIBLE) {
                details.setVisibility(View.GONE);
                arrow.setRotation(270);
            } else {
                details.setVisibility(View.VISIBLE);
                arrow.setRotation(90);

                if (topicsContainer.getChildCount() == 0) {
                    fetchTopics(subject.getName(), topicsContainer);
                }
            }
        });

        // 🔴 IMPORTANT FIX: NO DEFAULT 50%
        int initial = subject.getProficiency(); // should be 0 initially

        seekBar.setProgress(initial);

        if (initial == 0) {
            tvPercent.setText("Not Set");
        } else {
            tvPercent.setText(initial + "%");
        }

        // ✅ ONLY update when user interacts
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

                if (progress == 0) {
                    tvPercent.setText("Not Set");
                } else {
                    tvPercent.setText(progress + "%");
                }

                if (fromUser) {
                    subject.setProficiency(progress);
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (subject.getProficiency() == 0) {
                    Toast.makeText(SubjectAssessmentActivity.this,
                            "Please set proficiency for " + subject.getName(),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        QuizCardRefs qr = new QuizCardRefs();
        qr.subject = subject;
        qr.seekBar = seekBar;
        qr.tvPercent = tvPercent;
        quizCardRefsBySubject.put(subject.getName(), qr);

        MaterialButton btnQuizAi = cardView.findViewById(R.id.btnQuizAi);
        btnQuizAi.setOnClickListener(v -> {
            if (firebaseUid == null || firebaseUid.isEmpty()) {
                Toast.makeText(this,
                        "Sign in to take the proficiency quiz",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            if (details.getVisibility() != View.VISIBLE) {
                details.setVisibility(View.VISIBLE);
                arrow.setRotation(90);
                if (topicsContainer.getChildCount() == 0) {
                    fetchTopics(subject.getName(), topicsContainer);
                }
            }

            Intent intent = new Intent(this, SubjectQuizActivity.class);
            intent.putExtra(SubjectQuizActivity.EXTRA_SUBJECT, subject.getName());
            intent.putExtra(SubjectQuizActivity.EXTRA_FIREBASE_UID, firebaseUid);
            quizLauncher.launch(intent);
        });

        container.addView(cardView);
    }

    // ========================
    // FETCH TOPICS
    // ========================
    private void fetchTopics(String subjectName, LinearLayout container) {

        String url = SUBJECTS_BASE_URL + "/topics?name="
                + Uri.encode(subjectName) + "&firebase_uid=" + firebaseUid;

        queue.add(new JsonArrayRequest(Request.Method.GET, url, null,
                response -> {
                    for (int i = 0; i < response.length(); i++) {
                        try {
                            JSONObject obj = response.getJSONObject(i);
                            addCheckbox(
                                    obj.getInt("id"),
                                    obj.getString("topic_name"),
                                    obj.optInt("is_completed") == 1,
                                    container
                            );
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                },
                error -> Toast.makeText(this, "Topics load failed", Toast.LENGTH_SHORT).show()
        ));
    }

    private void addCheckbox(int id, String name, boolean checked, LinearLayout container) {

        CheckBox cb = new CheckBox(this);
        cb.setText(name);
        cb.setChecked(checked);
        cb.setTextColor(Color.WHITE);
        cb.setButtonTintList(ColorStateList.valueOf(Color.parseColor("#A855F7")));

        cb.setOnCheckedChangeListener((v, isChecked) -> updateTopic(id, isChecked));

        container.addView(cb);
    }

    private void updateTopic(int topicId, boolean isCompleted) {
        try {
            JSONObject body = new JSONObject();
            body.put("firebase_uid", firebaseUid);
            body.put("topic_id", topicId);
            body.put("is_completed", isCompleted);

            queue.add(new JsonObjectRequest(
                    Request.Method.POST,
                    SUBJECTS_BASE_URL + "/updateTopicProgress",
                    body,
                    null,
                    null
            ));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ========================
    // COMPLETE SETUP (TRANSACTION)
    // ========================
    private void completeSetup() {
        try {
            JSONObject body = new JSONObject();
            body.put("firebase_uid", firebaseUid);

            JSONArray arr = new JSONArray();

            for (Subject s : selectedSubjects) {

                if (s.getExamDate() == null ||
                        s.getExamDate().equals("Set your exam date") ||
                        s.getProficiency() == 0) {

                    Toast.makeText(this,
                            "Complete all subjects (date + proficiency required)",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                JSONObject obj = new JSONObject();
                obj.put("name", s.getName());
                obj.put("exam_date", s.getExamDate());
                obj.put("confidence", s.getProficiency());

                arr.put(obj);
            }

            body.put("subjects", arr);

            queue.add(new JsonObjectRequest(
                    Request.Method.POST,
                    SUBJECTS_BASE_URL + "/completeSetup",
                    body,
                    response -> generatePlan(),
                    error -> Toast.makeText(this, "Setup failed", Toast.LENGTH_SHORT).show()
            ));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ========================
    // GENERATE PLAN
    // ========================
    private void generatePlan() {
        try {
            JSONObject body = new JSONObject();
            body.put("firebase_uid", firebaseUid);
            body.put("total_daily_hours", 6);

            queue.add(new JsonObjectRequest(
                    Request.Method.POST,
                    PLAN_URL,
                    body,
                    response -> {
                        JSONArray plan = response.optJSONArray("plan");

                        Intent intent = new Intent(this, HomeActivity.class);
                        intent.putExtra("generatedPlanJson",
                                plan != null ? plan.toString() : "[]");
                        intent.putExtra("selectedSubjects", selectedSubjects);
                        intent.putExtra("selectedExam", selectedExamName);

                        startActivity(intent);
                        finish();
                    },
                    error -> Toast.makeText(this, "Plan failed", Toast.LENGTH_SHORT).show()
            ));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}