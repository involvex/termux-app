#!/data/data/com.termux/files/usr/bin/bash
# commons.sh — shared shell utility library
# Ported from core-termux utils/colors.sh + utils/log.sh + utils/version.sh
# Version: 1.0.0
# Auto-sourced via etc/profile.d/invapp-library.sh

# Guard against double-source
[[ -n "${__INVAPP_COMMONS_LOADED:-}" ]] && return
__INVAPP_COMMONS_LOADED=1

# ===== PATHS =====
INVAPP_LIB_DIR="${INVAPP_LIB_DIR:-$PREFIX/lib/invapp}"
INVAPP_CACHE_DIR="${INVAPP_CACHE_DIR:-$PREFIX/lib/invapp}"
mkdir -p "$INVAPP_CACHE_DIR" 2>/dev/null || true

# ===== COLOR DEFINITIONS (light) =====
BLACK=$'\e[1;30m'
GRAY=$'\e[0;90m'
BLUE=$'\e[1;34m'
GREEN=$'\e[1;32m'
CYAN=$'\e[1;36m'
RED=$'\e[1;31m'
PURPLE=$'\e[1;35m'
YELLOW=$'\e[1;33m'
NC=$'\e[1;37m'

# dark colors
D_BLACK=$'\e[0;30m'
D_BLUE=$'\e[0;34m'
D_GREEN=$'\e[0;32m'
D_CYAN=$'\e[0;36m'
D_RED=$'\e[0;31m'
D_PURPLE=$'\e[0;35m'
D_YELLOW=$'\e[0;33m'
D_NC=$'\e[0;37m'

# ===== LOGGING FUNCTIONS =====
log_info()    { echo -e "    ${CYAN}➜${D_CYAN} $*${D_NC}"; }
log_success() { echo -e "    ${GREEN}✔${D_GREEN} $*${D_NC}"; }
log_warn()    { echo -e "    ${YELLOW}⚠${D_YELLOW} $*${D_NC}"; }
log_error()   { echo -e "    ${RED}✖${D_RED} $*${D_NC}" >&2; }
log_debug()   { [[ "${INVAPP_DEBUG:-0}" == "1" ]] && echo -e "    ${PURPLE}⚙${D_PURPLE} [DEBUG] $*${D_NC}"; true; }
list_item()   { echo -e "    ${GRAY}•${D_NC} $*"; }

# list_item_check "done" "text" — colored status bullet
list_item_check() {
  local status="$1" text="$2"
  case "$status" in
    "done"|"success") echo -e "    ${GREEN}✔${D_NC} $text" ;;
    "pending")         echo -e "    ${YELLOW}⏳${D_NC} $text" ;;
    "error"|"fail")    echo -e "    ${RED}✖${D_NC} $text" ;;
    *)                 echo -e "    ${GRAY}•${D_NC} $text" ;;
  esac
}

# ===== SEPARATOR FUNCTIONS =====
separator() {
  local cols=$(tput cols 2>/dev/null || echo 80)
  local line=$(printf "%${cols}s")
  echo -e "${GRAY}${line// /─}${NC}"
}

separator_double() {
  local cols=$(tput cols 2>/dev/null || echo 80)
  local line=$(printf "%${cols}s")
  echo -e "${GRAY}${line// /═}${NC}"
  echo -e "${GRAY}${line// /═}${NC}"
}

separator_section() {
  local title="$1"
  local cols=$(tput cols 2>/dev/null || echo 80)
  local padding=$(( (cols - ${#title} - 2) / 2 ))
  local line=$(printf "%${padding}s")
  echo -e "${GRAY}${line// /─} ${D_CYAN}${title}${GRAY} ${line// /─}${NC}"
}

# ===== CENTER TEXT =====
center_text() {
  local cols=$(tput cols 2>/dev/null || echo 80)
  local text="$1"
  local clean_text=$(echo -e "$text" | sed $'s/\x1b\[[0-9;]*m//g')
  local clean_len=${#clean_text}
  local padding=$(( (cols - clean_len) / 2 ))
  printf "%${padding}s" ""
  echo -e "$text"
}

# ===== BOX FUNCTIONS =====
box() {
  local text="$1"
  local len=${#text}
  local line=$(printf "%$((len + 2))s")
  echo -e "${GRAY}╭${line// /─}╮${NC}"
  echo -e "${GRAY}│${D_CYAN} $text ${GRAY}│${NC}"
  echo -e "${GRAY}╰${line// /─}╯${NC}"
}

box_large() {
  local text="$1"
  local len=${#text}
  local line=$(printf "%$((len + 4))s")
  echo -e "${GRAY}╔${line// /═}╗${NC}"
  echo -e "${GRAY}║${D_CYAN}  $text  ${GRAY}║${NC}"
  echo -e "${GRAY}╚${line// /═}╝${NC}"
}

# ===== TABLE FUNCTIONS =====
# Usage: table_start "Header1" "Header2" "Status"
#        table_row "val1" "val2" "installed"
#        table_end
_table_headers=()
_table_widths=()
_table_total=0

table_start() {
  _table_headers=()
  _table_widths=()
  local cols=$(tput cols 2>/dev/null || echo 80)
  local total_width=0
  local hdr
  for hdr in "$@"; do
    _table_headers+=("$hdr")
    local w=${#hdr}
    [[ $w -lt 12 ]] && w=12
    _table_widths+=($w)
    total_width=$((total_width + w + 3))
  done
  _table_total=$((total_width + 1))
  local sep=$(printf "%${_table_total}s")
  echo -e "${GRAY}${sep// /─}${NC}"
  local line=""
  for i in "${!_table_headers[@]}"; do
    line+="${GRAY}│${D_CYAN} $(_pad_right "${_table_headers[$i]}" ${_table_widths[$i]}) ${GRAY}"
  done
  echo -e "${line}${GRAY}│${NC}"
  local dsep=$(printf "%${_table_total}s")
  echo -e "${GRAY}${dsep// /─}${NC}"
}

_pad_right() {
  local text="$1" width="$2"
  printf "%-${width}s" "$text"
}

table_row() {
  local line=""
  local vals=("$@")
  local status="${vals[-1]}"
  unset 'vals[-1]'
  for i in "${!vals[@]}"; do
    line+="${GRAY}│${D_NC} $(_pad_right "${vals[$i]}" ${_table_widths[$i]}) ${GRAY}"
  done
  line+="${GRAY}│${D_GREEN} $(_pad_right "$status" ${_table_widths[-1]}) ${GRAY}│${NC}"
  echo -e "$line"
}

table_end() {
  local sep=$(printf "%${_table_total}s")
  echo -e "${GRAY}${sep// /─}${NC}"
}

# ===== LOADING SPINNER =====
# Usage: loading "Message" command args...
loading() {
  local message="$1"; shift
  local frames=('⠋' '⠙' '⠹' '⠸' '⠼' '⠴' '⠦' '⠧' '⠇' '⠏')
  local delay=0.08
  local tmpfile; tmpfile="$(mktemp)"
  printf "    ${CYAN}%s${D_CYAN} %s${NC}" "${frames[0]}" "$message"
  "$@" >"$tmpfile" 2>&1 &
  local pid=$!
  local i=0
  while kill -0 "$pid" 2>/dev/null; do
    printf "\r    ${CYAN}%s${D_CYAN} %s${NC}" "${frames[i]}" "$message"
    ((i = (i + 1) % ${#frames[@]}))
    sleep $delay
  done
  wait "$pid"
  local exit_code=$?
  if [[ $exit_code -eq 0 ]]; then
    printf "\r    ${GREEN}✔${D_GREEN} %s${NC}\n" "$message"
    [[ -s "$tmpfile" ]] && cat "$tmpfile"
  elif [[ $exit_code -eq 2 ]]; then
    printf "\r    ${CYAN}➜${D_CYAN} %s${NC}\n" "$message"
    [[ -s "$tmpfile" ]] && cat "$tmpfile"
  else
    printf "\r    ${RED}✖${D_RED} %s${NC}\n" "$message"
    cat "$tmpfile"
  fi
  rm -f "$tmpfile"
  return $exit_code
}

# ===== CONFIRMATION PROMPT =====
# Usage: read_confirm_default "Continue?" "y" VAR_NAME
# default="y" → [Y/n] | default="n" → [y/N]
# Returns 0 if yes, 1 if no. VAR_NAME receives "y" or "n".
read_confirm_default() {
  local prompt="$1" default="$2" var="$3" _val
  local show_default
  if [[ "$default" == "y" ]]; then
    show_default="${D_GREEN}Y${GRAY}/${D_RED}n${GRAY}"
  else
    show_default="${D_GREEN}y${GRAY}/${D_RED}N${GRAY}"
  fi
  while true; do
    echo -e "    ${GRAY}┌─${D_YELLOW} ${prompt} ${GRAY}[${show_default}${GRAY}]${D_NC}" >&2
    echo -e -n "    ${GRAY}└─${D_YELLOW}▶ ${D_NC}" >&2
    read -rn1 _val; echo >&2
    if [[ -z "$_val" ]]; then _val="$default"; fi
    case "${_val,,}" in
      y) read -r "$var" <<<"y"; return 0 ;;
      n) read -r "$var" <<<"n"; return 1 ;;
      *) echo -e "    ${RED}✖${D_NC} Reply ${D_GREEN}y${D_NC} or ${D_RED}n${D_NC}" >&2 ;;
    esac
  done
}

# ===== INTERACTIVE SELECTION MENU =====
# Usage: read_select "Prompt" VAR_NAME "Option1" "Option2" ...
read_select() {
  local prompt="$1" var="$2"; shift 2
  local -a options=("$@")
  local selected=0 total=${#options[@]}
  local cols; cols=$(tput cols 2>/dev/null || echo 80)
  local margin=6 max_width=$((cols - margin))
  _render_select() {
    echo -e "    ${GRAY}┌─${D_CYAN} ${prompt}${NC}" >&2
    for ((i = 0; i < total; i++)); do
      local text="${options[$i]}"
      if (( ${#text} > max_width )); then text="${text:0:$((max_width - 3))}..."; fi
      if ((i == selected)); then
        echo -e "    ${GRAY}│  ${D_CYAN}▶ ${WHITE}${text}${D_NC}" >&2
      else
        echo -e "    ${GRAY}│    ${GRAY}${text}${D_NC}" >&2
      fi
    done
    echo -e "    ${GRAY}└─ ${GRAY}↑↓ move  Enter confirm${D_NC}" >&2
  }
  local lines=$((total + 1))
  tput civis 2>/dev/null
  _render_select
  local key
  while read -rsn3 key 2>/dev/null; do
    case "$key" in
      $'\e[A'|$'\eOA') ((selected = (selected - 1 + total) % total)) ;;
      $'\e[B'|$'\eOB') ((selected = (selected + 1) % total)) ;;
      '') ((selected = (selected + 1) % total)) ;;
      *) continue ;;
    esac
    tput cuu "$lines" 2>/dev/null
    _render_select
  done
  read -r "$var" <<<"${options[$selected]}"
  tput cnormal 2>/dev/null
  return 0
}

# ===== VERSION DETECTION =====
_parse_version() {
  local output="$1" ver
  ver=$(echo "$output" | grep -oE '[0-9]+\.[0-9]+(\.[0-9]+)?' | head -1)
  if [ -z "$ver" ]; then echo ""; return 1; fi
  local parts; IFS='.' read -ra parts <<< "$ver"
  if [ ${#parts[@]} -eq 2 ]; then echo "${parts[0]}.${parts[1]}.0"; else echo "$ver"; fi
}

_get_installed_version() {
  local binary="$1" flag="${2:---version}" display="${3:-$binary}" output
  if ! command -v "$binary" &>/dev/null; then echo ""; return 1; fi
  output=$("$binary" "$flag" 2>&1)
  _parse_version "$output"
}

_compare_versions() {
  local v1="$1" v2="$2"
  if [ -z "$v1" ] || [ -z "$v2" ]; then return 2; fi
  local strip_v1="${v1#v}" strip_v2="${v2#v}"
  if [ "$strip_v1" = "$strip_v2" ]; then return 0; fi
  local highest; highest=$(printf '%s\n%s\n' "$strip_v1" "$strip_v2" | sort -V | tail -1)
  [ "$highest" = "$strip_v1" ]
}

# _check_update_needed "Tool Name" installed_ver remote_ver update_func
_check_update_needed() {
  local display_name="$1" installed_ver="$2" remote_ver="$3" update_func="$4"
  if [ -z "$installed_ver" ]; then
    log_warn "$display_name: could not detect installed version"
    echo
    local confirm_var
    read_confirm_default "Update $display_name anyway?" "y" confirm_var
    if [ "$confirm_var" = "y" ]; then $update_func; return $? ; fi
    log_info "Skipped $display_name"; return 0
  fi
  if [ -z "$remote_ver" ]; then
    log_warn "$display_name: could not detect remote version"
    echo
    local confirm_var
    read_confirm_default "Update $display_name anyway?" "y" confirm_var
    if [ "$confirm_var" = "y" ]; then $update_func; return $? ; fi
    log_info "Skipped $display_name"; return 0
  fi
  if _compare_versions "$installed_ver" "$remote_ver"; then
    local display_ver="$installed_ver"
    [[ "$display_ver" != v* ]] && display_ver="v$display_ver"
    log_success "$display_name is up to date ($display_ver)"
    return 2
  fi
  local display_ver="$installed_ver" remote_display="$remote_ver"
  [[ "$display_ver" != v* ]] && display_ver="v$display_ver"
  [[ "$remote_display" != v* ]] && remote_display="v$remote_display"
  log_info "$display_name: ${D_GREEN}${display_ver}${D_NC} → ${D_CYAN}${remote_display}${D_NC}"
  local confirm_var
  read_confirm_default "Update to $remote_ver?" "y" confirm_var
  if [ "$confirm_var" = "y" ]; then $update_func; return $? ; fi
  log_info "Skipped $display_name"; return 0
}

# ===== STATUS CHECKERS =====
_check_cmd() {
  if command -v "$1" &>/dev/null; then echo "installed"; else echo "not-installed"; fi
}

_check_pkg() {
  if dpkg -s "$1" &>/dev/null 2>&1 || command -v "$1" &>/dev/null; then echo "installed"; else echo "not-installed"; fi
}

# ===== TIP HELPER =====
log_tip() { echo -e " ${D_CYAN}● Tip${NC} $*"; }

# ===== ENVIRONMENT HELPERS =====
_env_rc_file() {
  if [[ -f "$HOME/.zshrc" ]]; then echo "$HOME/.zshrc"
  else echo "$HOME/.bashrc"; fi
}

_env_valid_key() {
  local key="$1"
  if [[ -z "$key" ]]; then log_error "Variable name cannot be empty"; return 1; fi
  if [[ ! "$key" =~ ^[a-zA-Z_][a-zA-Z0-9_]*$ ]]; then
    log_error "Invalid variable name: $key"
    list_item "Must start with a letter or underscore, followed by letters, digits, or underscores"
    return 1
  fi
}
