package com.comp9003.foodtracker;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.*;
import androidx.annotation.*;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import java.util.*;

public class FilterBottomSheetFragment extends BottomSheetDialogFragment {

    public interface FilterListener {
        void onApply(Set<String> tags, int nearThreshold, int expiredThreshold,
                     Set<String> storages, Set<String> statuses,
                     Boolean openedFilter, Boolean sharedFilter, Boolean fromOtherFilter);
    }

    private FilterListener listener;

    public void setListener(FilterListener listener) {
        this.listener = listener;
    }

    // ================= STATE =================
    private Set<String> selectedTags     = new HashSet<>();
    private int selectedNear             = 0;     // 0 = Unlimited
    private int selectedExpired          = 0;     // 0 = Unlimited
    private Set<String> selectedStorages = new HashSet<>(); // "room"|"chilled"|"frozen"
    private Set<String> selectedStatuses = new HashSet<>(); // "fresh"|"near_expiry"|"expired"
    private Boolean selectedOpened       = null;  // null=All, true=Opened, false=Unopened
    private Boolean selectedShared       = null;  // null=All, true=Shared, false=Not shared
    private Boolean selectedFromOther    = null;  // null=no filter, true=from others only

    // ================= INIT =================
    public void setInit(Set<String> tags, int near, int expired,
                        Set<String> storages, Set<String> statuses,
                        Boolean openedFilter, Boolean sharedFilter, Boolean fromOtherFilter) {
        if (tags != null)     selectedTags = new HashSet<>(tags);
        selectedNear      = near;
        selectedExpired   = expired;
        if (storages != null) selectedStorages = new HashSet<>(storages);
        if (statuses != null) selectedStatuses = new HashSet<>(statuses);
        selectedOpened    = openedFilter;
        selectedShared    = sharedFilter;
        selectedFromOther = fromOtherFilter;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.bottom_sheet_filter, container, false);

        View btnClose = view.findViewById(R.id.btn_close);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss());
        }

        // TAG
        View tagAll = view.findViewById(R.id.tag_all);
        View tagFruit = view.findViewById(R.id.tag_fruit);
        View tagVegetable = view.findViewById(R.id.tag_vegetable);
        View tagMeat = view.findViewById(R.id.tag_meat);
        View tagSeafood = view.findViewById(R.id.tag_seafood);
        View tagDairy = view.findViewById(R.id.tag_dairy);
        View tagBakery = view.findViewById(R.id.tag_bakery);
        View tagSnack = view.findViewById(R.id.tag_snack);
        View tagFrozen = view.findViewById(R.id.tag_frozen);
        View tagDrink = view.findViewById(R.id.tag_drink);
        View tagHealthy = view.findViewById(R.id.tag_healthy);
        View tagSweet = view.findViewById(R.id.tag_sweet);
        View tagSpicy = view.findViewById(R.id.tag_spicy);

        // NEAR
        View nearAll = view.findViewById(R.id.near_exp_all);
        View near5 = view.findViewById(R.id.near_exp_5);
        View near10 = view.findViewById(R.id.near_exp_10);
        View near15 = view.findViewById(R.id.near_exp_15);
        View near30 = view.findViewById(R.id.near_exp_30);

        // EXPIRED
        View expAll = view.findViewById(R.id.exp_all);
        View exp5 = view.findViewById(R.id.exp_5);
        View exp10 = view.findViewById(R.id.exp_10);
        View exp15 = view.findViewById(R.id.exp_15);
        View exp30 = view.findViewById(R.id.exp_30);

        // STORAGE
        View storageAll    = view.findViewById(R.id.storage_all);
        View storageRoom   = view.findViewById(R.id.storage_room);
        View storageChilled= view.findViewById(R.id.storage_chilled);
        View storageFrozen = view.findViewById(R.id.storage_frozen);

        // FOOD STATUS
        View statusAll       = view.findViewById(R.id.status_all);
        View statusFresh     = view.findViewById(R.id.status_fresh);
        View statusNearExpiry= view.findViewById(R.id.status_near_expiry);
        View statusExpired   = view.findViewById(R.id.status_expired);

        // OPENED
        View openedAll = view.findViewById(R.id.opened_filter_all);
        View openedYes = view.findViewById(R.id.opened_filter_yes);
        View openedNo  = view.findViewById(R.id.opened_filter_no);

        // SHARED
        View sharedAll       = view.findViewById(R.id.shared_filter_all);
        View sharedYes       = view.findViewById(R.id.shared_filter_yes);
        View sharedNo        = view.findViewById(R.id.shared_filter_no);
        View sharedFromOther = view.findViewById(R.id.shared_filter_from_other);

        // INIT UI
        refreshTagUI(tagAll, tagFruit, tagVegetable, tagMeat,
                tagSeafood, tagDairy, tagBakery, tagSnack,
                tagFrozen, tagDrink, tagHealthy, tagSweet, tagSpicy);
        refreshNearUI(nearAll, near5, near10, near15, near30);
        refreshExpiredUI(expAll, exp5, exp10, exp15, exp30);
        refreshStorageUI(storageAll, storageRoom, storageChilled, storageFrozen);
        refreshStatusUI(statusAll, statusFresh, statusNearExpiry, statusExpired);
        refreshOpenedUI(openedAll, openedYes, openedNo);
        refreshSharedUI(sharedAll, sharedYes, sharedNo, sharedFromOther);

        // TAG CLICK
        tagAll.setOnClickListener(v -> {
            selectedTags.clear();
            refreshTagUI(tagAll, tagFruit, tagVegetable, tagMeat,
                    tagSeafood, tagDairy, tagBakery, tagSnack,
                    tagFrozen, tagDrink, tagHealthy, tagSweet, tagSpicy);
        });

        setTag(tagFruit, "Fruit",
                tagAll, tagFruit, tagVegetable, tagMeat,
                tagSeafood, tagDairy, tagBakery, tagSnack,
                tagFrozen, tagDrink, tagHealthy, tagSweet, tagSpicy);

        setTag(tagVegetable, "Vegetable",
                tagAll, tagFruit, tagVegetable, tagMeat,
                tagSeafood, tagDairy, tagBakery, tagSnack,
                tagFrozen, tagDrink, tagHealthy, tagSweet, tagSpicy);

        setTag(tagMeat, "Meat",
                tagAll, tagFruit, tagVegetable, tagMeat,
                tagSeafood, tagDairy, tagBakery, tagSnack,
                tagFrozen, tagDrink, tagHealthy, tagSweet, tagSpicy);
        setTag(tagSeafood, "Seafood",
                tagAll, tagFruit, tagVegetable, tagMeat,
                tagSeafood, tagDairy, tagBakery, tagSnack,
                tagFrozen, tagDrink, tagHealthy, tagSweet, tagSpicy);
        setTag(tagDairy, "Dairy",
                tagAll, tagFruit, tagVegetable, tagMeat,
                tagSeafood, tagDairy, tagBakery, tagSnack,
                tagFrozen, tagDrink, tagHealthy, tagSweet, tagSpicy);
        setTag(tagBakery, "Bakery",
                tagAll, tagFruit, tagVegetable, tagMeat,
                tagSeafood, tagDairy, tagBakery, tagSnack,
                tagFrozen, tagDrink, tagHealthy, tagSweet, tagSpicy);
        setTag(tagSnack, "Snack",
                tagAll, tagFruit, tagVegetable, tagMeat,
                tagSeafood, tagDairy, tagBakery, tagSnack,
                tagFrozen, tagDrink, tagHealthy, tagSweet, tagSpicy);
        setTag(tagFrozen, "Frozen",
                tagAll, tagFruit, tagVegetable, tagMeat,
                tagSeafood, tagDairy, tagBakery, tagSnack,
                tagFrozen, tagDrink, tagHealthy, tagSweet, tagSpicy);
        setTag(tagDrink, "Drink",
                tagAll, tagFruit, tagVegetable, tagMeat,
                tagSeafood, tagDairy, tagBakery, tagSnack,
                tagFrozen, tagDrink, tagHealthy, tagSweet, tagSpicy);
        setTag(tagHealthy, "Healthy",
                tagAll, tagFruit, tagVegetable, tagMeat,
                tagSeafood, tagDairy, tagBakery, tagSnack,
                tagFrozen, tagDrink, tagHealthy, tagSweet, tagSpicy);
        setTag(tagSweet, "Sweet",
                tagAll, tagFruit, tagVegetable, tagMeat,
                tagSeafood, tagDairy, tagBakery, tagSnack,
                tagFrozen, tagDrink, tagHealthy, tagSweet, tagSpicy);
        setTag(tagSpicy, "Spicy",
                tagAll, tagFruit, tagVegetable, tagMeat,
                tagSeafood, tagDairy, tagBakery, tagSnack,
                tagFrozen, tagDrink, tagHealthy, tagSweet, tagSpicy);

        // NEAR CLICK — single-select
        nearAll.setOnClickListener(v -> {
            selectedNear = 0;
            refreshNearUI(nearAll, near5, near10, near15, near30);
        });
        setNear(near5,  5,  nearAll, near5, near10, near15, near30);
        setNear(near10, 10, nearAll, near5, near10, near15, near30);
        setNear(near15, 15, nearAll, near5, near10, near15, near30);
        setNear(near30, 30, nearAll, near5, near10, near15, near30);

        // EXPIRED CLICK — single-select
        expAll.setOnClickListener(v -> {
            selectedExpired = 0;
            refreshExpiredUI(expAll, exp5, exp10, exp15, exp30);
        });
        setExpired(exp5,  5,  expAll, exp5, exp10, exp15, exp30);
        setExpired(exp10, 10, expAll, exp5, exp10, exp15, exp30);
        setExpired(exp15, 15, expAll, exp5, exp10, exp15, exp30);
        setExpired(exp30, 30, expAll, exp5, exp10, exp15, exp30);

        // STORAGE CLICK — multi-select
        storageAll.setOnClickListener(v -> {
            selectedStorages.clear();
            refreshStorageUI(storageAll, storageRoom, storageChilled, storageFrozen);
        });
        setStorage(storageRoom,    "room",    storageAll, storageRoom, storageChilled, storageFrozen);
        setStorage(storageChilled, "chilled", storageAll, storageRoom, storageChilled, storageFrozen);
        setStorage(storageFrozen,  "frozen",  storageAll, storageRoom, storageChilled, storageFrozen);

        // STATUS CLICK — multi-select
        statusAll.setOnClickListener(v -> {
            selectedStatuses.clear();
            refreshStatusUI(statusAll, statusFresh, statusNearExpiry, statusExpired);
        });
        setStatus(statusFresh,      "fresh",       statusAll, statusFresh, statusNearExpiry, statusExpired);
        setStatus(statusNearExpiry, "near_expiry", statusAll, statusFresh, statusNearExpiry, statusExpired);
        setStatus(statusExpired,    "expired",     statusAll, statusFresh, statusNearExpiry, statusExpired);

        // OPENED CLICK — single-select
        openedAll.setOnClickListener(v -> {
            selectedOpened = null;
            refreshOpenedUI(openedAll, openedYes, openedNo);
        });
        openedYes.setOnClickListener(v -> {
            selectedOpened = (Boolean.TRUE.equals(selectedOpened)) ? null : Boolean.TRUE;
            refreshOpenedUI(openedAll, openedYes, openedNo);
        });
        openedNo.setOnClickListener(v -> {
            selectedOpened = (Boolean.FALSE.equals(selectedOpened)) ? null : Boolean.FALSE;
            refreshOpenedUI(openedAll, openedYes, openedNo);
        });

        // SHARED CLICK — single-select; Shared/NotShared and FromOther are mutually exclusive
        sharedAll.setOnClickListener(v -> {
            selectedShared    = null;
            selectedFromOther = null;
            refreshSharedUI(sharedAll, sharedYes, sharedNo, sharedFromOther);
        });
        sharedYes.setOnClickListener(v -> {
            selectedShared    = Boolean.TRUE.equals(selectedShared) ? null : Boolean.TRUE;
            selectedFromOther = null;
            refreshSharedUI(sharedAll, sharedYes, sharedNo, sharedFromOther);
        });
        sharedNo.setOnClickListener(v -> {
            selectedShared    = Boolean.FALSE.equals(selectedShared) ? null : Boolean.FALSE;
            selectedFromOther = null;
            refreshSharedUI(sharedAll, sharedYes, sharedNo, sharedFromOther);
        });
        sharedFromOther.setOnClickListener(v -> {
            selectedFromOther = Boolean.TRUE.equals(selectedFromOther) ? null : Boolean.TRUE;
            selectedShared    = null;
            refreshSharedUI(sharedAll, sharedYes, sharedNo, sharedFromOther);
        });

        // RESET
        view.findViewById(R.id.btn_reset).setOnClickListener(v -> {

            selectedTags.clear();
            selectedNear     = 0;
            selectedExpired  = 0;
            selectedStorages.clear();
            selectedStatuses.clear();
            selectedOpened    = null;
            selectedShared    = null;
            selectedFromOther = null;

            refreshTagUI(tagAll, tagFruit, tagVegetable, tagMeat,
                    tagSeafood, tagDairy, tagBakery, tagSnack,
                    tagFrozen, tagDrink, tagHealthy, tagSweet, tagSpicy);

            refreshNearUI(nearAll, near5, near10, near15, near30);
            refreshExpiredUI(expAll, exp5, exp10, exp15, exp30);
            refreshStorageUI(storageAll, storageRoom, storageChilled, storageFrozen);
            refreshStatusUI(statusAll, statusFresh, statusNearExpiry, statusExpired);
            refreshOpenedUI(openedAll, openedYes, openedNo);
            refreshSharedUI(sharedAll, sharedYes, sharedNo, sharedFromOther);
        });

        // CONFIRM
        view.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            if (listener != null) {
                listener.onApply(selectedTags, selectedNear, selectedExpired,
                        selectedStorages, selectedStatuses, selectedOpened, selectedShared, selectedFromOther);
            }
            dismiss();
        });


        return view;
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

    // ================= TAG =================
    private void setTag(View v, String value,
                        View tagAll,
                        View tagFruit,
                        View tagVegetable,
                        View tagMeat,
                        View tagSeafood,
                        View tagDairy,
                        View tagBakery,
                        View tagSnack,
                        View tagFrozen,
                        View tagDrink,
                        View tagHealthy,
                        View tagSweet,
                        View tagSpicy) {

        v.setOnClickListener(view -> {

            if (selectedTags.contains(value)) {
                selectedTags.remove(value);
            } else {
                selectedTags.add(value);
            }

            refreshTagUI(tagAll, tagFruit, tagVegetable, tagMeat,
                    tagSeafood, tagDairy, tagBakery, tagSnack,
                    tagFrozen, tagDrink, tagHealthy, tagSweet, tagSpicy);
        });
    }

    // ================= NEAR — single-select =================
    private void setNear(View v, int value,
                         View nearAll, View near5, View near10, View near15, View near30) {
        v.setOnClickListener(view -> {
            // Tap same option again → deselect (back to Unlimited)
            selectedNear = (selectedNear == value) ? 0 : value;
            refreshNearUI(nearAll, near5, near10, near15, near30);
        });
    }

    // ================= EXPIRED — single-select =================
    private void setExpired(View v, int value,
                            View expAll, View exp5, View exp10, View exp15, View exp30) {
        v.setOnClickListener(view -> {
            selectedExpired = (selectedExpired == value) ? 0 : value;
            refreshExpiredUI(expAll, exp5, exp10, exp15, exp30);
        });
    }

    // ================= UI REFRESH =================
    private void refreshTagUI(View tagAll,
                              View tagFruit,
                              View tagVegetable,
                              View tagMeat,
                              View tagSeafood,
                              View tagDairy,
                              View tagBakery,
                              View tagSnack,
                              View tagFrozen,
                              View tagDrink,
                              View tagHealthy,
                              View tagSweet,
                              View tagSpicy) {

        tagAll.setSelected(selectedTags.isEmpty());

        tagFruit.setSelected(selectedTags.contains("Fruit"));
        tagVegetable.setSelected(selectedTags.contains("Vegetable"));
        tagMeat.setSelected(selectedTags.contains("Meat"));
        tagSeafood.setSelected(selectedTags.contains("Seafood"));
        tagDairy.setSelected(selectedTags.contains("Dairy"));
        tagBakery.setSelected(selectedTags.contains("Bakery"));
        tagSnack.setSelected(selectedTags.contains("Snack"));
        tagFrozen.setSelected(selectedTags.contains("Frozen"));
        tagDrink.setSelected(selectedTags.contains("Drink"));
        tagHealthy.setSelected(selectedTags.contains("Healthy"));
        tagSweet.setSelected(selectedTags.contains("Sweet"));
        tagSpicy.setSelected(selectedTags.contains("Spicy"));
    }

    private void refreshNearUI(View nearAll, View near5, View near10, View near15, View near30) {
        nearAll.setSelected(selectedNear == 0);
        near5  .setSelected(selectedNear == 5);
        near10 .setSelected(selectedNear == 10);
        near15 .setSelected(selectedNear == 15);
        near30 .setSelected(selectedNear == 30);
    }

    private void refreshExpiredUI(View expAll, View exp5, View exp10, View exp15, View exp30) {
        expAll.setSelected(selectedExpired == 0);
        exp5  .setSelected(selectedExpired == 5);
        exp10 .setSelected(selectedExpired == 10);
        exp15 .setSelected(selectedExpired == 15);
        exp30 .setSelected(selectedExpired == 30);
    }

    // ================= STORAGE =================
    private void setStorage(View v, String key,
                            View storageAll, View storageRoom, View storageChilled, View storageFrozen) {
        v.setOnClickListener(view -> {
            if (selectedStorages.contains(key)) selectedStorages.remove(key);
            else selectedStorages.add(key);
            refreshStorageUI(storageAll, storageRoom, storageChilled, storageFrozen);
        });
    }

    private void refreshStorageUI(View storageAll, View storageRoom, View storageChilled, View storageFrozen) {
        storageAll    .setSelected(selectedStorages.isEmpty());
        storageRoom   .setSelected(selectedStorages.contains("room"));
        storageChilled.setSelected(selectedStorages.contains("chilled"));
        storageFrozen .setSelected(selectedStorages.contains("frozen"));
    }

    // ================= STATUS =================
    private void setStatus(View v, String key,
                           View statusAll, View statusFresh, View statusNear, View statusExpired) {
        v.setOnClickListener(view -> {
            if (selectedStatuses.contains(key)) selectedStatuses.remove(key);
            else selectedStatuses.add(key);
            refreshStatusUI(statusAll, statusFresh, statusNear, statusExpired);
        });
    }

    private void refreshStatusUI(View statusAll, View statusFresh, View statusNear, View statusExpired) {
        statusAll    .setSelected(selectedStatuses.isEmpty());
        statusFresh  .setSelected(selectedStatuses.contains("fresh"));
        statusNear   .setSelected(selectedStatuses.contains("near_expiry"));
        statusExpired.setSelected(selectedStatuses.contains("expired"));
    }

    // ================= OPENED =================
    private void refreshOpenedUI(View openedAll, View openedYes, View openedNo) {
        openedAll.setSelected(selectedOpened == null);
        openedYes.setSelected(Boolean.TRUE.equals(selectedOpened));
        openedNo .setSelected(Boolean.FALSE.equals(selectedOpened));
    }

    // ================= SHARED =================
    private void refreshSharedUI(View sharedAll, View sharedYes, View sharedNo, View sharedFromOther) {
        boolean noneActive = selectedShared == null && !Boolean.TRUE.equals(selectedFromOther);
        sharedAll      .setSelected(noneActive);
        sharedYes      .setSelected(Boolean.TRUE.equals(selectedShared));
        sharedNo       .setSelected(Boolean.FALSE.equals(selectedShared));
        sharedFromOther.setSelected(Boolean.TRUE.equals(selectedFromOther));
    }
}
