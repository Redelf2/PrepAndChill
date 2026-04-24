package com.example.prepandchill;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class CreateAccountActivity extends AppCompatActivity {

    private boolean passwordVisible = false;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_account);

        mAuth = FirebaseAuth.getInstance();

        EditText etFullName = findViewById(R.id.etFullName);
        EditText etEmail = findViewById(R.id.etEmail);
        EditText etPassword = findViewById(R.id.etPassword);
        ImageView btnBack = findViewById(R.id.btnBack);
        ImageView btnToggle = findViewById(R.id.btnTogglePassword);
        MaterialButton btnCreate = findViewById(R.id.btnCreateAccount);
        TextView tvLogin = findViewById(R.id.tvLogin);

        etFullName.setHintTextColor(Color.parseColor("#475569"));
        etEmail.setHintTextColor(Color.parseColor("#475569"));
        etPassword.setHintTextColor(Color.parseColor("#475569"));

        btnBack.setOnClickListener(v -> finish());

        // Show / Hide password
        btnToggle.setOnClickListener(v -> {
            if (passwordVisible) {
                etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
                passwordVisible = false;
            } else {
                etPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                passwordVisible = true;
            }
            etPassword.setSelection(etPassword.length());
        });


        btnCreate.setOnClickListener(v -> {

            String username = etFullName.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            // 🔹 Validation
            if (TextUtils.isEmpty(username) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
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

                            // 🔴 Safety check
                            if (user == null) {
                                Toast.makeText(this, "User creation failed", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            // 🔹 Save extra data in Firestore
                            FirebaseFirestore db = FirebaseFirestore.getInstance();

                            Map<String, Object> map = new HashMap<>();
                            map.put("username", username);
                            map.put("email", email);

                            db.collection("users")
                                    .document(user.getUid())
                                    .set(map)
                                    .addOnSuccessListener(aVoid -> {

                                        Toast.makeText(this, "Account created successfully!", Toast.LENGTH_SHORT).show();

                                        // ✅ Move only after DB success
                                        Intent intent = new Intent(CreateAccountActivity.this, ExamSelectionActivity.class);
                                        startActivity(intent);
                                        finish();
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(this, "Database error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                    });

                        } else {

                            // 🔹 Better error handling
                            if (task.getException() instanceof FirebaseAuthUserCollisionException) {
                                Toast.makeText(this, "Email already registered", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(this,
                                        "Error: " + task.getException().getMessage(),
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    });
        });


        tvLogin.setOnClickListener(v -> {
            Intent intent = new Intent(CreateAccountActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        });
    }
}