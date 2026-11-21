package com.comp9003.foodtracker;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.LocaleList;
import android.view.View;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;
import com.comp9003.foodtracker.model.Product;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;


import java.io.File;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Locale;

public class EditProductActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        LocaleList localeList = LocaleList.forLanguageTags("en");
        Locale.setDefault(localeList.get(0));
        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        config.setLocales(localeList);
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    private EditText etName, etQuantity, etPrice,
            etCreatedDate, etProductionDate, etExpireDate;

    private TextView tvSave, tvCancel;
    private ImageView ivProduct;
    private Uri pendingImageUri;
    private String savedImageUri;
    private static final int REQ_PICK_IMAGE = 7101;
    private static final int REQ_TAKE_PHOTO = 7102;
    private static final int REQ_CAMERA_PERMISSION = 7103;
    private static final String STORAGE_ROOM = "Room temp";
    private static final String STORAGE_CHILLED = "Chilled";
    private static final String STORAGE_FROZEN = "Frozen";
    private static final String LEGACY_STORAGE_ROOM = "\u5e38\u6e29";
    private static final String LEGACY_STORAGE_CHILLED = "\u51b7\u85cf";
    private static final String LEGACY_STORAGE_FROZEN = "\u51b7\u51bb";

    private ArrayList<String> selectedTags = new ArrayList<>();

    private TextView tagFruit, tagVegetable, tagMeat, tagSeafood,
            tagDairy, tagBakery, tagSnack, tagFrozen,
            tagDrink, tagHealthy, tagSweet, tagSpicy;

    private TextView storageRoom, storageFridge, storageFreezer;
    private TextView openedYes, openedNo;
    private TextView sharedYes, sharedNo;
    private String selectedStorageState = null;
    private Boolean selectedOpened = null;
    private boolean selectedShared = false;

    private int productIndex = -1;
    private Product oldProduct;
    /** Non-null when editing a shared item owned by another group member. */
    private String ownerUser = null;
    private boolean pendingExpireDatePicker = false;

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
        setContentView(R.layout.activity_add_product);

        View main = findViewById(R.id.main);
        if (main != null) {
            int pl = main.getPaddingLeft();
            int pt = main.getPaddingTop();
            int pr = main.getPaddingRight();
            int pb = main.getPaddingBottom();
            ViewCompat.setOnApplyWindowInsetsListener(main, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(
                        pl + systemBars.left,
                        pt + systemBars.top,
                        pr + systemBars.right,
                        pb + systemBars.bottom
                );
                return insets;
            });
        }

        ProductRepository.ensureReady(this);

        TextView tvTitle = findViewById(R.id.tv_page_title);
        tvTitle.setText("Edit Product");

        ownerUser = getIntent().getStringExtra("ownerUser");

        if (ownerUser != null) {
            String currentUser = ProductRepository.getCurrentUser(this);
            if (!ProductRepository.areUsersInSameFamily(this, currentUser, ownerUser)) {
                Toast.makeText(this, "This shared item is only visible within your family", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            // Editing a shared product owned by another group member
            oldProduct = (com.comp9003.foodtracker.model.Product)
                    getIntent().getSerializableExtra("sharedProduct");
            if (oldProduct == null) { finish(); return; }
            productIndex = -1;
        } else {
            productIndex = getIntent().getIntExtra("index", -1);
            if (productIndex < 0 || productIndex >= ProductRepository.products.size()) {
                finish();
                return;
            }
            oldProduct = ProductRepository.products.get(productIndex);
        }

        ivProduct = findViewById(R.id.iv_product);
        etName = findViewById(R.id.et_name);
        etQuantity = findViewById(R.id.et_quantity);
        etPrice = findViewById(R.id.et_price);
        etCreatedDate = findViewById(R.id.et_created_date);
        etProductionDate = findViewById(R.id.et_production_date);
        etExpireDate = findViewById(R.id.et_expire_date);

        tvSave = findViewById(R.id.tv_save);
        tvCancel = findViewById(R.id.tv_cancel);

        bindTags();
        setupTagClicks();
        bindStorageAndOpened();
        setupStorageAndOpenedClicks();

        refreshStorageUi();
        refreshOpenedUi();

        fillOldData();

        tvCancel.setOnClickListener(v -> finish());
        tvSave.setOnClickListener(v -> saveEditedProduct());

        if (ivProduct != null) {
            ivProduct.setOnClickListener(v -> showImagePicker());
        }

        if (etCreatedDate != null) {
            etCreatedDate.setOnClickListener(v -> showCreatedDatePicker());
        }
        if (etProductionDate != null) {
            etProductionDate.setOnClickListener(v -> showProductionDatePicker());
        }
        if (etExpireDate != null) {
            etExpireDate.setOnClickListener(v -> onExpireDateClicked());
        }
    }

    private void onExpireDateClicked() {
        if (selectedTags.isEmpty()) {
            pendingExpireDatePicker = true;
            new AlertDialog.Builder(this)
                    .setMessage("Please select a category before setting expiry date.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        pendingExpireDatePicker = false;
        showExpireDatePicker();
    }

    private void showCreatedDatePicker() {
        LocalDate initial = parseDateOrNull(etCreatedDate.getText().toString().trim());
        if (initial == null) initial = LocalDate.now();
        long initialMillis = toEpochMillis(initial);

        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select added date")
                .setSelection(initialMillis)
                .setTextInputFormat(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()))
                .build();

        LocalDate created = initial;
        LocalDate production = parseDateOrNull(etProductionDate.getText().toString().trim());
        LocalDate outlineDate = (production != null) ? production : created;

        picker.addOnPositiveButtonClickListener(selection -> {
            LocalDate selected = fromEpochMillis(selection);
            etCreatedDate.setText(selected.toString());
        });

        showWithCalendarMarkers(picker, "created_date_picker", outlineDate, null, null);
    }

    private void showProductionDatePicker() {
        LocalDate created = parseDateOrNull(etCreatedDate.getText().toString().trim());
        if (created == null) created = LocalDate.now();
        final LocalDate createdFinal = created;

        LocalDate initial = parseDateOrNull(etProductionDate.getText().toString().trim());
        if (initial == null) {
            LocalDate today = LocalDate.now();
            initial = today.isAfter(created) ? created : today;
        }
        long initialMillis = toEpochMillis(initial);

        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select production date (optional)")
                .setSelection(initialMillis)
                .setTextInputFormat(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()))
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            LocalDate selected = fromEpochMillis(selection);
            if (selected.isAfter(createdFinal)) {
                new AlertDialog.Builder(this)
                        .setMessage("Production date cannot be after added date. Please select again.")
                        .setPositiveButton("OK", null)
                        .show();
                return;
            }
            etProductionDate.setText(selected.toString());
        });

        LocalDate production = parseDateOrNull(etProductionDate.getText().toString().trim());
        LocalDate outlineDate = (production != null) ? production : created;
        showWithCalendarMarkers(picker, "production_date_picker", outlineDate, null, null);
    }

    private void showExpireDatePicker() {
        LocalDate created = parseDateOrNull(etCreatedDate.getText().toString().trim());
        if (created == null) created = LocalDate.now();

        LocalDate expire = parseDateOrNull(etExpireDate.getText().toString().trim());
        if (expire == null) {
            LocalDate today = LocalDate.now();
            expire = today.isBefore(created) ? created : today;
        } else if (expire.isBefore(created)) {
            expire = created;
        }

        long createdMillis = toEpochMillis(created);
        long expireMillis = toEpochMillis(expire);

        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setStart(createdMillis)
                .setValidator(DateValidatorPointForward.from(createdMillis))
                .build();

        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select expiry date")
                .setSelection(expireMillis)
                .setCalendarConstraints(constraints)
                .setTextInputFormat(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()))
                .build();

        LocalDate production = parseDateOrNull(etProductionDate.getText().toString().trim());
        LocalDate outlineDate = (production != null) ? production : created;

        LocalDate recommendDate = null;
        String legendText = null;
        if (!selectedTags.isEmpty()) {
            String tag = selectedTags.get(0);
            int recDays = ExpirySettingsActivity.getRecommendedDays(this, tag, selectedStorageState);
            recommendDate = created.plusDays(recDays);
            if (recommendDate.isBefore(created)) recommendDate = created;
            legendText = "Suggested expiry date · " + recDays
                    + (recDays == 1 ? " day" : " days") + " (Expiry Settings)";
        }

        picker.addOnPositiveButtonClickListener(selection -> {
            if (selection == null) return;
            LocalDate selectedExpire = fromEpochMillis(selection);
            etExpireDate.setText(selectedExpire.toString());
        });

        showWithCalendarMarkers(picker, "expire_date_picker", outlineDate, recommendDate, legendText);
    }

    private static LocalDate parseDateOrNull(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(text.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static long toEpochMillis(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    private static LocalDate fromEpochMillis(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate();
    }

    private void showWithCalendarMarkers(MaterialDatePicker<Long> picker,
                                        String fragmentTag,
                                        LocalDate outlineDate,
                                        LocalDate recommendDate,
                                        String legendText) {

        Long outlineMillis = outlineDate != null ? toEpochMillis(outlineDate) : null;
        Long recommendMillis = recommendDate != null ? toEpochMillis(recommendDate) : null;

        FragmentManager fm = getSupportFragmentManager();
        FragmentManager.FragmentLifecycleCallbacks cb = new FragmentManager.FragmentLifecycleCallbacks() {
            @Override
            public void onFragmentViewCreated(FragmentManager fm, Fragment f, View v, Bundle savedInstanceState) {
                if (f != picker) return;

                View months = v.findViewById(com.google.android.material.R.id.mtrl_calendar_months);
                if (months instanceof RecyclerView) {
                    RecyclerView rv = (RecyclerView) months;
                    rv.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                            applyCalendarMarkers(v, outlineMillis, recommendMillis));
                    rv.post(() -> applyCalendarMarkers(v, outlineMillis, recommendMillis));
                } else {
                    v.post(() -> applyCalendarMarkers(v, outlineMillis, recommendMillis));
                }

                if (recommendMillis != null && legendText != null) {
                    injectCalendarLegend(v, months, legendText);
                }

                setupYearSelectionMonitor(v);

                fm.unregisterFragmentLifecycleCallbacks(this);
            }
        };

        fm.registerFragmentLifecycleCallbacks(cb, false);
        picker.show(fm, fragmentTag);
    }

    private void applyCalendarMarkers(View root, Long outlineMillis, Long recommendMillis) {
        ArrayList<GridView> grids = new ArrayList<>();
        collectCalendarGrids(root, grids);
        if (grids.isEmpty()) return;

        for (GridView grid : grids) {
            ListAdapter adapter = grid.getAdapter();
            if (adapter == null) continue;

            int childCount = grid.getChildCount();
            for (int childIndex = 0; childIndex < childCount; childIndex++) {
                View child = grid.getChildAt(childIndex);
                if (!(child instanceof TextView)) continue;

                int adapterIndex = grid.getFirstVisiblePosition() + childIndex;
                if (adapterIndex < 0 || adapterIndex >= adapter.getCount()) continue;

                Object item = adapter.getItem(adapterIndex);
                if (!(item instanceof Long)) {
                    child.getOverlay().clear();
                    continue;
                }

                Long dayMillis = (Long) item;
                child.getOverlay().clear();

                Drawable marker = null;
                if (recommendMillis != null && recommendMillis.equals(dayMillis)) {
                    marker = createRecommendSquareDrawable();
                } else if (outlineMillis != null && outlineMillis.equals(dayMillis)) {
                    marker = createOutlineCircleDrawable();
                }

                if (marker != null) {
                    int w = child.getWidth();
                    int h = child.getHeight();
                    if (w > 0 && h > 0) {
                        int desired = dpToPx(28);
                        int size = Math.min(desired, Math.min(w, h));
                        int left = (w - size) / 2;
                        int top = (h - size) / 2;
                        marker.setBounds(left, top, left + size, top + size);
                        child.getOverlay().add(marker);
                    }
                }
            }
        }
    }

    private void collectCalendarGrids(View view, ArrayList<GridView> out) {
        if (view == null) return;
        if (view instanceof GridView && view.getClass().getName().endsWith("MaterialCalendarGridView")) {
            out.add((GridView) view);
            return;
        }
        if (!(view instanceof android.view.ViewGroup)) return;
        android.view.ViewGroup vg = (android.view.ViewGroup) view;
        for (int i = 0; i < vg.getChildCount(); i++) {
            collectCalendarGrids(vg.getChildAt(i), out);
        }
    }

    // ── Year-then-Month quick selection ──────────────────────────────────────

    private void setupYearSelectionMonitor(View pickerView) {
        boolean[] wasInYearMode = {false};
        pickerView.getViewTreeObserver().addOnGlobalLayoutListener(
                new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        if (!pickerView.isAttachedToWindow()) {
                            pickerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            return;
                        }
                        View yearFrame = pickerView.findViewById(
                                com.google.android.material.R.id.mtrl_calendar_year_selector_frame);
                        boolean inYearMode = yearFrame != null
                                && yearFrame.getVisibility() == View.VISIBLE;
                        if (wasInYearMode[0] && !inYearMode) {
                            pickerView.post(() -> showMonthPickerDialog(pickerView));
                        }
                        wasInYearMode[0] = inYearMode;
                    }
                });
    }

    private void showMonthPickerDialog(View pickerView) {
        int currentYear = getCurrentPickerYear(pickerView);
        if (currentYear < 0) return;

        final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

        // Root card — matches Material date-picker surface colour & corner radius
        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setPadding(dpToPx(8), dpToPx(20), dpToPx(8), dpToPx(20));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xFFECE6F0);
        cardBg.setCornerRadius(dpToPx(28));
        root.setBackground(cardBg);

        // Year label — same uppercase-small style as the year grid header
        TextView yearTv = new TextView(this);
        yearTv.setText(String.valueOf(currentYear));
        yearTv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
        yearTv.setTypeface(null, android.graphics.Typeface.BOLD);
        yearTv.setTextColor(0xFF49454F);
        yearTv.setLetterSpacing(0.06f);
        yearTv.setGravity(Gravity.CENTER);
        yearTv.setPadding(0, 0, 0, dpToPx(10));
        root.addView(yearTv);

        AlertDialog[] ref = {null};
        int thisMonth = LocalDate.now().getMonthValue();
        int thisYear  = LocalDate.now().getYear();

        // Pill dimensions — proportional to how the year highlight looks
        int pillW = dpToPx(80);
        int pillH = dpToPx(38);

        for (int row = 0; row < 4; row++) {
            android.widget.LinearLayout rowLayout = new android.widget.LinearLayout(this);
            rowLayout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            rowLayout.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(52)));

            for (int col = 0; col < 3; col++) {
                int monthIndex = row * 3 + col;
                final int month = monthIndex + 1;

                // FrameLayout cell so the pill centres without stretching
                android.widget.FrameLayout cell = new android.widget.FrameLayout(this);
                cell.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                        0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1f));

                TextView tv = new TextView(this);
                tv.setText(MONTHS[monthIndex]);
                tv.setGravity(Gravity.CENTER);
                tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);

                android.widget.FrameLayout.LayoutParams tvLp =
                        new android.widget.FrameLayout.LayoutParams(pillW, pillH);
                tvLp.gravity = Gravity.CENTER;
                tv.setLayoutParams(tvLp);

                boolean isToday = (month == thisMonth && currentYear == thisYear);
                if (isToday) {
                    tv.setTextColor(0xFFFFFFFF);
                    GradientDrawable pill = new GradientDrawable();
                    pill.setColor(0xFFE58332);
                    pill.setCornerRadius(dpToPx(19)); // fully rounded pill
                    tv.setBackground(pill);
                } else {
                    tv.setTextColor(0xFF1C1B1F);
                }

                cell.addView(tv);
                cell.setOnClickListener(v -> {
                    if (ref[0] != null) ref[0].dismiss();
                    navigatePickerToMonth(pickerView, currentYear, month);
                });
                rowLayout.addView(cell);
            }
            root.addView(rowLayout);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(root)
                .create();
        // Transparent window so only our rounded card is visible
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        ref[0] = dialog;
        dialog.show();
        // Fix dialog width to align with the date-picker card width
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(dpToPx(320),
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private void navigatePickerToMonth(View pickerView, int targetYear, int targetMonth) {
        try {
            int[] current = getCurrentPickerYearMonth(pickerView);
            if (current == null) return;
            int diff = (targetYear - current[0]) * 12 + (targetMonth - current[1]);
            if (diff == 0) return;
            int navId = diff > 0
                    ? com.google.android.material.R.id.month_navigation_next
                    : com.google.android.material.R.id.month_navigation_previous;
            View btn = pickerView.findViewById(navId);
            if (btn == null) return;
            int steps = Math.abs(diff);
            for (int i = 0; i < steps; i++) {
                btn.performClick();
            }
        } catch (Exception ignored) {}
    }

    private int getCurrentPickerYear(View pickerView) {
        int[] ym = getCurrentPickerYearMonth(pickerView);
        return ym != null ? ym[0] : -1;
    }

    private int[] getCurrentPickerYearMonth(View pickerView) {
        try {
            View toggle = pickerView.findViewById(
                    com.google.android.material.R.id.month_navigation_fragment_toggle);
            if (!(toggle instanceof TextView)) return null;
            String text = ((TextView) toggle).getText().toString().trim();
            for (String pattern : new String[]{"MMMM yyyy", "MMM yyyy"}) {
                try {
                    java.text.SimpleDateFormat sdf =
                            new java.text.SimpleDateFormat(pattern, Locale.ENGLISH);
                    java.util.Date d = sdf.parse(text);
                    if (d != null) {
                        java.util.Calendar cal = java.util.Calendar.getInstance();
                        cal.setTime(d);
                        return new int[]{cal.get(java.util.Calendar.YEAR),
                                cal.get(java.util.Calendar.MONTH) + 1};
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Drawable createOutlineCircleDrawable() {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(Color.TRANSPARENT);
        shape.setStroke(dpToPx(2), 0xFF9E9E9E);
        return shape;
    }

    private Drawable createRecommendSquareDrawable() {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(0x33E58332);
        shape.setStroke(dpToPx(2), 0xFFE58332);
        return shape;
    }

    private void injectCalendarLegend(View dialogRoot, View monthsView, String legendText) {
        try {
            // Don't inject twice
            if (dialogRoot.findViewWithTag("recommend_legend") != null) return;

            // Find the action button row by locating cancel or confirm button
            View cancelBtn  = dialogRoot.findViewById(com.google.android.material.R.id.cancel_button);
            View confirmBtn = dialogRoot.findViewById(com.google.android.material.R.id.confirm_button);
            View actionAnchor = cancelBtn != null ? cancelBtn : confirmBtn;

            // The action row is the parent of one of those buttons
            android.view.ViewGroup actionRow = actionAnchor != null
                    && actionAnchor.getParent() instanceof android.view.ViewGroup
                    ? (android.view.ViewGroup) actionAnchor.getParent() : null;

            // The container that holds both the calendar and the action row
            android.view.ViewGroup container = actionRow != null
                    && actionRow.getParent() instanceof android.view.ViewGroup
                    ? (android.view.ViewGroup) actionRow.getParent() : null;

            if (container == null) return;

            // Build legend row
            android.widget.LinearLayout row = new android.widget.LinearLayout(this);
            row.setTag("recommend_legend");
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(dpToPx(24), dpToPx(8), dpToPx(24), dpToPx(4));

            android.view.View dot = new android.view.View(this);
            GradientDrawable dotShape = new GradientDrawable();
            dotShape.setShape(GradientDrawable.OVAL);
            dotShape.setColor(0x33E58332);
            dotShape.setStroke(dpToPx(2), 0xFFE58332);
            dot.setBackground(dotShape);
            int dotSize = dpToPx(12);
            android.widget.LinearLayout.LayoutParams dotLp =
                    new android.widget.LinearLayout.LayoutParams(dotSize, dotSize);
            dotLp.setMarginEnd(dpToPx(8));
            dot.setLayoutParams(dotLp);
            row.addView(dot);

            android.widget.TextView label = new android.widget.TextView(this);
            label.setText(legendText);
            label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
            label.setTextColor(0xFF8A7B6E);
            row.addView(label);

            // Insert immediately before the action row
            int insertAt = container.getChildCount(); // fallback: append
            for (int i = 0; i < container.getChildCount(); i++) {
                if (container.getChildAt(i) == actionRow) {
                    insertAt = i;
                    break;
                }
            }
            container.addView(row, insertAt);
        } catch (Exception ignored) {}
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void showImagePicker() {
        String[] options = new String[]{"Take photo", "Choose from gallery", "Cancel"};
        new AlertDialog.Builder(this)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        takePhoto();
                    } else if (which == 1) {
                        pickFromGallery();
                    } else {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    private void pickFromGallery() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQ_PICK_IMAGE);
    }

    private void takePhoto() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA_PERMISSION);
            return;
        }

        Uri uri = createCameraOutputUri();
        if (uri == null) {
            Toast.makeText(this, "Unable to open camera", Toast.LENGTH_SHORT).show();
            return;
        }
        pendingImageUri = uri;

        Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(intent, REQ_TAKE_PHOTO);
    }

    private Uri createCameraOutputUri() {
        try {
            File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (dir == null) dir = getFilesDir();
            File file = File.createTempFile("product_", ".jpg", dir);
            return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        } catch (Exception e) {
            return null;
        }
    }

    private void bindTags() {
        tagFruit = findViewById(R.id.tag_fruit);
        tagVegetable = findViewById(R.id.tag_vegetable);
        tagMeat = findViewById(R.id.tag_meat);
        tagSeafood = findViewById(R.id.tag_seafood);
        tagDairy = findViewById(R.id.tag_dairy);
        tagBakery = findViewById(R.id.tag_bakery);
        tagSnack = findViewById(R.id.tag_snack);
        tagFrozen = findViewById(R.id.tag_frozen);
        tagDrink = findViewById(R.id.tag_drink);
        tagHealthy = findViewById(R.id.tag_healthy);
        tagSweet = findViewById(R.id.tag_sweet);
        tagSpicy = findViewById(R.id.tag_spicy);
    }

    private void bindStorageAndOpened() {
        storageRoom = findViewById(R.id.storage_room);
        storageFridge = findViewById(R.id.storage_fridge);
        storageFreezer = findViewById(R.id.storage_freezer);

        openedYes = findViewById(R.id.opened_yes);
        openedNo = findViewById(R.id.opened_no);

        sharedYes = findViewById(R.id.shared_yes);
        sharedNo  = findViewById(R.id.shared_no);
    }

    private void refreshSharedUi() {
        applySharedToggle(sharedYes, selectedShared);
        applySharedToggle(sharedNo, !selectedShared);
    }

    private void applySharedToggle(TextView btn, boolean active) {
        if (btn == null) return;
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(active ? 0xFFFFBE82 : 0xFFFFDAB8);
        bg.setCornerRadius(getResources().getDisplayMetrics().density * 20);
        btn.setBackground(bg);
    }

    private void setupTagClicks() {
        setupTag(tagFruit, "Fruit");
        setupTag(tagVegetable, "Vegetable");
        setupTag(tagMeat, "Meat");
        setupTag(tagSeafood, "Seafood");
        setupTag(tagDairy, "Dairy");
        setupTag(tagBakery, "Bakery");
        setupTag(tagSnack, "Snack");
        setupTag(tagFrozen, "Frozen");
        setupTag(tagDrink, "Drink");
        setupTag(tagHealthy, "Healthy");
        setupTag(tagSweet, "Sweet");
        setupTag(tagSpicy, "Spicy");
    }

    private void setupStorageAndOpenedClicks() {
        if (storageRoom != null) storageRoom.setOnClickListener(v -> {
            selectedStorageState = isStorageState(selectedStorageState, STORAGE_ROOM, LEGACY_STORAGE_ROOM) ? null : STORAGE_ROOM;
            refreshStorageUi();
        });
        if (storageFridge != null) storageFridge.setOnClickListener(v -> {
            selectedStorageState = isStorageState(selectedStorageState, STORAGE_CHILLED, LEGACY_STORAGE_CHILLED) ? null : STORAGE_CHILLED;
            refreshStorageUi();
        });
        if (storageFreezer != null) storageFreezer.setOnClickListener(v -> {
            selectedStorageState = isStorageState(selectedStorageState, STORAGE_FROZEN, LEGACY_STORAGE_FROZEN) ? null : STORAGE_FROZEN;
            refreshStorageUi();
        });

        if (openedYes != null) openedYes.setOnClickListener(v -> {
            selectedOpened = (selectedOpened != null && selectedOpened) ? null : Boolean.TRUE;
            refreshOpenedUi();
        });
        if (openedNo != null) openedNo.setOnClickListener(v -> {
            selectedOpened = (selectedOpened != null && !selectedOpened) ? null : Boolean.FALSE;
            refreshOpenedUi();
        });

        if (sharedYes != null) sharedYes.setOnClickListener(v -> {
            if (!ProductRepository.isCurrentUserInFamily(this)) {
                Toast.makeText(this, "Create or join a family to share items", Toast.LENGTH_SHORT).show();
                selectedShared = false;
                refreshSharedUi();
                return;
            }
            selectedShared = true;
            refreshSharedUi();
        });
        if (sharedNo  != null) sharedNo .setOnClickListener(v -> { selectedShared = false; refreshSharedUi(); });
    }

    private void setupTag(TextView tagView, String tagName) {
        tagView.setOnClickListener(v -> {
            selectOnly(tagName);
            if (pendingExpireDatePicker && !selectedTags.isEmpty()) {
                pendingExpireDatePicker = false;
                showExpireDatePicker();
            }
        });
    }

    private void refreshStorageUi() {
        if (storageRoom != null) storageRoom.setSelected(isStorageState(selectedStorageState, STORAGE_ROOM, LEGACY_STORAGE_ROOM));
        if (storageFridge != null) storageFridge.setSelected(isStorageState(selectedStorageState, STORAGE_CHILLED, LEGACY_STORAGE_CHILLED));
        if (storageFreezer != null) storageFreezer.setSelected(isStorageState(selectedStorageState, STORAGE_FROZEN, LEGACY_STORAGE_FROZEN));
    }

    private boolean isStorageState(String value, String expected, String legacy) {
        return expected.equals(value) || legacy.equals(value);
    }

    private void refreshOpenedUi() {
        if (openedYes != null) openedYes.setSelected(Boolean.TRUE.equals(selectedOpened));
        if (openedNo != null) openedNo.setSelected(Boolean.FALSE.equals(selectedOpened));
    }

    private void fillOldData() {
        etName.setText(oldProduct.name);
        etQuantity.setText(String.valueOf(oldProduct.quantity));
        etPrice.setText(String.valueOf(oldProduct.price));
        etCreatedDate.setText(oldProduct.createdDate.toString());
        if (oldProduct.productionDate != null && etProductionDate != null) {
            etProductionDate.setText(oldProduct.productionDate.toString());
        }
        etExpireDate.setText(oldProduct.expireDate.toString());

        savedImageUri = oldProduct.imageUri;
        if (ivProduct != null) {
            if (savedImageUri != null) {
                try {
                    ivProduct.setImageURI(Uri.parse(savedImageUri));
                } catch (Exception e) {
                    ivProduct.setImageResource(R.drawable.profile_placeholder);
                }
            } else {
                ivProduct.setImageResource(R.drawable.profile_placeholder);
            }
        }

        if (oldProduct.tags != null) {
            selectedTags.clear();
            if (!oldProduct.tags.isEmpty()) {
                selectedTags.add(oldProduct.tags.get(0));
            }
        }

        refreshTagUI();

        selectedStorageState = oldProduct.storageState;
        selectedOpened = oldProduct.isOpened;
        selectedShared = oldProduct.isShared;
        refreshStorageUi();
        refreshOpenedUi();
        refreshSharedUi();
    }

    private void refreshTagUI() {
        if (selectedTags.isEmpty()) {
            tagFruit.setSelected(false);
            tagVegetable.setSelected(false);
            tagMeat.setSelected(false);
            tagSeafood.setSelected(false);
            tagDairy.setSelected(false);
            tagBakery.setSelected(false);
            tagSnack.setSelected(false);
            tagFrozen.setSelected(false);
            tagDrink.setSelected(false);
            tagHealthy.setSelected(false);
            tagSweet.setSelected(false);
            tagSpicy.setSelected(false);
            return;
        }

        String tagName = selectedTags.get(0);
        tagFruit.setSelected("Fruit".equals(tagName));
        tagVegetable.setSelected("Vegetable".equals(tagName));
        tagMeat.setSelected("Meat".equals(tagName));
        tagSeafood.setSelected("Seafood".equals(tagName));
        tagDairy.setSelected("Dairy".equals(tagName));
        tagBakery.setSelected("Bakery".equals(tagName));
        tagSnack.setSelected("Snack".equals(tagName));
        tagFrozen.setSelected("Frozen".equals(tagName));
        tagDrink.setSelected("Drink".equals(tagName));
        tagHealthy.setSelected("Healthy".equals(tagName));
        tagSweet.setSelected("Sweet".equals(tagName));
        tagSpicy.setSelected("Spicy".equals(tagName));
    }

    private void selectOnly(String tagName) {
        selectedTags.clear();
        selectedTags.add(tagName);
        refreshTagUI();
    }

    private void saveEditedProduct() {
        String name = etName.getText().toString().trim();

        if (name.isEmpty()) {
            etName.setError("Product name is required");
            return;
        }

        int quantity = 1;
        if (!etQuantity.getText().toString().trim().isEmpty()) {
            try {
                quantity = Integer.parseInt(etQuantity.getText().toString().trim());
            } catch (Exception e) {
                etQuantity.setError("Invalid quantity");
                return;
            }
        }

        double price = 0;
        if (!etPrice.getText().toString().trim().isEmpty()) {
            try {
                price = Double.parseDouble(etPrice.getText().toString().trim());
            } catch (Exception e) {
                etPrice.setError("Invalid price");
                return;
            }
        }

        LocalDate created;
        if (etCreatedDate.getText().toString().trim().isEmpty()) {
            created = LocalDate.now();
        } else {
            try {
                created = LocalDate.parse(etCreatedDate.getText().toString().trim());
            } catch (Exception e) {
                etCreatedDate.setError("Format: yyyy-MM-dd");
                return;
            }
        }

        LocalDate production = null;
        if (etProductionDate != null && !etProductionDate.getText().toString().trim().isEmpty()) {
            try {
                production = LocalDate.parse(etProductionDate.getText().toString().trim());
            } catch (Exception e) {
                etProductionDate.setError("Format: yyyy-MM-dd");
                return;
            }
        }

        if (selectedTags.isEmpty()) {
            selectedTags.add("Fruit");
        }

        LocalDate expire;
        if (etExpireDate.getText().toString().trim().isEmpty()) {
            etExpireDate.setError("Expire date is required");
            return;
        }
        try {
            expire = LocalDate.parse(etExpireDate.getText().toString().trim());
        } catch (Exception e) {
            etExpireDate.setError("Format: yyyy-MM-dd");
            return;
        }

        Product updatedProduct = new Product(
                name,
                quantity,
                price,
                created,
                production,
                expire,
                savedImageUri,
                new ArrayList<>(selectedTags)
        );
        updatedProduct.storageState = selectedStorageState;
        updatedProduct.isOpened = selectedOpened;
        updatedProduct.isShared = selectedShared;
        if (selectedShared) {
            updatedProduct.sharedByUser = (oldProduct.sharedByUser != null)
                    ? oldProduct.sharedByUser
                    : ProductRepository.getCurrentUser(this);
        }

        updatedProduct.isArchived = oldProduct.isArchived;
        updatedProduct.isSelected = false;

        if (ownerUser != null) {
            // Save back to the owner's Room DB row using the entity's primary key
            updatedProduct.entityId = oldProduct.entityId;
            ProductRepository.saveSharedProductEdit(this, updatedProduct, ownerUser);
        } else {
            ProductRepository.products.set(productIndex, updatedProduct);
            ProductRepository.saveCurrentUserProducts(this);
        }

        finish();
    }

    private int getDefaultExpireDays(String tag) {
        switch (tag) {
            case "Fruit":
                return 3;
            case "Vegetable":
                return 3;
            case "Meat":
                return 1;
            case "Seafood":
                return 1;
            case "Dairy":
                return 2;
            case "Bakery":
                return 1;
            case "Snack":
                return 30;
            case "Frozen":
                return 30;
            case "Drink":
                return 30;
            case "Healthy":
                return 90;
            case "Sweet":
                return 30;
            case "Spicy":
                return 60;
            default:
                return 7;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK) return;

        if (requestCode == REQ_PICK_IMAGE) {
            if (data == null) return;
            Uri uri = data.getData();
            if (uri == null) return;
            try {
                getContentResolver().takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                );
            } catch (Exception ignored) {
            }
            savedImageUri = uri.toString();
            if (ivProduct != null) {
                try {
                    ivProduct.setImageURI(uri);
                } catch (Exception ignored) {
                }
            }
        } else if (requestCode == REQ_TAKE_PHOTO) {
            if (pendingImageUri == null) return;
            savedImageUri = pendingImageUri.toString();
            if (ivProduct != null) {
                try {
                    ivProduct.setImageURI(pendingImageUri);
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_CAMERA_PERMISSION) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            takePhoto();
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
        }
    }
}
