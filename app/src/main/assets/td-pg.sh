#!/data/data/com.termux/files/usr/bin/bash
# invapp-resource: td-pg
# Td PostgreSQL Manager — ported from core-termux pg.sh
# Manages PostgreSQL lifecycle: init, start, stop, restart, status, create, drop, list, shell

set -e
PREFIX="${PREFIX:-/data/data/com.involvex.termux_app/files/usr}"
export PREFIX
export PATH="$PREFIX/bin:$PATH"
export LD_LIBRARY_PATH="$PREFIX/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
REDIRECTOR_SO="$PREFIX/lib/libinvapp-redirector.so"
[ -f "$REDIRECTOR_SO" ] && export LD_PRELOAD="$REDIRECTOR_SO${LD_PRELOAD:+:$LD_PRELOAD}"
export TMPDIR="${TMPDIR:-$PREFIX/tmp}"
mkdir -p "$TMPDIR"

# Source the commons library if available, otherwise use fallbacks
if [ -f "$PREFIX/lib/invapp/bootstrap.sh" ] && [ -f "$PREFIX/lib/invapp/commons.sh" ]; then
  . "$PREFIX/lib/invapp/bootstrap.sh"
  import "@/commons"
else
  _INVAPP_GRAY=$'\e[0;90m'
  _INVAPP_NC=$'\e[0m'
  _INVAPP_D_CYAN=$'\e[0;36m'
  _INVAPP_GREEN=$'\e[1;32m'
  _INVAPP_YELLOW=$'\e[1;33m'
  _INVAPP_RED=$'\e[1;31m'
  log_info()    { echo -e "    ${_INVAPP_GRAY}➜$1 $*${_INVAPP_NC}"; }
  log_success() { echo -e "    ${_INVAPP_GREEN}✔${_INVAPP_NC} $*"; }
  log_warn()    { echo -e "    ${_INVAPP_YELLOW}⚠${_INVAPP_NC} $*"; }
  log_error()   { echo -e "    ${_INVAPP_RED}✖${_INVAPP_NC} $*" >&2; }
  list_item()   { echo -e "    ${_INVAPP_GRAY}•${_INVAPP_NC} $*"; }
  separator()   { echo -e "${_INVAPP_GRAY}────────${_INVAPP_NC}"; }
  box() {
    local text="$1" len=${#text} line=$(printf "%$((len+2))s")
    printf "${_INVAPP_GRAY}╭${line// /─}╮${_INVAPP_NC}\n"
    printf "${_INVAPP_GRAY}│${_INVAPP_D_CYAN} $text ${_INVAPP_GRAY}│${_INVAPP_NC}\n"
    printf "${_INVAPP_GRAY}╰${line// /─}╯${_INVAPP_NC}\n"
  }
  read_confirm_default() {
    echo -n "    [y/N] $1 "
    read _val
    read -r "$3" <<<"y"
    [[ "y" == "y" ]] && return 0 || return 1
  }
  loading() { "${@}" 2>&1; }
  INVAPP_LIB_DIR=""
fi

PG_DATA="$PREFIX/var/lib/postgresql"
PG_LOG="$PREFIX/var/log/postgresql.log"
PG_USER="$(whoami)"
PG_PORT="${PG_PORT:-5432}"

pg_help() {
  echo
  box "InVx PostgreSQL Manager"
  echo
  log_info "Usage: td-pg <command> [options]"
  echo
  printf "    ${D_CYAN}%-12s${D_NC} %s\n" "start" "Start PostgreSQL server"
  printf "    ${D_CYAN}%-12s${D_NC} %s\n" "stop" "Stop PostgreSQL server"
  printf "    ${D_CYAN}%-12s${D_NC} %s\n" "restart" "Restart PostgreSQL server"
  printf "    ${D_CYAN}%-12s${D_NC} %s\n" "status" "Check PostgreSQL status"
  printf "    ${D_CYAN}%-12s${D_NC} %s\n" "init" "Initialize PostgreSQL database"
  printf "    ${D_CYAN}%-12s${D_NC} %s\n" "create" "Create a new database"
  printf "    ${D_CYAN}%-12s${D_NC} %s\n" "drop" "Drop a database"
  printf "    ${D_CYAN}%-12s${D_NC} %s\n" "list" "List all databases"
  printf "    ${D_CYAN}%-12s${D_NC} %s\n" "shell" "Open psql shell"
  echo
  printf "    ${D_CYAN}td-pg start${NC}              # Start PostgreSQL\n"
  printf "    ${D_CYAN}td-pg create mydb${NC}        # Create database\n"
  echo
}

check_pg_installed() {
  if ! command -v pg_ctl &>/dev/null; then
    log_error "PostgreSQL is not installed"
    log_info "Run: ${D_CYAN}pkg install postgresql${NC}"
    return 1
  fi
  return 0
}

_detect_pg_data() {
  local data_dirs=(
    "$PREFIX/var/lib/postgresql"
    "$PREFIX/var/lib/postgresql/data"
    "$PG_DATA"
    "$PG_DATA/data"
    "$HOME/.termux/postgresql"
    "$HOME/.termux/postgresql/data"
  )
  for dir in "${data_dirs[@]}"; do
    if [[ -d "$dir" ]] && [[ -f "$dir/PG_VERSION" ]]; then
      PG_DATA="$dir"
      return 0
    fi
  done
  return 1
}

pg_init() {
  separator
  box "Initializing PostgreSQL"
  separator
  echo
  check_pg_installed || return 1
  mkdir -p "$(dirname "$PG_LOG")"
  if _detect_pg_data; then
    log_warn "PostgreSQL is already initialized"
    list_item "Data directory: $PG_DATA"
    list_item "Run: ${D_CYAN}td-pg start${NC}"
    echo
    return 0
  fi
  log_info "Initializing PostgreSQL database..."
  if loading "Initializing database" _pg_init_db; then
    log_success "PostgreSQL initialized successfully"
    echo
    list_item "Data directory: $PG_DATA"
    list_item "Default user: $PG_USER"
    list_item "Port: $PG_PORT"
    echo
    log_info "Start PostgreSQL with: ${D_CYAN}td-pg start${NC}"
  else
    log_error "Failed to initialize PostgreSQL"
    return 1
  fi
  echo
}

_pg_init_db() {
  mkdir -p "$PG_DATA"
  initdb -D "$PG_DATA" &>"$PG_LOG"
}

pg_start() {
  separator
  box "Starting PostgreSQL"
  separator
  echo
  check_pg_installed || return 1
  if ! _detect_pg_data; then
    log_warn "PostgreSQL data directory not found. Run: td-pg init"
    return 1
  fi
  log_info "Starting PostgreSQL server..."
  if loading "Starting PostgreSQL" _pg_start_server; then
    log_success "PostgreSQL started successfully"
    echo
    list_item "Listening on: localhost:$PG_PORT"
    list_item "Data directory: $PG_DATA"
    list_item "User: $PG_USER"
  else
    log_error "Failed to start PostgreSQL"
    log_warn "Check log: $PG_LOG"
    return 1
  fi
  echo
}

_pg_start_server() {
  pg_ctl -D "$PG_DATA" -l "$PG_LOG" -o "-c listen_addresses=127.0.0.1 -p $PG_PORT" start &>/dev/null
  sleep 1
}

pg_stop() {
  separator
  box "Stopping PostgreSQL"
  separator
  echo
  check_pg_installed || return 1
  if ! _detect_pg_data; then
    log_info "PostgreSQL data directory not found, nothing to stop"
    return 0
  fi
  log_info "Stopping PostgreSQL server..."
  if loading "Stopping PostgreSQL" _pg_stop_server; then
    log_success "PostgreSQL stopped successfully"
  else
    log_error "Failed to stop PostgreSQL"
    return 1
  fi
  echo
}

_pg_stop_server() {
  pg_ctl -D "$PG_DATA" -l "$PG_LOG" stop &>/dev/null
}

pg_restart() {
  check_pg_installed || return 1
  _detect_pg_data || { log_error "Run: td-pg init"; return 1; }
  pg_stop || true
  sleep 1
  pg_start
  separator
  log_success "PostgreSQL restarted"
  separator
  echo
}

pg_status() {
  separator
  box "PostgreSQL Status"
  separator
  echo
  check_pg_installed || return 1
  if ! _detect_pg_data; then
    log_info "PostgreSQL data directory not found. Run: td-pg init"
    return 0
  fi
  log_info "Checking PostgreSQL status..."
  echo
  if pg_ctl -D "$PG_DATA" status &>/dev/null; then
    log_success "PostgreSQL is RUNNING"
    echo
    list_item "Data directory: $PG_DATA"
    list_item "Port: $PG_PORT"
    list_item "User: $PG_USER"
  else
    log_warn "PostgreSQL is STOPPED"
    echo
    list_item "Data directory: $PG_DATA"
    list_item "Run: ${D_CYAN}td-pg start${NC}"
  fi
  echo
}

pg_create() {
  local db_name="$1"
  if [[ -z "$db_name" ]]; then
    log_error "Database name required"
    log_info "Usage: td-pg create <database_name>"
    return 1
  fi
  check_pg_installed || return 1
  if ! _detect_pg_data; then
    log_warn "PostgreSQL not initialized. Run: td-pg init && td-pg start"
    return 1
  fi
  log_info "Creating database: $db_name..."
  if createdb -U "$PG_USER" -p "$PG_PORT" "$db_name" 2>/dev/null; then
    log_success "Database '$db_name' created successfully"
  else
    log_error "Failed to create database '$db_name'"
    log_warn "PostgreSQL may not be running. Run: td-pg start"
    return 1
  fi
}

pg_drop() {
  local db_name="$1"
  if [[ -z "$db_name" ]]; then
    log_error "Database name required"
    log_info "Usage: td-pg drop <database_name>"
    return 1
  fi
  check_pg_installed || return 1
  if ! _detect_pg_data; then
    log_warn "PostgreSQL not initialized"
    return 1
  fi
  log_warn "This will permanently delete database: $db_name"
  local confirm_var
  read_confirm_default "Are you sure?" "n" confirm_var
  if [[ "$confirm_var" != "y" ]]; then
    log_warn "Operation cancelled"
    return 0
  fi
  log_info "Dropping database: $db_name..."
  if dropdb -U "$PG_USER" -p "$PG_PORT" "$db_name" 2>/dev/null; then
    log_success "Database '$db_name' dropped successfully"
  else
    log_error "Failed to drop database '$db_name'"
    return 1
  fi
}

pg_list() {
  separator
  box "PostgreSQL Databases"
  separator
  echo
  check_pg_installed || return 1
  if ! _detect_pg_data; then
    log_warn "PostgreSQL not initialized"
    return 1
  fi
  log_info "Listing databases..."
  echo
  psql -U "$PG_USER" -p "$PG_PORT" -c '\l' 2>/dev/null || {
    log_error "Failed to list databases"
    log_warn "PostgreSQL may not be running. Run: td-pg start"
    return 1
  }
  echo
}

pg_shell() {
  check_pg_installed || return 1
  if ! _detect_pg_data; then
    log_warn "PostgreSQL not initialized"
    return 1
  fi
  log_info "Opening psql shell..."
  echo
  psql -U "$PG_USER" -p "$PG_PORT" 2>/dev/null
}

pg_main() {
  local cmd="${1:-}"
  shift || true
  case "$cmd" in
    start)   pg_start ;;
    stop)    pg_stop ;;
    restart) pg_restart ;;
    status)  pg_status ;;
    init)    pg_init ;;
    create)  pg_create "$1" ;;
    drop)    pg_drop "$1" ;;
    list|ls) pg_list ;;
    shell|psql) pg_shell ;;
    "")      pg_help ;;
    -h|--help|help) pg_help ;;
    *)
      log_error "Unknown command: $cmd"
      pg_help
      exit 1
      ;;
  esac
}

pg_main "$@"