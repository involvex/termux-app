package com.invapp.app.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.invapp.app.TermuxActivity;
import com.invapp.shared.errors.Error;
import com.invapp.shared.file.FileUtils;
import com.invapp.shared.logger.Logger;
import com.invapp.shared.termux.TermuxConstants;
import com.invapp.shared.termux.settings.properties.TermuxPropertyConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Writes bundled ANSI palettes to {@code ~/.termux/colors.properties} and
 * updates {@code night-mode} for System. Broadcasts style reload.
 */
public final class ThemePaletteHelper {

    public static final String ID_HACKER = "hacker";
    public static final String ID_MATCHA = "matcha";
    public static final String ID_PAPER = "paper";
    public static final String ID_SYSTEM = "system";

    public static final String[] THEME_IDS = {
        ID_HACKER, ID_MATCHA, ID_PAPER, ID_SYSTEM
    };

    /** Parallel to {@link #THEME_IDS}. */
    public static final String[] THEME_LABELS = {
        "Hacker", "Matcha", "Paper", "System"
    };

    public static final String THEME_MARK_PREFIX = "invapp-theme:";
    private static final String LOG_TAG = "ThemePaletteHelper";
    private static final String PREFS = "invapp_theme_palette";
    private static final String KEY_THEME = "theme_id";

    private ThemePaletteHelper() {}

    @NonNull
    public static String getSelectedThemeId(@NonNull Context context) {
        String id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME, ID_HACKER);
        return isKnown(id) ? id : ID_HACKER;
    }

    public static boolean isKnown(@Nullable String id) {
        if (id == null) {
            return false;
        }
        for (String known : THEME_IDS) {
            if (known.equals(id)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    public static String assetForTheme(@NonNull String themeId) {
        switch (themeId) {
            case ID_MATCHA:
                return "colors-matcha.properties";
            case ID_PAPER:
            case ID_SYSTEM:
                return "colors-paper.properties";
            case ID_HACKER:
            default:
                return "colors.properties";
        }
    }

    @NonNull
    public static String nightModeForTheme(@NonNull String themeId) {
        switch (themeId) {
            case ID_PAPER:
                return "false";
            case ID_SYSTEM:
                return "system";
            case ID_MATCHA:
            case ID_HACKER:
            default:
                return "true";
        }
    }

    /** True when colors.properties has an InvApp theme mark (safe to overwrite on pick). */
    public static boolean hasThemeMark(@Nullable String colorsText) {
        return colorsText != null && colorsText.contains(THEME_MARK_PREFIX);
    }

    @Nullable
    public static String readThemeMark(@NonNull File colorsFile) {
        if (!colorsFile.isFile()) {
            return null;
        }
        try {
            StringBuilder contents = new StringBuilder();
            Error readError = FileUtils.readTextFromFile(
                "colors.properties", colorsFile.getAbsolutePath(), StandardCharsets.UTF_8,
                contents, false);
            if (readError != null) {
                return null;
            }
            String text = contents.toString();
            int idx = text.indexOf(THEME_MARK_PREFIX);
            if (idx < 0) {
                return null;
            }
            int start = idx + THEME_MARK_PREFIX.length();
            int end = start;
            while (end < text.length()) {
                char c = text.charAt(end);
                if (c == '\n' || c == '\r' || c == ' ' || c == '\t') {
                    break;
                }
                end++;
            }
            String id = text.substring(start, end).trim().toLowerCase(Locale.US);
            return isKnown(id) ? id : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Apply a palette + night-mode, persist selection, reload styling.
     * @return null on success, error message otherwise
     */
    @Nullable
    public static String applyTheme(@NonNull Context context, @NonNull String themeId,
                                    boolean force) {
        if (!isKnown(themeId)) {
            return "Unknown theme";
        }
        File colorsFile = TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE;
        if (!force && colorsFile.isFile() && colorsFile.length() > 0) {
            String mark = readThemeMark(colorsFile);
            if (mark == null) {
                // Custom / unmarked palette — require force (Reset / explicit confirm).
                try {
                    StringBuilder contents = new StringBuilder();
                    Error readError = FileUtils.readTextFromFile(
                        "colors.properties", colorsFile.getAbsolutePath(),
                        StandardCharsets.UTF_8, contents, false);
                    if (readError == null && !hasThemeMark(contents.toString())
                        && contents.length() > 0) {
                        return "Custom colors.properties (no InvApp mark); use Reset or force";
                    }
                } catch (Exception ignored) {
                    // fall through to write
                }
            }
        }
        if (!copyAssetToColors(context, assetForTheme(themeId), colorsFile)) {
            return "Failed writing colors.properties";
        }
        if (!upsertNightMode(nightModeForTheme(themeId))) {
            return "Failed writing night-mode";
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, themeId)
            .apply();
        TermuxActivity.updateTermuxActivityStyling(context, true);
        return null;
    }

    /** Recopy Hacker v2 and night-mode=true. */
    @Nullable
    public static String resetToHacker(@NonNull Context context) {
        return applyTheme(context, ID_HACKER, true);
    }

    static boolean copyAssetToColors(@NonNull Context context, @NonNull String asset,
                                      @NonNull File colorsFile) {
        try {
            File parent = colorsFile.getParentFile();
            if (parent != null && !parent.isDirectory()) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            try (InputStream in = context.getAssets().open(asset);
                 OutputStream out = new FileOutputStream(colorsFile)) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
            }
            return true;
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "copyAssetToColors", e);
            return false;
        }
    }

    static boolean upsertNightMode(@NonNull String value) {
        File propsFile = TermuxConstants.TERMUX_PROPERTIES_PRIMARY_FILE;
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
                    contents.setLength(0);
                }
            }
            String updated = ExtraKeysBarHelper.upsertRawProperty(contents.toString(),
                TermuxPropertyConstants.KEY_NIGHT_MODE, value);
            Error writeError = FileUtils.writeTextToFile("termux.properties",
                propsFile.getAbsolutePath(), StandardCharsets.UTF_8, updated, false);
            return writeError == null;
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "upsertNightMode", e);
            return false;
        }
    }
}
