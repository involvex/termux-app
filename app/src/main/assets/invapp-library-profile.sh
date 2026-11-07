# invapp-library — auto-source bootstrap + commons library
# Sourced by login shells via profile.d. Safe to skip if files missing.

# 0. Host bash needs $PREFIX/lib (libandroid-support, libreadline). The proot
#    wrapper uses libexec/proot-libs only; if that leaks into this shell, heal.
if [ -n "${PREFIX:-}" ] && [ -d "$PREFIX/lib" ]; then
  case ":${LD_LIBRARY_PATH:-}:" in
    *":$PREFIX/lib:"*|*"$PREFIX/lib:"*) ;;
    *)
      export LD_LIBRARY_PATH="$PREFIX/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
      ;;
  esac
fi

# 1. Bootstrap: provides the `import` function for modular sourcing
if [ -f "$PREFIX/lib/invapp/bootstrap.sh" ]; then
  # shellcheck source=/dev/null
  . "$PREFIX/lib/invapp/bootstrap.sh"
fi

# 2. Commons library: colors, logging, tables, spinners, version detection
if [ -f "$PREFIX/lib/invapp/commons.sh" ]; then
  # shellcheck source=/dev/null
  . "$PREFIX/lib/invapp/commons.sh"
fi
