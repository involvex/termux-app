#!/data/data/com.termux/files/usr/bin/bash
# invapp bootstrap — modular import system
# Ported from core-termux utils/bootstrap.sh, adapted for InVxTermux.
# Provides: import "@/commons" -> $INVAPP_LIB_DIR/commons.sh

# Avoid re-declaration on double-source
[[ -n "${__INVAPP_BOOTSTRAP_LOADED:-}" ]] && return
__INVAPP_BOOTSTRAP_LOADED=1

# Registry of already-imported modules (dedup)
declare -A __INVAPP_IMPORTED

# INVAPP_LIB_DIR resolution:
#   1. Override via $INVAPP_LIB_DIR if already set
#   2. Default to $PREFIX/lib/invapp
if [[ -z "${INVAPP_LIB_DIR:-}" ]]; then
  if [[ -n "${PREFIX:-}" ]]; then
    INVAPP_LIB_DIR="$PREFIX/lib/invapp"
  else
    _this_dir="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" 2>/dev/null && pwd)"
    case "$_this_dir" in
      */lib/invapp) INVAPP_LIB_DIR="$_this_dir" ;;
      *) INVAPP_LIB_DIR="/data/data/com.involvex.termux_app/files/usr/lib/invapp" ;;
    esac
    unset _this_dir
  fi
fi
export INVAPP_LIB_DIR

# import "@/path/to/module" -> "$INVAPP_LIB_DIR/path/to/module.sh"
# Replaces leading @ with $INVAPP_LIB_DIR, appends .sh, dedup guard.
import() {
  local path="${1//@/$INVAPP_LIB_DIR}"
  if [[ "$path" != *.sh ]]; then
    path="${path}.sh"
  fi
  if [[ -n "${__INVAPP_IMPORTED[$path]+x}" ]]; then
    return 0
  fi
  if [[ ! -f "$path" ]]; then
    echo "invapp: import error: $path not found" >&2
    exit 1
  fi
  __INVAPP_IMPORTED[$path]=1
  source "$path"
}
