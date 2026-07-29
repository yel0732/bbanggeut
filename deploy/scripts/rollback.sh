#!/bin/sh
set -eu

previous_file=$1
deploy_root=${DEPLOY_ROOT:-/srv/a501-deploy-state/runtime}
compose_file=${COMPOSE_FILE:-$deploy_root/compose.yml}
env_file=${ENV_FILE:-/srv/a501-secrets/.env}

. "$previous_file"
export AI_SERVER_IMAGE BACKEND_IMAGE NGINX_IMAGE
export NGINX_CONFIG_FILE="$deploy_root/deploy/nginx.conf"
export CERTBOT_WEBROOT=${CERTBOT_WEBROOT_HOST:-/srv/a501/deploy/certbot/www}
export LETSENCRYPT_LIVE_DIR=${LETSENCRYPT_LIVE_DIR_HOST:-/srv/a501/deploy/certbot/letsencrypt/live/i15a501.p.ssafy.io}
export LETSENCRYPT_ARCHIVE_DIR=${LETSENCRYPT_ARCHIVE_DIR_HOST:-/srv/a501/deploy/certbot/letsencrypt/archive/i15a501.p.ssafy.io}

docker compose -p a501 -f "$compose_file" --env-file "$env_file" up -d --no-deps ai-server
"$(dirname "$0")/wait-for-healthy.sh" a501-ai-server-1 120
docker compose -p a501 -f "$compose_file" --env-file "$env_file" up -d --no-deps backend
"$(dirname "$0")/wait-for-healthy.sh" a501-backend-1 180
docker compose -p a501 -f "$compose_file" --env-file "$env_file" up -d --no-deps nginx
"$(dirname "$0")/wait-for-healthy.sh" a501-nginx-1 60
