package com.invapp.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.invapp.shared.errors.Error;
import com.invapp.shared.file.FileUtils;
import com.invapp.shared.logger.Logger;
import com.invapp.shared.termux.TermuxConstants;
import com.invapp.shared.termux.settings.properties.TermuxPropertyConstants;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Preference-backed page-2 extra-keys catalog and {@code termux.properties} writer.
 *
 * <p>Page 0 (nav/modifiers) stays fixed; page 2 ids are user-selectable.
 */
public final class ExtraKeysBarHelper {

    public static final String ID_PULL = "PULL";
    public static final String ID_BUNI = "BUNI";
    public static final String ID_DEV = "DEV";
    public static final String ID_REPOS = "REPOS";
    public static final String ID_CLONE = "CLONE";
    public static final String ID_AI = "AI";
    public static final String ID_NEW = "NEW";
    public static final String ID_DRAWER = "DRAWER";
    public static final String ID_DRAWER_RIGHT = "DRAWER_RIGHT";
    public static final String ID_KEYBOARD = "KEYBOARD";
    public static final String ID_PASTE = "PASTE";

    public static final String[] ALL_PAGE2_IDS = {
        ID_PULL, ID_BUNI, ID_DEV, ID_REPOS, ID_CLONE, ID_AI, ID_NEW,
        ID_DRAWER, ID_DRAWER_RIGHT, ID_KEYBOARD, ID_PASTE
    };

    /** Display labels parallel to {@link #ALL_PAGE2_IDS}. */
    public static final String[] PAGE2_LABELS = {
        "pull", "bun i", "dev", "repos", "clone", "AI", "new",
        "drawer", "tools", "keyboard", "paste"
    };

    public static final String[] DEFAULT_PAGE2_IDS = {
        ID_PULL, ID_BUNI, ID_DEV, ID_REPOS, ID_CLONE, ID_AI,
        ID_DRAWER, ID_DRAWER_RIGHT, ID_KEYBOARD, ID_PASTE
    };

    private static final String PREFS = "invapp_extra_keys_bar";
    private static final String KEY_ORDER = "page2_order";
    private static final String SEP = ",";
    private static final String LOG_TAG = "ExtraKeysBarHelper";

    private static final String PAGE0_JSON =
        "[['ESC','/',{key: '-', popup: '|'},'HOME','UP','END','PGUP'],"
            + "['TAB','CTRL','ALT','LEFT','DOWN','RIGHT','PGDN']]";

    private ExtraKeysBarHelper() {}

    @NonNull
    public static List<String> getPage2Ids(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_ORDER, null);
        if (raw == null || raw.trim().isEmpty()) {
            return new ArrayList<>(Arrays.asList(DEFAULT_PAGE2_IDS));
        }
        List<String> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String part : raw.split(SEP)) {
            String id = part.trim();
            if (id.isEmpty() || !isKnown(id) || !seen.add(id)) {
                continue;
            }
            out.add(id);
        }
        if (out.isEmpty()) {
            return new ArrayList<>(Arrays.asList(DEFAULT_PAGE2_IDS));
        }
        return out;
    }

    public static void setPage2Ids(@NonNull Context context, @NonNull List<String> ids) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String id : ids) {
            if (id != null && isKnown(id)) {
                seen.add(id);
            }
        }
        if (seen.isEmpty()) {
            seen.addAll(Arrays.asList(DEFAULT_PAGE2_IDS));
        }
        StringBuilder sb = new StringBuilder();
        for (String id : seen) {
            if (sb.length() > 0) {
                sb.append(SEP);
            }
            sb.append(id);
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ORDER, sb.toString())
            .apply();
    }

    public static void resetToDefault(@NonNull Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ORDER)
            .apply();
    }

    public static boolean isKnown(@Nullable String id) {
        if (id == null) {
            return false;
        }
        for (String known : ALL_PAGE2_IDS) {
            if (known.equals(id)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    public static boolean[] checkedFlags(@NonNull List<String> visible) {
        boolean[] flags = new boolean[ALL_PAGE2_IDS.length];
        for (int i = 0; i < ALL_PAGE2_IDS.length; i++) {
            flags[i] = visible.contains(ALL_PAGE2_IDS[i]);
        }
        return flags;
    }

    @NonNull
    public static List<String> filterToCatalogOrder(@NonNull boolean[] checked) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < ALL_PAGE2_IDS.length && i < checked.length; i++) {
            if (checked[i]) {
                out.add(ALL_PAGE2_IDS[i]);
            }
        }
        return out;
    }

    /** Build multi-page {@code extra-keys} JSON. */
    @NonNull
    public static String buildExtraKeysJson(@NonNull List<String> page2Ids) {
        StringBuilder page2 = new StringBuilder("[");
        List<String> row1 = new ArrayList<>();
        List<String> row2 = new ArrayList<>();
        int i = 0;
        for (String id : page2Ids) {
            String token = keyToken(id);
            if (i < 4) {
                row1.add(token);
            } else {
                row2.add(token);
            }
            i++;
        }
        if (row1.isEmpty()) {
            row1.add(keyToken(ID_KEYBOARD));
        }
        page2.append(joinRow(row1));
        if (!row2.isEmpty()) {
            page2.append(',').append(joinRow(row2));
        }
        page2.append(']');
        return "[" + PAGE0_JSON + "," + page2 + "]";
    }

    @NonNull
    private static String keyToken(@NonNull String id) {
        switch (id) {
            case ID_PULL: return "{key: 'PULL', display: 'pull'}";
            case ID_BUNI: return "{key: 'BUNI', display: 'bun i'}";
            case ID_DEV: return "{key: 'DEV', display: 'dev'}";
            case ID_REPOS: return "{key: 'REPOS', display: 'repos'}";
            case ID_CLONE: return "{key: 'CLONE', display: 'clone'}";
            case ID_AI: return "{key: 'AI', display: 'AI'}";
            case ID_NEW: return "{key: 'NEW', display: 'new'}";
            case ID_DRAWER_RIGHT: return "{key: 'DRAWER_RIGHT', display: 'tools'}";
            case ID_DRAWER: return "'DRAWER'";
            case ID_KEYBOARD: return "'KEYBOARD'";
            case ID_PASTE: return "'PASTE'";
            default: return "'" + id + "'";
        }
    }

    @NonNull
    private static String joinRow(@NonNull List<String> tokens) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(tokens.get(i));
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * Writes / updates the {@code extra-keys} property in primary termux.properties.
     * @return true on success
     */
    public static boolean writeExtraKeysProperty(@NonNull List<String> page2Ids) {
        String json = buildExtraKeysJson(page2Ids);
        File propsFile = new File(TermuxConstants.TERMUX_PROPERTIES_PRIMARY_FILE_PATH);
        try {
            File parent = propsFile.getParentFile();
            if (parent != null && !parent.isDirectory()) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            StringBuilder contents = new StringBuilder();
            if (propsFile.isFile()) {
                Error readError = FileUtils.readTextFromFile("termux.properties",
                    propsFile.getAbsolutePath(), StandardCharsets.UTF_8, contents, false);
                if (readError != null) {
                    Logger.logWarn(LOG_TAG, "read props: " + readError);
                    contents.setLength(0);
                }
            }
            String updated = upsertProperty(contents.toString(),
                TermuxPropertyConstants.KEY_EXTRA_KEYS, json);
            Error writeError = FileUtils.writeTextToFile("termux.properties",
                propsFile.getAbsolutePath(), StandardCharsets.UTF_8, updated, false);
            if (writeError != null) {
                Logger.logWarn(LOG_TAG, "write props: " + writeError);
                return false;
            }
            return true;
        } catch (Exception e) {
            Logger.logWarn(LOG_TAG, "writeExtraKeysProperty: " + e.getMessage());
            return false;
        }
    }

    /**
     * Wrap a property value in single quotes so humans and parsers keep braces /
     * spaces / colons intact. Readers should call {@link #unquotePropertyValue}.
     */
    @NonNull
    public static String quotePropertyValue(@NonNull String value) {
        // java.util.Properties does not treat quotes as delimiters; they are stored
        // literally and stripped when the value is interpreted as extra-keys JSON.
        return "'" + value + "'";
    }

    /** Strip a single pair of surrounding {@code '} or {@code "} if present. */
    @NonNull
    public static String unquotePropertyValue(@Nullable String value) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
        if (v.length() >= 2) {
            char a = v.charAt(0);
            char b = v.charAt(v.length() - 1);
            if ((a == '\'' && b == '\'') || (a == '"' && b == '"')) {
                return v.substring(1, v.length() - 1);
            }
        }
        return value;
    }

    /**
     * Insert or replace {@code key=value} in a properties file body.
     * Values are written quoted ({@link #quotePropertyValue}).
     */
    @NonNull
    static String upsertProperty(@NonNull String text, @NonNull String key, @NonNull String value) {
        String line = key + " = " + quotePropertyValue(value);
        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder();
        boolean replaced = false;
        for (String existing : lines) {
            String trimmed = existing.trim();
            if (trimmed.startsWith(key + "=") || trimmed.startsWith(key + " =")) {
                if (!replaced) {
                    out.append(line).append('\n');
                    replaced = true;
                }
                continue;
            }
            out.append(existing).append('\n');
        }
        if (!replaced) {
            if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') {
                out.append('\n');
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }
}
