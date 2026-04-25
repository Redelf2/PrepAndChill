package com.example.prepandchill;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;

public class HomeActivity extends AppCompatActivity {

    private ArrayList<Subject> selectedSubjects;
    private RecyclerView rvTodayPlan;
    private RecyclerView rvTaskAgent;
    private HomeSubjectAdapter adapter;
    private TaskAgentAdapter taskAgentAdapter;
    private ArrayList<TaskItem> taskAgentTasks;
    private EditText etAiCommand;
    private MaterialButton btnAiSend;
    private String selectedExamName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );

        setContentView(R.layout.activity_home);

        rvTodayPlan = findViewById(R.id.rvTodayPlan);
        rvTaskAgent = findViewById(R.id.rvTaskAgent);
        etAiCommand = findViewById(R.id.etAiCommand);
        btnAiSend = findViewById(R.id.btnAiSend);

        Intent intent = getIntent();
        selectedSubjects = (ArrayList<Subject>) intent.getSerializableExtra("selectedSubjects");
        selectedExamName = intent.getStringExtra("selectedExam");

        if (selectedSubjects == null) {
            selectedSubjects = new ArrayList<>();
        }

        taskAgentTasks = new ArrayList<>();
        taskAgentAdapter = new TaskAgentAdapter(taskAgentTasks);

        updateDynamicUI();

        adapter = new HomeSubjectAdapter(selectedSubjects);
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

    private void updateDynamicUI() {
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
    }
}
