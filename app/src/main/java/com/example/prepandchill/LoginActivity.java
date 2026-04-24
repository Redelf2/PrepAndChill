package com.example.prepandchill;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.*;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.*;
import com.android.volley.toolbox.*;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.*;

import com.google.android.gms.auth.api.signin.*;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {

    private boolean passwordVisible = false;
    private FirebaseAuth mAuth;
    private RequestQueue requestQueue;

    private GoogleSignInClient googleSignInClient;
    private static final int RC_SIGN_IN = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();
        requestQueue = Volley.newRequestQueue(this);

        EditText etEmail = findViewById(R.id.etEmail);
        EditText etPassword = findViewById(R.id.etPassword);
        ImageView btnBack = findViewById(R.id.btnBack);
        ImageView btnToggle = findViewById(R.id.btnTogglePassword);
        MaterialButton btnLogin = findViewById(R.id.btnLogin);
        TextView tvSignUp = findViewById(R.id.tvSignUp);
        View googleBtn = findViewById(R.id.btnGoogle);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        googleSignInClient = GoogleSignIn.getClient(this, gso);

        btnBack.setOnClickListener(v -> finish());


        btnToggle.setOnClickListener(v -> {
            if (passwordVisible) {
                etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
            } else {
                etPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            }
            passwordVisible = !passwordVisible;
            etPassword.setSelection(etPassword.length());
        });


        btnLogin.setOnClickListener(v -> {

            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {

                        if (task.isSuccessful()) {

                            FirebaseUser user = mAuth.getCurrentUser();

                            if (user != null) {
                                sendToServer(
                                        user.getUid(),
                                        user.getDisplayName() != null ? user.getDisplayName() : "User",
                                        user.getEmail()
                                );
                            }

                        } else {
                            Toast.makeText(this,
                                    task.getException() != null ? task.getException().getMessage() : "Login failed",
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        });


        googleBtn.setOnClickListener(v -> {
            googleSignInClient.signOut().addOnCompleteListener(task -> {
                Intent signInIntent = googleSignInClient.getSignInIntent();
                startActivityForResult(signInIntent, RC_SIGN_IN);
            });
        });

        tvSignUp.setOnClickListener(v -> {
            startActivity(new Intent(this, CreateAccountActivity.class));
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);

            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (Exception e) {
                Toast.makeText(this, "Google Sign-In Failed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {

        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);

        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {

                    if (task.isSuccessful()) {

                        FirebaseUser user = mAuth.getCurrentUser();

                        if (user != null) {
                            sendToServer(
                                    user.getUid(),
                                    user.getDisplayName(),
                                    user.getEmail()
                            );
                        }
                    }
                });
    }


    private void sendToServer(String uid, String username, String email) {

        String url = "http://10.7.28.203:3000/register";

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
                            Toast.LENGTH_SHORT).show();

                    startActivity(new Intent(this, ExamSelectionActivity.class));
                    finish();
                },
                error -> {
                    Toast.makeText(this,
                            "Network Error: " + error.toString(),
                            Toast.LENGTH_LONG).show();
                }
        );

        requestQueue.add(request);
    }
}