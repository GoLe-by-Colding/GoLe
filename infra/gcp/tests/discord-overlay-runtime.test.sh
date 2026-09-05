#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
IMAGE="python@sha256:9d2e5553305c7c7b0097999bb17187c69b921ccd6bc9d40e4bb5ebe652c00285"

docker run --rm --interactive --volume "$ROOT:/source:ro" "$IMAGE" bash -seu <<'CONTAINER_TEST'
install -d -m 0755 /etc/gole /usr/local/sbin /run/lock
printf 'root:root\n' > /etc/gole/deploy-user
install -m 0660 -o root -g root /dev/null /run/lock/gole-production-rollout.lock
install -m 0755 /source/infra/gcp/scripts/gole-hostctl.sh /usr/local/sbin/gole-hostctl

operations='https://discord.com/api/webhooks/100000000000000001/ABCDEFGHIJKLMNOPQRSTUVWXYZabcdef_1000000001'
account='https://discordapp.com/api/webhooks/100000000000000002/ABCDEFGHIJKLMNOPQRSTUVWXYZabcdef_1000000002'
payment='https://discord.com/api/webhooks/100000000000000003/ABCDEFGHIJKLMNOPQRSTUVWXYZabcdef_1000000003'
request() {
  printf '%s\n' \
    'GOLE_DISCORD_ALERTS_ENABLED=true' \
    "DISCORD_DEPLOY_WEBHOOK_URL=$operations" \
    "DISCORD_OPERATIONS_WEBHOOK_URL=$operations" \
    "DISCORD_ACCOUNT_WEBHOOK_URL=$account" \
    "DISCORD_PAYMENT_WEBHOOK_URL=$payment" \
    "DISCORD_SUPPORT_WEBHOOK_URL=$operations" \
    'DISCORD_SUPPRESS_NOTIFICATIONS=false'
}

request | SUDO_USER=root /usr/local/sbin/gole-hostctl discord-overlay-install
[ "$(stat -c '%U:%G:%a' /etc/gole/discord.env)" = root:root:600 ]
[ "$(wc -l < /etc/gole/discord.env)" -eq 7 ]
baseline="$(sha256sum /etc/gole/discord.env | cut -d' ' -f1)"

if request | sed 's#https://discord.com/api/webhooks/#https://attacker.test/api/webhooks/#' |
  SUDO_USER=root /usr/local/sbin/gole-hostctl discord-overlay-install >/tmp/invalid.out 2>&1; then
  echo 'invalid Discord origin was accepted' >&2
  exit 1
fi
[ "$(sha256sum /etc/gole/discord.env | cut -d' ' -f1)" = "$baseline" ]
! grep -Fq 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdef' /tmp/invalid.out

if { request; printf 'UNKNOWN=value\n'; } |
  SUDO_USER=root /usr/local/sbin/gole-hostctl discord-overlay-install >/tmp/unknown.out 2>&1; then
  echo 'unknown Discord overlay key was accepted' >&2
  exit 1
fi
[ "$(sha256sum /etc/gole/discord.env | cut -d' ' -f1)" = "$baseline" ]

if request | sed 's/DISCORD_SUPPRESS_NOTIFICATIONS=false/DISCORD_SUPPRESS_NOTIFICATIONS=maybe/' |
  SUDO_USER=root /usr/local/sbin/gole-hostctl discord-overlay-install >/tmp/flag.out 2>&1; then
  echo 'invalid Discord suppression flag was accepted' >&2
  exit 1
fi

echo 'Discord root overlay validation and atomic install runtime tests passed.'
CONTAINER_TEST
