package com.invapp.app.utils;

import android.app.PendingIntent;

import com.invapp.shared.android.PendingIntentUtils;

/**
 * App-module facade over {@link PendingIntentUtils}.
 */
public final class PendingIntentFlags {

    private PendingIntentFlags() {}

    /** {@link PendingIntent#FLAG_UPDATE_CURRENT} plus IMMUTABLE on API 23+. */
    public static int mutableUpdate() {
        return PendingIntentUtils.immutable(PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Fresh immutable intent (no update). */
    public static int immutable() {
        return PendingIntentUtils.immutable(0);
    }
}
