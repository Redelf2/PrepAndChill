package com.example.prepandchill;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class TaskAgentApiClient {
    private static final String API_KEY = "YOUR_HUGGING_FACE_API_KEY"; // Replace with your actual API key
    private static final String MODEL = "google/flan-t5-small";
    private static final String ENDPOINT = "https://api-inference.huggingface.co/models/" + MODEL;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    public interface TaskAgentCallback {
        void onSuccess(TaskAgentResponse taskAgentResponse);
        void onFailure(String errorMessage);
    }

    public static void sendTaskAgentCommand(String userInput, TaskAgentCallback callback) {
        if (userInput == null || userInput.trim().isEmpty()) {
            callback.onFailure("Command cannot be empty");
            return;
        }

        String prompt = "Parse the user command into STRICT JSON only. " +
                "Return only an object with fields action and tasks. " +
                "action must be add_tasks. tasks must be an array of objects with title and duration. " +
                "Do not add any explanation, markdown, or extra keys. " +
                "If the command cannot be turned into tasks, return {\"action\":\"none\",\"tasks\":[]}. " +
                "User command: \"" + userInput.replace("\"", "\\\"") + "\".";

        try {
            JSONObject payload = new JSONObject();
            payload.put("inputs", prompt);
            payload.put("options", new JSONObject().put("wait_for_model", true));

            RequestBody body = RequestBody.create(payload.toString(), JSON);
            Request request = new Request.Builder()
                    .url(ENDPOINT)
                    .addHeader("Authorization", "Bearer " + API_KEY)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onFailure("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        callback.onFailure("AI service error: " + response.code());
                        return;
                    }

                    try {
                        String responseBody = response.body() != null ? response.body().string() : "";
                        TaskAgentResponse agentResponse = TaskAgentParser.parseResponse(responseBody);
                        callback.onSuccess(agentResponse);
                    } catch (IOException e) {
                        callback.onFailure("Response read failed: " + e.getMessage());
                    } catch (JSONException e) {
                        callback.onFailure("Invalid AI response format");
                    }
                }
            });
        } catch (JSONException e) {
            callback.onFailure("Request build failed: " + e.getMessage());
        }
    }
}
