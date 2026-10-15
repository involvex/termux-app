package com.invapp.app;

import android.content.Context;
import android.system.Os;

import androidx.annotation.NonNull;

import com.invapp.shared.logger.Logger;
import com.invapp.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Seeds the {@code commons.sh} shell utility library under
 * {@code $PREFIX/lib/invapp/} and a profile.d hook that auto-sources it
 * in login shells.
 *
 * <p>Library provides: colored logging, tables, boxes, spinners, interactive
 * selection menus, version detection, and update checking — ported from
 * core-termux's {@code utils/colors.sh} and {@code utils/log.sh} and
 * {@code utils/version.sh}, adapted to {@code com.involvex.termux_app}.
 *
 * <p>Both files are read from app assets (not embedded as Java strings) to
 * keep the shell content maintainable. Idempotent: safe to call on every
 * app start; a version stamp avoids redundant writes.
 */
public final class ShellLibraryInstaller {

    private static final String LOG_TAG = "ShellLibraryInstaller";

    private static final String LIB_DIR_NAME = "invapp";
    private static final String LIB_FILE_NAME = "commons.sh";
    private static final String LIB_ASSET = "commons.sh";
    private static final String BOOTSTRAP_ASSET = "invapp-bootstrap.sh";
    private static final String BOOTSTRAP_FILE_NAME = "bootstrap.sh";
    private static final String PROFILE_D_HOOK_ASSET = "invapp-library-profile.sh";
    private static final String PROFILE_D_HOOK = "invapp-library.sh";

    private static final String STAMP_PATH = "var/lib/invapp/invapp-commons.version";

    /** Script assets to seed into $PREFIX/bin. */
    private static final String[] SCRIPT_ASSETS = {
        "td-pg.sh",
        "td-kilo-setup",
        "td-cline-setup",
        "kilo",
        "cline"
    };


    /** Library version — bump to force refresh on upgrade. */
    public static final String LIBRARY_VERSION = "1.3.0";

    private ShellLibraryInstaller() {}

    /**
     * Install/refresh the commons library, profile.d hook, and executable
     * scripts. Safe to call on every app start; version stamps avoid
     * redundant writes.
     */
    public static void installIfNeeded(@NonNull Context context) {
        File prefix = new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH);
        if (!prefix.isDirectory()) {
            Logger.logWarn(LOG_TAG, "Prefix missing; skip shell library install");
            return;
        }

        boolean needRefresh = false;
        File stamp = new File(prefix, STAMP_PATH);
        String current;
        try {
            current = stamp.isFile() ? readStamp(stamp) : "";
        } catch (Exception e) {
            current = "";
        }
        if (!LIBRARY_VERSION.equals(current)) {
            needRefresh = true;
        }

        // Always check scripts — they have independent stamps.
        installScripts(context, prefix);

        if (!needRefresh) {
            return;
        }

        boolean ok = true;

        // Ensure lib dir exists (for bootstrap + commons).
        File libDir = new File(prefix, "lib/" + LIB_DIR_NAME);
        ensureDir(libDir);

        // Seed the bootstrap file (provides import function).
        File bootstrapFile = new File(libDir, BOOTSTRAP_FILE_NAME);
        if (writeAssetToFile(context, BOOTSTRAP_ASSET, bootstrapFile)) {
            Logger.logInfo(LOG_TAG, "Installed invapp-bootstrap.sh");
        } else {
            ok = false;
        }

        // Seed the library file.
        File libFile = new File(libDir, LIB_FILE_NAME);
        if (writeAssetToFile(context, LIB_ASSET, libFile)) {
            Logger.logInfo(LOG_TAG, "Installed invapp-commons.sh");
        } else {
            ok = false;
        }

        // Seed env.sh — the shared env + td_* helper library now sourced by the
        // bun/bunx/bun-doctor wrappers that migrated onto the invapp/bin assets
        // (see TermuxBunInstaller). It uses ${VAR:-default}, so already-set device
        // values win; a com.termux PREFIX fallback only triggers if unset and is
        // still covered by the redirector. Sourced, not executed -> 0644.
        File envFile = new File(libDir, "env.sh");
        if (writeAssetToFile(context, "invapp/env.sh", envFile)) {
            Logger.logInfo(LOG_TAG, "Installed invapp-env.sh");
        } else {
            ok = false;
        }

        // Seed the profile.d hook.
        File profileD = new File(prefix, "etc/profile.d");
        ensureDir(profileD);
        File hook = new File(profileD, PROFILE_D_HOOK);
        if (writeAssetToFile(context, PROFILE_D_HOOK_ASSET, hook)) {
            Logger.logInfo(LOG_TAG, "Installed profile.d hook");
        } else {
            ok = false;
        }

        if (ok) {
            // Update stamp.
            try {
                File stampDir = stamp.getParentFile();
                if (stampDir != null) ensureDir(stampDir);
                writeTextFile(stamp, LIBRARY_VERSION + "\n");
            } catch (Exception e) {
                Logger.logWarn(LOG_TAG, "Could not write version stamp: " + e.getMessage());
            }
        }
    }

    /**
     * Seed executable scripts from assets into $PREFIX/bin. Each script is
     * stamped individually so it can be refreshed on library version change.
     */
    private static void installScripts(@NonNull Context context, @NonNull File prefix) {
        for (String asset : SCRIPT_ASSETS) {
            String binName = asset.replace(".sh", "");
            File dest = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, binName);
            File scriptStamp = new File(prefix, "var/lib/invapp/" + binName + ".version");
            String scriptVer = "";
            try {
                scriptVer = scriptStamp.isFile() ? readStamp(scriptStamp) : "";
            } catch (Exception ignored) {}
            // Refresh if missing or version changed
            if (dest.isFile() && LIBRARY_VERSION.equals(scriptVer)) {
                continue;
            }
            if (writeAssetToFile(context, asset, dest, 0700)) {
                Logger.logInfo(LOG_TAG, "Installed " + binName);
                writeTextFile(scriptStamp, LIBRARY_VERSION + "\n");
            }
        }
    }

    /**
     * Read a text asset and write it to dest (mode 0644).
     */
    @NonNull
    public static boolean writeAssetToFile(@NonNull Context context,
                                             @NonNull String assetName,
                                             @NonNull File dest,
                                             int mode) {
        try {
            File parent = dest.getParentFile();
            if (parent != null) {
                ensureDir(parent);
            }
            try (InputStream in = context.getAssets().open(assetName);
                 FileOutputStream out = new FileOutputStream(dest)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
            }
            //noinspection OctalInteger
            Os.chmod(dest.getAbsolutePath(), mode);
            return true;
        } catch (Exception e) {
            Logger.logWarn(LOG_TAG, "Failed installing asset " + assetName + " to " + dest + ": " + e.getMessage());
            return false;
        }
    }

    /** Convenience: seed a non-executable asset (0644). */
    @NonNull
    public static boolean writeAssetToFile(@NonNull Context context,
                                             @NonNull String assetName,
                                             @NonNull File dest) {
        return writeAssetToFile(context, assetName, dest, 0644);
    }

    /** Convenience: seed an executable asset to $PREFIX/bin (0700). */
    @NonNull
    public static boolean writeExecutableAsset(@NonNull Context context,
                                                 @NonNull String assetName,
                                                 @NonNull String binName) {
        File dest = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, binName);
        return writeAssetToFile(context, assetName, dest, 0700);
    }

    @NonNull
    private static String readStamp(@NonNull File stamp) throws Exception {
        byte[] buf = new byte[(int) stamp.length()];
        java.io.FileInputStream fis = new java.io.FileInputStream(stamp);
        fis.read(buf);
        fis.close();
        return new String(buf, StandardCharsets.UTF_8).trim();
    }

    private static void ensureDir(@NonNull File dir) {
        if (!dir.exists() && !dir.mkdirs()) {
            Logger.logWarn(LOG_TAG, "Could not create " + dir);
        }
    }

    private static void writeTextFile(@NonNull File dest, @NonNull String contents) {
        try {
            File parent = dest.getParentFile();
            if (parent != null) {
                ensureDir(parent);
            }
            File tmp = new File(dest.getAbsolutePath() + ".new");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(contents.getBytes(StandardCharsets.UTF_8));
            }
            //noinspection OctalInteger
            Os.chmod(tmp.getAbsolutePath(), 0644);
            if (dest.exists() && !dest.delete()) {
                try {
                    Os.remove(dest.getAbsolutePath());
                } catch (Exception e) {
                    Logger.logWarn(LOG_TAG, "Could not replace " + dest + ": " + e.getMessage());
                }
            }
            if (!tmp.renameTo(dest)) {
                Logger.logWarn(LOG_TAG, "Could not install " + dest);
                tmp.delete();
            }
        } catch (Exception e) {
            Logger.logWarn(LOG_TAG, "Text install failed for " + dest + ": " + e.getMessage());
        }
    }

    /**
     * Returns the library version string for diagnostics.
     */
    @NonNull
    public static String libraryVersion() {
        return LIBRARY_VERSION;
    }
}