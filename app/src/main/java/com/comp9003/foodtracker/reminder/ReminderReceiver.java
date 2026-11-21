package com.comp9003.foodtracker.reminder;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.comp9003.foodtracker.MainActivity;
import com.comp9003.foodtracker.ProductRepository;
import com.comp9003.foodtracker.R;
import com.comp9003.foodtracker.model.Product;

import java.time.LocalDate;

/**
 * BroadcastReceiver triggered by the daily AlarmManager alarm.
 * Scans the current user's product list and fires notifications for items that are
 * expiring soon or entering near-expiry, based on user-configured reminder rules.
 */
public class ReminderReceiver extends BroadcastReceiver {
    private static final String PREFS_NAME = "reminder_prefs";
    private static final String KEY_DAILY_ENABLED = "daily_enabled";
    private static final String KEY_RINGTONE = "ringtone";
    /** When true, the reminder fires every day once the threshold is crossed (not only on the exact day). */
    private static final String KEY_PERSISTENT_ENABLED = "persistent_enabled";
    private static final String KEY_EXPIRE_RULE = "expire_rule";
    private static final String KEY_NEAR_EXPIRE_RULE = "near_expire_rule";

    // Ringtone mode constants
    private static final int RINGTONE_FOLLOW_SYSTEM = 0; // silent if system is on silent
    private static final int RINGTONE_DEFAULT = 1;
    private static final int RINGTONE_BELL = 2;

    // Reminder rule constants — each maps to a specific days-ahead offset via ruleToDays()
    private static final int RULE_DISABLED = 0;
    private static final int RULE_TODAY = 1;      // remind on the exact day
    private static final int RULE_BEFORE_2 = 2;   // remind 2 days before
    private static final int RULE_BEFORE_3 = 3;
    private static final int RULE_BEFORE_15 = 4;
    private static final int RULE_BEFORE_30 = 5;

    private static final String CHANNEL_ID = "foodtracker_reminders";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null) return;

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_DAILY_ENABLED, true)) return;

        String user = ProductRepository.getCurrentUser(context);
        if (user == null) return;

        ProductRepository.ensureReady(context);

        int expireRule = prefs.getInt(KEY_EXPIRE_RULE, RULE_BEFORE_3);
        int nearRule = prefs.getInt(KEY_NEAR_EXPIRE_RULE, RULE_TODAY);
        boolean persistent = prefs.getBoolean(KEY_PERSISTENT_ENABLED, false);
        int ringtone = prefs.getInt(KEY_RINGTONE, RINGTONE_FOLLOW_SYSTEM);

        LocalDate today = LocalDate.now();

        int expireCount = 0;
        int nearCount = 0;

        int expireDays = ruleToDays(expireRule);
        int nearDays = ruleToDays(nearRule);

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

        if (expireCount <= 0 && nearCount <= 0) return;

        ensureChannel(context);
        NotificationManagerCompat nm = NotificationManagerCompat.from(context);

        if (expireCount > 0) {
            String msg = buildMessage(persistent, expireDays, expireCount, true);
            nm.notify(2001, buildNotification(context, ringtone, msg));
        }
        if (nearCount > 0) {
            String msg = buildMessage(persistent, nearDays, nearCount, false);
            nm.notify(2002, buildNotification(context, ringtone, msg));
        }
    }

    /** Converts a RULE_* constant to the corresponding number of look-ahead days (0 = disabled). */
    private static int ruleToDays(int rule) {
        if (rule == RULE_TODAY) return 1;
        if (rule == RULE_BEFORE_2) return 2;
        if (rule == RULE_BEFORE_3) return 3;
        if (rule == RULE_BEFORE_15) return 15;
        if (rule == RULE_BEFORE_30) return 30;
        return 0;
    }

    private static String buildMessage(boolean persistent, int ruleDays, int count, boolean expired) {
        String prefix;
        if (persistent || ruleDays <= 1) {
            prefix = "Today";
        } else {
            prefix = "In " + ruleDays + " days";
        }
        String type = expired ? "expiring soon" : "entering near-expiry soon";
        return prefix + " you have " + count + " items " + type + ". Please handle them promptly 🌹";
    }

    private static NotificationCompat.Builder baseBuilder(Context context, int ringtone) {
        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent contentIntent = PendingIntent.getActivity(context, 9100, openIntent, piFlags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        Uri sound = soundUri(ringtone);
        if (sound != null) builder.setSound(sound);
        return builder;
    }

    private static android.app.Notification buildNotification(Context context, int ringtone, String message) {
        return baseBuilder(context, ringtone)
                .setContentTitle("FoodTracker")
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .build();
    }

    private static Uri soundUri(int ringtone) {
        if (ringtone == RINGTONE_DEFAULT || ringtone == RINGTONE_BELL) {
            return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }
        return null;
    }

    private static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT);
        nm.createNotificationChannel(ch);
    }

}
