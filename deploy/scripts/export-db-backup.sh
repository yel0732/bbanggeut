#!/bin/sh
set -eu

backup_file=${1:-}
destination=${2:-}

if [ -z "$backup_file" ] || [ -z "$destination" ]; then
    echo 'Usage: export-db-backup.sh BACKUP.sql.gz MOUNTED_DIRECTORY_OR_SCP_DESTINATION' >&2
    exit 2
fi
[ -f "$backup_file" ] || { echo "Backup file not found: $backup_file" >&2; exit 1; }
gzip -t "$backup_file"

case "$destination" in
    *:*)
        scp -p "$backup_file" "$destination"
        ;;
    *)
        [ -d "$destination" ] || { echo "Destination directory not found: $destination" >&2; exit 1; }
        source_device=$(stat -c %d "$backup_file")
        destination_device=$(stat -c %d "$destination")
        [ "$source_device" != "$destination_device" ] || {
            echo 'Destination must be a separately mounted filesystem, not the EC2 root disk.' >&2
            exit 1
        }
        cp -p "$backup_file" "$destination/"
        ;;
esac
echo "Backup exported outside the EC2 root filesystem: $backup_file"
