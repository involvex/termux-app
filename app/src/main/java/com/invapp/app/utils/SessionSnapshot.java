package com.invapp.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Persist terminal session cwd/title across process death (phantom kill).
 *
 * <p>Restores fresh shells in the same directories — no pty/scrollback replay.
 * Stored under {@code invapp_session_snapshot}. Cap matches
 * {@code TermuxTerminalSessionActivityClient} ({@link #MAX_SESSIONS}).
 */
public final class SessionSnapshot {

    public static final String PREFS = "invapp_session_snapshot";
    public static final String KEY_SESSIONS = "sessions_json";
    public static final int MAX_SESSIONS = 8;

    private static final String JSON_CWD = "cwd";
    private static final String JSON_TITLE = "title";

    /** One restored shell: working directory + optional display title. */
    public static final class Entry {
        @NonNull
        public final String cwd;
        @Nullable
        public final String title;

        public Entry(@NonNull String cwd, @Nullable String title) {
            this.cwd = cwd;
            this.title = title;
        }
    }

    private SessionSnapshot() {}

    /**
     * Offer restore only on cold start with no live sessions, a non-empty
     * snapshot, and no RUN / failsafe / auto-start override.
     */
    public static boolean shouldOfferRestore(boolean activityRecreated,
                                             boolean hasLiveSessions,
                                             boolean hasSnapshot,
                                             boolean isRunShortcut,
                                             boolean autoStartConsole,
                                             boolean isFailsafe) {
        if (activityRecreated) {
            return false;
        }
        if (hasLiveSessions) {
            return false;
        }
        if (!hasSnapshot) {
            return false;
        }
        if (isRunShortcut) {
            return false;
        }
        if (autoStartConsole) {
            return false;
        }
        if (isFailsafe) {
            return false;
        }
        return true;
    }

    /**
     * Keep non-empty cwd under {@code homePath} (app home). Rejects
     * {@code ..} traversal and paths outside home.
     */
    @Nullable
    public static String sanitizeCwd(@Nullable String cwd, @NonNull String homePath) {
        if (cwd == null) {
            return null;
        }
        String trimmed = cwd.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.contains("\0") || trimmed.contains("..")) {
            return null;
        }
        String home = stripTrailingSlash(homePath);
        if (home.isEmpty()) {
            return null;
        }
        String path = stripTrailingSlash(trimmed);
        if (path.equals(home)) {
            return path;
        }
        if (path.startsWith(home + "/")) {
            return path;
        }
        return null;
    }

    /**
     * Sanitize, dedupe by cwd (first wins), and cap at {@code maxSessions}.
     */
    @NonNull
    public static List<Entry> sanitizeEntries(@Nullable List<Entry> raw,
                                              @NonNull String homePath,
                                              int maxSessions) {
        if (raw == null || raw.isEmpty() || maxSessions <= 0) {
            return Collections.emptyList();
        }
        List<Entry> out = new ArrayList<>(Math.min(raw.size(), maxSessions));
        for (Entry entry : raw) {
            if (entry == null) {
                continue;
            }
            String cwd = sanitizeCwd(entry.cwd, homePath);
            if (cwd == null) {
                continue;
            }
            if (containsCwd(out, cwd)) {
                continue;
            }
            String title = entry.title;
            if (title != null) {
                title = title.trim();
                if (title.isEmpty()) {
                    title = null;
                }
            }
            out.add(new Entry(cwd, title));
            if (out.size() >= maxSessions) {
                break;
            }
        }
        return out;
    }

    /** Load and sanitize entries under the given home path. */
    @NonNull
    public static List<Entry> load(@NonNull Context context, @NonNull String homePath) {
        String json = prefs(context).getString(KEY_SESSIONS, null);
        return sanitizeEntries(parseJson(json), homePath, MAX_SESSIONS);
    }

    /** Persist sanitized entries; clears the key when empty. */
    public static void save(@NonNull Context context,
                            @Nullable List<Entry> entries,
                            @NonNull String homePath) {
        List<Entry> clean = sanitizeEntries(entries, homePath, MAX_SESSIONS);
        SharedPreferences.Editor editor = prefs(context).edit();
        if (clean.isEmpty()) {
            editor.remove(KEY_SESSIONS);
        } else {
            editor.putString(KEY_SESSIONS, toJson(clean));
        }
        editor.apply();
    }

    public static void clear(@NonNull Context context) {
        prefs(context).edit().remove(KEY_SESSIONS).apply();
    }

    public static boolean hasSnapshot(@NonNull Context context, @NonNull String homePath) {
        return !load(context, homePath).isEmpty();
    }

    /**
     * Minimal JSON array codec (no {@code org.json} — keeps unit tests off
     * the Android stub). Schema: {@code [{"cwd":"...","title":"..."}, ...]}.
     */
    @VisibleForTesting
    @NonNull
    static List<Entry> parseJson(@Nullable String json) {
        if (json == null) {
            return Collections.emptyList();
        }
        String trimmed = json.trim();
        if (trimmed.isEmpty() || trimmed.charAt(0) != '[') {
            return Collections.emptyList();
        }
        List<Entry> out = new ArrayList<>();
        int i = 1;
        int n = trimmed.length();
        while (i < n) {
            char c = trimmed.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == ',') {
                i++;
                continue;
            }
            if (c == ']') {
                break;
            }
            if (c != '{') {
                return Collections.emptyList();
            }
            int objEnd = findMatchingBrace(trimmed, i);
            if (objEnd < 0) {
                return Collections.emptyList();
            }
            String obj = trimmed.substring(i, objEnd + 1);
            String cwd = readJsonStringField(obj, JSON_CWD);
            if (cwd != null && !cwd.isEmpty()) {
                String title = readJsonStringField(obj, JSON_TITLE);
                if (title != null && title.isEmpty()) {
                    title = null;
                }
                out.add(new Entry(cwd, title));
            }
            i = objEnd + 1;
        }
        return out;
    }

    @VisibleForTesting
    @NonNull
    static String toJson(@NonNull List<Entry> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        boolean first = true;
        for (Entry entry : entries) {
            if (entry == null || entry.cwd == null) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('{');
            sb.append('"').append(JSON_CWD).append("\":");
            appendJsonString(sb, entry.cwd);
            if (entry.title != null) {
                sb.append(",\"").append(JSON_TITLE).append("\":");
                appendJsonString(sb, entry.title);
            }
            sb.append('}');
        }
        sb.append(']');
        return sb.toString();
    }

    private static int findMatchingBrace(@NonNull String s, int openIdx) {
        boolean inString = false;
        boolean escape = false;
        int depth = 0;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Nullable
    private static String readJsonStringField(@NonNull String obj, @NonNull String key) {
        String needle = "\"" + key + "\"";
        int keyIdx = obj.indexOf(needle);
        if (keyIdx < 0) {
            return null;
        }
        int colon = obj.indexOf(':', keyIdx + needle.length());
        if (colon < 0) {
            return null;
        }
        int i = colon + 1;
        while (i < obj.length() && Character.isWhitespace(obj.charAt(i))) {
            i++;
        }
        if (i >= obj.length() || obj.charAt(i) != '"') {
            return null;
        }
        i++;
        StringBuilder value = new StringBuilder();
        boolean escape = false;
        while (i < obj.length()) {
            char c = obj.charAt(i);
            if (escape) {
                if (c == 'n') {
                    value.append('\n');
                } else if (c == 't') {
                    value.append('\t');
                } else if (c == 'r') {
                    value.append('\r');
                } else if (c == '"' || c == '\\' || c == '/') {
                    value.append(c);
                } else {
                    value.append(c);
                }
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else if (c == '"') {
                return value.toString();
            } else {
                value.append(c);
            }
            i++;
        }
        return null;
    }

    private static void appendJsonString(@NonNull StringBuilder sb, @NonNull String value) {
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\').append(c);
            } else if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\r') {
                sb.append("\\r");
            } else if (c == '\t') {
                sb.append("\\t");
            } else {
                sb.append(c);
            }
        }
        sb.append('"');
    }

    private static boolean containsCwd(@NonNull List<Entry> entries, @NonNull String cwd) {
        for (Entry e : entries) {
            if (cwd.equals(e.cwd)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private static String stripTrailingSlash(@NonNull String path) {
        if (path.length() > 1 && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
