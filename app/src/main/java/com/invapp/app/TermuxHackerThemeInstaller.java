package com.invapp.app;

import android.content.Context;

import androidx.annotation.NonNull;

import com.invapp.shared.errors.Error;
import com.invapp.shared.file.FileUtils;
import com.invapp.shared.logger.Logger;
import com.invapp.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Installs / upgrades the InvApp hacker theme under {@code ~/.termux/}.
 * Overwrites the known-broken v1 palette (blue slots remapped to green, which
 * made {@code ls} directory names render as solid green bars).
 */
public final class TermuxHackerThemeInstaller {

    private static final String LOG_TAG = "TermuxHackerThemeInstaller";
    private static final String COLORS_ASSET = "colors.properties";
    /** Marker in asset / installed file for the fixed palette. */
    private static final String COLORS_VERSION_MARK = "v2: color4/color12";

    private TermuxHackerThemeInstaller() {}

    public static void installDefaultsIfMissing(@NonNull Context context) {
        try {
            FileUtils.createDirectoryFile(TermuxConstants.TERMUX_DATA_HOME_DIR_PATH);
            installColorsProperties(context);
            ensureNightModeEnabled();
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed installing hacker theme defaults", e);
        }
    }

    private static void installColorsProperties(@NonNull Context context) {
        File colorsFile = TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE;
        if (colorsFile.isFile() && colorsFile.length() > 0 && !needsColorsUpgrade(colorsFile)) {
            return;
        }
        try (InputStream in = context.getAssets().open(COLORS_ASSET);
             OutputStream out = new FileOutputStream(colorsFile)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
            Logger.logInfo(LOG_TAG, "Installed hacker colors.properties");
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed writing colors.properties", e);
        }
    }

    /** True for missing v2 mark or the old green-as-blue InvApp palette. */
    private static boolean needsColorsUpgrade(@NonNull File colorsFile) {
        try {
            StringBuilder contents = new StringBuilder();
            Error readError = FileUtils.readTextFromFile(
                "colors.properties", colorsFile.getAbsolutePath(), StandardCharsets.UTF_8,
                contents, false);
            if (readError != null) {
                return false;
            }
            String text = contents.toString();
            if (text.contains(COLORS_VERSION_MARK)) {
                return false;
            }
            // Stock InvApp v1 or any file that still maps "blue" to matrix green.
            return text.contains("InvApp Hacker theme")
                || text.contains("color4=#00AA55")
                || text.contains("color12=#39FF14");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Ensure {@code night-mode=true} is present in termux.properties when the
     * file is missing or the key is unset, so the UI stays on the dark hacker theme.
     */
    private static void ensureNightModeEnabled() {
        File propsFile = TermuxConstants.TERMUX_PROPERTIES_PRIMARY_FILE;
        try {
            if (!propsFile.exists()) {
                FileUtils.createDirectoryFile(propsFile.getParent());
                String contents = "# InvApp defaults\nnight-mode=true\n";
                Error writeError = FileUtils.writeTextToFile("termux.properties",
                    propsFile.getAbsolutePath(), StandardCharsets.UTF_8, contents, false);
                if (writeError == null) {
                    Logger.logInfo(LOG_TAG, "Created termux.properties with night-mode=true");
                }
                return;
            }
            StringBuilder contents = new StringBuilder();
            Error readError = FileUtils.readTextFromFile(
                "termux.properties", propsFile.getAbsolutePath(), StandardCharsets.UTF_8,
                contents, false);
            if (readError != null) {
                return;
            }
            String text = contents.toString();
            if (text.matches("(?s).*(?m)^[ \\t]*night-mode[ \\t]*=.*")) {
                return;
            }
            String patched = text;
            if (!patched.isEmpty() && !patched.endsWith("\n")) {
                patched = patched + "\n";
            }
            patched = patched + "night-mode=true\n";
            Error writeError = FileUtils.writeTextToFile("termux.properties",
                propsFile.getAbsolutePath(), StandardCharsets.UTF_8, patched, false);
            if (writeError == null) {
                Logger.logInfo(LOG_TAG, "Appended night-mode=true to termux.properties");
            }
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed ensuring night-mode", e);
        }
    }
}
