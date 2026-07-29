#!/bin/sh
set -eu

sha=$1
source_root=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)
deploy_root=${DEPLOY_ROOT:-/srv/a501-deploy-state/runtime}
compose_file=${COMPOSE_FILE:-$deploy_root/compose.yml}
env_file=${ENV_FILE:-/srv/a501-secrets/.env}
state_dir=${DEPLOY_STATE_DIR:-/srv/a501-deploy-state}
previous_file="$state_dir/previous.env"
script_dir="$deploy_root/deploy/scripts"

# Containers must never bind-mount files from Jenkins' disposable workspace.
# Publish only runtime deployment assets to the stable EC2 deployment directory.
mkdir -p "$deploy_root/deploy" "$script_dir"
install -m 0644 "$source_root/compose.yml" "$deploy_root/compose.yml"
install -m 0644 "$source_root/deploy/nginx.conf" "$deploy_root/deploy/nginx.conf"
install -m 0644 "$source_root/deploy/nginx-http.conf" "$deploy_root/deploy/nginx-http.conf"
install -m 0755 "$source_root"/deploy/scripts/*.sh "$script_dir/"

mkdir -p "$state_dir"
ai_previous=$(docker inspect --format '{{.Config.Image}}' a501-ai-server-1)
backend_previous=$(docker inspect --format '{{.Config.Image}}' a501-backend-1)
nginx_previous=$(docker inspect --format '{{.Config.Image}}' a501-nginx-1)
printf "AI_SERVER_IMAGE='%s'\nBACKEND_IMAGE='%s'\nNGINX_IMAGE='%s'\n" \
    "$ai_previous" "$backend_previous" "$nginx_previous" > "$previous_file"

export AI_SERVER_IMAGE="a501-ai-server:$sha"
export BACKEND_IMAGE="a501-backend:$sha"
export NGINX_IMAGE="a501-nginx:$sha"
export NGINX_CONFIG_FILE="$deploy_root/deploy/nginx.conf"
export CERTBOT_WEBROOT=${CERTBOT_WEBROOT_HOST:-/srv/a501/deploy/certbot/www}
export LETSENCRYPT_LIVE_DIR=${LETSENCRYPT_LIVE_DIR_HOST:-/etc/letsencrypt/live/i15a501.p.ssafy.io}
export LETSENCRYPT_ARCHIVE_DIR=${LETSENCRYPT_ARCHIVE_DIR_HOST:-/etc/letsencrypt/archive/i15a501.p.ssafy.io}

rollback() {
    trap - INT TERM HUP EXIT
    echo 'Deployment failed; restoring previous application images.' >&2
    COMPOSE_FILE="$compose_file" ENV_FILE="$env_file" "$script_dir/rollback.sh" "$previous_file"
}
trap rollback INT TERM HUP EXIT

COMPOSE_FILE="$compose_file" ENV_FILE="$env_file" "$script_dir/backup-db.sh"
docker compose -p a501 -f "$compose_file" --env-file "$env_file" up -d --no-deps redis
"$script_dir/wait-for-healthy.sh" a501-redis-1 60
docker compose -p a501 -f "$compose_file" --env-file "$env_file" up -d --no-deps ai-server
"$script_dir/wait-for-healthy.sh" a501-ai-server-1 120
docker compose -p a501 -f "$compose_file" --env-file "$env_file" up -d --no-deps backend
"$script_dir/wait-for-healthy.sh" a501-backend-1 180
docker compose -p a501 -f "$compose_file" --env-file "$env_file" up -d --no-deps nginx
"$script_dir/wait-for-healthy.sh" a501-nginx-1 60
"$script_dir/smoke-test.sh"

trap - INT TERM HUP EXIT
printf '%s\n' "$sha" > "$state_dir/current-sha"
