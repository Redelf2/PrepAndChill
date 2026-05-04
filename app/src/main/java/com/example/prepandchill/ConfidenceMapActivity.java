package com.example.prepandchill;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ConfidenceMapActivity extends AppCompatActivity {

    private static final String BASE_IP = "10.7.28.203";
    private static final String SUBJECTS_BASE = "http://" + BASE_IP + ":3000/api/subjects";

    private RequestQueue queue;
    private final ArrayList<Subject> subjects = new ArrayList<>();

    private TextView tvMapAvgConfidence;
    private TextView tvMapAvgDelta;
    private ProgressBar pbMapAvgConfidence;
    private TextView tvMapSubjectCount;
    private LinearLayout llSubjectList;
    private LinearLayout llCriticalList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        setContentView(R.layout.activity_confidence_map);

        queue = Volley.newRequestQueue(this);

        tvMapAvgConfidence = findViewById(R.id.tvMapAvgConfidence);
        tvMapAvgDelta = findViewById(R.id.tvMapAvgDelta);
        pbMapAvgConfidence = findViewById(R.id.pbMapAvgConfidence);
        tvMapSubjectCount = findViewById(R.id.tvMapSubjectCount);
        llSubjectList = findViewById(R.id.llConfidenceSubjectList);
        llCriticalList = findViewById(R.id.llCriticalReviewList);

        ImageView btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        @SuppressWarnings("unchecked")
        ArrayList<Subject> fromIntent =
                (ArrayList<Subject>) getIntent().getSerializableExtra("selectedSubjects");
        subjects.clear();
        if (fromIntent != null) {
            subjects.addAll(fromIntent);
        }
        bindUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchMine();
    }

    private void fetchMine() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            bindUi();
            return;
        }
        String url = SUBJECTS_BASE + "/mine?firebase_uid=" + Uri.encode(user.getUid());
        JsonArrayRequest req =
                new JsonArrayRequest(
                        Request.Method.GET,
                        url,
                        null,
                        response -> {
                            subjects.clear();
                            for (int i = 0; i < response.length(); i++) {
                                try {
                                    JSONObject o = response.getJSONObject(i);
                                    subjects.add(SubjectMineParser.fromMineJson(o));
                                } catch (Exception ignored) {
                                }
                            }
                            bindUi();
                        },
                        error -> bindUi());
        queue.add(req);
    }

    private void bindUi() {
        if (tvMapAvgConfidence == null || llSubjectList == null) {
            return;
        }

        if (subjects.isEmpty()) {
            tvMapAvgConfidence.setText("—");
            if (tvMapAvgDelta != null) {
                tvMapAvgDelta.setVisibility(View.GONE);
            }
            if (pbMapAvgConfidence != null) {
                pbMapAvgConfidence.setProgress(0);
            }
            if (tvMapSubjectCount != null) {
                tvMapSubjectCount.setText("0");
            }
            llSubjectList.removeAllViews();
            TextView empty = new TextView(this);
            empty.setTextColor(ContextCompat.getColor(this, R.color.text_gray));
            empty.setTextSize(14);
            empty.setText("No subjects yet. Finish onboarding, then take AI quizzes from Assessment.");
            llSubjectList.addView(empty);
            bindCritical(null);
            return;
        }

        int sum = 0;
        for (Subject s : subjects) {
            sum += s.getProficiency();
        }
        int avg = sum / subjects.size();
        tvMapAvgConfidence.setText(avg + "%");
        if (pbMapAvgConfidence != null) {
            pbMapAvgConfidence.setProgress(avg);
        }
        if (tvMapSubjectCount != null) {
            tvMapSubjectCount.setText(String.valueOf(subjects.size()));
        }

        if (tvMapAvgDelta != null) {
            int prev = ConfidencePrefs.getLastAverageConfidence(this);
            if (prev >= 0) {
                int d = avg - prev;
                if (d != 0) {
                    tvMapAvgDelta.setVisibility(View.VISIBLE);
                    tvMapAvgDelta.setText((d > 0 ? "+" : "") + d + "% vs last home refresh");
                    tvMapAvgDelta.setTextColor(
                            d > 0 ? Color.parseColor("#00C853") : Color.parseColor("#FF5252"));
                } else {
                    tvMapAvgDelta.setVisibility(View.GONE);
                }
            } else {
                tvMapAvgDelta.setVisibility(View.GONE);
            }
        }

        List<Subject> sorted = new ArrayList<>(subjects);
        Collections.sort(sorted, Comparator.comparingInt(Subject::getProficiency));

        llSubjectList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Subject s : sorted) {
            View row = inflater.inflate(R.layout.item_confidence_map_subject, llSubjectList, false);
            TextView tvName = row.findViewById(R.id.tvConfSubjectName);
            TextView tvMeta = row.findViewById(R.id.tvConfSubjectMeta);
            TextView tvPct = row.findViewById(R.id.tvConfSubjectPercent);
            ProgressBar pb = row.findViewById(R.id.pbConfSubject);
            if (tvName != null) {
                tvName.setText(s.getName());
            }
            if (tvMeta != null) {
                tvMeta.setText(
                        "Difficulty "
                                + s.getDifficulty()
                                + "/3 · Exam "
                                + (s.getExamDate() != null ? s.getExamDate() : ""));
            }
            if (tvPct != null) {
                tvPct.setText(s.getProficiency() + "%");
            }
            if (pb != null) {
                pb.setProgress(s.getProficiency());
            }
            llSubjectList.addView(row);
        }

        bindCritical(sorted);
    }

    /** Lowest-confidence subjects — prompt quiz from Assessment. */
    private void bindCritical(List<Subject> sortedAscending) {
        if (llCriticalList == null) {
            return;
        }
        llCriticalList.removeAllViews();
        if (sortedAscending == null || sortedAscending.isEmpty()) {
            return;
        }
        int n = Math.min(2, sortedAscending.size());
        for (int i = 0; i < n; i++) {
            Subject s = sortedAscending.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setBackgroundResource(R.drawable.bg_streak_card);
            int pad = (int) (16 * getResources().getDisplayMetrics().density);
            card.setPadding(pad, pad, pad, pad);
            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int) (12 * getResources().getDisplayMetrics().density);
            card.setLayoutParams(lp);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setLayoutParams(
                    new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView title = new TextView(this);
            title.setText(s.getName());
            title.setTextColor(ContextCompat.getColor(this, R.color.text_white));
            title.setTextSize(16);
            title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);

            TextView sub = new TextView(this);
            sub.setText("Confidence " + s.getProficiency() + "% — take AI quiz to update");
            sub.setTextColor(ContextCompat.getColor(this, R.color.text_gray));
            sub.setTextSize(12);

            textCol.addView(title);
            textCol.addView(sub);

            MaterialButton btn = new MaterialButton(this);
            btn.setText("Quiz");
            btn.setOnClickListener(
                    v -> {
                        Intent intent = new Intent(this, SubjectAssessmentActivity.class);
                        intent.putExtra("selectedSubjects", new ArrayList<>(subjects));
                        startActivity(intent);
                    });

            card.addView(textCol);
            card.addView(btn);
            llCriticalList.addView(card);
        }
    }
}
