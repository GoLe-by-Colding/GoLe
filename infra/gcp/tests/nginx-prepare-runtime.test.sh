#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
IMAGE="ubuntu@sha256:33ceb71981b602c1a7443a53469e4dba065f7503eab3078a2d7a57a2ab987517"

docker run --rm --interactive \
  --volume "$ROOT:/app:ro" \
  "$IMAGE" bash -seu <<'CONTAINER_TEST'
install -d -m 0755 /etc/gole /test-bin
cat > /test-bin/docker <<'FAKE_DOCKER'
#!/bin/sh
echo 'unprivileged candidate preparation must not call Docker' >&2
exit 99
FAKE_DOCKER
chmod 0755 /test-bin/docker
export PATH="/test-bin:$PATH"

printf 'server { listen 80; }\n' > /etc/gole/nginx.conf
chmod 0644 /etc/gole/nginx.conf
request_http='10000000-0000-4000-8000-000000000001'
candidate_http="/tmp/gole-nginx.${request_http//-/}"
/app/infra/gcp/scripts/prepare-nginx-config.sh "$request_http" >/dev/null
[ "$(stat -c '%U:%G:%a' "$candidate_http")" = 'root:root:600' ]
grep -q 'server_name www.gole.co.kr' "$candidate_http"
grep -q 'return 301 https://gole.co.kr\$request_uri' "$candidate_http"
! grep -q '__DOMAIN__' "$candidate_http"
! grep -q 'listen 443 ssl' "$candidate_http"
rm -f "$candidate_http"

printf 'server {\n    listen 443 ssl;\n}\n' > /etc/gole/nginx.conf
request_https='20000000-0000-4000-8000-000000000002'
candidate_https="/tmp/gole-nginx.${request_https//-/}"
/app/infra/gcp/scripts/prepare-nginx-config.sh "$request_https" >/dev/null
grep -q 'listen 443 ssl' "$candidate_https"
grep -q 'return 301 https://gole.co.kr\$request_uri' "$candidate_https"
grep -q 'client_max_body_size 64m' "$candidate_https"
! grep -q '__DOMAIN__' "$candidate_https"
rm -f "$candidate_https"

if /app/infra/gcp/scripts/prepare-nginx-config.sh not-a-uuid >/dev/null 2>&1; then
  echo 'invalid request ID was accepted' >&2
  exit 1
fi

echo 'Source-controlled Nginx candidate runtime tests passed.'
CONTAINER_TEST
