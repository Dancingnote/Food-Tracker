package com.comp9003.foodtracker;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.ImageView;
import android.widget.RadioButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class SortBottomSheetFragment extends BottomSheetDialogFragment {

    public interface OnSortSelectedListener {
        void onSortSelected(String type, boolean isAsc);
    }

    private OnSortSelectedListener listener;

    public void setListener(OnSortSelectedListener listener) {
        this.listener = listener;
    }

    // STATE
    private String selectedType = "recent";
    private boolean isAsc = true;

    // UI
    private RadioButton rbRecent, rbName, rbStock, rbExpire, rbNearExpire;
    private ImageView ivRecent, ivName, ivStock, ivExpire, ivNearExpire;

    public void setInit(String type, boolean asc) {
        selectedType = type;
        isAsc = asc;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.bottom_sheet_sort, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {

        bind(view);
        updateUI();

        view.findViewById(R.id.sort_recent).setOnClickListener(v -> select("recent"));
        view.findViewById(R.id.sort_name).setOnClickListener(v -> select("name"));
        view.findViewById(R.id.sort_stock).setOnClickListener(v -> select("stock"));
        view.findViewById(R.id.sort_expire).setOnClickListener(v -> select("expire"));
        view.findViewById(R.id.sort_near_expire).setOnClickListener(v -> select("near_expire"));

        if (rbRecent != null) rbRecent.setOnClickListener(v -> select("recent"));
        if (rbName != null) rbName.setOnClickListener(v -> select("name"));
        if (rbStock != null) rbStock.setOnClickListener(v -> select("stock"));
        if (rbExpire != null) rbExpire.setOnClickListener(v -> select("expire"));
        if (rbNearExpire != null) rbNearExpire.setOnClickListener(v -> select("near_expire"));

        view.findViewById(R.id.btn_close).setOnClickListener(v -> dismiss());
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

    private void bind(View view) {

        rbRecent = view.findViewById(R.id.rb_recent);
        rbName = view.findViewById(R.id.rb_name);
        rbStock = view.findViewById(R.id.rb_stock);
        rbExpire = view.findViewById(R.id.rb_expire);
        rbNearExpire = view.findViewById(R.id.rb_near_expire);

        ivRecent = view.findViewById(R.id.iv_order_recent);
        ivName = view.findViewById(R.id.iv_order_name);
        ivStock = view.findViewById(R.id.iv_order_stock);
        ivExpire = view.findViewById(R.id.iv_order_expire);
        ivNearExpire = view.findViewById(R.id.iv_order_near_expire);
    }

    private void select(String type) {

        if (selectedType.equals(type)) {
            isAsc = !isAsc;   // toggle
        } else {
            selectedType = type;
            isAsc = true;     // default asc
        }

        updateUI();

        if (listener != null) {
            listener.onSortSelected(selectedType, isAsc);
        }
    }

    private void updateUI() {

        reset();

        switch (selectedType) {

            case "recent":
                rbRecent.setChecked(true);
                showArrow(ivRecent);
                break;

            case "name":
                rbName.setChecked(true);
                showArrow(ivName);
                break;

            case "stock":
                rbStock.setChecked(true);
                showArrow(ivStock);
                break;

            case "expire":
                rbExpire.setChecked(true);
                showArrow(ivExpire);
                break;

            case "near_expire":
                rbNearExpire.setChecked(true);
                showArrow(ivNearExpire);
                break;
        }
    }

    private void showArrow(ImageView iv) {
        iv.setVisibility(View.VISIBLE);
        iv.setRotation(isAsc ? 0 : 180);
    }

    private void reset() {

        rbRecent.setChecked(false);
        rbName.setChecked(false);
        rbStock.setChecked(false);
        rbExpire.setChecked(false);
        rbNearExpire.setChecked(false);

        ivRecent.setVisibility(View.GONE);
        ivName.setVisibility(View.GONE);
        ivStock.setVisibility(View.GONE);
        ivExpire.setVisibility(View.GONE);
        ivNearExpire.setVisibility(View.GONE);
    }
}
