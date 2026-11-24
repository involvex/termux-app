package com.invapp.shared.android;

import android.app.PendingIntent;
import android.os.Build;

/**
 * PendingIntent mutability helpers for targetSdk 31+ (S).
 */
public final class PendingIntentUtils {

    private PendingIntentUtils() {}

    /** OR {@link PendingIntent#FLAG_IMMUTABLE} on API 23+. */
    public static int immutable(int flags) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return flags | PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    /**
     * OR {@link PendingIntent#FLAG_MUTABLE} on API 31+ (required when the
     * intent must accept fill-in extras, e.g. widget templates).
     */
    public static int mutable(int flags) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return flags | PendingIntent.FLAG_MUTABLE;
        }
        return flags;
    }
}
