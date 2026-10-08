package com.invapp.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * Cold-start prefs ported from Termux-Ultra {@code LaunchPrefs}.
 *
 * <p>Keys match Ultra ({@code launch_page}, {@code auto_start_console}) but the
 * file is isolated ({@code invapp_launch}) like our other {@code invapp_*}
 * prefs. Defaults preserve current behavior: {@code TERMINAL} + no forced
 * session (Ultra defaults to {@code OVERVIEW}).
 *
 * <p>InVx mapping (no Compose tabs here): {@code OVERVIEW} opens the left
 * drawer on cold start; {@code TERMINAL} keeps the current direct-terminal
 * start. {@code auto_start_console} forces a fresh session instead of
 * restoring the stored/last one (guarded by not-recreated + not RUN shortcut
 * in {@code TermuxActivity}).
 */
public final class LaunchPrefs {

    /** Same SharedPreferences key names as Ultra. */
    public static final String KEY_LAUNCH_PAGE = "launch_page";
    public static final String KEY_AUTO_START_CONSOLE = "auto_start_console";

    private static final String PREFS = "invapp_launch";

    /** Mirrors Ultra {@code LaunchPage} (ordinal-persisted). */
    public enum LaunchPage {
        OVERVIEW,
        TERMINAL;

        public int storedValue() {
            return ordinal();
        }

        /** Out-of-range ordinals fall back to {@link #TERMINAL}. */
        @NonNull
        public static LaunchPage fromStored(int stored) {
            LaunchPage[] values = values();
            if (stored < 0 || stored >= values.length) {
                return TERMINAL;
            }
            return values[stored];
        }
    }

    private LaunchPrefs() {}

    /** Pure logic: Ultra parity — auto-start locks the effective page to overview. */
    @NonNull
    public static LaunchPage effectiveLaunchPage(int storedOrdinal, boolean autoStartConsole) {
        if (autoStartConsole) {
            return LaunchPage.OVERVIEW;
        }
        return LaunchPage.fromStored(storedOrdinal);
    }

    /**
     * Pure logic for {@code TermuxActivity.onServiceConnected}: force a new
     * session only on cold start with the toggle on, existing sessions present,
     * and not a launcher RUN-shortcut intent.
     */
    public static boolean shouldForceNewSession(boolean activityRecreated,
                                                boolean autoStartConsole,
                                                boolean hasExistingSessions,
                                                boolean isRunShortcut) {
        return !activityRecreated && autoStartConsole && hasExistingSessions && !isRunShortcut;
    }

    @NonNull
    public static LaunchPage getLaunchPage(@NonNull Context context) {
        return LaunchPage.fromStored(prefs(context).getInt(KEY_LAUNCH_PAGE,
            LaunchPage.TERMINAL.storedValue()));
    }

    public static void setLaunchPage(@NonNull Context context, @NonNull LaunchPage page) {
        prefs(context).edit().putInt(KEY_LAUNCH_PAGE, page.storedValue()).apply();
    }

    /** Effective page honoring the auto-start lock (no disk write-back). */
    @NonNull
    public static LaunchPage effectiveLaunchPage(@NonNull Context context) {
        SharedPreferences prefs = prefs(context);
        return effectiveLaunchPage(
            prefs.getInt(KEY_LAUNCH_PAGE, LaunchPage.TERMINAL.storedValue()),
            prefs.getBoolean(KEY_AUTO_START_CONSOLE, false));
    }

    public static boolean isAutoStartConsole(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_AUTO_START_CONSOLE, false);
    }

    public static void setAutoStartConsole(@NonNull Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_AUTO_START_CONSOLE, enabled).apply();
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
