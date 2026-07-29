#!/bin/sh
set -eu

deploy_root=${DEPLOY_ROOT:-/srv/a501}
compose_file=${COMPOSE_FILE:-$deploy_root/compose.yml}
env_file=${ENV_FILE:-/srv/a501-secrets/.env}
backup_dir=${DB_BACKUP_DIR:-/srv/a501-backups/mysql}
retention_days=${DB_BACKUP_RETENTION_DAYS:-14}
timestamp=$(date -u +%Y%m%dT%H%M%SZ)
output="$backup_dir/a501-$timestamp.sql.gz"

mkdir -p "$backup_dir"
docker compose -p a501 -f "$compose_file" --env-file "$env_file" exec -T mysql \
    sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump --single-transaction --routines --triggers -u root "$MYSQL_DATABASE"' \
    | gzip -c > "$output"
test -s "$output"
find "$backup_dir" -type f -name 'a501-*.sql.gz' -mtime "+$retention_days" -delete
echo "$output"
