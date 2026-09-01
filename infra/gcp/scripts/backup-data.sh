#!/usr/bin/env bash
set -Eeuo pipefail

DEST="${1:?usage: backup-data.sh DESTINATION_DIRECTORY}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
install -d -m 0700 "$DEST"

docker exec gole-mongo mongodump --db=gole --archive=/tmp/gole.archive.gz --gzip
docker cp gole-mongo:/tmp/gole.archive.gz "$DEST/mongo-$STAMP.archive.gz"
docker exec gole-mongo rm /tmp/gole.archive.gz
docker run --rm -v gole_minio-data:/source:ro -v "$DEST:/backup" alpine \
  tar -C /source -czf "/backup/minio-$STAMP.tar.gz" .
sha256sum "$DEST"/*-$STAMP.* > "$DEST/SHA256SUMS-$STAMP"
