package com.example.prepandchill;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

public class HomeActivity extends AppCompatActivity {

    private static final String BASE_IP = "10.7.28.203";
    private static final String SUBJECTS_BASE = "http://" + BASE_IP + ":3000/api/subjects";

    private ArrayList<Subject> selectedSubjects;
    private RecyclerView rvTodayPlan;
    private RecyclerView rvTaskAgent;
    private HomeSubjectAdapter adapter;
    private TaskAgentAdapter taskAgentAdapter;
    private ArrayList<TaskItem> taskAgentTasks;
    private EditText etAiCommand;
    private MaterialButton btnAiSend;
    private String selectedExamName;
    private Map<String, String> subjectToDuration;
    private RequestQueue queue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );

        setContentView(R.layout.activity_home);

        queue = Volley.newRequestQueue(this);

        rvTodayPlan = findViewById(R.id.rvTodayPlan);
        rvTaskAgent = findViewById(R.id.rvTaskAgent);
        etAiCommand = findViewById(R.id.etAiCommand);
        btnAiSend = findViewById(R.id.btnAiSend);

        Intent intent = getIntent();
        selectedSubjects = (ArrayList<Subject>) intent.getSerializableExtra("selectedSubjects");
        selectedExamName = intent.getStringExtra("selectedExam");
        String generatedPlanJson = intent.getStringExtra("generatedPlanJson");

        if (selectedSubjects == null) {
            selectedSubjects = new ArrayList<>();
        }

        taskAgentTasks = new ArrayList<>();
        taskAgentAdapter = new TaskAgentAdapter(taskAgentTasks);

        updateDynamicUI();

        if (generatedPlanJson != null && !generatedPlanJson.trim().isEmpty()) {
            // Persist for Study/Timetable screens too.
            try {
                // Validate JSON early so we don't save garbage.
                new JSONArray(generatedPlanJson);
                PlanPrefs.savePlanJson(this, generatedPlanJson);
            } catch (Exception ignored) {
            }
        }

        subjectToDuration = PlanParser.subjectToDuration(PlanPrefs.readPlanJson(this));

        adapter = new HomeSubjectAdapter(selectedSubjects, subjectToDuration);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        rvTodayPlan.setLayoutManager(layoutManager);
        rvTodayPlan.setAdapter(adapter);

        rvTaskAgent.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        rvTaskAgent.setAdapter(taskAgentAdapter);

        btnAiSend.setOnClickListener(v -> submitAiCommand());

        TextView btnViewAll = findViewById(R.id.btnViewAll);
        if (btnViewAll != null) {
            btnViewAll.setOnClickListener(v -> {
                Intent tIntent = new Intent(HomeActivity.this, TimetableActivity.class);
                tIntent.putExtra("selectedSubjects", selectedSubjects);
                tIntent.putExtra("generatedPlanJson", PlanPrefs.readPlanJson(HomeActivity.this));
                startActivity(tIntent);
            });
        }

        LinearLayout cvConfidenceMap = findViewById(R.id.cvConfidenceMap);
        if (cvConfidenceMap != null) {
            cvConfidenceMap.setOnClickListener(v -> {
                Intent cIntent = new Intent(HomeActivity.this, ConfidenceMapActivity.class);
                cIntent.putExtra("selectedSubjects", selectedSubjects);
                startActivity(cIntent);
            });
        }

        MaterialButton btnViewDetails = findViewById(R.id.btnViewDetails);
        if (btnViewDetails != null) {
            btnViewDetails.setOnClickListener(v -> {
                Intent dIntent = new Intent(HomeActivity.this, ExamDetailsActivity.class);
                dIntent.putExtra("selectedSubjects", selectedSubjects);
                dIntent.putExtra("selectedExam", selectedExamName);
                startActivity(dIntent);
            });
        }

        LinearLayout navStudy = findViewById(R.id.navStudy);
        if (navStudy != null) {
            navStudy.setOnClickListener(v -> {
                Intent tIntent = new Intent(HomeActivity.this, TimetableActivity.class);
                tIntent.putExtra("selectedSubjects", selectedSubjects);
                tIntent.putExtra("generatedPlanJson", PlanPrefs.readPlanJson(HomeActivity.this));
                startActivity(tIntent);
            });
        }

        LinearLayout navAnalytics = findViewById(R.id.navAnalytics);
        if (navAnalytics != null) {
            navAnalytics.setOnClickListener(v -> {
                Intent cIntent = new Intent(HomeActivity.this, ConfidenceMapActivity.class);
                cIntent.putExtra("selectedSubjects", selectedSubjects);
                startActivity(cIntent);
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchEnrolledSubjectsAndRefreshUi();
    }

    private void submitAiCommand() {
        String command = etAiCommand.getText().toString().trim();
        if (command.isEmpty()) {
            showToast("Please enter a task command.");
            return;
        }

        btnAiSend.setEnabled(false);
        TaskAgentApiClient.sendTaskAgentCommand(command, new TaskAgentApiClient.TaskAgentCallback() {
            @Override
            public void onSuccess(TaskAgentResponse taskAgentResponse) {
                runOnUiThread(() -> {
                    btnAiSend.setEnabled(true);
                    if ("add_tasks".equals(taskAgentResponse.getAction()) && !taskAgentResponse.getTasks().isEmpty()) {
                        int startPosition = taskAgentTasks.size();
                        taskAgentTasks.addAll(taskAgentResponse.getTasks());
                        taskAgentAdapter.notifyItemRangeInserted(startPosition, taskAgentResponse.getTasks().size());
                        etAiCommand.setText("");
                    } else {
                        showToast("AI returned no tasks or invalid action.");
                    }
                });
            }

            @Override
            public void onFailure(String errorMessage) {
                runOnUiThread(() -> {
                    btnAiSend.setEnabled(true);
                    showToast(errorMessage);
                });
            }
        });
    }

    private void showToast(String message) {
        Toast.makeText(HomeActivity.this, message, Toast.LENGTH_SHORT).show();
    }

    private static String resolveUserDisplayName(FirebaseUser user) {
        if (user == null) {
            return "User";
        }
        String display = user.getDisplayName();
        if (display != null && !display.trim().isEmpty()) {
            return display.trim();
        }
        String email = user.getEmail();
        if (email != null && email.contains("@")) {
            return email.substring(0, email.indexOf('@')).trim();
        }
        return "User";
    }

    private void updateDynamicUI() {
        TextView tvUserName = findViewById(R.id.tvUserName);
        if (tvUserName != null) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            tvUserName.setText(resolveUserDisplayName(user));
        }

        TextView tvExamTitle = findViewById(R.id.tvExamTitle);
        if (tvExamTitle != null && selectedExamName != null) {
            tvExamTitle.setText(selectedExamName);
        }

        if (selectedSubjects != null && !selectedSubjects.isEmpty()) {
            int totalProficiency = 0;
            for (Subject s : selectedSubjects) {
                totalProficiency += s.getProficiency();
            }
            int averageProficiency = totalProficiency / selectedSubjects.size();

            TextView tvProgressPercent = findViewById(R.id.tvProgressPercent);
            if (tvProgressPercent != null) {
                tvProgressPercent.setText(averageProficiency + "%");
            }

            View rlProgress = findViewById(R.id.rlProgress);
            if (rlProgress instanceof android.view.ViewGroup) {
                android.view.ViewGroup group = (android.view.ViewGroup) rlProgress;
                for (int i = 0; i < group.getChildCount(); i++) {
                    View child = group.getChildAt(i);
                    if (child instanceof ProgressBar) {
                        ((ProgressBar) child).setProgress(averageProficiency);
                    }
                }
            }
        }

        bindConfidenceMapStrip(selectedSubjects);
    }

    private void fetchEnrolledSubjectsAndRefreshUi() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            updateDynamicUI();
            return;
        }

        String url = SUBJECTS_BASE + "/mine?firebase_uid=" + Uri.encode(user.getUid());
        JsonArrayRequest req =
                new JsonArrayRequest(
                        Request.Method.GET,
                        url,
                        null,
                        response -> {
                            ArrayList<Subject> next = new ArrayList<>();
                            for (int i = 0; i < response.length(); i++) {
                                try {
                                    JSONObject o = response.getJSONObject(i);
                                    next.add(SubjectMineParser.fromMineJson(o));
                                } catch (Exception ignored) {
                                }
                            }
                            selectedSubjects.clear();
                            selectedSubjects.addAll(next);
                            subjectToDuration =
                                    PlanParser.subjectToDuration(
                                            PlanPrefs.readPlanJson(HomeActivity.this));
                            if (adapter != null) {
                                adapter.notifyDataSetChanged();
                            }
                            updateDynamicUI();
                        },
                        error -> updateDynamicUI());
        queue.add(req);
    }

    private void bindConfidenceMapStrip(ArrayList<Subject> subjects) {
        LinearLayout llCols = findViewById(R.id.llHomeConfidenceColumns);
        TextView tvOverall = findViewById(R.id.tvHomeConfidenceOverall);
        TextView tvDelta = findViewById(R.id.tvHomeConfidenceDelta);
        TextView tvHint = findViewById(R.id.tvHomeConfidenceHint);
        if (llCols == null || tvOverall == null) {
            return;
        }

        llCols.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int colW = (int) (48 * density);
        int maxBarH = (int) (120 * density);

        if (subjects == null || subjects.isEmpty()) {
            tvOverall.setText("—");
            if (tvDelta != null) {
                tvDelta.setVisibility(View.GONE);
            }
            if (tvHint != null) {
                tvHint.setText("Complete setup, then use AI quiz on each subject to build this map.");
            }
            TextView empty = new TextView(this);
            empty.setTextColor(ContextCompat.getColor(this, R.color.text_gray));
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            empty.setText("No enrolled subjects yet.");
            llCols.addView(empty);
            return;
        }

        if (tvHint != null) {
            tvHint.setText("Per-subject confidence from setup & AI quiz");
        }

        int minIdx = 0;
        for (int i = 1; i < subjects.size(); i++) {
            if (subjects.get(i).getProficiency() < subjects.get(minIdx).getProficiency()) {
                minIdx = i;
            }
        }

        for (int i = 0; i < subjects.size(); i++) {
            Subject subject = subjects.get(i);
            int prof = subject.getProficiency();
            boolean weakest = i == minIdx;

            FrameLayout frame = new FrameLayout(this);
            LinearLayout.LayoutParams frameLp =
                    new LinearLayout.LayoutParams(colW, maxBarH);
            frame.setLayoutParams(frameLp);

            int barH = Math.max((int) (maxBarH * prof / 100.0), (int) (8 * density));
            FrameLayout.LayoutParams barLp =
                    new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, barH);
            barLp.gravity = Gravity.BOTTOM;
            View bar = new View(this);
            bar.setLayoutParams(barLp);
            bar.setBackgroundColor(
                    ContextCompat.getColor(
                            this,
                            weakest ? R.color.chart_bar_highlight : R.color.chart_bar_default));
            frame.addView(bar);

            TextView label = new TextView(this);
            label.setText(abbrevSubjectName(subject.getName()));
            label.setTextColor(ContextCompat.getColor(this, R.color.text_gray));
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            label.setGravity(Gravity.CENTER_HORIZONTAL);

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams colLp =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            int m = (int) (4 * density);
            colLp.setMargins(m, 0, m, 0);
            col.setLayoutParams(colLp);
            col.addView(frame);
            col.addView(label);

            llCols.addView(col);
        }

        int total = 0;
        for (Subject s : subjects) {
            total += s.getProficiency();
        }
        int avg = total / subjects.size();
        tvOverall.setText(avg + "%");

        if (tvDelta != null) {
            int prev = ConfidencePrefs.getLastAverageConfidence(this);
            if (prev >= 0) {
                int d = avg - prev;
                if (d != 0) {
                    tvDelta.setVisibility(View.VISIBLE);
                    tvDelta.setText((d > 0 ? "+" : "") + d + "%");
                    tvDelta.setTextColor(
                            d > 0 ? Color.parseColor("#00C853") : Color.parseColor("#FF5252"));
                } else {
                    tvDelta.setVisibility(View.GONE);
                }
            } else {
                tvDelta.setVisibility(View.GONE);
            }
        }
        ConfidencePrefs.setLastAverageConfidence(this, avg);
    }

    private static String abbrevSubjectName(String name) {
        if (name == null) {
            return "?";
        }
        String t = name.trim();
        if (t.isEmpty()) {
            return "?";
        }
        if (t.length() <= 3) {
            return t.toUpperCase(Locale.US);
        }
        return t.substring(0, 3).toUpperCase(Locale.US);
    }
}
