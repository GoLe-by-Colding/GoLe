#!/usr/bin/env bash
set -Eeuo pipefail

BACKUP_ROOT="/var/backups/gole-data"
MINIO_VOLUME_ROOT="/var/lib/docker/volumes/gole_minio-data/_data"
REDIS_VOLUME_ROOT="/var/lib/docker/volumes/gole_redis-data/_data"

die() {
  echo "$*" >&2
  exit 1
}

[ "$(id -u)" -eq 0 ] || die "logical restore must run as root"
[ "$#" -eq 1 ] || die "usage: restore-data.sh BACKUP_DIRECTORY"
SOURCE="$1"
[[ "$SOURCE" =~ ^${BACKUP_ROOT}/20[0-9]{6}T[0-9]{6}Z$ ]] ||
  die "logical restore path is invalid"
[ -d "$SOURCE" ] && [ ! -L "$SOURCE" ] &&
  [ "$(stat -c '%U:%G:%a' "$SOURCE")" = root:root:700 ] ||
  die "logical restore directory is invalid"
for artifact in COMPLETE SHA256SUMS mongo.archive.gz minio.tar.gz redis.tar.gz; do
  [ -f "$SOURCE/$artifact" ] && [ ! -L "$SOURCE/$artifact" ] &&
    [ "$(stat -c '%U:%G:%a' "$SOURCE/$artifact")" = root:root:600 ] ||
    die "logical restore artifact is invalid"
done
(
  cd "$SOURCE"
  sha256sum --check --strict --status SHA256SUMS &&
    [ -s mongo.archive.gz ] &&
    [ -s minio.tar.gz ] &&
  [ -s redis.tar.gz ]
) || die "logical restore checksum validation failed"

container_state() {
  docker inspect --format '{{.State.Running}}' "$1" 2>/dev/null || printf 'missing\n'
}

# The restore command never stops a live public application implicitly. The
# operator must first quiesce it, leaving only healthy Mongo running for the
# streaming mongorestore. Redis and MinIO volumes are extracted only while
# their containers are stopped.
for container in gole-nginx gole-frontend gole-backend gole-support-agent gole-budget-relay; do
  [ "$(container_state "$container")" != true ] ||
    die "public application must be quiesced before logical restore"
done
[ "$(container_state gole-redis)" = false ] || die "Redis must be stopped before logical restore"
[ "$(container_state gole-minio)" = false ] || die "MinIO must be stopped before logical restore"
[ "$(docker inspect --format \
  '{{.State.Status}}:{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' \
  gole-mongo 2>/dev/null || true)" = running:healthy ] ||
  die "Mongo must be running and healthy before logical restore"
for volume_root in "$MINIO_VOLUME_ROOT" "$REDIS_VOLUME_ROOT"; do
  [ -d "$volume_root" ] && [ ! -L "$volume_root" ] ||
    die "fixed restore volume is missing or unsafe"
done

docker exec -i gole-mongo mongorestore --drop --archive --gzip < "$SOURCE/mongo.archive.gz"
find "$MINIO_VOLUME_ROOT" -mindepth 1 -xdev -delete
tar --same-owner -C "$MINIO_VOLUME_ROOT" -xzf "$SOURCE/minio.tar.gz"
find "$REDIS_VOLUME_ROOT" -mindepth 1 -xdev -delete
tar --same-owner -C "$REDIS_VOLUME_ROOT" -xzf "$SOURCE/redis.tar.gz"
sync -f "$MINIO_VOLUME_ROOT"
sync -f "$REDIS_VOLUME_ROOT"
