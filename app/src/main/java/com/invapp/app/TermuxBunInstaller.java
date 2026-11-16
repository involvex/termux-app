package com.invapp.app;

import android.content.Context;
import android.system.Os;

import androidx.annotation.NonNull;

import com.invapp.shared.logger.Logger;
import com.invapp.shared.termux.TermuxConstants;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Installs the official Android Bun binary and a single {@code $PREFIX/bin/bun}
 * wrapper.
 *
 * <p>Real binary lives at {@code $PREFIX/libexec/bun}. The {@code bin/bun}
 * wrapper drops a bare path-redirector-only preload and instead loads
 * {@code libinvapp-bun-seccomp.so} (+ redirector for {@code #!/usr/bin/env}
 * shebang rewrite so bunx/package bins work). Sets OPENSSL + Android
 * {@code --os}/{@code --cpu} install filters.
 *
 * <p>Uses oven-sh {@code bun-linux-*-android.zip} (Bionic PIE), not glibc Linux
 * builds.
 */
public final class TermuxBunInstaller {

    private static final String LOG_TAG = "TermuxBunInstaller";

    /** Must match the zips downloaded in {@code app/build.gradle}. */
    public static final String BUNDLED_BUN_VERSION = "1.4.4";

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
                    installShellHelpers(context);
                    installOpencodeShimFromAssets(context);
                    installLitertGlStubsFromAssets(context);
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

        installShellHelpers(context);
        installOpencodeShimFromAssets(context);
        installLitertGlStubsFromAssets(context);
    }

    /**
     * Ships a prebuilt glibc {@code LD_PRELOAD} shim so OpenCode can resolve DNS
     * without {@code gcc-glibc}. Stock glibc hardcodes {@code /data/data/com.termux}
     * paths; the shim intercepts {@code gethostbyname2}/{@code getaddrinfo} and
     * queries 8.8.8.8 directly (and-code instead uses musl OpenCode under proot).
     */
    private static void installOpencodeShimFromAssets(@NonNull Context context) {
        String abi = android.os.Build.SUPPORTED_ABIS.length > 0
            ? android.os.Build.SUPPORTED_ABIS[0] : "";
        String asset;
        if ("arm64-v8a".equals(abi) || "aarch64".equals(abi)) {
            asset = "opencode/libinvapp-opencode-shim-aarch64.so";
        } else if ("x86_64".equals(abi)) {
            // Built on demand via opencode-setup when gcc-glibc is present.
            return;
        } else {
            return;
        }
        File libDir = new File(TermuxConstants.TERMUX_LIB_PREFIX_DIR_PATH);
        File dest = new File(libDir, "libinvapp-opencode-shim.so");
        try {
            if (!libDir.exists() && !libDir.mkdirs()) {
                Logger.logWarn(LOG_TAG, "Could not create " + libDir);
                return;
            }
            try (java.io.InputStream in = context.getAssets().open(asset);
                 FileOutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
            }
            //noinspection OctalInteger
            Os.chmod(dest.getAbsolutePath(), 0755);
            Logger.logInfo(LOG_TAG, "Installed OpenCode DNS shim → " + dest);
        } catch (Exception e) {
            Logger.logWarn(LOG_TAG, "OpenCode shim asset install failed: " + e.getMessage());
        }
    }

    /**
     * Ships no-op EGL/GLES stubs so the LiteRT-LM native engine can be loaded.
     *
     * <p>{@code litert-lm-api} (every Android wheel, 0.14 → 0.18) links
     * {@code libGLESv2.so}, {@code libEGL.so} and {@code libGLESv3.so}. The
     * system {@code libGLESv3.so} has an undefined reference to
     * {@code android::egl_get_connection()}, a <em>vendor</em> libEGL symbol an
     * app process cannot resolve, so {@code dlopen()} of the engine dies with
     * {@code cannot locate symbol "_ZN7android18egl_get_connectionEv"}. The CPU
     * backend never calls GL, so stubs with the system SONAMEs resolve the
     * engine's references and the loader stops pulling in the graphics stack.
     *
     * <p>Installed to {@code $PREFIX/lib/litert-lm-gl/} (never
     * {@code $PREFIX/lib}, where every process would see them) and preloaded by
     * the {@code litert-lm} wrapper only.
     */
    private static void installLitertGlStubsFromAssets(@NonNull Context context) {
        String abi = android.os.Build.SUPPORTED_ABIS.length > 0
            ? android.os.Build.SUPPORTED_ABIS[0] : "";
        String assetDir;
        if ("arm64-v8a".equals(abi) || "aarch64".equals(abi)) {
            assetDir = "arm64-v8a";
        } else if ("x86_64".equals(abi)) {
            assetDir = "x86_64";
        } else {
            return; // 32-bit ABIs have no litert-lm-api wheel.
        }
        File destDir = new File(TermuxConstants.TERMUX_LIB_PREFIX_DIR_PATH,
            "litert-lm-gl");
        for (String soname : new String[] {"libEGL.so", "libGLESv2.so", "libGLESv3.so"}) {
            File dest = new File(destDir, soname);
            if (!ShellLibraryInstaller.writeAssetToFile(
                    context, "litert-lm-gl/" + assetDir + "/" + soname, dest, 0644)) {
                Logger.logWarn(LOG_TAG, "LiteRT-LM GL stub install failed: " + soname);
            }
        }
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
    private static void installShellHelpers(@NonNull Context context) {
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

        // bun wrapper is now shared with the container: assets/invapp/bin/bun.
        // It sources env.sh (PREFIX/HOME/etc.), loads the seccomp + redirector
        // shims only when present (device), and forces Android --os/--cpu. On the
        // device the portable #!@BASH@ shebang is resolved here to the app's bash,
        // identical to the previous "#!" + bash inline form. The real binary is
        // unchanged ($PREFIX/libexec/bun).
        writeExec(new File(binDir, "bun"),
                assetWithBashShebang(context, "invapp/bin/bun", bash));

        // bunx must run package bins under Bun. Stock `bun x` execs the bin
        // shebang (#!/usr/bin/env node); without node that exits 127 and often
        // prints nothing — the "silent fail" users hit for create-vite / serve.
        writeExec(new File(binDir, "bunx"),
                assetWithBashShebang(context, "invapp/bin/bunx", bash));

        // Provide `node` → bun when nodejs package is not installed so
        // #!/usr/bin/env node scripts (npm bins, bunx children) actually run.
        ensureNodeBunShim(context, binDir, bash);

        // Relocate ELF proot → libexec/proot-bin and seed fork-safe wrapper so
        // proot-distro / pd / td-resource-linux do not ENOENT guest bash under
        // the path redirector.
        ensureProotWrapper(context, bash);

        writeExec(new File(binDir, "bun-doctor"),
                assetWithBashShebang(context, "invapp/bin/bun-doctor", bash));
        writeExec(new File(binDir, "bun-version"),
                assetWithBashShebang(context, "invapp/bin/bun-version", bash));
        writeExec(new File(binDir, "bun-upgrade"),
                assetWithBashShebang(context, "invapp/bin/bun-upgrade", bash));

        // OpenCode V2 bootstrap (optional AI CLI → Preview).
        // Do NOT bun-install opencode-ai: bare libexec/bun hits SIGSYS (seccomp),
        // and bun's global bin is a JS stub that demands postinstall. Fetch the
        // official V2 linux-glibc tarball from opencode.ai/files/bin, then wrap
        // with ld-linux. Version resolves live via
        // https://opencode.ai/update/api/latest/cli/npm (fallback 2.0.6);
        // OPENCODE_VERSION / --version pins, 1.x pins still use the old GitHub
        // anomalyco/opencode releases for rollback. See https://opencode.ai/v2/docs/.
        // glibc lives in glibc-repo (not tur-repo); trusted=yes for apt-key flakes.
        writeExec(new File(binDir, "opencode-setup"),
                assetWithBashShebang(context, "invapp/bin/opencode-setup", bash));

        // Fast path: rebuild DNS shim + seed resolv/CA without re-downloading OpenCode.
        writeExec(new File(binDir, "opencode-fix-net"),
                assetWithBashShebang(context, "invapp/bin/opencode-fix-net", bash));

        // OpenJDK 17 via pkg (Termux-built JVM). Not embedded in the APK (~96 MiB deb
        // per arch); use td-jdk-setup for recommends + recovery from common failures.
        String tdJdkSetup = ""
            + "#!" + bash + "\n"
            + "set -e\n"
            + "PREFIX=\"" + prefix + "\"\n"
            + "HOME=\"" + home + "\"\n"
            + "export PATH=\"$PREFIX/bin:$HOME/.bun/bin:$PATH\"\n"
            + "REDIRECTOR_SO=\"$PREFIX/lib/libinvapp-redirector.so\"\n"
            + "[ -f \"$REDIRECTOR_SO\" ] && export LD_PRELOAD=\"$REDIRECTOR_SO${LD_PRELOAD:+:$LD_PRELOAD}\"\n"
            + "export TMPDIR=\"${TMPDIR:-$PREFIX/tmp}\"\n"
            + "mkdir -p \"$TMPDIR\"\n"
            + "if [ -x \"$PREFIX/bin/invapp-openjdk-recover\" ]; then\n"
            + "  \"$PREFIX/bin/invapp-openjdk-recover\" || true\n"
            + "fi\n"
            + "JDK_HOME=\"$PREFIX/lib/jvm/java-17-openjdk\"\n"
            + "if [ -x \"$JDK_HOME/bin/java\" ]; then\n"
            + "  if \"$JDK_HOME/bin/java\" -version 2>&1 | head -1; then\n"
            + "    echo \"OpenJDK 17 already installed at $JDK_HOME\"\n"
            + "    echo \"JAVA_HOME=$JDK_HOME\"\n"
            + "    exit 0\n"
            + "  fi\n"
            + "fi\n"
            + "echo \"td-jdk-setup: installing Termux OpenJDK 17 (not Debian/Ubuntu JDK)…\"\n"
            + "echo \"Note: ~220 MiB on disk; 'Setting up openjdk-17' may sit on update-alternatives for a few minutes.\"\n"
            + "if ! command -v pkg >/dev/null 2>&1; then\n"
            + "  echo \"td-jdk-setup: pkg missing — finish bootstrap first\" >&2\n"
            + "  exit 1\n"
            + "fi\n"
            + "if [ -x \"$PREFIX/bin/ivx-fix-dpkg-scripts\" ]; then\n"
            + "  \"$PREFIX/bin/ivx-fix-dpkg-scripts\" || true\n"
            + "elif [ -x \"$PREFIX/bin/invapp-fix-dpkg-scripts\" ]; then\n"
            + "  \"$PREFIX/bin/invapp-fix-dpkg-scripts\" || true\n"
            + "fi\n"
            + "pkg update -y || apt-get update -y\n"
            + "dpkg --configure -a 2>/dev/null || true\n"
            + "deps=\"libandroid-shmem libandroid-spawn libiconv libjpeg-turbo zlib littlecms alsa-plugins\"\n"
            + "recs=\"ca-certificates-java openjdk-17-x resolv-conf\"\n"
            + "# Install openjdk-17 before -x (avoids broken Depends if preinst fails).\n"
            + "if ! pkg install -y $deps openjdk-17; then\n"
            + "  echo \"td-jdk-setup: pkg install openjdk-17 failed — trying repack helper…\" >&2\n"
            + "  if [ -x \"$PREFIX/bin/invapp-openjdk-repack-install\" ]; then\n"
            + "    \"$PREFIX/bin/invapp-openjdk-repack-install\" || true\n"
            + "  fi\n"
            + "fi\n"
            + "if [ ! -x \"$JDK_HOME/bin/java\" ]; then\n"
            + "  apt-get install -y $deps openjdk-17 2>/dev/null || true\n"
            + "fi\n"
            + "if [ -x \"$JDK_HOME/bin/java\" ]; then\n"
            + "  pkg install -y $recs 2>/dev/null || apt-get install -y $recs 2>/dev/null || true\n"
            + "fi\n"
            + "dpkg --configure -a 2>/dev/null || true\n"
            + "if [ ! -x \"$JDK_HOME/bin/java\" ]; then\n"
            + "  echo \"td-jdk-setup: java binary missing after install\" >&2\n"
            + "  echo \"Run: jdk-doctor   then: pkg install -f openjdk-17\" >&2\n"
            + "  exit 1\n"
            + "fi\n"
            + "if ! \"$JDK_HOME/bin/java\" -version 2>&1; then\n"
            + "  echo \"td-jdk-setup: java -version failed (Bad system call?)\" >&2\n"
            + "  echo \"Use only pkg openjdk-17 — not proot/apt Debian JDK on the host prefix.\" >&2\n"
            + "  echo \"Ensure libandroid-shmem is installed: pkg install libandroid-shmem\" >&2\n"
            + "  exit 1\n"
            + "fi\n"
            + "echo \"OK. JAVA_HOME=$JDK_HOME\"\n"
            + "echo \"New shell sessions load JAVA_HOME from $JDK_HOME/etc/profile.d/java.sh\"\n";
        writeExec(new File(binDir, "td-jdk-setup"), tdJdkSetup);

        // ivx-fix-dpkg-scripts (+ invapp- alias) seeded by IvxConfigureInstaller.

        String openjdkRecover = ""
            + "#!" + bash + "\n"
            + "PREFIX=\"" + prefix + "\"\n"
            + "export PATH=\"$PREFIX/bin:$PATH\"\n"
            + "REDIRECTOR_SO=\"$PREFIX/lib/libinvapp-redirector.so\"\n"
            + "[ -f \"$REDIRECTOR_SO\" ] && export LD_PRELOAD=\"$REDIRECTOR_SO${LD_PRELOAD:+:$LD_PRELOAD}\"\n"
            + "echo \"invapp-openjdk-recover: clearing broken openjdk-17 / openjdk-17-x state…\"\n"
            + "dpkg --remove --force-remove-reinstreq openjdk-17-x 2>/dev/null || true\n"
            + "dpkg --remove --force-remove-reinstreq openjdk-17 2>/dev/null || true\n"
            + "dpkg --purge --force-remove-reinstreq openjdk-17-x 2>/dev/null || true\n"
            + "dpkg --purge --force-remove-reinstreq openjdk-17 2>/dev/null || true\n"
            + "rm -rf \"$PREFIX/var/lib/dpkg/tmp.ci\"/* 2>/dev/null || true\n"
            + "apt --fix-broken install -y 2>/dev/null || true\n"
            + "dpkg --configure -a 2>/dev/null || true\n"
            + "echo \"invapp-openjdk-recover: done (check: dpkg -l | grep openjdk)\"\n";
        writeExec(new File(binDir, "invapp-openjdk-recover"), openjdkRecover);

        String openjdkRepackInstall = ""
            + "#!" + bash + "\n"
            + "set -e\n"
            + "PREFIX=\"" + prefix + "\"\n"
            + "export PATH=\"$PREFIX/bin:$PATH\"\n"
            + "REDIRECTOR_SO=\"$PREFIX/lib/libinvapp-redirector.so\"\n"
            + "[ -f \"$REDIRECTOR_SO\" ] && export LD_PRELOAD=\"$REDIRECTOR_SO${LD_PRELOAD:+:$LD_PRELOAD}\"\n"
            + "DEB=\"\"\n"
            + "for c in \"$HOME/../cache/apt/archives/openjdk-17_\"*_aarch64.deb \\\n"
            + "         \"$PREFIX/../cache/apt/archives/openjdk-17_\"*_aarch64.deb; do\n"
            + "  [ -f \"$c\" ] && DEB=\"$c\" && break\n"
            + "done\n"
            + "if [ -z \"$DEB\" ]; then\n"
            + "  echo \"invapp-openjdk-repack-install: download deb first (pkg install openjdk-17)\" >&2\n"
            + "  exit 1\n"
            + "fi\n"
            + "WORK=\"$PREFIX/tmp/openjdk-repack\"\n"
            + "rm -rf \"$WORK\" && mkdir -p \"$WORK\" && cd \"$WORK\"\n"
            + "ar x \"$DEB\"\n"
            + "tar xf control.tar.xz\n"
            + "for s in preinst postinst prerm postrm; do\n"
            + "  [ -f \"$s\" ] || continue\n"
            + "  if ! head -1 \"$s\" | grep -q '^#!'; then\n"
            + "    { echo \"#!$PREFIX/bin/sh\"; cat \"$s\"; } > \"$s.new\" && mv \"$s.new\" \"$s\"\n"
            + "  fi\n"
            + "  sed -i \"s/com.termux/com.involvex.termux_app/g\" \"$s\" 2>/dev/null || true\n"
            + "  chmod 700 \"$s\"\n"
            + "done\n"
            + "tar -cJf control.tar.xz ./*\n"
            + "printf '2.0\\n' > debian-binary\n"
            + "ar cr openjdk-17-fixed.deb debian-binary control.tar.xz data.tar.xz\n"
            + "dpkg -i ./openjdk-17-fixed.deb\n"
            + "dpkg --configure -a\n"
            + "echo \"invapp-openjdk-repack-install: openjdk-17 installed; run: pkg install openjdk-17-x\"\n";
        writeExec(new File(binDir, "invapp-openjdk-repack-install"), openjdkRepackInstall);

        String jdkDoctor = ""
            + "#!" + bash + "\n"
            + "PREFIX=\"" + prefix + "\"\n"
            + "HOME=\"" + home + "\"\n"
            + "export PATH=\"$PREFIX/bin:$HOME/.bun/bin:$PATH\"\n"
            + "REDIRECTOR_SO=\"$PREFIX/lib/libinvapp-redirector.so\"\n"
            + "[ -f \"$REDIRECTOR_SO\" ] && export LD_PRELOAD=\"$REDIRECTOR_SO${LD_PRELOAD:+:$LD_PRELOAD}\"\n"
            + "echo \"--- java on PATH ---\"\n"
            + "command -v java 2>&1 || echo \"java: not on PATH\"\n"
            + "ls -la \"$PREFIX/bin/java\" 2>&1 || true\n"
            + "echo \"--- JVM tree ---\"\n"
            + "ls -la \"$PREFIX/lib/jvm/\" 2>&1 || true\n"
            + "echo \"--- java -version ---\"\n"
            + "java -version 2>&1 || echo \"java -version failed (see docs: openjdk troubleshooting)\"\n"
            + "echo \"--- pkg ---\"\n"
            + "pkg show openjdk-17 2>&1 | head -20 || apt-cache show openjdk-17 2>&1 | head -20 || true\n"
            + "echo \"--- shmem / iconv (common dlopen failures) ---\"\n"
            + "pkg show libandroid-shmem libiconv 2>&1 | grep -E '^Package:|^Status:' || true\n"
            + "echo \"--- JAVA_HOME ---\"\n"
            + "echo \"JAVA_HOME=${JAVA_HOME:-unset}\"\n"
            + "cat \"$PREFIX/lib/jvm/java-17-openjdk/etc/profile.d/java.sh\" 2>/dev/null || true\n";
        writeExec(new File(binDir, "jdk-doctor"), jdkDoctor);

        // OpenCode V2 background service helper (default 0.0.0.0:4000).
        // Replaces legacy opencode-v2-server; td-ai is a thin alias.
        // See assets/invapp/bin/opencode-server + docs/ai.md.
        writeExec(new File(binDir, "opencode-server"),
                assetWithBashShebang(context, "invapp/bin/opencode-server", bash));
        writeExec(new File(binDir, "td-ai"),
                assetWithBashShebang(context, "invapp/bin/td-ai", bash));
        writeExec(new File(binDir, "td-ai-stop"),
                assetWithBashShebang(context, "invapp/bin/td-ai-stop", bash));
        // Drop broken upstream-style shims that point at ~/.opencode/bin.
        for (String legacy : new String[] {
            "opencode-v2", "opencode-v2-server"
        }) {
            File stale = new File(binDir, legacy);
            if (stale.exists() && !stale.delete()) {
                // best-effort; opencode-server also removes these on start
            }
            File bunStale = new File(new File(home, ".bun/bin"), legacy);
            if (bunStale.exists()) {
                //noinspection ResultOfMethodCallIgnored
                bunStale.delete();
            }
        }

        // Cursor Agent CLI (glibc Node 24 + linux-arm64-gnu.node). Never Bun / curl|bash.
        // Official layout under ~/.local/share/cursor-agent; stable wrappers in $PREFIX/bin.
        writeExec(new File(binDir, "cursor-agent-setup"),
                assetWithBashShebang(context, "invapp/bin/cursor-agent-setup", bash));

        writeExec(new File(binDir, "td-agent-worker"),
                assetWithBashShebang(context, "invapp/bin/td-agent-worker", bash));

        writeExec(new File(binDir, "td-upgrade"),
                assetWithBashShebang(context, "invapp/bin/td-upgrade", bash));

        // Dev-server helper: clear errors + Preview/LAN hints before bun run.
        // Shared with the container via invapp/bin/td-dev; branches UX on
        // INVAPP_ON_DEVICE (env.sh) so phones show the drawer/Preview path and
        // containers show the docker host path from a single asset.
        writeExec(new File(binDir, "td-dev"),
                assetWithBashShebang(context, "invapp/bin/td-dev", bash));
        // PC ↔ phone: fetch + ff-only pull every ~/repos git tree (widget + bar).
        writeExec(new File(binDir, "td-sync"),
                assetWithBashShebang(context, "invapp/bin/td-sync", bash));

        // Optional Kali NetHunter via Termux udocker + TigerVNC (device-only).
        // `td-udocker-setup` pulls/creates; `td-nethunter-setup` adds the desktop;
        // `td-nethunter` starts VNC on 127.0.0.1:5901 for AVNC/KeX (no noVNC).
        writeExec(new File(binDir, "td-udocker-setup"),
                assetWithBashShebang(context, "invapp/bin/td-udocker-setup", bash));
        writeExec(new File(binDir, "td-udocker"),
                assetWithBashShebang(context, "invapp/bin/td-udocker", bash));
        writeExec(new File(binDir, "td-nethunter-setup"),
                assetWithBashShebang(context, "invapp/bin/td-nethunter-setup", bash));
        writeExec(new File(binDir, "td-nethunter"),
                assetWithBashShebang(context, "invapp/bin/td-nethunter", bash));
        writeExec(new File(binDir, "td-nethunter-debug"),
                assetWithBashShebang(context, "invapp/bin/td-nethunter-debug", bash));
        // Alias → td-udocker (kept for muscle memory after proot-distro → udocker).
        writeExec(new File(binDir, "td-guest"),
                assetWithBashShebang(context, "invapp/bin/td-guest", bash));
        // Friendly entry points: Ubuntu/Debian (+ optional VNC :5902) and Kali
        // NetHunter (kali.download — not `pd install kali*`).
        writeExec(new File(binDir, "td-proot-linux"),
                assetWithBashShebang(context, "invapp/bin/td-proot-linux", bash));
        writeExec(new File(binDir, "td-proot-nethunter"),
                assetWithBashShebang(context, "invapp/bin/td-proot-nethunter", bash));

        // Vite golden-path scaffold under ~/repos (create-vite + host 0.0.0.0).
        // pwa / pwa-react overlay vite-plugin-pwa on vanilla / react bases.
        File libexecDir = new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH, "libexec");
        ensureDir(libexecDir);
        String pwaConfigJs = ""
            + "// Writes vite.config.js with VitePWA (+ optional React plugin).\n"
            + "// Usage: bun \"$PREFIX/libexec/invapp-vite-pwa.mjs\" [react]\n"
            + "import { writeFileSync, existsSync, unlinkSync } from \"fs\";\n"
            + "import { basename } from \"path\";\n"
            + "\n"
            + "const name = basename(process.cwd());\n"
            + "const isReact = process.argv[2] === \"react\";\n"
            + "const reactImport = isReact\n"
            + "  ? \"import react from '@vitejs/plugin-react'\\n\"\n"
            + "  : \"\";\n"
            + "const reactPlug = isReact ? \"react(), \" : \"\";\n"
            + "const cfg =\n"
            + "  \"import { defineConfig } from 'vite'\\n\" +\n"
            + "  \"import { VitePWA } from 'vite-plugin-pwa'\\n\" +\n"
            + "  reactImport +\n"
            + "  \"\\nexport default defineConfig({\\n\" +\n"
            + "  \"  server: { host: '0.0.0.0', port: 5173 },\\n\" +\n"
            + "  \"  preview: { host: '0.0.0.0', port: 4173 },\\n\" +\n"
            + "  \"  plugins: [\" + reactPlug + \"VitePWA({\\n\" +\n"
            + "  \"    registerType: 'autoUpdate',\\n\" +\n"
            + "  \"    includeAssets: ['favicon.svg', 'vite.svg'],\\n\" +\n"
            + "  \"    manifest: {\\n\" +\n"
            + "  \"      name: \" + JSON.stringify(name) + \",\\n\" +\n"
            + "  \"      short_name: \" + JSON.stringify(name) + \",\\n\" +\n"
            + "  \"      start_url: '/',\\n\" +\n"
            + "  \"      display: 'standalone',\\n\" +\n"
            + "  \"      background_color: '#ffffff',\\n\" +\n"
            + "  \"      theme_color: '#242424',\\n\" +\n"
            + "  \"      icons: [{ src: '/vite.svg', sizes: 'any', type: 'image/svg+xml', purpose: 'any' }]\\n\" +\n"
            + "  \"    }\\n\" +\n"
            + "  \"  })]\\n\" +\n"
            + "  \"})\\n\";\n"
            + "\n"
            + "for (const f of [\n"
            + "  \"vite.config.js\",\n"
            + "  \"vite.config.ts\",\n"
            + "  \"vite.config.mjs\",\n"
            + "  \"vite.config.mts\",\n"
            + "]) {\n"
            + "  if (existsSync(f)) unlinkSync(f);\n"
            + "}\n"
            + "writeFileSync(\"vite.config.js\", cfg);\n";
        writeExec(new File(libexecDir, "invapp-vite-pwa.mjs"), pwaConfigJs);

        String tdScaffold = ""
            + "#!" + bash + "\n"
            + "set -e\n"
            + "PREFIX=\"" + prefix + "\"\n"
            + "HOME=\"" + home + "\"\n"
            + "export PATH=\"$PREFIX/bin:$HOME/.bun/bin:$PATH\"\n"
            + "HERE=0\n"
            + "NO_INSTALL=0\n"
            + "NAME=\"\"\n"
            + "TEMPLATE=\"react-ts\"\n"
            + "while [ $# -gt 0 ]; do\n"
            + "  case \"$1\" in\n"
            + "    --here) HERE=1; shift ;;\n"
            + "    --no-install) NO_INSTALL=1; shift ;;\n"
            + "    -h|--help)\n"
            + "      echo \"usage: td-scaffold [name] [template] [--here] [--no-install]\" >&2\n"
            + "      echo \"templates: vanilla vanilla-ts react react-ts vue vue-ts pwa pwa-react\" >&2\n"
            + "      echo \"default template: react-ts\" >&2\n"
            + "      exit 0\n"
            + "      ;;\n"
            + "    --*) echo \"td-scaffold: unknown flag $1\" >&2; exit 2 ;;\n"
            + "    *)\n"
            + "      if [ -z \"$NAME\" ]; then NAME=\"$1\"\n"
            + "      else TEMPLATE=\"$1\"\n"
            + "      fi\n"
            + "      shift\n"
            + "      ;;\n"
            + "  esac\n"
            + "done\n"
            + "if [ \"$HERE\" = 1 ]; then\n"
            + "  if [ -z \"$NAME\" ]; then NAME=\".\"; fi\n"
            + "elif [ -z \"$NAME\" ]; then\n"
            + "  echo \"usage: td-scaffold <name> [template] [--here] [--no-install]\" >&2\n"
            + "  echo \"templates: vanilla vanilla-ts react react-ts vue vue-ts pwa pwa-react\" >&2\n"
            + "  exit 2\n"
            + "fi\n"
            + "case \"$NAME\" in\n"
            + "  .) ;;\n"
            + "  *[!a-zA-Z0-9._-]*) echo \"td-scaffold: invalid name '$NAME'\" >&2; exit 2 ;;\n"
            + "  *[/\\\\]*|*/storage/*|*emulated*) echo \"td-scaffold: refuse shared/noexec path\" >&2; exit 2 ;;\n"
            + "esac\n"
            + "PWA=0\n"
            + "BASE_TEMPLATE=\"$TEMPLATE\"\n"
            + "case \"$TEMPLATE\" in\n"
            + "  pwa) PWA=1; BASE_TEMPLATE=vanilla ;;\n"
            + "  pwa-react) PWA=1; BASE_TEMPLATE=react ;;\n"
            + "  vanilla|vanilla-ts|react|react-ts|vue|vue-ts|svelte|svelte-ts|solid|solid-ts|qwik|qwik-ts|preact|preact-ts) ;;\n"
            + "  *) echo \"td-scaffold: unknown template '$TEMPLATE'\" >&2; exit 2 ;;\n"
            + "esac\n"
            + "if ! command -v bun >/dev/null 2>&1; then\n"
            + "  echo \"td-scaffold: bun missing — reopen the app\" >&2\n"
            + "  exit 127\n"
            + "fi\n"
            + "if [ \"$HERE\" = 1 ]; then\n"
            + "  TARGET_DIR=\"$(pwd)\"\n"
            + "  CREATE_NAME=\".\"\n"
            + "  if [ -e package.json ]; then\n"
            + "    echo \"td-scaffold: package.json already exists in $TARGET_DIR\" >&2\n"
            + "    exit 1\n"
            + "  fi\n"
            + "else\n"
            + "  mkdir -p \"$HOME/repos\"\n"
            + "  TARGET_DIR=\"$HOME/repos/$NAME\"\n"
            + "  CREATE_NAME=\"$NAME\"\n"
            + "  if [ -e \"$TARGET_DIR\" ]; then\n"
            + "    echo \"td-scaffold: $TARGET_DIR already exists\" >&2\n"
            + "    exit 1\n"
            + "  fi\n"
            + "  cd \"$HOME/repos\"\n"
            + "fi\n"
            + "echo \"td-scaffold: create-vite $CREATE_NAME --template $BASE_TEMPLATE\"\n"
            + "bunx --bun create-vite@latest \"$CREATE_NAME\" --template \"$BASE_TEMPLATE\"\n"
            + "if [ \"$HERE\" != 1 ]; then\n"
            + "  cd \"$NAME\"\n"
            + "fi\n"
            + "# Force LAN-friendly Vite dev server for Preview / Copy LAN.\n"
            + "if [ -f package.json ]; then\n"
            + "  bun -e \"\n"
            + "const fs=require('fs');\n"
            + "const p=JSON.parse(fs.readFileSync('package.json','utf8'));\n"
            + "p.scripts=p.scripts||{};\n"
            + "p.scripts.dev='vite --host 0.0.0.0 --port 5173';\n"
            + "p.scripts.preview=p.scripts.preview||'vite preview --host 0.0.0.0 --port 4173';\n"
            + "fs.writeFileSync('package.json', JSON.stringify(p,null,2)+'\\n');\n"
            + "\"\n"
            + "fi\n"
            + "if [ ! -f .gitignore ]; then\n"
            + "  printf '%s\\n' 'node_modules/' 'dist/' '.DS_Store' '*.local' > .gitignore\n"
            + "fi\n"
            + "if [ ! -f README.md ]; then\n"
            + "  cat > README.md <<'EOF'\n"
            + "# Project\n"
            + "\n"
            + "Scaffolded with `td-scaffold` for InVxTermux.\n"
            + "\n"
            + "## Dev\n"
            + "\n"
            + "```bash\n"
            + "td-dev          # or: bun run dev\n"
            + "```\n"
            + "\n"
            + "Then: drawer → **Preview** → **Scan** → **5173**. Use **Copy LAN** when the server binds `0.0.0.0`.\n"
            + "EOF\n"
            + "fi\n"
            + "if [ \"$NO_INSTALL\" != 1 ]; then\n"
            + "  echo \"td-scaffold: bun install\"\n"
            + "  bun install\n"
            + "else\n"
            + "  echo \"td-scaffold: skipping bun install (--no-install)\"\n"
            + "fi\n"
            + "if [ \"$PWA\" = 1 ]; then\n"
            + "  if [ \"$NO_INSTALL\" = 1 ]; then\n"
            + "    echo \"td-scaffold: PWA needs install — run: bun install && bun add -d vite-plugin-pwa\" >&2\n"
            + "  else\n"
            + "    echo \"td-scaffold: add vite-plugin-pwa\"\n"
            + "    bun add -d vite-plugin-pwa\n"
            + "    bun \"$PREFIX/libexec/invapp-vite-pwa.mjs\" \"$BASE_TEMPLATE\"\n"
            + "    echo \"td-scaffold: PWA enabled (vite-plugin-pwa)\"\n"
            + "  fi\n"
            + "fi\n"
            + "echo \"\"\n"
            + "echo \"OK: $(pwd)\"\n"
            + "echo \"Next: td-dev    # or drawer → bun run dev\"\n"
            + "echo \"Then: drawer → Preview → Scan → 5173 (Copy LAN if needed)\"\n"
            + "if [ \"$PWA\" = 1 ]; then\n"
            + "  echo \"PWA build: bun run build   # then Preview :4173 or install from browser\"\n"
            + "fi\n"
            + "echo \"\"\n"
            + "pwd\n";
        writeExec(new File(binDir, "td-scaffold"), tdScaffold);

        // Clone a remote into ~/repos (PC ↔ phone golden path).
        String tdClone = ""
            + "#!" + bash + "\n"
            + "set -e\n"
            + "PREFIX=\"" + prefix + "\"\n"
            + "HOME=\"" + home + "\"\n"
            + "export PATH=\"$PREFIX/bin:$HOME/.bun/bin:$PATH\"\n"
            + "REDIRECTOR_SO=\"$PREFIX/lib/libinvapp-redirector.so\"\n"
            + "[ -f \"$REDIRECTOR_SO\" ] && export LD_PRELOAD=\"$REDIRECTOR_SO${LD_PRELOAD:+:$LD_PRELOAD}\"\n"
            + "export LD_LIBRARY_PATH=\"$PREFIX/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}\"\n"
            + "URL=\"${1-}\"\n"
            + "if [ -z \"$URL\" ]; then\n"
            + "  echo \"usage: td-clone <git-url> [name] [--bun-i]\" >&2\n"
            + "  exit 2\n"
            + "fi\n"
            + "shift || true\n"
            + "NAME=\"\"\n"
            + "DO_BUN=0\n"
            + "for arg in \"$@\"; do\n"
            + "  case \"$arg\" in\n"
            + "    --bun-i|--bun-install) DO_BUN=1 ;;\n"
            + "    -*) echo \"td-clone: unknown flag '$arg'\" >&2; exit 2 ;;\n"
            + "    *)\n"
            + "      if [ -n \"$NAME\" ]; then\n"
            + "        echo \"usage: td-clone <git-url> [name] [--bun-i]\" >&2\n"
            + "        exit 2\n"
            + "      fi\n"
            + "      NAME=\"$arg\"\n"
            + "      ;;\n"
            + "  esac\n"
            + "done\n"
            + "if [ -z \"$NAME\" ]; then\n"
            + "  NAME=$(basename \"$URL\")\n"
            + "  NAME=${NAME%.git}\n"
            + "  NAME=${NAME%%\\?*}\n"
            + "  NAME=${NAME%%\\#*}\n"
            + "fi\n"
            + "case \"$NAME\" in\n"
            + "  ''|'.'|'..'|*[!a-zA-Z0-9._-]*)\n"
            + "    echo \"td-clone: invalid name '$NAME'\" >&2\n"
            + "    exit 2\n"
            + "    ;;\n"
            + "esac\n"
            + "if ! command -v git >/dev/null 2>&1; then\n"
            + "  echo \"td-clone: git missing — pkg install git\" >&2\n"
            + "  exit 127\n"
            + "fi\n"
            + "mkdir -p \"$HOME/repos\"\n"
            + "DEST=\"$HOME/repos/$NAME\"\n"
            + "if [ -e \"$DEST\" ]; then\n"
            + "  echo \"td-clone: $DEST already exists\" >&2\n"
            + "  exit 1\n"
            + "fi\n"
            + "echo \"td-clone: git clone $URL → $DEST\"\n"
            + "git clone -- \"$URL\" \"$DEST\"\n"
            + "cd \"$DEST\"\n"
            + "if [ \"$DO_BUN\" = 1 ]; then\n"
            + "  if [ -f package.json ]; then\n"
            + "    if ! command -v bun >/dev/null 2>&1; then\n"
            + "      echo \"td-clone: bun missing — reopen the app\" >&2\n"
            + "      exit 127\n"
            + "    fi\n"
            + "    echo \"td-clone: bun install\"\n"
            + "    bun install\n"
            + "  else\n"
            + "    echo \"td-clone: no package.json — skip bun install\"\n"
            + "  fi\n"
            + "fi\n"
            + "echo \"\"\n"
            + "echo \"OK: $DEST\"\n"
            + "echo \"Next: td-dev    # or drawer → bun run dev\"\n"
            + "echo \"Then: drawer → Preview → Scan (Copy LAN if needed)\"\n"
            + "echo \"\"\n"
            + "pwd\n";
        writeExec(new File(binDir, "td-clone"), tdClone);

        // KeepAliveService: applicationId ≠ Java package — use FQCN.
        String apiPkg = TermuxConstants.TERMUX_API_PACKAGE_NAME;
        String keepaliveCmp = apiPkg + "/com.invapp.api.KeepAliveService";
        String termuxApiStart = ""
            + "#!" + bash + "\n"
            + "# invapp: termux-api-start — KeepAliveService (FQCN)\n"
            + "am startservice -n " + keepaliveCmp + "\n";
        writeExec(new File(binDir, "termux-api-start"), termuxApiStart);
        String termuxApiStop = ""
            + "#!" + bash + "\n"
            + "# invapp: termux-api-stop — KeepAliveService (FQCN)\n"
            + "am stopservice -n " + keepaliveCmp + "\n";
        writeExec(new File(binDir, "termux-api-stop"), termuxApiStop);

        // MediaProjection screenshot via Termux:API (consent dialog once).
        // Mirrors contrib/termux-api-package/scripts/termux-screenshot.in
        // (getopts + libexec/termux-api Screenshot).
        String termuxScreenshot = ""
            + "#!" + bash + "\n"
            + "# invapp: termux-screenshot — Capture display via MediaProjection\n"
            + "# Upstream-ready twin: contrib/termux-api-package/scripts/termux-screenshot.in\n"
            + "set -e -u\n"
            + "PREFIX=\"" + prefix + "\"\n"
            + "HOME=\"" + home + "\"\n"
            + "export PATH=\"$PREFIX/bin:$PATH\"\n"
            + "SCRIPTNAME=termux-screenshot\n"
            + "show_usage() {\n"
            + "  echo \"Usage: termux-screenshot [-d delay_ms] [output-file]\"\n"
            + "  echo \"Take a screenshot (MediaProjection consent) and save it as PNG.\"\n"
            + "  echo \" -d delay_ms  Wait after consent before capture (default: 800)\"\n"
            + "  echo \"Default file: $HOME/repos/screen-ocr/latest.png\"\n"
            + "  exit 0\n"
            + "}\n"
            + "PARAMS=\"\"\n"
            + "DELAY_SET=0\n"
            + "while getopts :hd: option\n"
            + "do\n"
            + "  case \"$option\" in\n"
            + "    h) show_usage;;\n"
            + "    d) PARAMS=\"$PARAMS --ei delay_ms $OPTARG\"; DELAY_SET=1;;\n"
            + "    ?) echo \"$SCRIPTNAME: illegal option -$OPTARG\"; exit 1;;\n"
            + "  esac\n"
            + "done\n"
            + "shift $((OPTIND-1))\n"
            + "if [ $# -gt 1 ]; then echo \"$SCRIPTNAME: too many arguments\"; exit 1; fi\n"
            + "if [ \"$DELAY_SET\" = 0 ]; then\n"
            + "  PARAMS=\"$PARAMS --ei delay_ms ${SCREENSHOT_DELAY_MS:-800}\"\n"
            + "fi\n"
            + "OUT=\"${1-}\"\n"
            + "if [ -z \"$OUT\" ]; then\n"
            + "  OUT=\"$HOME/repos/screen-ocr/latest.png\"\n"
            + "fi\n"
            + "mkdir -p \"$(dirname \"$OUT\")\"\n"
            + "if [ ! -x \"$PREFIX/libexec/termux-api\" ]; then\n"
            + "  echo \"$SCRIPTNAME: need pkg install termux-api (libexec/termux-api)\" >&2\n"
            + "  exit 127\n"
            + "fi\n"
            + "touch \"$OUT\"\n"
            + "PARAMS=\"$PARAMS --es file $(realpath \"$OUT\")\"\n"
            + "# shellcheck disable=SC2086\n"
            + "result=$(\"$PREFIX/libexec/termux-api\" Screenshot $PARAMS || true)\n"
            + "case \"$result\" in\n"
            + "  ERROR:*)\n"
            + "    echo \"$result\" >&2\n"
            + "    exit 1\n"
            + "    ;;\n"
            + "  \"\")\n"
            + "    echo \"$SCRIPTNAME: empty response from Termux:API\" >&2\n"
            + "    exit 1\n"
            + "    ;;\n"
            + "esac\n"
            + "printf '%s\\n' \"$result\"\n";
        writeExec(new File(binDir, "termux-screenshot"), termuxScreenshot);

        // OCR screenshot → clipboard (capture preferred, else latest/pick).
        String tdScreenOcr = ""
            + "#!" + bash + "\n"
            + "# invapp: td-screen-ocr — screenshot/OCR → clipboard\n"
            + "set -e\n"
            + "PREFIX=\"" + prefix + "\"\n"
            + "HOME=\"" + home + "\"\n"
            + "export PATH=\"$PREFIX/bin:$PATH\"\n"
            + "OCR_LANG=\"${OCR_LANG:-eng}\"\n"
            + "TESSDATA_DIR=\"${TESSDATA_PREFIX:-$PREFIX/share/tessdata}\"\n"
            + "MODE=auto\n"
            + "IMG=\"\"\n"
            + "toast() { command -v termux-toast >/dev/null 2>&1 && termux-toast \"$1\" || true; }\n"
            + "lang_pack_hint() {\n"
            + "  echo \"Lang pack: copy ${OCR_LANG}.traineddata → $TESSDATA_DIR\"\n"
            + "  echo \"  (eng ships with: pkg install tesseract)\"\n"
            + "  echo \"  Others: https://github.com/tesseract-ocr/tessdata\"\n"
            + "}\n"
            + "while [ $# -gt 0 ]; do\n"
            + "  case \"$1\" in\n"
            + "    -h|--help)\n"
            + "      echo \"Usage: td-screen-ocr [--capture|--latest|--pick] [image]\"\n"
            + "      echo \"OCR a screenshot into the clipboard (tesseract).\"\n"
            + "      echo \"  --capture  termux-screenshot (MediaProjection)\"\n"
            + "      echo \"  --latest   newest PNG/JPG under Screenshots folders\"\n"
            + "      echo \"  --pick     termux-storage-get file picker\"\n"
            + "      echo \"Env: OCR_LANG (default eng), SCREENSHOT_DELAY_MS, TESSDATA_PREFIX\"\n"
            + "      echo \"Lang packs: eng via pkg install tesseract; others → $PREFIX/share/tessdata\"\n"
            + "      exit 0\n"
            + "      ;;\n"
            + "    --capture) MODE=capture; shift ;;\n"
            + "    --latest)  MODE=latest; shift ;;\n"
            + "    --pick)    MODE=pick; shift ;;\n"
            + "    --no-capture) MODE=latest; shift ;;\n"
            + "    *) IMG=\"$1\"; MODE=file; shift ;;\n"
            + "  esac\n"
            + "done\n"
            + "if ! command -v tesseract >/dev/null 2>&1; then\n"
            + "  msg='Need: pkg install tesseract (incl. eng lang pack)'\n"
            + "  toast \"$msg\"; echo \"$msg\" >&2\n"
            + "  lang_pack_hint >&2\n"
            + "  exit 127\n"
            + "fi\n"
            + "if [ ! -f \"$TESSDATA_DIR/${OCR_LANG}.traineddata\" ]; then\n"
            + "  msg=\"Missing ${OCR_LANG}.traineddata — see toast/stderr\"\n"
            + "  toast \"Lang pack: put ${OCR_LANG}.traineddata in tessdata\"\n"
            + "  echo \"$msg\" >&2\n"
            + "  lang_pack_hint >&2\n"
            + "  exit 127\n"
            + "fi\n"
            + "if ! command -v termux-clipboard-set >/dev/null 2>&1; then\n"
            + "  msg='Need Termux:API APK + pkg install termux-api'\n"
            + "  toast \"$msg\"; echo \"$msg\" >&2\n"
            + "  exit 127\n"
            + "fi\n"
            + "find_latest() {\n"
            + "  local newest=\"\" newest_ts=0 f ts\n"
            + "  for dir in \\\n"
            + "      \"$HOME/storage/dcim/Screenshots\" \\\n"
            + "      \"$HOME/storage/pictures/Screenshots\" \\\n"
            + "      \"$HOME/storage/shared/DCIM/Screenshots\" \\\n"
            + "      \"$HOME/storage/shared/Pictures/Screenshots\"; do\n"
            + "    [ -d \"$dir\" ] || continue\n"
            + "    # shellcheck disable=SC2044\n"
            + "    for f in \"$dir\"/*; do\n"
            + "      [ -f \"$f\" ] || continue\n"
            + "      case \"$f\" in\n"
            + "        *.png|*.PNG|*.jpg|*.JPG|*.jpeg|*.JPEG|*.webp|*.WEBP) ;;\n"
            + "        *) continue ;;\n"
            + "      esac\n"
            + "      ts=$(stat -c %Y \"$f\" 2>/dev/null || echo 0)\n"
            + "      if [ \"$ts\" -ge \"$newest_ts\" ]; then\n"
            + "        newest_ts=$ts\n"
            + "        newest=$f\n"
            + "      fi\n"
            + "    done\n"
            + "  done\n"
            + "  printf '%s' \"$newest\"\n"
            + "}\n"
            + "resolve_image() {\n"
            + "  case \"$MODE\" in\n"
            + "    file)\n"
            + "      if [ ! -f \"$IMG\" ]; then\n"
            + "        echo \"td-screen-ocr: not a file: $IMG\" >&2\n"
            + "        return 1\n"
            + "      fi\n"
            + "      printf '%s' \"$IMG\"\n"
            + "      return 0\n"
            + "      ;;\n"
            + "    capture)\n"
            + "      toast 'Capturing screen…'\n"
            + "      if ! command -v termux-screenshot >/dev/null 2>&1; then\n"
            + "        echo \"td-screen-ocr: termux-screenshot missing — reopen the app\" >&2\n"
            + "        return 1\n"
            + "      fi\n"
            + "      mkdir -p \"$HOME/repos/screen-ocr\"\n"
            + "      local out path\n"
            + "      out=\"$HOME/repos/screen-ocr/latest.png\"\n"
            + "      path=$(termux-screenshot \"$out\") || return 1\n"
            + "      case \"$path\" in ERROR:*) echo \"$path\" >&2; return 1 ;; esac\n"
            + "      printf '%s' \"$path\"\n"
            + "      return 0\n"
            + "      ;;\n"
            + "    pick)\n"
            + "      if ! command -v termux-storage-get >/dev/null 2>&1; then\n"
            + "        echo \"td-screen-ocr: termux-storage-get missing (pkg install termux-api)\" >&2\n"
            + "        return 1\n"
            + "      fi\n"
            + "      toast 'Pick an image…'\n"
            + "      mkdir -p \"$HOME/repos/screen-ocr\"\n"
            + "      local tmp=\"$HOME/repos/screen-ocr/picked.bin\"\n"
            + "      termux-storage-get \"$tmp\" || return 1\n"
            + "      [ -s \"$tmp\" ] || return 1\n"
            + "      printf '%s' \"$tmp\"\n"
            + "      return 0\n"
            + "      ;;\n"
            + "    latest)\n"
            + "      local latest\n"
            + "      latest=$(find_latest)\n"
            + "      if [ -z \"$latest\" ]; then\n"
            + "        echo \"td-screen-ocr: no screenshots (run termux-setup-storage)\" >&2\n"
            + "        return 1\n"
            + "      fi\n"
            + "      printf '%s' \"$latest\"\n"
            + "      return 0\n"
            + "      ;;\n"
            + "    auto)\n"
            + "      if command -v termux-screenshot >/dev/null 2>&1 \\\n"
            + "          && [ -x \"$PREFIX/libexec/termux-api\" ]; then\n"
            + "        toast 'Capturing screen…'\n"
            + "        mkdir -p \"$HOME/repos/screen-ocr\"\n"
            + "        local out path\n"
            + "        out=\"$HOME/repos/screen-ocr/latest.png\"\n"
            + "        if path=$(termux-screenshot \"$out\" 2>/dev/null); then\n"
            + "          case \"$path\" in\n"
            + "            ERROR:*|\"\") ;;\n"
            + "            *)\n"
            + "              if [ -s \"$path\" ]; then\n"
            + "                printf '%s' \"$path\"\n"
            + "                return 0\n"
            + "              fi\n"
            + "              ;;\n"
            + "          esac\n"
            + "        fi\n"
            + "        toast 'Capture failed — trying latest screenshot'\n"
            + "      fi\n"
            + "      MODE=latest\n"
            + "      resolve_image\n"
            + "      return $?\n"
            + "      ;;\n"
            + "  esac\n"
            + "  return 1\n"
            + "}\n"
            + "IMG_PATH=$(resolve_image) || {\n"
            + "  toast 'No screenshot to OCR'\n"
            + "  exit 1\n"
            + "}\n"
            + "toast 'OCR…'\n"
            + "tess_err=$(mktemp \"$PREFIX/tmp/td-ocr.XXXXXX\" 2>/dev/null || mktemp)\n"
            + "text=$(tesseract \"$IMG_PATH\" stdout -l \"$OCR_LANG\" 2>\"$tess_err\" || true)\n"
            + "text=$(printf '%s' \"$text\" | sed -e 's/[[:space:]]*$//')\n"
            + "if [ -z \"$text\" ]; then\n"
            + "  toast \"OCR empty — check ${OCR_LANG} lang pack\"\n"
            + "  echo \"td-screen-ocr: no text from $IMG_PATH\" >&2\n"
            + "  lang_pack_hint >&2\n"
            + "  if [ -s \"$tess_err\" ]; then cat \"$tess_err\" >&2; fi\n"
            + "  rm -f \"$tess_err\"\n"
            + "  exit 1\n"
            + "fi\n"
            + "rm -f \"$tess_err\"\n"
            + "printf '%s' \"$text\" | termux-clipboard-set\n"
            + "chars=$(printf '%s' \"$text\" | wc -c | tr -d ' ')\n"
            + "toast \"Copied $chars chars\"\n"
            + "echo \"OCR → clipboard ($chars chars) from $IMG_PATH\"\n";
        writeExec(new File(binDir, "td-screen-ocr"), tdScreenOcr);

        installBashCompletions(prefix);
        ensureWorkspaceDirs();
        repairPrefixBinPermissions();
    }

    /**
     * Installs {@code $PREFIX/etc/profile.d/invapp-completions.sh} and static
     * completers under {@code $PREFIX/etc/bash_completion.d/}. Does not touch
     * {@code ~/.bashrc}. Open a new session after install.
     */
    static void installBashCompletions(@NonNull String prefix) {
        File profileD = new File(prefix, "etc/profile.d");
        File completionD = new File(prefix, "etc/bash_completion.d");
        ensureDir(profileD);
        ensureDir(completionD);

        String hook = ""
            + "# invapp-completions — sourced by login shells via profile.d\n"
            + "[ -n \"${BASH_VERSION-}\" ] || return 0\n"
            + "shopt -q progcomp 2>/dev/null || shopt -s progcomp 2>/dev/null || true\n"
            + "if [ -f \"$PREFIX/share/bash-completion/bash_completion\" ]; then\n"
            + "  # shellcheck source=/dev/null\n"
            + "  . \"$PREFIX/share/bash-completion/bash_completion\"\n"
            + "elif [ -f \"$PREFIX/etc/bash_completion\" ]; then\n"
            + "  # shellcheck source=/dev/null\n"
            + "  . \"$PREFIX/etc/bash_completion\"\n"
            + "fi\n"
            + "for _invapp_comp in \"$PREFIX/etc/bash_completion.d\"/*.bash \"$PREFIX/etc/bash_completion.d\"/*.sh; do\n"
            + "  [ -f \"$_invapp_comp\" ] || continue\n"
            + "  # shellcheck source=/dev/null\n"
            + "  . \"$_invapp_comp\"\n"
            + "done\n"
            + "unset _invapp_comp\n";
        writeTextFile(new File(profileD, "invapp-completions.sh"), hook);

        writeTextFile(new File(completionD, "invapp-bun.bash"), bunCompletionScript());
        writeTextFile(new File(completionD, "invapp-pkg.bash"), pkgCompletionScript());
        writeTextFile(new File(completionD, "invapp-npm.bash"), npmCompletionScript());
        writeTextFile(new File(completionD, "invapp-gh.bash"), ghCompletionScript());
        writeTextFile(new File(completionD, "invapp-git.bash"), gitCompletionScript());
    }

    @NonNull
    static String bunCompletionScript() {
        return ""
            + "# invapp bun completions (subset; regenerate with: bun completions bash)\n"
            + "_invapp_bun() {\n"
            + "  local cur=\"${COMP_WORDS[COMP_CWORD]}\"\n"
            + "  local cmds=\"run test install i add remove rm update create x exec init help --version --help\"\n"
            + "  if [ \"$COMP_CWORD\" -eq 1 ]; then\n"
            + "    COMPREPLY=( $(compgen -W \"$cmds\" -- \"$cur\") )\n"
            + "    return\n"
            + "  fi\n"
            + "  case \"${COMP_WORDS[1]}\" in\n"
            + "    run|test|x|exec) COMPREPLY=( $(compgen -f -- \"$cur\") ) ;;\n"
            + "    *) COMPREPLY=( $(compgen -W \"$cmds\" -- \"$cur\") ) ;;\n"
            + "  esac\n"
            + "}\n"
            + "complete -F _invapp_bun bun 2>/dev/null || true\n"
            + "complete -F _invapp_bun bunx 2>/dev/null || true\n";
    }

    @NonNull
    static String pkgCompletionScript() {
        return ""
            + "# invapp pkg completions\n"
            + "_invapp_pkg() {\n"
            + "  local cur=\"${COMP_WORDS[COMP_CWORD]}\"\n"
            + "  local cmds=\"install uninstall reinstall search list files show upgrade update clean hold unhold help\"\n"
            + "  if [ \"$COMP_CWORD\" -eq 1 ]; then\n"
            + "    COMPREPLY=( $(compgen -W \"$cmds\" -- \"$cur\") )\n"
            + "    return\n"
            + "  fi\n"
            + "  case \"${COMP_WORDS[1]}\" in\n"
            + "    install|uninstall|reinstall|search|files|show|hold|unhold)\n"
            + "      if command -v apt-cache >/dev/null 2>&1; then\n"
            + "        COMPREPLY=( $(apt-cache --no-generate pkgnames \"$cur\" 2>/dev/null) )\n"
            + "      else\n"
            + "        COMPREPLY=( $(compgen -W \"\" -- \"$cur\") )\n"
            + "      fi\n"
            + "      ;;\n"
            + "    *) COMPREPLY=( $(compgen -W \"$cmds\" -- \"$cur\") ) ;;\n"
            + "  esac\n"
            + "}\n"
            + "complete -F _invapp_pkg pkg 2>/dev/null || true\n";
    }

    @NonNull
    static String npmCompletionScript() {
        return ""
            + "# invapp npm completions (works with real npm or node shim)\n"
            + "_invapp_npm() {\n"
            + "  local cur=\"${COMP_WORDS[COMP_CWORD]}\"\n"
            + "  local cmds=\"install i uninstall remove rm run test start build publish pack link unlink outdated audit doctor help\"\n"
            + "  if [ \"$COMP_CWORD\" -eq 1 ]; then\n"
            + "    COMPREPLY=( $(compgen -W \"$cmds\" -- \"$cur\") )\n"
            + "    return\n"
            + "  fi\n"
            + "  case \"${COMP_WORDS[1]}\" in\n"
            + "    run|start|test|build) COMPREPLY=( $(compgen -f -- \"$cur\") ) ;;\n"
            + "    *) COMPREPLY=( $(compgen -W \"$cmds\" -- \"$cur\") ) ;;\n"
            + "  esac\n"
            + "}\n"
            + "complete -F _invapp_npm npm 2>/dev/null || true\n"
            + "complete -F _invapp_npm npx 2>/dev/null || true\n";
    }

    @NonNull
    static String ghCompletionScript() {
        return ""
            + "# invapp gh completions — prefer upstream when available\n"
            + "if command -v gh >/dev/null 2>&1; then\n"
            + "  if ! complete -p gh >/dev/null 2>&1; then\n"
            + "    eval \"$(gh completion -s bash 2>/dev/null)\" || true\n"
            + "  fi\n"
            + "fi\n"
            + "if ! complete -p gh >/dev/null 2>&1; then\n"
            + "  _invapp_gh() {\n"
            + "    local cur=\"${COMP_WORDS[COMP_CWORD]}\"\n"
            + "    local cmds=\"auth repo pr issue release gist api browse config help\"\n"
            + "    if [ \"$COMP_CWORD\" -eq 1 ]; then\n"
            + "      COMPREPLY=( $(compgen -W \"$cmds\" -- \"$cur\") )\n"
            + "    fi\n"
            + "  }\n"
            + "  complete -F _invapp_gh gh 2>/dev/null || true\n"
            + "fi\n";
    }

    @NonNull
    static String gitCompletionScript() {
        return ""
            + "# invapp git stub — skip if bash-completion already registered git\n"
            + "if complete -p git >/dev/null 2>&1; then\n"
            + "  return 0 2>/dev/null || true\n"
            + "fi\n"
            + "_invapp_git() {\n"
            + "  local cur=\"${COMP_WORDS[COMP_CWORD]}\"\n"
            + "  local cmds=\"status add commit push pull clone fetch checkout branch merge rebase log diff stash remote init help\"\n"
            + "  if [ \"$COMP_CWORD\" -eq 1 ]; then\n"
            + "    COMPREPLY=( $(compgen -W \"$cmds\" -- \"$cur\") )\n"
            + "    return\n"
            + "  fi\n"
            + "  COMPREPLY=( $(compgen -f -- \"$cur\") )\n"
            + "}\n"
            + "complete -F _invapp_git git 2>/dev/null || true\n";
    }

    private static void writeTextFile(@NonNull File dest, @NonNull String contents) {
        try {
            File parent = dest.getParentFile();
            if (parent != null) {
                ensureDir(parent);
            }
            File tmp = new File(dest.getAbsolutePath() + ".new");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(contents.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        } catch (Exception e) {
            Logger.logWarn(LOG_TAG, "Text install failed for " + dest + ": " + e.getMessage());
        }
    }

    /**
     * If {@code node} is missing (or is our previous shim), install a tiny
     * wrapper that execs Bun. Leaves a real {@code nodejs} package binary alone.
     */
    private static void ensureNodeBunShim(@NonNull Context context, @NonNull File binDir,
                                         @NonNull String bash) {
        File node = new File(binDir, "node");
        if (node.isFile() && !isInvappNodeShim(node)) {
            return;
        }
        // Content is shared with the container seed (assets/invapp/bin/node) so
        // the shim text lives in one place. The container ships real nodejs-lts
        // and seed.sh skips it, so this only lands on device when no real
        // nodejs package is present. isInvappNodeShim guards real node above.
        writeExec(node, assetWithBashShebang(context, "invapp/bin/node", bash));
    }

    private static boolean isInvappNodeShim(@NonNull File node) {
        try (FileInputStream in = new FileInputStream(node)) {
            byte[] buf = new byte[256];
            int n = in.read(buf);
            if (n <= 0) {
                return false;
            }
            String head = new String(buf, 0, n, java.nio.charset.StandardCharsets.UTF_8);
            return head.contains("invapp-bun-node-shim");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Keep a fork-safe {@code $PREFIX/bin/proot} wrapper in front of the Termux
     * ELF. Stock {@code proot} under the path redirector fails guest
     * {@code execve("/usr/bin/bash")} for proot-distro and friends.
     *
     * <p>On each seed: if {@code bin/proot} is an ELF (fresh {@code pkg install}),
     * move it to {@code libexec/proot-bin}; symlink host-only deps
     * ({@code libtalloc}, {@code libandroid-shmem}, …) into
     * {@code libexec/proot-libs} (narrow LD_LIBRARY_PATH for the host ELF);
     * always rewrite the wrapper and a copy at
     * {@code libexec/invapp-proot-wrapper} for shell-side rewrap.
     */
    private static void ensureProotWrapper(@NonNull Context context, @NonNull String bash) {
        String prefix = TermuxConstants.TERMUX_PREFIX_DIR_PATH;
        File binProot = new File(prefix, "bin/proot");
        File libexecDir = new File(prefix, "libexec");
        File realProot = new File(libexecDir, "proot-bin");
        File wrapperTemplate = new File(libexecDir, "invapp-proot-wrapper");
        ensureDir(libexecDir);
        ensureProotLibs(prefix);

        if (binProot.isFile() && isElfFile(binProot)) {
            if (!moveOrCopyFile(binProot, realProot)) {
                Logger.logWarn(LOG_TAG, "Could not relocate proot ELF to " + realProot);
            }
        } else if (!realProot.isFile()) {
            // Prefer packaged locations if libexec/proot-bin is still empty.
            File[] candidates = {
                new File(prefix, "libexec/proot/proot"),
                new File(prefix, "lib/proot/proot"),
            };
            for (File c : candidates) {
                if (c.isFile() && isElfFile(c) && copyFile(c, realProot)) {
                    break;
                }
            }
        }

        String wrapper = assetWithBashShebang(context, "invapp/bin/proot", bash);
        writeExec(wrapperTemplate, wrapper);
        writeExec(binProot, wrapper);
    }

    /**
     * Stage host-only shared libs for {@code proot-bin} without putting full
     * {@code $PREFIX/lib} on {@code LD_LIBRARY_PATH} (that breaks guest glibc).
     */
    private static void ensureProotLibs(@NonNull String prefix) {
        File libDir = new File(prefix, "lib");
        File prootLibs = new File(prefix, "libexec/proot-libs");
        ensureDir(prootLibs);
        if (!libDir.isDirectory()) {
            return;
        }
        final String[] prefixes = {
            "libtalloc.so",
            "libandroid-shmem.so",
            "libandroid-support.so",
        };
        File[] matches = libDir.listFiles((dir, name) -> {
            for (String p : prefixes) {
                if (name.equals(p) || name.startsWith(p + ".")) {
                    return true;
                }
            }
            // Also accept libtalloc.so.2 style where prefix already includes .so
            for (String p : prefixes) {
                if (name.startsWith(p)) {
                    return true;
                }
            }
            return false;
        });
        if (matches == null) {
            return;
        }
        for (File src : matches) {
            if (!src.exists()) {
                continue;
            }
            File dest = new File(prootLibs, src.getName());
            // Refresh every seed so pkg upgrades are picked up.
            //noinspection ResultOfMethodCallIgnored
            dest.delete();
            try {
                Os.symlink(src.getAbsolutePath(), dest.getAbsolutePath());
            } catch (Exception e) {
                if (!copyFile(src, dest)) {
                    Logger.logWarn(LOG_TAG, "proot-libs: could not stage " + src.getName());
                }
            }
        }
    }

    private static boolean isElfFile(@NonNull File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] magic = new byte[4];
            int n = in.read(magic);
            return n == 4
                && magic[0] == 0x7f
                && magic[1] == 'E'
                && magic[2] == 'L'
                && magic[3] == 'F';
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean moveOrCopyFile(@NonNull File from, @NonNull File to) {
        if (from.equals(to)) {
            return true;
        }
        //noinspection ResultOfMethodCallIgnored
        to.delete();
        if (from.renameTo(to)) {
            return true;
        }
        if (!copyFile(from, to)) {
            return false;
        }
        //noinspection ResultOfMethodCallIgnored
        from.delete();
        return true;
    }

    private static boolean copyFile(@NonNull File from, @NonNull File to) {
        try (FileInputStream in = new FileInputStream(from);
             FileOutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
            }
            try {
                //noinspection OctalInteger
                Os.chmod(to.getAbsolutePath(), 0700);
            } catch (Exception ignored) {
            }
            return true;
        } catch (Exception e) {
            Logger.logWarn(LOG_TAG, "copy " + from + " → " + to + ": " + e.getMessage());
            //noinspection ResultOfMethodCallIgnored
            to.delete();
            return false;
        }
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
        String[] names = {
            "am.termuxam", "ksu", "am", "login", "apt", "apt-get", "dpkg",
            "bun", "bunx", "node", "proot", "td-ai", "td-ai-stop", "opencode-server", "td-dev",
            "td-udocker-setup", "td-udocker", "td-guest",
            "td-nethunter-setup", "td-nethunter", "td-nethunter-debug",
            "td-proot-linux", "td-proot-nethunter",
            "td-scaffold", "td-clone",

            "td-screen-ocr", "termux-screenshot", "termux-api-start", "termux-api-stop",
            "opencode-setup", "opencode-fix-net", "opencode", "bun-doctor",
            "bun-version", "bun-upgrade",
            "td-resource", "td-resource-linux", "td-resource-python",
            "td-resource-qemu", "td-resource-bun", "td-resource-ai",
            "cursor-agent-setup", "agent", "cursor-agent", "td-agent-worker", "td-upgrade",
            "ivx-configure", "ivx-doctor", "ivx-cleanup", "ivx-disk-analyze",
            "ivx-fix-dpkg-scripts", "invapp-fix-dpkg-scripts"
        };
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

    static boolean writeExec(@NonNull File dest, @NonNull String contents) {
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
                return false;
            }
            return true;
        } catch (Exception e) {
            Logger.logWarn(LOG_TAG, "Helper install failed for " + dest + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Read a shell helper from app assets and resolve its portable
     * {@code #!@BASH@} shebang to this app's bash — the same value the inline
     * {@code "#!" + bash} literals resolved to before. The body is shared
     * verbatim with the container (seed.sh resolves the identical shebang to
     * {@code $PREFIX/bin/bash}). The asset sources env.sh, which preserves the
     * already-set package-specific PREFIX/HOME, so one file serves both runtimes.
     */
    @NonNull
    static String assetWithBashShebang(@NonNull Context context,
                                                 @NonNull String assetName,
                                                 @NonNull String bash) {
        try (InputStream in = context.getAssets().open(assetName);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            String content = new String(out.toByteArray(),
                    java.nio.charset.StandardCharsets.UTF_8);
            String marker = "#!@BASH@\n";
            if (content.startsWith(marker)) {
                content = "#!" + bash + "\n" + content.substring(marker.length());
            } else if (content.startsWith("#!@BASH@")) {
                content = "#!" + bash + "\n" + content.substring("#!@BASH@".length());
            }
            return content;
        } catch (Exception e) {
            Logger.logWarn(LOG_TAG, "Failed reading asset " + assetName
                    + ": " + e.getMessage());
            // Never let a missing asset silently remove bun — degrade to a loud exit.
            return "#!" + bash + "\n# missing asset " + assetName + " — reopen the app\nexit 127\n";
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
