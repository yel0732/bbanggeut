#!/bin/sh
set -eu

mode=${1:-dry-run}
case "$mode" in
    dry-run)
        echo 'Dry run only. Docker disk usage:'
        docker system df
        echo 'Run with --apply to remove dangling images/build cache older than 7 days.'
        ;;
    --apply)
        docker image prune -f --filter until=168h
        docker builder prune -f --filter until=168h
        ;;
    *)
        echo 'Usage: cleanup-docker.sh [dry-run|--apply]' >&2
        exit 2
        ;;
esac
