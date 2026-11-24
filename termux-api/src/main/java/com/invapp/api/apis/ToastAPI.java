package com.invapp.api.apis;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.invapp.api.util.ResultReturner;
import com.invapp.shared.logger.Logger;

import java.io.PrintWriter;

/**
 * {@code termux-toast}. On Android 11+ ({@code targetSdk}/{@code SDK} 30+),
 * {@link Toast#getView()} is {@code null} for text toasts created with
 * {@link Toast#makeText}, so tinting the background must be skipped — otherwise
 * background widget scripts crash Termux:API with NPE on {@code getBackground()}.
 */
public class ToastAPI {

    private static final String LOG_TAG = "ToastAPI";

    public static void onReceive(final Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        final int durationExtra = intent.getBooleanExtra("short", false) ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG;
        final int backgroundColor = getColorExtra(intent, "background", Color.GRAY);
        final int textColor = getColorExtra(intent, "text_color", Color.WHITE);
        final int gravity = getGravityExtra(intent);

        final Handler handler = new Handler(Looper.getMainLooper());
        final Context appContext = context.getApplicationContext();

        ResultReturner.returnData(context, intent, new ResultReturner.WithStringInput() {
            @Override
            public void writeResult(PrintWriter out) {
                handler.post(() -> showToast(appContext, inputString, durationExtra,
                    backgroundColor, textColor, gravity));
            }
        });
    }

    /**
     * Show a toast; apply custom colors/gravity only when the platform still
     * exposes a toast view (pre-Android 11 text toasts).
     */
    static void showToast(Context context, String text, int duration,
                          int backgroundColor, int textColor, int gravity) {
        try {
            Toast toast = Toast.makeText(context, text == null ? "" : text, duration);
            View toastView = toast.getView();
            if (toastView != null) {
                Drawable background = toastView.getBackground();
                if (background != null) {
                    background.setTint(backgroundColor);
                }
                TextView textView = toastView.findViewById(android.R.id.message);
                if (textView != null) {
                    textView.setTextColor(textColor);
                }
                toast.setGravity(gravity, 0, 0);
            }
            toast.show();
        } catch (RuntimeException e) {
            // Never take down Termux:API for a toast (background restrictions, bad token, …).
            Logger.logWarn(LOG_TAG, "Failed to show toast: " + e.getMessage());
        }
    }

    protected static int getColorExtra(Intent intent, String extra, int defaultColor) {
        int color = defaultColor;

        if (intent.hasExtra(extra)) {
            String colorExtra = intent.getStringExtra(extra);

            try {
                color = Color.parseColor(colorExtra);
            } catch (IllegalArgumentException e) {
                Logger.logError(LOG_TAG, String.format("Failed to parse color '%s' for '%s'", colorExtra, extra));
            }
        }
        return color;
    }

    protected static int getGravityExtra(Intent intent) {
        String extraGravity = intent.getStringExtra("gravity");

        switch (extraGravity == null ? "" : extraGravity) {
            case "top": return Gravity.TOP;
            case "middle": return Gravity.CENTER;
            case "bottom": return Gravity.BOTTOM;
            default: return Gravity.CENTER;
        }
    }

}
