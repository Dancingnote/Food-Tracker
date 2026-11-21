package com.comp9003.foodtracker;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class EditProfileActivity extends AppCompatActivity {
    private static final int REQ_AVATAR = 6001;

    private ImageView ivAvatar;
    private TextView tvUsername;
    private EditText etNickname;
    private EditText etOldPwd;
    private EditText etNewPwd;
    private EditText etConfirmPwd;

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
        ui |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ui |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(ui);
        setContentView(R.layout.activity_edit_profile);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ProductRepository.ensureReady(this);

        ivAvatar = findViewById(R.id.iv_avatar);
        tvUsername = findViewById(R.id.tv_username);
        etNickname = findViewById(R.id.et_nickname);
        etOldPwd = findViewById(R.id.et_old_password);
        etNewPwd = findViewById(R.id.et_new_password);
        etConfirmPwd = findViewById(R.id.et_confirm_password);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_save).setOnClickListener(v -> saveAll());

        findViewById(R.id.iv_avatar).setOnClickListener(v ->
                startActivityForResult(new Intent(this, AvatarEditActivity.class), REQ_AVATAR)
        );

        bindProfile();
    }

    private void bindProfile() {
        String username = ProductRepository.getDisplayUsername(this);
        tvUsername.setText(username);

        String nickname = ProductRepository.getNickname(this);
        etNickname.setText(nickname);

        String avatar = ProductRepository.getAvatarUri(this);
        if (avatar != null) {
            try {
                ivAvatar.setImageURI(Uri.parse(avatar));
            } catch (Exception ignored) {
            }
        } else {
            ivAvatar.setImageResource(R.drawable.profile_placeholder);
        }
    }

    private void saveNickname() {
        String nickname = etNickname.getText().toString().trim();
        if (nickname.isEmpty()) {
            etNickname.setError("Please enter a nickname");
            return;
        }
        ProductRepository.setNickname(this, nickname);
    }

    private void savePassword() {
        String oldPwd = etOldPwd.getText().toString();
        String newPwd = etNewPwd.getText().toString();
        String confirm = etConfirmPwd.getText().toString();

        if (oldPwd.trim().isEmpty() && newPwd.trim().isEmpty() && confirm.trim().isEmpty()) {
            return;
        }

        if (oldPwd.trim().isEmpty()) {
            etOldPwd.setError("Please enter your current password");
            return;
        }
        if (newPwd.trim().isEmpty()) {
            etNewPwd.setError("Please enter a new password");
            return;
        }
        if (newPwd.length() < 6) {
            etNewPwd.setError("Password must be at least 6 characters");
            return;
        }
        if (newPwd.length() > 32) {
            etNewPwd.setError("Password must be at most 32 characters");
            return;
        }
        if (!containsUpper(newPwd) || !containsLower(newPwd) || !containsDigit(newPwd)) {
            etNewPwd.setError("Password must include uppercase, lowercase, and a number");
            return;
        }
        if (!newPwd.equals(confirm)) {
            etConfirmPwd.setError("Passwords do not match");
            return;
        }

        boolean ok = ProductRepository.changePassword(this, oldPwd, newPwd);
        if (!ok) {
            Toast.makeText(this, "Incorrect current password", Toast.LENGTH_SHORT).show();
            return;
        }

        etOldPwd.setText("");
        etNewPwd.setText("");
        etConfirmPwd.setText("");
    }

    private void saveAll() {
        saveNickname();
        if (etNickname.getError() != null) return;
        savePassword();
        if (etOldPwd.getError() != null || etNewPwd.getError() != null || etConfirmPwd.getError() != null) return;
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != REQ_AVATAR || resultCode != RESULT_OK) return;
        bindProfile();
    }
}
