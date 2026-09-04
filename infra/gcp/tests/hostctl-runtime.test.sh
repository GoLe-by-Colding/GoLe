#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
IMAGE="ubuntu@sha256:33ceb71981b602c1a7443a53469e4dba065f7503eab3078a2d7a57a2ab987517"

docker run --rm --interactive \
  --volume "$ROOT:/source:ro" \
  "$IMAGE" bash -seu <<'CONTAINER_TEST'
install -d -m 0755 /etc/gole /usr/local/sbin
printf 'goledeploy:goledeploy\n' > /etc/gole/deploy-user
install -m 0755 /source/infra/gcp/scripts/gole-hostctl.sh /usr/local/sbin/gole-hostctl
install -m 0600 /source/infra/gcp/tests/fixtures/production.env /etc/gole/gole.env
printf '5\n' > /etc/gole/gole.env.version
sha='0123456789abcdef0123456789abcdef01234567'
printf '%s\n' "$sha" > /etc/gole/deployed.sha
chmod 0644 /etc/gole/gole.env.version /etc/gole/deployed.sha

output="$(SUDO_USER=goledeploy /usr/local/sbin/gole-hostctl privilege-probe)"
[ -z "$output" ]
[ "$(SUDO_USER=goledeploy /usr/local/sbin/gole-hostctl deployment-read-sha)" = "$sha" ]
[ "$(SUDO_USER=root /usr/local/sbin/gole-hostctl env-read-version)" = 5 ]
[ "$(stat -c '%U:%G:%a' /etc/gole/gole.env)" = root:root:600 ]

for invocation in \
  'privilege-probe extra' \
  'deployment-read-sha extra' \
  'deployment-verify-runtime' \
  'host-poweroff' \
  'env-install /tmp/attacker'; do
  # shellcheck disable=SC2086 -- intentional argument-vector contract fixture.
  if SUDO_USER=goledeploy /usr/local/sbin/gole-hostctl $invocation >/dev/null 2>&1; then
    echo "hostctl accepted invalid operation vector: $invocation" >&2
    exit 1
  fi
done

if SUDO_USER=attacker /usr/local/sbin/gole-hostctl privilege-probe >/dev/null 2>&1; then
  echo 'hostctl accepted an unconfigured sudo caller' >&2
  exit 1
fi
printf '5\n6\n' > /etc/gole/gole.env.version
if SUDO_USER=root /usr/local/sbin/gole-hostctl env-read-version >/dev/null 2>&1; then
  echo 'multi-line environment version marker was accepted' >&2
  exit 1
fi
printf '%s\n%s\n' "${sha:0:20}" "${sha:20}" > /etc/gole/deployed.sha
if SUDO_USER=goledeploy /usr/local/sbin/gole-hostctl deployment-read-sha >/dev/null 2>&1; then
  echo 'multi-line deployment SHA marker was accepted' >&2
  exit 1
fi

echo 'gole-hostctl caller, argument-vector, and marker runtime tests passed.'
CONTAINER_TEST
