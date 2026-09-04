#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
IMAGE="ubuntu@sha256:33ceb71981b602c1a7443a53469e4dba065f7503eab3078a2d7a57a2ab987517"

docker run --rm --interactive \
  --volume "$ROOT:/source:ro" \
  "$IMAGE" bash -seu <<'CONTAINER_TEST'
install -d -m 0755 /test-bin /usr/local/sbin \
  /var/lib/docker/volumes/gole_minio-data/_data
dd if=/dev/urandom of=/var/lib/docker/volumes/gole_minio-data/_data/object.bin \
  bs=2048 count=1 status=none
install -m 0755 /source/infra/gcp/scripts/backup-data.sh /usr/local/sbin/gole-backup-data

cat > /test-bin/docker <<'FAKE_DOCKER'
#!/bin/sh
set -eu
case "$1" in
  inspect)
    printf 'running:healthy\n'
    ;;
  exec)
    printf 'mongo-export\n' >> /tmp/backup-events
    [ ! -e /tmp/fail-mongo ] || exit 71
    dd if=/dev/zero bs=2048 count=1 status=none
    ;;
  run)
    case "$*" in
      *'service freeze'*) printf 'freeze\n' >> /tmp/backup-events ;;
      *'service unfreeze'*) printf 'unfreeze\n' >> /tmp/backup-events ;;
      *) exit 72 ;;
    esac
    ;;
  *) exit 73 ;;
esac
FAKE_DOCKER
cat > /test-bin/tar <<'FAKE_TAR'
#!/bin/sh
printf 'minio-archive\n' >> /tmp/backup-events
exec /usr/bin/tar "$@"
FAKE_TAR
cat > /test-bin/sync <<'FAKE_SYNC'
#!/bin/sh
printf 'sync %s\n' "$*" >> /tmp/backup-events
exec /usr/bin/sync "$@"
FAKE_SYNC
chmod 0755 /test-bin/*
export PATH="/test-bin:$PATH"

# A failed Mongo export occurs while MinIO is frozen. The EXIT trap must
# unfreeze it and no completion marker may survive.
: > /tmp/backup-events
: > /tmp/fail-mongo
if /usr/local/sbin/gole-backup-data >/tmp/backup-failure.out 2>&1; then
  echo 'failed logical backup unexpectedly succeeded' >&2
  exit 1
fi
[ "$(grep -c '^freeze$' /tmp/backup-events)" = 1 ]
[ "$(grep -c '^unfreeze$' /tmp/backup-events)" = 1 ]
! find /var/backups/gole-data -name COMPLETE -print -quit | grep -q .
rm -f /tmp/fail-mongo

# Seed harmless old directories to prove rotation is count-based rather than
# mtime rounding. Only the two lexically newest recovery points may remain.
install -d -m 0700 /var/backups/gole-data/20200101T000000Z \
  /var/backups/gole-data/20210101T000000Z \
  /var/backups/gole-data/20220101T000000Z
: > /tmp/backup-events
/usr/local/sbin/gole-backup-data
/usr/local/sbin/gole-backup-data --verify-latest
[ "$(find /var/backups/gole-data -mindepth 1 -maxdepth 1 -type d \
  -name '20??????T??????Z' | wc -l)" = 2 ]

freeze_line="$(grep -n '^freeze$' /tmp/backup-events | cut -d: -f1)"
mongo_line="$(grep -n '^mongo-export$' /tmp/backup-events | cut -d: -f1)"
archive_line="$(grep -n '^minio-archive$' /tmp/backup-events | cut -d: -f1)"
unfreeze_line="$(grep -n '^unfreeze$' /tmp/backup-events | cut -d: -f1)"
payload_sync_line="$(grep -n 'sync .*mongo.archive.gz' /tmp/backup-events | cut -d: -f1)"
complete_sync_line="$(grep -n 'sync .*COMPLETE' /tmp/backup-events | cut -d: -f1)"
[ "$freeze_line" -lt "$mongo_line" ]
[ "$mongo_line" -lt "$archive_line" ]
[ "$archive_line" -lt "$unfreeze_line" ]
[ "$payload_sync_line" -lt "$complete_sync_line" ]

echo 'Logical backup freeze, durability, verification, and rotation tests passed.'
CONTAINER_TEST
