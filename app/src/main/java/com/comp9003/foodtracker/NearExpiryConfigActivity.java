package com.comp9003.foodtracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.LinkedHashMap;
import java.util.Map;

public class NearExpiryConfigActivity extends AppCompatActivity {

    static final String PREFS_NAME  = "near_expiry_prefs";
    static final String KEY_ENABLED = "enabled";
    private static final String KEY_PREFIX = "days_";   // e.g. "days_fruit"

    // Category key → default near-expiry days
    private static final LinkedHashMap<String, Integer> DEFAULTS = new LinkedHashMap<>();
    static {
        DEFAULTS.put("fruit",           3);
        DEFAULTS.put("vegetable",       3);
        DEFAULTS.put("meat_chilled",    1);
        DEFAULTS.put("seafood_chilled", 1);
        DEFAULTS.put("meat_frozen",    30);
        DEFAULTS.put("seafood_frozen", 30);
        DEFAULTS.put("dairy",           2);
        DEFAULTS.put("bakery",          1);
        DEFAULTS.put("snack",          30);
        DEFAULTS.put("frozen",         30);
        DEFAULTS.put("drink",          30);
        DEFAULTS.put("healthy",        90);
        DEFAULTS.put("sweet",          30);
        DEFAULTS.put("spicy",          60);
    }

    private static final LinkedHashMap<String, String> LABELS = new LinkedHashMap<>();
    static {
        LABELS.put("fruit",           "Fruit");
        LABELS.put("vegetable",       "Vegetables");
        LABELS.put("meat_chilled",    "Meat (Chilled)");
        LABELS.put("seafood_chilled", "Seafood (Chilled)");
        LABELS.put("meat_frozen",     "Meat (Frozen)");
        LABELS.put("seafood_frozen",  "Seafood (Frozen)");
        LABELS.put("dairy",           "Dairy");
        LABELS.put("bakery",          "Bakery");
        LABELS.put("snack",           "Snacks");
        LABELS.put("frozen",          "Frozen Food");
        LABELS.put("drink",           "Drinks");
        LABELS.put("healthy",         "Supplements");
        LABELS.put("sweet",           "Candy");
        LABELS.put("spicy",           "Condiments");
    }

    /**
     * Returns the configured near-expiry threshold days for the given category key.
     * Key matches the same keys as ExpirySettingsActivity (fruit, meat_chilled, etc.).
     */
    public static int getNearExpiryDays(Context context, String key) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int def = DEFAULTS.containsKey(key) ? DEFAULTS.get(key) : 3;
        return prefs.getInt(KEY_PREFIX + key, def);
    }

    // ─── Activity ─────────────────────────────────────────────────────────────

    private SharedPreferences prefs;
    private SwitchCompat switchEnabled;
    private LinearLayout rowsContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_near_expiry_config);

        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
        int ui = getWindow().getDecorView().getSystemUiVisibility();
        ui |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ui |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(ui);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        ensureDefaults();

        switchEnabled = findViewById(R.id.switchEnabled);
        rowsContainer = findViewById(R.id.rows_container);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnReset).setOnClickListener(v -> resetAll());

        switchEnabled.setOnCheckedChangeListener((btn, checked) ->
                prefs.edit().putBoolean(KEY_ENABLED, checked).apply());

        buildRows();
        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUi();
    }

    private void ensureDefaults() {
        if (!prefs.contains(KEY_ENABLED)) {
            prefs.edit().putBoolean(KEY_ENABLED, true).apply();
        }
        SharedPreferences.Editor editor = prefs.edit();
        for (Map.Entry<String, Integer> e : DEFAULTS.entrySet()) {
            String k = KEY_PREFIX + e.getKey();
            if (!prefs.contains(k)) editor.putInt(k, e.getValue());
        }
        editor.apply();
    }

    private void refreshUi() {
        switchEnabled.setChecked(prefs.getBoolean(KEY_ENABLED, true));
    }

    // ─── Row building ─────────────────────────────────────────────────────────

    private void buildRows() {
        rowsContainer.removeAllViews();
        float density = getResources().getDisplayMetrics().density;

        boolean first = true;
        for (Map.Entry<String, String> entry : LABELS.entrySet()) {
            String key   = entry.getKey();
            String label = entry.getValue();
            int days     = prefs.getInt(KEY_PREFIX + key, DEFAULTS.get(key));

            if (!first) {
                View divider = new View(this);
                divider.setBackgroundColor(0xFFE6D8C9);
                LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, (int) density);
                divLp.setMarginStart((int) (18 * density));
                divLp.setMarginEnd((int) (18 * density));
                divider.setLayoutParams(divLp);
                rowsContainer.addView(divider);
            }
            first = false;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setClickable(true);
            row.setFocusable(true);
            int[] attrs = {android.R.attr.selectableItemBackground};
            android.content.res.TypedArray ta = obtainStyledAttributes(attrs);
            android.graphics.drawable.Drawable ripple = ta.getDrawable(0);
            ta.recycle();
            row.setBackground(ripple);

            int hPad = (int) (18 * density);
            int rowH = (int) (56 * density);
            row.setPadding(hPad, 0, hPad, 0);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, rowH));

            TextView tvLabel = new TextView(this);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tvLabel.setLayoutParams(labelLp);
            tvLabel.setText(label);
            tvLabel.setTextColor(0xFF514438);
            tvLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);

            TextView tvDays = new TextView(this);
            tvDays.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            tvDays.setText(formatDays(days));
            tvDays.setTextColor(0xFF8A7B6E);
            tvDays.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);

            TextView tvChevron = new TextView(this);
            LinearLayout.LayoutParams chevLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            chevLp.setMarginStart((int) (6 * density));
            tvChevron.setLayoutParams(chevLp);
            tvChevron.setText("›");
            tvChevron.setTextColor(0xFF514438);
            tvChevron.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20);

            row.addView(tvLabel);
            row.addView(tvDays);
            row.addView(tvChevron);

            final String rowKey = key;
            row.setOnClickListener(v -> showEditDialog(
                    rowKey, LABELS.get(rowKey), tvDays,
                    "Days before the item appears in Near-Expiry list"));

            rowsContainer.addView(row);
        }
    }

    // ─── Edit dialog ──────────────────────────────────────────────────────────

    static void showEditDaysDialog(
            Context context,
            android.view.LayoutInflater inflater,
            String prefsName,
            String prefKey,
            int defaultDays,
            String categoryLabel,
            String subtitle,
            TextView tvDaysToUpdate,
            Runnable onSaved) {

        View dlgView = inflater.inflate(R.layout.dialog_edit_days, null);

        TextView tvTitle    = dlgView.findViewById(R.id.dlg_title);
        TextView tvSubtitle = dlgView.findViewById(R.id.dlg_subtitle);
        EditText etInput    = dlgView.findViewById(R.id.dlg_input);
        TextView tvHint     = dlgView.findViewById(R.id.dlg_default_hint);
        TextView btnMinus   = dlgView.findViewById(R.id.dlg_btn_minus);
        TextView btnPlus    = dlgView.findViewById(R.id.dlg_btn_plus);

        tvTitle.setText(categoryLabel);
        tvSubtitle.setText(subtitle);

        SharedPreferences prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        int current = prefs.getInt(prefKey, defaultDays);
        etInput.setText(String.valueOf(current));
        // Move cursor to end, no selection highlight
        etInput.setSelection(etInput.getText().length());
        // Warm text highlight color instead of default blue
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            etInput.setTextSelectHandle(R.drawable.dlg_stepper_btn_bg);
        }
        tvHint.setText("Default: " + formatDays(defaultDays));

        // Stepper helpers
        Runnable stepUp = () -> {
            String t = etInput.getText().toString().trim();
            int v = 1;
            try { v = Integer.parseInt(t); } catch (NumberFormatException ignored) {}
            v = Math.min(v + 1, 9999);
            etInput.setText(String.valueOf(v));
            etInput.setSelection(etInput.getText().length());
        };
        Runnable stepDown = () -> {
            String t = etInput.getText().toString().trim();
            int v = 2;
            try { v = Integer.parseInt(t); } catch (NumberFormatException ignored) {}
            v = Math.max(v - 1, 1);
            etInput.setText(String.valueOf(v));
            etInput.setSelection(etInput.getText().length());
        };

        btnPlus.setOnClickListener(v -> stepUp.run());
        btnMinus.setOnClickListener(v -> stepDown.run());

        // Long-press repeat for stepper buttons
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        Runnable[] repeatUp   = {null};
        Runnable[] repeatDown = {null};
        repeatUp[0]   = () -> { stepUp.run();   handler.postDelayed(repeatUp[0],   80); };
        repeatDown[0] = () -> { stepDown.run(); handler.postDelayed(repeatDown[0], 80); };
        btnPlus.setOnLongClickListener(v -> { handler.postDelayed(repeatUp[0],   300); return true; });
        btnMinus.setOnLongClickListener(v -> { handler.postDelayed(repeatDown[0], 300); return true; });
        btnPlus.setOnTouchListener((v, ev) -> {
            if (ev.getAction() == android.view.MotionEvent.ACTION_UP
                    || ev.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                handler.removeCallbacks(repeatUp[0]);
            }
            return false;
        });
        btnMinus.setOnTouchListener((v, ev) -> {
            if (ev.getAction() == android.view.MotionEvent.ACTION_UP
                    || ev.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                handler.removeCallbacks(repeatDown[0]);
            }
            return false;
        });

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(dlgView)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Restore Default", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xFFE58332);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(0xFF8A7B6E);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(0xFF8A7B6E);

            // Save with validation (don't dismiss on error)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String text = etInput.getText().toString().trim();
                if (text.isEmpty()) { highlight(etInput); return; }
                int val;
                try { val = Integer.parseInt(text); } catch (NumberFormatException e) {
                    highlight(etInput); return;
                }
                if (val <= 0) { highlight(etInput); return; }
                prefs.edit().putInt(prefKey, val).apply();
                if (tvDaysToUpdate != null) tvDaysToUpdate.setText(formatDays(val));
                if (onSaved != null) onSaved.run();
                dialog.dismiss();
            });

            // Restore default — fill input, don't save yet
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                etInput.setText(String.valueOf(defaultDays));
                etInput.setSelection(etInput.getText().length());
            });

            // Auto-show keyboard
            etInput.post(() -> {
                etInput.requestFocus();
                android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager)
                        context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(etInput,
                        android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            });
        });

        dialog.show();
    }

    /** Flash the input box border red briefly to signal invalid input. */
    private static void highlight(EditText et) {
        android.graphics.drawable.GradientDrawable err =
                new android.graphics.drawable.GradientDrawable();
        err.setColor(0xFFFFEEEE);
        err.setStroke((int)(1.5f * et.getResources().getDisplayMetrics().density), 0xFFE53935);
        err.setCornerRadius(14 * et.getResources().getDisplayMetrics().density);
        et.setBackground(err);
        et.postDelayed(() -> et.setBackgroundResource(R.drawable.dlg_input_bg), 600);
    }

    private void showEditDialog(String key, String label, TextView tvDays, String subtitle) {
        showEditDaysDialog(
                this,
                getLayoutInflater(),
                PREFS_NAME,
                KEY_PREFIX + key,
                DEFAULTS.get(key),
                label,
                subtitle,
                tvDays,
                null);
    }

    static String formatDays(int days) {
        return days + " " + (days == 1 ? "day" : "days");
    }

    // ─── Reset ────────────────────────────────────────────────────────────────

    private void resetAll() {
        new AlertDialog.Builder(this)
                .setTitle("Reset to defaults")
                .setMessage("Restore all categories to their default near-expiry days?")
                .setPositiveButton("Reset", (dialog, which) -> {
                    SharedPreferences.Editor editor = prefs.edit();
                    for (Map.Entry<String, Integer> e : DEFAULTS.entrySet()) {
                        editor.putInt(KEY_PREFIX + e.getKey(), e.getValue());
                    }
                    editor.apply();
                    buildRows();
                    android.widget.Toast.makeText(this,
                            "Reset to defaults", android.widget.Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
