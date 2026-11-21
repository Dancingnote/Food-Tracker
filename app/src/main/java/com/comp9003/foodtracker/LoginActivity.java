package com.comp9003.foodtracker;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Login screen shown before the main activity when no session exists.
 * On successful login, sets RESULT_OK so MainActivity can proceed.
 */
public class LoginActivity extends AppCompatActivity {
    private static final int REQ_SIGNUP = 2001;

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
        setContentView(R.layout.activity_login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.content_container), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        EditText etUsername = findViewById(R.id.username);
        EditText etPassword = findViewById(R.id.pwd);
        Button btnSignIn = findViewById(R.id.btn_sign_in);
        TextView tvNewUser = findViewById(R.id.tv_new_user);

        tvNewUser.setOnClickListener(v -> startActivityForResult(new Intent(this, SignupActivity.class), REQ_SIGNUP));

        Runnable doLogin = () -> {
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString();

            if (username.isEmpty()) {
                etUsername.setError("Username is required");
                return;
            }
            if (password.trim().isEmpty()) {
                etPassword.setError("Password is required");
                return;
            }

            boolean ok = ProductRepository.login(this, username, password);
            if (!ok) {
                Toast.makeText(this, "Invalid username or password", Toast.LENGTH_SHORT).show();
                return;
            }

            setResult(RESULT_OK);
            finish();
        };

        btnSignIn.setOnClickListener(v -> doLogin.run());

        etPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                doLogin.run();
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != REQ_SIGNUP) return;

        if (ProductRepository.isLoggedIn(this)) {
            setResult(RESULT_OK);
            finish();
        }
    }
}
