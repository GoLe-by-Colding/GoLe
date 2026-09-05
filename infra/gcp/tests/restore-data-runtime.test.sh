#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
IMAGE="ubuntu@sha256:33ceb71981b602c1a7443a53469e4dba065f7503eab3078a2d7a57a2ab987517"

docker run --rm --interactive --volume "$ROOT:/source:ro" "$IMAGE" bash -seu <<'CONTAINER_TEST'
backup=/var/backups/gole-data/20260904T140000Z
minio_root=/var/lib/docker/volumes/gole_minio-data/_data
redis_root=/var/lib/docker/volumes/gole_redis-data/_data
install -d -m 0700 "$backup"
install -d -m 0755 /test-bin /usr/local/sbin "$minio_root" "$redis_root"
printf 'old-minio\n' > "$minio_root/old"
printf 'old-redis\n' > "$redis_root/old"
work=/tmp/restore-payload
install -d "$work/minio" "$work/redis"
printf 'new-minio\n' > "$work/minio/object"
printf 'new-redis\n' > "$work/redis/dump.rdb"
printf 'mongo-archive\n' > "$backup/mongo.archive.gz"
tar -C "$work/minio" -czf "$backup/minio.tar.gz" .
tar -C "$work/redis" -czf "$backup/redis.tar.gz" .
(
  cd "$backup"
  sha256sum mongo.archive.gz minio.tar.gz redis.tar.gz > SHA256SUMS
)
printf 'format=gole-logical-backup-v1\ncreated_at=20260904T140000Z\n' > "$backup/COMPLETE"
chmod 0600 "$backup"/*
install -m 0755 /source/infra/gcp/scripts/restore-data.sh /usr/local/sbin/gole-restore-data

cat > /test-bin/docker <<'EOF'
#!/bin/sh
set -eu
case "$1:$2" in
  inspect:--format)
    format="$3"
    container="$4"
    case "$format" in
      *State.Health*)
        [ "$container" = gole-mongo ] && printf 'running:healthy\n' || exit 1
        ;;
      *State.Running*)
        case "$container" in
          gole-redis|gole-minio) printf 'false\n' ;;
          *) printf 'false\n' ;;
        esac
        ;;
    esac
    ;;
  exec:-i)
    cat > /tmp/restored-mongo.archive.gz
    printf '%s\n' "$*" > /tmp/mongorestore.call
    ;;
  *) exit 90 ;;
esac
EOF
chmod 0755 /test-bin/docker
export PATH="/test-bin:$PATH"

# A corrupted checksum must fail before Mongo or either fixed volume changes.
cp "$backup/SHA256SUMS" /tmp/checksums
printf 'corrupt\n' >> "$backup/mongo.archive.gz"
if /usr/local/sbin/gole-restore-data "$backup" >/tmp/corrupt.out 2>&1; then
  echo 'restore accepted a corrupted recovery point' >&2
  exit 1
fi
[ ! -e /tmp/mongorestore.call ]
[ -e "$minio_root/old" ] && [ -e "$redis_root/old" ]
mv /tmp/checksums "$backup/SHA256SUMS"
printf 'mongo-archive\n' > "$backup/mongo.archive.gz"

/usr/local/sbin/gole-restore-data "$backup"
cmp "$backup/mongo.archive.gz" /tmp/restored-mongo.archive.gz
grep -Fq 'exec -i gole-mongo mongorestore --drop --archive --gzip' /tmp/mongorestore.call
[ ! -e "$minio_root/old" ] && [ ! -e "$redis_root/old" ]
grep -qx 'new-minio' "$minio_root/object"
grep -qx 'new-redis' "$redis_root/dump.rdb"

echo 'Logical Mongo, MinIO, and Redis restore contract passed.'
CONTAINER_TEST
