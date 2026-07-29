#!/bin/sh
set -eu

container=$1
timeout_seconds=${2:-120}
elapsed=0

while [ "$elapsed" -lt "$timeout_seconds" ]; do
    status=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || true)
    [ "$status" = healthy ] && exit 0
    [ "$status" = exited ] && break
    sleep 5
    elapsed=$((elapsed + 5))
done

docker logs --tail 100 "$container" 2>&1 || true
echo "Container did not become healthy: $container" >&2
exit 1
