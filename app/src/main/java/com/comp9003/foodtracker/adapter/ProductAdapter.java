package com.comp9003.foodtracker.adapter;

import android.graphics.Outline;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.comp9003.foodtracker.ProductRepository;
import com.comp9003.foodtracker.R;
import com.comp9003.foodtracker.model.Product;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Locale;
import java.util.List;
import java.util.Set;

/**
 * RecyclerView adapter for the product list.
 *
 * Performance notes:
 * - All SharedPreferences and Room reads are done once per data refresh in {@link #refreshBindCache}
 *   and then read from instance fields during onBindViewHolder — avoiding I/O on the bind path.
 * - The progress bar uses GPU-only scaleX transform (no layout pass) to update the freshness bar.
 * - Pill badge drawables are allocated once per ViewHolder and reused by mutating color on each bind.
 */
public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ViewHolder> {

    private List<Product> productList;
    private boolean selectMode = false;
    /** Tracks which products are showing the percentage badge (toggled by tapping the progress bar). */
    private final Set<Product> percentVisibleProducts = new HashSet<>();
    /** Cached checkbox width used to indent the bottom row; computed once and reused. */
    private int cachedCheckboxWidth = 0;

    // ── Bind-cycle cache ─────────────────────────────────────────────────────
    // Refreshed once before every notifyDataSetChanged(); used read-only in bind.
    // Avoids Room DB queries and SharedPreferences reads inside onBindViewHolder.
    private String   bindCurrentUser   = null;
    private LocalDate bindToday        = LocalDate.now();
    private int   dispPriority         = 0;  // 0=time remaining, 1=quantity
    private int   dispStyle            = 0;  // 0=auto, 1=days, 2=date
    private int   dispType             = 0;  // 0=expire date, 1=near-expiry date
    private int   dispShowTime         = 1;
    private int   dispDateFmt          = 0;
    private boolean dispIncludeProdDay = true;
    private boolean nearExpiryEnabled  = true;

    /** Call this once before every notifyDataSetChanged() to update the bind cache. */
    public void refreshBindCache(Context context) {
        bindCurrentUser = ProductRepository.getCurrentUser(context);
        bindToday       = LocalDate.now();

        SharedPreferences dp = context.getSharedPreferences("display_prefs", Context.MODE_PRIVATE);
        dispPriority      = dp.getInt("priority_content", 0);
        dispStyle         = dp.getInt("remaining_style", 0);
        dispType          = dp.getInt("remaining_type", 0);
        dispShowTime      = dp.getInt("show_time", 1);
        dispDateFmt       = dp.getInt("date_format", 0);
        dispIncludeProdDay= dp.getBoolean("include_prod_day", true);

        nearExpiryEnabled = context.getSharedPreferences("near_expiry_prefs", Context.MODE_PRIVATE)
                                   .getBoolean("enabled", true);
    }

    public interface OnItemClickListener {
        void onItemClick(Product product, int position);
    }

    private OnItemClickListener listener;

    public ProductAdapter(List<Product> productList, OnItemClickListener listener) {
        this.productList = productList;
        this.listener = listener;
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        ImageView imgItem;
        View bottomRow;
        View progressContainer;
        View viewProgress;
        TextView tvName;
        TextView tvSubInfo;
        TextView tvTargetDate;
        TextView tvProgressBadge;
        TextView tvExpiryState;
        TextView tvRightPrimary;
        TextView tvTagOpened;
        TextView tvTagStorage;
        TextView tvTagShared;
        TextView tvTagFromUser;
        View viewFromAccent;
        View tagsContainer;
        CheckBox cbSelect;
        GradientDrawable progressBadgeBg;
        GradientDrawable fromAccentBg;
        GradientDrawable expiryStateBg;
        GradientDrawable tagOpenedBg;
        GradientDrawable tagStorageBg;
        GradientDrawable tagSharedBg;
        int strokeWidthPx;
        int bottomRowBasePaddingStart;
        int bottomRowBasePaddingTop;
        int bottomRowBasePaddingEnd;
        int bottomRowBasePaddingBottom;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            imgItem = itemView.findViewById(R.id.img_item);
            bottomRow = itemView.findViewById(R.id.bottom_row);
            progressContainer = itemView.findViewById(R.id.progress_container);
            viewProgress = itemView.findViewById(R.id.view_progress);
            tvName = itemView.findViewById(R.id.tv_name);
            tvSubInfo = itemView.findViewById(R.id.tv_sub_info);
            tvTargetDate = itemView.findViewById(R.id.tv_target_date);
            tvProgressBadge = itemView.findViewById(R.id.tv_progress_badge);
            tvExpiryState = itemView.findViewById(R.id.tv_expiry_state);
            tvRightPrimary = itemView.findViewById(R.id.tv_right_primary);
            tvTagOpened = itemView.findViewById(R.id.tv_tag_opened);
            tvTagStorage = itemView.findViewById(R.id.tv_tag_storage);
            tvTagShared = itemView.findViewById(R.id.tv_tag_shared);
            tvTagFromUser = itemView.findViewById(R.id.tv_tag_from_user);
            viewFromAccent = itemView.findViewById(R.id.view_from_accent);
            tagsContainer = itemView.findViewById(R.id.tags_container);
            cbSelect = itemView.findViewById(R.id.cb_select);

            if (bottomRow != null) {
                bottomRowBasePaddingStart = bottomRow.getPaddingStart();
                bottomRowBasePaddingTop = bottomRow.getPaddingTop();
                bottomRowBasePaddingEnd = bottomRow.getPaddingEnd();
                bottomRowBasePaddingBottom = bottomRow.getPaddingBottom();
            }
            // Progress bar is match_parent; scale from the left edge (GPU-only, no layout pass)
            if (viewProgress != null) viewProgress.setPivotX(0f);

            // Pre-built pill drawables for status badge and tags — light tint fill (~12% alpha)
            // + full-opacity colored border. Allocated once per ViewHolder; fill/stroke updated on bind.
            float density = itemView.getResources().getDisplayMetrics().density;
            strokeWidthPx = Math.round(1.5f * density);
            float cornerR  = 50f * density;   // pill shape

            expiryStateBg = new GradientDrawable();
            expiryStateBg.setColor(0x206ED26B);   // default fresh; updated per-bind
            expiryStateBg.setStroke(strokeWidthPx, 0xFF6ED26B);
            expiryStateBg.setCornerRadius(cornerR);
            if (tvExpiryState != null) tvExpiryState.setBackground(expiryStateBg);

            tagOpenedBg = new GradientDrawable();
            tagOpenedBg.setColor(0x20E685ED);     // ~12% pink fill
            tagOpenedBg.setStroke(strokeWidthPx, 0xFFE685ED);
            tagOpenedBg.setCornerRadius(cornerR);
            if (tvTagOpened != null) tvTagOpened.setBackground(tagOpenedBg);

            tagStorageBg = new GradientDrawable();
            tagStorageBg.setColor(0x200B9ED4);    // ~12% blue fill
            tagStorageBg.setStroke(strokeWidthPx, 0xFF0B9ED4);
            tagStorageBg.setCornerRadius(cornerR);
            if (tvTagStorage != null) tvTagStorage.setBackground(tagStorageBg);

            tagSharedBg = new GradientDrawable();
            tagSharedBg.setColor(0x2026A69A);     // ~12% teal fill
            tagSharedBg.setStroke(strokeWidthPx, 0xFF26A69A);
            tagSharedBg.setCornerRadius(cornerR);
            if (tvTagShared != null) tvTagShared.setBackground(tagSharedBg);

            cbSelect.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                productList.get(pos).isSelected = cbSelect.isChecked();
            });

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                Product clicked = productList.get(pos);
                if (selectMode) {
                    clicked.isSelected = !clicked.isSelected;
                    cbSelect.setChecked(clicked.isSelected);
                } else if (listener != null) {
                    listener.onItemClick(clicked, pos);
                }
            });

            if (progressContainer != null) {
                progressContainer.setOnClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return;
                    Product p = productList.get(pos);
                    boolean nowVisible;
                    if (percentVisibleProducts.contains(p)) {
                        percentVisibleProducts.remove(p);
                        nowVisible = false;
                    } else {
                        percentVisibleProducts.add(p);
                        nowVisible = true;
                    }
                    if (tvProgressBadge != null) {
                        tvProgressBadge.setVisibility(nowVisible ? View.VISIBLE : View.GONE);
                    }
                });
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_product, parent, false);

        // Clip all children (including the accent strip) to the card's 20dp rounded outline,
        // so the accent strip corners fuse exactly with the card's corner shape.
        view.setClipToOutline(true);
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                float r = v.getResources().getDisplayMetrics().density * 20;
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r);
            }
        });

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        Product product = productList.get(position);
        Context context = holder.itemView.getContext();

        holder.itemView.setTranslationX(0f);
        holder.tvName.setText(product.name);

        String category = "";
        if (product.tags != null && !product.tags.isEmpty()) {
            category = product.tags.get(0);
        }

        // Glide: async decode on background thread, memory+disk cache, auto-cancel on recycle
        if (product.imageUri != null) {
            Glide.with(holder.itemView)
                 .load(product.imageUri)
                 .placeholder(R.drawable.profile_placeholder)
                 .error(R.drawable.profile_placeholder)
                 .centerCrop()
                 .into(holder.imgItem);
        } else {
            Glide.with(holder.itemView).clear(holder.imgItem);
            holder.imgItem.setImageResource(R.drawable.profile_placeholder);
        }

        DisplayBindResult bindResult = bindDisplayPrefs(holder, product, category);

        // Always show a status badge: Fresh / Near expiry / Expired!
        // Each badge uses a light tint fill + full-opacity colored border matching the text.
        int expiryState = bindResult.expiryState;
        if (expiryState == 2) {
            holder.tvExpiryState.setText("Expired!");
            holder.tvExpiryState.setTextColor(0xFFFC2200);
            if (holder.expiryStateBg != null) {
                holder.expiryStateBg.setColor(0x20FC2200);
                holder.expiryStateBg.setStroke(holder.strokeWidthPx, 0xFFFC2200);
            }
            holder.tvExpiryState.setVisibility(View.VISIBLE);
        } else if (expiryState == 1) {
            holder.tvExpiryState.setText("Near expiry");
            holder.tvExpiryState.setTextColor(0xFFE58333);
            if (holder.expiryStateBg != null) {
                holder.expiryStateBg.setColor(0x20E58333);
                holder.expiryStateBg.setStroke(holder.strokeWidthPx, 0xFFE58333);
            }
            holder.tvExpiryState.setVisibility(View.VISIBLE);
        } else {
            holder.tvExpiryState.setText("Fresh");
            holder.tvExpiryState.setTextColor(0xFF6ED26B);
            if (holder.expiryStateBg != null) {
                holder.expiryStateBg.setColor(0x206ED26B);
                holder.expiryStateBg.setStroke(holder.strokeWidthPx, 0xFF6ED26B);
            }
            holder.tvExpiryState.setVisibility(View.VISIBLE);
        }

        applyProgressUi(holder, product, bindResult.percent, expiryState);
        bindStorageOpenedTags(holder, product);

        holder.cbSelect.setVisibility(selectMode ? View.VISIBLE : View.GONE);
        holder.cbSelect.setChecked(product.isSelected);
        syncBottomRowIndent(holder);

    }

    private void syncBottomRowIndent(@NonNull ViewHolder holder) {
        if (holder.bottomRow == null) return;
        if (!selectMode || holder.cbSelect == null) {
            holder.bottomRow.setPaddingRelative(
                    holder.bottomRowBasePaddingStart,
                    holder.bottomRowBasePaddingTop,
                    holder.bottomRowBasePaddingEnd,
                    holder.bottomRowBasePaddingBottom);
            return;
        }
        if (cachedCheckboxWidth > 0) {
            holder.bottomRow.setPaddingRelative(
                    holder.bottomRowBasePaddingStart + cachedCheckboxWidth,
                    holder.bottomRowBasePaddingTop,
                    holder.bottomRowBasePaddingEnd,
                    holder.bottomRowBasePaddingBottom);
        } else {
            // Only post for the very first item; subsequent items use the cached value
            holder.itemView.post(() -> {
                int w = holder.cbSelect.getWidth();
                if (w > 0) cachedCheckboxWidth = w;
                holder.bottomRow.setPaddingRelative(
                        holder.bottomRowBasePaddingStart + w,
                        holder.bottomRowBasePaddingTop,
                        holder.bottomRowBasePaddingEnd,
                        holder.bottomRowBasePaddingBottom);
            });
        }
    }

    private void bindStorageOpenedTags(@NonNull ViewHolder holder, @NonNull Product product) {
        Context context = holder.itemView.getContext();
        boolean fromOther = product.sharedByUser != null
                && !product.sharedByUser.equals(bindCurrentUser);

        boolean showAny = false;

        if (holder.tvTagOpened != null) {
            if (product.isOpened == null) {
                holder.tvTagOpened.setVisibility(View.GONE);
            } else {
                holder.tvTagOpened.setText(Boolean.TRUE.equals(product.isOpened) ? "Opened" : "Unopened");
                holder.tvTagOpened.setVisibility(View.VISIBLE);
                showAny = true;
            }
        }

        if (holder.tvTagStorage != null) {
            if (product.storageState == null || product.storageState.trim().isEmpty()) {
                holder.tvTagStorage.setVisibility(View.GONE);
            } else {
                holder.tvTagStorage.setText(normalizeStorageLabel(product.storageState));
                holder.tvTagStorage.setVisibility(View.VISIBLE);
                showAny = true;
            }
        }

        if (holder.tvTagShared != null) {
            if (product.isShared && !fromOther) {
                holder.tvTagShared.setVisibility(View.VISIBLE);
                showAny = true;
            } else {
                holder.tvTagShared.setVisibility(View.GONE);
            }
        }

        // "From [name]" tag and left accent strip for items shared by others
        if (holder.tvTagFromUser != null) {
            if (fromOther) {
                String raw = (product.sharedByDisplayName != null && !product.sharedByDisplayName.isEmpty())
                        ? product.sharedByDisplayName : product.sharedByUser;
                String label = (raw != null && raw.length() > 6) ? raw.substring(0, 6) + "…" : raw;
                holder.tvTagFromUser.setText("👤 " + label);
                holder.tvTagFromUser.setVisibility(View.VISIBLE);
                showAny = true;
            } else {
                holder.tvTagFromUser.setVisibility(View.GONE);
            }
        }

        if (holder.viewFromAccent != null) {
            if (fromOther) {
                if (holder.fromAccentBg == null) {
                    float r = context.getResources().getDisplayMetrics().density * 20;
                    holder.fromAccentBg = new GradientDrawable(
                            GradientDrawable.Orientation.TOP_BOTTOM,
                            new int[]{0xFFFFBB68, 0xFFFFA040, 0xFFFFBB68});
                    holder.fromAccentBg.setCornerRadii(new float[]{r, r, 0, 0, 0, 0, r, r});
                    holder.viewFromAccent.setBackground(holder.fromAccentBg);
                }
                holder.viewFromAccent.setVisibility(View.VISIBLE);
            } else {
                holder.viewFromAccent.setVisibility(View.GONE);
            }
        }

        if (holder.tagsContainer != null) {
            holder.tagsContainer.setVisibility(showAny ? View.VISIBLE : View.GONE);
        }
    }

    private static final class DisplayBindResult {
        final int percent;
        final int expiryState;

        DisplayBindResult(int percent, int expiryState) {
            this.percent = percent;
            this.expiryState = expiryState;
        }
    }

    private DisplayBindResult bindDisplayPrefs(@NonNull ViewHolder holder, @NonNull Product product, @NonNull String category) {
        // Use bind-cycle cache — no SharedPreferences or DB reads here
        LocalDate today  = bindToday;
        LocalDate expiry = product.expireDate != null ? product.expireDate : today;
        LocalDate target = (dispType == 1) ? expiry.minusDays(1) : expiry;

        holder.tvSubInfo.setText(category + " · Remaining " + product.quantity);

        long remainingDays    = ChronoUnit.DAYS.between(today, target);
        long remainingToExpiry= ChronoUnit.DAYS.between(today, expiry);

        holder.tvTargetDate.setText(
                (dispType == 1 ? "Near-expiry date " : "Expiry date ")
                + formatDate(target, dispDateFmt, dispShowTime == 0));

        int shelfDays = calcShelfDays(product, dispIncludeProdDay);
        int percent   = calcPercent(remainingToExpiry, shelfDays);

        if (holder.viewProgress != null) {
            // GPU-only scale from left edge — no layout pass, no measure/relayout
            holder.viewProgress.setScaleX(Math.max(0f, Math.min(1f, percent / 100f)));
        }

        String rightPrimary;
        if (dispPriority == 1) {
            rightPrimary = String.valueOf(product.quantity);
        } else {
            rightPrimary = formatRemainingByStyle(remainingDays, dispStyle, target, dispDateFmt, dispShowTime == 0);
        }
        holder.tvRightPrimary.setText(rightPrimary);

        int expiryState = getExpiryStateFromCache(product);
        return new DisplayBindResult(percent, expiryState);
    }

    /** Inline expiry-state check using bind-cycle cache — no SharedPreferences reads. */
    private int getExpiryStateFromCache(Product product) {
        if (product == null || product.expireDate == null) return 0;
        if (product.expireDate.isBefore(bindToday)) return 2;
        if (!nearExpiryEnabled) return 0;
        int daysToExpire = (int) ChronoUnit.DAYS.between(bindToday, product.expireDate);
        return daysToExpire <= ProductRepository.thresholdDaysByCategory(product) ? 1 : 0;
    }

    private void applyProgressUi(@NonNull ViewHolder holder, @NonNull Product product, int percent, int expiryState) {
        if (holder.viewProgress != null) {
            int color;
            if (percent < 10) {
                color = 0xFFFC2200;
            } else if (expiryState == 1) {
                color = 0xFFF2C94C;
            } else {
                color = 0xFF6ED26B;
            }
            holder.viewProgress.setBackgroundColor(color);

            if (holder.tvProgressBadge != null) {
                holder.tvProgressBadge.setText(percent + "%");

                if (holder.progressBadgeBg == null) {
                    holder.progressBadgeBg = new GradientDrawable();
                    holder.progressBadgeBg.setCornerRadius(dpToPx(holder.itemView, 6));
                    holder.tvProgressBadge.setBackground(holder.progressBadgeBg);
                }
                holder.progressBadgeBg.setColor(color);
                holder.tvProgressBadge.setTextColor(percent < 10 ? 0xFFFFFFFF : 0xFF514438);
            }
        }

        boolean isVisible = percentVisibleProducts.contains(product);
        if (holder.tvProgressBadge != null) {
            holder.tvProgressBadge.setVisibility(isVisible ? View.VISIBLE : View.GONE);
        }

    }

    private static float dpToPx(@NonNull View view, float dp) {
        return dp * view.getResources().getDisplayMetrics().density;
    }

    /**
     * Calculates the total shelf life in days from production/created date to expiry.
     * Adding 1 when includeProdDay=true counts the production date itself as a full day.
     */
    private static int calcShelfDays(Product product, boolean includeProdDay) {
        LocalDate start = product.productionDate != null ? product.productionDate : product.createdDate;
        LocalDate end = product.expireDate;
        if (start == null || end == null) return 0;
        long days = ChronoUnit.DAYS.between(start, end);
        if (includeProdDay) days += 1;
        return (int) Math.max(0, days);
    }

    /** Returns freshness as a percentage (0–100) of shelf life remaining. */
    private static int calcPercent(long remainingDays, int shelfDays) {
        if (shelfDays <= 0) return 0;
        long clamped = Math.max(0, Math.min(remainingDays, shelfDays));
        double ratio = (double) clamped / (double) shelfDays;
        return (int) Math.round(ratio * 100.0);
    }

    private static String formatRemainingByStyle(long remainingDays, int style, LocalDate targetDate, int dateFmt, boolean showHm) {
        if (remainingDays < 0) {
            if (style == 2) {
                return formatDate(targetDate, dateFmt, showHm);
            }
            return "Expired";
        }

        if (style == 1) {
            return remainingDays + "d";
        }
        if (style == 2) {
            return formatDate(targetDate, dateFmt, showHm);
        }

        if (remainingDays >= 365) {
            long years = remainingDays / 365;
            return years + "y";
        }
        if (remainingDays >= 30) {
            long months = remainingDays / 30;
            return months + "m";
        }
        return remainingDays + "d";
    }

    private static final DateTimeFormatter[] DATE_FORMATTERS = {
        DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.getDefault()),
        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault()),
        DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.getDefault()),
        DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH),
    };

    private static String formatDate(LocalDate date, int dateFmt, boolean showHm) {
        if (date == null) return "";
        int idx = (dateFmt >= 0 && dateFmt < DATE_FORMATTERS.length) ? dateFmt : 0;
        return date.format(DATE_FORMATTERS[idx]);
    }

    /**
     * Translates legacy Chinese storage state strings to their English display equivalents.
     * Modern entries are already in English; this handles data entered before the locale change.
     */
    private static String normalizeStorageLabel(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if ("\u5e38\u6e29".equals(value)) return "Room temp";
        if ("\u51b7\u85cf".equals(value)) return "Chilled";
        if ("\u51b7\u51bb".equals(value)) return "Frozen";
        return value;
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        Glide.with(holder.imgItem.getContext()).clear(holder.imgItem);
    }

    @Override
    public int getItemCount() {
        return productList.size();
    }

    public void updateList(List<Product> newList) {
        this.productList = newList;
        notifyDataSetChanged();
    }

    public void setSelectMode(boolean selectMode) {
        this.selectMode = selectMode;
        cachedCheckboxWidth = 0;
        notifyDataSetChanged();
    }

    public boolean isSelectMode() {
        return selectMode;
    }
}
