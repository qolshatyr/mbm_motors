#!/usr/bin/env bash
# Daily PostgreSQL dump with 14-day local retention and, when BACKUP_REMOTE is set in .env,
# an off-server copy through rclone; BACKUP_HEARTBEAT_URL reports each run to a monitor. Run from cron in deploy/production; see docs/Deployment.md.
set -euo pipefail
cd "$(dirname "$0")"
umask 077

log() { echo "$(date -u +%FT%TZ) $*"; }
fail() { log "ERROR: $*" >&2; exit 1; }

# Read KEY=value from .env without sourcing it: BCrypt hashes in .env contain '$'.
env_value() {
  [ -f .env ] || return 0
  sed -n "s/^$1=//p" .env | tail -n 1
}

remote=${BACKUP_REMOTE-$(env_value BACKUP_REMOTE)}
keep_days=${BACKUP_REMOTE_KEEP_DAYS-$(env_value BACKUP_REMOTE_KEEP_DAYS)}
keep_days=${keep_days:-30}
allow_plain=${BACKUP_REMOTE_ALLOW_PLAIN-$(env_value BACKUP_REMOTE_ALLOW_PLAIN)}
heartbeat=${BACKUP_HEARTBEAT_URL-$(env_value BACKUP_HEARTBEAT_URL)}
case "$heartbeat" in "" | https://*) ;; *) fail "BACKUP_HEARTBEAT_URL must start with https://" ;; esac

mkdir -p backups
file="backups/mbm_motors-$(date +%Y%m%d-%H%M).sql.gz"
# Healthchecks-style monitor: the URL on success, URL/fail on any error; a missing ping alerts too.
finish() {
  local status=$?
  rm -f "$file.tmp"
  if [ -n "$heartbeat" ]; then
    local suffix=""
    [ "$status" -eq 0 ] || suffix=/fail
    curl -fsS -m 10 --retry 3 -o /dev/null "$heartbeat$suffix" || log "WARNING: heartbeat ping failed"
  fi
  exit "$status"
}
trap finish EXIT

docker compose exec -T db pg_dump -U mbm -d mbm_motors --no-owner | gzip > "$file.tmp"
# A dump cut short (disk full, killed container) still gzips cleanly; pg_dump's closing line proves it finished.
gzip -t "$file.tmp" || fail "backup archive is corrupt"
dump_end=$(gzip -dc "$file.tmp" | tail -n 5)
[[ "$dump_end" == *'PostgreSQL database dump complete'* ]] || fail "dump is incomplete"
mv "$file.tmp" "$file"
find backups -name 'mbm_motors-*.sql.gz' -mtime +14 -delete
log "Backup written: $file"

[ -n "$remote" ] || exit 0

# The local copy above is kept even if the off-server step fails.
case "$remote" in *:*) ;; *) fail "BACKUP_REMOTE must look like remote:path" ;; esac
[[ "$keep_days" =~ ^[0-9]+$ && "$keep_days" -ge 7 ]] || fail "BACKUP_REMOTE_KEEP_DAYS must be a number >= 7"
command -v rclone > /dev/null || fail "rclone is not installed"
remote_type=$(rclone listremotes --long | awk -v name="${remote%%:*}:" '$1 == name { print $2 }')
[ -n "$remote_type" ] || fail "rclone remote ${remote%%:*} is not configured"
# Dumps hold partner contacts and order history: leave the server only encrypted.
if [ "$remote_type" != crypt ] && [ "$allow_plain" != true ]; then
  fail "rclone remote ${remote%%:*} is $remote_type, not crypt; set BACKUP_REMOTE_ALLOW_PLAIN=true to accept"
fi

name=$(basename "$file")
rclone copy "$file" "$remote" || fail "upload to $remote failed"
[ "$(rclone lsf "$remote/$name")" = "$name" ] || fail "uploaded copy $name not found in $remote"
rclone delete --min-age "${keep_days}d" --include 'mbm_motors-*.sql.gz' "$remote" \
  || fail "old copies in $remote were not removed"
echo "$(date -u +%FT%TZ) $name" > backups/.last-offsite
log "Off-server copy: $remote/$name"
