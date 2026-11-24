package com.invapp.app.terminal.io;

import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.invapp.app.TermuxActivity;

/**
 * Keep ExtraKeysView visible above the IME in fullscreen by applying
 * {@link WindowInsetsCompat} IME + system-bar padding (replaces the old
 * global-layout height hack).
 */
public final class FullScreenWorkAround {

    private FullScreenWorkAround() {}

    public static void apply(TermuxActivity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) {
            return;
        }
        View child = content.getChildAt(0);
        final int initialLeft = child.getPaddingLeft();
        final int initialTop = child.getPaddingTop();
        final int initialRight = child.getPaddingRight();
        final int initialBottom = child.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(child, (v, insets) -> {
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int bottom = Math.max(ime.bottom, bars.bottom);
            v.setPadding(initialLeft + bars.left, initialTop + bars.top,
                initialRight + bars.right, initialBottom + bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(child);
    }
}
