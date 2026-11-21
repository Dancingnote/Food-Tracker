package com.comp9003.foodtracker;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.CheckBox;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DisplayPreferencesActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "display_prefs";

    private static final String KEY_PRIORITY_CONTENT = "priority_content";
    private static final String KEY_REMAINING_STYLE = "remaining_style";
    private static final String KEY_REMAINING_TYPE = "remaining_type";
    private static final String KEY_SHOW_TIME = "show_time";
    private static final String KEY_DATE_FORMAT = "date_format";
    private static final String KEY_INCLUDE_PROD_DAY = "include_prod_day";

    private static final int PRIORITY_REMAINING_TIME = 0;
    private static final int PRIORITY_REMAINING_STOCK = 1;

    private static final int STYLE_DYNAMIC = 0;
    private static final int STYLE_DAYS_ONLY = 1;
    private static final int STYLE_TARGET_DATE = 2;

    private static final int TYPE_BY_EXPIRY = 0;
    private static final int TYPE_BY_NEAR_EXPIRY = 1;

    private static final int SHOW_TIME_WITH_HM = 0;
    private static final int SHOW_TIME_DATE_ONLY = 1;

    private static final int DATE_FMT_DOT = 0;
    private static final int DATE_FMT_SLASH = 1;
    private static final int DATE_FMT_DASH = 2;
    private static final int DATE_FMT_CN = 3;

    private SharedPreferences prefs;

    private TextView tvSampleRightPrimary;
    private TextView tvSampleSubInfo;
    private TextView tvSampleTargetDate;
    private TextView tvSampleName;
    private View viewSampleProgress;
    private TextView tvSampleExpiryState;
    private TextView tvSampleProgressBadge;
    private CheckBox cbSampleSelect;
    private View sampleTagsContainer;
    private TextView tvSampleTagOpened;
    private TextView tvSampleTagStorage;
    private ImageView ivSampleImage;

    private RadioGroup rgPriority;
    private RadioGroup rgRemainingStyle;
    private RadioGroup rgRemainingType;
    private RadioGroup rgDateFormat;
    private RadioGroup rgIncludeProdDay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_display_preferences);

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

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        tvSampleRightPrimary = findViewById(R.id.tv_right_primary);
        tvSampleSubInfo = findViewById(R.id.tv_sub_info);
        tvSampleTargetDate = findViewById(R.id.tv_target_date);
        tvSampleName = findViewById(R.id.tv_name);
        ivSampleImage = findViewById(R.id.img_item);
        viewSampleProgress = findViewById(R.id.view_progress);
        tvSampleExpiryState = findViewById(R.id.tv_expiry_state);
        tvSampleProgressBadge = findViewById(R.id.tv_progress_badge);
        cbSampleSelect = findViewById(R.id.cb_select);
        sampleTagsContainer = findViewById(R.id.tags_container);
        tvSampleTagOpened = findViewById(R.id.tv_tag_opened);
        tvSampleTagStorage = findViewById(R.id.tv_tag_storage);

        if (tvSampleName != null) tvSampleName.setText("Fresh milk");
        if (tvSampleExpiryState != null) tvSampleExpiryState.setVisibility(View.GONE);
        if (tvSampleProgressBadge != null) tvSampleProgressBadge.setVisibility(View.GONE);
        if (cbSampleSelect != null) cbSampleSelect.setVisibility(View.GONE);
        if (sampleTagsContainer != null) sampleTagsContainer.setVisibility(View.VISIBLE);
        if (tvSampleTagOpened != null) {
            tvSampleTagOpened.setText("Unopened");
            tvSampleTagOpened.setVisibility(View.VISIBLE);
        }
        if (tvSampleTagStorage != null) {
            tvSampleTagStorage.setText("Chilled");
            tvSampleTagStorage.setVisibility(View.VISIBLE);
        }

        rgPriority = findViewById(R.id.rgPriority);
        rgRemainingStyle = findViewById(R.id.rgRemainingStyle);
        rgRemainingType = findViewById(R.id.rgRemainingType);
        rgDateFormat = findViewById(R.id.rgDateFormat);
        rgIncludeProdDay = findViewById(R.id.rgIncludeProdDay);

        bindRadioGroups();
        restoreSelections();
        updateSample();
    }

    private void bindRadioGroups() {
        rgPriority.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbPriorityRemainingTime) {
                prefs.edit().putInt(KEY_PRIORITY_CONTENT, PRIORITY_REMAINING_TIME).apply();
            } else if (checkedId == R.id.rbPriorityRemainingStock) {
                prefs.edit().putInt(KEY_PRIORITY_CONTENT, PRIORITY_REMAINING_STOCK).apply();
            }
            updateSample();
        });

        rgRemainingStyle.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbStyleDynamic) {
                prefs.edit().putInt(KEY_REMAINING_STYLE, STYLE_DYNAMIC).apply();
            } else if (checkedId == R.id.rbStyleDaysOnly) {
                prefs.edit().putInt(KEY_REMAINING_STYLE, STYLE_DAYS_ONLY).apply();
            } else if (checkedId == R.id.rbStyleTargetDate) {
                prefs.edit().putInt(KEY_REMAINING_STYLE, STYLE_TARGET_DATE).apply();
            }
            updateSample();
        });

        rgRemainingType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbTypeExpiry) {
                prefs.edit().putInt(KEY_REMAINING_TYPE, TYPE_BY_EXPIRY).apply();
            } else if (checkedId == R.id.rbTypeNearExpiry) {
                prefs.edit().putInt(KEY_REMAINING_TYPE, TYPE_BY_NEAR_EXPIRY).apply();
            }
            updateSample();
        });

        rgDateFormat.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbDateFmtDot) {
                prefs.edit().putInt(KEY_DATE_FORMAT, DATE_FMT_DOT).apply();
            } else if (checkedId == R.id.rbDateFmtSlash) {
                prefs.edit().putInt(KEY_DATE_FORMAT, DATE_FMT_SLASH).apply();
            } else if (checkedId == R.id.rbDateFmtDash) {
                prefs.edit().putInt(KEY_DATE_FORMAT, DATE_FMT_DASH).apply();
            } else if (checkedId == R.id.rbDateFmtCn) {
                prefs.edit().putInt(KEY_DATE_FORMAT, DATE_FMT_CN).apply();
            }
            updateSample();
        });

        rgIncludeProdDay.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbIncludeProdYes) {
                prefs.edit().putBoolean(KEY_INCLUDE_PROD_DAY, true).apply();
            } else if (checkedId == R.id.rbIncludeProdNo) {
                prefs.edit().putBoolean(KEY_INCLUDE_PROD_DAY, false).apply();
            }
            updateSample();
        });
    }

    private void restoreSelections() {
        int priority = prefs.getInt(KEY_PRIORITY_CONTENT, PRIORITY_REMAINING_TIME);
        rgPriority.check(priority == PRIORITY_REMAINING_STOCK ? R.id.rbPriorityRemainingStock : R.id.rbPriorityRemainingTime);

        int style = prefs.getInt(KEY_REMAINING_STYLE, STYLE_DYNAMIC);
        if (style == STYLE_DAYS_ONLY) {
            rgRemainingStyle.check(R.id.rbStyleDaysOnly);
        } else if (style == STYLE_TARGET_DATE) {
            rgRemainingStyle.check(R.id.rbStyleTargetDate);
        } else {
            rgRemainingStyle.check(R.id.rbStyleDynamic);
        }

        int type = prefs.getInt(KEY_REMAINING_TYPE, TYPE_BY_EXPIRY);
        rgRemainingType.check(type == TYPE_BY_NEAR_EXPIRY ? R.id.rbTypeNearExpiry : R.id.rbTypeExpiry);

        int dateFmt = prefs.getInt(KEY_DATE_FORMAT, DATE_FMT_DOT);
        if (dateFmt == DATE_FMT_SLASH) {
            rgDateFormat.check(R.id.rbDateFmtSlash);
        } else if (dateFmt == DATE_FMT_DASH) {
            rgDateFormat.check(R.id.rbDateFmtDash);
        } else if (dateFmt == DATE_FMT_CN) {
            rgDateFormat.check(R.id.rbDateFmtCn);
        } else {
            rgDateFormat.check(R.id.rbDateFmtDot);
        }

        boolean includeProd = prefs.getBoolean(KEY_INCLUDE_PROD_DAY, true);
        rgIncludeProdDay.check(includeProd ? R.id.rbIncludeProdYes : R.id.rbIncludeProdNo);
    }

    private void updateSample() {
        int priority = prefs.getInt(KEY_PRIORITY_CONTENT, PRIORITY_REMAINING_TIME);
        int style = prefs.getInt(KEY_REMAINING_STYLE, STYLE_DYNAMIC);
        int type = prefs.getInt(KEY_REMAINING_TYPE, TYPE_BY_EXPIRY);
        int dateFmt = prefs.getInt(KEY_DATE_FORMAT, DATE_FMT_DOT);
        boolean includeProdDay = prefs.getBoolean(KEY_INCLUDE_PROD_DAY, true);

        int sampleStock = 10;
        int shelfDays = 45;

        Date today = startOfDay(new Date());
        Date productionDate = today;
        Date expiryDate = addDays(today, 31);
        Date nearExpiryDate = addDays(expiryDate, -1);

        Date targetDate = (type == TYPE_BY_NEAR_EXPIRY) ? nearExpiryDate : expiryDate;
        long remainingDays = daysBetween(today, startOfDay(targetDate));
        long remainingToExpiry = daysBetween(today, startOfDay(expiryDate));
        int percent = calcPercent(remainingToExpiry, shelfDays);

        String formattedTargetDate = formatDate(targetDate, dateFmt, false);
        String targetLabel = (type == TYPE_BY_NEAR_EXPIRY) ? "Near-expiry date " : "Expiry date ";
        tvSampleTargetDate.setText(targetLabel + formattedTargetDate);

        String rightPrimary;
        if (priority == PRIORITY_REMAINING_STOCK) {
            rightPrimary = String.valueOf(sampleStock);
        } else {
            rightPrimary = formatRemainingByStyle(remainingDays, style, targetDate, dateFmt, false);
        }
        tvSampleRightPrimary.setText(rightPrimary);

        if (priority == PRIORITY_REMAINING_STOCK) {
            tvSampleSubInfo.setText("Food · Remaining " + sampleStock);
        } else {
            tvSampleSubInfo.setText("Food · Remaining " + sampleStock);
        }

        if (viewSampleProgress != null) {
            int color = percent < 10 ? 0xFFFC2200 : 0xFF6ED26B;
            viewSampleProgress.setBackgroundColor(color);
            viewSampleProgress.post(() -> {
                View parent = (viewSampleProgress.getParent() instanceof View) ? (View) viewSampleProgress.getParent() : null;
                int full = parent != null ? parent.getWidth() : 0;
                if (full <= 0) return;
                int barWidth = Math.max(0, Math.min(full, (int) Math.round(full * (percent / 100.0))));
                ViewGroup.LayoutParams lp = viewSampleProgress.getLayoutParams();
                lp.width = barWidth;
                viewSampleProgress.setLayoutParams(lp);
            });
        }
    }

    private String formatRemainingByStyle(long remainingDays, int style, Date targetDate, int dateFmt, boolean showHm) {
        if (remainingDays < 0) {
            if (style == STYLE_TARGET_DATE) {
                return formatDate(targetDate, dateFmt, showHm);
            }
            return "Expired";
        }

        if (style == STYLE_DAYS_ONLY) {
            return remainingDays + "d";
        }

        if (style == STYLE_TARGET_DATE) {
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

    private int calcPercent(long remainingDays, int shelfDays) {
        if (shelfDays <= 0) return 0;
        long clamped = Math.max(0, Math.min(remainingDays, shelfDays));
        double ratio = (double) clamped / (double) shelfDays;
        return (int) Math.round(ratio * 100.0);
    }

    private String formatDate(Date date, int dateFmt, boolean showHm) {
        String datePattern;
        Locale locale = Locale.getDefault();
        if (dateFmt == DATE_FMT_SLASH) {
            datePattern = "dd/MM/yyyy";
        } else if (dateFmt == DATE_FMT_DASH) {
            datePattern = "dd-MM-yyyy";
        } else if (dateFmt == DATE_FMT_CN) {
            datePattern = "dd MMM yyyy";
            locale = Locale.ENGLISH;
        } else {
            datePattern = "dd.MM.yyyy";
        }

        String pattern = showHm ? (datePattern + " HH:mm") : datePattern;
        SimpleDateFormat sdf = new SimpleDateFormat(pattern, locale);
        return sdf.format(date);
    }

    private Date startOfDay(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    private Date addDays(Date base, int days) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(base);
        cal.add(Calendar.DAY_OF_YEAR, days);
        return cal.getTime();
    }

    private long daysBetween(Date startInclusive, Date endInclusive) {
        long diffMillis = endInclusive.getTime() - startInclusive.getTime();
        return diffMillis / (24L * 60L * 60L * 1000L);
    }
}
