package com.comp9003.foodtracker;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Sign-up screen for creating a new account.
 * Enforces username and password complexity rules before calling ProductRepository.signUp().
 * Returns RESULT_OK to LoginActivity on success so the user is logged in immediately.
 */
public class SignupActivity extends AppCompatActivity {
    private static final int USERNAME_MAX_LEN = 20;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
        int ui = getWindow().getDecorView().getSystemUiVisibility();
        ui |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        ui |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        ui |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        getWindow().getDecorView().setSystemUiVisibility(ui);
        setContentView(R.layout.activity_signup);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.content_container), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        EditText etUsername = findViewById(R.id.name);
        EditText etPassword = findViewById(R.id.username);
        EditText etConfirm = findViewById(R.id.pwd);
        Button btnSignUp = findViewById(R.id.btn_sign_up);

        btnSignUp.setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString();
            String confirm = etConfirm.getText().toString();

            if (username.isEmpty()) {
                etUsername.setError("Please enter a username");
                return;
            }

            if (username.length() < 6) {
                etUsername.setError("Username must be at least 6 characters");
                return;
            }

            if (username.length() > USERNAME_MAX_LEN) {
                etUsername.setError("Username must be at most 20 characters");
                return;
            }

            if (!username.matches("^[A-Za-z0-9_]+$")) {
                etUsername.setError("Username can only contain letters, numbers, and underscore");
                return;
            }

            if (ProductRepository.userExists(this, username)) {
                etUsername.setError("This username is already taken");
                return;
            }

            if (password.trim().isEmpty()) {
                etPassword.setError("Please enter a password");
                return;
            }

            if (password.length() < 6) {
                etPassword.setError("Password must be at least 6 characters");
                return;
            }

            if (password.length() > 32) {
                etPassword.setError("Password must be at most 32 characters");
                return;
            }

            if (!containsUpper(password) || !containsLower(password) || !containsDigit(password)) {
                etPassword.setError("Password must include uppercase, lowercase, and a number");
                return;
            }

            if (!password.equals(confirm)) {
                etConfirm.setError("Passwords do not match");
                return;
            }

            boolean ok = ProductRepository.signUp(this, username, password);
            if (!ok) {
                Toast.makeText(this, "Sign up failed. Please try again.", Toast.LENGTH_SHORT).show();
                return;
            }

            setResult(RESULT_OK);
            finish();
        });
    }

    private static boolean containsUpper(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'A' && c <= 'Z') return true;
        }
        return false;
    }

    private static boolean containsLower(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'a' && c <= 'z') return true;
        }
        return false;
    }

    private static boolean containsDigit(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') return true;
        }
        return false;
    }
}
