#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
IMAGE="ubuntu@sha256:33ceb71981b602c1a7443a53469e4dba065f7503eab3078a2d7a57a2ab987517"

docker run --rm --interactive --volume "$ROOT:/source:ro" "$IMAGE" bash -seu <<'CONTAINER_TEST'
install -d -m 0755 /etc/gole /usr/local/sbin /test-bin
printf 'root:root\n' > /etc/gole/deploy-user
install -m 0755 /source/infra/gcp/scripts/gole-hostctl.sh /usr/local/sbin/gole-hostctl

cat > /test-bin/systemctl <<'EOF'
#!/bin/sh
printf '%s\n' "$*" >> /tmp/systemctl.calls
case "$1 ${2:-} ${3:-}" in
  'is-active --quiet gole-cost-guard-watchdog.timer')
    [ "${TIMER_ACTIVE:-0}" = 1 ] ;;
  'is-active --quiet '*) exit 1 ;;
  'poweroff --no-block '*) touch /tmp/poweroff-requested ;;
  *) exit 1 ;;
esac
EOF
cat > /test-bin/docker <<'EOF'
#!/bin/sh
# Model the dangerous legacy condition: the relay still looks healthy even
# though no root broker or watchdog is active.
case "$*" in
  *gole-budget-relay*) printf 'running:healthy\n' ;;
  *) exit 1 ;;
esac
EOF
chmod 0755 /test-bin/systemctl /test-bin/docker
export PATH="/test-bin:/usr/sbin:/usr/bin:/sbin:/bin"

if SUDO_USER=root /usr/local/sbin/gole-hostctl cost-guard-fail-closed \
  >/tmp/fail-closed.out 2>&1; then
  echo 'healthy legacy relay bypassed missing budget protections' >&2
  exit 1
fi
[ -e /tmp/poweroff-requested ] || {
  cat /tmp/fail-closed.out >&2
  cat /tmp/systemctl.calls >&2
  echo 'missing fail-closed poweroff request' >&2
  exit 1
}
grep -q 'cost guard protections are unavailable' /tmp/fail-closed.out || {
  cat /tmp/fail-closed.out >&2
  exit 1
}

rm -f /tmp/poweroff-requested
TIMER_ACTIVE=1 SUDO_USER=root /usr/local/sbin/gole-hostctl cost-guard-fail-closed
[ ! -e /tmp/poweroff-requested ] || {
  echo 'active watchdog timer incorrectly requested poweroff' >&2
  exit 1
}

echo 'Cost guard fail-closed fallback contract passed.'
CONTAINER_TEST
