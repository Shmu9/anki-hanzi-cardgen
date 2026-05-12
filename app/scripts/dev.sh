#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ROOT_DIR="$(cd "$APP_DIR/.." && pwd)"
BACKEND_DIR="$APP_DIR/backend"
FRONTEND_DIR="$APP_DIR/frontend"
DEV_DIR="$APP_DIR/.dev"

POSTGRES_HOST="${POSTGRES_HOST:-127.0.0.1}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
APP_DB_NAME="${APP_DB_NAME:-hanzi_cardgen}"
BACKEND_PORT="${BACKEND_PORT:-8766}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"
BACKEND_JAR="$BACKEND_DIR/target/hanzi-cardgen-backend-1.0.0.jar"

backend_pid=""
frontend_pid=""
started_postgres=0

log() {
  printf '[dev] %s\n' "$*"
}

cleanup() {
  if [[ -n "$frontend_pid" ]] && kill -0 "$frontend_pid" 2>/dev/null; then
    log "stopping frontend pid $frontend_pid"
    kill "$frontend_pid" 2>/dev/null || true
  fi
  if [[ -n "$backend_pid" ]] && kill -0 "$backend_pid" 2>/dev/null; then
    log "stopping backend pid $backend_pid"
    kill "$backend_pid" 2>/dev/null || true
  fi
  if [[ "$started_postgres" == "1" ]]; then
    local data_dir
    data_dir="$(postgres_data_dir)"
    if [[ -n "$data_dir" ]]; then
      log "stopping PostgreSQL started by this script"
      pg_ctl -D "$data_dir" stop -m fast >/dev/null || true
    fi
  fi
}

trap cleanup EXIT INT TERM

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    printf 'Missing required command: %s\n' "$1" >&2
    exit 1
  fi
}

postgres_data_dir() {
  if [[ -n "${POSTGRES_DATA_DIR:-}" && -d "$POSTGRES_DATA_DIR" ]]; then
    printf '%s\n' "$POSTGRES_DATA_DIR"
    return
  fi

  local candidates=(
    "/opt/homebrew/var/postgresql@15"
    "/opt/homebrew/var/postgresql"
    "/usr/local/var/postgresql@15"
    "/usr/local/var/postgres"
    "/usr/local/var/postgresql"
  )
  local candidate
  for candidate in "${candidates[@]}"; do
    if [[ -d "$candidate" ]]; then
      printf '%s\n' "$candidate"
      return
    fi
  done
}

postgres_ready() {
  pg_isready -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" -d postgres >/dev/null 2>&1
}

start_postgres_if_needed() {
  require_command pg_isready
  require_command psql
  require_command createdb

  if postgres_ready; then
    log "PostgreSQL is already accepting connections on $POSTGRES_HOST:$POSTGRES_PORT"
    return
  fi

  require_command pg_ctl
  local data_dir
  data_dir="$(postgres_data_dir)"
  if [[ -z "$data_dir" ]]; then
    printf 'PostgreSQL is not reachable and no data directory was found.\n' >&2
    printf 'Set POSTGRES_DATA_DIR or start PostgreSQL manually, then rerun this script.\n' >&2
    exit 1
  fi

  mkdir -p "$DEV_DIR"
  log "starting PostgreSQL from $data_dir"
  pg_ctl -D "$data_dir" -l "$DEV_DIR/postgres.log" start
  started_postgres=1

  local attempts=0
  until postgres_ready; do
    attempts=$((attempts + 1))
    if [[ "$attempts" -gt 20 ]]; then
      printf 'PostgreSQL did not become ready. See %s/postgres.log\n' "$DEV_DIR" >&2
      exit 1
    fi
    sleep 0.5
  done
}

ensure_database() {
  if psql -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" -d postgres -tAc "select 1 from pg_database where datname = '$APP_DB_NAME'" | grep -q 1; then
    log "database $APP_DB_NAME already exists"
    return
  fi

  log "creating database $APP_DB_NAME"
  createdb -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" "$APP_DB_NAME"
}

build_backend() {
  require_command mvn
  log "building backend"
  (cd "$BACKEND_DIR" && mvn -q -DskipTests package)
}

ensure_frontend_deps() {
  require_command npm
  if [[ ! -d "$FRONTEND_DIR/node_modules" ]]; then
    log "installing frontend dependencies"
    (cd "$FRONTEND_DIR" && npm install)
  fi
}

start_backend() {
  require_command java
  export HANZI_APP_DB_URL="${HANZI_APP_DB_URL:-jdbc:postgresql://$POSTGRES_HOST:$POSTGRES_PORT/$APP_DB_NAME}"
  export HANZI_APP_DB_USER="${HANZI_APP_DB_USER:-${PGUSER:-$USER}}"
  export HANZI_APP_DB_PASSWORD="${HANZI_APP_DB_PASSWORD:-${PGPASSWORD:-}}"

  log "starting backend on http://127.0.0.1:$BACKEND_PORT"
  (
    cd "$BACKEND_DIR"
    java -jar "$BACKEND_JAR" --port "$BACKEND_PORT"
  ) &
  backend_pid=$!
}

start_frontend() {
  log "starting frontend on http://127.0.0.1:$FRONTEND_PORT"
  (
    cd "$FRONTEND_DIR"
    export VITE_API_BASE_URL="${VITE_API_BASE_URL:-http://127.0.0.1:$BACKEND_PORT}"
    npm run dev -- --port "$FRONTEND_PORT"
  ) &
  frontend_pid=$!
}

main() {
  if [[ ! -f "$ROOT_DIR/dictionary/dict.sqlite3" ]]; then
    printf 'Missing dictionary/dict.sqlite3. Build the dictionary before starting the app.\n' >&2
    exit 1
  fi

  start_postgres_if_needed
  ensure_database
  build_backend
  ensure_frontend_deps
  start_backend
  start_frontend

  log "ready"
  log "frontend: http://127.0.0.1:$FRONTEND_PORT"
  log "backend:  http://127.0.0.1:$BACKEND_PORT"
  log "database: $APP_DB_NAME on $POSTGRES_HOST:$POSTGRES_PORT"
  log "press Ctrl-C to stop"

  wait "$backend_pid" "$frontend_pid"
}

main "$@"
