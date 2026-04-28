package com.example.prepandchill;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.LinearLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

public class StudyActivity extends AppCompatActivity {

    private LinearLayout llStudyPlan;
    private RequestQueue queue;
    private String firebaseUid;
    private final String BASE_IP = "10.7.28.203";
    private final String SUBJECTS_BASE_URL = "http://" + BASE_IP + ":3000/api/subjects";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Modern full screen look
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );

        setContentView(R.layout.activity_study);

        ImageView btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        llStudyPlan = findViewById(R.id.llStudyPlan);
        queue = Volley.newRequestQueue(this);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        firebaseUid = (user != null) ? user.getUid() : null;

        // System back behaves naturally
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });

        renderStudyPlan();

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_study);
            bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    BottomNavNavigator.open(this, HomeActivity.class);
                    return true;
                } else if (id == R.id.nav_study) {
                    return true;
                } else if (id == R.id.nav_analytics) {
                    BottomNavNavigator.open(this, ConfidenceMapActivity.class);
                    return true;
                } else if (id == R.id.nav_focus) {
                    BottomNavNavigator.open(this, FocusActivity.class);
                    return true;
                }
                return false;
            });
        }
    }

    private void renderStudyPlan() {
        if (llStudyPlan == null) return;
        llStudyPlan.removeAllViews();

        String planJson = PlanPrefs.readPlanJson(this);
        JSONArray arr;
        try {
            arr = new JSONArray(planJson != null ? planJson : "[]");
        } catch (Exception e) {
            arr = new JSONArray();
        }

        if (arr.length() == 0) {
            TextView tv = new TextView(this);
            tv.setText("No study plan yet. Generate your plan first.");
            tv.setTextColor(getResources().getColor(R.color.text_gray));
            tv.setPadding(8, 16, 8, 16);
            llStudyPlan.addView(tv);
            return;
        }

        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;

            String subject = o.optString("subject", "Subject");
            int minutes = o.optInt("time_minutes", 0);
            String progress = o.optString("progress", "");

            View row = getLayoutInflater().inflate(R.layout.item_study_plan_row, llStudyPlan, false);
            TextView tvSubject = row.findViewById(R.id.tvSubject);
            TextView tvDuration = row.findViewById(R.id.tvDuration);
            TextView tvProgress = row.findViewById(R.id.tvProgress);
            TextView tvNext1 = row.findViewById(R.id.tvNext1);
            TextView tvNext2 = row.findViewById(R.id.tvNext2);

            tvSubject.setText(subject);
            tvDuration.setText(PlanParser.formatMinutes(minutes));
            tvProgress.setText(progress != null && !progress.isEmpty() ? progress : " ");

            tvNext1.setText("• Loading syllabus…");
            tvNext2.setText("• ");
            fetchTwoNextTopics(subject, tvNext1, tvNext2);

            llStudyPlan.addView(row);
        }
    }

    private void fetchTwoNextTopics(String subjectName, TextView tv1, TextView tv2) {
        if (queue == null || firebaseUid == null || subjectName == null) {
            tv1.setText("• Add syllabus topics for this subject.");
            tv2.setText("• ");
            return;
        }

        String url = SUBJECTS_BASE_URL + "/topics?name=" + android.net.Uri.encode(subjectName) +
                "&firebase_uid=" + android.net.Uri.encode(firebaseUid);

        JsonArrayRequest request = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    String t1 = null;
                    String t2 = null;
                    for (int i = 0; i < response.length(); i++) {
                        JSONObject o = response.optJSONObject(i);
                        if (o == null) continue;
                        boolean done = o.optInt("is_completed", 0) == 1;
                        if (done) continue;
                        String name = o.optString("topic_name", "").trim();
                        if (name.isEmpty()) continue;
                        if (t1 == null) t1 = name;
                        else if (t2 == null) { t2 = name; break; }
                    }

                    if (t1 == null) {
                        tv1.setText("• No pending topics found.");
                        tv2.setText("• ");
                    } else {
                        tv1.setText("• " + t1);
                        tv2.setText(t2 != null ? ("• " + t2) : "• ");
                    }
                },
                error -> {
                    tv1.setText("• Couldn’t load syllabus right now.");
                    tv2.setText("• ");
                }
        );

        queue.add(request);
    }
}

