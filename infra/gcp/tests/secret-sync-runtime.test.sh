#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
IMAGE="python@sha256:9d2e5553305c7c7b0097999bb17187c69b921ccd6bc9d40e4bb5ebe652c00285"

docker run --rm --interactive \
  --volume "$ROOT:/source:ro" \
  "$IMAGE" bash -seu <<'CONTAINER_TEST'
sha='1111111111111111111111111111111111111111'
release="/var/lib/gole/releases/$sha"
install -d -m 0755 /etc/gole /usr/local/libexec/gole /usr/local/sbin /usr/local/bin \
  "$release/infra/gcp"
printf 'root:root\n' > /etc/gole/deploy-user
printf 'MINIO_ROOT_USER=test\nMINIO_ROOT_PASSWORD=test-password\n' > /etc/gole/infra.env
install -m 0600 -o root -g root /source/infra/gcp/tests/fixtures/discord.env \
  /etc/gole/discord.env
printf 'PROJECT_ID=test-project-123\n' > /etc/gole/cloud-broker.conf
chmod 0600 /etc/gole/infra.env /etc/gole/cloud-broker.conf
install -m 0755 /source/infra/gcp/scripts/gole-hostctl.sh /usr/local/sbin/gole-hostctl
install -m 0755 /source/infra/gcp/scripts/validate-production-env.py \
  /usr/local/libexec/gole/validate-production-env.py
ln -s /usr/local/bin/python3 /usr/bin/python3
printf '#!/bin/sh\ncat >/dev/null\n' > /usr/local/libexec/gole/validate-production-compose.py
chmod 0755 /usr/local/libexec/gole/*.py
touch "$release/infra/gcp/docker-compose.yml"
printf '%s\n' "$sha" > "$release/.gole-source-sha"
chown -R root:root /var/lib/gole
chmod -R go-w /var/lib/gole

install -d /fixtures
install -m 0600 /source/infra/gcp/tests/fixtures/production.env /fixtures/base.env
cp /fixtures/base.env /fixtures/v6.env
printf 'SAFE_POLICY_REVISION=6\n' >> /fixtures/v6.env
cp /fixtures/base.env /fixtures/v8.env
printf 'SAFE_POLICY_REVISION=8\n' >> /fixtures/v8.env
cp /fixtures/base.env /fixtures/v9.env
printf 'SAFE_POLICY_REVISION=9\n' >> /fixtures/v9.env
install -m 0600 /source/infra/gcp/tests/fixtures/development.env /fixtures/development.env

cat > /usr/bin/gcloud <<'FAKE_GCLOUD'
#!/bin/sh
set -eu
[ "$1" = secrets ] && [ "$2" = versions ] && [ "$3" = access ] || exit 91
version="$4"
shift 4
output=''
for argument in "$@"; do
  case "$argument" in
    --secret=gole-production-env|--project=test-project-123|--quiet) ;;
    --out-file=*) output="${argument#--out-file=}" ;;
    *) exit 92 ;;
  esac
done
[ -n "$output" ] || exit 93
printf 'called:%s\n' "$version" >> /tmp/gcloud-calls
case "$version" in
  5) cp /fixtures/base.env "$output" ;;
  6) cp /fixtures/v6.env "$output" ;;
  7) cp /fixtures/development.env "$output" ;;
  8) cp /fixtures/v8.env "$output" ;;
  9) cp /fixtures/v9.env "$output" ;;
  *) exit 94 ;;
esac
FAKE_GCLOUD

cat > /usr/local/bin/docker <<'FAKE_DOCKER'
#!/bin/sh
set -eu
printf '%s\n' "$*" >> /tmp/docker-calls
case "$1" in
  compose)
    case "$*" in
      *' config --format json') printf '{}\n' ;;
      *' config --services') printf 'support-agent\nbackend\nfrontend\nbudget-relay\nnginx\n' ;;
      *' up -d '*)
        if [ -e /tmp/fail-up-once ]; then
          rm -f /tmp/fail-up-once
          exit 50
        fi
        ;;
    esac
    ;;
  inspect) printf 'running:healthy\n' ;;
  exec) ;;
  *) exit 95 ;;
esac
FAKE_DOCKER

cat > /usr/local/bin/curl <<'FAKE_CURL'
#!/bin/sh
case "$*" in
  *'http://www.gole.co.kr/__gole-canonical-check?source=runtime'*)
    printf '301|https://gole.co.kr/__gole-canonical-check?source=runtime' ;;
  *'https://www.gole.co.kr/__gole-canonical-check?source=runtime'*)
    printf '301|https://gole.co.kr/__gole-canonical-check?source=runtime' ;;
  *'-fsSI '*'https://gole.co.kr/'*)
    printf 'HTTP/2 200\r\nStrict-Transport-Security: max-age=31536000\r\n\r\n' ;;
esac
exit 0
FAKE_CURL

cat > /usr/local/bin/systemctl <<'FAKE_SYSTEMCTL'
#!/bin/sh
if [ "$1" = is-active ] && [ "$2" = --quiet ] &&
  [ "$3" = gole-cost-guard-watchdog.timer ]; then
  exit 0
fi
if [ "$1" = poweroff ]; then
  printf 'poweroff\n' >> /tmp/poweroff-calls
  exit 0
fi
exit 96
FAKE_SYSTEMCTL
chmod 0755 /usr/bin/gcloud /usr/local/bin/docker /usr/local/bin/curl \
  /usr/local/bin/systemctl

install -m 0600 -o root -g root /fixtures/base.env /etc/gole/gole.env
printf '5\n' > /etc/gole/gole.env.version
printf '%s\n' "$sha" > /etc/gole/deployed.sha
chmod 0644 /etc/gole/gole.env.version /etc/gole/deployed.sha
baseline_hash="$(sha256sum /etc/gole/gole.env | cut -d' ' -f1)"

run_sync() {
  SUDO_USER=root /usr/local/sbin/gole-hostctl secret-sync "$1" "$2"
}

# Replay is rejected before metadata credentials/Secret Manager are touched.
if run_sync 4 00000000-0000-0000-0000-000000000004 >/tmp/lower.out 2>&1; then
  echo 'older Secret Manager version was accepted' >&2
  exit 1
fi
[ ! -e /tmp/gcloud-calls ]
[ "$(sha256sum /etc/gole/gole.env | cut -d' ' -f1)" = "$baseline_hash" ]

# Same version and bytes is an idempotent no-op.
if ! run_sync 5 00000000-0000-0000-0000-000000000005 >/tmp/equal.out 2>&1; then
  cat /tmp/equal.out >&2
  exit 1
fi
[ ! -e /var/backups/gole-env ]

# A development payload fails before install and never leaks a value/version.
if run_sync 7 00000000-0000-0000-0000-000000000007 >/tmp/development.out 2>&1; then
  echo 'development environment was accepted by Secret Sync' >&2
  exit 1
fi
[ "$(cat /etc/gole/gole.env.version)" = 5 ]
[ "$(sha256sum /etc/gole/gole.env | cut -d' ' -f1)" = "$baseline_hash" ]
! grep -Fq 'developer@example.test' /tmp/development.out

run_sync 6 10000000-0000-0000-0000-000000000006 >/tmp/success.out 2>&1
v6_hash="$(sha256sum /fixtures/v6.env | cut -d' ' -f1)"
[ "$(cat /etc/gole/gole.env.version)" = 6 ]
[ "$(sha256sum /etc/gole/gole.env | cut -d' ' -f1)" = "$v6_hash" ]

# A failed rollout restores the old env/version, re-verifies the same immutable
# LKG release and leaves no candidate or transaction. The first failed up is
# consumed; recovery must perform a second successful up.
touch /tmp/fail-up-once
if run_sync 8 20000000-0000-0000-0000-000000000008 >/tmp/failure.out 2>&1; then
  echo 'failed environment rollout returned success' >&2
  exit 1
fi
[ "$(cat /etc/gole/gole.env.version)" = 6 ]
[ "$(sha256sum /etc/gole/gole.env | cut -d' ' -f1)" = "$v6_hash" ]
[ ! -e /etc/gole/gole.env.transaction ]
[ ! -e /tmp/poweroff-calls ]

run_sync 8 30000000-0000-0000-0000-000000000008 >/tmp/v8.out 2>&1
run_sync 9 40000000-0000-0000-0000-000000000009 >/tmp/v9.out 2>&1
[ "$(cat /etc/gole/gole.env.version)" = 9 ]
[ "$(sha256sum /etc/gole/gole.env | cut -d' ' -f1)" = \
  "$(sha256sum /fixtures/v9.env | cut -d' ' -f1)" ]

# Plaintext rollback files are root-only and exactly the newest two are kept.
[ "$(find /var/backups/gole-env -maxdepth 1 -type f -name 'gole.env.*' | wc -l)" = 2 ]
while IFS= read -r backup; do
  [ "$(stat -c '%U:%G:%a' "$backup")" = root:root:600 ]
done < <(find /var/backups/gole-env -maxdepth 1 -type f -name 'gole.env.*')
if find /etc/gole -maxdepth 1 -type f \( -name '.secret.*' -o -name '.environment.candidate.*' \) |
  grep -q .; then
  echo 'Secret Sync left a plaintext temporary behind' >&2
  exit 1
fi
for output in /tmp/equal.out /tmp/development.out /tmp/success.out /tmp/failure.out /tmp/v8.out /tmp/v9.out; do
  ! grep -Fq 'abcdefghijklmnop' "$output"
  ! grep -Eq '[0-9a-f]{64}' "$output"
done

echo 'Secret Sync root broker, rollback, replay, and backup retention tests passed.'
CONTAINER_TEST
