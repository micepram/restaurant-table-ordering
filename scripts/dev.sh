#!/usr/bin/env bash
#
# Brings up the whole backend: infrastructure containers, then every service jar.
#
#   ./scripts/dev.sh            start infra + all services
#   ./scripts/dev.sh stop       stop the services (leaves containers running)
#   ./scripts/dev.sh down       stop the services and the containers
#   ./scripts/dev.sh status     show what is listening
#   ./scripts/dev.sh logs menu  tail one service's log
#
# Logs go to logs/<service>.log; PIDs to logs/<service>.pid.

source "$(dirname "${BASH_SOURCE[0]}")/env.sh"

LOG_DIR="$REPO_ROOT/logs"
mkdir -p "$LOG_DIR"

# service:port, in dependency order. menu before order because order-service calls it.
SERVICES=(
  "table-service:8084"
  "menu-service:8081"
  "order-service:8082"
  "kitchen-service:8083"
  "payment-service:8085"
  "notification-service:8086"
  "gateway:8080"
)

wait_for_infra() {
  echo "==> waiting for containers to report healthy"
  for _ in $(seq 1 60); do
    local unhealthy
    unhealthy=$(docker compose ps --format '{{.Service}} {{.Status}}' \
      | grep -cv 'healthy' || true)
    if [[ "$unhealthy" -eq 0 ]]; then
      echo "    all healthy"
      return 0
    fi
    sleep 2
  done
  echo "    warning: containers did not all report healthy; continuing anyway" >&2
}

start_infra() {
  if ! docker info >/dev/null 2>&1; then
    echo "==> starting Docker Desktop"
    open -a Docker
    for _ in $(seq 1 60); do
      docker info >/dev/null 2>&1 && break
      sleep 5
    done
  fi
  echo "==> starting kafka, postgres, redis"
  (cd "$REPO_ROOT" && docker compose up -d)
  wait_for_infra
}

start_services() {
  for entry in "${SERVICES[@]}"; do
    local name="${entry%%:*}"
    local port="${entry##*:}"
    local jar="$REPO_ROOT/$name/target/$name-0.1.0-SNAPSHOT.jar"

    if [[ ! -f "$jar" ]]; then
      echo "    skipping $name (not built yet)"
      continue
    fi
    if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
      echo "    $name already listening on $port"
      continue
    fi

    echo "==> starting $name on $port"
    nohup "$JAVA_HOME/bin/java" -jar "$jar" > "$LOG_DIR/$name.log" 2>&1 &
    echo $! > "$LOG_DIR/$name.pid"
  done
}

wait_for_services() {
  echo "==> waiting for services to accept connections"
  for entry in "${SERVICES[@]}"; do
    local name="${entry%%:*}"
    local port="${entry##*:}"
    [[ -f "$REPO_ROOT/$name/target/$name-0.1.0-SNAPSHOT.jar" ]] || continue
    for _ in $(seq 1 45); do
      if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
        echo "    $name up on $port"
        break
      fi
      sleep 1
    done
  done
}

stop_services() {
  echo "==> stopping services"
  for entry in "${SERVICES[@]}"; do
    local name="${entry%%:*}"
    local pidfile="$LOG_DIR/$name.pid"
    if [[ -f "$pidfile" ]] && kill -0 "$(cat "$pidfile")" 2>/dev/null; then
      kill "$(cat "$pidfile")" && echo "    stopped $name"
    fi
    rm -f "$pidfile"
  done
  # Catch anything started outside this script.
  pkill -f 'restaurant-table-ordering/.*/target/.*-0.1.0-SNAPSHOT.jar' 2>/dev/null || true
}

case "${1:-start}" in
  start)
    start_infra
    start_services
    wait_for_services
    echo
    echo "Backend ready. Frontends: cd frontend && npm install && npm run dev"
    ;;
  stop)
    stop_services
    ;;
  down)
    stop_services
    (cd "$REPO_ROOT" && docker compose down)
    ;;
  status)
    (cd "$REPO_ROOT" && docker compose ps --format 'table {{.Service}}\t{{.Status}}')
    echo
    for entry in "${SERVICES[@]}"; do
      name="${entry%%:*}"; port="${entry##*:}"
      if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
        printf '  %-22s listening on %s\n' "$name" "$port"
      else
        printf '  %-22s down\n' "$name"
      fi
    done
    ;;
  logs)
    tail -f "$LOG_DIR/${2:?usage: dev.sh logs <service-name>}.log"
    ;;
  *)
    echo "usage: dev.sh [start|stop|down|status|logs <service>]" >&2
    exit 1
    ;;
esac
