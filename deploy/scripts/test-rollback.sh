#!/bin/sh
set -eu

deploy_root=${DEPLOY_ROOT:-/srv/a501-deploy-state/runtime}
compose_file=${COMPOSE_FILE:-$deploy_root/compose.yml}
env_file=${ENV_FILE:-/srv/a501-secrets/.env}
state_dir=${DEPLOY_STATE_DIR:-/srv/a501-deploy-state}
previous_file=${1:-$state_dir/previous.env}
script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
timestamp=$(date -u +%Y%m%dT%H%M%SZ)
current_file="$state_dir/rollback-test-current-$timestamp.env"
restored=false

[ -s "$previous_file" ] || { echo "Previous image state not found: $previous_file" >&2; exit 1; }
mkdir -p "$state_dir"

ai_current=$(docker inspect --format '{{.Config.Image}}' a501-ai-server-1)
backend_current=$(docker inspect --format '{{.Config.Image}}' a501-backend-1)
nginx_current=$(docker inspect --format '{{.Config.Image}}' a501-nginx-1)
printf "AI_SERVER_IMAGE='%s'\nBACKEND_IMAGE='%s'\nNGINX_IMAGE='%s'\n" \
    "$ai_current" "$backend_current" "$nginx_current" > "$current_file"

restore_current() {
    trap - INT TERM HUP EXIT
    if [ "$restored" = false ]; then
        echo 'Restoring the current application images.' >&2
        COMPOSE_FILE="$compose_file" ENV_FILE="$env_file" \
            "$script_dir/rollback.sh" "$current_file"
        "$script_dir/smoke-test.sh"
        restored=true
    fi
    rm -f "$current_file"
}
trap restore_current INT TERM HUP EXIT

COMPOSE_FILE="$compose_file" ENV_FILE="$env_file" "$script_dir/backup-db.sh" >/dev/null
echo "Testing rollback from backend=$backend_current"
COMPOSE_FILE="$compose_file" ENV_FILE="$env_file" \
    "$script_dir/rollback.sh" "$previous_file"
"$script_dir/smoke-test.sh"
echo 'Previous images became healthy; rollback path verified.'

restore_current
trap - INT TERM HUP EXIT
echo 'Current images restored and smoke-tested.'
