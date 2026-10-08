package com.invapp.app.utils;

import android.content.Context;

import androidx.annotation.NonNull;

import com.invapp.shared.termux.TermuxConstants;
import com.invapp.shared.termux.TermuxUtils;

import java.io.File;

/**
 * Integration flags for the separate API / Widget plugin APKs (v1: these two
 * only).
 *
 * <p>Adapted from Termux-Ultra {@code IntegratedTools}: Ultra vendors the
 * plugins into the main app and toggles components via
 * {@code PackageManager.setComponentEnabledSetting()}. Our plugins are
 * separate APKs sharing {@code sharedUserId}, so a component toggle from the
 * main app is not applicable — instead this tracks an explicit enable flag
 * per tool plus live install status, and gates app-side integration behavior
 * (widget refresh broadcast). Keys match Ultra
 * ({@code tool_termux_api}, {@code tool_termux_widget}, default off) but the
 * file is isolated ({@code invapp_integrated_tools}).
 */
public final class IntegratedTools {

    private static final String PREFS = "invapp_integrated_tools";

    /** v1 tool set. */
    public enum Tool {
        API("tool_termux_api"),
        WIDGET("tool_termux_widget");

        private final String prefKey;

        Tool(@NonNull String prefKey) {
            this.prefKey = prefKey;
        }

        @NonNull
        public String prefKey() {
            return prefKey;
        }

        /** Plugin APK package name (never hardcoded elsewhere). */
        @NonNull
        public String packageName() {
            switch (this) {
                case API:
                    return TermuxConstants.TERMUX_API_PACKAGE_NAME;
                case WIDGET:
                    return TermuxConstants.TERMUX_WIDGET_PACKAGE_NAME;
                default:
                    throw new IllegalStateException(name());
            }
        }
    }

    /** User-visible integration state. */
    public enum Status {
        /** Flag off — integration behavior gated, regardless of install state. */
        DISABLED,
        /** Flag on + plugin APK present (+ API CLI present for API). */
        READY,
        /** Flag on but plugin APK missing (or signature-mismatched). */
        MISSING_APK,
        /** Flag on, API APK present, but {@code pkg install termux-api} missing. */
        MISSING_CLI
    }

    private IntegratedTools() {}

    /** All v1 tools in display order. */
    @NonNull
    public static Tool[] allTools() {
        return new Tool[] {Tool.API, Tool.WIDGET};
    }

    /** Pure mapping for tests and summaries. */
    @NonNull
    public static Status statusFor(boolean enabled, boolean apkInstalled, boolean cliPresent) {
        if (!enabled) {
            return Status.DISABLED;
        }
        if (!apkInstalled) {
            return Status.MISSING_APK;
        }
        if (!cliPresent) {
            return Status.MISSING_CLI;
        }
        return Status.READY;
    }

    public static boolean isEnabled(@NonNull Context context, @NonNull Tool tool) {
        return prefs(context).getBoolean(tool.prefKey(), false);
    }

    public static void setEnabled(@NonNull Context context, @NonNull Tool tool, boolean enabled) {
        prefs(context).edit().putBoolean(tool.prefKey(), enabled).apply();
    }

    /** True when at least one v1 tool flag is on (controls config entry). */
    public static boolean anyEnabled(@NonNull Context context) {
        for (Tool tool : allTools()) {
            if (isEnabled(context, tool)) {
                return true;
            }
        }
        return false;
    }

    /** Plugin APK resolvable under our sharedUserId (null when missing/mismatched). */
    public static boolean isPluginInstalled(@NonNull Context context, @NonNull Tool tool) {
        switch (tool) {
            case API:
                return TermuxUtils.getTermuxAPIPackageContext(context) != null;
            case WIDGET:
                return TermuxUtils.getTermuxWidgetPackageContext(context) != null;
            default:
                throw new IllegalStateException(tool.name());
        }
    }

    /** Shell CLIs from {@code pkg install termux-api} (API only; Widget needs none). */
    public static boolean isCliPresent(@NonNull Tool tool) {
        if (tool != Tool.API) {
            return true;
        }
        return new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH,
            "termux-clipboard-get").isFile();
    }

    /** Live status combining flag + install state. */
    @NonNull
    public static Status statusOf(@NonNull Context context, @NonNull Tool tool) {
        return statusFor(isEnabled(context, tool),
            isPluginInstalled(context, tool), isCliPresent(tool));
    }

    /** Compact one-line status for the parent settings row. */
    @NonNull
    public static String statusSummary(@NonNull Context context) {
        StringBuilder sb = new StringBuilder();
        for (Tool tool : allTools()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(tool == Tool.API ? "API" : "Widget");
            sb.append(": ");
            switch (statusOf(context, tool)) {
                case READY:
                    sb.append("ready");
                    break;
                case MISSING_APK:
                    sb.append("APK missing");
                    break;
                case MISSING_CLI:
                    sb.append("needs pkg");
                    break;
                case DISABLED:
                    sb.append("off");
                    break;
            }
        }
        return sb.toString();
    }

    @NonNull
    private static android.content.SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
