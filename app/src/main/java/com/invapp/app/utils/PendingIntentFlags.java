package com.invapp.app.utils;

import android.app.PendingIntent;
import android.os.Build;

/**
 * Immutable PendingIntent flags required when targeting API 31+.
 */
public final class PendingIntentFlags {

    private PendingIntentFlags() {}

    /** {@link PendingIntent#FLAG_UPDATE_CURRENT} plus IMMUTABLE on API 23+. */
    public static int mutableUpdate() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    /** Fresh immutable intent (no update). */
    public static int immutable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return PendingIntent.FLAG_IMMUTABLE;
        }
        return 0;
    }
}
