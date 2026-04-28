package com.example.prepandchill;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
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

public class SubjectAssessmentActivity extends AppCompatActivity {

    private static final String TAG = "SubjectAssessment";
    private LinearLayout container;
    private ArrayList<Subject> selectedSubjects;
    private String selectedExam;
    private RequestQueue queue;
    private String firebaseUid;

     private final String BASE_IP = "10.7.28.203";
    private final String SUBJECTS_BASE_URL = "http://" + BASE_IP + ":3000/api/subjects";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_assessment);

        selectedExam = getIntent().getStringExtra("selectedExam");
        container = findViewById(R.id.llAssessmentContainer);
        ImageView btnBack = findViewById(R.id.btnBack);
        MaterialButton btnGenerate = findViewById(R.id.btnGenerate);

        queue = Volley.newRequestQueue(this);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        firebaseUid = (user != null) ? user.getUid() : null;

        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        selectedSubjects = (ArrayList<Subject>) getIntent().getSerializableExtra("selectedSubjects");

        if (selectedSubjects != null && container != null) {
            for (Subject subject : selectedSubjects) {
                addSubjectCard(subject);
            }
        }

        if (btnGenerate != null) {
            btnGenerate.setOnClickListener(v -> {
                if (firebaseUid == null) {
                    Toast.makeText(this, "User session expired. Please login again.", Toast.LENGTH_LONG).show();
                    return;
                }
                generatePlanFromServer(6);
            });
        }
    }

    private void addSubjectCard(Subject subject) {
        View cardView = LayoutInflater.from(this).inflate(R.layout.item_assessment_card, container, false);
        LinearLayout header = cardView.findViewById(R.id.headerSubject);
        LinearLayout details = cardView.findViewById(R.id.layoutDetails);
        ImageView arrow = cardView.findViewById(R.id.ivArrow);
        TextView tvName = cardView.findViewById(R.id.tvSubjectName);
        SeekBar seekBar = cardView.findViewById(R.id.seekBarProficiency);
        TextView tvPercent = cardView.findViewById(R.id.tvPercent);
        LinearLayout topicsContainer = cardView.findViewById(R.id.llTopicsContainer);

        if (tvName != null) tvName.setText(subject.getName());

        header.setOnClickListener(v -> {
            if (details.getVisibility() == View.VISIBLE) {
                details.setVisibility(View.GONE);
                arrow.setRotation(270);
            } else {
                details.setVisibility(View.VISIBLE);
                arrow.setRotation(0);
                if (topicsContainer != null && topicsContainer.getChildCount() == 0) {
                    fetchTopicsForSubject(subject.getName(), topicsContainer);
                }
            }
        });

        seekBar.setProgress(subject.getProficiency());
        tvPercent.setText(String.format("%d%%", subject.getProficiency()));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvPercent.setText(String.format("%d%%", progress));
                subject.setProficiency(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                saveConfidenceToDB(subject.getName(), subject.getProficiency());
            }
        });

        container.addView(cardView);
    }

    private void fetchTopicsForSubject(String subjectName, LinearLayout topicsContainer) {
        String encodedName = Uri.encode(subjectName);
        String encodedUid = Uri.encode(firebaseUid != null ? firebaseUid : "");
        String url = SUBJECTS_BASE_URL + "/topics?name=" + encodedName + "&firebase_uid=" + encodedUid;

        Log.d(TAG, "Fetching topics from: " + url);

        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
                response -> {
                    if (response.length() == 0) {
                        showNoTopicsMsg(topicsContainer);
                        return;
                    }
                    for (int i = 0; i < response.length(); i++) {
                        try {
                            JSONObject topicObj = response.getJSONObject(i);
                            addTopicCheckBox(
                                    topicObj.getInt("id"),
                                    topicObj.getString("topic_name"),
                                    topicObj.optInt("is_completed", 0) == 1,
                                    topicsContainer
                            );
                        } catch (Exception e) {
                            Log.e(TAG, "JSON Error", e);
                        }
                    }
                },
                error -> {
                    String errorType = "Connection Error";
                    if (error.networkResponse != null) {
                        errorType = "Server Error: " + error.networkResponse.statusCode;
                    }
                    Toast.makeText(this, errorType + "\nSyllabus failed to load.", Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Volley Error: " + error.toString());
                }
        );
        queue.add(request);
    }

    private void addTopicCheckBox(int topicId, String topicName, boolean isCompleted, LinearLayout container) {
        CheckBox cb = new CheckBox(this);
        cb.setText(topicName);
        cb.setChecked(isCompleted);
        cb.setTextColor(Color.WHITE);
        cb.setButtonTintList(ColorStateList.valueOf(Color.parseColor("#A855F7")));
        cb.setPadding(0, 10, 0, 10);
        cb.setTextSize(14);
        cb.setOnCheckedChangeListener((v, isChecked) -> updateTopicProgress(topicId, isChecked));
        container.addView(cb);
    }

    private void showNoTopicsMsg(LinearLayout container) {
        TextView tv = new TextView(this);
        tv.setText("No syllabus found for this subject.");
        tv.setTextColor(Color.GRAY);
        tv.setPadding(10, 10, 10, 10);
        container.addView(tv);
    }

    private void updateTopicProgress(int topicId, boolean isCompleted) {
        try {
            JSONObject body = new JSONObject();
            body.put("firebase_uid", firebaseUid);
            body.put("topic_id", topicId);
            body.put("is_completed", isCompleted);
            queue.add(new JsonObjectRequest(Request.Method.POST, SUBJECTS_BASE_URL + "/updateTopicProgress", body, null, null));
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void saveConfidenceToDB(String subjectName, int confidence) {
        try {
            JSONObject body = new JSONObject();
            body.put("firebase_uid", firebaseUid);
            body.put("subject_name", subjectName);
            body.put("confidence", confidence);
            queue.add(new JsonObjectRequest(Request.Method.POST, SUBJECTS_BASE_URL + "/updateConfidence", body, null, null));
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void generatePlanFromServer(double hours) {
        String url = "http://" + BASE_IP + ":3000/api/plan/generatePlan";
        try {
            JSONObject body = new JSONObject();
            body.put("firebase_uid", firebaseUid);
            body.put("total_daily_hours", hours);

            queue.add(new JsonObjectRequest(Request.Method.POST, url, body,
                response -> {
                    JSONArray plan = response.optJSONArray("plan");
                    Intent intent = new Intent(this, HomeActivity.class);
                    intent.putExtra("generatedPlanJson", plan != null ? plan.toString() : "[]");
                    startActivity(intent);
                    finish();
                },
                error -> Toast.makeText(this, "Failed to reach server for plan generation.", Toast.LENGTH_SHORT).show()
            ));
        } catch (Exception e) { e.printStackTrace(); }
    }
}