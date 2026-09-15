package com.invapp.app;

import android.content.Context;
import android.system.Os;

import androidx.annotation.NonNull;

import com.invapp.shared.logger.Logger;
import com.invapp.shared.termux.TermuxConstants;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Installs the official Android Bun binary and a single {@code $PREFIX/bin/bun}
 * wrapper.
 *
 * <p>Real binary lives at {@code $PREFIX/libexec/bun}. The {@code bin/bun}
 * wrapper clears {@code LD_PRELOAD} (path redirector breaks Bun / optional
 * native installs → SIGSYS 31) and sets OPENSSL + npm/bun platform hints.
 *
 * <p>Uses oven-sh {@code bun-linux-*-android.zip} (Bionic PIE), not glibc Linux
 * builds.
 */
public final class TermuxBunInstaller {

    private static final String LOG_TAG = "TermuxBunInstaller";

    /** Must match the zips downloaded in {@code app/build.gradle}. */
    public static final String BUNDLED_BUN_VERSION = "1.4.2";

    private static final String LIBEXEC_REL = "libexec/bun";

    private TermuxBunInstaller() {}

    /**
     * Extract bundled Bun into the Termux prefix when missing or outdated.
     * Safe to call on every app start; refreshes wrappers even when current.
     */
    public static void installIfNeeded(@NonNull Context context) {
        File binDir = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH);
        if (!binDir.isDirectory()) {
            Logger.logWarn(LOG_TAG, "Prefix bin missing; skip Bun install");
            return;
        }

        File libexecDir = new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH, "libexec");
        File bunReal = new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH, LIBEXEC_REL);
        File stamp = new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH,
            "var/lib/invapp/bun.version");

        boolean realOk = bunReal.isFile() && bunReal.canExecute()
            && isAndroidBunBinary(bunReal);
        if (realOk && stamp.isFile()) {
            try {
                if (BUNDLED_BUN_VERSION.equals(readStamp(stamp))) {
                    installShellHelpers();
                    ensureWorkspaceDirs();
                    return;
                }
            } catch (Exception ignored) {
                // Reinstall below.
            }
        }

        if (bunReal.isFile() && !isAndroidBunBinary(bunReal)) {
            Logger.logWarn(LOG_TAG,
                "Replacing non-Android Bun at " + bunReal
                    + " (curl|bash glibc builds → required file not found / SIGSYS 31)");
        }

        byte[] zipBytes;
        try {
            zipBytes = loadZipBytes();
        } catch (UnsatisfiedLinkError e) {
            Logger.logWarn(LOG_TAG, "libinvapp-bun not loaded: " + e.getMessage());
            return;
        }
        if (zipBytes == null || zipBytes.length == 0) {
            Logger.logInfo(LOG_TAG,
                "No bundled Bun for this ABI (need aarch64 or x86_64)");
            return;
        }

        try {
            if (!libexecDir.exists() && !libexecDir.mkdirs()) {
                Logger.logWarn(LOG_TAG, "Could not create " + libexecDir);
            }

            File staging = new File(libexecDir, "bun.new");
            if (staging.exists() && !staging.delete()) {
                Logger.logWarn(LOG_TAG, "Could not delete old staging bun");
            }

            boolean extracted = false;
            try (ZipInputStream zip = new ZipInputStream(
                    new ByteArrayInputStream(zipBytes))) {
                ZipEntry entry;
                byte[] buffer = new byte[8192];
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (entry.isDirectory()) {
                        continue;
                    }
                    if (!name.endsWith("/bun") && !name.equals("bun")) {
                        continue;
                    }
                    try (FileOutputStream out = new FileOutputStream(staging)) {
                        int n;
                        while ((n = zip.read(buffer)) != -1) {
                            out.write(buffer, 0, n);
                        }
                    }
                    extracted = true;
                    break;
                }
            }

            if (!extracted || !staging.isFile()) {
                Logger.logError(LOG_TAG, "Bun binary not found inside bundled zip");
                //noinspection ResultOfMethodCallIgnored
                staging.delete();
                return;
            }

            //noinspection OctalInteger
            Os.chmod(staging.getAbsolutePath(), 0700);
            if (bunReal.exists() && !bunReal.delete()) {
                Logger.logWarn(LOG_TAG, "Could not replace existing libexec bun");
            }
            if (!staging.renameTo(bunReal)) {
                throw new RuntimeException("Failed to move bun into libexec");
            }

            // Remove legacy ELF that used to live at $PREFIX/bin/bun.
            File legacyBin = new File(binDir, "bun");
            if (legacyBin.isFile() && isAndroidBunBinary(legacyBin)) {
                //noinspection ResultOfMethodCallIgnored
                legacyBin.delete();
            }

            File stampDir = stamp.getParentFile();
            if (stampDir != null && !stampDir.exists() && !stampDir.mkdirs()) {
                Logger.logWarn(LOG_TAG, "Could not create stamp directory");
            }
            try (FileOutputStream out = new FileOutputStream(stamp)) {
                out.write(BUNDLED_BUN_VERSION.getBytes(
                    java.nio.charset.StandardCharsets.UTF_8));
            }

            Logger.logInfo(LOG_TAG,
                "Installed Bun " + BUNDLED_BUN_VERSION + " → " + bunReal);

            ensureWorkspaceDirs();
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Bun install failed", e);
        }

        installShellHelpers();
    }

    private static void ensureWorkspaceDirs() {
        ensureDir(new File(TermuxConstants.TERMUX_HOME_DIR_PATH,
            ".bun/install/cache"));
        ensureDir(new File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".bun/bin"));
        ensureDir(new File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".cache"));
        ensureDir(new File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".npm"));
        ensureDir(new File(TermuxConstants.TERMUX_HOME_DIR_PATH, "repos"));
    }

    /**
     * Writes {@code $PREFIX/bin/bun} and {@code bunx} wrappers. No per-package
     * helpers — all CLIs go through the single shim.
     */
    private static void installShellHelpers() {
        File binDir = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH);
        if (!binDir.isDirectory()) {
            return;
        }
        String prefix = TermuxConstants.TERMUX_PREFIX_DIR_PATH;
        String home = TermuxConstants.TERMUX_HOME_DIR_PATH;
        String bash = prefix + "/bin/bash";

        // Drop obsolete per-package helper if present from earlier builds.
        File oldCreateExpo = new File(binDir, "create-expo");
        if (oldCreateExpo.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            oldCreateExpo.delete();
        }

        String bunWrapper = ""
            + "#!" + bash + "\n"
            + "PREFIX=\"" + prefix + "\"\n"
            + "HOME=\"" + home + "\"\n"
            + "REAL=\"$PREFIX/libexec/bun\"\n"
            + "export PATH=\"$PREFIX/bin:$HOME/.bun/bin:$PATH\"\n"
            + "export LD_LIBRARY_PATH=\"$PREFIX/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}\"\n"
            + "export OPENSSL_CONF=\"$PREFIX/etc/tls/openssl.cnf\"\n"
            + "export SSL_CERT_FILE=\"$PREFIX/etc/tls/cert.pem\"\n"
            + "export NODE_EXTRA_CA_CERTS=\"$PREFIX/etc/tls/cert.pem\"\n"
            + "export BUN_INSTALL=\"$PREFIX\"\n"
            + "export BUN_INSTALL_BIN=\"$PREFIX/bin\"\n"
            + "export BUN_INSTALL_CACHE_DIR=\"$HOME/.bun/install/cache\"\n"
            + "export BUN_TMPDIR=\"$PREFIX/tmp\"\n"
            + "export TMPDIR=\"$PREFIX/tmp\"\n"
            + "export XDG_CACHE_HOME=\"$HOME/.cache\"\n"
            + "export npm_config_cache=\"$HOME/.npm\"\n"
            + "export npm_config_prefix=\"$PREFIX\"\n"
            + "export npm_config_platform=android\n"
            + "export npm_config_os=android\n"
            + "case \"$(uname -m)\" in\n"
            + "  aarch64|arm64) export npm_config_arch=arm64; export npm_config_cpu=arm64 ;;\n"
            + "  x86_64|amd64)  export npm_config_arch=x64;   export npm_config_cpu=x64 ;;\n"
            + "  *)             export npm_config_arch=arm64; export npm_config_cpu=arm64 ;;\n"
            + "esac\n"
            + "mkdir -p \"$BUN_INSTALL_CACHE_DIR\" \"$HOME/.bun/bin\" \"$HOME/repos\" \"$TMPDIR\"\n"
            + "if [ ! -x \"$REAL\" ]; then\n"
            + "  echo \"bun: missing Android binary at $REAL (reopen the app)\" >&2\n"
            + "  exit 127\n"
            + "fi\n"
            // Drop path redirector for Bun — LD_PRELOAD + optional linux natives → SIGSYS.
            // Explicit empty LD_PRELOAD= (redirector honors clear on execve).
            + "set -- \"$@\"\n"
            + "cmd=\"${1-}\"\n"
            // Force Android optionalDependency filter. Bun reports platform=android but
            // still resolves linux-* natives (e.g. @rolldown/binding-linux-arm-gnueabihf)
            // for many scaffolds / Windows lockfiles → SIGSYS 31 on extract/link.
            + "case \"$cmd\" in\n"
            + "  install|i|add|update|remove|rm|create)\n"
            + "    shift\n"
            + "    has_os=0; has_cpu=0\n"
            + "    for a in \"$@\"; do\n"
            + "      case \"$a\" in --os|--os=*) has_os=1 ;; --cpu|--cpu=*) has_cpu=1 ;; esac\n"
            + "    done\n"
            + "    extra=\"\"\n"
            + "    [ \"$has_os\" = 0 ] && extra=\"$extra --os=android\"\n"
            + "    if [ \"$has_cpu\" = 0 ]; then\n"
            + "      case \"$(uname -m)\" in aarch64|arm64) extra=\"$extra --cpu=arm64\" ;;\n"
            + "        x86_64|amd64) extra=\"$extra --cpu=x64\" ;; *) extra=\"$extra --cpu=arm64\" ;; esac\n"
            + "    fi\n"
            + "    # shellcheck disable=SC2086\n"
            + "    LD_PRELOAD= exec \"$REAL\" \"$cmd\"$extra \"$@\"\n"
            + "    ;;\n"
            + "esac\n"
            + "LD_PRELOAD= exec \"$REAL\" \"$@\"\n";
        writeExec(new File(binDir, "bun"), bunWrapper);

        String bunx = ""
            + "#!" + bash + "\n"
            + "exec \"" + prefix + "/bin/bun\" x \"$@\"\n";
        writeExec(new File(binDir, "bunx"), bunx);

        String bunDoctor = ""
            + "#!" + bash + "\n"
            + "PREFIX=\"" + prefix + "\"\n"
            + "echo \"wrapper: $PREFIX/bin/bun\"\n"
            + "echo \"real:    $PREFIX/libexec/bun\"\n"
            + "ls -la \"$PREFIX/bin/bun\" \"$PREFIX/libexec/bun\" 2>&1\n"
            + "if command -v readelf >/dev/null; then\n"
            + "  readelf -l \"$PREFIX/libexec/bun\" 2>/dev/null | grep -A1 INTERP || true\n"
            + "fi\n"
            + "\"$PREFIX/bin/bun\" --version\n"
            + "echo \"LD_PRELOAD in shell: ${LD_PRELOAD:-unset}\"\n"
            + "echo \"OPENSSL_CONF=${OPENSSL_CONF:-unset}\"\n";
        writeExec(new File(binDir, "bun-doctor"), bunDoctor);

        ensureWorkspaceDirs();
        repairPrefixBinPermissions();
    }

    /**
     * Some package files land without the execute bit (dpkg extract / noexec
     * quirks). {@code npm doctor} flags these; fix known PREFIX/bin entries.
     */
    private static void repairPrefixBinPermissions() {
        File binDir = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH);
        if (!binDir.isDirectory()) {
            return;
        }
        String[] names = {"am.termuxam", "ksu", "am", "login", "apt", "apt-get", "dpkg"};
        for (String name : names) {
            File f = new File(binDir, name);
            if (!f.isFile()) {
                continue;
            }
            try {
                //noinspection OctalInteger
                Os.chmod(f.getAbsolutePath(), 0700);
            } catch (Exception e) {
                Logger.logWarn(LOG_TAG, "chmod " + f + ": " + e.getMessage());
            }
        }
    }

    private static void writeExec(@NonNull File dest, @NonNull String contents) {
        try {
            File tmp = new File(dest.getAbsolutePath() + ".new");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(contents.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            //noinspection OctalInteger
            Os.chmod(tmp.getAbsolutePath(), 0700);
            if (dest.exists() && !dest.delete()) {
                // Symlink (old bunx→bun) or busy file
                try {
                    Os.remove(dest.getAbsolutePath());
                } catch (Exception e) {
                    Logger.logWarn(LOG_TAG, "Could not replace " + dest + ": " + e.getMessage());
                }
            }
            if (!tmp.renameTo(dest)) {
                Logger.logWarn(LOG_TAG, "Could not install " + dest);
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        } catch (Exception e) {
            Logger.logWarn(LOG_TAG, "Helper install failed for " + dest + ": " + e.getMessage());
        }
    }

    private static void ensureDir(@NonNull File dir) {
        if (!dir.exists() && !dir.mkdirs()) {
            Logger.logWarn(LOG_TAG, "Could not create " + dir);
        }
    }

    /**
     * Official Android builds use the Bionic linker ({@code /system/bin/linker64} or
     * {@code linker}). glibc linux builds request {@code /lib/ld-linux-*.so.1}.
     */
    private static boolean isAndroidBunBinary(@NonNull File bunBin) {
        try (FileInputStream in = new FileInputStream(bunBin)) {
            byte[] head = new byte[4096];
            int n = in.read(head);
            if (n < 64) {
                return false;
            }
            if (head[0] != 0x7f || head[1] != 'E' || head[2] != 'L' || head[3] != 'F') {
                return false;
            }
            String probe = new String(head, 0, n, java.nio.charset.StandardCharsets.ISO_8859_1);
            if (probe.contains("/system/bin/linker")) {
                return true;
            }
            return !probe.contains("ld-linux") && !probe.contains("ld-musl");
        } catch (Exception e) {
            Logger.logWarn(LOG_TAG, "Could not inspect bun ELF: " + e.getMessage());
            return false;
        }
    }

    private static String readStamp(@NonNull File stamp) throws Exception {
        byte[] buf = new byte[(int) Math.min(stamp.length(), 64)];
        try (FileInputStream in = new FileInputStream(stamp)) {
            int n = in.read(buf);
            if (n <= 0) {
                return "";
            }
            return new String(buf, 0, n,
                java.nio.charset.StandardCharsets.UTF_8).trim();
        }
    }

    private static byte[] loadZipBytes() {
        System.loadLibrary("invapp-bun");
        return getZip();
    }

    private static native byte[] getZip();
}
