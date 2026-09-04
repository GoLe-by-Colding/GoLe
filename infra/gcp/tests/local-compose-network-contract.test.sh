#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
model="$(mktemp)"
cleanup() { rm -f -- "$model"; }
trap cleanup EXIT

docker compose -f "$ROOT/docker-compose.yml" config --format json > "$model"
jq -e '
  [.services | to_entries[] | .value.ports[]?] as $ports |
  ($ports | length) > 0 and
  all($ports[]; .host_ip == "127.0.0.1")
' "$model" >/dev/null || {
  echo "로컬 Compose의 공개 포트는 모두 127.0.0.1에만 바인딩해야 합니다." >&2
  exit 1
}

echo "Local Compose loopback-only port contract passed."
