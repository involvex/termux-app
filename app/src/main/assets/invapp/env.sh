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
