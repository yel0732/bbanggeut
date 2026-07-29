#!/bin/sh
set -eu

network=${COMPOSE_PROJECT_NAME:-a501}_edge

docker run --rm --network "$network" curlimages/curl:8.12.1 \
    -fsS http://ai-server:8000/health/ready >/dev/null
docker run --rm --network "$network" curlimages/curl:8.12.1 \
    -fsS http://backend:8080/actuator/health/readiness >/dev/null
docker run --rm --network "$network" curlimages/curl:8.12.1 \
    -fsS http://backend:8080/internal/v1/realtime/jwks >/dev/null
