package com.invapp.app.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.invapp.app.TermuxBunInstaller;
import com.invapp.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

/**
 * Reads APK-bundled vs on-device Bun stamp/version for Settings and tests.
 * Does not invoke {@code bun upgrade} or replace {@code .incbin} binaries.
 */
public final class BunVersionHelper {

    public static final String RELEASES_URL =
        TermuxConstants.TERMUX_GITHUB_REPO_URL + "/releases";

    private BunVersionHelper() {}

    /** Snapshot of stamp file, expected APK pin, and optional running version. */
    public static final class Report {
        @NonNull public final String bundledExpected;
        @Nullable public final String stamped;
        @Nullable public final String running;
        public final boolean libexecPresent;
        public final boolean mismatch;

        Report(@NonNull String bundledExpected,
               @Nullable String stamped,
               @Nullable String running,
               boolean libexecPresent) {
            this.bundledExpected = bundledExpected;
            this.stamped = stamped;
            this.running = running;
            this.libexecPresent = libexecPresent;
            this.mismatch = isMismatch(stamped, running, bundledExpected);
        }
    }

    @NonNull
    public static Report collect() {
        return collect(TermuxConstants.TERMUX_PREFIX_DIR_PATH,
            TermuxBunInstaller.BUNDLED_BUN_VERSION);
    }

    @VisibleForTesting
    @NonNull
    static Report collect(@NonNull String prefixPath, @NonNull String bundledExpected) {
        File stamp = new File(prefixPath, "var/lib/invapp/bun.version");
        File libexec = new File(prefixPath, "libexec/bun");
        String stamped = readStamp(stamp);
        String running = null;
        if (libexec.isFile() && libexec.canExecute()) {
            // Settings card uses stamp + bundled pin; shell `bun-version` reports
            // live `bun --version`. Avoid spawning processes from preference UI.
            running = stamped;
        }
        return new Report(bundledExpected, stamped, running, libexec.isFile());
    }

    @VisibleForTesting
    static boolean isMismatch(@Nullable String stamped,
                              @Nullable String running,
                              @NonNull String bundledExpected) {
        if (stamped != null && !stamped.isEmpty()
            && !bundledExpected.equals(stamped)) {
            return true;
        }
        if (running != null && !running.isEmpty()
            && stamped != null && !stamped.isEmpty()
            && !running.equals(stamped)) {
            return true;
        }
        return false;
    }

    @Nullable
    @VisibleForTesting
    static String readStamp(@NonNull File stamp) {
        if (!stamp.isFile()) {
            return null;
        }
        try (FileInputStream in = new FileInputStream(stamp)) {
            byte[] buf = new byte[64];
            int n = in.read(buf);
            if (n <= 0) {
                return null;
            }
            return new String(buf, 0, n, StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return null;
        }
    }
}
