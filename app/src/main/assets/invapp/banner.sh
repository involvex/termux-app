#!/system/bin/sh
# InVxTermux -- login banner.
#
# Sourced by $PREFIX/etc/profile.d/invapp-banner.sh (and reused by
# `ivx-configure banner preview`); never executed directly.
#
# The built-in art is pre-rendered pyfiglet output for "InvolveX":
#   font "pixelfont" by Ankur Bhagat (forked from fsymbols), GPL-3.
# Only the rendered glyphs live here -- the .flf font itself is NOT shipped.
# Regenerate custom art with `ivx-configure banner text "TEXT"` (needs figlet).
#
# Design constraints -- login must stay instant and side-effect free:
#   * no network, no python, no pkg, no sleeps, no `bun --version`
#   * bash only ($'...' escapes); the profile.d hook gates on BASH_VERSION
#   * return 0 on every path: a broken user config must never break login

case "${__INVX_BANNER_LOADED:-}" in
    '') __INVX_BANNER_LOADED=1 ;;
    *) return 0 ;;
esac

# ---------------------------------------------------------------------------
# config ($XDG_CONFIG_HOME/invapp/config, sourced like ~/.bashrc)
# ---------------------------------------------------------------------------
ivx_banner_config() {
    _ivx_conf="${XDG_CONFIG_HOME:-$HOME/.config}/invapp/config"
    if [ -f "$_ivx_conf" ] && [ -r "$_ivx_conf" ]; then
        # shellcheck disable=SC1090
        . "$_ivx_conf" 2>/dev/null || true
    fi
    : "${IVX_BANNER:=1}"
    : "${IVX_STYLE:=pixelfont}"
    : "${IVX_COLOR:=green}"
    : "${IVX_TIPS:=1}"
    : "${IVX_STATUS:=1}"
    : "${IVX_FONT:=pixelfont}"
    : "${IVX_ART:=}"
    _ivx_art_file="${XDG_CONFIG_HOME:-$HOME/.config}/invapp/art.txt"
}

# ---------------------------------------------------------------------------
# colours (never emit escapes into a pipe or with NO_COLOR)
# ---------------------------------------------------------------------------
ivx_banner_color() {
    _ivx_c=''
    [ -n "${NO_COLOR:-}" ] && return 0
    [ "${IVX_COLOR}" = none ] && return 0
    [ -t 1 ] || return 0
    case "${IVX_COLOR}" in
        green)   _ivx_c=$'\033[1;32m' ;;
        cyan)    _ivx_c=$'\033[1;36m' ;;
        magenta) _ivx_c=$'\033[1;35m' ;;
        white)   _ivx_c=$'\033[1;37m' ;;
        rainbow)
            case $(( $1 % 5 )) in
                1) _ivx_c=$'\033[1;32m' ;;
                2) _ivx_c=$'\033[1;36m' ;;
                3) _ivx_c=$'\033[1;35m' ;;
                4) _ivx_c=$'\033[1;33m' ;;
                *) _ivx_c=$'\033[1;34m' ;;
            esac
            ;;
        *) _ivx_c=$'\033[1;32m' ;;
    esac
}

# ---------------------------------------------------------------------------
# width / style fallback
# ---------------------------------------------------------------------------
ivx_banner_cols() {
    _ivx_cols=$(tput cols 2>/dev/null || true)
    case "${_ivx_cols:-}" in
        ''|*[!0-9]*) _ivx_cols=${COLUMNS:-80} ;;
    esac
    case "${_ivx_cols:-}" in
        ''|*[!0-9]*) _ivx_cols=80 ;;
    esac
    [ "$_ivx_cols" -ge 10 ] 2>/dev/null || _ivx_cols=80
}

ivx_banner_pick_style() {
    case "${IVX_STYLE}" in
        plain|mini|small|pixelfont) _ivx_style="${IVX_STYLE}" ;;
        custom) _ivx_style=custom ;;
        *) _ivx_style=pixelfont ;;
    esac
    # A custom art file wins, but only when one actually exists.
    if [ "$_ivx_style" = custom ]; then
        if [ -r "${_ivx_art_file}" ] && [ -s "${_ivx_art_file}" ]; then
            return 0
        fi
        _ivx_style=pixelfont
    fi
    if [ "$_ivx_style" = auto ]; then
        _ivx_style=pixelfont
    fi
    # Never let art overflow a phone-width terminal.
    if [ "$_ivx_style" = pixelfont ] && [ "$_ivx_cols" -lt 34 ]; then
        _ivx_style=mini
    fi
    if [ "$_ivx_style" = small ] && [ "$_ivx_cols" -lt 36 ]; then
        _ivx_style=mini
    fi
    if [ "$_ivx_style" = mini ] && [ "$_ivx_cols" -lt 22 ]; then
        _ivx_style=plain
    fi
}

# Width of the built-in art in columns. Kept as a constant because ${#s}
# counts bytes (3 per block glyph) in a C locale.
ivx_banner_art_width() {
    case "${_ivx_style}" in
        pixelfont) _ivx_art_w=30 ;;
        small)     _ivx_art_w=33 ;;
        mini)      _ivx_art_w=20 ;;
        plain)     _ivx_art_w=11 ;;
        *)         _ivx_art_w=0 ;;
    esac
}

ivx_banner_art() {
    case "${_ivx_style}" in
        plain)
            _ivx_art='InVxTermux'
            ;;
        mini)
            _ivx_art='___
 | ._    _ |   _ \/
_|_| |\/(_)|\/(/_/\'
            ;;
        small)
            _ivx_art=' ___             _        __  __
|_ _|_ ___ _____| |_ _____\ \/ /
 | || '"'"' \ V / _ \ \ V / -_)>  <
|___|_||_\_/\___/_|\_/\___/_/\_\'
            ;;
        *)
            _ivx_art='█ █▄ █ █ █ █▀█ █   █ █ █▀▀ ▀▄▀
█ █ ▀█ ▀▄▀ █▄█ █▄▄ ▀▄▀ ██▄ █ █'
            ;;
    esac
}

# ---------------------------------------------------------------------------
# rendering
# ---------------------------------------------------------------------------
ivx_banner_print_art() {
    _ivx_n=0
    printf '%s\n' "$_ivx_art" | while IFS= read -r _ivx_line; do
        _ivx_n=$((_ivx_n + 1))
        ivx_banner_color "$_ivx_n"
        if [ -n "$_ivx_c" ]; then
            if [ "$_ivx_style" = custom ]; then
                printf '%s%s\033[0m\n' "$_ivx_c" "$_ivx_line"
            else
                printf '  %s%s\033[0m\n' "$_ivx_c" "$_ivx_line"
            fi
        else
            if [ "$_ivx_style" = custom ]; then
                printf '%s\n' "$_ivx_line"
            else
                printf '  %s\n' "$_ivx_line"
            fi
        fi
    done
}

ivx_banner_center() {
    _ivx_len=${#1}
    _ivx_pad=$(( (_ivx_art_w - _ivx_len) / 2 ))
    [ "$_ivx_pad" -lt 0 ] && _ivx_pad=0
    if [ "$_ivx_art_w" -gt 0 ]; then
        _ivx_pad=$((_ivx_pad + 2))
    fi
    _ivx_tn=$(( ${_ivx_tn:-0} + 1 ))
    ivx_banner_color "$_ivx_tn"
    if [ -n "$_ivx_c" ]; then
        printf '%*s%s%s\033[0m\n' "$_ivx_pad" '' "$_ivx_c" "$1"
    else
        printf '%*s%s\n' "$_ivx_pad" '' "$1"
    fi
}

ivx_banner_status() {
    _ivx_status=''
    _ivx_bun=$(cat "${PREFIX:-/nonexistent}/var/lib/invapp/bun.version" 2>/dev/null || true)
    case "${_ivx_bun:-}" in
        ''|unsupported) : ;;
        *) _ivx_status="bun ${_ivx_bun}" ;;
    esac
    if command -v git >/dev/null 2>&1; then
        _ivx_status="${_ivx_status:+$_ivx_status · }git ✓"
    fi
    if command -v opencode >/dev/null 2>&1; then
        _ivx_status="${_ivx_status:+$_ivx_status · }opencode ✓"
    elif command -v agent >/dev/null 2>&1; then
        _ivx_status="${_ivx_status:+$_ivx_status · }agent ✓"
    fi
    [ -n "$_ivx_status" ] && ivx_banner_center "$_ivx_status"
    return 0
}

ivx_banner_tips() {
    if [ "${INVAPP_ON_DEVICE:-1}" = 0 ]; then
        ivx_banner_center 'ivx-configure · td-dev · td-info · td-install-extras'
    else
        ivx_banner_center 'ivx-configure · td-dev · td-ai · td-info'
    fi
}

# ---------------------------------------------------------------------------
# entry point
# ---------------------------------------------------------------------------
ivx_banner_print() {
    ivx_banner_config
    # IVX_FORCE=1 is used by `ivx-configure banner preview`: preview must show
    # the banner even when it is switched off for logins.
    if [ "${IVX_FORCE:-0}" != 1 ]; then
        [ "${IVX_BANNER}" = 0 ] && return 0
        [ -n "${INVAPP_NO_BANNER:-}" ] && return 0
    fi

    ivx_banner_cols
    ivx_banner_pick_style

    # Custom art (art.txt or IVX_ART) replaces the built-in glyph table.
    if [ -n "${IVX_ART}" ] && [ -r "${IVX_ART}" ] && [ -s "${IVX_ART}" ]; then
        _ivx_style=custom
        _ivx_art=$(cat "${IVX_ART}" 2>/dev/null || true)
    elif [ "${IVX_STYLE}" = custom ] && [ -r "${_ivx_art_file}" ] && [ -s "${_ivx_art_file}" ]; then
        _ivx_style=custom
        _ivx_art=$(cat "${_ivx_art_file}" 2>/dev/null || true)
    else
        ivx_banner_art
    fi
    ivx_banner_art_width

    [ -n "${_ivx_art:-}" ] || return 0

    printf '\n'
    ivx_banner_print_art
    printf '\n'
    ivx_banner_center 'InVxTermux · phone + PC terminal dev'
    [ "${IVX_STATUS}" = 0 ] || ivx_banner_status
    [ "${IVX_TIPS}" = 0 ] || ivx_banner_tips
    printf '\n'
    return 0
}
