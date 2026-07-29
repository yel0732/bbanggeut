#!/bin/sh
set -eu

sha=$1
compose_file=${COMPOSE_FILE:-compose.yml}
env_file=${ENV_FILE:-/srv/a501-secrets/.env}
state_dir=${DEPLOY_STATE_DIR:-/srv/a501-deploy-state}
previous_file="$state_dir/previous.env"

mkdir -p "$state_dir"
ai_previous=$(docker inspect --format '{{.Config.Image}}' a501-ai-server-1)
backend_previous=$(docker inspect --format '{{.Config.Image}}' a501-backend-1)
nginx_previous=$(docker inspect --format '{{.Config.Image}}' a501-nginx-1)
printf "AI_SERVER_IMAGE='%s'\nBACKEND_IMAGE='%s'\nNGINX_IMAGE='%s'\n" \
    "$ai_previous" "$backend_previous" "$nginx_previous" > "$previous_file"

export AI_SERVER_IMAGE="a501-ai-server:$sha"
export BACKEND_IMAGE="a501-backend:$sha"
export NGINX_IMAGE="a501-nginx:$sha"

rollback() {
    trap - INT TERM HUP EXIT
    echo 'Deployment failed; restoring previous application images.' >&2
    COMPOSE_FILE="$compose_file" ENV_FILE="$env_file" "$(dirname "$0")/rollback.sh" "$previous_file"
}
trap rollback INT TERM HUP EXIT

COMPOSE_FILE="$compose_file" ENV_FILE="$env_file" "$(dirname "$0")/backup-db.sh"
docker compose -p a501 -f "$compose_file" --env-file "$env_file" up -d --no-deps ai-server
"$(dirname "$0")/wait-for-healthy.sh" a501-ai-server-1 120
docker compose -p a501 -f "$compose_file" --env-file "$env_file" up -d --no-deps backend
"$(dirname "$0")/wait-for-healthy.sh" a501-backend-1 180
docker compose -p a501 -f "$compose_file" --env-file "$env_file" up -d --no-deps nginx
"$(dirname "$0")/wait-for-healthy.sh" a501-nginx-1 60
"$(dirname "$0")/smoke-test.sh"

trap - INT TERM HUP EXIT
printf '%s\n' "$sha" > "$state_dir/current-sha"
