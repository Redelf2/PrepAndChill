package com.example.prepandchill;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import android.content.res.ColorStateList;
import android.graphics.Color;

public class CreateAccountActivity extends AppCompatActivity {

    boolean passwordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_account);
        EditText etFullName = findViewById(R.id.etFullName);
        EditText etEmail = findViewById(R.id.etEmail);
        ImageView btnBack = findViewById(R.id.btnBack);
        EditText etPassword = findViewById(R.id.etPassword);
        ImageView btnToggle = findViewById(R.id.btnTogglePassword);
        MaterialButton btnCreate = findViewById(R.id.btnCreateAccount);
        TextView tvLogin = findViewById(R.id.tvLogin);

        etFullName.setHintTextColor(Color.parseColor("#475569"));
        etEmail.setHintTextColor(Color.parseColor("#475569"));
        etPassword.setHintTextColor(Color.parseColor("#475569"));

        btnBack.setOnClickListener(v -> finish());

        //  Toggle password visibility
        btnToggle.setOnClickListener(v -> {
            if (passwordVisible) {
                etPassword.setTransformationMethod(
                        PasswordTransformationMethod.getInstance());
                passwordVisible = false;
            } else {
                etPassword.setTransformationMethod(
                        HideReturnsTransformationMethod.getInstance());
                passwordVisible = true;
            }
            etPassword.setSelection(etPassword.length());
        });

        //  Create Account button goes to Exam Selection
        btnCreate.setOnClickListener(v -> {
            Intent intent = new Intent(
                    CreateAccountActivity.this,
                    ExamSelectionActivity.class);
            startActivity(intent);
        });

        //  "Log in" goes back to Splash
        tvLogin.setOnClickListener(v -> finish());
    }
}