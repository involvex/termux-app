package com.invapp.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * First-launch checklist prefs (FEAT-018). Skip-able and re-runnable.
 */
public final class OnboardingPrefs {

    public static final String PREFS = "invapp_onboarding";
    public static final String KEY_COMPLETED = "completed";
    public static final String KEY_SKIPPED = "skipped";
    public static final String KEY_STEP = "step";

    public static final int STEP_STORAGE = 0;
    public static final int STEP_REPOS = 1;
    public static final int STEP_PREVIEW = 2;
    public static final int STEP_AI = 3;
    public static final int STEP_COUNT = 4;

    private OnboardingPrefs() {}

    public static boolean shouldShowOnColdStart(@NonNull Context context) {
        SharedPreferences prefs = prefs(context);
        return !prefs.getBoolean(KEY_COMPLETED, false)
            && !prefs.getBoolean(KEY_SKIPPED, false);
    }

    public static boolean isFinished(@NonNull Context context) {
        SharedPreferences prefs = prefs(context);
        return prefs.getBoolean(KEY_COMPLETED, false)
            || prefs.getBoolean(KEY_SKIPPED, false);
    }

    public static int getStep(@NonNull Context context) {
        int step = prefs(context).getInt(KEY_STEP, STEP_STORAGE);
        if (step < 0 || step >= STEP_COUNT) {
            return STEP_STORAGE;
        }
        return step;
    }

    public static void setStep(@NonNull Context context, int step) {
        int clamped = Math.max(0, Math.min(STEP_COUNT - 1, step));
        prefs(context).edit().putInt(KEY_STEP, clamped).apply();
    }

    public static void markCompleted(@NonNull Context context) {
        prefs(context).edit()
            .putBoolean(KEY_COMPLETED, true)
            .putBoolean(KEY_SKIPPED, false)
            .putInt(KEY_STEP, STEP_COUNT - 1)
            .apply();
    }

    public static void markSkipped(@NonNull Context context) {
        prefs(context).edit()
            .putBoolean(KEY_SKIPPED, true)
            .apply();
    }

    /** Reset so Help / Settings can re-run the wizard. */
    public static void reset(@NonNull Context context) {
        prefs(context).edit()
            .putBoolean(KEY_COMPLETED, false)
            .putBoolean(KEY_SKIPPED, false)
            .putInt(KEY_STEP, STEP_STORAGE)
            .apply();
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
