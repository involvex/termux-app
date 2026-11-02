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

# td_udocker_container_exists <name>
td_udocker_container_exists() {
    td_udocker_ensure || return 1
    udocker inspect "$1" >/dev/null 2>&1
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

# td_udocker_run <name> [cmd...]
# Runs a command (default: /bin/bash -l) inside the named container.
# Keeps LD_PRELOAD for the path redirector. Shared host netns (udocker P1
# default) so guest :5901 is reachable from Android VNC clients.
# Interactive TTY: udocker attaches when stdin is a terminal (no docker-style -i).
td_udocker_run() {
    _name="${1:-}"
    [ -n "$_name" ] || { echo "td_udocker_run: need container name" >&2; return 2; }
    shift
    [ $# -gt 0 ] || set -- /bin/bash -l
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
