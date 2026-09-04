#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
IMAGE="nginx:1.29-alpine@sha256:5616878291a2eed594aee8db4dade5878cf7edcb475e59193904b198d9b830de"
TEST_ROOT="$(mktemp -d "$ROOT/.tmp-nginx-dns.XXXXXX")"
TEST_SUFFIX="$(basename "$TEST_ROOT" | tr -cd 'a-zA-Z0-9' | tail -c 20)"
NETWORK="gole-dns-$TEST_SUFFIX"
BACKEND="gole-backend-$TEST_SUFFIX"
FRONTEND="gole-frontend-$TEST_SUFFIX"
FILLER="gole-filler-$TEST_SUFFIX"
PROXY="gole-proxy-$TEST_SUFFIX"

cleanup() {
  docker rm -f "$PROXY" "$BACKEND" "$FRONTEND" "$FILLER" >/dev/null 2>&1 || true
  docker network rm "$NETWORK" >/dev/null 2>&1 || true
  rm -rf -- "$TEST_ROOT"
}
trap cleanup EXIT INT TERM

printf '%s\n' \
  'server {' \
  '  listen 8080;' \
  '  location / { return 200 "backend-v1\n"; }' \
  '}' > "$TEST_ROOT/backend-v1.conf"
printf '%s\n' \
  'server {' \
  '  listen 8080;' \
  '  location / { return 200 "backend-v2\n"; }' \
  '}' > "$TEST_ROOT/backend-v2.conf"
printf '%s\n' \
  'server {' \
  '  listen 3000;' \
  '  location / { return 200 "frontend-ok\n"; }' \
  '}' > "$TEST_ROOT/frontend.conf"
sed 's/__DOMAIN__/gole.co.kr/g' "$ROOT/infra/gcp/nginx-http.conf.template" > \
  "$TEST_ROOT/proxy.conf"

docker network create "$NETWORK" >/dev/null
docker run --detach --name "$BACKEND" --network "$NETWORK" --network-alias backend \
  --volume "$TEST_ROOT/backend-v1.conf:/etc/nginx/conf.d/default.conf:ro" \
  "$IMAGE" >/dev/null
docker run --detach --name "$FRONTEND" --network "$NETWORK" --network-alias frontend \
  --volume "$TEST_ROOT/frontend.conf:/etc/nginx/conf.d/default.conf:ro" \
  "$IMAGE" >/dev/null
docker run --detach --name "$PROXY" --network "$NETWORK" \
  --volume "$TEST_ROOT/proxy.conf:/etc/nginx/conf.d/default.conf:ro" \
  "$IMAGE" >/dev/null

request() {
  docker exec "$PROXY" wget -qO- --header='Host: gole.co.kr' \
    http://127.0.0.1/api/runtime-dns
}

for attempt in $(seq 1 30); do
  if [ "$(request 2>/dev/null || true)" = backend-v1 ]; then
    break
  fi
  test "$attempt" -lt 30
  sleep 1
done

proxy_id="$(docker inspect --format '{{.Id}}' "$PROXY")"
old_backend_ip="$(docker inspect --format "{{(index .NetworkSettings.Networks \"$NETWORK\").IPAddress}}" "$BACKEND")"
docker rm -f "$BACKEND" >/dev/null
docker run --detach --name "$FILLER" --network "$NETWORK" "$IMAGE" >/dev/null
docker run --detach --name "$BACKEND" --network "$NETWORK" --network-alias backend \
  --volume "$TEST_ROOT/backend-v2.conf:/etc/nginx/conf.d/default.conf:ro" \
  "$IMAGE" >/dev/null
new_backend_ip="$(docker inspect --format "{{(index .NetworkSettings.Networks \"$NETWORK\").IPAddress}}" "$BACKEND")"
test "$new_backend_ip" != "$old_backend_ip"

for attempt in $(seq 1 30); do
  if [ "$(request 2>/dev/null || true)" = backend-v2 ]; then
    break
  fi
  test "$attempt" -lt 30
  sleep 1
done

test "$(request)" = backend-v2
test "$(docker inspect --format '{{.Id}}' "$PROXY")" = "$proxy_id"
test "$(docker inspect --format '{{.State.Status}}' "$PROXY")" = running

echo 'Nginx dynamic Docker DNS runtime test passed.'
