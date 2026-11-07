# InVxTermux -- optional DNS/proxy/GOPROXY exports from user config.
#
# Sourced by every login shell via $PREFIX/etc/profile.d/. Reads
# ${XDG_CONFIG_HOME:-$HOME/.config}/invapp/config (errors ignored) and
# exports HTTP(S)_PROXY / ALL_PROXY / NO_PROXY / GOPROXY only when the
# matching IVX_* key is non-empty. Never fetches free proxies; never
# edits ~/.bashrc. Open a new session after `ivx-configure network …`.
#
# Safe for widgets / bash -lc / non-interactive: exports only, no I/O.

_ivx_net_cfg="${XDG_CONFIG_HOME:-${HOME:-}/.config}/invapp/config"
if [ -r "$_ivx_net_cfg" ]; then
    # shellcheck source=/dev/null
    . "$_ivx_net_cfg" 2>/dev/null || true
fi
unset _ivx_net_cfg

# HTTP / HTTPS proxies (uppercase + lowercase for picky tools).
if [ -n "${IVX_HTTP_PROXY:-}" ]; then
    export HTTP_PROXY="$IVX_HTTP_PROXY"
    export http_proxy="$IVX_HTTP_PROXY"
    if [ -z "${IVX_HTTPS_PROXY:-}" ]; then
        export HTTPS_PROXY="$IVX_HTTP_PROXY"
        export https_proxy="$IVX_HTTP_PROXY"
    fi
fi
if [ -n "${IVX_HTTPS_PROXY:-}" ]; then
    export HTTPS_PROXY="$IVX_HTTPS_PROXY"
    export https_proxy="$IVX_HTTPS_PROXY"
fi

# SOCKS / catch-all.
if [ -n "${IVX_ALL_PROXY:-}" ]; then
    export ALL_PROXY="$IVX_ALL_PROXY"
    export all_proxy="$IVX_ALL_PROXY"
fi

if [ -n "${IVX_NO_PROXY:-}" ]; then
    export NO_PROXY="$IVX_NO_PROXY"
    export no_proxy="$IVX_NO_PROXY"
fi

# Go module proxy (after DNS works; empty = leave Go defaults alone).
if [ -n "${IVX_GOPROXY:-}" ]; then
    export GOPROXY="$IVX_GOPROXY"
fi
