package com.comp9003.foodtracker;

import android.app.TimePickerDialog;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.comp9003.foodtracker.reminder.ReminderScheduler;

import java.util.Locale;

public class ReminderSettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "reminder_prefs";

    private static final String KEY_DAILY_ENABLED = "daily_enabled";
    private static final String KEY_DAILY_TIME_MINUTES = "daily_time_minutes";
    private static final String KEY_RINGTONE = "ringtone";
    private static final String KEY_PERSISTENT_ENABLED = "persistent_enabled";
    private static final String KEY_EXPIRE_RULE = "expire_rule";
    private static final String KEY_NEAR_EXPIRE_RULE = "near_expire_rule";

    private static final int RINGTONE_FOLLOW_SYSTEM = 0;
    private static final int RINGTONE_DEFAULT = 1;
    private static final int RINGTONE_BELL = 2;

    private static final int RULE_DISABLED = 0;
    private static final int RULE_TODAY = 1;
    private static final int RULE_BEFORE_2 = 2;
    private static final int RULE_BEFORE_3 = 3;
    private static final int RULE_BEFORE_15 = 4;
    private static final int RULE_BEFORE_30 = 5;

    private static final int REQ_POST_NOTIFICATIONS = 9901;
    private static final String KEY_RULES_MIGRATED = "rules_migrated_v2";

    private SharedPreferences prefs;

    private SwitchCompat switchDaily;
    private SwitchCompat switchPersistent;

    private LinearLayout rowTime;
    private LinearLayout rowRingtone;
    private LinearLayout rowExpire;
    private LinearLayout rowNearExpire;

    private TextView tvTimeValue;
    private TextView tvRingtoneValue;
    private TextView tvExpireValue;
    private TextView tvNearExpireValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_reminder_settings);

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

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        ensureDefaults();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        switchDaily = findViewById(R.id.switchDaily);
        switchPersistent = findViewById(R.id.switchPersistent);

        rowTime = findViewById(R.id.rowTime);
        rowRingtone = findViewById(R.id.rowRingtone);
        rowExpire = findViewById(R.id.rowExpire);
        rowNearExpire = findViewById(R.id.rowNearExpire);

        tvTimeValue = findViewById(R.id.tvTimeValue);
        tvRingtoneValue = findViewById(R.id.tvRingtoneValue);
        tvExpireValue = findViewById(R.id.tvExpireValue);
        tvNearExpireValue = findViewById(R.id.tvNearExpireValue);

        bind();
        refreshUi();
        if (prefs.getBoolean(KEY_DAILY_ENABLED, true)) {
            ensureNotificationPermission();
        }
        ReminderScheduler.apply(this);
    }

    private void ensureDefaults() {
        SharedPreferences.Editor editor = prefs.edit();
        boolean changed = false;
        if (!prefs.contains(KEY_DAILY_ENABLED)) {
            editor.putBoolean(KEY_DAILY_ENABLED, true);
            changed = true;
        }
        if (!prefs.contains(KEY_DAILY_TIME_MINUTES)) {
            editor.putInt(KEY_DAILY_TIME_MINUTES, 9 * 60);
            changed = true;
        }
        if (!prefs.contains(KEY_RINGTONE)) {
            editor.putInt(KEY_RINGTONE, RINGTONE_FOLLOW_SYSTEM);
            changed = true;
        }
        if (!prefs.contains(KEY_PERSISTENT_ENABLED)) {
            editor.putBoolean(KEY_PERSISTENT_ENABLED, false);
            changed = true;
        }
        if (!prefs.contains(KEY_EXPIRE_RULE)) {
            editor.putInt(KEY_EXPIRE_RULE, RULE_BEFORE_3);
            changed = true;
        }
        if (!prefs.contains(KEY_NEAR_EXPIRE_RULE)) {
            editor.putInt(KEY_NEAR_EXPIRE_RULE, RULE_TODAY);
            changed = true;
        }
        if (changed) editor.apply();

        if (!prefs.getBoolean(KEY_RULES_MIGRATED, false)) {
            migrateLegacyRules();
        }
    }

    private void migrateLegacyRules() {
        int expireRule = prefs.getInt(KEY_EXPIRE_RULE, RULE_BEFORE_3);
        int nearRule = prefs.getInt(KEY_NEAR_EXPIRE_RULE, RULE_TODAY);

        int migratedExpire = expireRule;
        if (expireRule == 0) migratedExpire = RULE_BEFORE_15;
        else if (expireRule == 1) migratedExpire = RULE_TODAY;
        else if (expireRule == 2) migratedExpire = RULE_BEFORE_3;
        else if (expireRule == 3) migratedExpire = RULE_BEFORE_15;

        int migratedNear = nearRule;
        if (nearRule == 0) migratedNear = RULE_TODAY;
        else if (nearRule == 1) migratedNear = RULE_BEFORE_2;
        else if (nearRule == 2) migratedNear = RULE_BEFORE_3;

        prefs.edit()
                .putInt(KEY_EXPIRE_RULE, migratedExpire)
                .putInt(KEY_NEAR_EXPIRE_RULE, migratedNear)
                .putBoolean(KEY_RULES_MIGRATED, true)
                .apply();
    }

    private void bind() {
        switchDaily.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_DAILY_ENABLED, isChecked).apply();
            if (isChecked) ensureNotificationPermission();
            refreshUi();
            ReminderScheduler.apply(this);
        });

        switchPersistent.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_PERSISTENT_ENABLED, isChecked).apply();
            refreshUi();
        });

        rowTime.setOnClickListener(v -> openTimePicker());
        rowRingtone.setOnClickListener(v -> openRingtoneDialog());
        rowExpire.setOnClickListener(v -> openExpireRuleDialog());
        rowNearExpire.setOnClickListener(v -> openNearExpireRuleDialog());
    }

    private void refreshUi() {
        boolean dailyEnabled = prefs.getBoolean(KEY_DAILY_ENABLED, true);
        int minutes = prefs.getInt(KEY_DAILY_TIME_MINUTES, 9 * 60);
        int ringtone = prefs.getInt(KEY_RINGTONE, RINGTONE_FOLLOW_SYSTEM);
        boolean persistentEnabled = prefs.getBoolean(KEY_PERSISTENT_ENABLED, false);

        int expireRule = prefs.getInt(KEY_EXPIRE_RULE, RULE_BEFORE_3);
        int nearRule = prefs.getInt(KEY_NEAR_EXPIRE_RULE, RULE_TODAY);

        switchDaily.setChecked(dailyEnabled);
        switchPersistent.setChecked(persistentEnabled);

        tvTimeValue.setText(formatTime(minutes));
        tvRingtoneValue.setText(ringtoneLabel(ringtone));
        tvExpireValue.setText(expireRuleLabel(expireRule));
        tvNearExpireValue.setText(nearRuleLabel(nearRule));

        setRowEnabled(rowTime, tvTimeValue, dailyEnabled);
        setRowEnabled(rowRingtone, tvRingtoneValue, dailyEnabled);
        switchPersistent.setEnabled(dailyEnabled);
        findViewById(R.id.rowPersistent).setEnabled(dailyEnabled);
        findViewById(R.id.rowPersistent).setAlpha(dailyEnabled ? 1.0f : 0.45f);
    }

    private void setRowEnabled(LinearLayout row, TextView value, boolean enabled) {
        row.setEnabled(enabled);
        row.setClickable(enabled);
        row.setAlpha(enabled ? 1.0f : 0.45f);
        value.setAlpha(enabled ? 1.0f : 0.7f);
    }

    private void openTimePicker() {
        int minutes = prefs.getInt(KEY_DAILY_TIME_MINUTES, 9 * 60);
        int hour = minutes / 60;
        int minute = minutes % 60;
        TimePickerDialog dialog = new TimePickerDialog(
                this,
                (view, selectedHour, selectedMinute) -> {
                    prefs.edit().putInt(KEY_DAILY_TIME_MINUTES, selectedHour * 60 + selectedMinute).apply();
                    refreshUi();
                    ReminderScheduler.apply(this);
                },
                hour,
                minute,
                true
        );
        dialog.show();
    }

    private void openRingtoneDialog() {
        int current = prefs.getInt(KEY_RINGTONE, RINGTONE_FOLLOW_SYSTEM);
        String[] labels = new String[]{"Follow system", "Default", "Bell"};
        int[] values = new int[]{RINGTONE_FOLLOW_SYSTEM, RINGTONE_DEFAULT, RINGTONE_BELL};
        int checkedIndex = indexOf(values, current);
        new AlertDialog.Builder(this)
                .setTitle("Ringtone")
                .setSingleChoiceItems(labels, checkedIndex, (dialog, which) -> {
                    prefs.edit().putInt(KEY_RINGTONE, values[which]).apply();
                    dialog.dismiss();
                    refreshUi();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openExpireRuleDialog() {
        int current = prefs.getInt(KEY_EXPIRE_RULE, RULE_BEFORE_3);
        String[] labels = new String[]{"Do not remind", "On expiry day", "2 days before expiry", "3 days before expiry", "15 days before expiry", "30 days before expiry"};
        int[] values = new int[]{RULE_DISABLED, RULE_TODAY, RULE_BEFORE_2, RULE_BEFORE_3, RULE_BEFORE_15, RULE_BEFORE_30};
        int checkedIndex = indexOf(values, current);
        new AlertDialog.Builder(this)
                .setTitle("Expired")
                .setSingleChoiceItems(labels, checkedIndex, (dialog, which) -> {
                    prefs.edit().putInt(KEY_EXPIRE_RULE, values[which]).apply();
                    dialog.dismiss();
                    refreshUi();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openNearExpireRuleDialog() {
        int current = prefs.getInt(KEY_NEAR_EXPIRE_RULE, RULE_TODAY);
        String[] labels = new String[]{"Do not remind", "On near-expiry day", "2 days before near-expiry", "3 days before near-expiry", "15 days before near-expiry", "30 days before near-expiry"};
        int[] values = new int[]{RULE_DISABLED, RULE_TODAY, RULE_BEFORE_2, RULE_BEFORE_3, RULE_BEFORE_15, RULE_BEFORE_30};
        int checkedIndex = indexOf(values, current);
        new AlertDialog.Builder(this)
                .setTitle("Near-expiry")
                .setSingleChoiceItems(labels, checkedIndex, (dialog, which) -> {
                    prefs.edit().putInt(KEY_NEAR_EXPIRE_RULE, values[which]).apply();
                    dialog.dismiss();
                    refreshUi();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int indexOf(int[] values, int target) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == target) return i;
        }
        return 0;
    }

    private String formatTime(int minutes) {
        int hour = minutes / 60;
        int minute = minutes % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
    }

    private String ringtoneLabel(int ringtone) {
        if (ringtone == RINGTONE_DEFAULT) return "Default";
        if (ringtone == RINGTONE_BELL) return "Bell";
        return "Follow system";
    }

    private String expireRuleLabel(int rule) {
        if (rule == RULE_TODAY) return "On expiry day";
        if (rule == RULE_BEFORE_2) return "2 days before expiry";
        if (rule == RULE_BEFORE_3) return "3 days before expiry";
        if (rule == RULE_BEFORE_15) return "15 days before expiry";
        if (rule == RULE_BEFORE_30) return "30 days before expiry";
        return "Do not remind";
    }

    private String nearRuleLabel(int rule) {
        if (rule == RULE_TODAY) return "On near-expiry day";
        if (rule == RULE_BEFORE_2) return "2 days before near-expiry";
        if (rule == RULE_BEFORE_3) return "3 days before near-expiry";
        if (rule == RULE_BEFORE_15) return "15 days before near-expiry";
        if (rule == RULE_BEFORE_30) return "30 days before near-expiry";
        return "Do not remind";
    }

    private void ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        ActivityCompat.requestPermissions(
                this,
                new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                REQ_POST_NOTIFICATIONS
        );
    }
}
