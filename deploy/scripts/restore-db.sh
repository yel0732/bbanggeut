#!/bin/sh
set -eu

backup_file=${1:-}
confirmation=${2:-}
deploy_root=${DEPLOY_ROOT:-/srv/a501-deploy-state/runtime}
compose_file=${COMPOSE_FILE:-$deploy_root/compose.yml}
env_file=${ENV_FILE:-/srv/a501-secrets/.env}
script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)

if [ -z "$backup_file" ] || [ "$confirmation" != 'RESTORE_PRODUCTION_DB' ]; then
    echo 'Usage: restore-db.sh BACKUP.sql.gz RESTORE_PRODUCTION_DB' >&2
    exit 2
fi
[ -f "$backup_file" ] || { echo "Backup file not found: $backup_file" >&2; exit 1; }
gzip -t "$backup_file"

database=$(docker compose -p a501 -f "$compose_file" --env-file "$env_file" exec -T mysql \
    sh -c 'printf %s "$MYSQL_DATABASE"')
case "$database" in
    ''|*[!A-Za-z0-9_]*) echo 'MYSQL_DATABASE contains unsupported characters.' >&2; exit 1 ;;
esac

echo 'Creating a safety backup before production restore.'
COMPOSE_FILE="$compose_file" ENV_FILE="$env_file" "$script_dir/backup-db.sh"

docker compose -p a501 -f "$compose_file" --env-file "$env_file" stop nginx backend ai-server
docker compose -p a501 -f "$compose_file" --env-file "$env_file" exec -T mysql sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -u root -e \
    "DROP DATABASE IF EXISTS `'$database'`; CREATE DATABASE `'$database'` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"'
gzip -dc "$backup_file" | docker compose -p a501 -f "$compose_file" --env-file "$env_file" \
    exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -u root "$MYSQL_DATABASE"'

docker compose -p a501 -f "$compose_file" --env-file "$env_file" up -d ai-server backend nginx
"$script_dir/wait-for-healthy.sh" a501-ai-server-1 120
"$script_dir/wait-for-healthy.sh" a501-backend-1 180
"$script_dir/wait-for-healthy.sh" a501-nginx-1 60
"$script_dir/smoke-test.sh"
echo "Production restore completed and verified: $backup_file"
