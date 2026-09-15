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
 * Installs the official Android Bun binary into {@code $PREFIX/bin/bun}.
 *
 * <p>Uses oven-sh {@code bun-linux-*-android.zip} artifacts (Bionic PIE), not the
 * glibc Linux builds — those die with SIGSYS (signal 31) on Android.
 */
public final class TermuxBunInstaller {

    private static final String LOG_TAG = "TermuxBunInstaller";

    /** Must match the zips downloaded in {@code app/build.gradle}. */
    public static final String BUNDLED_BUN_VERSION = "1.4.2";

    private TermuxBunInstaller() {}

    /**
     * Extract bundled Bun into the Termux prefix when missing or outdated.
     * Safe to call on every app start; no-ops when already current.
     */
    public static void installIfNeeded(@NonNull Context context) {
        File binDir = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH);
        if (!binDir.isDirectory()) {
            Logger.logWarn(LOG_TAG, "Prefix bin missing; skip Bun install");
            return;
        }

        File bunBin = new File(binDir, "bun");
        File stamp = new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH,
            "var/lib/invapp/bun.version");

        if (bunBin.isFile() && bunBin.canExecute() && stamp.isFile()
                && isAndroidBunBinary(bunBin)) {
            try {
                String installed = readStamp(stamp);
                if (BUNDLED_BUN_VERSION.equals(installed)) {
                    installShellHelpers();
                    return;
                }
            } catch (Exception ignored) {
                // Reinstall below.
            }
        } else if (bunBin.isFile() && !isAndroidBunBinary(bunBin)) {
            Logger.logWarn(LOG_TAG,
                "Replacing non-Android Bun at " + bunBin
                    + " (curl|bash installs glibc linux builds which fail with "
                    + "'required file not found' / SIGSYS 31)");
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
            File staging = new File(binDir, "bun.new");
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
                    // Official layout: bun-linux-*-android/bun
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
            if (bunBin.exists() && !bunBin.delete()) {
                Logger.logWarn(LOG_TAG, "Could not replace existing bun");
            }
            if (!staging.renameTo(bunBin)) {
                throw new RuntimeException("Failed to move bun into place");
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
                "Installed Bun " + BUNDLED_BUN_VERSION + " → " + bunBin);

            ensureDir(new File(TermuxConstants.TERMUX_HOME_DIR_PATH,
                ".bun/install/cache"));
            ensureDir(new File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".cache"));
            ensureDir(new File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".npm"));
            ensureDir(new File(TermuxConstants.TERMUX_HOME_DIR_PATH, "repos"));
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Bun install failed", e);
        }

        // Always refresh helpers (cheap) so curl|bash damage and missing
        // OPENSSL_CONF workarounds stay applied.
        installShellHelpers();
    }

    /**
     * Writes small shell helpers into {@code $PREFIX/bin} so Expo scaffolding
     * works without {@code bunx} hitting SIGSYS / noexec shared-storage paths.
     */
    private static void installShellHelpers() {
        File binDir = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH);
        if (!binDir.isDirectory()) {
            return;
        }
        String prefix = TermuxConstants.TERMUX_PREFIX_DIR_PATH;
        String home = TermuxConstants.TERMUX_HOME_DIR_PATH;
        // Force Android bun + openssl; run create-expo under bun (not node shebang).
        String createExpo = ""
            + "#!/data/data/com.involvex.termux_app/files/usr/bin/bash\n"
            + "set -e\n"
            + "PREFIX=\"" + prefix + "\"\n"
            + "HOME=\"" + home + "\"\n"
            + "export PATH=\"$PREFIX/bin:$PATH\"\n"
            + "export LD_LIBRARY_PATH=\"$PREFIX/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}\"\n"
            + "export OPENSSL_CONF=\"$PREFIX/etc/tls/openssl.cnf\"\n"
            + "export SSL_CERT_FILE=\"$PREFIX/etc/tls/cert.pem\"\n"
            + "export BUN_INSTALL_CACHE_DIR=\"$HOME/.bun/install/cache\"\n"
            + "export BUN_TMPDIR=\"$PREFIX/tmp\"\n"
            + "export TMPDIR=\"$PREFIX/tmp\"\n"
            + "export npm_config_cache=\"$HOME/.npm\"\n"
            + "mkdir -p \"$BUN_INSTALL_CACHE_DIR\" \"$HOME/repos\" \"$TMPDIR\" "
            + "\"$HOME/.local/share/create-expo-runner\"\n"
            + "case \"$PWD\" in\n"
            + "  /storage/*|/sdcard/*|*/storage/shared/*)\n"
            + "    echo \"create-expo: refuse shared/noexec storage. Use: cd ~/repos\" >&2\n"
            + "    exit 1\n"
            + "    ;;\n"
            + "esac\n"
            + "CALLER_PWD=$PWD\n"
            + "RUNNER=\"$HOME/.local/share/create-expo-runner\"\n"
            + "ENTRY=\"$RUNNER/node_modules/create-expo/build/index.js\"\n"
            + "if [ ! -f \"$ENTRY\" ]; then\n"
            + "  echo \"create-expo: installing CLI into $RUNNER ...\"\n"
            + "  (cd \"$RUNNER\" && \"$PREFIX/bin/bun\" add create-expo@latest)\n"
            + "fi\n"
            + "if ! \"$PREFIX/bin/bun\" --version >/dev/null 2>&1; then\n"
            + "  echo \"create-expo: bun broken — reopen the app to restore Android bun\" >&2\n"
            + "  exit 1\n"
            + "fi\n"
            + "cd \"$CALLER_PWD\"\n"
            + "exec \"$PREFIX/bin/bun\" \"$ENTRY\" \"$@\"\n";
        writeExec(new File(binDir, "create-expo"), createExpo);

        String bunDoctor = ""
            + "#!/data/data/com.involvex.termux_app/files/usr/bin/bash\n"
            + "PREFIX=\"" + prefix + "\"\n"
            + "B=\"$PREFIX/bin/bun\"\n"
            + "echo \"bun: $B\"\n"
            + "ls -la \"$B\"\n"
            + "if command -v readelf >/dev/null; then\n"
            + "  readelf -l \"$B\" 2>/dev/null | grep -A1 INTERP || true\n"
            + "fi\n"
            + "\"$B\" --version\n"
            + "echo \"OPENSSL_CONF=${OPENSSL_CONF:-unset}\"\n";
        writeExec(new File(binDir, "bun-doctor"), bunDoctor);

        // Replace bun→bunx symlink: stock bunx hits SIGSYS (signal 31) on Android
        // when spawning package bins. Install packages under ~/.local/share/bunx-runner
        // and exec the package bin with Android bun instead.
        String bunx = ""
            + "#!/data/data/com.involvex.termux_app/files/usr/bin/bash\n"
            + "set -e\n"
            + "PREFIX=\"" + prefix + "\"\n"
            + "HOME=\"" + home + "\"\n"
            + "export PATH=\"$PREFIX/bin:$HOME/.bun/bin:$PATH\"\n"
            + "export LD_LIBRARY_PATH=\"$PREFIX/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}\"\n"
            + "export OPENSSL_CONF=\"$PREFIX/etc/tls/openssl.cnf\"\n"
            + "export SSL_CERT_FILE=\"$PREFIX/etc/tls/cert.pem\"\n"
            + "export BUN_INSTALL_CACHE_DIR=\"$HOME/.bun/install/cache\"\n"
            + "export BUN_TMPDIR=\"$PREFIX/tmp\"\n"
            + "export TMPDIR=\"$PREFIX/tmp\"\n"
            + "export npm_config_cache=\"$HOME/.npm\"\n"
            + "BUN=\"$PREFIX/bin/bun\"\n"
            + "if [ $# -lt 1 ]; then\n"
            + "  echo \"usage: bunx <package[@version]> [args...]\" >&2\n"
            + "  exit 1\n"
            + "fi\n"
            + "PKG=\"$1\"; shift\n"
            + "while [ \"$PKG\" = \"--bun\" ] || [ \"$PKG\" = \"-b\" ] || [ \"$PKG\" = \"--package\" ] || [ \"$PKG\" = \"-p\" ]; do\n"
            + "  if [ \"$PKG\" = \"--package\" ] || [ \"$PKG\" = \"-p\" ]; then PKG=\"$1\"; shift; else PKG=\"$1\"; shift || true; fi\n"
            + "done\n"
            + "if [ -z \"$PKG\" ] || [ \"${PKG#-}\" != \"$PKG\" ]; then\n"
            + "  echo \"bunx: missing package name\" >&2\n"
            + "  exit 1\n"
            + "fi\n"
            + "SAFE=$(printf '%s' \"$PKG\" | sed 's/[^A-Za-z0-9._@+-]/_/g')\n"
            + "RUNNER=\"$HOME/.local/share/bunx-runner/$SAFE\"\n"
            + "mkdir -p \"$RUNNER\" \"$HOME/.bun/bin\"\n"
            + "CALLER_PWD=$PWD\n"
            + "if [ ! -f \"$RUNNER/package.json\" ]; then\n"
            + "  echo \"bunx: installing $PKG ...\" >&2\n"
            + "  (cd \"$RUNNER\" && \"$BUN\" add \"$PKG\")\n"
            + "fi\n"
            + "ENTRY=$(cd \"$RUNNER\" && \"$BUN\" -e '\n"
            + "const fs=require(\"fs\"); const path=require(\"path\");\n"
            + "let spec=process.argv[1]; let name=spec;\n"
            + "if(name.startsWith(\"@\")){const i=name.indexOf(\"@\",1); if(i>0) name=name.slice(0,i);}\n"
            + "else {const i=name.indexOf(\"@\"); if(i>0) name=name.slice(0,i);}\n"
            + "const pjPath=path.join(\"node_modules\",...name.split(\"/\"),\"package.json\");\n"
            + "if(!fs.existsSync(pjPath)) { console.error(\"bunx: package not found at \"+pjPath); process.exit(1); }\n"
            + "const pj=JSON.parse(fs.readFileSync(pjPath,\"utf8\"));\n"
            + "let bin=pj.bin; if(!bin){console.error(\"bunx: package has no bin\"); process.exit(1);}\n"
            + "if(typeof bin===\"object\") bin=Object.values(bin)[0];\n"
            + "console.log(path.resolve(path.join(\"node_modules\",...name.split(\"/\"),bin)));\n"
            + "' \"$PKG\")\n"
            + "cd \"$CALLER_PWD\"\n"
            + "exec \"$BUN\" \"$ENTRY\" \"$@\"\n";
        writeExec(new File(binDir, "bunx"), bunx);

        ensureDir(new File(home, ".bun/bin"));
        ensureDir(new File(home, "repos"));
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
                Logger.logWarn(LOG_TAG, "Could not replace " + dest);
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
     * {@code linker}). glibc linux builds request {@code /lib/ld-linux-*.so.1} and fail
     * at exec with "required file not found".
     */
    private static boolean isAndroidBunBinary(@NonNull File bunBin) {
        try (FileInputStream in = new FileInputStream(bunBin)) {
            byte[] head = new byte[4096];
            int n = in.read(head);
            if (n < 64) {
                return false;
            }
            // ELF magic
            if (head[0] != 0x7f || head[1] != 'E' || head[2] != 'L' || head[3] != 'F') {
                return false;
            }
            String probe = new String(head, 0, n, java.nio.charset.StandardCharsets.ISO_8859_1);
            if (probe.contains("/system/bin/linker")) {
                return true;
            }
            // Reject known glibc / musl interpreters
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
