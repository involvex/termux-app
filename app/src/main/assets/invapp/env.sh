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

# --- proot guest execution (td-nethunter*) -----------------------------------
# The GUI helpers need THREE things proot-distro does not give us on this fork:
# a rootfs path that resolves across proot-distro layouts, proot's temp dir
# decoupled from the shared $PREFIX/tmp, and an exec that a fallback can take
# over when `proot-distro login` fails. All three live here so the scripts,
# the self-test and the container twin agree.

# td_proot_rootfs <container-name> — print the container's rootfs path.
# proot-distro 5.x keeps containers under containers/<name>/rootfs; older
# builds used installed-rootfs/<name>. The path is printed whether or not it
# exists, so callers can report the path they tried.
td_proot_rootfs() {
    _rfs="$PREFIX/var/lib/proot-distro/containers/$1/rootfs"
    if [ ! -d "$_rfs" ]; then
        _rfs="$PREFIX/var/lib/proot-distro/installed-rootfs/$1"
    fi
    printf '%s\n' "$_rfs"
}

# td_proot_exec <container-name> <cmd...>
# Runs <cmd> as root inside the guest, with the binds a desktop needs.
#
# LD_PRELOAD is dropped, not left alone: the path redirector and termux-exec
# rewrite absolute paths (/bin, /usr/bin, /data/data/com.termux) at exec time,
# and a guest argv must reach proot untouched — proot-distro pops it for proot
# for the same reason. PROOT_TMP_DIR is private so a concurrent proot session
# cannot wipe the loader proot extracts there.
# PROOT_NO_SECCOMP defaults to 1: Android's seccomp filter rejects the ptrace
# setup proot uses on several devices, and the cost is only speed.
td_proot_exec() {
    _name="$1"
    shift
    _rfs="$(td_proot_rootfs "$_name")"
    if [ ! -d "$_rfs" ]; then
        echo "td_proot_exec: no rootfs for '$_name' (looked in $_rfs)" >&2
        return 1
    fi
    _tmp="${TD_NETHUNTER_STATE:-$PREFIX/var/td-nethunter}/proot-tmp"
    mkdir -p "$_tmp" "$PREFIX/tmp" 2>/dev/null || true
    env -u LD_PRELOAD \
        PROOT_TMP_DIR="$_tmp" \
        PROOT_NO_SECCOMP="${PROOT_NO_SECCOMP:-1}" \
        proot -0 --kill-on-exit --link2symlink -r "$_rfs" -w /root \
        -b /dev -b /proc -b /sys \
        -b /dev/urandom:/dev/random \
        -b "$PREFIX:/termux" \
        /usr/bin/env -i \
        HOME=/root \
        TERM="${TERM:-xterm-256color}" \
        PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
        "$@"
}

# td_guest_login <container-name> <cmd...>
# proot-distro first (it owns container state and the guest environment), then
# a direct proot exec we control end to end. The fallback is what makes the
# GUI helpers work when proot-distro's login path cannot exec the guest shell.
# LD_PRELOAD is left in place for proot-distro itself: it is a Termux Python
# program that needs the redirector to find its own stdlib.
td_guest_login() {
    _name="$1"
    shift
    if command -v proot-distro >/dev/null 2>&1; then
        PROOT_TMP_DIR="${TD_NETHUNTER_STATE:-$PREFIX/var/td-nethunter}/proot-tmp" \
            proot-distro login "$_name" -- "$@" && return 0
        echo "td_guest_login: proot-distro login failed — using direct proot" >&2
    fi
    td_proot_exec "$_name" "$@"
}
