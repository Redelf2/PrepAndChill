package com.example.prepandchill;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * AI-generated quiz flow: POST /api/quiz/generateForSubject → answer UI → POST /api/quiz/submit.
 */
public class SubjectQuizActivity extends AppCompatActivity {

    public static final String EXTRA_SUBJECT = "extra_subject_name";
    public static final String EXTRA_FIREBASE_UID = "extra_firebase_uid";

    public static final String RESULT_SUBJECT_NAME = "result_subject_name";
    public static final String RESULT_CONFIDENCE = "result_confidence";
    public static final String RESULT_DIFFICULTY = "result_difficulty";

    private final String BASE_IP = "10.7.28.203";
    private final String QUIZ_BASE = "http://" + BASE_IP + ":3000/api/quiz";

    private String subjectName;
    private String firebaseUid;

    private ProgressBar progressGenerating;
    private TextView tvGeneratingHint;
    private ScrollView scrollQuiz;
    private LinearLayout llQuizQuestions;
    private MaterialButton btnSubmitQuiz;
    private TextView tvQuizTitle;

    private boolean submitInFlight;

    /** Prefer JSON {"error":"..."} from API when present so Volley errors are readable. */
    private static String quizErrorMessage(com.android.volley.VolleyError error, String fallback) {
        if (error == null) return fallback;
        if (error.networkResponse == null || error.networkResponse.data == null) {
            String m = error.getMessage();
            return (m != null && !m.isEmpty()) ? m : fallback;
        }
        try {
            String raw = new String(error.networkResponse.data, StandardCharsets.UTF_8);
            JSONObject o = new JSONObject(raw);
            String e = o.optString("error", "");
            if (!e.isEmpty()) return e;
            return !raw.isEmpty() ? raw : fallback;
        } catch (Exception ignored) {
            return new String(error.networkResponse.data, StandardCharsets.UTF_8);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_quiz);

        subjectName = getIntent().getStringExtra(EXTRA_SUBJECT);
        firebaseUid = getIntent().getStringExtra(EXTRA_FIREBASE_UID);

        if (subjectName == null || subjectName.trim().isEmpty()) {
            Toast.makeText(this, "Missing subject", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (firebaseUid == null || firebaseUid.isEmpty()) {
            Toast.makeText(this, "Sign in required for quiz scoring", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        progressGenerating = findViewById(R.id.progressGenerating);
        tvGeneratingHint = findViewById(R.id.tvGeneratingHint);
        scrollQuiz = findViewById(R.id.scrollQuiz);
        llQuizQuestions = findViewById(R.id.llQuizQuestions);
        btnSubmitQuiz = findViewById(R.id.btnSubmitQuiz);
        tvQuizTitle = findViewById(R.id.tvQuizTitle);

        findViewById(R.id.btnQuizClose).setOnClickListener(v -> finish());

        tvQuizTitle.setText(subjectName);

        btnSubmitQuiz.setOnClickListener(v -> submitAnswers());

        requestGenerateQuiz();
    }

    private void requestGenerateQuiz() {
        progressGenerating.setVisibility(View.VISIBLE);
        tvGeneratingHint.setVisibility(View.VISIBLE);
        scrollQuiz.setVisibility(View.GONE);
        btnSubmitQuiz.setVisibility(View.GONE);

        try {
            JSONObject body = new JSONObject();
            body.put("subject", subjectName);
            body.put("num_questions", 10);

            JsonObjectRequest req = new JsonObjectRequest(
                    Request.Method.POST,
                    QUIZ_BASE + "/generateForSubject",
                    body,
                    response -> {
                        progressGenerating.setVisibility(View.GONE);
                        tvGeneratingHint.setVisibility(View.GONE);

                        if (!response.optBoolean("success", false)) {
                            Toast.makeText(this,
                                    response.optString("error", "Generation failed"),
                                    Toast.LENGTH_LONG).show();
                            finish();
                            return;
                        }

                        JSONArray questions = response.optJSONArray("questions");
                        if (questions == null || questions.length() == 0) {
                            Toast.makeText(this, "No questions returned", Toast.LENGTH_LONG).show();
                            finish();
                            return;
                        }

                        renderQuestions(questions);
                        scrollQuiz.setVisibility(View.VISIBLE);
                        btnSubmitQuiz.setVisibility(View.VISIBLE);
                    },
                    error -> {
                        progressGenerating.setVisibility(View.GONE);
                        tvGeneratingHint.setVisibility(View.GONE);
                        String msg = quizErrorMessage(error, "Quiz generation failed");
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                        finish();
                    }
            ) {
                @Override
                public Map<String, String> getHeaders() {
                    Map<String, String> h = new HashMap<>();
                    h.put("Content-Type", "application/json");
                    return h;
                }
            };

            Volley.newRequestQueue(this).add(req);
        } catch (Exception e) {
            progressGenerating.setVisibility(View.GONE);
            tvGeneratingHint.setVisibility(View.GONE);
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void renderQuestions(JSONArray questions) {
        llQuizQuestions.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        String[] letters = {"A", "B", "C", "D"};
        String[] optionKeys = {"option_a", "option_b", "option_c", "option_d"};

        try {
            for (int i = 0; i < questions.length(); i++) {
                JSONObject q = questions.getJSONObject(i);
                int qid = q.getInt("id");
                int diff = q.optInt("difficulty_level", 2);
                String prompt = q.getString("question");

                View block = inflater.inflate(R.layout.item_quiz_question, llQuizQuestions, false);
                block.setTag(qid);

                TextView tvBadge = block.findViewById(R.id.tvDifficultyBadge);
                TextView tvPrompt = block.findViewById(R.id.tvQuestionPrompt);
                RadioGroup rg = block.findViewById(R.id.rgChoices);

                String diffLabel = diff == 1 ? "Easy" : (diff == 3 ? "Hard" : "Medium");
                tvBadge.setText(diffLabel + " · Q" + (i + 1));
                tvPrompt.setText(prompt);

                rg.removeAllViews();

                for (int j = 0; j < 4; j++) {
                    String txt = q.optString(optionKeys[j], "");
                    RadioButton rb = new RadioButton(this);
                    rb.setId(View.generateViewId());
                    rb.setTag(letters[j]);
                    rb.setText(letters[j] + ") " + txt);
                    rb.setTextColor(Color.parseColor("#E2E8F0"));
                    rb.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#A855F7")));
                    rg.addView(rb);
                }

                llQuizQuestions.addView(block);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Parse error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void submitAnswers() {
        if (submitInFlight) return;

        try {
            JSONArray answers = new JSONArray();
            for (int i = 0; i < llQuizQuestions.getChildCount(); i++) {
                View block = llQuizQuestions.getChildAt(i);
                Object tag = block.getTag();
                if (!(tag instanceof Integer)) continue;

                int qid = (Integer) tag;
                RadioGroup rg = block.findViewById(R.id.rgChoices);
                int checkedId = rg.getCheckedRadioButtonId();
                if (checkedId == -1) {
                    Toast.makeText(this, "Answer every question before submitting", Toast.LENGTH_SHORT).show();
                    return;
                }

                RadioButton rb = block.findViewById(checkedId);
                Object letterObj = rb.getTag();
                String letter = letterObj != null ? letterObj.toString() : "";

                JSONObject one = new JSONObject();
                one.put("question_id", qid);
                one.put("selected_option", letter);
                answers.put(one);
            }

            if (answers.length() == 0) {
                Toast.makeText(this, "No answers", Toast.LENGTH_SHORT).show();
                return;
            }

            JSONObject body = new JSONObject();
            body.put("firebase_uid", firebaseUid);
            body.put("subject", subjectName);
            body.put("answers", answers);

            submitInFlight = true;
            btnSubmitQuiz.setEnabled(false);

            JsonObjectRequest req = new JsonObjectRequest(
                    Request.Method.POST,
                    QUIZ_BASE + "/submit",
                    body,
                    response -> {
                        submitInFlight = false;
                        btnSubmitQuiz.setEnabled(true);

                        if (!response.optBoolean("success", false)) {
                            Toast.makeText(this,
                                    response.optString("error", "Submit failed"),
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        int confidence = response.optInt("confidence", 0);
                        int difficulty = response.optInt("difficulty", 2);

                        Intent data = new Intent();
                        data.putExtra(RESULT_SUBJECT_NAME, subjectName);
                        data.putExtra(RESULT_CONFIDENCE, confidence);
                        data.putExtra(RESULT_DIFFICULTY, difficulty);
                        setResult(RESULT_OK, data);

                        Toast.makeText(this,
                                "Score saved: " + confidence + "% confidence",
                                Toast.LENGTH_LONG).show();
                        finish();
                    },
                    error -> {
                        submitInFlight = false;
                        btnSubmitQuiz.setEnabled(true);
                        String msg = quizErrorMessage(error, "Submit failed");
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    }
            ) {
                @Override
                public Map<String, String> getHeaders() {
                    Map<String, String> h = new HashMap<>();
                    h.put("Content-Type", "application/json");
                    return h;
                }
            };

            Volley.newRequestQueue(this).add(req);
        } catch (Exception e) {
            submitInFlight = false;
            btnSubmitQuiz.setEnabled(true);
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
