#!/bin/sh
set -eu

previous_file=$1
compose_file=${COMPOSE_FILE:-compose.yml}
env_file=${ENV_FILE:-/srv/a501-secrets/.env}

. "$previous_file"
export AI_SERVER_IMAGE BACKEND_IMAGE NGINX_IMAGE

docker compose -p a501 -f "$compose_file" --env-file "$env_file" up -d --no-deps ai-server
"$(dirname "$0")/wait-for-healthy.sh" a501-ai-server-1 120
docker compose -p a501 -f "$compose_file" --env-file "$env_file" up -d --no-deps backend
"$(dirname "$0")/wait-for-healthy.sh" a501-backend-1 180
docker compose -p a501 -f "$compose_file" --env-file "$env_file" up -d --no-deps nginx
"$(dirname "$0")/wait-for-healthy.sh" a501-nginx-1 60
