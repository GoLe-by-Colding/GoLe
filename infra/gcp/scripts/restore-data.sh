#!/usr/bin/env bash
set -Eeuo pipefail

SOURCE="${1:?usage: restore-data.sh BACKUP_DIRECTORY}"
MONGO_ARCHIVE="$(find "$SOURCE" -maxdepth 1 -name 'mongo-*.archive.gz' -print | sort | tail -1)"
MINIO_ARCHIVE="$(find "$SOURCE" -maxdepth 1 -name 'minio-*.tar.gz' -print | sort | tail -1)"

test -n "$MONGO_ARCHIVE"
docker cp "$MONGO_ARCHIVE" gole-mongo:/tmp/gole.archive.gz
docker exec gole-mongo mongorestore --drop --archive=/tmp/gole.archive.gz --gzip
docker exec gole-mongo rm /tmp/gole.archive.gz

if [ -n "$MINIO_ARCHIVE" ]; then
  docker run --rm -v gole_minio-data:/target -v "$SOURCE:/backup:ro" alpine \
    sh -c "rm -rf /target/* && tar -C /target -xzf /backup/$(basename "$MINIO_ARCHIVE")"
fi
