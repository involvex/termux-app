#!/system/bin/sh
# InVxTermux container — shared environment for seeded helpers.
#
# Sourced by every helper in $PREFIX/bin (and by the profile.d snippet), not
# executed, so the shebang is cosmetic. POSIX on purpose: it must also work when
# sourced from a POSIX shell.
#
# Mirrors the contract of the on-device helpers written by
# TermuxBunInstaller.java ($PREFIX/bin, $HOME/repos, bun on PATH) so the same
# muscle memory works inside the container.
#
# What is dropped compared to the APK, and why:
#   - libinvapp-redirector.so  : only needed to map com.termux -> com.involvex
#                                on device. In the container the prefix *is*
#                                /data/data/com.termux (stock bootstrap), so
#                                nothing has to be remapped.
#   - libinvapp-bun-seccomp.so : Android's seccomp traps openat2/fchmodat2 for
#                                Bun. Docker has its own profile -- see the
#                                README section "Bun + Docker seccomp".
#   - libinvapp-opencode-shim  : patches getifaddrs/resolv for Android's netd.
#                                Docker provides /etc/resolv.conf and /sys.
# What is KEPT, and why (easy to get wrong):
#   - npm_config_platform=android / bun --os=android : Termux packages are
#     bionic-linked even in a container, so Linux/glibc natives cannot load.

: "${PREFIX:=/data/data/com.termux/files/usr}"
: "${HOME:=/data/data/com.termux/files/home}"
export PREFIX HOME

PATH="$PREFIX/bin:$HOME/.bun/bin:$HOME/.local/bin:$PATH"
export PATH
# Device vs container. The device prefix lives under the app package name
# (com.involvex.termux_app); the stock container prefix is com.termux. Lets
# shared helpers branch UX (drawer/Preview on-device vs. docker host outside)
# from a single asset instead of a duplicate device-only copy. Detected via
# the package name in $PREFIX, which is already normalized above.
case "$PREFIX" in
    *com.involvex*) INVAPP_ON_DEVICE=1 ;;
    *)             INVAPP_ON_DEVICE=0 ;;
esac
export INVAPP_ON_DEVICE

export TMPDIR="${TMPDIR:-$PREFIX/tmp}"
mkdir -p "$TMPDIR" 2>/dev/null || true

# bun (and other prefix binaries) are bionic-linked against $PREFIX/lib. Without
# this they fail to start with no message at all -- the same reason the APK's
# wrapper exports it.
export LD_LIBRARY_PATH="$PREFIX/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
export OPENSSL_CONF="${OPENSSL_CONF:-$PREFIX/etc/tls/openssl.cnf}"

export SSL_CERT_FILE="${SSL_CERT_FILE:-$PREFIX/etc/tls/cert.pem}"
export NODE_EXTRA_CA_CERTS="${NODE_EXTRA_CA_CERTS:-$PREFIX/etc/tls/cert.pem}"
export CURL_CA_BUNDLE="${CURL_CA_BUNDLE:-$PREFIX/etc/tls/cert.pem}"

export BUN_INSTALL="${BUN_INSTALL:-$PREFIX}"
export BUN_INSTALL_BIN="${BUN_INSTALL_BIN:-$PREFIX/bin}"
export BUN_INSTALL_CACHE_DIR="${BUN_INSTALL_CACHE_DIR:-$HOME/.bun/install/cache}"
export XDG_CACHE_HOME="${XDG_CACHE_HOME:-$HOME/.cache}"
export npm_config_cache="${npm_config_cache:-$HOME/.npm}"
export npm_config_prefix="${npm_config_prefix:-$PREFIX}"

INVAPP_LIB_DIR="${INVAPP_LIB_DIR:-$PREFIX/lib/invapp}"
export INVAPP_LIB_DIR

td_arch() {
    case "$(uname -m)" in
        aarch64|arm64) echo aarch64 ;;
        x86_64|amd64)  echo x64 ;;
        *)             echo "$(uname -m)" ;;
    esac
}

td_bun_cpu() {
    case "$(uname -m)" in
        aarch64|arm64) echo arm64 ;;
        x86_64|amd64)  echo x64 ;;
        *)             echo arm64 ;;
    esac
}

# Bun, OpenCode and Cursor Agent publish x64 + aarch64 only (verified against
# oven-sh/bun releases: no armv7, no 386). arm/i686 images are base-only.
td_supports_ai_abi() {
    case "$(td_arch)" in
        aarch64|x64) return 0 ;;
        *) return 1 ;;
    esac
}

td_no_ai_abi_note() {
    echo "$(basename "$0"): $(uname -m) has no bun/AI binaries (x64 + aarch64 only)" >&2
    echo "  node fallback: pkg install nodejs" >&2
}

td_require_bun() {
    if ! command -v bun >/dev/null 2>&1; then
        td_no_ai_abi_note
        exit 127
    fi
}

td_require_git() {
    if ! command -v git >/dev/null 2>&1; then
        echo "$(basename "$0"): git missing -- pkg install git" >&2
        exit 127
    fi
}

# --- udocker guest execution (td-nethunter* / td-udocker*) -------------------
# Replaces the old proot-distro NetHunter path. Termux's `udocker` package
# (P1/proot engine) pulls OCI images and runs them without root. Leftover trees
# under $PREFIX/var/lib/proot-distro/ are orphaned — remove with
# `proot-distro remove <name>` or `rm -rf` if you no longer need them.
#
# LD_PRELOAD stays set for the host `udocker` Python process: Termux's package
# patches hardcode /data/data/com.termux/.../bin/proot, and the path redirector
# must rewrite those opens on this fork. Do not `env -u LD_PRELOAD udocker …`.
# PROOT_NO_SECCOMP defaults to 1 (Android seccomp vs proot ptrace).

: "${TD_UDOCKER_IMAGE:=kalilinux/kali-rolling}"
: "${TD_UDOCKER_NAME:=kali}"
TD_UDOCKER_STATE="${TD_UDOCKER_STATE:-$PREFIX/var/td-udocker}"
export TD_UDOCKER_IMAGE TD_UDOCKER_NAME TD_UDOCKER_STATE

# td_udocker_name_from_image <oci-ref> — container name for an image.
# kalilinux/kali-rolling → kali (NetHunter default); otherwise last path
# segment with any tag stripped (ubuntu:22.04 → ubuntu).
td_udocker_name_from_image() {
    _img="${1:-$TD_UDOCKER_IMAGE}"
    case "$_img" in
        kalilinux/kali-rolling|kalilinux/kali-rolling:*|kali-rolling|kali)
            printf '%s\n' "kali"
            return 0
            ;;
    esac
    _n="${_img##*/}"
    _n="${_n%%:*}"
    printf '%s\n' "$_n"
}

# td_udocker_platform — OCI platform string for this device.
# Python 3.13 on Android reports sys.platform=android, so bare `udocker pull`
# looks for android/arm64 in multi-arch manifests and fails with
# "manifest not found" / "no files downloaded". Always pull as linux/*.
td_udocker_platform() {
    if [ -n "${TD_UDOCKER_PLATFORM:-}" ]; then
        printf '%s\n' "$TD_UDOCKER_PLATFORM"
        return 0
    fi
    case "$(uname -m)" in
        aarch64|arm64) printf '%s\n' "linux/arm64" ;;
        armv7*|armv8*|arm) printf '%s\n' "linux/arm/v7" ;;
        x86_64|amd64) printf '%s\n' "linux/amd64" ;;
        i686|i386) printf '%s\n' "linux/386" ;;
        *) printf '%s\n' "linux/arm64" ;;
    esac
}

# td_udocker_arch_tag — single-arch Docker Hub tag Kali publishes
# (arm64|amd64|armhf|i386). Prefer these over :latest on Android.
td_udocker_arch_tag() {
    case "$(td_udocker_platform)" in
        linux/arm64) printf '%s\n' "arm64" ;;
        linux/arm/v7|linux/arm) printf '%s\n' "armhf" ;;
        linux/amd64) printf '%s\n' "amd64" ;;
        linux/386) printf '%s\n' "i386" ;;
        *) printf '%s\n' "arm64" ;;
    esac
}

# td_udocker_resolve_image <oci-ref> — rewrite untagged / :latest Kali (and
# similar multi-arch images) to an arch-specific tag so Hub returns a single
# manifest udocker can fetch without platform negotiation.
td_udocker_resolve_image() {
    _img="${1:-$TD_UDOCKER_IMAGE}"
    case "$_img" in
        kalilinux/kali-rolling|kalilinux/kali-rolling:latest)
            printf '%s\n' "kalilinux/kali-rolling:$(td_udocker_arch_tag)"
            ;;
        *)
            printf '%s\n' "$_img"
            ;;
    esac
}

# td_udocker_pull <oci-ref> — pull with --platform=linux/….
# Sets TD_UDOCKER_LAST_PULLED to the ref that succeeded (do not capture stdout —
# udocker prints progress there).
td_udocker_pull() {
    _want="${1:-}"
    [ -n "$_want" ] || return 2
    _plat="$(td_udocker_platform)"
    _resolved="$(td_udocker_resolve_image "$_want")"
    TD_UDOCKER_LAST_PULLED=""
    td_udocker_ensure || return 1
    # Prefer the resolved (arch-tagged) ref first.
    if PROOT_NO_SECCOMP="${PROOT_NO_SECCOMP:-1}" \
        udocker pull --platform="$_plat" "$_resolved"; then
        TD_UDOCKER_LAST_PULLED="$_resolved"
        return 0
    fi
    # Retry without --platform (older udocker); still use resolved tag.
    if PROOT_NO_SECCOMP="${PROOT_NO_SECCOMP:-1}" \
        udocker pull "$_resolved"; then
        TD_UDOCKER_LAST_PULLED="$_resolved"
        return 0
    fi
    # Last try: original ref + platform (untagged multi-arch non-Kali images).
    if [ "$_resolved" != "$_want" ]; then
        if PROOT_NO_SECCOMP="${PROOT_NO_SECCOMP:-1}" \
            udocker pull --platform="$_plat" "$_want"; then
            TD_UDOCKER_LAST_PULLED="$_want"
            return 0
        fi
    fi
    return 1
}

# td_udocker_ensure — require the udocker binary; export PROOT_NO_SECCOMP.
td_udocker_ensure() {
    if ! command -v udocker >/dev/null 2>&1; then
        echo "td_udocker_ensure: udocker missing — run: td-udocker-setup" >&2
        return 1
    fi
    export PROOT_NO_SECCOMP="${PROOT_NO_SECCOMP:-1}"
    return 0
}

# td_udocker_ensure_proot — Termux ELF proot must win over a pip "proot" shim.
td_udocker_ensure_proot() {
    _p="$PREFIX/bin/proot"
    if [ ! -f "$_p" ]; then
        echo "td_udocker_ensure_proot: installing proot" >&2
        pkg install -y proot || return 1
    elif ! head -c 4 "$_p" 2>/dev/null | od -An -tx1 | grep -q '7f 45 4c 46'; then
        echo "td_udocker_ensure_proot: rogue non-ELF proot shim — reinstalling" >&2
        pkg reinstall -y proot 2>/dev/null \
            || apt-get install -y --reinstall proot \
            || { rm -f "$_p"; pkg install -y proot; } \
            || return 1
    fi
    "$_p" --version >/dev/null 2>&1 || {
        echo "td_udocker_ensure_proot: proot unusable — try: pip uninstall proot && pkg install -y -f proot" >&2
        return 1
    }
    return 0
}

# td_udocker_rootfs_dir <name> — persistent extracted rootfs (NetHunter path).
# udocker import re-extracts layers with the same broken host proot/tar combo on
# this fork, so kali.download images live here and run under direct proot.
td_udocker_rootfs_dir() {
    printf '%s\n' "${TD_UDOCKER_STATE}/rootfs/$1"
}

# td_udocker_backend <name> — "rootfs" | "udocker" | empty
td_udocker_backend() {
    _bf="${TD_UDOCKER_STATE}/$1.backend"
    if [ -f "$_bf" ]; then
        head -1 "$_bf"
        return 0
    fi
    if [ -d "$(td_udocker_rootfs_dir "$1")" ] \
        && td_udocker_find_rootfs_dir "$(td_udocker_rootfs_dir "$1")" >/dev/null 2>&1; then
        printf '%s\n' "rootfs"
        return 0
    fi
    if command -v udocker >/dev/null 2>&1 && udocker inspect "$1" >/dev/null 2>&1; then
        printf '%s\n' "udocker"
        return 0
    fi
    return 1
}

# td_udocker_container_exists <name> — rootfs tree or udocker container.
td_udocker_container_exists() {
    _be="$(td_udocker_backend "$1" 2>/dev/null)" || return 1
    [ -n "$_be" ]
}

# td_udocker_ready <name> — container exists and setup left a ready marker.
td_udocker_ready() {
    [ -f "$TD_UDOCKER_STATE/$1.ready" ] || return 1
    td_udocker_container_exists "$1"
}

# td_udocker_nethunter_ready <name> — VNC/desktop stack installed.
td_udocker_nethunter_ready() {
    [ -f "$TD_UDOCKER_STATE/$1.nethunter.ready" ] || return 1
    td_udocker_ready "$1"
}

# td_udocker_conf_init — ensure ~/.udocker/udocker.conf so guest daemons
# (TigerVNC) survive after `udocker run` returns, and seccomp stays off.
td_udocker_conf_init() {
    _home="${HOME:-$PREFIX/../home}"
    _dir="${UDOCKER_DIR:-$_home/.udocker}"
    mkdir -p "$_dir"
    _cfg="$_dir/udocker.conf"
    if [ ! -f "$_cfg" ]; then
        cat >"$_cfg" <<'EOF'
[DEFAULT]
# Leave VNC (and other guest daemons) alive after udocker run exits.
proot_killonexit = False
proot_noseccomp = True
proot_link2symlink = True
EOF
        return 0
    fi
    # Existing file (user or older setup): force the keys VNC needs.
    if ! grep -q '^[[:space:]]*proot_killonexit[[:space:]]*=' "$_cfg" 2>/dev/null; then
        printf '\nproot_killonexit = False\n' >>"$_cfg"
    else
        sed -i 's/^[[:space:]]*proot_killonexit[[:space:]]*=.*/proot_killonexit = False/' "$_cfg" 2>/dev/null || true
    fi
    if ! grep -q '^[[:space:]]*proot_noseccomp[[:space:]]*=' "$_cfg" 2>/dev/null; then
        printf 'proot_noseccomp = True\n' >>"$_cfg"
    fi
}

# Official NetHunter rootless rootfs (kali.download) — Hub pulls often fail on
# Android (auth/SSL/registry). Same tarballs the KeX installer uses.
: "${TD_NETHUNTER_ROOTFS_BASE:=https://kali.download/nethunter-images/current/rootfs}"
: "${TD_NETHUNTER_VARIANT:=nano}"

# td_nethunter_arch — arm64|armhf for NetHunter rootfs filenames.
td_nethunter_arch() {
    case "$(uname -m)" in
        aarch64|arm64) printf '%s\n' "arm64" ;;
        armv7*|armv8*|arm) printf '%s\n' "armhf" ;;
        *) printf '%s\n' "arm64" ;;
    esac
}

# td_udocker_find_rootfs_dir <extract-dir> — directory that contains bin/sh
# (or usr/bin/bash). NetHunter tarballs unpack as kali-arm64/; a flat docker
# export has bin/ at the top. Wrong nesting is why "proot backup → udocker"
# often reports missing /bin/sh.
td_udocker_find_rootfs_dir() {
    _base="$1"
    [ -d "$_base" ] || return 1
    if [ -e "$_base/bin/sh" ] || [ -e "$_base/usr/bin/bash" ] || [ -e "$_base/bin/bash" ]; then
        printf '%s\n' "$_base"
        return 0
    fi
    # Single top-level dir (kali-arm64, kali-armhf, …).
    _n=0
    _only=""
    for _d in "$_base"/*/; do
        [ -d "$_d" ] || continue
        _n=$((_n + 1))
        _only="${_d%/}"
    done
    if [ "$_n" = 1 ] && { [ -e "$_only/bin/sh" ] || [ -e "$_only/usr/bin/bash" ] || [ -e "$_only/bin/bash" ]; }; then
        printf '%s\n' "$_only"
        return 0
    fi
    # Deep search (maxdepth 3).
    _hit=$(find "$_base" -maxdepth 3 \( -path '*/bin/sh' -o -path '*/usr/bin/bash' \) -type f 2>/dev/null | head -1)
    [ -n "$_hit" ] || return 1
    case "$_hit" in
        */usr/bin/bash) printf '%s\n' "$(dirname "$(dirname "$(dirname "$_hit")")")" ;;
        */bin/sh|*/bin/bash) printf '%s\n' "$(dirname "$(dirname "$_hit")")" ;;
        *) return 1 ;;
    esac
}

# td_udocker_proot_loader — Termux proot loader path (needed when LD_PRELOAD
# is cleared; proot's compiled-in path points at com.termux).
td_udocker_proot_loader() {
    for _l in "$PREFIX/libexec/proot/loader" "$PREFIX/lib/proot/loader"; do
        if [ -x "$_l" ]; then
            printf '%s\n' "$_l"
            return 0
        fi
    done
    return 1
}

# td_udocker_extract_rootfs <archive> <dest-dir>
# Official install-nethunter-termux uses `proot --link2symlink tar` so hardlinks
# become symlinks. On this fork that only works with LD_PRELOAD cleared and
# PROOT_LOADER set — otherwise proot dies with
#   execve("…/bin/tar"): No such file or directory
# (redirector/termux-exec rewrite the host tar argv). Device-node EPERM under
# ./dev is expected. Success = bin/sh exists after extract, not tar's rc.
td_udocker_extract_rootfs() {
    _archive="$1"
    _dest="$2"
    mkdir -p "$_dest"
    _tar_bin="$(command -v tar)" || {
        echo "td_udocker_extract_rootfs: tar not on PATH" >&2
        return 1
    }
    # Prefer realpath so proot gets a stable absolute path.
    if command -v realpath >/dev/null 2>&1; then
        _tar_bin="$(realpath "$_tar_bin" 2>/dev/null || echo "$_tar_bin")"
        _archive="$(realpath "$_archive" 2>/dev/null || echo "$_archive")"
        _dest="$(realpath "$_dest" 2>/dev/null || echo "$_dest")"
    fi
    _tar_flags=""
    case "$_archive" in
        *.tar.xz|*.txz) _tar_flags="-xJf" ;;
        *.tar.gz|*.tgz) _tar_flags="-xzf" ;;
        *.tar) _tar_flags="-xf" ;;
        *)
            if "$_tar_bin" -tJf "$_archive" >/dev/null 2>&1; then _tar_flags="-xJf"
            elif "$_tar_bin" -tzf "$_archive" >/dev/null 2>&1; then _tar_flags="-xzf"
            else _tar_flags="-xf"
            fi
            ;;
    esac
    _err="${_dest}.tar.err"
    _ldr="$(td_udocker_proot_loader)" || _ldr=""
    # Stock Termux proot looks under /data/data/com.termux/.../tmp when
    # LD_PRELOAD is cleared — point it at our prefix temp instead.
    mkdir -p "$PREFIX/tmp" "${TD_UDOCKER_STATE}/proot-tmp"
    _ptmp="${TD_UDOCKER_STATE}/proot-tmp"

    # 1) proot --link2symlink with a clean preload (fork-safe).
    if [ -n "$_ldr" ]; then
        env -u LD_PRELOAD \
            PROOT_LOADER="$_ldr" \
            PROOT_TMP_DIR="$_ptmp" \
            TMPDIR="$PREFIX/tmp" \
            PROOT_NO_SECCOMP="${PROOT_NO_SECCOMP:-1}" \
            proot --link2symlink \
            "$_tar_bin" $_tar_flags "$_archive" -C "$_dest" \
            >"$_err" 2>&1 || true
    else
        env -u LD_PRELOAD \
            PROOT_TMP_DIR="$_ptmp" \
            TMPDIR="$PREFIX/tmp" \
            PROOT_NO_SECCOMP="${PROOT_NO_SECCOMP:-1}" \
            proot --link2symlink \
            "$_tar_bin" $_tar_flags "$_archive" -C "$_dest" \
            >"$_err" 2>&1 || true
    fi

    # 2) Fallback: plain tar (ignore mknod/hardlink failures). Core shells are
    #    usually regular files; duplicate hardlink names may be missing.
    if ! td_udocker_find_rootfs_dir "$_dest" >/dev/null; then
        echo "td_udocker_extract_rootfs: proot path failed — plain tar fallback" >&2
        [ -s "$_err" ] && tail -8 "$_err" >&2
        rm -rf "$_dest"
        mkdir -p "$_dest"
        "$_tar_bin" $_tar_flags "$_archive" -C "$_dest" \
            >"$_err" 2>&1 || true
    fi

    if ! td_udocker_find_rootfs_dir "$_dest" >/dev/null; then
        echo "td_udocker_extract_rootfs: extract left no usable rootfs" >&2
        [ -s "$_err" ] && tail -15 "$_err" >&2
        return 1
    fi
    _noise='Cannot mknod|Cannot hard link|Exiting with failure|proot error|proot info|fatal error|can.t canonicalize|Unable to create temp directory|proot warning'
    if [ -s "$_err" ] && grep -qvE "$_noise" "$_err" 2>/dev/null; then
        echo "td_udocker_extract_rootfs: non-fatal tar notes:" >&2
        grep -vE "$_noise" "$_err" 2>/dev/null | tail -8 >&2 || true
    fi
    rm -f "$_err"
    return 0
}

# td_udocker_install_rootfs_tar <archive> <name>
# Extract a NetHunter/proot rootfs into $TD_UDOCKER_STATE/rootfs/<name> and
# mark backend=rootfs. Does NOT use `udocker import` — that re-runs host proot
# to unpack layers and fails on this fork ("while extracting image layer" /
# guest bash with no execute bit). Same end state as KeX: a directory + proot.
td_udocker_install_rootfs_tar() {
    _archive="$1"
    _cname="$2"
    [ -f "$_archive" ] || { echo "td_udocker_install_rootfs_tar: missing $_archive" >&2; return 1; }
    [ -n "$_cname" ] || { echo "td_udocker_install_rootfs_tar: need name" >&2; return 2; }
    td_udocker_ensure_proot || return 1
    _work="${TD_UDOCKER_STATE}/import-$$"
    _dest="$(td_udocker_rootfs_dir "$_cname")"
    rm -rf "$_work"
    mkdir -p "$_work" "$TD_UDOCKER_STATE"
    echo "td_udocker_install_rootfs_tar: extracting with proot --link2symlink …" >&2
    if ! td_udocker_extract_rootfs "$_archive" "$_work"; then
        rm -rf "$_work"
        return 1
    fi
    _root="$(td_udocker_find_rootfs_dir "$_work")" || {
        echo "td_udocker_install_rootfs_tar: no bin/sh in archive" >&2
        rm -rf "$_work"
        return 1
    }
    echo "td_udocker_install_rootfs_tar: rootfs=$_root → $_dest" >&2
    mkdir -p "$_root/dev" "$_root/proc" "$_root/sys"
    find "$_root/dev" -mindepth 1 -maxdepth 1 \( -type b -o -type c -o -type p \) -delete 2>/dev/null || true
    # Executable bits sometimes drop across Android extracts.
    chmod +x "$_root/bin/sh" "$_root/bin/bash" "$_root/usr/bin/bash" \
        "$_root/usr/bin/env" 2>/dev/null || true
    chmod -R u+rwX "$_root/bin" "$_root/usr/bin" "$_root/sbin" "$_root/usr/sbin" 2>/dev/null || true
    # Drop a broken half-imported udocker container with the same name.
    if command -v udocker >/dev/null 2>&1; then
        udocker rm -f "$_cname" >/dev/null 2>&1 || udocker rm "$_cname" >/dev/null 2>&1 || true
    fi
    rm -rf "$_dest"
    mkdir -p "$(dirname "$_dest")"
    # Move the guest tree into place (handles kali-arm64/ nesting).
    if [ "$_root" = "$_work" ]; then
        mv "$_work" "$_dest" || { rm -rf "$_work"; return 1; }
    else
        mv "$_root" "$_dest" || { rm -rf "$_work"; return 1; }
        rm -rf "$_work"
    fi
    printf '%s\n' "rootfs" >"${TD_UDOCKER_STATE}/${_cname}.backend"
    return 0
}

# Compatibility alias (older call sites / docs).
td_udocker_import_rootfs_tar() {
    td_udocker_install_rootfs_tar "$@"
}

# td_udocker_proot_run <name> [cmd...] — fork-safe direct proot into a rootfs.
td_udocker_proot_run() {
    _name="${1:-}"
    [ -n "$_name" ] || return 2
    shift
    [ $# -gt 0 ] || set -- /bin/bash -l
    _rfs="$(td_udocker_rootfs_dir "$_name")"
    _rfs="$(td_udocker_find_rootfs_dir "$_rfs" 2>/dev/null || echo "$_rfs")"
    [ -d "$_rfs" ] || { echo "td_udocker_proot_run: no rootfs at $_rfs" >&2; return 1; }
    td_udocker_ensure_proot || return 1
    _ldr="$(td_udocker_proot_loader)" || _ldr=""
    mkdir -p "$PREFIX/tmp" "${TD_UDOCKER_STATE}/proot-tmp" \
        "$_rfs/dev" "$_rfs/proc" "$_rfs/sys" "$_rfs/tmp"
    _koe="--kill-on-exit"
    [ "${TD_PROOT_KEEP_ALIVE:-0}" = 1 ] && _koe=""
    _env="HOME=/root TERM=${TERM:-xterm-256color} PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin LANG=C.UTF-8"
    if [ -n "$_ldr" ]; then
        env -u LD_PRELOAD \
            PROOT_LOADER="$_ldr" \
            PROOT_TMP_DIR="${TD_UDOCKER_STATE}/proot-tmp" \
            TMPDIR="$PREFIX/tmp" \
            PROOT_NO_SECCOMP="${PROOT_NO_SECCOMP:-1}" \
            proot -0 $_koe --link2symlink -r "$_rfs" -w /root \
            -b /dev -b /proc -b /sys \
            -b /dev/urandom:/dev/random \
            -b "$PREFIX:/termux" \
            /usr/bin/env -i $_env "$@"
    else
        env -u LD_PRELOAD \
            PROOT_TMP_DIR="${TD_UDOCKER_STATE}/proot-tmp" \
            TMPDIR="$PREFIX/tmp" \
            PROOT_NO_SECCOMP="${PROOT_NO_SECCOMP:-1}" \
            proot -0 $_koe --link2symlink -r "$_rfs" -w /root \
            -b /dev -b /proc -b /sys \
            -b /dev/urandom:/dev/random \
            -b "$PREFIX:/termux" \
            /usr/bin/env -i $_env "$@"
    fi
}

# td_udocker_load_docker_save <docker-save.tar> [image-name] [container-name]
# Loads a `docker save` tarball (layers+metadata), then creates a container.
# PC: docker pull --platform linux/arm64 kalilinux/kali-rolling:arm64
#     docker save -o kali.tar kalilinux/kali-rolling:arm64
td_udocker_load_docker_save() {
    _file="$1"
    _imgname="${2:-kalilinux/kali-rolling:arm64}"
    _cname="${3:-kali}"
    [ -f "$_file" ] || { echo "td_udocker_load_docker_save: missing $_file" >&2; return 1; }
    td_udocker_ensure || return 1
    td_udocker_conf_init
    if ! PROOT_NO_SECCOMP="${PROOT_NO_SECCOMP:-1}" \
        udocker load -i "$_file" "$_imgname"; then
        # Some builds ignore the name arg; try load without rename.
        PROOT_NO_SECCOMP="${PROOT_NO_SECCOMP:-1}" udocker load -i "$_file" \
            || return 1
    fi
    if td_udocker_container_exists "$_cname" 2>/dev/null; then
        udocker rm -f "$_cname" >/dev/null 2>&1 || udocker rm "$_cname" >/dev/null 2>&1 || true
    fi
    # Prefer creating from the named image; fall back to first listed image.
    if ! PROOT_NO_SECCOMP="${PROOT_NO_SECCOMP:-1}" \
        udocker create --name="$_cname" "$_imgname"; then
        _first=$(udocker images 2>/dev/null | awk 'NR>1 {print $1; exit}')
        [ -n "$_first" ] || return 1
        PROOT_NO_SECCOMP="${PROOT_NO_SECCOMP:-1}" \
            udocker create --name="$_cname" "$_first" || return 1
    fi
    PROOT_NO_SECCOMP="${PROOT_NO_SECCOMP:-1}" \
        udocker setup --execmode=P1 "$_cname" 2>/dev/null || true
    TD_UDOCKER_LAST_PULLED="$_imgname"
    return 0
}

# td_nethunter_download_rootfs [variant] — download official NH rootfs to
# $TD_UDOCKER_STATE and print the local path on stdout.
td_nethunter_download_rootfs() {
    _var="${1:-${TD_NETHUNTER_VARIANT:-nano}}"
    _arch="$(td_nethunter_arch)"
    _name="kali-nethunter-rootfs-${_var}-${_arch}.tar.xz"
    _url="${TD_NETHUNTER_ROOTFS_BASE}/${_name}"
    _dest="${TD_UDOCKER_STATE}/${_name}"
    mkdir -p "$TD_UDOCKER_STATE"
    if [ -f "$_dest" ] && [ -s "$_dest" ]; then
        echo "td_nethunter_download_rootfs: using existing $_dest" >&2
        printf '%s\n' "$_dest"
        return 0
    fi
    echo "td_nethunter_download_rootfs: fetching $_url" >&2
    echo "  (nano≈170MiB minimal≈120MiB full≈1.7GiB compressed)" >&2
    if command -v curl >/dev/null 2>&1; then
        curl -fL --retry 3 --retry-delay 2 -o "$_dest.partial" "$_url" \
            || { rm -f "$_dest.partial"; return 1; }
    elif command -v wget >/dev/null 2>&1; then
        wget -O "$_dest.partial" "$_url" || { rm -f "$_dest.partial"; return 1; }
    else
        echo "td_nethunter_download_rootfs: need curl or wget" >&2
        return 1
    fi
    mv -f "$_dest.partial" "$_dest"
    printf '%s\n' "$_dest"
}

# td_udocker_run <name> [cmd...]
# Runs a command (default: /bin/bash -l) inside the named guest.
# NetHunter / --from-tar installs use backend=rootfs → direct fork-safe proot
# (udocker import re-extracts layers and fails on this fork). Hub/docker-save
# keep backend=udocker. Shared host netns so guest :5901 is reachable from
# Android VNC clients. For long-lived guests (Xvnc) set TD_PROOT_KEEP_ALIVE=1.
td_udocker_run() {
    _name="${1:-}"
    [ -n "$_name" ] || { echo "td_udocker_run: need container name" >&2; return 2; }
    shift
    [ $# -gt 0 ] || set -- /bin/bash -l
    _be="$(td_udocker_backend "$_name" 2>/dev/null)" || {
        echo "td_udocker_run: no guest '$_name' — run td-nethunter-setup / td-udocker-setup" >&2
        return 1
    }
    if [ "$_be" = "rootfs" ]; then
        td_udocker_proot_run "$_name" "$@"
        return $?
    fi
    td_udocker_ensure || return 1
    td_udocker_ensure_proot || return 1
    td_udocker_conf_init
    # Intentional: do not unset LD_PRELOAD (redirector for com.termux paths).
    PROOT_NO_SECCOMP="${PROOT_NO_SECCOMP:-1}" \
        udocker run "$_name" "$@"
}

# td_guest_login <name> <cmd...> — compatibility alias used by older call sites.
td_guest_login() {
    _name="$1"
    shift
    [ $# -gt 0 ] || set -- /bin/bash -l
    td_udocker_run "$_name" "$@"
}
