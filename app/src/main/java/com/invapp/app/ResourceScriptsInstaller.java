package com.invapp.app;

import android.content.Context;
import android.system.Os;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.invapp.shared.logger.Logger;
import com.invapp.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Seeds one-click resource helpers under {@code $PREFIX/bin} (Ultra-inspired,
 * InVx-adapted). No tmux, no baked AVNC, no 1.2G auto-downloads.
 *
 * <p>Scripts: {@code td-resource-linux} (proot Ubuntu/Debian),
 * {@code td-resource-python}, {@code td-resource-qemu} (native pkgs only),
 * {@code td-resource-bun}, {@code td-resource-ai} (opencode/agent health),
 * {@code td-resource} (catalog lister).
 *
 * <p>Installer itself preserves {@code libinvapp-redirector.so} via
 * {@code LD_PRELOAD} (never bare {@code unset} at top level). The generated
 * container runner ({@code ~/debian-container/run.sh}) unsets it inside the
 * isolated proot mount — that string only appears inside the heredoc.
 */
public final class ResourceScriptsInstaller {

    private static final String LOG_TAG = "ResourceScriptsInstaller";

    public static final String ID_LINUX = "td-resource-linux";
    public static final String ID_PYTHON = "td-resource-python";
    public static final String ID_QEMU = "td-resource-qemu";
    public static final String ID_BUN = "td-resource-bun";
    public static final String ID_AI = "td-resource-ai";
    public static final String ID_CATALOG = "td-resource";

    private ResourceScriptsInstaller() {}

    /** All helper ids seeded into {@code $PREFIX/bin}. */
    @NonNull
    public static String[] allScriptIds() {
        return new String[] {
            ID_LINUX, ID_PYTHON, ID_QEMU, ID_BUN, ID_AI, ID_CATALOG
        };
    }

    /** Absolute path of a helper under {@code $PREFIX/bin}. */
    @NonNull
    public static File scriptFile(@NonNull String id) {
        return new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, id);
    }

    /**
     * Refresh all helpers on every app start (bin helpers are code, not user
     * data — overwrite is safe). Also refreshes bash completions.
     */
    public static void installIfNeeded(@NonNull Context context) {
        File binDir = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH);
        if (!binDir.isDirectory()) {
            Logger.logWarn(LOG_TAG, "Prefix bin missing; skip resource install");
            return;
        }
        int n = 0;
        for (String id : allScriptIds()) {
            String body = scriptBody(id);
            if (body == null) {
                continue;
            }
            if (writeExec(scriptFile(id), body)) {
                n++;
            }
        }
        installBashCompletions(TermuxConstants.TERMUX_PREFIX_DIR_PATH);
        Logger.logInfo(LOG_TAG, "Installed/refresh resource helpers (" + n + ")");
    }

    /** Shell body for an id, or null for unknown ids. */
    @Nullable
    public static String scriptBody(@NonNull String id) {
        switch (id) {
            case ID_LINUX:
                return linuxBody();
            case ID_PYTHON:
                return pythonBody();
            case ID_QEMU:
                return qemuBody();
            case ID_BUN:
                return bunBody();
            case ID_AI:
                return aiBody();
            case ID_CATALOG:
                return catalogBody();
            default:
                return null;
        }
    }

    @NonNull
    static String header(@NonNull String id) {
        String bash = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/bash";
        String prefix = TermuxConstants.TERMUX_PREFIX_DIR_PATH;
        String home = TermuxConstants.TERMUX_HOME_DIR_PATH;
        return ""
            + "#!" + bash + "\n"
            + "# invapp-resource: " + id + " (Ultra-inspired, InVx-adapted; no tmux)\n"
            + "set -e\n"
            + "PREFIX=\"" + prefix + "\"\n"
            + "HOME=\"" + home + "\"\n"
            + "export PATH=\"$PREFIX/bin:$HOME/.bun/bin:$PATH\"\n"
            + "export LD_LIBRARY_PATH=\"$PREFIX/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}\"\n"
            + "REDIRECTOR_SO=\"$PREFIX/lib/libinvapp-redirector.so\"\n"
            + "[ -f \"$REDIRECTOR_SO\" ] && export LD_PRELOAD=\"$REDIRECTOR_SO${LD_PRELOAD:+:$LD_PRELOAD}\"\n"
            + "export TMPDIR=\"${TMPDIR:-$PREFIX/tmp}\"\n"
            + "mkdir -p \"$TMPDIR\"\n";
    }

    /** proot Ubuntu Noble/Jammy or Debian Bookworm into ~/debian-container. */
    @NonNull
    static String linuxBody() {
        return header(ID_LINUX)
            + "CONTAINER_DIR=\"$HOME/debian-container\"\n"
            + "ROOTFS=\"$CONTAINER_DIR/rootfs\"\n"
            + "RUN_SH=\"$CONTAINER_DIR/run.sh\"\n"
            + "usage() {\n"
            + "  echo \"usage: td-resource-linux [--run CMD...] [--reinstall]\" >&2\n"
            + "  echo \"  default: install Ubuntu Noble (fallback Jammy, Debian Bookworm) via proot\" >&2\n"
            + "  echo \"  --run: exec inside container via run.sh (proot isolated, LD_PRELOAD unset there)\" >&2\n"
            + "  exit 2\n"
            + "}\n"
            + "if [ \"${1-}\" = \"-h\" ] || [ \"${1-}\" = \"--help\" ]; then usage; fi\n"
            + "# --run passthrough uses the generated isolated runner.\n"
            + "if [ \"${1-}\" = \"--run\" ]; then\n"
            + "  shift\n"
            + "  if [ ! -x \"$RUN_SH\" ]; then echo \"td-resource-linux: container missing — run without args first\" >&2; exit 1; fi\n"
            + "  exec \"$RUN_SH\" \"$@\"\n"
            + "fi\n"
            + "if [ \"${1-}\" = \"--reinstall\" ]; then rm -rf \"$CONTAINER_DIR\"; shift; fi\n"
            + "if [ -x \"$RUN_SH\" ] && [ -f \"$ROOTFS/bin/bash\" ]; then\n"
            + "  echo \"Container present: $CONTAINER_DIR\"\n"
            + "  echo \"Run: td-resource-linux --run bash   (shares \\$HOME as /root/shared)\"\n"
            + "  exit 0\n"
            + "fi\n"
            + "for dep in proot tar xz dpkg; do\n"
            + "  if ! command -v \"$dep\" >/dev/null 2>&1; then\n"
            + "    echo \"td-resource-linux: installing proot wget tar xz-utils…\"\n"
            + "    pkg install -y proot wget tar xz-utils || { echo \"pkg install failed\" >&2; exit 1; }\n"
            + "    break\n"
            + "  fi\n"
            + "done\n"
            + "ARCH=$(dpkg --print-architecture 2>/dev/null || uname -m)\n"
            + "case \"$ARCH\" in\n"
            + "  aarch64|arm64) IMG_ARCH=arm64 ;;\n"
            + "  arm*|armhf) IMG_ARCH=armhf ;;\n"
            + "  x86_64|amd64) IMG_ARCH=amd64 ;;\n"
            + "  i*86) IMG_ARCH=i386 ;;\n"
            + "  *) echo \"unsupported arch: $ARCH\" >&2; exit 1 ;;\n"
            + "esac\n"
            + "DATE=$(date +%Y%m%d 2>/dev/null || echo 20260212)\n"
            + "CANDIDATES=\"ubuntu/noble ubuntu/jammy debian/bookworm\"\n"
            + "TARBALL=\"$TMPDIR/rootfs.tar.xz\"\n"
            + "OK=0\n"
            + "for cand in $CANDIDATES; do\n"
            + "  dist=${cand%%/*}; rel=${cand##*/}\n"
            + "  URL=\"https://images.linuxcontainers.org/images/$dist/$IMG_ARCH/default/$DATE/rootfs.tar.xz\"\n"
            + "  echo \"Trying $dist/$rel ($IMG_ARCH)…\"\n"
            + "  echo \"  $URL\"\n"
            + "  if command -v curl >/dev/null 2>&1; then curl -fL --retry 2 -o \"$TARBALL\" \"$URL\" || continue\n"
            + "  elif command -v wget >/dev/null 2>&1; then wget -O \"$TARBALL\" \"$URL\" || continue\n"
            + "  else echo \"need curl or wget\" >&2; exit 127; fi\n"
            + "  if xz -t \"$TARBALL\" 2>/dev/null; then OK=1; DISTRO=\"$dist\"; break; fi\n"
            + "  echo \"checksum/verify failed for $cand — next…\" >&2\n"
            + "done\n"
            + "if [ \"$OK\" != 1 ]; then echo \"td-resource-linux: all images failed\" >&2; exit 1; fi\n"
            + "mkdir -p \"$ROOTFS\"\n"
            + "echo \"Extracting ($DISTRO) → $ROOTFS …\"\n"
            + "proot --link2symlink tar -xJf \"$TARBALL\" -C \"$ROOTFS\"\n"
            + "rm -f \"$TARBALL\"\n"
            + "# sources.list per distro/arch.\n"
            + "case \"$DISTRO\" in\n"
            + "  debian) echo \"deb http://deb.debian.org/debian bookworm main\" > \"$ROOTFS/etc/apt/sources.list\" ;;\n"
            + "  *) case \"$IMG_ARCH\" in arm64|armhf) MIRROR=http://ports.ubuntu.com ;; *) MIRROR=http://archive.ubuntu.com/ubuntu ;; esac\n"
            + "     printf '%s\\n' \"deb $MIRROR noble main universe\" > \"$ROOTFS/etc/apt/sources.list\" ;;\n"
            + "esac\n"
            + "printf '%s\\n' 'nameserver 8.8.8.8' 'nameserver 1.1.1.1' > \"$ROOTFS/etc/resolv.conf\"\n"
            + "# Isolated runner: LD_PRELOAD is unset ONLY inside the container mount.\n"
            + "cat > \"$RUN_SH\" <<'RUNEOF'\n"
            + "#!/bin/bash\n"
            + "# generated by td-resource-linux — isolated proot runner\n"
            + "unset LD_PRELOAD\n"
            + "CONTAINER_DIR=\"$(cd \"$(dirname \"$0\")\" && pwd)\"\n"
            + "ROOTFS=\"$CONTAINER_DIR/rootfs\"\n"
            + "SHARED_HOME=\"${HOME:-$HOME}\"\n"
            + "EXTRA_BIND=\"\"\n"
            + "if [ -n \"${SHARED_HOME}\" ] && [ -d \"${SHARED_HOME}\" ]; then\n"
            + "  EXTRA_BIND=\"-b ${SHARED_HOME}:/root/shared\"\n"
            + "fi\n"
            + "# shellcheck disable=SC2086\n"
            + "exec proot --link2symlink -0 -r \"$ROOTFS\" -b /dev -b /proc -b /sys $EXTRA_BIND -w /root /usr/bin/env -i HOME=/root PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin LANG=C.UTF-8 /bin/bash --login \"$@\"\n"
            + "RUNEOF\n"
            + "chmod 700 \"$RUN_SH\"\n"
            + "echo \"OK: $CONTAINER_DIR\"\n"
            + "echo \"Enter: td-resource-linux --run bash\"\n";
    }

    /** Python env via pkg (no proot JDK confusion). */
    @NonNull
    static String pythonBody() {
        return header(ID_PYTHON)
            + "if [ \"${1-}\" = \"-h\" ] || [ \"${1-}\" = \"--help\" ]; then\n"
            + "  echo \"usage: td-resource-python [--venv NAME]\" >&2\n"
            + "  echo \"  installs pkg python, ensures pip, optional venv under ~/repos\" >&2\n"
            + "  exit 0\n"
            + "fi\n"
            + "VENV=\"${2-}\"\n"
            + "if [ \"${1-}\" = \"--venv\" ] && [ -z \"$VENV\" ]; then echo \"td-resource-python: --venv needs a name\" >&2; exit 2; fi\n"
            + "if ! command -v python >/dev/null 2>&1; then\n"
            + "  echo \"td-resource-python: pkg install python…\"\n"
            + "  pkg install -y python || exit 1\n"
            + "fi\n"
            + "python --version\n"
            + "if ! python -m pip --version >/dev/null 2>&1; then\n"
            + "  echo \"pip missing — pkg install python should include it; trying ensurepip…\" >&2\n"
            + "  python -m ensurepip --upgrade || true\n"
            + "fi\n"
            + "python -m pip --version || true\n"
            + "if [ \"${1-}\" = \"--venv\" ]; then\n"
            + "  mkdir -p \"$HOME/repos\"\n"
            + "  DEST=\"$HOME/repos/$VENV/.venv\"\n"
            + "  if [ -e \"$DEST\" ]; then echo \"venv exists: $DEST\"; exit 0; fi\n"
            + "  echo \"Creating venv: $DEST\"\n"
            + "  python -m venv \"$DEST\"\n"
            + "  echo \"Activate: source $DEST/bin/activate\"\n"
            + "  exit 0\n"
            + "fi\n"
            + "echo \"OK. Next: python -m pip install <pkg>   or: td-resource-python --venv myapp\"\n";
    }

    /** Native qemu packages only — no large image auto-download. */
    @NonNull
    static String qemuBody() {
        return header(ID_QEMU)
            + "if [ \"${1-}\" = \"-h\" ] || [ \"${1-}\" = \"--help\" ]; then\n"
            + "  echo \"usage: td-resource-qemu\" >&2\n"
            + "  echo \"  installs native qemu pkgs (no VM image download). Bring your own qcow2/iso.\" >&2\n"
            + "  exit 0\n"
            + "fi\n"
            + "echo \"td-resource-qemu: installing native qemu packages…\"\n"
            + "pkg install -y unstable-repo x11-repo 2>/dev/null || true\n"
            + "pkg install -y qemu-system-aarch64 qemu-utils genisoimage python curl wget openssh termux-api || exit 1\n"
            + "echo \"OK: $(qemu-system-aarch64 --version 2>/dev/null | head -1)\"\n"
            + "echo \"Next (manual): qemu-img create -f qcow2 ~/qemu-vm/disk.qcow2 20G\"\n"
            + "echo \"VNC view: use an external AVNC app or a web VNC in ~/repos + drawer Preview.\"\n";
    }

    /** Bun health + Android filter hints. */
    @NonNull
    static String bunBody() {
        return header(ID_BUN)
            + "REAL=\"$PREFIX/libexec/bun\"\n"
            + "echo \"real:    $REAL\"\n"
            + "echo \"wrapper: $PREFIX/bin/bun\"\n"
            + "ls -la \"$PREFIX/bin/bun\" \"$REAL\" \"$PREFIX/bin/bunx\" \"$PREFIX/bin/node\" 2>&1 || true\n"
            + "if [ ! -x \"$REAL\" ]; then\n"
            + "  echo \"bun: missing Android binary — reopen the app to reinstall\" >&2\n"
            + "  exit 127\n"
            + "fi\n"
            + "\"$PREFIX/bin/bun\" --version\n"
            + "echo \"node → $(command -v node 2>/dev/null || echo missing)\"\n"
            + "echo \"npm_config_os=${npm_config_os:-unset} npm_config_cpu=${npm_config_cpu:-unset}\"\n"
            + "echo \"Tip: bun install adds --os=android --cpu=arm64|x64 automatically via wrapper.\"\n"
            + "echo \"If a Windows lockfile pinned linux-* natives: rm -rf node_modules <lockfile> && bun i\"\n"
            + "echo \"Doctor: bun-doctor\"\n";
    }

    /** OpenCode / Cursor Agent health — hints only, no downloads. */
    @NonNull
    static String aiBody() {
        return header(ID_AI)
            + "echo \"== opencode ==\"\n"
            + "OC_BIN=$(find \"$PREFIX/libexec/opencode\" -type f -name opencode 2>/dev/null | head -1)\n"
            + "echo \"binary:  ${OC_BIN:-missing (run opencode-setup)}\"\n"
            + "echo \"wrapper: $PREFIX/bin/opencode\"\n"
            + "ls -la \"$PREFIX/bin/opencode\" 2>&1 || true\n"
            + "ARCH_LD=\"ld-linux-aarch64.so.1\"\n"
            + "case \"$(uname -m)\" in x86_64|amd64) ARCH_LD=\"ld-linux-x86-64.so.2\" ;; esac\n"
            + "echo \"ld:      $PREFIX/glibc/lib/$ARCH_LD\"\n"
            + "[ -x \"$PREFIX/glibc/lib/$ARCH_LD\" ] || echo \"glibc missing — run opencode-setup\" >&2\n"
            + "echo \"shim:    $PREFIX/lib/libinvapp-opencode-shim.so\"\n"
            + "[ -f \"$PREFIX/lib/libinvapp-opencode-shim.so\" ] || echo \"shim missing — reopen app or run opencode-setup\" >&2\n"
            + "echo \"\"\n"
            + "echo \"== cursor agent ==\"\n"
            + "echo \"binary:  $HOME/.local/share/cursor-agent (run cursor-agent-setup)\"\n"
            + "ls -la \"$PREFIX/bin/agent\" 2>&1 || true\n"
            + "echo \"\"\n"
            + "echo \"Fixes: opencode-fix-net   # DNS/CA + shim rebuild\"\n"
            + "echo \"Serve: td-ai   # binds 0.0.0.0:4096; Preview → http://127.0.0.1:4096/\"\n"
            + "echo \"Chat API fail ('typo in the url or port'): rm -rf ~/.cache/opencode && opencode-fix-net && td-ai\"\n";
    }

    /** Catalog lister. */
    @NonNull
    static String catalogBody() {
        return header(ID_CATALOG)
            + "echo \"InVxTermux resources (Ultra-inspired, no tmux):\"\n"
            + "for h in td-resource-linux td-resource-python td-resource-qemu td-resource-bun td-resource-ai; do\n"
            + "  if command -v \"$h\" >/dev/null 2>&1; then st=\"ready\"; else st=\"missing (reopen app)\"; fi\n"
            + "  printf '  %-20s %s\\n' \"$h\" \"$st\"\n"
            + "done\n"
            + "echo \"Usage: <helper> --help\"\n";
    }

    static void installBashCompletions(@NonNull String prefix) {
        File completionD = new File(prefix, "etc/bash_completion.d");
        if (!completionD.isDirectory() && !completionD.mkdirs()) {
            Logger.logWarn(LOG_TAG, "Could not create " + completionD);
            return;
        }
        String body = ""
            + "# invapp td-resource completions\n"
            + "_invapp_td_resource() {\n"
            + "  local cur=\"${COMP_WORDS[COMP_CWORD]}\"\n"
            + "  local cmds=\"td-resource td-resource-linux td-resource-python td-resource-qemu td-resource-bun td-resource-ai\"\n"
            + "  if [ \"$COMP_CWORD\" -eq 1 ]; then\n"
            + "    COMPREPLY=( $(compgen -W \"$cmds\" -- \"$cur\") )\n"
            + "    return\n"
            + "  fi\n"
            + "  case \"${COMP_WORDS[1]}\" in\n"
            + "    td-resource-linux) COMPREPLY=( $(compgen -W \"--run --reinstall --help\" -- \"$cur\") ) ;;\n"
            + "    td-resource-python) COMPREPLY=( $(compgen -W \"--venv --help\" -- \"$cur\") ) ;;\n"
            + "    *) COMPREPLY=( $(compgen -W \"--help\" -- \"$cur\") ) ;;\n"
            + "  esac\n"
            + "}\n"
            + "for _c in td-resource td-resource-linux td-resource-python td-resource-qemu td-resource-bun td-resource-ai; do\n"
            + "  complete -F _invapp_td_resource \"$_c\" 2>/dev/null || true\n"
            + "done\n"
            + "unset _c\n";
        File dest = new File(completionD, "invapp-td-resource.bash");
        try {
            File tmp = new File(dest.getAbsolutePath() + ".new");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
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
            Logger.logWarn(LOG_TAG, "Completion install failed: " + e.getMessage());
        }
    }

    private static boolean writeExec(@NonNull File dest, @NonNull String contents) {
        try {
            File parent = dest.getParentFile();
            if (parent != null && !parent.isDirectory()) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            File tmp = new File(dest.getAbsolutePath() + ".new");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(contents.getBytes(StandardCharsets.UTF_8));
            }
            //noinspection OctalInteger
            Os.chmod(tmp.getAbsolutePath(), 0700);
            if (dest.exists() && !dest.delete()) {
                try {
                    Os.remove(dest.getAbsolutePath());
                } catch (Exception e) {
                    Logger.logWarn(LOG_TAG, "replace " + dest + ": " + e.getMessage());
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
            Logger.logWarn(LOG_TAG, "write " + dest + ": " + e.getMessage());
            return false;
        }
    }
}
