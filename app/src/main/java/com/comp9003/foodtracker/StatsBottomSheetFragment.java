package com.comp9003.foodtracker;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.*;
import com.comp9003.foodtracker.model.Product;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.time.LocalDate;
import java.util.*;

public class StatsBottomSheetFragment extends BottomSheetDialogFragment {

    private static final String[] TAG_ORDER = {
            "Fruit", "Vegetable", "Meat", "Seafood", "Dairy",
            "Bakery", "Snack", "Frozen", "Drink", "Healthy", "Sweet", "Spicy"
    };

    private static final int[] BAR_COLORS = {
            0xFFE58332, 0xFF4CAF50, 0xFFE53935, 0xFF1565C0, 0xFFAB47BC,
            0xFFFF7043, 0xFFFFA000, 0xFF00ACC1, 0xFF26A69A, 0xFF66BB6A,
            0xFFEC407A, 0xFFEF5350
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.bottom_sheet_stats, container, false);

        view.findViewById(R.id.btn_close_stats).setOnClickListener(v -> dismiss());

        // ── Gather stats ──────────────────────────────────────────────
        List<Product> all = ProductRepository.products;
        int active = 0, archived = 0, nearExpiry = 0, expired = 0;
        int addedThisMonth = 0, archivedThisMonth = 0;

        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);

        Map<String, Integer> tagCount = new LinkedHashMap<>();
        for (String t : TAG_ORDER) tagCount.put(t, 0);

        for (Product p : all) {
            if (p == null) continue;

            if (p.isArchived) {
                archived++;
                // Treat "archived this month" as items created this month that are archived
                if (p.createdDate != null && !p.createdDate.isBefore(monthStart)) {
                    archivedThisMonth++;
                }
            } else {
                active++;
                if (p.expireDate != null) {
                    int days = p.getDaysToExpire();
                    if (days < 0) {
                        expired++;
                    } else if (days <= 7) {
                        nearExpiry++;
                    }
                }
            }

            if (p.createdDate != null && !p.createdDate.isBefore(monthStart)) {
                addedThisMonth++;
            }

            // Count primary tag
            if (p.tags != null && !p.tags.isEmpty()) {
                String tag = p.tags.get(0);
                if (tagCount.containsKey(tag)) {
                    tagCount.put(tag, tagCount.get(tag) + 1);
                }
            }
        }

        // ── Overview ──────────────────────────────────────────────────
        ((TextView) view.findViewById(R.id.tv_stat_active)).setText(String.valueOf(active));
        ((TextView) view.findViewById(R.id.tv_stat_archived)).setText(String.valueOf(archived));
        ((TextView) view.findViewById(R.id.tv_stat_near)).setText(String.valueOf(nearExpiry));
        ((TextView) view.findViewById(R.id.tv_stat_expired)).setText(String.valueOf(expired));
        ((TextView) view.findViewById(R.id.tv_stat_added_month)).setText(String.valueOf(addedThisMonth));
        ((TextView) view.findViewById(R.id.tv_stat_archived_month)).setText(String.valueOf(archivedThisMonth));

        // ── Category bars ──────────────────────────────────────────────
        LinearLayout barsContainer = view.findViewById(R.id.ll_category_bars);
        int maxCount = 1;
        for (int v2 : tagCount.values()) maxCount = Math.max(maxCount, v2);

        int colorIdx = 0;
        for (Map.Entry<String, Integer> entry : tagCount.entrySet()) {
            if (entry.getValue() == 0) { colorIdx++; continue; }
            barsContainer.addView(buildCategoryRow(entry.getKey(), entry.getValue(),
                    maxCount, BAR_COLORS[colorIdx % BAR_COLORS.length]));
            colorIdx++;
        }

        if (barsContainer.getChildCount() == 0) {
            TextView empty = new TextView(getContext());
            empty.setText("No items yet");
            empty.setTextColor(0xFF8A7B6E);
            empty.setTextSize(13);
            barsContainer.addView(empty);
        }

        return view;
    }

    private View buildCategoryRow(String label, int count, int maxCount, int color) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, 0, 0, dpToPx(8));
        row.setLayoutParams(rowLp);

        // Label
        TextView tvLabel = new TextView(getContext());
        tvLabel.setText(label);
        tvLabel.setTextColor(0xFF514438);
        tvLabel.setTextSize(13);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(dpToPx(72),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tvLabel.setLayoutParams(labelLp);
        row.addView(tvLabel);

        // Bar background
        LinearLayout barBg = new LinearLayout(getContext());
        barBg.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable bgDrawable = new GradientDrawable();
        bgDrawable.setShape(GradientDrawable.RECTANGLE);
        bgDrawable.setColor(0xFFEDE5DC);
        bgDrawable.setCornerRadius(dpToPx(6));
        barBg.setBackground(bgDrawable);
        LinearLayout.LayoutParams bgLp = new LinearLayout.LayoutParams(
                0, dpToPx(16), 1f);
        barBg.setLayoutParams(bgLp);

        // Bar fill
        View barFill = new View(getContext());
        GradientDrawable fillDrawable = new GradientDrawable();
        fillDrawable.setShape(GradientDrawable.RECTANGLE);
        fillDrawable.setColor(color);
        fillDrawable.setCornerRadius(dpToPx(6));
        barFill.setBackground(fillDrawable);
        float ratio = (float) count / maxCount;
        LinearLayout.LayoutParams fillLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, ratio);
        barFill.setLayoutParams(fillLp);

        // Spacer
        View spacer = new View(getContext());
        LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f - ratio);
        spacer.setLayoutParams(spacerLp);

        barBg.addView(barFill);
        barBg.addView(spacer);
        row.addView(barBg);

        // Count label
        TextView tvCount = new TextView(getContext());
        tvCount.setText(String.valueOf(count));
        tvCount.setTextColor(0xFF514438);
        tvCount.setTextSize(13);
        tvCount.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_END);
        LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(dpToPx(28),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        countLp.setMarginStart(dpToPx(6));
        tvCount.setLayoutParams(countLp);
        row.addView(tvCount);

        return row;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog == null) return;
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        if (dialog instanceof BottomSheetDialog) {
            android.widget.FrameLayout bottomSheet = ((BottomSheetDialog) dialog)
                    .findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundColor(Color.TRANSPARENT);
            }
        }
    }
}
