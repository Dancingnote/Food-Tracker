package com.comp9003.foodtracker;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.LocaleList;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.comp9003.foodtracker.model.Product;
import com.comp9003.foodtracker.reminder.ReminderScheduler;

import java.time.LocalDate;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    /**
     * Forces the app locale to English regardless of the device language setting.
     * This ensures all UI strings (dates, labels, number formats) use English conventions.
     */
    @Override
    protected void attachBaseContext(Context newBase) {
        LocaleList localeList = LocaleList.forLanguageTags("en");
        Locale.setDefault(localeList.get(0));
        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        config.setLocales(localeList);
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    private static final int REQ_LOGIN = 1001;
    private View navHome;
    private View navStats;
    private View navFamily;
    private View navProfile;
    private TextView tvNavChatBadge;

    private static final String REMINDER_PREFS = "reminder_prefs";
    private static final String KEY_DAILY_ENABLED = "daily_enabled";
    private static final String KEY_PERSISTENT_ENABLED = "persistent_enabled";
    private static final String KEY_EXPIRE_RULE = "expire_rule";
    private static final String KEY_NEAR_EXPIRE_RULE = "near_expire_rule";

    private static final int RULE_DISABLED = 0;
    private static final int RULE_TODAY = 1;
    private static final int RULE_BEFORE_2 = 2;
    private static final int RULE_BEFORE_3 = 3;
    private static final int RULE_BEFORE_15 = 4;
    private static final int RULE_BEFORE_30 = 5;

    private static final String INAPP_STATE_PREFS = "inapp_reminder_state";
    private static final String KEY_INAPP_SNOOZE = "inapp_snooze";
    private View reminderBanner;
    private TextView reminderBannerText;
    private TextView reminderBannerAction;
    private float bannerTouchDownY;
    private float bannerStartTranslationY;
    private boolean bannerDragging;
    private boolean entryBannerShown = false;

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
        setContentView(R.layout.activity_main);

        ProductRepository.ensureReady(this);
        ReminderScheduler.apply(this);

        View mainView = findViewById(R.id.main);
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(
                        systemBars.left,
                        systemBars.top,
                        systemBars.right,
                        0
                );
                return insets;
            });
        }

        reminderBanner = findViewById(R.id.reminder_banner);
        reminderBannerText = findViewById(R.id.reminder_banner_text);
        reminderBannerAction = findViewById(R.id.reminder_banner_action);
        setupBannerGestures();
        bindBannerActions();

        if (!ProductRepository.isLoggedIn(this)) {
            startLoginFlow();
        } else if (savedInstanceState == null) {
            switchFragment(new HomeFragment());
        }

        navHome        = findViewById(R.id.nav_home_btn);
        navStats       = findViewById(R.id.nav_stats_btn);
        navFamily      = findViewById(R.id.nav_family_btn);
        navProfile     = findViewById(R.id.nav_profile_btn);
        tvNavChatBadge = findViewById(R.id.nav_chat_badge);

        if (navHome != null && navProfile != null) {
            setupCustomNavigation();
            syncBottomNavSelection();
        }

        View fabAdd = findViewById(R.id.fab_add);
        if (fabAdd != null) {
            fabAdd.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, AddProductActivity.class);
                startActivity(intent);
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        maybeShowInAppReminderOnce();
        syncBottomNavSelection();
        updateNavChatBadge();
    }

    /** Shows/hides the red dot badge on the bottom-nav Share button. */
    void updateNavChatBadge() {
        if (tvNavChatBadge == null) return;
        String user = ProductRepository.getCurrentUser(this);
        int unread = FamilyFragment.getUnreadCount(this, user);
        if (unread > 0) {
            tvNavChatBadge.setText(unread > 99 ? "99+" : String.valueOf(unread));
            tvNavChatBadge.setVisibility(View.VISIBLE);
        } else {
            tvNavChatBadge.setVisibility(View.GONE);
        }
    }

    /**
     * Shows the in-app reminder banner at most once per app launch.
     * Checks whether the user has already dismissed ("snoozed") the banner today before showing.
     */
    private void maybeShowInAppReminderOnce() {
        if (entryBannerShown) return;
        if (!ProductRepository.isLoggedIn(this)) return;
        ProductRepository.ensureReady(this);

        String user = ProductRepository.getCurrentUser(this);
        if (user == null) return;
        if (isSnoozedToday(user)) return;

        SharedPreferences prefs = getSharedPreferences(REMINDER_PREFS, MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_DAILY_ENABLED, true)) return;

        boolean persistent = prefs.getBoolean(KEY_PERSISTENT_ENABLED, false);
        int expireRule = prefs.getInt(KEY_EXPIRE_RULE, RULE_BEFORE_3);
        int nearRule = prefs.getInt(KEY_NEAR_EXPIRE_RULE, RULE_TODAY);

        int expireDays = ruleToDays(expireRule);
        int nearDays = ruleToDays(nearRule);

        LocalDate today = LocalDate.now();
        int expireCount = 0;
        int nearCount = 0;

        for (Product p : ProductRepository.products) {
            if (p == null) continue;
            if (p.isArchived) continue;
            if (p.expireDate == null) continue;

            if (expireDays > 0) {
                LocalDate remindDate = p.expireDate.minusDays(expireDays);
                if ((persistent && !today.isBefore(remindDate)) || today.equals(remindDate)) {
                    expireCount++;
                }
            }

            if (nearDays > 0) {
                int threshold = ProductRepository.thresholdDaysByCategory(p);
                LocalDate nearDate = p.expireDate.minusDays(threshold);
                LocalDate remindDate = nearDate.minusDays(nearDays);
                if ((persistent && !today.isBefore(remindDate)) || today.equals(remindDate)) {
                    nearCount++;
                }
            }
        }

        String message;
        if (nearCount > 0 && expireCount > 0) {
            message = "You have " + nearCount + " items entering near-expiry soon and " + expireCount + " items expiring soon. Please handle them promptly 🍊";
        } else if (nearCount > 0) {
            message = "You have " + nearCount + " items entering near-expiry soon. Please handle them promptly 🍊";
        } else if (expireCount > 0) {
            message = "You have " + expireCount + " items expiring soon. Please handle them promptly 🍊";
        } else {
            return;
        }

        showTopBanner(message);
        if (reminderBanner != null && reminderBanner.getVisibility() == View.VISIBLE) {
            entryBannerShown = true;
        }
    }

    private void bindBannerActions() {
        if (reminderBannerAction == null) return;
        reminderBannerAction.setOnClickListener(v -> {
            String user = ProductRepository.getCurrentUser(this);
            if (user != null) snoozeToday(user);
            hideTopBanner();
            entryBannerShown = true;
        });
    }

    private boolean isSnoozedToday(String user) {
        SharedPreferences sp = getSharedPreferences(INAPP_STATE_PREFS, MODE_PRIVATE);
        String today = LocalDate.now().toString();
        String key = user + ":" + KEY_INAPP_SNOOZE;
        return today.equals(sp.getString(key, null));
    }

    private void snoozeToday(String user) {
        SharedPreferences sp = getSharedPreferences(INAPP_STATE_PREFS, MODE_PRIVATE);
        String today = LocalDate.now().toString();
        String key = user + ":" + KEY_INAPP_SNOOZE;
        sp.edit().putString(key, today).apply();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    /**
     * Attaches a swipe-up-to-dismiss touch handler to the reminder banner.
     * Dragging up past a 36dp threshold triggers dismissal with a slide-out animation.
     */
    private void setupBannerGestures() {
        if (reminderBanner == null) return;

        reminderBanner.setOnTouchListener((v, event) -> {
            if (reminderBanner.getVisibility() != View.VISIBLE) return false;
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    bannerTouchDownY = event.getRawY();
                    bannerStartTranslationY = reminderBanner.getTranslationY();
                    bannerDragging = true;
                    return true;
                case android.view.MotionEvent.ACTION_MOVE:
                    if (!bannerDragging) return false;
                    float dy = event.getRawY() - bannerTouchDownY;
                    float next = bannerStartTranslationY + dy;
                    if (next > 0) next = 0;
                    reminderBanner.setTranslationY(next);
                    float alpha = 1f - Math.min(1f, Math.abs(next) / (float) Math.max(1, reminderBanner.getHeight()));
                    reminderBanner.setAlpha(Math.max(0.2f, alpha));
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    if (!bannerDragging) return false;
                    bannerDragging = false;
                    float threshold = -dpToPx(36);
                    if (reminderBanner.getTranslationY() <= threshold) {
                        hideTopBanner();
                    } else {
                        reminderBanner.animate()
                                .translationY(0f)
                                .alpha(1f)
                                .setDuration(160)
                                .start();
                    }
                    return true;
                default:
                    return false;
            }
        });
    }

    private void showTopBanner(String message) {
        if (reminderBanner == null || reminderBannerText == null) return;
        reminderBannerText.setText(message);
        reminderBanner.setVisibility(View.VISIBLE);
        reminderBanner.setAlpha(1f);

        reminderBanner.post(() -> {
            float start = -reminderBanner.getHeight();
            if (start >= 0) start = -dpToPx(64);
            if (reminderBanner.getTranslationY() == 0f) {
                reminderBanner.setTranslationY(start);
            }
            reminderBanner.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(220)
                    .start();
        });
    }

    private void hideTopBanner() {
        if (reminderBanner == null) return;
        float end = -reminderBanner.getHeight();
        if (end >= 0) end = -dpToPx(64);
        reminderBanner.animate()
                .translationY(end)
                .alpha(0f)
                .setDuration(180)
                .withEndAction(() -> {
                    reminderBanner.setVisibility(View.GONE);
                    reminderBanner.setAlpha(1f);
                    reminderBanner.setTranslationY(0f);
                })
                .start();
    }

    private static int ruleToDays(int rule) {
        if (rule == RULE_TODAY) return 1;
        if (rule == RULE_BEFORE_2) return 2;
        if (rule == RULE_BEFORE_3) return 3;
        if (rule == RULE_BEFORE_15) return 15;
        if (rule == RULE_BEFORE_30) return 30;
        return 0;
    }

    private void setupCustomNavigation() {
        navHome.setOnClickListener(v -> {
            Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (current instanceof HomeFragment) {
                ((HomeFragment) current).requestClearSearch();
            } else {
                HomeFragment home = new HomeFragment();
                home.requestClearSearch();
                switchFragment(home);
            }
            setSelectedTab(NavTab.HOME);
        });

        if (navStats != null) {
            navStats.setOnClickListener(v -> {
                Fragment cur = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                if (!(cur instanceof StatsFragment)) switchFragment(new StatsFragment());
                setSelectedTab(NavTab.STATS);
            });
        }

        if (navFamily != null) {
            navFamily.setOnClickListener(v -> {
                Fragment cur = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                if (!(cur instanceof FamilyFragment)) switchFragment(new FamilyFragment());
                setSelectedTab(NavTab.FAMILY);
            });
        }

        navProfile.setOnClickListener(v -> {
            Fragment cur = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (!(cur instanceof ProfileFragment)) switchFragment(new ProfileFragment());
            setSelectedTab(NavTab.PROFILE);
        });
    }

    private enum NavTab { HOME, STATS, FAMILY, PROFILE }

    private void switchFragment(@NonNull Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
        setSelectedTab(fragmentToTab(fragment));
    }

    private void syncBottomNavSelection() {
        if (navHome == null || navProfile == null) return;
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        setSelectedTab(fragmentToTab(current));
    }

    private NavTab fragmentToTab(Fragment f) {
        if (f instanceof ProfileFragment) return NavTab.PROFILE;
        if (f instanceof FamilyFragment)  return NavTab.FAMILY;
        if (f instanceof StatsFragment)   return NavTab.STATS;
        return NavTab.HOME;
    }

    private void setSelectedTab(NavTab tab) {
        if (navHome    != null) navHome.setSelected(tab == NavTab.HOME);
        if (navStats   != null) navStats.setSelected(tab == NavTab.STATS);
        if (navFamily  != null) navFamily.setSelected(tab == NavTab.FAMILY);
        if (navProfile != null) navProfile.setSelected(tab == NavTab.PROFILE);
        updateNavChatBadge();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != REQ_LOGIN) return;

        if (!ProductRepository.isLoggedIn(this)) {
            finish();
            return;
        }

        ProductRepository.loadCurrentUserProducts(this);
        entryBannerShown = false;

        View bottomNavCard = findViewById(R.id.bottom_nav_card);
        if (bottomNavCard != null) bottomNavCard.setVisibility(View.VISIBLE);

        View fabAdd = findViewById(R.id.fab_add);
        if (fabAdd != null) fabAdd.setVisibility(View.VISIBLE);

        Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (!(current instanceof HomeFragment)) {
            switchFragment(new HomeFragment());
        }
        setSelectedTab(NavTab.HOME);
    }

    public void startLoginFlow() {
        View bottomNavCard = findViewById(R.id.bottom_nav_card);
        if (bottomNavCard != null) bottomNavCard.setVisibility(View.GONE);

        View fabAdd = findViewById(R.id.fab_add);
        if (fabAdd != null) fabAdd.setVisibility(View.GONE);

        startActivityForResult(new Intent(this, LoginActivity.class), REQ_LOGIN);
    }
}
