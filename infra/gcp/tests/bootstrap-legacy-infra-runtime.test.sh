#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
IMAGE="python@sha256:9d2e5553305c7c7b0097999bb17187c69b921ccd6bc9d40e4bb5ebe652c00285"

docker run --rm --interactive --volume "$ROOT:/source:ro" "$IMAGE" bash -seu <<'CONTAINER_TEST'
legacy_sha=1111111111111111111111111111111111111111
legacy_user=gole-legacy-user-01
legacy_password=ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuv
install -d -m 0755 /etc/gole
useradd --create-home --user-group kscold
ln -s /usr/local/bin/python3 /usr/bin/python3

sed -n '/^read_metadata_migration_marker()/,/^}/p' \
  /source/infra/gcp/scripts/bootstrap-host.sh > /tmp/infra-functions.sh
sed -n '/^validate_legacy_infrastructure_credentials_file()/,/^}/p' \
  /source/infra/gcp/scripts/bootstrap-host.sh >> /tmp/infra-functions.sh
sed -n '/^select_infrastructure_credential_source()/,/^}/p' \
  /source/infra/gcp/scripts/bootstrap-host.sh >> /tmp/infra-functions.sh
# shellcheck disable=SC1091
source /tmp/infra-functions.sh

METADATA_MIGRATION_MARKER=/etc/gole/metadata-migration.pending
GOLE_METADATA_MIGRATION_SOURCE_SHA="$legacy_sha"
cleanup_files=()

write_marker() {
  printf 'state=pending\nlegacy_sha=%s\n' "$1" > "$METADATA_MIGRATION_MARKER"
  chown root:root "$METADATA_MIGRATION_MARKER"
  chmod 0644 "$METADATA_MIGRATION_MARKER"
}

write_legacy_env() {
  printf 'MINIO_ROOT_USER=%s\nMINIO_ROOT_PASSWORD=%s\n' \
    "$legacy_user" "$legacy_password" > /etc/gole/infra.env
  chown kscold:kscold /etc/gole/infra.env
  chmod 0600 /etc/gole/infra.env
}

expect_rejected() {
  if select_infrastructure_credential_source /etc/gole/infra.env \
    > /tmp/rejected.out 2>&1; then
    echo "legacy infrastructure case unexpectedly accepted: $1" >&2
    exit 1
  fi
  if grep -Fq "$legacy_password" /tmp/rejected.out; then
    echo "legacy infrastructure failure exposed a credential: $1" >&2
    exit 1
  fi
}

write_marker "$legacy_sha"
write_legacy_env
select_infrastructure_credential_source /etc/gole/infra.env
[ "$infrastructure_credential_source" != /etc/gole/infra.env ]
[ "$(stat -c '%U:%G:%a:%h' "$infrastructure_credential_source")" = root:root:600:1 ]
cmp -s /etc/gole/infra.env "$infrastructure_credential_source"

write_legacy_env
rm -f "$METADATA_MIGRATION_MARKER"
expect_rejected missing-marker

write_marker 2222222222222222222222222222222222222222
write_legacy_env
expect_rejected mismatched-marker

write_marker "$legacy_sha"
write_legacy_env
ln /etc/gole/infra.env /etc/gole/infra.env.hardlink
expect_rejected hardlink
rm -f /etc/gole/infra.env.hardlink

write_legacy_env
mv /etc/gole/infra.env /etc/gole/infra.env.target
ln -s /etc/gole/infra.env.target /etc/gole/infra.env
expect_rejected symlink
rm -f /etc/gole/infra.env /etc/gole/infra.env.target

write_legacy_env
printf 'UNEXPECTED=true\n' >> /etc/gole/infra.env
expect_rejected unexpected-key

write_legacy_env
printf 'short\n' > /etc/gole/infra.env
chown kscold:kscold /etc/gole/infra.env
chmod 0600 /etc/gole/infra.env
expect_rejected malformed-values

write_legacy_env
chown root:root /etc/gole/infra.env
select_infrastructure_credential_source /etc/gole/infra.env
[ "$infrastructure_credential_source" = /etc/gole/infra.env ]

grep -Fq 'sync -f /etc/gole/infra.env' /source/infra/gcp/scripts/bootstrap-host.sh
grep -Fq 'sync -f /etc/gole' /source/infra/gcp/scripts/bootstrap-host.sh
grep -Fq 'stat -c '\''%h'\'' /etc/gole/infra.env' \
  /source/infra/gcp/scripts/bootstrap-host.sh

echo 'Legacy infrastructure credential adoption runtime contract passed.'
CONTAINER_TEST
