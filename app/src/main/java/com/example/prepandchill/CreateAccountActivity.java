package com.example.prepandchill;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.*;
import com.android.volley.toolbox.*;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.*;

import org.json.JSONObject;

public class CreateAccountActivity extends AppCompatActivity {

    private boolean passwordVisible = false;
    private FirebaseAuth mAuth;
    private RequestQueue requestQueue; // ✅ important

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_account);

        mAuth = FirebaseAuth.getInstance();
        requestQueue = Volley.newRequestQueue(this); // ✅ init once

        EditText etFullName = findViewById(R.id.etFullName);
        EditText etEmail = findViewById(R.id.etEmail);
        EditText etPassword = findViewById(R.id.etPassword);
        ImageView btnBack = findViewById(R.id.btnBack);
        ImageView btnToggle = findViewById(R.id.btnTogglePassword);
        MaterialButton btnCreate = findViewById(R.id.btnCreateAccount);
        TextView tvLogin = findViewById(R.id.tvLogin);

        btnBack.setOnClickListener(v -> finish());

        tvLogin.setOnClickListener(v -> {
            startActivity(new Intent(CreateAccountActivity.this, LoginActivity.class));
        });

        btnToggle.setOnClickListener(v -> {
            if (passwordVisible) {
                etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
            } else {
                etPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            }
            passwordVisible = !passwordVisible;
            etPassword.setSelection(etPassword.length()); // ✅ fix cursor
        });

        btnCreate.setOnClickListener(v -> {

            String username = etFullName.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (TextUtils.isEmpty(username) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            if (password.length() < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                return;
            }

            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {

                        if (task.isSuccessful()) {

                            FirebaseUser user = mAuth.getCurrentUser();

                            if (user != null) {
                                sendToServer(user.getUid(), username, email);
                            }

                        } else {
                            Toast.makeText(this,
                                    task.getException() != null ? task.getException().getMessage() : "Error",
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        });
    }

    private void sendToServer(String uid, String username, String email) {

        String url = "http://10.7.28.203:3000/api/auth/register";

        JSONObject json = new JSONObject();

        try {
            json.put("uid", uid);
            json.put("username", username);
            json.put("email", email);
        } catch (Exception e) {
            e.printStackTrace();
        }

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                url,
                json,
                response -> {
                    Toast.makeText(this,
                            response.optString("message"),
                            Toast.LENGTH_LONG).show();


                    startActivity(new Intent(this, ExamSelectionActivity.class));
                    finish();
                },
                error -> {
                    String msg;
                    if (error instanceof NoConnectionError || error instanceof TimeoutError) {
                        msg = "Can't reach server. Check Wi‑Fi/mobile data and ensure the server is running and reachable.";
                    } else if (error instanceof AuthFailureError) {
                        msg = "Auth failure while contacting server.";
                    } else if (error instanceof ServerError) {
                        msg = "Server error. Check backend logs.";
                    } else if (error instanceof ParseError) {
                        msg = "Bad response from server (parse error).";
                    } else {
                        msg = "Network error: " + error.toString();
                    }

                    Toast.makeText(this,
                            msg,
                            Toast.LENGTH_LONG).show();
                }
        );

        requestQueue.add(request); // ✅ use same queue
    }
}