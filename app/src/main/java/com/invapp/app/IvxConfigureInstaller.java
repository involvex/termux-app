package com.invapp.app;

import android.content.Context;
import android.system.Os;

import androidx.annotation.NonNull;

import com.invapp.shared.logger.Logger;
import com.invapp.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Seeds the InVxTermux setup surface:
 *
 * <ul>
 *   <li>{@code $PREFIX/bin/ivx-configure} — banner / completions / packages /
 *       network / doctor menu (pure bash, no fzf/gum)</li>
 *   <li>{@code $PREFIX/bin/ivx-doctor} — thin wrapper for
 *       {@code ivx-configure doctor}, matching {@code bun-doctor} /
 *       {@code jdk-doctor}</li>
 *   <li>{@code $PREFIX/bin/ivx-cleanup} — interactive cleanup for pkg / cargo /
 *       bun -g / npm -g / pip / uv tool</li>
 *   <li>{@code $PREFIX/bin/ivx-fix-dpkg-scripts} — heal dpkg {@code info/format}
 *       + patch maintainer scripts ({@code invapp-fix-dpkg-scripts} alias)</li>
 *   <li>{@code $PREFIX/lib/invapp/banner.sh} — pre-rendered pixelfont art +
 *       renderer, sourced by the login hook</li>
 *   <li>{@code $PREFIX/etc/profile.d/invapp-banner.sh} — interactive-only
 *       login banner hook (no {@code ~/.bashrc} edits)</li>
 *   <li>{@code $PREFIX/etc/profile.d/invapp-network.sh} — optional
 *       HTTP(S)/SOCKS/GOPROXY exports from user config</li>
 *   <li>{@code $PREFIX/etc/bash_completion.d/invapp-ivx.bash} — completions
 *       for the new commands</li>
 * </ul>
 *
 * <p>The exec helpers are rewritten on every app start (code, not user data,
 * like {@link ResourceScriptsInstaller}). User config lives in
 * {@code $XDG_CONFIG_HOME/invapp/} and is never touched here.
 */
public final class IvxConfigureInstaller {

    private static final String LOG_TAG = "IvxConfigureInstaller";

    /** Bumped when the seeded shell assets change in a user-visible way. */
    public static final String VERSION = "1.2.0";

    public static final String ASSET_CONFIGURE = "invapp/bin/ivx-configure";
    public static final String ASSET_DOCTOR = "invapp/bin/ivx-doctor";
    public static final String ASSET_CLEANUP = "invapp/bin/ivx-cleanup";
    public static final String ASSET_FIX_DPKG = "invapp/bin/ivx-fix-dpkg-scripts";
    public static final String ASSET_FIX_DPKG_ALIAS = "invapp/bin/invapp-fix-dpkg-scripts";
    public static final String ASSET_BANNER = "invapp/banner.sh";
    public static final String ASSET_HOOK = "invapp/config/profile.d/invapp-banner.sh";
    public static final String ASSET_NETWORK_HOOK = "invapp/config/profile.d/invapp-network.sh";
    public static final String ASSET_COMPLETION = "invapp/config/bash_completion.d/invapp-ivx.bash";

    public static final String BIN_CONFIGURE = "ivx-configure";
    public static final String BIN_DOCTOR = "ivx-doctor";
    public static final String BIN_CLEANUP = "ivx-cleanup";
    public static final String BIN_FIX_DPKG = "ivx-fix-dpkg-scripts";
    public static final String BIN_FIX_DPKG_ALIAS = "invapp-fix-dpkg-scripts";

    private static final String LIB_BANNER_REL = "lib/invapp/banner.sh";
    private static final String HOOK_REL = "etc/profile.d/invapp-banner.sh";
    private static final String NETWORK_HOOK_REL = "etc/profile.d/invapp-network.sh";
    private static final String COMPLETION_REL = "etc/bash_completion.d/invapp-ivx.bash";
    private static final String STAMP_REL = "var/lib/invapp/ivx.version";

    private IvxConfigureInstaller() {}

    /** Installed version stamp, or {@code null} when never installed. */
    public static String version() {
        return VERSION;
    }

    @NonNull
    public static File configureFile() {
        return new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, BIN_CONFIGURE);
    }

    @NonNull
    public static File doctorFile() {
        return new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, BIN_DOCTOR);
    }

    @NonNull
    public static File cleanupFile() {
        return new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, BIN_CLEANUP);
    }

    @NonNull
    public static File fixDpkgFile() {
        return new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, BIN_FIX_DPKG);
    }

    @NonNull
    public static File fixDpkgAliasFile() {
        return new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, BIN_FIX_DPKG_ALIAS);
    }

    @NonNull
    public static File bannerFile() {
        return new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH, LIB_BANNER_REL);
    }

    @NonNull
    public static File hookFile() {
        return new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH, HOOK_REL);
    }

    @NonNull
    public static File networkHookFile() {
        return new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH, NETWORK_HOOK_REL);
    }

    @NonNull
    public static File completionFile() {
        return new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH, COMPLETION_REL);
    }

    @NonNull
    public static File stampFile() {
        return new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH, STAMP_REL);
    }

    /**
     * Seed/refresh the setup helpers. Idempotent and safe on every app start;
     * a missing prefix (before the bootstrap is unpacked) is a quiet no-op.
     */
    public static void installIfNeeded(@NonNull Context context) {
        File prefix = new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH);
        File binDir = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH);
        if (!prefix.isDirectory() || !binDir.isDirectory()) {
            Logger.logWarn(LOG_TAG, "Prefix missing; skip ivx-configure install");
            return;
        }

        String bash = resolveBash(prefix);
        boolean ok = true;

        // Exec helpers: refreshed every start, like the td-resource scripts.
        ok &= TermuxBunInstaller.writeExec(configureFile(),
            TermuxBunInstaller.assetWithBashShebang(context, ASSET_CONFIGURE, bash));
        ok &= TermuxBunInstaller.writeExec(doctorFile(),
            TermuxBunInstaller.assetWithBashShebang(context, ASSET_DOCTOR, bash));
        ok &= TermuxBunInstaller.writeExec(cleanupFile(),
            TermuxBunInstaller.assetWithBashShebang(context, ASSET_CLEANUP, bash));
        ok &= TermuxBunInstaller.writeExec(fixDpkgFile(),
            TermuxBunInstaller.assetWithBashShebang(context, ASSET_FIX_DPKG, bash));
        ok &= TermuxBunInstaller.writeExec(fixDpkgAliasFile(),
            TermuxBunInstaller.assetWithBashShebang(context, ASSET_FIX_DPKG_ALIAS, bash));

        // Sourced/sidecar assets: 0644, same writer the shell library uses.
        ok &= ShellLibraryInstaller.writeAssetToFile(context, ASSET_BANNER, bannerFile());
        ok &= ShellLibraryInstaller.writeAssetToFile(context, ASSET_HOOK, hookFile());
        ok &= ShellLibraryInstaller.writeAssetToFile(context, ASSET_NETWORK_HOOK, networkHookFile());
        ok &= ShellLibraryInstaller.writeAssetToFile(context, ASSET_COMPLETION, completionFile());

        if (!ok) {
            Logger.logWarn(LOG_TAG, "Partial ivx-configure install; will retry next start");
            return;
        }

        writeStamp();
        Logger.logInfo(LOG_TAG, "Installed ivx-configure " + VERSION);
    }

    @NonNull
    private static String resolveBash(@NonNull File prefix) {
        File bash = new File(prefix, "bin/bash");
        return bash.isFile() ? bash.getAbsolutePath() : "/system/bin/sh";
    }

    private static void writeStamp() {
        File stamp = stampFile();
        File dir = stamp.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            Logger.logWarn(LOG_TAG, "Could not create " + dir);
            return;
        }
        File tmp = new File(stamp.getAbsolutePath() + ".new");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write((VERSION + "\n").getBytes(StandardCharsets.UTF_8));
            //noinspection OctalInteger
            Os.chmod(tmp.getAbsolutePath(), 0644);
            if (stamp.exists() && !stamp.delete()) {
                Os.remove(stamp.getAbsolutePath());
            }
            if (!tmp.renameTo(stamp)) {
                Logger.logWarn(LOG_TAG, "Could not install " + stamp);
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        } catch (Exception e) {
            Logger.logWarn(LOG_TAG, "Could not write version stamp: " + e.getMessage());
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }
}
