# InVxTermux container -- sourced by every login shell.
#
# Gives the container the same CA/bun environment the APK's wrapper exports,
# so `bun`, `curl` and node behave identically whether you are in the app or
# in the container. No ~/.bashrc edits -- profile.d is the supported hook.
#
# Host bash needs $PREFIX/lib (libandroid-support, libreadline). The proot
# wrapper intentionally sets LD_LIBRARY_PATH to libexec/proot-libs only; if that
# value leaks into an interactive shell, restore the real prefix lib dir.
if [ -n "${PREFIX:-}" ] && [ -d "$PREFIX/lib" ]; then
    case ":${LD_LIBRARY_PATH:-}:" in
        *":$PREFIX/lib:"*|*"$PREFIX/lib:"*) ;;
        *)
            export LD_LIBRARY_PATH="$PREFIX/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
            ;;
    esac
    case ":${LD_LIBRARY_PATH:-}:" in
        *"/libexec/proot-libs:"*|*"${PREFIX}/libexec/proot-libs:"*)
            export LD_LIBRARY_PATH="$PREFIX/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
            ;;
    esac
fi
if [ -f "$PREFIX/lib/invapp/env.sh" ]; then
    . "$PREFIX/lib/invapp/env.sh" 2>/dev/null || true
fi
