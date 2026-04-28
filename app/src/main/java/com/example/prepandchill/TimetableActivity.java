package com.example.prepandchill;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TimetableActivity extends AppCompatActivity {

    private LinearLayout llDateSelector;
    private LinearLayout llUpcomingSessions;
    private int selectedOffset = 0;

    private RequestQueue queue;
    private String firebaseUid;
    private final String BASE_IP = "10.7.28.203";
    private final String SUBJECTS_BASE_URL = "http://" + BASE_IP + ":3000/api/subjects";

    private String planJson;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
        
        setContentView(R.layout.activity_timetable);

        ImageView btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        llDateSelector = findViewById(R.id.llDateSelector);
        llUpcomingSessions = findViewById(R.id.llUpcomingSessions);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        firebaseUid = (user != null) ? user.getUid() : null;
        queue = Volley.newRequestQueue(this);

        Intent intent = getIntent();
        String fromIntent = intent != null ? intent.getStringExtra("generatedPlanJson") : null;
        if (fromIntent != null && !fromIntent.trim().isEmpty()) {
            try {
                new JSONArray(fromIntent);
                PlanPrefs.savePlanJson(this, fromIntent);
            } catch (Exception ignored) {
            }
        }
        planJson = PlanPrefs.readPlanJson(this);

        bindDateChips();
        renderSessionsForSelectedDay();
    }

    private void bindDateChips() {
        if (llDateSelector == null) return;
        llDateSelector.removeAllViews();

        for (int offset = 0; offset < 7; offset++) {
            View chip = getLayoutInflater().inflate(R.layout.item_date_chip, llDateSelector, false);
            TextView tvDow = chip.findViewById(R.id.tvDow);
            TextView tvDay = chip.findViewById(R.id.tvDay);
            View vDot = chip.findViewById(R.id.vDot);

            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_YEAR, offset);
            Date d = c.getTime();

            String dow = new SimpleDateFormat("EEE", Locale.getDefault()).format(d).toUpperCase(Locale.getDefault());
            String dayNum = new SimpleDateFormat("d", Locale.getDefault()).format(d);
            tvDow.setText(dow);
            tvDay.setText(dayNum);

            boolean selected = (offset == selectedOffset);
            applyChipSelectedState(chip, selected, vDot, tvDow, tvDay);

            int finalOffset = offset;
            chip.setOnClickListener(v -> {
                selectedOffset = finalOffset;
                for (int i = 0; i < llDateSelector.getChildCount(); i++) {
                    View child = llDateSelector.getChildAt(i);
                    TextView cdow = child.findViewById(R.id.tvDow);
                    TextView cday = child.findViewById(R.id.tvDay);
                    View cdot = child.findViewById(R.id.vDot);
                    applyChipSelectedState(child, i == selectedOffset, cdot, cdow, cday);
                }
                renderSessionsForSelectedDay();
            });

            llDateSelector.addView(chip);
        }
    }

    private void applyChipSelectedState(View chip, boolean selected, View dot, TextView tvDow, TextView tvDay) {
        if (selected) {
            chip.setBackgroundResource(R.drawable.bg_date_selected);
            if (dot != null) dot.setVisibility(View.VISIBLE);
            if (tvDow != null) tvDow.setTextColor(getResources().getColor(R.color.accent_orange));
            if (tvDay != null) {
                tvDay.setTextColor(getResources().getColor(R.color.accent_orange));
                tvDay.setTextSize(18);
            }
        } else {
            chip.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            if (dot != null) dot.setVisibility(View.GONE);
            if (tvDow != null) tvDow.setTextColor(getResources().getColor(R.color.text_gray));
            if (tvDay != null) {
                tvDay.setTextColor(getResources().getColor(R.color.text_gray));
                tvDay.setTextSize(16);
            }
        }
    }

    private void renderSessionsForSelectedDay() {
        if (llUpcomingSessions == null) return;
        llUpcomingSessions.removeAllViews();

        ArrayList<JSONObject> planItems = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(planJson != null ? planJson : "[]");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) planItems.add(o);
            }
        } catch (Exception ignored) {
        }

        if (planItems.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("No study plan yet. Generate your plan first.");
            tv.setTextColor(getResources().getColor(R.color.text_gray));
            tv.setPadding(8, 16, 8, 16);
            llUpcomingSessions.addView(tv);
            return;
        }

        Calendar start = Calendar.getInstance();
        start.set(Calendar.HOUR_OF_DAY, 9 + Math.min(3, selectedOffset));
        start.set(Calendar.MINUTE, 0);

        for (JSONObject item : planItems) {
            String subject = item.optString("subject", "Subject");
            int subjectId = item.optInt("subject_id", -1);
            int minutes = item.optInt("time_minutes", 60);
            String progress = item.optString("progress", "");

            Calendar end = (Calendar) start.clone();
            end.add(Calendar.MINUTE, Math.max(25, minutes));

            String timeRange = formatTime(start.getTime()) + " - " + formatTime(end.getTime());

            View card = getLayoutInflater().inflate(R.layout.item_timetable_session, llUpcomingSessions, false);
            TextView tvTitle = card.findViewById(R.id.tvSessionTitle);
            TextView tvTime = card.findViewById(R.id.tvSessionTime);
            TextView tvMeta = card.findViewById(R.id.tvSessionMeta);
            TextView tvTopic1 = card.findViewById(R.id.tvTopic1);
            TextView tvTopic2 = card.findViewById(R.id.tvTopic2);
            MaterialButton btnStart = card.findViewById(R.id.btnStartPomodoro);

            tvTitle.setText(subject);
            tvTime.setText(timeRange);
            tvMeta.setText(progress != null && !progress.isEmpty() ? progress : ("Planned: " + PlanParser.formatMinutes(minutes)));

            final List<JSONObject> topicList = new ArrayList<>();
            fetchTwoFocusTopics(subject, tvTopic1, tvTopic2, topicList);

            btnStart.setOnClickListener(v -> {
                Intent focusIntent = new Intent(TimetableActivity.this, FocusActivity.class);
                focusIntent.putExtra("subject_name", subject);
                focusIntent.putExtra("subject_id", subjectId);
                focusIntent.putExtra("total_minutes", minutes);
                
                if (!topicList.isEmpty()) {
                    focusIntent.putExtra("topic_name", topicList.get(0).optString("topic_name"));
                    focusIntent.putExtra("topic_id", topicList.get(0).optInt("id"));
                } else {
                    focusIntent.putExtra("topic_name", "General Study");
                    focusIntent.putExtra("topic_id", -1);
                }
                startActivity(focusIntent);
            });

            llUpcomingSessions.addView(card);
            start = end;
        }
    }

    private String formatTime(Date date) {
        return new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date);
    }

    private void fetchTwoFocusTopics(String subjectName, TextView tv1, TextView tv2, List<JSONObject> outList) {
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
                    outList.clear();
                    for (int i = 0; i < response.length(); i++) {
                        JSONObject o = response.optJSONObject(i);
                        if (o == null) continue;
                        boolean done = o.optInt("is_completed", 0) == 1;
                        if (done) continue;
                        outList.add(o);
                    }

                    if (outList.isEmpty()) {
                        tv1.setText("• No pending topics found.");
                        tv2.setText("• ");
                    } else {
                        tv1.setText("• " + outList.get(0).optString("topic_name"));
                        if (outList.size() > 1) {
                            tv2.setText("• " + outList.get(1).optString("topic_name"));
                        } else {
                            tv2.setText("• ");
                        }
                    }
                },
                error -> {
                    tv1.setText("• Couldn’t load syllabus.");
                    tv2.setText("• ");
                }
        );

        queue.add(request);
    }
}
