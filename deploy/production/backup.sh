#!/usr/bin/env bash
# Daily PostgreSQL dump with 14-day retention. Run from cron in deploy/production.
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p backups
umask 077
file="backups/mbm_motors-$(date +%Y%m%d-%H%M).sql.gz"
docker compose exec -T db pg_dump -U mbm -d mbm_motors --no-owner | gzip > "$file.tmp"
mv "$file.tmp" "$file"
find backups -name 'mbm_motors-*.sql.gz' -mtime +14 -delete
echo "Backup written: $file"
