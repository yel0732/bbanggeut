#!/bin/sh
set -eu

source_dir=${JENKINS_HOME_DIR:-/srv/jenkins/home}
backup_dir=${JENKINS_BACKUP_DIR:-/srv/jenkins-backup}
retention_days=${JENKINS_BACKUP_RETENTION_DAYS:-14}
timestamp=$(date -u +%Y%m%dT%H%M%SZ)
container=${JENKINS_CONTAINER:-a501-jenkins}
was_running=false

if [ "$(docker inspect --format '{{.State.Running}}' "$container" 2>/dev/null || true)" = true ]; then
    was_running=true
    docker stop "$container" >/dev/null
fi

restart_jenkins() {
    if [ "$was_running" = true ]; then
        docker start "$container" >/dev/null
    fi
}
trap restart_jenkins EXIT INT TERM HUP

mkdir -p "$backup_dir"
tar --exclude='./workspace' --exclude='./caches' -C "$source_dir" \
    -czf "$backup_dir/jenkins-$timestamp.tar.gz" .
find "$backup_dir" -type f -name 'jenkins-*.tar.gz' -mtime "+$retention_days" -delete

trap - EXIT INT TERM HUP
restart_jenkins
