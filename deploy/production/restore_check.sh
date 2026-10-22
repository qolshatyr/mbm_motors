#!/usr/bin/env bash
# Restore a backup into the throwaway database restore_check, compare it with the live
# database and drop it again. The live database is only read. See docs/Deployment.md.
#   ./restore_check.sh                      newest copy in backups/
#   ./restore_check.sh --remote             newest copy in BACKUP_REMOTE (proves the off-server copy)
#   ./restore_check.sh backups/<file>.sql.gz
set -euo pipefail
cd "$(dirname "$0")"
umask 077

log() { echo "$(date -u +%FT%TZ) $*"; }
fail() { log "ERROR: $*" >&2; exit 1; }

env_value() {
  [ -f .env ] || return 0
  sed -n "s/^$1=//p" .env | tail -n 1
}

check_db=restore_check
# Tables whose row counts are compared; a table missing from the backup fails the check.
tables=(products product_offers product_tags partners local_orders local_order_items delivery_outbox sync_runs)

psql_db() { docker compose exec -T db psql -X -q -v ON_ERROR_STOP=1 -U mbm -d "$@"; }

workdir=$(mktemp -d)
cleanup() {
  psql_db postgres -c "drop database if exists $check_db" > /dev/null 2>&1 || true
  rm -rf "$workdir"
}
trap cleanup EXIT

case "${1:-}" in
  --remote)
    remote=${BACKUP_REMOTE-$(env_value BACKUP_REMOTE)}
    [ -n "$remote" ] || fail "BACKUP_REMOTE is not set"
    name=$(rclone lsf --files-only --include 'mbm_motors-*.sql.gz' "$remote" | sort | tail -n 1)
    [ -n "$name" ] || fail "no backups in $remote"
    rclone copy "$remote/$name" "$workdir" || fail "download of $remote/$name failed"
    file="$workdir/$name"
    ;;
  "")
    file=$(find backups -maxdepth 1 -name 'mbm_motors-*.sql.gz' 2> /dev/null | sort | tail -n 1)
    [ -n "$file" ] || fail "no backups in backups/"
    ;;
  *)
    file=$1
    [ -f "$file" ] || fail "$file not found"
    ;;
esac

log "Checking $(basename "$file")"
gzip -t "$file" || fail "archive is corrupt"
psql_db postgres -c "drop database if exists $check_db" -c "create database $check_db"
gzip -dc "$file" | psql_db "$check_db" > /dev/null || fail "restore stopped on an error"

query="select (select version from flyway_schema_history where success order by installed_rank desc limit 1)"
for table in "${tables[@]}"; do query+=", (select count(*) from $table)"; done
restored=$(psql_db "$check_db" -At -c "$query") || fail "restored database lacks expected tables"
live=$(psql_db mbm_motors -At -c "$query") || fail "live database query failed"

IFS='|' read -r -a restored_values <<< "$restored"
IFS='|' read -r -a live_values <<< "$live"
printf '%-20s %10s %10s\n' '' backup live migration "${restored_values[0]}" "${live_values[0]}"
for i in "${!tables[@]}"; do
  printf '%-20s %10s %10s\n' "${tables[$i]}" "${restored_values[$((i + 1))]}" "${live_values[$((i + 1))]}"
done

[ -n "${restored_values[0]}" ] || fail "backup has no applied migrations"
if [ "${restored_values[0]}" != "${live_values[0]}" ]; then
  log "WARNING: migration version differs; the backup predates an update"
fi
log "Restore check passed; $check_db dropped"
