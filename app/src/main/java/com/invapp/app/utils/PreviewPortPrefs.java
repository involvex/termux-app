package com.invapp.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Preference-backed preferred / recent / starred localhost Preview ports.
 */
public final class PreviewPortPrefs {

    /** Default preferred ports (Vite / Expo / OpenCode / common frameworks). */
    public static final int[] DEFAULT_PREFERRED = {
        4096, 3000, 3001, 4173, 5000, 5173, 8000, 8080, 8081, 8888, 19000, 19006
    };

    private static final String PREFS = "invapp_preview_ports";
    private static final String KEY_PREFERRED = "preferred_ports";
    private static final String KEY_RECENT = "recent_ports";
    private static final String KEY_STARRED = "starred_ports";
    private static final String SEP = ",";
    private static final int MAX_RECENT = 8;
    private static final int MAX_STARRED = 16;

    private PreviewPortPrefs() {}

    @NonNull
    public static List<Integer> defaultPreferredList() {
        List<Integer> out = new ArrayList<>(DEFAULT_PREFERRED.length);
        for (int p : DEFAULT_PREFERRED) {
            out.add(p);
        }
        return out;
    }

    @NonNull
    public static String defaultPreferredCsv() {
        return portsToCsv(defaultPreferredList());
    }

    @NonNull
    public static List<Integer> getPreferredPorts(@NonNull Context context) {
        SharedPreferences prefs = prefs(context);
        String raw = prefs.getString(KEY_PREFERRED, null);
        if (raw == null || raw.trim().isEmpty()) {
            return defaultPreferredList();
        }
        List<Integer> parsed = parsePortsCsv(raw);
        return parsed.isEmpty() ? defaultPreferredList() : parsed;
    }

    @NonNull
    public static String getPreferredPortsCsv(@NonNull Context context) {
        SharedPreferences prefs = prefs(context);
        String raw = prefs.getString(KEY_PREFERRED, null);
        if (raw == null || raw.trim().isEmpty()) {
            return defaultPreferredCsv();
        }
        List<Integer> parsed = parsePortsCsv(raw);
        return parsed.isEmpty() ? defaultPreferredCsv() : portsToCsv(parsed);
    }

    public static void setPreferredPortsCsv(@NonNull Context context, @Nullable String csv) {
        List<Integer> parsed = parsePortsCsv(csv);
        if (parsed.isEmpty()) {
            prefs(context).edit().remove(KEY_PREFERRED).apply();
            return;
        }
        prefs(context).edit().putString(KEY_PREFERRED, portsToCsv(parsed)).apply();
    }

    public static boolean isPreferred(@NonNull Context context, int port) {
        return indexOf(getPreferredPorts(context), port) >= 0;
    }

    @NonNull
    public static List<Integer> getRecent(@NonNull Context context) {
        return readPortList(context, KEY_RECENT, MAX_RECENT);
    }

    public static void addRecent(@NonNull Context context, int port) {
        if (port < 1 || port > 65535) {
            return;
        }
        LinkedHashSet<Integer> set = new LinkedHashSet<>();
        set.add(port);
        for (Integer p : getRecent(context)) {
            if (set.size() >= MAX_RECENT) {
                break;
            }
            set.add(p);
        }
        writePortList(context, KEY_RECENT, new ArrayList<>(set), MAX_RECENT);
    }

    public static void forgetRecent(@NonNull Context context, int port) {
        List<Integer> recent = getRecent(context);
        if (!recent.remove(Integer.valueOf(port))) {
            return;
        }
        writePortList(context, KEY_RECENT, recent, MAX_RECENT);
    }

    @NonNull
    public static List<Integer> getStarred(@NonNull Context context) {
        return readPortList(context, KEY_STARRED, MAX_STARRED);
    }

    public static boolean isStarred(@NonNull Context context, int port) {
        return getStarred(context).contains(port);
    }

    public static void toggleStar(@NonNull Context context, int port) {
        if (port < 1 || port > 65535) {
            return;
        }
        List<Integer> starred = new ArrayList<>(getStarred(context));
        if (starred.contains(port)) {
            starred.remove(Integer.valueOf(port));
        } else {
            starred.add(0, port);
            while (starred.size() > MAX_STARRED) {
                starred.remove(starred.size() - 1);
            }
        }
        writePortList(context, KEY_STARRED, starred, MAX_STARRED);
    }

    public static void forgetStar(@NonNull Context context, int port) {
        List<Integer> starred = getStarred(context);
        if (!starred.remove(Integer.valueOf(port))) {
            return;
        }
        writePortList(context, KEY_STARRED, starred, MAX_STARRED);
    }

    /** Remove from recent and starred. */
    public static void forgetPort(@NonNull Context context, int port) {
        forgetRecent(context, port);
        forgetStar(context, port);
    }

    /**
     * Chip order: starred → listening preferred → other listening → recent (deduped).
     */
    @NonNull
    public static List<Integer> orderChips(@NonNull Context context,
                                           @NonNull List<Integer> listening) {
        Set<Integer> listenSet = new LinkedHashSet<>(listening);
        LinkedHashSet<Integer> out = new LinkedHashSet<>();
        List<Integer> preferred = getPreferredPorts(context);

        for (Integer p : getStarred(context)) {
            out.add(p);
        }
        for (Integer p : preferred) {
            if (listenSet.contains(p)) {
                out.add(p);
            }
        }
        List<Integer> rest = new ArrayList<>();
        for (Integer p : listening) {
            if (!out.contains(p)) {
                rest.add(p);
            }
        }
        Collections.sort(rest, LocalhostPortScanner.preferredFirstComparator(preferred));
        out.addAll(rest);
        for (Integer p : getRecent(context)) {
            out.add(p);
        }
        return new ArrayList<>(out);
    }

    @VisibleForTesting
    @NonNull
    public static List<Integer> parsePortsCsv(@Nullable String csv) {
        if (csv == null || csv.trim().isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<Integer> seen = new LinkedHashSet<>();
        for (String part : csv.split("[,;\\s]+")) {
            String t = part.trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                int port = Integer.parseInt(t);
                if (port >= 1 && port <= 65535) {
                    seen.add(port);
                }
            } catch (NumberFormatException ignored) {
                // skip invalid tokens
            }
        }
        return new ArrayList<>(seen);
    }

    @NonNull
    public static String portsToCsv(@NonNull List<Integer> ports) {
        StringBuilder sb = new StringBuilder();
        for (Integer p : ports) {
            if (p == null || p < 1 || p > 65535) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(SEP);
            }
            sb.append(p);
        }
        return sb.toString();
    }

    private static int indexOf(@NonNull List<Integer> list, int port) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == port) {
                return i;
            }
        }
        return -1;
    }

    @NonNull
    private static List<Integer> readPortList(@NonNull Context context, @NonNull String key,
                                              int max) {
        String raw = prefs(context).getString(key, null);
        List<Integer> parsed = parsePortsCsv(raw);
        if (parsed.size() > max) {
            return new ArrayList<>(parsed.subList(0, max));
        }
        return parsed;
    }

    private static void writePortList(@NonNull Context context, @NonNull String key,
                                      @NonNull List<Integer> ports, int max) {
        List<Integer> trimmed = ports.size() > max
            ? new ArrayList<>(ports.subList(0, max)) : ports;
        prefs(context).edit().putString(key, portsToCsv(trimmed)).apply();
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Debug helper for tests — not used in production UI. */
    @VisibleForTesting
    static void clearAll(@NonNull Context context) {
        prefs(context).edit().clear().apply();
    }
}
