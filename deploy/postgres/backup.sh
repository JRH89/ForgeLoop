#!/bin/sh
# Atomic custom-format snapshots. Keep originals until an off-host retention policy exists.
set -eu
umask 077
mkdir -p /backups
while true; do
    snapshot="/backups/forgeloop-$(date -u +%Y%m%dT%H%M%SZ)-$$.dump"
    if pg_dump --format=custom --no-owner --no-acl --file="$snapshot.partial"; then
        if pg_restore --list "$snapshot.partial" >/dev/null; then
            mv "$snapshot.partial" "$snapshot"
            sha256sum "$snapshot" > "$snapshot.sha256"
            # Evidence objects are immutable; capture them after the DB snapshot.
            tar -czf "$snapshot.artifacts.tar.gz.partial" -C /artifacts .
            mv "$snapshot.artifacts.tar.gz.partial" "$snapshot.artifacts.tar.gz"
            sha256sum "$snapshot.artifacts.tar.gz" > "$snapshot.artifacts.tar.gz.sha256"
            touch "$snapshot.complete"
            echo "Database backup completed: $snapshot"
        else
            echo "Database backup validation failed" >&2
            exit 1
        fi
    else
        echo "Database backup failed" >&2
        exit 1
    fi
    sleep "${BACKUP_INTERVAL_SECONDS:-21600}"
done
