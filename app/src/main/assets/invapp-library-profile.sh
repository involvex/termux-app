# invapp-library — auto-source bootstrap + commons library
# Sourced by login shells via profile.d. Safe to skip if files missing.

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
