#!/bin/sh
set -eu

deploy_root=${DEPLOY_ROOT:-/srv/a501-deploy-state/runtime}
compose_file=${COMPOSE_FILE:-$deploy_root/compose.yml}
env_file=${ENV_FILE:-/srv/a501-secrets/.env}

/usr/bin/docker compose -p a501 -f "$compose_file" --env-file "$env_file" \
    exec -T nginx nginx -s reload
