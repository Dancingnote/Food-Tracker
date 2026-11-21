package com.comp9003.foodtracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.LinkedHashMap;
import java.util.Map;

public class ExpirySettingsActivity extends AppCompatActivity {

    static final String PREFS_NAME = "expiry_settings_prefs";

    // Category key → default days
    private static final LinkedHashMap<String, Integer> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put("fruit",          7);
        DEFAULTS.put("vegetable",      5);
        DEFAULTS.put("meat_chilled",   2);
        DEFAULTS.put("meat_frozen",   90);
        DEFAULTS.put("seafood_chilled", 1);
        DEFAULTS.put("seafood_frozen", 60);
        DEFAULTS.put("dairy",          5);
        DEFAULTS.put("bakery",         3);
        DEFAULTS.put("snack",        180);
        DEFAULTS.put("frozen",        90);
        DEFAULTS.put("drink",        180);
        DEFAULTS.put("healthy",      365);
        DEFAULTS.put("sweet",        365);
        DEFAULTS.put("spicy",        180);
    }

    private static final LinkedHashMap<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put("fruit",           "Fruit");
        LABELS.put("vegetable",       "Vegetables");
        LABELS.put("meat_chilled",    "Meat (Chilled)");
        LABELS.put("meat_frozen",     "Meat (Frozen)");
        LABELS.put("seafood_chilled", "Seafood (Chilled)");
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
     * Returns the recommended expiry days for the given tag and storage state.
     * Call from AddProductActivity / EditProductActivity instead of getDefaultExpireDays().
     */
    public static int getRecommendedDays(Context context, String tag, String storageState) {
        if (tag == null) return 7;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String key = resolveKey(tag, storageState);
        int def = DEFAULTS.containsKey(key) ? DEFAULTS.get(key) : 7;
        return prefs.getInt(key, def);
    }

    /** Maps an AddProduct tag name + storage state to a PREFS key. */
    private static String resolveKey(String tag, String storageState) {
        boolean frozen = "Frozen".equalsIgnoreCase(storageState)
                || "冷冻".equals(storageState); // 冷冻
        switch (tag) {
            case "Fruit":     return "fruit";
            case "Vegetable": return "vegetable";
            case "Meat":      return frozen ? "meat_frozen" : "meat_chilled";
            case "Seafood":   return frozen ? "seafood_frozen" : "seafood_chilled";
            case "Dairy":     return "dairy";
            case "Bakery":    return "bakery";
            case "Snack":     return "snack";
            case "Frozen":    return "frozen";
            case "Drink":     return "drink";
            case "Healthy":   return "healthy";
            case "Sweet":     return "sweet";
            case "Spicy":     return "spicy";
            default:          return "fruit";
        }
    }

    // ─── Activity ────────────────────────────────────────────────────────────

    private SharedPreferences prefs;
    private LinearLayout rowsContainer;

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
        ui |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ui |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(ui);

        setContentView(R.layout.activity_expiry_settings);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        rowsContainer = findViewById(R.id.rows_container);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnReset).setOnClickListener(v -> resetAll());

        buildRows();
    }

    // ─── Row building ─────────────────────────────────────────────────────────

    private void buildRows() {
        rowsContainer.removeAllViews();
        float density = getResources().getDisplayMetrics().density;

        boolean first = true;
        for (Map.Entry<String, String> entry : LABELS.entrySet()) {
            String key   = entry.getKey();
            String label = entry.getValue();
            int days     = prefs.getInt(key, DEFAULTS.get(key));

            if (!first) {
                // Divider
                View divider = new View(this);
                divider.setBackgroundColor(0xFFE6D8C9);
                LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (int) density);
                divLp.setMarginStart((int) (64 * density));
                divLp.setMarginEnd((int) (18 * density));
                divider.setLayoutParams(divLp);
                rowsContainer.addView(divider);
            }
            first = false;

            // Row
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setClickable(true);
            row.setFocusable(true);

            TypedValue outValue = new TypedValue();
            if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                    && outValue.resourceId != 0) {
                row.setBackgroundResource(outValue.resourceId);
            }

            int hPad = (int) (18 * density);
            int rowH = (int) (66 * density);
            row.setPadding(hPad, 0, hPad, 0);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, rowH));

            // Category label
            TextView tvLabel = new TextView(this);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tvLabel.setLayoutParams(labelLp);
            tvLabel.setText(label);
            tvLabel.setTextColor(0xFF514438);
            tvLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);

            // Days value
            TextView tvDays = new TextView(this);
            tvDays.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            tvDays.setText(NearExpiryConfigActivity.formatDays(days));
            tvDays.setTextColor(0xFF8A7B6E);
            tvDays.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);

            // Chevron
            TextView tvChevron = new TextView(this);
            LinearLayout.LayoutParams chevLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            chevLp.setMarginStart((int) (8 * density));
            tvChevron.setLayoutParams(chevLp);
            tvChevron.setText("›");
            tvChevron.setTextColor(0xFF514438);
            tvChevron.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22);

            row.addView(tvLabel);
            row.addView(tvDays);
            row.addView(tvChevron);

            final String rowKey = key;
            row.setOnClickListener(v -> showEditDialog(rowKey, LABELS.get(rowKey), tvDays, DEFAULTS.get(rowKey)));

            rowsContainer.addView(row);
        }
    }

    // ─── Edit dialog ──────────────────────────────────────────────────────────

    private void showEditDialog(String key, String label, TextView tvDays, int defaultDays) {
        NearExpiryConfigActivity.showEditDaysDialog(
                this,
                getLayoutInflater(),
                PREFS_NAME,
                key,
                defaultDays,
                label,
                "Set recommended expiry days per category",
                tvDays,
                null);
    }

    // ─── Reset ────────────────────────────────────────────────────────────────

    private void resetAll() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Reset to defaults")
                .setMessage("Restore all categories to their default recommended days?")
                .setPositiveButton("Reset", (dialog, which) -> {
                    SharedPreferences.Editor editor = prefs.edit();
                    for (Map.Entry<String, Integer> e : DEFAULTS.entrySet()) {
                        editor.putInt(e.getKey(), e.getValue());
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
