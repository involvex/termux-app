# InVxTermux -- login banner hook.
#
# Sourced by every login shell via $PREFIX/etc/profile.d/. Prints nothing unless
# this is a genuinely interactive terminal: automation, `ssh host cmd`, scp,
# widget RUN_COMMAND and `bash -lc` stay byte-for-byte silent.
#
# Opt out for one session:  INVAPP_NO_BANNER=1
# Opt out permanently:      ivx-configure banner off
#
# Never edits ~/.bashrc / ~/.profile / ~/.zshrc -- profile.d is the hook.

[ -n "${BASH_VERSION-}" ] || return 0
case "${-:-}" in
    *i*) ;;
    *) return 0 ;;
esac
[ -t 1 ] || return 0
[ "${TERM:-dumb}" != dumb ] || return 0
[ -z "${SSH_ORIGINAL_COMMAND:-}" ] || return 0
[ -z "${INVAPP_NO_BANNER:-}" ] || return 0
[ -z "${INVX_BANNER_SHOWN:-}" ] || return 0

if [ -r "${PREFIX:-}/lib/invapp/banner.sh" ]; then
    # shellcheck source=/dev/null
    . "${PREFIX}/lib/invapp/banner.sh"
    ivx_banner_print || true
    export INVX_BANNER_SHOWN=1
fi

return 0
