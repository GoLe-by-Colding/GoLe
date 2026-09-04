#!/usr/bin/env bash
set -Eeuo pipefail

# Root-only logical recovery point created shortly before the crash-consistent
# Compute Engine snapshot. Paths and containers are fixed so this helper cannot
# become an arbitrary filesystem reader through caller-controlled arguments.
BACKUP_ROOT="/var/backups/gole-data"
LOCK_FILE="/run/lock/gole-data-backup.lock"
MAX_AGE_SECONDS=93600
KEEP_COUNT=2
MIN_FREE_KIB=$((10 * 1024 * 1024))
MAX_PAYLOAD_BYTES=$((80 * 1024 * 1024 * 1024))
MONGO_CONTAINER="gole-mongo"
MINIO_CONTAINER="gole-minio"
MINIO_VOLUME_ROOT="/var/lib/docker/volumes/gole_minio-data/_data"
MC_IMAGE="minio/mc:latest@sha256:a7fe349ef4bd8521fb8497f55c6042871b2ae640607cf99d9bede5e9bdf11727"

die() {
  echo "$*" >&2
  exit 1
}

[ "$(id -u)" -eq 0 ] || die "logical backup must run as root"
install -d -m 0700 -o root -g root "$BACKUP_ROOT"
install -d -m 0755 -o root -g root /run/lock
exec 9>"$LOCK_FILE"
flock -n 9 || die "another logical backup is active"

latest_complete() {
  find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -xdev -type d \
    -name '20??????T??????Z' -printf '%f\n' | LC_ALL=C sort -r | head -n 1
}

verify_directory() {
  local directory="$1" manifest
  [[ "$directory" =~ ^${BACKUP_ROOT}/20[0-9]{6}T[0-9]{6}Z$ ]] ||
    die "logical backup directory is invalid"
  [ -d "$directory" ] && [ ! -L "$directory" ] || die "logical backup is missing"
  [ "$(stat -c '%U:%G:%a' "$directory")" = "root:root:700" ] ||
    die "logical backup permissions are invalid"
  manifest="$directory/COMPLETE"
  [ -f "$manifest" ] && [ ! -L "$manifest" ] || die "logical backup completion marker is missing"
  [ "$(stat -c '%U:%G:%a' "$manifest")" = "root:root:600" ] ||
    die "logical backup completion marker permissions are invalid"
  (
    cd "$directory"
    sha256sum --check --strict --status SHA256SUMS
    [ -s mongo.archive.gz ]
    [ -s minio.tar.gz ]
  ) || die "logical backup checksum validation failed"
}

if [ "${1:-}" = "--verify-latest" ] && [ "$#" -eq 1 ]; then
  latest="$(latest_complete)"
  [ -n "$latest" ] || die "no completed logical backup exists"
  verify_directory "$BACKUP_ROOT/$latest"
  age=$(( $(date -u +%s) - $(stat -c '%Y' "$BACKUP_ROOT/$latest/COMPLETE") ))
  [ "$age" -ge 0 ] && [ "$age" -le "$MAX_AGE_SECONDS" ] ||
    die "the latest logical backup is stale"
  exit 0
fi
[ "$#" -eq 0 ] || die "usage: backup-data.sh [--verify-latest]"

available_kib="$(df -Pk "$BACKUP_ROOT" | awk 'NR == 2 {print $4}')"
[[ "$available_kib" =~ ^[0-9]+$ ]] && [ "$available_kib" -ge "$MIN_FREE_KIB" ] ||
  die "insufficient boot-disk space for a logical recovery point"

for container in "$MONGO_CONTAINER" "$MINIO_CONTAINER"; do
  state="$(docker inspect --format '{{.State.Status}}:{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' \
    "$container" 2>/dev/null || true)"
  [ "$state" = "running:healthy" ] || die "required data container is not healthy"
done
[ -d "$MINIO_VOLUME_ROOT" ] && [ ! -L "$MINIO_VOLUME_ROOT" ] ||
  die "the fixed MinIO data volume is missing or unsafe"

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
final_dir="$BACKUP_ROOT/$stamp"
[ ! -e "$final_dir" ] && [ ! -L "$final_dir" ] || die "logical backup already exists"
staging_dir="$(mktemp -d "$BACKUP_ROOT/.staging.XXXXXX")"
chmod 0700 "$staging_dir"
minio_frozen=0
cleanup() {
  status=$?
  if [ "$minio_frozen" -eq 1 ]; then
    docker run --rm --network gole_data --env-file /etc/gole/infra.env \
      "$MC_IMAGE" sh -eu -c \
      'mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null && mc admin service unfreeze local >/dev/null' \
      >/dev/null 2>&1 || true
  fi
  [ -z "$staging_dir" ] || rm -rf -- "$staging_dir"
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# Freeze object mutations across both exports. This closes the cross-store
# window where Mongo could reference a media object deleted before the MinIO
# archive. The EXIT trap always attempts an unfreeze on failure or signal.
docker run --rm --network gole_data --env-file /etc/gole/infra.env \
  "$MC_IMAGE" sh -eu -c \
  'mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null && mc admin service freeze local >/dev/null' \
  >/dev/null
minio_frozen=1

# --oplog produces a point-in-time-consistent full replica-set dump and avoids
# a container temporary file that could remain after interruption.
timeout 10m docker exec "$MONGO_CONTAINER" mongodump --archive --gzip --oplog \
  > "$staging_dir/mongo.archive.gz"
timeout 10m tar --one-file-system --numeric-owner -C "$MINIO_VOLUME_ROOT" -czf \
  "$staging_dir/minio.tar.gz" .
docker run --rm --network gole_data --env-file /etc/gole/infra.env \
  "$MC_IMAGE" sh -eu -c \
  'mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null && mc admin service unfreeze local >/dev/null' \
  >/dev/null
minio_frozen=0

mongo_size="$(stat -c '%s' "$staging_dir/mongo.archive.gz")"
minio_size="$(stat -c '%s' "$staging_dir/minio.tar.gz")"
[ "$mongo_size" -ge 1024 ] && [ "$minio_size" -ge 1024 ] &&
  [ $((mongo_size + minio_size)) -le "$MAX_PAYLOAD_BYTES" ] ||
  die "logical backup payload size is outside the reviewed bounds"

(
  cd "$staging_dir"
  sha256sum mongo.archive.gz minio.tar.gz > SHA256SUMS
)
chmod 0600 "$staging_dir"/*
chown -R root:root "$staging_dir"
# Persist payloads, checksums and their directory entries before creating the
# completion marker. guest_flush=false snapshots may begin immediately after
# this service returns, so page-cache-only success is not sufficient.
sync -f "$staging_dir/mongo.archive.gz"
sync -f "$staging_dir/minio.tar.gz"
sync -f "$staging_dir/SHA256SUMS"
sync -f "$staging_dir"
printf 'format=gole-logical-backup-v1\ncreated_at=%s\n' "$stamp" > "$staging_dir/COMPLETE"
chmod 0600 "$staging_dir/COMPLETE"
chown root:root "$staging_dir/COMPLETE"
sync -f "$staging_dir/COMPLETE"
sync -f "$staging_dir"
mv -- "$staging_dir" "$final_dir"
staging_dir=""
sync -f "$BACKUP_ROOT"
trap - EXIT INT TERM

verify_directory "$final_dir"
available_kib="$(df -Pk "$BACKUP_ROOT" | awk 'NR == 2 {print $4}')"
[[ "$available_kib" =~ ^[0-9]+$ ]] && [ "$available_kib" -ge "$MIN_FREE_KIB" ] ||
  die "logical backup completed but boot-disk free space is below the safe floor"

# Keep exactly the two newest completed local artifacts. Every automatic disk
# snapshot contains the two artifacts current at snapshot time; retaining more
# locally only inflates future snapshot deltas without increasing the three-day
# snapshot recovery-point count.
mapfile -t completed_backups < <(
  find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -xdev -type d \
    -name '20??????T??????Z' -printf '%f\n' | LC_ALL=C sort -r
)
for old_name in "${completed_backups[@]:$KEEP_COUNT}"; do
  [[ "$old_name" =~ ^20[0-9]{6}T[0-9]{6}Z$ ]] ||
    die "logical backup rotation selected an invalid path"
  old_directory="$BACKUP_ROOT/$old_name"
  [ -d "$old_directory" ] && [ ! -L "$old_directory" ] ||
    die "logical backup rotation target is unsafe"
  find "$old_directory" -xdev -depth -delete
done
