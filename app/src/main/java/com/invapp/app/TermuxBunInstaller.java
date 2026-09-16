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
            // Seccomp SIGSYS→ENOSYS (openat2/fchmodat2) PLUS path redirector so
            // bunx/child bins with #!/usr/bin/env node get shebang rewrite.
            // Redirector alone used to SIGSYS on install; seccomp fixes that.
            + "SECCOMP_SO=\"$PREFIX/lib/libinvapp-bun-seccomp.so\"\n"
            + "REDIRECTOR_SO=\"$PREFIX/lib/libinvapp-redirector.so\"\n"
            + "preload=\"\"\n"
            + "[ -f \"$SECCOMP_SO\" ] && preload=\"$SECCOMP_SO\"\n"
            + "[ -f \"$REDIRECTOR_SO\" ] && preload=\"${preload:+$preload:}$REDIRECTOR_SO\"\n"
            + "export LD_PRELOAD=\"$preload\"\n"
            + "set -- \"$@\"\n"
            + "cmd=\"${1-}\"\n"
            // Force Android optionalDependency filter. Bun reports platform=android but
            // still resolves linux-* natives (e.g. @rolldown/binding-linux-arm-gnueabihf)
            // for many scaffolds / Windows lockfiles → bad optional extracts.
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
            + "    exec \"$REAL\" \"$cmd\"$extra \"$@\"\n"
            + "    ;;\n"
            + "esac\n"
            + "exec \"$REAL\" \"$@\"\n";
        writeExec(new File(binDir, "bun"), bunWrapper);

        // bunx must run package bins under Bun. Stock `bun x` execs the bin
        // shebang (#!/usr/bin/env node); without node that exits 127 and often
        // prints nothing — the "silent fail" users hit for create-vite / serve.
        String bunx = ""
            + "#!" + bash + "\n"
            + "PREFIX=\"" + prefix + "\"\n"
            + "has_bun=0\n"
            + "for a in \"$@\"; do\n"
            + "  case \"$a\" in --bun) has_bun=1 ;; esac\n"
            + "done\n"
            + "if [ \"$has_bun\" = 1 ]; then\n"
            + "  exec \"$PREFIX/bin/bun\" x \"$@\"\n"
            + "fi\n"
            + "exec \"$PREFIX/bin/bun\" x --bun \"$@\"\n";
        writeExec(new File(binDir, "bunx"), bunx);

        // Provide `node` → bun when nodejs package is not installed so
        // #!/usr/bin/env node scripts (npm bins, bunx children) actually run.
        ensureNodeBunShim(binDir, bash, prefix);

        String bunDoctor = ""
            + "#!" + bash + "\n"
            + "PREFIX=\"" + prefix + "\"\n"
            + "HOME=\"" + home + "\"\n"
            + "echo \"wrapper: $PREFIX/bin/bun\"\n"
            + "echo \"real:    $PREFIX/libexec/bun\"\n"
            + "ls -la \"$PREFIX/bin/bun\" \"$PREFIX/libexec/bun\" \"$PREFIX/bin/bunx\" \"$PREFIX/bin/node\" 2>&1\n"
            + "if command -v readelf >/dev/null; then\n"
            + "  readelf -l \"$PREFIX/libexec/bun\" 2>/dev/null | grep -A1 INTERP || true\n"
            + "fi\n"
            + "\"$PREFIX/bin/bun\" --version\n"
            + "echo \"node → $(command -v node 2>/dev/null || echo missing)\"\n"
            + "if [ -f \"$PREFIX/bin/node\" ]; then head -2 \"$PREFIX/bin/node\"; fi\n"
            + "echo \"LD_PRELOAD in shell: ${LD_PRELOAD:-unset}\"\n"
            + "echo \"OPENSSL_CONF=${OPENSSL_CONF:-unset}\"\n"
            + "echo \"--- bunx smoke ---\"\n"
            + "bunx --bun cowsay ok 2>&1 | head -15 || true\n";
        writeExec(new File(binDir, "bun-doctor"), bunDoctor);

        // OpenCode bootstrap (optional AI CLI → Preview).
        // No opencode-android-* (anomalyco/opencode#12515). Do NOT bun-install
        // opencode-ai: bare libexec/bun hits SIGSYS (seccomp), and bun's
        // global bin is a JS stub that demands postinstall. Fetch the official
        // linux-glibc tarball from GitHub releases, then wrap with ld-linux.
        // glibc lives in glibc-repo (not tur-repo); trusted=yes for apt-key flakes.
        String opencodeSetup = ""
            + "#!" + bash + "\n"
            + "set -e\n"
            + "PREFIX=\"" + prefix + "\"\n"
            + "HOME=\"" + home + "\"\n"
            + "export PATH=\"$PREFIX/bin:$HOME/.bun/bin:$PATH\"\n"
            + "export LD_LIBRARY_PATH=\"$PREFIX/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}\"\n"
            + "export TMPDIR=\"${TMPDIR:-$PREFIX/tmp}\"\n"
            + "mkdir -p \"$TMPDIR\"\n"
            + "REDIRECTOR_SO=\"$PREFIX/lib/libinvapp-redirector.so\"\n"
            + "[ -f \"$REDIRECTOR_SO\" ] && export LD_PRELOAD=\"$REDIRECTOR_SO${LD_PRELOAD:+:$LD_PRELOAD}\"\n"
            // Drop bun's JS stub immediately — it only prints the postinstall error.
            + "rm -f \"$PREFIX/bin/opencode\" \"$HOME/.bun/bin/opencode\" 2>/dev/null || true\n"
            + "case \"$(uname -m)\" in\n"
            + "  aarch64|arm64) OC_ASSET=opencode-linux-arm64.tar.gz; OC_LD=ld-linux-aarch64.so.1 ;;\n"
            + "  x86_64|amd64)  OC_ASSET=opencode-linux-x64.tar.gz;  OC_LD=ld-linux-x86-64.so.2 ;;\n"
            + "  *)             OC_ASSET=opencode-linux-arm64.tar.gz; OC_LD=ld-linux-aarch64.so.1 ;;\n"
            + "esac\n"
            + "OC_DIR=\"$PREFIX/libexec/opencode\"\n"
            + "OC_BIN=\"$OC_DIR/opencode\"\n"
            + "OC_VER=\"${OPENCODE_VERSION:-}\"\n"
            + "if [ -n \"$OC_VER\" ]; then\n"
            + "  case \"$OC_VER\" in v*) ;; *) OC_VER=\"v$OC_VER\" ;; esac\n"
            + "  OC_URL=\"https://github.com/anomalyco/opencode/releases/download/$OC_VER/$OC_ASSET\"\n"
            + "else\n"
            + "  OC_URL=\"https://github.com/anomalyco/opencode/releases/latest/download/$OC_ASSET\"\n"
            + "fi\n"
            + "echo \"Downloading $OC_ASSET (official linux binary; no bun install)…\"\n"
            + "echo \"  $OC_URL\"\n"
            + "mkdir -p \"$OC_DIR\" \"$TMPDIR\"\n"
            + "OC_TAR=\"$TMPDIR/$OC_ASSET\"\n"
            + "if command -v curl >/dev/null 2>&1; then\n"
            + "  curl -fL --retry 3 --retry-delay 2 -o \"$OC_TAR\" \"$OC_URL\"\n"
            + "elif command -v wget >/dev/null 2>&1; then\n"
            + "  wget -O \"$OC_TAR\" \"$OC_URL\"\n"
            + "else\n"
            + "  echo \"opencode-setup: need curl or wget\" >&2\n"
            + "  exit 127\n"
            + "fi\n"
            + "rm -rf \"$OC_DIR.new\"\n"
            + "mkdir -p \"$OC_DIR.new\"\n"
            + "tar -xzf \"$OC_TAR\" -C \"$OC_DIR.new\"\n"
            + "rm -f \"$OC_TAR\"\n"
            + "FOUND=$(find \"$OC_DIR.new\" -type f -name opencode 2>/dev/null | head -1)\n"
            + "if [ -z \"$FOUND\" ] || [ ! -f \"$FOUND\" ]; then\n"
            + "  echo \"opencode-setup: tarball had no opencode binary\" >&2\n"
            + "  rm -rf \"$OC_DIR.new\"\n"
            + "  exit 1\n"
            + "fi\n"
            + "chmod +x \"$FOUND\"\n"
            + "rm -rf \"$OC_DIR\"\n"
            + "mv \"$OC_DIR.new\" \"$OC_DIR\"\n"
            + "OC_BIN=$(find \"$OC_DIR\" -type f -name opencode 2>/dev/null | head -1)\n"
            + "if [ -z \"$OC_BIN\" ] || [ ! -x \"$OC_BIN\" ]; then\n"
            + "  echo \"opencode-setup: install failed (binary missing)\" >&2\n"
            + "  exit 1\n"
            + "fi\n"
            // glibc packages hardcode /data/data/com.termux — rewrite for this app id.
            // Prefer ld-linux --library-path over stock grun (grun also hardcodes com.termux
            // and does not pass --library-path, so libc.so.6 resolves to EACCES).
            + "fixup_glibc_paths() {\n"
            + "  local f\n"
            + "  for f in \"$PREFIX/opt/glibc-runner/glibc-runner.sh\" \"$PREFIX/bin/grun\"; do\n"
            + "    [ -f \"$f\" ] || continue\n"
            + "    grep -q 'com\\.termux' \"$f\" 2>/dev/null || continue\n"
            + "    sed -i 's|/data/data/com\\.termux/files|"
            + TermuxConstants.TERMUX_FILES_DIR_PATH + "|g' \"$f\" 2>/dev/null || true\n"
            + "  done\n"
            // Linker scripts named libc.so break runtime loads (invalid ELF). Prefer SONAME symlinks.
            + "  if [ -f \"$PREFIX/glibc/lib/libc.so.6\" ]; then\n"
            + "    ln -sfn libc.so.6 \"$PREFIX/glibc/lib/libc.so\" 2>/dev/null || true\n"
            + "  fi\n"
            + "  if [ -f \"$PREFIX/glibc/lib/libm.so.6\" ]; then\n"
            + "    ln -sfn libm.so.6 \"$PREFIX/glibc/lib/libm.so\" 2>/dev/null || true\n"
            + "  fi\n"
            + "  ln -sfn libdl.so.2 \"$PREFIX/glibc/lib/libdl.so\" 2>/dev/null || true\n"
            + "  ln -sfn libpthread.so.0 \"$PREFIX/glibc/lib/libpthread.so\" 2>/dev/null || true\n"
            // nostdlib stub: some glibc loads look for liblog; system liblog is Bionic-only.
            + "  if [ ! -f \"$PREFIX/glibc/lib/liblog.so\" ] && [ -x \"$PREFIX/bin/clang\" ]; then\n"
            + "    cat > \"$PREFIX/tmp/invapp-liblog-stub.c\" <<'STUBEOF'\n"
            + "int __android_log_write(int a, const char* b, const char* c) { (void)a;(void)b;(void)c; return 0; }\n"
            + "int __android_log_print(int a, const char* b, const char* c, ...) { (void)a;(void)b;(void)c; return 0; }\n"
            + "int __android_log_vprint(int a, const char* b, const char* c, void* d) { (void)a;(void)b;(void)c;(void)d; return 0; }\n"
            + "void __android_log_assert(const char* a, const char* b, const char* c, ...) { (void)a;(void)b;(void)c; }\n"
            + "STUBEOF\n"
            + "    \"$PREFIX/bin/clang\" -shared -nostdlib -fPIC -Wl,-soname,liblog.so \\\n"
            + "      -o \"$PREFIX/glibc/lib/liblog.so\" \"$PREFIX/tmp/invapp-liblog-stub.c\" 2>/dev/null || true\n"
            + "  fi\n"
            + "}\n"
            + "ensure_glibc() {\n"
            + "  if [ -x \"$PREFIX/glibc/lib/$OC_LD\" ]; then\n"
            + "    fixup_glibc_paths\n"
            + "    return 0\n"
            + "  fi\n"
            + "  echo \"opencode-setup: installing glibc (linux binaries need it on Android)…\"\n"
            + "  if ! command -v pkg >/dev/null 2>&1 && ! command -v apt-get >/dev/null 2>&1; then\n"
            + "    return 1\n"
            + "  fi\n"
            + "  mkdir -p \"$PREFIX/etc/apt/sources.list.d\"\n"
            // trusted=yes: apt-key under path-redirector sometimes fails on new repos.
            + "  printf '%s\\n' \\\n"
            + "    'deb [trusted=yes] https://packages-cf.termux.dev/apt/termux-glibc/ glibc stable' \\\n"
            + "    > \"$PREFIX/etc/apt/sources.list.d/glibc.list\"\n"
            + "  export DEBIAN_FRONTEND=noninteractive\n"
            + "  pkg install -y glibc-repo 2>/dev/null || true\n"
            + "  apt-get update -y 2>/dev/null || pkg update -y 2>/dev/null || true\n"
            + "  pkg install -y glibc glibc-runner 2>/dev/null \\\n"
            + "    || apt-get install -y glibc glibc-runner 2>/dev/null \\\n"
            + "    || pkg install -y glibc 2>/dev/null \\\n"
            + "    || apt-get install -y glibc 2>/dev/null \\\n"
            + "    || true\n"
            + "  if [ ! -x \"$PREFIX/glibc/lib/$OC_LD\" ]; then\n"
            + "    return 1\n"
            + "  fi\n"
            + "  fixup_glibc_paths\n"
            + "  return 0\n"
            + "}\n"
            + "if ! ensure_glibc; then\n"
            + "  echo \"opencode-setup: glibc missing.\" >&2\n"
            + "  echo \"  pkg install glibc-repo && pkg install glibc glibc-runner\" >&2\n"
            + "  echo \"Then re-run: opencode-setup\" >&2\n"
            + "  exit 1\n"
            + "fi\n"
            + "WRAPPER=\"$PREFIX/bin/opencode\"\n"
            + "rm -f \"$WRAPPER\" \"$HOME/.bun/bin/opencode\" 2>/dev/null || true\n"
            + "cat > \"$WRAPPER\" <<EOF\n"
            + "#!$PREFIX/bin/bash\n"
            + "# invapp-opencode-wrapper — OpenCode linux binary via glibc ld-linux\n"
            + "OC_BIN=\"$OC_BIN\"\n"
            + "PREFIX=\"$PREFIX\"\n"
            + "OC_LD=\"$OC_LD\"\n"
            + "LD=\"\\$PREFIX/glibc/lib/\\$OC_LD\"\n"
            + "LIB=\"\\$PREFIX/glibc/lib\"\n"
            + "export TMPDIR=\"\\${TMPDIR:-\\$PREFIX/tmp}\"\n"
            + "mkdir -p \"\\$TMPDIR\" 2>/dev/null || true\n"
            + "if [ ! -x \"\\$OC_BIN\" ]; then\n"
            + "  echo \"opencode: binary missing — run opencode-setup\" >&2\n"
            + "  exit 127\n"
            + "fi\n"
            + "if [ ! -x \"\\$LD\" ]; then\n"
            + "  echo \"opencode: glibc missing — run opencode-setup\" >&2\n"
            + "  exit 127\n"
            + "fi\n"
            // Empty LD_PRELOAD= (not unset): path redirector re-injects when the
            // key is absent; empty value is honored as an intentional clear.
            + "export LD_PRELOAD=\n"
            + "export LD_LIBRARY_PATH=\n"
            + "export PATH=\"\\$PREFIX/bin:\\$PATH\"\n"
            + "exec \"\\$LD\" --library-path \"\\$LIB\" \"\\$OC_BIN\" \"\\$@\"\n"
            + "EOF\n"
            + "chmod 700 \"$WRAPPER\"\n"
            + "mkdir -p \"$HOME/.bun/bin\"\n"
            + "rm -f \"$HOME/.bun/bin/opencode\" 2>/dev/null || true\n"
            + "cp -f \"$WRAPPER\" \"$HOME/.bun/bin/opencode\"\n"
            + "chmod 700 \"$HOME/.bun/bin/opencode\"\n"
            + "hash -r 2>/dev/null || true\n"
            + "export PATH=\"$PREFIX/bin:$HOME/.bun/bin:$PATH\"\n"
            + "if ! head -1 \"$WRAPPER\" | grep -q bash; then\n"
            + "  echo \"opencode-setup: wrapper write failed (still a bun symlink?)\" >&2\n"
            + "  exit 1\n"
            + "fi\n"
            + "if ! command -v opencode >/dev/null 2>&1; then\n"
            + "  echo \"opencode-setup: wrapper not on PATH\" >&2\n"
            + "  exit 1\n"
            + "fi\n"
            + "echo \"opencode-setup: probing opencode --version…\"\n"
            + "if opencode --version 2>&1; then\n"
            + "  echo \"OK. Run: td-ai   # starts web UI on :4096 for Preview\"\n"
            + "else\n"
            + "  echo \"opencode-setup: wrapper installed but binary failed under glibc\" >&2\n"
            + "  echo \"Binary: $OC_BIN\" >&2\n"
            + "  exit 1\n"
            + "fi\n";
        writeExec(new File(binDir, "opencode-setup"), opencodeSetup);

        String tdAi = ""
            + "#!" + bash + "\n"
            + "PREFIX=\"" + prefix + "\"\n"
            + "HOME=\"" + home + "\"\n"
            + "export PATH=\"$PREFIX/bin:$HOME/.bun/bin:$PATH\"\n"
            + "PORT=\"${1:-4096}\"\n"
            + "case \"$PORT\" in\n"
            + "  ''|*[!0-9]*) echo \"usage: td-ai [port]\" >&2; exit 2 ;;\n"
            + "esac\n"
            // Skip start when something already accepts connections on PORT.
            + "if (echo >/dev/tcp/127.0.0.1/\"$PORT\") >/dev/null 2>&1; then\n"
            + "  echo \"td-ai: already listening on :$PORT — open Preview\"\n"
            + "  exit 0\n"
            + "fi\n"
            + "if ! command -v bun >/dev/null 2>&1; then\n"
            + "  echo \"td-ai: bun missing — reopen the app\" >&2\n"
            + "  exit 127\n"
            + "fi\n"
            + "if ! command -v opencode >/dev/null 2>&1; then\n"
            + "  echo \"opencode not found — running opencode-setup…\"\n"
            + "  opencode-setup || exit $?\n"
            + "  export PATH=\"$PREFIX/bin:$HOME/.bun/bin:$PATH\"\n"
            + "  hash -r 2>/dev/null || true\n"
            + "fi\n"
            + "if ! command -v opencode >/dev/null 2>&1; then\n"
            + "  echo \"td-ai: opencode still missing after setup\" >&2\n"
            + "  exit 127\n"
            + "fi\n"
            + "echo \"\"\n"
            + "echo \"OpenCode web → http://127.0.0.1:$PORT/\"\n"
            + "echo \"In the app: drawer → Preview → Scan → tap $PORT\"\n"
            + "echo \"(Copy LAN shares Wi‑Fi URL only if the server binds 0.0.0.0)\"\n"
            + "echo \"\"\n"
            // Prefer web UI for Preview; fall back to serve if web subcommand missing.
            + "if opencode web --help >/dev/null 2>&1; then\n"
            + "  exec opencode web --port \"$PORT\" --hostname 0.0.0.0\n"
            + "fi\n"
            + "if opencode serve --help >/dev/null 2>&1; then\n"
            + "  exec opencode serve --port \"$PORT\" --hostname 0.0.0.0\n"
            + "fi\n"
            + "echo \"td-ai: opencode has no web/serve command — try: opencode --help\" >&2\n"
            + "exec opencode --help\n";
        writeExec(new File(binDir, "td-ai"), tdAi);

        // Dev-server helper: clear errors + Preview/LAN hints before bun run.
        String tdDev = ""
            + "#!" + bash + "\n"
            + "PREFIX=\"" + prefix + "\"\n"
            + "HOME=\"" + home + "\"\n"
            + "export PATH=\"$PREFIX/bin:$HOME/.bun/bin:$PATH\"\n"
            + "SCRIPT=\"${1:-dev}\"\n"
            + "if [ \"$#\" -gt 0 ]; then shift; fi\n"
            + "if ! command -v bun >/dev/null 2>&1; then\n"
            + "  echo \"td-dev: bun missing — reopen the app\" >&2\n"
            + "  exit 127\n"
            + "fi\n"
            + "if [ ! -f package.json ]; then\n"
            + "  echo \"td-dev: no package.json in $(pwd)\" >&2\n"
            + "  echo \"hint: cd ~/repos/<project> first\" >&2\n"
            + "  exit 1\n"
            + "fi\n"
            + "echo \"td-dev: bun run $SCRIPT $*\"\n"
            + "echo \"When the port appears: drawer → Preview → Scan (or Copy LAN)\"\n"
            + "echo \"\"\n"
            + "exec bun run \"$SCRIPT\" \"$@\"\n";
        writeExec(new File(binDir, "td-dev"), tdDev);

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
            + "NAME=\"${1-}\"\n"
            + "TEMPLATE=\"${2:-vanilla}\"\n"
            + "if [ -z \"$NAME\" ]; then\n"
            + "  echo \"usage: td-scaffold <name> [template]\" >&2\n"
            + "  echo \"templates: vanilla vanilla-ts react react-ts vue vue-ts pwa pwa-react\" >&2\n"
            + "  exit 2\n"
            + "fi\n"
            + "case \"$NAME\" in\n"
            + "  *[!a-zA-Z0-9._-]*) echo \"td-scaffold: invalid name '$NAME'\" >&2; exit 2 ;;\n"
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
            + "mkdir -p \"$HOME/repos\"\n"
            + "cd \"$HOME/repos\"\n"
            + "if [ -e \"$NAME\" ]; then\n"
            + "  echo \"td-scaffold: $HOME/repos/$NAME already exists\" >&2\n"
            + "  exit 1\n"
            + "fi\n"
            + "echo \"td-scaffold: create-vite $NAME --template $BASE_TEMPLATE\"\n"
            + "bunx --bun create-vite@latest \"$NAME\" --template \"$BASE_TEMPLATE\"\n"
            + "cd \"$NAME\"\n"
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
            + "echo \"td-scaffold: bun install\"\n"
            + "bun install\n"
            + "if [ \"$PWA\" = 1 ]; then\n"
            + "  echo \"td-scaffold: add vite-plugin-pwa\"\n"
            + "  bun add -d vite-plugin-pwa\n"
            + "  bun \"$PREFIX/libexec/invapp-vite-pwa.mjs\" \"$BASE_TEMPLATE\"\n"
            + "  echo \"td-scaffold: PWA enabled (vite-plugin-pwa)\"\n"
            + "fi\n"
            + "echo \"\"\n"
            + "echo \"OK: $HOME/repos/$NAME\"\n"
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

        ensureWorkspaceDirs();
        repairPrefixBinPermissions();
    }

    /**
     * If {@code node} is missing (or is our previous shim), install a tiny
     * wrapper that execs Bun. Leaves a real {@code nodejs} package binary alone.
     */
    private static void ensureNodeBunShim(@NonNull File binDir, @NonNull String bash,
                                          @NonNull String prefix) {
        File node = new File(binDir, "node");
        if (node.isFile() && !isInvappNodeShim(node)) {
            return;
        }
        String shim = ""
            + "#!" + bash + "\n"
            + "# invapp-bun-node-shim — provides node for #!/usr/bin/env node bins\n"
            + "exec \"" + prefix + "/bin/bun\" \"$@\"\n";
        writeExec(node, shim);
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
            "bun", "bunx", "node", "td-ai", "td-dev", "td-scaffold", "td-clone",
            "opencode-setup", "opencode", "bun-doctor"
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
