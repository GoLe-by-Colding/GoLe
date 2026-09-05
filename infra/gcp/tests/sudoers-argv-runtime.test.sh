#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
docker run --rm -i -v "$ROOT:/source:ro" \
  ubuntu@sha256:33ceb71981b602c1a7443a53469e4dba065f7503eab3078a2d7a57a2ab987517 bash -seu <<'TEST'
apt-get update -qq
DEBIAN_FRONTEND=noninteractive apt-get install -y -qq sudo >/dev/null
useradd --create-home goledeploy
sed s/__DEPLOY_USER__/goledeploy/g /source/infra/gcp/sudoers/gole-deploy > /etc/sudoers.d/gole-deploy
chmod 0440 /etc/sudoers.d/gole-deploy
visudo -cf /etc/sudoers.d/gole-deploy
printf '#!/bin/sh\nexit 0\n' > /usr/local/sbin/gole-hostctl
chmod 0755 /usr/local/sbin/gole-hostctl
allow() { runuser -u goledeploy -- sudo -n /usr/local/sbin/gole-hostctl "$@"; }
deny() {
  if allow "$@" >/dev/null 2>&1; then
    echo "unexpected sudo permission: $*" >&2
    exit 1
  fi
}
sha=1111111111111111111111111111111111111111
request=12345678-1234-1234-1234-123456789abc
allow privilege-probe
allow deployment-begin all "$sha" 0 "$request"
allow deployment-environment-prepare 6 "$sha" "$request"
allow secret-sync 6 "$request"
allow certificate-issue
deny
deny privilege-probe extra
deny deployment-begin all "$sha" 0 "$request" extra
deny deployment-environment-prepare latest "$sha" "$request"
deny secret-sync 6 "$request" extra
deny certificate-renew
deny deployment-reset-initial-failure
if runuser -u goledeploy -- sudo -n /bin/sh -c true >/dev/null 2>&1; then
  echo 'arbitrary root shell allowed' >&2
  exit 1
fi
echo 'Real sudo argument allowlist regression passed.'
TEST
