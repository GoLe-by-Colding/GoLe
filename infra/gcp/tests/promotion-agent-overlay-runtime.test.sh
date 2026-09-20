#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
IMAGE="python@sha256:9d2e5553305c7c7b0097999bb17187c69b921ccd6bc9d40e4bb5ebe652c00285"

docker run --rm --interactive --volume "$ROOT:/source:ro" "$IMAGE" bash -seu <<'CONTAINER_TEST'
install -d -m 0755 /etc/gole /usr/local/sbin /run/lock
printf 'root:root\n' > /etc/gole/deploy-user
install -m 0660 -o root -g root /dev/null /run/lock/gole-production-rollout.lock
install -m 0755 /source/infra/gcp/scripts/gole-hostctl.sh /usr/local/sbin/gole-hostctl

# 부트스트랩이 남기는 상태: 값 없이 비어 있고 root 전용이다. 유닛의 EnvironmentFile 이
# '-' 없이 이 파일을 요구하므로, 비어 있다는 이유로 거부하면 안 된다.
install -m 0600 -o root -g root /dev/null /etc/gole/promotion-agent.env
SUDO_USER=root /usr/local/sbin/gole-hostctl promotion-agent-overlay-verify

key='ABCDEFGHIJKLMNOPQRSTUVWXYZabcdef0123456789'
request() {
  printf '%s\n' \
    'PROMOTION_AGENT_ANTHROPIC_ENABLED=true' \
    "ANTHROPIC_API_KEY=$key" \
    'PROMOTION_AGENT_MODEL=claude-opus-5' \
    'PROMOTION_AGENT_ADMIN_EMAIL=promotion-bot@gole.co.kr' \
    'PROMOTION_AGENT_ADMIN_PASSWORD=not-a-real-secret-value' \
    'PROMOTION_AGENT_SITE_URL=https://gole.co.kr'
}

request | SUDO_USER=root /usr/local/sbin/gole-hostctl promotion-agent-overlay-install
[ "$(stat -c '%U:%G:%a' /etc/gole/promotion-agent.env)" = root:root:600 ]
[ "$(wc -l < /etc/gole/promotion-agent.env)" -eq 6 ]
SUDO_USER=root /usr/local/sbin/gole-hostctl promotion-agent-overlay-verify
baseline="$(sha256sum /etc/gole/promotion-agent.env | cut -d' ' -f1)"

# 이 파일은 루트 docker compose 의 보간 원본이다. 홍보 프로필 밖의 키를 받으면
# 운영 스택 설정을 여기로 흔들 수 있으므로 거부한다.
if { request; printf 'MINIO_ROOT_PASSWORD=%s\n' "$key"; } |
  SUDO_USER=root /usr/local/sbin/gole-hostctl promotion-agent-overlay-install \
    >/tmp/unknown.out 2>&1; then
  echo 'unknown promotion agent overlay key was accepted' >&2
  exit 1
fi
[ "$(sha256sum /etc/gole/promotion-agent.env | cut -d' ' -f1)" = "$baseline" ]

if request | sed 's/PROMOTION_AGENT_ANTHROPIC_ENABLED=true/PROMOTION_AGENT_ANTHROPIC_ENABLED=maybe/' |
  SUDO_USER=root /usr/local/sbin/gole-hostctl promotion-agent-overlay-install \
    >/tmp/flag.out 2>&1; then
  echo 'invalid promotion agent opt-in flag was accepted' >&2
  exit 1
fi
[ "$(sha256sum /etc/gole/promotion-agent.env | cut -d' ' -f1)" = "$baseline" ]

# 거부 경로가 값을 되뱉지 않는다.
if request | sed 's#https://gole.co.kr#http://gole.co.kr#' |
  SUDO_USER=root /usr/local/sbin/gole-hostctl promotion-agent-overlay-install \
    >/tmp/scheme.out 2>&1; then
  echo 'plaintext promotion agent target was accepted' >&2
  exit 1
fi
! grep -Fq "$key" /tmp/unknown.out
! grep -Fq "$key" /tmp/flag.out
! grep -Fq "$key" /tmp/scheme.out
[ "$(sha256sum /etc/gole/promotion-agent.env | cut -d' ' -f1)" = "$baseline" ]

echo 'Promotion agent root overlay validation and atomic install runtime tests passed.'
CONTAINER_TEST
