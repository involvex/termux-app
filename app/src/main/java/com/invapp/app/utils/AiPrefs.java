package com.invapp.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * User prefs for the AI Doctor Settings screen.
 *
 * <p>Host / version are applied as shell env prefixes on {@code td-ai} /
 * {@code opencode-setup} injects — not baked into every new session env.
 */
public final class AiPrefs {

    public static final String PREFS = "invapp_ai";
    public static final String KEY_OPENCODE_HOST = "opencode_host";
    public static final String KEY_OPENCODE_VERSION = "opencode_version";

    /** Explicit LAN bind for {@code td-ai} / OpenCode service. */
    public static final String HOST_WILDCARD = "0.0.0.0";
    /** Default bind: loopback-only (opt in to LAN via Settings / OPENCODE_HOST). */
    public static final String HOST_LOOPBACK = "127.0.0.1";

    private AiPrefs() {}

    @NonNull
    public static String getOpenCodeHost(@NonNull Context context) {
        // Default loopback when the key was never set; a stored 0.0.0.0 stays.
        String host = prefs(context).getString(KEY_OPENCODE_HOST, HOST_LOOPBACK);
        if (HOST_WILDCARD.equals(host)) {
            return HOST_WILDCARD;
        }
        return HOST_LOOPBACK;
    }

    public static void setOpenCodeHost(@NonNull Context context, @NonNull String host) {
        String normalized = HOST_LOOPBACK.equals(host) ? HOST_LOOPBACK : HOST_WILDCARD;
        prefs(context).edit().putString(KEY_OPENCODE_HOST, normalized).apply();
    }

    public static boolean isLoopbackHost(@NonNull Context context) {
        return HOST_LOOPBACK.equals(getOpenCodeHost(context));
    }

    /**
     * Optional pin for {@code OPENCODE_VERSION}; empty means live lookup.
     */
    @NonNull
    public static String getOpenCodeVersionPin(@NonNull Context context) {
        String v = prefs(context).getString(KEY_OPENCODE_VERSION, "");
        return v != null ? v.trim() : "";
    }

    public static void setOpenCodeVersionPin(@NonNull Context context, @Nullable String version) {
        String v = version != null ? version.trim() : "";
        prefs(context).edit().putString(KEY_OPENCODE_VERSION, v).apply();
    }

    /**
     * {@code OPENCODE_HOST=… [OPENCODE_VERSION=…] opencode-setup\n}
     */
    @NonNull
    public static String buildRepairCommand(@NonNull Context context) {
        return envPrefix(context) + WorkflowHelper.CMD_OPENCODE_SETUP;
    }

    /**
     * {@code OPENCODE_HOST=… td-ai\n}
     */
    @NonNull
    public static String buildTdAiCommand(@NonNull Context context) {
        return "OPENCODE_HOST=" + shellSingleQuote(getOpenCodeHost(context))
            + " " + WorkflowHelper.CMD_TD_AI;
    }

    @NonNull
    private static String envPrefix(@NonNull Context context) {
        StringBuilder sb = new StringBuilder();
        sb.append("OPENCODE_HOST=").append(shellSingleQuote(getOpenCodeHost(context))).append(' ');
        String pin = getOpenCodeVersionPin(context);
        if (!pin.isEmpty()) {
            sb.append("OPENCODE_VERSION=").append(shellSingleQuote(pin)).append(' ');
        }
        return sb.toString();
    }

    @NonNull
    private static String shellSingleQuote(@NonNull String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
