#!/bin/sh
set -eu

deploy_root=${DEPLOY_ROOT:-/srv/a501-deploy-state/runtime}
compose_file=${COMPOSE_FILE:-$deploy_root/compose.yml}
env_file=${ENV_FILE:-/srv/a501-secrets/.env}

echo '== Compose services =='
docker compose -p a501 -f "$compose_file" --env-file "$env_file" ps
echo '== Docker volumes =='
docker volume inspect a501_mysql_data a501_redis_data --format '{{.Name}} {{.Mountpoint}}'
echo '== Disk =='
df -h / /var/lib/docker /srv 2>/dev/null | awk 'NR == 1 || !seen[$6]++'
echo '== Memory and swap =='
free -h
swapon --show || true
echo '== Docker disk usage =='
docker system df
echo '== TLS certificate =='
openssl x509 -in /etc/letsencrypt/live/i15a501.p.ssafy.io/fullchain.pem \
    -noout -subject -issuer -dates
echo '== Certificate renewal timer =='
systemctl is-enabled certbot.timer
systemctl is-active certbot.timer
echo '== Application smoke test =='
"$(dirname "$0")/smoke-test.sh"
echo 'Operational checks passed.'
