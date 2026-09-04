#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
IMAGE="python@sha256:9d2e5553305c7c7b0097999bb17187c69b921ccd6bc9d40e4bb5ebe652c00285"

docker run --rm --interactive \
  --volume "$ROOT:/source:ro" \
  "$IMAGE" bash -seu <<'CONTAINER_TEST'
sha=0123456789abcdef0123456789abcdef01234567
release=/var/lib/gole/releases/$sha
install -d -m 0755 /app/.git /app/infra/gcp /etc/gole /usr/local/libexec/gole \
  /usr/local/sbin "$release/infra/gcp"
groupadd goledeploy
printf 'runner-owned-before-validation\n' >/app/infra/gcp/docker-compose.yml
install -m 0644 /source/infra/gcp/docker-compose.yml \
  "$release/infra/gcp/docker-compose.yml"
printf '%s\n' "$sha" >"$release/.gole-source-sha"
chown -R root:root /var/lib/gole/releases
chmod -R go-w /var/lib/gole/releases
printf 'goledeploy:goledeploy\n' > /etc/gole/deploy-user
printf '%s\n' "$sha" >/etc/gole/deployed.sha
chmod 0644 /etc/gole/deployed.sha
printf 'MINIO_ROOT_USER=test\nMINIO_ROOT_PASSWORD=test-password\n' > /etc/gole/infra.env
install -m 0600 -o root -g root /source/infra/gcp/tests/fixtures/discord.env \
  /etc/gole/discord.env
printf 'POLICY=test\n' > /etc/gole/gole.env
chmod 0600 /etc/gole/infra.env /etc/gole/gole.env
install -m 0660 -o root -g goledeploy /dev/null /run/lock/gole-production-rollout.lock
install -m 0755 /source/infra/gcp/scripts/gole-hostctl.sh /usr/local/sbin/gole-hostctl
printf '#!/bin/sh\nexit 0\n' > /usr/local/libexec/gole/validate-production-env.py
cat > /usr/local/libexec/gole/validate-production-compose.py <<'FAKE_VALIDATOR'
#!/bin/sh
set -eu
cat >/dev/null
# Deterministically model a runner replacing /app after policy validation. Root
# Docker must still parse the already-selected immutable deployment release.
printf 'runner-owned-after-validation\n' >/app/infra/gcp/docker-compose.yml
FAKE_VALIDATOR
cat > /usr/local/bin/docker <<'FAKE_DOCKER'
#!/bin/sh
set -eu
printf '%s\n' "$*" >> /tmp/docker-calls
[ "${*#*-f /var/lib/gole/releases/0123456789abcdef0123456789abcdef01234567/infra/gcp/docker-compose.yml}" != "$*" ] || {
  echo 'root Docker did not select the immutable deployment release' >&2
  exit 89
}
case "$*" in
  *'-f /app/infra/gcp/docker-compose.yml'*)
    echo 'root Docker parsed the runner-owned Compose file' >&2
    exit 88
    ;;
esac
case "$*" in
  *'config --format json'*) printf '{}\n' ;;
  *'--profile certificate run --rm --no-deps -T certbot renew --quiet'*) : ;;
  *'exec -T nginx nginx -t'*) : ;;
  *'exec -T nginx nginx -s reload'*) : ;;
  *) exit 90 ;;
esac
FAKE_DOCKER
chmod 0755 /usr/local/libexec/gole/*.py /usr/local/bin/docker

SUDO_USER=root /usr/local/sbin/gole-hostctl certificate-renew
grep -Fxq 'runner-owned-after-validation' /app/infra/gcp/docker-compose.yml
[ "$(grep -c -- "-f $release/infra/gcp/docker-compose.yml" /tmp/docker-calls)" -eq 4 ]
! grep -q -- '-f /app/infra/gcp/docker-compose.yml' /tmp/docker-calls
grep -q -- '--profile certificate run --rm --no-deps -T certbot renew --quiet' /tmp/docker-calls
grep -q 'exec -T nginx nginx -t' /tmp/docker-calls
grep -q 'exec -T nginx nginx -s reload' /tmp/docker-calls

if SUDO_USER=goledeploy /usr/local/sbin/gole-hostctl certificate-renew >/dev/null 2>&1; then
  echo 'runner was allowed to invoke certificate renewal' >&2
  exit 1
fi

exec 7>>/run/lock/gole-production-rollout.lock
flock -n 7
if SUDO_USER=root /usr/local/sbin/gole-hostctl certificate-renew >/dev/null 2>&1; then
  echo 'certificate renewal ignored the active production rollout lock' >&2
  exit 1
fi
flock -u 7

echo 'Certificate renewal trust and lock runtime contract passed.'
CONTAINER_TEST
