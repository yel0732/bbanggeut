#!/bin/sh
set -eu

deploy_root=${DEPLOY_ROOT:-/srv/a501-deploy-state/runtime}
compose_file=${COMPOSE_FILE:-$deploy_root/compose.yml}
env_file=${ENV_FILE:-/srv/a501-secrets/.env}
backup_dir=${DB_BACKUP_DIR:-/srv/a501-backups/mysql}
backup_file=${1:-}
timestamp=$(date -u +%Y%m%dT%H%M%SZ)
container="a501-restore-test-$timestamp"
volume="a501_restore_test_$timestamp"
temp_env=$(mktemp "${TMPDIR:-/tmp}/a501-restore-test.XXXXXX")

cleanup() {
    docker rm -f "$container" >/dev/null 2>&1 || true
    docker volume rm "$volume" >/dev/null 2>&1 || true
    rm -f "$temp_env"
}
trap cleanup INT TERM HUP EXIT

if [ -z "$backup_file" ]; then
    backup_file=$(find "$backup_dir" -maxdepth 1 -type f -name 'a501-*.sql.gz' | sort | tail -n 1)
fi
[ -n "$backup_file" ] && [ -f "$backup_file" ] || {
    echo "Backup file not found: ${backup_file:-<latest>}" >&2
    exit 1
}
gzip -t "$backup_file"

database=$(docker compose -p a501 -f "$compose_file" --env-file "$env_file" exec -T mysql \
    sh -c 'printf %s "$MYSQL_DATABASE"')
root_password=$(docker compose -p a501 -f "$compose_file" --env-file "$env_file" exec -T mysql \
    sh -c 'printf %s "$MYSQL_ROOT_PASSWORD"')
case "$database" in
    ''|*[!A-Za-z0-9_]*) echo 'MYSQL_DATABASE contains unsupported characters.' >&2; exit 1 ;;
esac

umask 077
printf 'MYSQL_ROOT_PASSWORD=%s\nMYSQL_DATABASE=%s\n' "$root_password" "$database" > "$temp_env"
docker volume create "$volume" >/dev/null
docker run -d --name "$container" --env-file "$temp_env" \
    -v "$volume:/var/lib/mysql" mysql:8.4 \
    --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci >/dev/null

attempt=0
until docker exec "$container" sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqladmin ping -h 127.0.0.1 -u root --silent'; do
    attempt=$((attempt + 1))
    [ "$attempt" -lt 60 ] || {
        docker logs "$container" >&2
        echo 'Temporary MySQL did not become ready.' >&2
        exit 1
    }
    sleep 2
done

gzip -dc "$backup_file" | docker exec -i "$container" sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -u root "$MYSQL_DATABASE"'

table_count=$(docker exec "$container" sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -N -B -u root -e \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '\''$MYSQL_DATABASE'\''"')
[ "$table_count" -gt 0 ] || {
    echo 'Restore completed without any application tables.' >&2
    exit 1
}

flyway_version=$(docker exec "$container" sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -N -B -u root "$MYSQL_DATABASE" -e \
    "SELECT COALESCE(MAX(version), '\''none'\'') FROM flyway_schema_history WHERE success = 1"')

echo "Temporary restore verified: backup=$backup_file tables=$table_count flyway=$flyway_version"
trap - INT TERM HUP EXIT
cleanup
