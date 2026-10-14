# InVxTermux container -- sourced by every login shell.
#
# Gives the container the same CA/bun environment the APK's wrapper exports,
# so `bun`, `curl` and node behave identically whether you are in the app or
# in the container. No ~/.bashrc edits -- profile.d is the supported hook.
if [ -f "$PREFIX/lib/invapp/env.sh" ]; then
    . "$PREFIX/lib/invapp/env.sh" 2>/dev/null || true
fi
