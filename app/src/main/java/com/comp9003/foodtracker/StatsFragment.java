package com.comp9003.foodtracker;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.*;
import androidx.fragment.app.Fragment;

import com.comp9003.foodtracker.model.Product;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class StatsFragment extends Fragment {

    private static final String[] TAG_ORDER = {
            "Fruit", "Vegetable", "Meat", "Seafood", "Dairy",
            "Bakery", "Snack", "Frozen", "Drink", "Healthy", "Sweet", "Spicy"
    };

    private static final int[] BAR_COLORS = {
            0xFFE58332, 0xFF4CAF50, 0xFFE53935, 0xFF1565C0, 0xFFAB47BC,
            0xFFFF7043, 0xFFFFA000, 0xFF00ACC1, 0xFF26A69A, 0xFF66BB6A,
            0xFFEC407A, 0xFFEF5350
    };

    private static final DateTimeFormatter MONTH_FMT =
            DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private LocalDate selectedMonth;
    private TextView tvMonthLabel;
    private TextView tvAddedMonth;
    private TextView tvArchivedMonth;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        selectedMonth = LocalDate.now().withDayOfMonth(1);

        // ── Compute & bind overview stats (all-time) ──────────────────────
        List<Product> all = ProductRepository.products;
        int active = 0, archived = 0, nearExpiry = 0, expired = 0;

        Map<String, Integer> tagCount = new LinkedHashMap<>();
        for (String t : TAG_ORDER) tagCount.put(t, 0);

        for (Product p : all) {
            if (p == null) continue;
            if (p.isArchived) {
                archived++;
            } else {
                active++;
                if (p.expireDate != null) {
                    int days = p.getDaysToExpire();
                    if (days < 0) expired++;
                    else if (days <= 7) nearExpiry++;
                }
            }
            if (p.tags != null && !p.tags.isEmpty()) {
                String tag = p.tags.get(0);
                if (tagCount.containsKey(tag))
                    tagCount.put(tag, tagCount.get(tag) + 1);
            }
        }

        setText(view, R.id.tv_stat_active,   active);
        setText(view, R.id.tv_stat_archived,  archived);
        setText(view, R.id.tv_stat_near,      nearExpiry);
        setText(view, R.id.tv_stat_expired,   expired);

        // ── Category bars ─────────────────────────────────────────────────
        LinearLayout barsContainer = view.findViewById(R.id.ll_category_bars);
        int maxCount = 1;
        for (int v : tagCount.values()) maxCount = Math.max(maxCount, v);

        int colorIdx = 0;
        for (Map.Entry<String, Integer> entry : tagCount.entrySet()) {
            if (entry.getValue() == 0) { colorIdx++; continue; }
            barsContainer.addView(buildCategoryRow(
                    entry.getKey(), entry.getValue(), maxCount,
                    BAR_COLORS[colorIdx % BAR_COLORS.length]));
            colorIdx++;
        }
        if (barsContainer.getChildCount() == 0) {
            TextView empty = new TextView(getContext());
            empty.setText("No items yet");
            empty.setTextColor(0xFF8A7B6E);
            empty.setTextSize(13);
            barsContainer.addView(empty);
        }

        // ── Month navigation ──────────────────────────────────────────────
        tvMonthLabel    = view.findViewById(R.id.tv_month_label);
        tvAddedMonth    = view.findViewById(R.id.tv_stat_added_month);
        tvArchivedMonth = view.findViewById(R.id.tv_stat_archived_month);

        view.findViewById(R.id.btn_month_prev).setOnClickListener(v -> {
            selectedMonth = selectedMonth.minusMonths(1);
            updateMonthSection();
        });
        view.findViewById(R.id.btn_month_next).setOnClickListener(v -> {
            selectedMonth = selectedMonth.plusMonths(1);
            updateMonthSection();
        });
        view.findViewById(R.id.tv_month_label).setOnClickListener(v -> showMonthPickerDialog());
        view.findViewById(R.id.ll_month_nav).setOnClickListener(v -> showMonthPickerDialog());

        updateMonthSection();
    }

    private void updateMonthSection() {
        if (tvMonthLabel == null) return;
        tvMonthLabel.setText(selectedMonth.format(MONTH_FMT));

        LocalDate monthEnd = selectedMonth.plusMonths(1);
        int added = 0, consumed = 0;

        for (Product p : ProductRepository.products) {
            if (p == null || p.createdDate == null) continue;
            boolean inMonth = !p.createdDate.isBefore(selectedMonth)
                           && p.createdDate.isBefore(monthEnd);
            if (!inMonth) continue;
            added++;
            if (p.isArchived) consumed++;
        }

        tvAddedMonth.setText(String.valueOf(added));
        tvArchivedMonth.setText(String.valueOf(consumed));
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private void setText(View root, int id, int value) {
        ((TextView) root.findViewById(id)).setText(String.valueOf(value));
    }

    private View buildCategoryRow(String label, int count, int maxCount, int color) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, 0, 0, dpToPx(10));
        row.setLayoutParams(rowLp);

        TextView tvLabel = new TextView(getContext());
        tvLabel.setText(label);
        tvLabel.setTextColor(0xFF514438);
        tvLabel.setTextSize(13);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(78),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        row.addView(tvLabel);

        LinearLayout barBg = new LinearLayout(getContext());
        barBg.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable bgDrawable = new GradientDrawable();
        bgDrawable.setColor(0xFFEDE5DC);
        bgDrawable.setCornerRadius(dpToPx(8));
        barBg.setBackground(bgDrawable);
        barBg.setLayoutParams(new LinearLayout.LayoutParams(0, dpToPx(18), 1f));

        View barFill = new View(getContext());
        GradientDrawable fillDrawable = new GradientDrawable();
        fillDrawable.setColor(color);
        fillDrawable.setCornerRadius(dpToPx(8));
        barFill.setBackground(fillDrawable);
        float ratio = (float) count / maxCount;
        barFill.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, ratio));

        View spacer = new View(getContext());
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f - ratio));

        barBg.addView(barFill);
        barBg.addView(spacer);
        row.addView(barBg);

        TextView tvCount = new TextView(getContext());
        tvCount.setText(String.valueOf(count));
        tvCount.setTextColor(0xFF514438);
        tvCount.setTextSize(13);
        tvCount.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_END);
        LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(
                dpToPx(28), LinearLayout.LayoutParams.WRAP_CONTENT);
        countLp.setMarginStart(dpToPx(8));
        tvCount.setLayoutParams(countLp);
        row.addView(tvCount);

        return row;
    }

    private void showMonthPickerDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());

        // Root container
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        int p16 = dpToPx(16);
        int p20 = dpToPx(20);
        root.setPadding(p20, p16, p20, p20);
        root.setBackgroundColor(0xFFFFF8F1);

        // Title
        TextView title = new TextView(requireContext());
        title.setText("Select Month");
        title.setTextSize(16);
        title.setTextColor(0xFF514438);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.setMargins(0, 0, 0, dpToPx(16));
        title.setLayoutParams(titleLp);
        root.addView(title);

        // Year navigation row
        final int[] pickerYear = {selectedMonth.getYear()};
        LinearLayout yearRow = new LinearLayout(requireContext());
        yearRow.setOrientation(LinearLayout.HORIZONTAL);
        yearRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams yearRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        yearRowLp.setMargins(0, 0, 0, dpToPx(16));
        yearRow.setLayoutParams(yearRowLp);

        TextView tvPrevYear = new TextView(requireContext());
        tvPrevYear.setText("‹");
        tvPrevYear.setTextSize(24);
        tvPrevYear.setTextColor(0xFF514438);
        tvPrevYear.setGravity(android.view.Gravity.CENTER);
        tvPrevYear.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(44), dpToPx(44)));
        tvPrevYear.setPadding(dpToPx(8), 0, dpToPx(8), 0);

        TextView tvYearLabel = new TextView(requireContext());
        tvYearLabel.setText(String.valueOf(pickerYear[0]));
        tvYearLabel.setTextSize(18);
        tvYearLabel.setTextColor(0xFF514438);
        tvYearLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        tvYearLabel.setGravity(android.view.Gravity.CENTER);
        tvYearLabel.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvNextYear = new TextView(requireContext());
        tvNextYear.setText("›");
        tvNextYear.setTextSize(24);
        tvNextYear.setTextColor(0xFF514438);
        tvNextYear.setGravity(android.view.Gravity.CENTER);
        tvNextYear.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(44), dpToPx(44)));
        tvNextYear.setPadding(dpToPx(8), 0, dpToPx(8), 0);

        yearRow.addView(tvPrevYear);
        yearRow.addView(tvYearLabel);
        yearRow.addView(tvNextYear);
        root.addView(yearRow);

        // Month grid (3 columns × 4 rows)
        String[] monthNames = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                               "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        GridLayout grid = new GridLayout(requireContext());
        grid.setColumnCount(3);
        grid.setRowCount(4);
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gridLp.setMargins(0, 0, 0, dpToPx(8));
        grid.setLayoutParams(gridLp);

        TextView[] monthCells = new TextView[12];
        for (int i = 0; i < 12; i++) {
            final int monthIdx = i + 1;
            TextView cell = new TextView(requireContext());
            cell.setText(monthNames[i]);
            cell.setTextSize(14);
            cell.setGravity(android.view.Gravity.CENTER);

            GridLayout.LayoutParams cellLp = new GridLayout.LayoutParams(
                    GridLayout.spec(i / 3, 1f), GridLayout.spec(i % 3, 1f));
            cellLp.width = 0;
            cellLp.height = dpToPx(48);
            cellLp.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
            cell.setLayoutParams(cellLp);

            monthCells[i] = cell;
            grid.addView(cell);

            cell.setOnClickListener(v -> {
                selectedMonth = LocalDate.of(pickerYear[0], monthIdx, 1);
                updateMonthSection();
                dialog.dismiss();
            });
        }

        root.addView(grid);

        // Helper to refresh cell highlight states
        Runnable refreshCells = () -> {
            for (int i = 0; i < 12; i++) {
                boolean isSelected = (pickerYear[0] == selectedMonth.getYear())
                        && (i + 1 == selectedMonth.getMonthValue());
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(dpToPx(10));
                if (isSelected) {
                    bg.setColor(0xFFE58332);
                    monthCells[i].setTextColor(0xFFFFFFFF);
                    monthCells[i].setTypeface(null, android.graphics.Typeface.BOLD);
                } else {
                    bg.setColor(0xFFEDE5DC);
                    monthCells[i].setTextColor(0xFF514438);
                    monthCells[i].setTypeface(null, android.graphics.Typeface.NORMAL);
                }
                monthCells[i].setBackground(bg);
            }
        };

        refreshCells.run();

        tvPrevYear.setOnClickListener(v -> {
            pickerYear[0]--;
            tvYearLabel.setText(String.valueOf(pickerYear[0]));
            refreshCells.run();
        });
        tvNextYear.setOnClickListener(v -> {
            pickerYear[0]++;
            tvYearLabel.setText(String.valueOf(pickerYear[0]));
            refreshCells.run();
        });

        dialog.setContentView(root);
        dialog.show();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
