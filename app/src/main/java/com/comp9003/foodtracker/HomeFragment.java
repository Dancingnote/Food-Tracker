package com.comp9003.foodtracker;

import android.content.Context;
import android.os.Bundle;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.GestureDetector;
import android.view.*;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.*;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.*;

import com.comp9003.foodtracker.adapter.ProductAdapter;
import com.comp9003.foodtracker.model.Product;

import android.content.Intent;
import java.util.*;

/**
 * Main product list screen.
 * Supports inline search, multi-select with bulk delete/archive, sort/filter bottom sheets,
 * swipe actions (left=delete, right=archive/restore), and a toggle between archived and active items.
 */
public class HomeFragment extends Fragment {

    private RecyclerView recyclerView;
    private ProductAdapter adapter;

    private EditText etSearch;
    private TextView tvSearch;
    private TextView tvGreetingName;
    private TextView tvArchiveSectionTitle;
    private ImageView ivArchiveToggle;
    private boolean showArchived = false;

    private List<Product> allProductList = new ArrayList<>();
    private List<Product> productList = new ArrayList<>();

    private SharedViewModel viewModel;

    private View homeMainContent;
    private View greetingBar, searchBar, normalToolbar;
    private View selectTopBar, selectBottomBar;
    private TextView tvSelectAll, tvExitSelect;
    private View layoutDelete, layoutArchive;
    private ImageView ivArchiveAction;
    private TextView tvArchiveAction;

    private boolean selectMode = false;
    private int originalHomePaddingTop = -1;
    private boolean pendingClearSearch = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        viewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);

        ProductRepository.ensureReady(requireContext());

        homeMainContent = view.findViewById(R.id.home_main_content);
        recyclerView = view.findViewById(R.id.rv_food_list);
        etSearch = view.findViewById(R.id.et_search);
        tvSearch = view.findViewById(R.id.tv_search);
        tvGreetingName = view.findViewById(R.id.tv_greeting_name);
        tvArchiveSectionTitle = view.findViewById(R.id.tv_archive_section_title);

        greetingBar = view.findViewById(R.id.greeting_bar);
        searchBar = view.findViewById(R.id.search_bar);
        normalToolbar = view.findViewById(R.id.normal_toolbar);

        selectTopBar = view.findViewById(R.id.select_top_bar);
        selectBottomBar = view.findViewById(R.id.select_bottom_bar);

        tvSelectAll = view.findViewById(R.id.tv_select_all);
        tvExitSelect = view.findViewById(R.id.tv_exit_select);

        layoutDelete = view.findViewById(R.id.layout_delete);
        layoutArchive = view.findViewById(R.id.layout_archive);
        ivArchiveAction = view.findViewById(R.id.iv_archive_action);
        tvArchiveAction = view.findViewById(R.id.tv_archive_action);

        ivArchiveToggle = view.findViewById(R.id.iv_archive_toggle);
        ivArchiveToggle.setAlpha(showArchived ? 1f : 0.4f);
        updateArchiveSectionTitle();

        updateGreeting();

        if (allProductList.isEmpty()) initData();

        setupDismissSearchOnBlankTouch(view);

        adapter = new ProductAdapter(productList, (product, position) -> {
            Intent intent = new Intent(requireContext(), EditProductActivity.class);
            if (isFromOther(product)) {
                // Group member editing another user's shared item
                intent.putExtra("sharedProduct", product);
                intent.putExtra("ownerUser", product.sharedByUser);
            } else {
                int realIndex = ProductRepository.products.indexOf(product);
                intent.putExtra("index", realIndex);
            }
            startActivity(intent);
        });

        adapter.refreshBindCache(requireContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(8);
        recyclerView.setAdapter(adapter);
        attachSwipeActions();

        view.findViewById(R.id.iv_sort).setOnClickListener(v -> {
            SortBottomSheetFragment sheet = new SortBottomSheetFragment();

            sheet.setInit(viewModel.sortType, viewModel.sortAsc);

            sheet.setListener((type, isAsc) -> {
                viewModel.sortType = type;
                viewModel.sortAsc = isAsc;
                applyAll();
            });

            sheet.show(getParentFragmentManager(), "sort");
        });

        ivArchiveToggle.setOnClickListener(v -> {
            showArchived = !showArchived;

            ivArchiveToggle.setAlpha(showArchived ? 1f : 0.4f);
            updateArchiveSectionTitle();

            for (Product p : ProductRepository.products) {
                p.isSelected = false;
            }

            applyAll();
        });

        view.findViewById(R.id.iv_filter).setOnClickListener(v -> {
            FilterBottomSheetFragment sheet = new FilterBottomSheetFragment();

            sheet.setInit(
                    viewModel.selectedTags,
                    viewModel.nearExpireThreshold,
                    viewModel.expiredThreshold,
                    viewModel.selectedStorages,
                    viewModel.selectedStatuses,
                    viewModel.filterOpened,
                    viewModel.filterShared,
                    viewModel.filterFromOther
            );

            sheet.setListener((tags, nearThreshold, expiredThreshold,
                                storages, statuses, openedFilter, sharedFilter, fromOtherFilter) -> {
                viewModel.selectedTags.clear();
                viewModel.selectedTags.addAll(tags);
                viewModel.nearExpireThreshold = nearThreshold;
                viewModel.expiredThreshold    = expiredThreshold;
                viewModel.selectedStorages.clear();
                viewModel.selectedStorages.addAll(storages);
                viewModel.selectedStatuses.clear();
                viewModel.selectedStatuses.addAll(statuses);
                viewModel.filterOpened    = openedFilter;
                viewModel.filterShared    = sharedFilter;
                viewModel.filterFromOther = fromOtherFilter;
                applyAll();
            });

            sheet.show(getParentFragmentManager(), "filter");
        });

        tvSearch.setOnClickListener(v -> {
            viewModel.keyword = etSearch.getText().toString().toLowerCase();
            applyAll();
        });

        view.findViewById(R.id.iv_select).setOnClickListener(v -> {
            enterSelectMode();
        });

        tvExitSelect.setOnClickListener(v -> {
            exitSelectMode();
        });

        tvSelectAll.setOnClickListener(v -> {
            boolean allSelected = areAllVisibleProductsSelected();

            for (Product p : productList) {
                if (!isFromOther(p)) p.isSelected = !allSelected;
            }

            adapter.notifyDataSetChanged();
            updateSelectAllText();
        });

        layoutDelete.setOnClickListener(v -> {
            confirmDeletion(() -> {
                ProductRepository.products.removeIf(p -> p.isSelected);
                ProductRepository.saveCurrentUserProducts(requireContext());

                exitSelectMode();
                rebuildAllProductList();
                applyAll();
            });
        });

        layoutArchive.setOnClickListener(v -> {
            boolean restoring = showArchived;
            for (Product p : ProductRepository.products) {
                if (p.isSelected) {
                    p.isArchived = !showArchived;
                    p.isSelected = false;
                }
            }
            if (restoring) mergeRestoredProducts();
            ProductRepository.saveCurrentUserProducts(requireContext());

            exitSelectMode();
            rebuildAllProductList();
            applyAll();
        });

        if (homeMainContent != null) {
            originalHomePaddingTop = homeMainContent.getPaddingTop();
        }

        if (pendingClearSearch) {
            applyClearSearch();
        }
    }

    private void setupDismissSearchOnBlankTouch(@NonNull View root) {
        View.OnTouchListener listener = (v, event) -> {
            if (event.getAction() != MotionEvent.ACTION_DOWN) return false;
            if (etSearch == null || !etSearch.isFocused()) return false;
            if (isTouchInsideView(event, etSearch)) return false;
            etSearch.clearFocus();
            hideKeyboard(etSearch);
            return false;
        };

        if (homeMainContent != null) homeMainContent.setOnTouchListener(listener);
        if (recyclerView != null) recyclerView.setOnTouchListener(listener);
        root.setOnTouchListener(listener);
    }

    private boolean isTouchInsideView(@NonNull MotionEvent event, @NonNull View view) {
        Rect rect = new Rect();
        view.getGlobalVisibleRect(rect);
        int x = (int) event.getRawX();
        int y = (int) event.getRawY();
        return rect.contains(x, y);
    }

    private void hideKeyboard(@NonNull View view) {
        Context context = view.getContext();
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        ProductRepository.loadCurrentUserProducts(requireContext());
        rebuildAllProductList();

        updateGreeting();

        if (adapter != null) {
            applyAll();
        }
    }

    private void updateGreeting() {
        if (tvGreetingName == null) return;
        String nickname = ProductRepository.getNickname(requireContext());
        if (nickname == null || nickname.trim().isEmpty()) {
            tvGreetingName.setText("Hello!");
        } else {
            tvGreetingName.setText("Hello, " + nickname + "!");
        }
    }

    private void initData() {
        rebuildAllProductList();
        productList.clear();
        productList.addAll(allProductList);
    }

    /** Rebuilds allProductList from current user's products + active shared items from others. */
    private void rebuildAllProductList() {
        allProductList.clear();
        allProductList.addAll(ProductRepository.products);
        List<com.comp9003.foodtracker.model.Product> fromOthers =
                ProductRepository.loadSharedFromOtherUsers(requireContext());
        allProductList.addAll(fromOthers);
    }

    /** Returns true if this product belongs to another user (not the current user). */
    private boolean isFromOther(com.comp9003.foodtracker.model.Product p) {
        if (p.sharedByUser == null) return false;
        String currentUser = ProductRepository.getCurrentUser(requireContext());
        return !p.sharedByUser.equalsIgnoreCase(currentUser);
    }

    private void applyAll() {
        productList.clear();
        productList.addAll(allProductList);
        applyFilter();
        applySort();
        adapter.refreshBindCache(requireContext());
        adapter.notifyDataSetChanged();
    }

    /**
     * Removes products from productList that don't satisfy the current filter criteria.
     * All SharedPreferences reads are hoisted above the removeIf loop to avoid per-item I/O.
     */
    private void applyFilter() {
        // Cache per-filter-pass values once — avoid SharedPreferences reads inside the loop
        boolean nearExpiryEnabled = requireContext()
                .getSharedPreferences("near_expiry_prefs", Context.MODE_PRIVATE)
                .getBoolean("enabled", true);
        java.time.LocalDate today = java.time.LocalDate.now();
        String currentUser = Boolean.TRUE.equals(viewModel.filterFromOther)
                ? ProductRepository.getCurrentUser(requireContext()) : null;

        productList.removeIf(p -> {
            if (p.isArchived != showArchived) return true;

            if (!viewModel.selectedTags.isEmpty()
                    && Collections.disjoint(p.tags, viewModel.selectedTags)) return true;

            if (viewModel.nearExpireThreshold > 0) {
                int days = p.getDaysToExpire();
                if (days < 0 || days > viewModel.nearExpireThreshold) return true;
            }

            if (viewModel.expiredThreshold > 0) {
                int days = p.getDaysAfterExpire();
                if (days < 0 || days > viewModel.expiredThreshold) return true;
            }

            if (!viewModel.keyword.isEmpty()
                    && !p.name.toLowerCase().contains(viewModel.keyword)) return true;

            if (!viewModel.selectedStorages.isEmpty()
                    && !viewModel.selectedStorages.contains(normalizeStorageKey(p.storageState)))
                return true;

            if (!viewModel.selectedStatuses.isEmpty()) {
                int state = computeExpiryState(p, today, nearExpiryEnabled);
                String key = state == 2 ? "expired" : state == 1 ? "near_expiry" : "fresh";
                if (!viewModel.selectedStatuses.contains(key)) return true;
            }

            if (viewModel.filterOpened != null
                    && !Objects.equals(p.isOpened, viewModel.filterOpened)) return true;

            if (viewModel.filterShared != null && p.isShared != viewModel.filterShared)
                return true;

            if (Boolean.TRUE.equals(viewModel.filterFromOther)) {
                boolean fromOther = p.sharedByUser != null
                        && !p.sharedByUser.equalsIgnoreCase(currentUser);
                if (!fromOther) return true;
            }

            return false;
        });
    }

    private static int computeExpiryState(Product p, java.time.LocalDate today, boolean nearExpiryEnabled) {
        if (p.expireDate == null) return 0;
        if (p.expireDate.isBefore(today)) return 2;
        if (!nearExpiryEnabled) return 0;
        int days = (int) java.time.temporal.ChronoUnit.DAYS.between(today, p.expireDate);
        return days <= ProductRepository.thresholdDaysByCategory(p) ? 1 : 0;
    }

    /** Maps a product's storageState string to one of "room", "chilled", "frozen", or "". */
    private static String normalizeStorageKey(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        String v = raw.trim();
        if ("Room temp".equals(v) || "常温".equals(v)) return "room";
        if ("Chilled".equals(v)   || "冷藏".equals(v)) return "chilled";
        if ("Frozen".equals(v)    || "冷冻".equals(v)) return "frozen";
        return "";
    }

    /** Sorts productList according to the current sort type and direction in SharedViewModel. */
    private void applySort() {

        Comparator<Product> comp;

        switch (viewModel.sortType) {

            case "name":
                comp = Comparator.comparing(p -> p.name.toLowerCase());
                break;

            case "stock":
                comp = Comparator.comparingInt(p -> p.quantity);
                break;

            case "expire":
                comp = Comparator.comparing(p -> p.expireDate);
                break;

            case "near_expire":
                comp = Comparator.comparing(Product::getDaysToExpire);
                break;

            default:
                comp = Comparator.comparing(p -> p.createdDate);
        }

        productList.sort(comp);

        if (!viewModel.sortAsc) Collections.reverse(productList);
    }

    /**
     * Attaches custom swipe gestures to the RecyclerView:
     * - Left swipe: delete (with confirmation dialog on full swipe)
     * - Right swipe (active list): partial open reveals ALL/PART archive buttons; full swipe archives all
     * - Right swipe (archived list): full swipe restores the item
     *
     * Draws the action backgrounds and icons directly on the Canvas in onChildDraw(),
     * and uses a GestureDetector + OnItemTouchListener to capture taps on the open-menu buttons.
     */
    private void attachSwipeActions() {
        if (recyclerView == null) return;

        // Disable change-animation crossfade that causes flicker on notifyItemChanged
        RecyclerView.ItemAnimator itemAnimator = recyclerView.getItemAnimator();
        if (itemAnimator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) itemAnimator).setSupportsChangeAnimations(false);
        }

        final int actionWidth = dpToPx(176);
        final int openThreshold = dpToPx(72);
        final int closeSwipeThreshold = dpToPx(12);
        final int[] swipedPos = new int[]{-1};
        final RectF[] btnAll = new RectF[]{null};
        final RectF[] btnPart = new RectF[]{null};

        ItemTouchHelper.SimpleCallback cb = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint slashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private float lastDx = 0f;
            private int lastPos = -1;
            // Tracks item that should snap to open-menu state during recovery animation
            private int pendingOpenPos = -1;
            // Cached drawables and reusable objects to avoid per-frame allocations
            private Drawable iconDelete = null;
            private Drawable iconArchive = null;
            private final RectF reuseAllRect = new RectF();
            private final RectF reusePartRect = new RectF();
            private final RectF reuseDrawRect = new RectF();
            private final Path reusePath = new Path();
            private boolean paintSizesReady = false;
            // Precomputed sizes (initialized once)
            private float cardCornerRadius = 0f;
            private int cardMarginBottomPx = 0;
            private int iconSizePx = 0;
            private int iconTextGapPx = 0;
            private float[] cornersAll = null;      // all 4 corners rounded
            private float[] cornersOnlyLeft = null; // only left corners rounded (for ALL section)

            {
                slashPaint.setStyle(Paint.Style.STROKE);
                slashPaint.setStrokeCap(Paint.Cap.ROUND);
                slashPaint.setColor(0xFFFFFFFF);
                textPaint.setTextAlign(Paint.Align.CENTER);
                textPaint.setFakeBoldText(true);
                dividerPaint.setStrokeCap(Paint.Cap.ROUND);
                dividerPaint.setColor(0x55FFFFFF);
            }

            @Override
            public int getSwipeDirs(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                if (selectMode) return 0;
                if (adapter != null && adapter.isSelectMode()) return 0;
                if (swipedPos[0] != -1 && viewHolder.getAdapterPosition() != swipedPos[0]) return 0;
                int pos = viewHolder.getAdapterPosition();
                if (pos >= 0 && pos < productList.size() && isFromOther(productList.get(pos))) return 0;
                return super.getSwipeDirs(recyclerView, viewHolder);
            }

            @Override
            public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
                return 10f;
            }

            @Override
            public float getSwipeEscapeVelocity(float defaultValue) {
                // Prevent velocity-based onSwiped trigger on fast fling
                return Float.MAX_VALUE;
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);

                int pos = viewHolder.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) {
                    pendingOpenPos = -1;
                    return;
                }
                if (lastPos != pos) {
                    pendingOpenPos = -1;
                    return;
                }

                float width = viewHolder.itemView.getWidth();
                float fullThreshold = width * 0.55f;

                if (lastDx <= -fullThreshold) {
                    pendingOpenPos = -1;
                    swipedPos[0] = -1;
                    viewHolder.itemView.setTranslationX(0f);
                    confirmDeletion(() -> {
                        if (pos >= 0 && pos < productList.size()) {
                            Product p = productList.get(pos);
                            ProductRepository.products.remove(p);
                            allProductList.remove(p);
                            ProductRepository.saveCurrentUserProducts(requireContext());
                            applyAll();
                        }
                    });
                    return;
                }

                if (lastDx >= fullThreshold) {
                    pendingOpenPos = -1;
                    swipedPos[0] = -1;
                    viewHolder.itemView.setTranslationX(0f);
                    if (showArchived) {
                        if (pos >= 0 && pos < productList.size()) {
                            Product p = productList.get(pos);
                            p.isArchived = false;
                            mergeRestoredProducts();
                            ProductRepository.saveCurrentUserProducts(requireContext());
                            allProductList.clear();
                            allProductList.addAll(ProductRepository.products);
                            applyAll();
                        }
                    } else {
                        confirmArchiveAll(() -> {
                            if (pos >= 0 && pos < productList.size()) {
                                Product p = productList.get(pos);
                                p.isArchived = true;
                                ProductRepository.saveCurrentUserProducts(requireContext());
                                allProductList.clear();
                                allProductList.addAll(ProductRepository.products);
                                applyAll();
                            }
                        });
                    }
                    return;
                }

                if (!showArchived && lastDx >= openThreshold) {
                    swipedPos[0] = pos;
                    pendingOpenPos = -1;
                    viewHolder.itemView.setTranslationX(actionWidth);
                    return;
                }

                pendingOpenPos = -1;
                swipedPos[0] = -1;
                viewHolder.itemView.setTranslationX(0f);
            }

            @Override
            public void onChildDraw(@NonNull Canvas c,
                                    @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder,
                                    float dX,
                                    float dY,
                                    int actionState,
                                    boolean isCurrentlyActive) {
                View itemView = viewHolder.itemView;
                int top = itemView.getTop();
                int bottom = itemView.getBottom();
                int left = itemView.getLeft();
                int right = itemView.getRight();
                int height = bottom - top;

                int iconSize = dpToPx(24);
                int iconMargin = dpToPx(22);
                int iconTop = top + (height - iconSize) / 2;

                int pos = viewHolder.getAdapterPosition();

                if (isCurrentlyActive && pos != RecyclerView.NO_POSITION) {
                    lastDx = dX;
                    lastPos = pos;
                    // Track whether this drag should open the menu on release
                    if (!showArchived) {
                        float width = itemView.getWidth();
                        float fullThreshold = width * 0.55f;
                        if (dX >= openThreshold && dX < fullThreshold) {
                            pendingOpenPos = pos;
                        } else {
                            pendingOpenPos = -1;
                        }
                    }
                }

                // During recovery animation and open-menu state, hold item at actionWidth
                // This prevents the snap-back flicker before clearView is called
                if (!isCurrentlyActive && pos != RecyclerView.NO_POSITION
                        && (pos == swipedPos[0] || (!showArchived && pos == pendingOpenPos))) {
                    dX = actionWidth;
                }

                // Lazy-init cached drawables and sizes (requires Context, done once)
                if (!paintSizesReady) {
                    cardCornerRadius = dpToPx(20);
                    cardMarginBottomPx = dpToPx(10);
                    iconSizePx = dpToPx(26);
                    iconTextGapPx = dpToPx(5);
                    float cr = cardCornerRadius;
                    cornersAll      = new float[]{cr, cr, cr, cr, cr, cr, cr, cr};
                    cornersOnlyLeft = new float[]{cr, cr,  0,  0,  0,  0, cr, cr};
                    slashPaint.setStrokeWidth(dpToPx(2.5f));
                    textPaint.setTextSize(dpToPx(11));
                    textPaint.setColor(0xFFFFFFFF);
                    dividerPaint.setStrokeWidth(dpToPx(1));
                    paintSizesReady = true;
                }
                if (iconDelete == null) {
                    Drawable d = ContextCompat.getDrawable(requireContext(), R.drawable.ic_delete);
                    if (d != null) { d = d.mutate(); d.setTint(0xFFFFFFFF); }
                    iconDelete = d;
                }
                if (iconArchive == null) {
                    Drawable d = ContextCompat.getDrawable(requireContext(), R.drawable.ic_archive);
                    if (d != null) { d = d.mutate(); d.setTint(0xFFFFFFFF); }
                    iconArchive = d;
                }

                // Visual card bounds: align with the item view's actual drawn area
                // itemView.getBottom() already excludes layout_marginBottom (external gap)
                float cardTop = top + dpToPx(1);
                float cardBot = bottom - dpToPx(5);
                float cardH   = cardBot - cardTop;
                // Precompute grouped icon+label height for vertical centering
                float groupH  = iconSizePx + iconTextGapPx - textPaint.ascent();
                float iconY   = cardTop + (cardH - groupH) / 2f;
                float textBaseline = iconY + iconSizePx + iconTextGapPx - textPaint.ascent();

                if (dX < 0) {
                    // ── LEFT SWIPE: Delete ──────────────────────────────────────
                    float areaLeft = right + dX;

                    reuseDrawRect.set(areaLeft, cardTop, right, cardBot);
                    reusePath.reset();
                    reusePath.addRoundRect(reuseDrawRect, cornersAll, Path.Direction.CW);
                    paint.setColor(0xFFE53935);
                    c.drawPath(reusePath, paint);

                    // Icon + "Delete" label, centered in the revealed area
                    if (iconDelete != null) {
                        float cx = areaLeft + (right - areaLeft) / 2f;
                        int half = iconSizePx / 2;
                        iconDelete.setBounds(
                                (int)(cx - half), (int)iconY,
                                (int)(cx + half), (int)(iconY + iconSizePx));
                        iconDelete.draw(c);
                        c.drawText("Delete", cx, textBaseline, textPaint);
                    }

                } else if (dX > 0) {
                    if (showArchived) {
                        // ── RIGHT SWIPE (archived): Restore ────────────────────
                        float areaRight = left + dX;

                        c.save();
                        c.clipRect(left, cardTop, areaRight, cardBot);

                        reuseDrawRect.set(left, cardTop, areaRight, cardBot);
                        reusePath.reset();
                        reusePath.addRoundRect(reuseDrawRect, cornersAll, Path.Direction.CW);
                        paint.setColor(0xFF00897B);
                        c.drawPath(reusePath, paint);

                        if (iconArchive != null) {
                            // Anchor icon at a fixed offset so it appears early in the swipe
                            float cx = left + dpToPx(52);
                            int half = iconSizePx / 2;
                            iconArchive.setBounds(
                                    (int)(cx - half), (int)iconY,
                                    (int)(cx + half), (int)(iconY + iconSizePx));
                            iconArchive.draw(c);

                            float il = cx - half;
                            c.drawLine(il + iconSizePx * 0.15f, iconY + iconSizePx * 0.20f,
                                       il + iconSizePx * 0.85f, iconY + iconSizePx * 0.80f,
                                       slashPaint);

                            c.drawText("Restore", cx, textBaseline, textPaint);
                        }

                        c.restore();

                    } else {
                        // ── RIGHT SWIPE (normal): ALL / PART archive menu ───────
                        float reveal = Math.min(dX, actionWidth);
                        float btnW   = actionWidth / 2f;

                        // Tap detection rects (full card bounds)
                        reuseAllRect.set(left,         cardTop, left + btnW,      cardBot);
                        reusePartRect.set(left + btnW, cardTop, left + actionWidth, cardBot);
                        btnAll[0]  = reuseAllRect;
                        btnPart[0] = reusePartRect;

                        c.save();
                        c.clipRect(left, cardTop, left + reveal, cardBot);

                        // Step 1: PART color as full rounded base (preserves right corners)
                        reuseDrawRect.set(left, cardTop, left + actionWidth, cardBot);
                        reusePath.reset();
                        reusePath.addRoundRect(reuseDrawRect, cornersAll, Path.Direction.CW);
                        paint.setColor(0xFF1E88E5);
                        c.drawPath(reusePath, paint);

                        // Step 2: ALL color over left half, only left corners rounded
                        reuseDrawRect.set(left, cardTop, left + btnW, cardBot);
                        reusePath.reset();
                        reusePath.addRoundRect(reuseDrawRect, cornersOnlyLeft, Path.Direction.CW);
                        paint.setColor(0xFF1565C0);
                        c.drawPath(reusePath, paint);

                        // Subtle white divider between the two buttons
                        int divInset = dpToPx(14);
                        c.drawLine(left + btnW, cardTop + divInset,
                                   left + btnW, cardBot  - divInset, dividerPaint);

                        // Icons + labels, grouped and vertically centered
                        if (iconArchive != null) {
                            int half = iconSizePx / 2;

                            float cxAll = reuseAllRect.centerX();
                            iconArchive.setBounds(
                                    (int)(cxAll - half), (int)iconY,
                                    (int)(cxAll + half), (int)(iconY + iconSizePx));
                            iconArchive.draw(c);
                            c.drawText("ALL", cxAll, textBaseline, textPaint);

                            float cxPart = reusePartRect.centerX();
                            iconArchive.setBounds(
                                    (int)(cxPart - half), (int)iconY,
                                    (int)(cxPart + half), (int)(iconY + iconSizePx));
                            iconArchive.draw(c);
                            c.drawText("PART", cxPart, textBaseline, textPaint);
                        }

                        c.restore();
                    }
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int pos = viewHolder.getAdapterPosition();
                if (adapter != null && pos != RecyclerView.NO_POSITION) adapter.notifyItemChanged(pos);
            }
        };

        new ItemTouchHelper(cb).attachToRecyclerView(recyclerView);

        Runnable closeSwipeMenu = () -> {
            if (swipedPos[0] == -1) return;
            int pos = swipedPos[0];
            swipedPos[0] = -1;
            btnAll[0] = null;
            btnPart[0] = null;
            if (adapter != null) adapter.notifyItemChanged(pos);
        };

        GestureDetector detector = new GestureDetector(requireContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (showArchived) return false;
                if (swipedPos[0] == -1) return false;
                RectF allRect = btnAll[0];
                RectF partRect = btnPart[0];
                if (allRect == null || partRect == null) return false;

                float x = e.getX();
                float y = e.getY();

                if (allRect.contains(x, y)) {
                    int pos = swipedPos[0];
                    closeSwipeMenu.run();
                    if (pos >= 0 && pos < productList.size()) {
                        Product p = productList.get(pos);
                        p.isArchived = true;
                        ProductRepository.saveCurrentUserProducts(requireContext());
                        applyAll();
                    }
                    return true;
                }

                if (partRect.contains(x, y)) {
                    int pos = swipedPos[0];
                    closeSwipeMenu.run();
                    if (pos >= 0 && pos < productList.size()) {
                        Product p = productList.get(pos);
                        showPartialArchiveDialog(p);
                    }
                    return true;
                }

                closeSwipeMenu.run();
                return false;
            }
        });

        recyclerView.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            private float downX;
            private float downY;

            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                if (showArchived) return false;
                if (swipedPos[0] == -1) return false;
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    downX = e.getX();
                    downY = e.getY();
                }
                return true;
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                if (showArchived) return;
                if (swipedPos[0] == -1) return;

                detector.onTouchEvent(e);

                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    downX = e.getX();
                    downY = e.getY();
                    RectF allRect = btnAll[0];
                    RectF partRect = btnPart[0];
                    if (allRect == null || partRect == null) {
                        closeSwipeMenu.run();
                        return;
                    }
                    float x = e.getX();
                    float y = e.getY();
                    if (!allRect.contains(x, y) && !partRect.contains(x, y)) {
                        closeSwipeMenu.run();
                    }
                    return;
                }

                if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    float dx = e.getX() - downX;
                    float dy = e.getY() - downY;
                    if (Math.abs(dx) > closeSwipeThreshold || Math.abs(dy) > closeSwipeThreshold) {
                        closeSwipeMenu.run();
                    }
                    return;
                }

                if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    closeSwipeMenu.run();
                }
            }
        });
    }

    private void confirmDeletion(@NonNull Runnable onConfirm) {
        if (!isAdded()) return;
        new AlertDialog.Builder(requireContext())
                .setMessage("Confirm deletion?")
                .setNegativeButton("Cancel", (d, which) -> d.dismiss())
                .setPositiveButton("Delete", (d, which) -> {
                    d.dismiss();
                    onConfirm.run();
                })
                .show();
    }

    private void confirmArchiveAll(@NonNull Runnable onConfirm) {
        if (!isAdded()) return;
        new AlertDialog.Builder(requireContext())
                .setMessage("Archive all?")
                .setNegativeButton("Cancel", (d, which) -> d.dismiss())
                .setPositiveButton("Archive", (d, which) -> {
                    d.dismiss();
                    onConfirm.run();
                })
                .show();
    }

    private void showPartialArchiveDialog(@NonNull Product product) {
        if (!isAdded()) return;
        int max = Math.max(1, product.quantity);

        Dialog dialog = new Dialog(requireContext());
        View v = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_partial_archive, null);
        dialog.setContentView(v);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    (int) (requireContext().getResources().getDisplayMetrics().widthPixels * 0.88f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvHint    = v.findViewById(R.id.tv_stock_hint);
        EditText etCount   = v.findViewById(R.id.tv_count);
        TextView btnMinus  = v.findViewById(R.id.btn_minus);
        TextView btnPlus   = v.findViewById(R.id.btn_plus);
        TextView btnCancel = v.findViewById(R.id.btn_cancel);
        TextView btnArchive= v.findViewById(R.id.btn_archive);

        tvHint.setText("Total in stock: " + product.quantity);

        final int[] qty = {1};
        final boolean[] selfUpdate = {false};

        Runnable refreshButtons = () -> {
            btnMinus.setAlpha(qty[0] <= 1   ? 0.35f : 1f);
            btnPlus .setAlpha(qty[0] >= max ? 0.35f : 1f);
        };

        // Sync EditText → qty (while user types)
        etCount.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (selfUpdate[0]) return;
                String str = s.toString().trim();
                if (str.isEmpty()) { refreshButtons.run(); return; }
                try {
                    int val = Integer.parseInt(str);
                    qty[0] = Math.max(1, Math.min(max, val));
                    // Clamp without triggering this watcher again
                    if (val != qty[0]) {
                        selfUpdate[0] = true;
                        etCount.setText(String.valueOf(qty[0]));
                        etCount.setSelection(etCount.getText().length());
                        selfUpdate[0] = false;
                    }
                } catch (NumberFormatException ignored) {}
                refreshButtons.run();
            }
        });

        // Clamp on focus-lost (handles empty/out-of-range input)
        etCount.setOnFocusChangeListener((v2, hasFocus) -> {
            if (!hasFocus) {
                String str = etCount.getText().toString().trim();
                int val = str.isEmpty() ? 1 : Integer.parseInt(str);
                qty[0] = Math.max(1, Math.min(max, val));
                selfUpdate[0] = true;
                etCount.setText(String.valueOf(qty[0]));
                selfUpdate[0] = false;
                refreshButtons.run();
            }
        });

        // Sync ± buttons → EditText
        btnMinus.setOnClickListener(v2 -> {
            if (qty[0] > 1) {
                qty[0]--;
                selfUpdate[0] = true;
                etCount.setText(String.valueOf(qty[0]));
                etCount.setSelection(etCount.getText().length());
                selfUpdate[0] = false;
                refreshButtons.run();
            }
        });
        btnPlus.setOnClickListener(v2 -> {
            if (qty[0] < max) {
                qty[0]++;
                selfUpdate[0] = true;
                etCount.setText(String.valueOf(qty[0]));
                etCount.setSelection(etCount.getText().length());
                selfUpdate[0] = false;
                refreshButtons.run();
            }
        });

        refreshButtons.run();

        btnCancel.setOnClickListener(v2 -> dialog.dismiss());
        btnArchive.setOnClickListener(v2 -> {
            // Commit any uncommitted typed value before archiving
            String str = etCount.getText().toString().trim();
            if (!str.isEmpty()) qty[0] = Math.max(1, Math.min(max, Integer.parseInt(str)));
            dialog.dismiss();
            archivePartial(product, qty[0]);
        });

        dialog.show();
    }

    /**
     * After restoring one or more archived items, scan all active (non-archived) products and
     * merge any pair that is identical in every field except quantity — summing their quantities.
     * This prevents duplicates when a partially-archived item is restored alongside its original.
     */
    private void mergeRestoredProducts() {
        List<Product> active = new ArrayList<>();
        for (Product p : ProductRepository.products) {
            if (!p.isArchived) active.add(p);
        }

        Set<Product> toRemove = new HashSet<>();
        for (int i = 0; i < active.size(); i++) {
            Product base = active.get(i);
            if (toRemove.contains(base)) continue;
            for (int j = i + 1; j < active.size(); j++) {
                Product other = active.get(j);
                if (toRemove.contains(other)) continue;
                if (isSameProduct(base, other)) {
                    base.quantity += other.quantity;
                    toRemove.add(other);
                }
            }
        }

        if (!toRemove.isEmpty()) {
            ProductRepository.products.removeAll(toRemove);
        }
    }

    private boolean isSameProduct(Product a, Product b) {
        return Objects.equals(a.name, b.name)
                && Objects.equals(a.expireDate, b.expireDate)
                && Objects.equals(a.productionDate, b.productionDate)
                && Objects.equals(a.createdDate, b.createdDate)
                && Objects.equals(a.storageState, b.storageState)
                && Objects.equals(a.isOpened, b.isOpened)
                && Double.compare(a.price, b.price) == 0
                && Objects.equals(a.imageUri, b.imageUri)
                && Objects.equals(a.tags, b.tags);
    }

    /**
     * Archives a portion of a product's stock.
     * If qty equals the total stock, archives the whole item.
     * Otherwise, splits into two Product objects: the archived portion and the remaining active stock.
     */
    private void archivePartial(@NonNull Product product, int qty) {
        if (qty <= 0) return;
        if (qty >= product.quantity) {
            product.isArchived = true;
            ProductRepository.saveCurrentUserProducts(requireContext());
            allProductList.clear();
            allProductList.addAll(ProductRepository.products);
            applyAll();
            return;
        }

        ArrayList<String> tags = product.tags == null ? new ArrayList<>() : new ArrayList<>(product.tags);
        Product archived = new Product(
                product.name,
                qty,
                product.price,
                product.createdDate,
                product.productionDate,
                product.expireDate,
                product.imageUri,
                tags
        );
        archived.isArchived = true;
        archived.storageState = product.storageState;
        archived.isOpened = product.isOpened;

        product.quantity = product.quantity - qty;

        ProductRepository.products.add(archived);
        ProductRepository.saveCurrentUserProducts(requireContext());
        allProductList.clear();
        allProductList.addAll(ProductRepository.products);
        applyAll();
    }

    private void enterSelectMode() {
        selectMode = true;

        greetingBar.setVisibility(View.GONE);
        searchBar.setVisibility(View.GONE);
        normalToolbar.setVisibility(View.GONE);
        if (tvArchiveSectionTitle != null) tvArchiveSectionTitle.setVisibility(View.GONE);

        selectTopBar.setVisibility(View.VISIBLE);
        selectBottomBar.setVisibility(View.VISIBLE);

        updateArchiveActionUi();

        if (homeMainContent != null) {
            int left = homeMainContent.getPaddingLeft();
            int right = homeMainContent.getPaddingRight();
            int bottom = homeMainContent.getPaddingBottom();
            int baseTop = originalHomePaddingTop >= 0 ? originalHomePaddingTop : homeMainContent.getPaddingTop();
            homeMainContent.setPadding(left, baseTop + dpToPx(56), right, bottom);
        }

        View bottomNavCard = requireActivity().findViewById(R.id.bottom_nav_card);
        if (bottomNavCard != null) bottomNavCard.setVisibility(View.GONE);

        View fabAdd = requireActivity().findViewById(R.id.fab_add);
        if (fabAdd != null) fabAdd.setVisibility(View.GONE);

        adapter.setSelectMode(true);
        updateSelectAllText();
    }

    private void exitSelectMode() {
        selectMode = false;

        for (Product p : ProductRepository.products) {
            p.isSelected = false;
        }

        greetingBar.setVisibility(View.VISIBLE);
        searchBar.setVisibility(View.VISIBLE);
        normalToolbar.setVisibility(View.VISIBLE);
        if (tvArchiveSectionTitle != null) tvArchiveSectionTitle.setVisibility(View.VISIBLE);

        selectTopBar.setVisibility(View.GONE);
        selectBottomBar.setVisibility(View.GONE);

        updateArchiveActionUi();

        if (homeMainContent != null) {
            int left = homeMainContent.getPaddingLeft();
            int right = homeMainContent.getPaddingRight();
            int bottom = homeMainContent.getPaddingBottom();
            int top = originalHomePaddingTop >= 0 ? originalHomePaddingTop : homeMainContent.getPaddingTop();
            homeMainContent.setPadding(left, top, right, bottom);
        }

        View bottomNavCard = requireActivity().findViewById(R.id.bottom_nav_card);
        if (bottomNavCard != null) bottomNavCard.setVisibility(View.VISIBLE);

        View fabAdd = requireActivity().findViewById(R.id.fab_add);
        if (fabAdd != null) fabAdd.setVisibility(View.VISIBLE);

        adapter.setSelectMode(false);
        updateSelectAllText();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
    }

    private int dpToPx(float dp) {
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
    }

    public void requestClearSearch() {
        pendingClearSearch = true;
        if (isAdded() && etSearch != null && viewModel != null) {
            applyClearSearch();
        }
    }

    private void applyClearSearch() {
        etSearch.setText("");
        viewModel.keyword = "";
        applyAll();
        pendingClearSearch = false;
    }

    private void updateArchiveActionUi() {
        if (tvArchiveAction != null) {
            tvArchiveAction.setText(showArchived ? "Restore" : "Archive");
        }
        if (ivArchiveAction != null) {
            ivArchiveAction.setImageResource(R.drawable.ic_archive);
        }
    }

    private void updateArchiveSectionTitle() {
        if (tvArchiveSectionTitle != null) {
            tvArchiveSectionTitle.setText(showArchived ? "Archived" : "Unarchived");
        }
    }

    private boolean areAllVisibleProductsSelected() {
        if (productList.isEmpty()) return false;

        for (Product p : productList) {
            if (!p.isSelected) return false;
        }

        return true;
    }

    private void updateSelectAllText() {
        tvSelectAll.setText(areAllVisibleProductsSelected() ? "Cancel All" : "Select All");
    }
}
