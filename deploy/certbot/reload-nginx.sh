#!/bin/sh
set -eu

cd /srv/a501
/usr/bin/docker compose --env-file /srv/a501-secrets/.env exec -T nginx nginx -s reload
