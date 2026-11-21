package com.comp9003.foodtracker.reminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.Calendar;

/**
 * Manages the daily AlarmManager reminder for expiring food items.
 * Call {@link #apply(Context)} whenever reminder settings change or on app startup.
 * Uses setInexactRepeating (battery-friendly) with RTC_WAKEUP so the device wakes up to fire.
 */
public final class ReminderScheduler {
    private static final String PREFS_NAME = "reminder_prefs";
    private static final String KEY_DAILY_ENABLED = "daily_enabled";
    private static final String KEY_DAILY_TIME_MINUTES = "daily_time_minutes";

    private ReminderScheduler() {
    }

    /** Reads the current reminder settings and either schedules or cancels the alarm. */
    public static void apply(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(KEY_DAILY_ENABLED, true);
        if (!enabled) {
            cancel(app);
            return;
        }

        int minutes = prefs.getInt(KEY_DAILY_TIME_MINUTES, 9 * 60); // default 09:00
        schedule(app, minutes);
    }

    /**
     * Schedules a repeating daily alarm at the given time-of-day.
     * If the target time has already passed today, the first trigger is moved to tomorrow.
     */
    private static void schedule(Context context, int minutesOfDay) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        PendingIntent pi = pendingIntent(context);

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.set(Calendar.HOUR_OF_DAY, minutesOfDay / 60);
        cal.set(Calendar.MINUTE, minutesOfDay % 60);

        long now = System.currentTimeMillis();
        long triggerAt = cal.getTimeInMillis();
        // If today's fire time is already past, move the first trigger to tomorrow
        if (triggerAt <= now) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
            triggerAt = cal.getTimeInMillis();
        }

        alarmManager.cancel(pi); // remove any existing alarm before re-registering
        alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                AlarmManager.INTERVAL_DAY,
                pi
        );
    }

    private static void cancel(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        alarmManager.cancel(pendingIntent(context));
    }

    private static PendingIntent pendingIntent(Context context) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, 9001, intent, flags);
    }
}
