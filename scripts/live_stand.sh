#!/usr/bin/env bash
# Live test stand on this computer: real CloudShop catalog, partner sign-in and delivery of orders
# as unposted sales, published through a temporary Cloudflare quick tunnel (https://*.trycloudflare.com).
#
#   scripts/live_stand.sh start | local | stop | status | url
#   scripts/live_stand.sh partner <login> <company> <contact> <phone>
#
# State lives in $MBM_STAND_DIR (default ~/.local/share/mbm-motors): PostgreSQL binaries and data,
# app.jar (release build with the interface), stand.env (staff access) and logs. The CloudShop token
# is read from the repository .env and never printed. Every order on the stand creates a real
# unposted sale in CloudShop on the partner's card.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
STAND="${MBM_STAND_DIR:-$HOME/.local/share/mbm-motors}"
PORT="${MBM_STAND_PORT:-18080}"
DB_PORT="${MBM_STAND_DB_PORT:-55432}"
TEST_CUSTOMER="6ac110842dd87801040fed88"   # «тестовый покупатель» in CloudShop
export DB_URL="jdbc:postgresql://127.0.0.1:${DB_PORT}/mbm_motors" DB_USER=mbm DB_PASSWORD=

token() { grep '^CLOUDSHOP_TOKEN=' "$ROOT/.env" | cut -d= -f2-; }
running() { [ -f "$STAND/$1.pid" ] && kill -0 "$(cat "$STAND/$1.pid")" 2>/dev/null; }

db_start() {
  if ! "$STAND/pg/bin/pg_ctl" -D "$STAND/pgdata" status >/dev/null 2>&1; then
    "$STAND/pg/bin/pg_ctl" -D "$STAND/pgdata" -l "$STAND/postgres.log" \
      -o "-p $DB_PORT -k $STAND -c listen_addresses=127.0.0.1" -w start >/dev/null
  fi
}

start() {
  app_start
  tunnel_start
  url
}

app_start() {
  db_start
  if ! running app; then
    [ -n "$(token)" ] || { echo 'В .env нет CLOUDSHOP_TOKEN.' >&2; exit 1; }
    set -a; . "$STAND/stand.env"; set +a
    CLOUDSHOP_TOKEN="$(token)" nohup java -jar "$STAND/app.jar" \
      --frontend.enabled=true --server.port="$PORT" \
      --server.forward-headers-strategy=framework \
      --catalog.sync.enabled=true --delivery.enabled=true \
      > "$STAND/app.log" 2>&1 &
    echo $! > "$STAND/app.pid"
    for _ in $(seq 1 90); do
      curl -fs -o /dev/null "http://127.0.0.1:$PORT/login" && break
      running app || { echo 'Приложение не запустилось, см. app.log' >&2; exit 1; }
      sleep 1
    done
  fi
}

tunnel_start() {
  if ! running tunnel; then
    : > "$STAND/tunnel.log"
    nohup cloudflared tunnel --no-autoupdate --url "http://127.0.0.1:$PORT" > "$STAND/tunnel.log" 2>&1 &
    echo $! > "$STAND/tunnel.pid"
    for _ in $(seq 1 60); do grep -q 'trycloudflare.com' "$STAND/tunnel.log" && break; sleep 1; done
  fi
}

url() {
  local address
  address="$(grep -o 'https://[a-z0-9-]*\.trycloudflare\.com' "$STAND/tunnel.log" 2>/dev/null | head -1 || true)"
  echo "Локально: http://127.0.0.1:$PORT"
  echo "Снаружи:  ${address:-туннель не запущен}"
}

stop() {
  for name in tunnel app; do
    if running "$name"; then kill "$(cat "$STAND/$name.pid")"; fi
    rm -f "$STAND/$name.pid"
  done
  "$STAND/pg/bin/pg_ctl" -D "$STAND/pgdata" -w stop >/dev/null 2>&1 || true
  echo 'Стенд остановлен.'
}

status() {
  for name in app tunnel; do running "$name" && echo "$name: работает" || echo "$name: остановлен"; done
  "$STAND/pg/bin/pg_ctl" -D "$STAND/pgdata" status >/dev/null 2>&1 && echo 'db: работает' || echo 'db: остановлена'
  url
}

partner() {
  [ $# -eq 4 ] || { echo 'partner <login> <company> <contact> <phone>' >&2; exit 2; }
  db_start
  read -rsp 'Пароль партнёра (не короче 10 символов): ' MBM_PARTNER_PASSWORD; echo
  MBM_PARTNER_LOGIN="$1" MBM_PARTNER_COMPANY="$2" MBM_PARTNER_CONTACT="$3" MBM_PARTNER_PHONE="$4" \
    MBM_PARTNER_PASSWORD="$MBM_PARTNER_PASSWORD" \
    java -jar "$STAND/app.jar" --spring.main.web-application-type=none --partner-add
  # On the stand every partner orders on the CloudShop test customer card.
  MBM_PARTNER_LOGIN="$1" MBM_CLOUDSHOP_CUSTOMER_ID="$TEST_CUSTOMER" \
    java -jar "$STAND/app.jar" --spring.main.web-application-type=none --partner-link-customer
}

case "${1:-}" in
  start) start ;;
  local) app_start; url ;;
  stop) stop ;;
  status) status ;;
  url) url ;;
  partner) shift; partner "$@" ;;
  *) echo 'Использование: scripts/live_stand.sh start|stop|status|url|partner' >&2; exit 2 ;;
esac
