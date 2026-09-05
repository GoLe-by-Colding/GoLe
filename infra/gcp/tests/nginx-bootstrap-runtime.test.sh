#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
IMAGE="ubuntu@sha256:33ceb71981b602c1a7443a53469e4dba065f7503eab3078a2d7a57a2ab987517"

docker run --rm --interactive \
  --volume "$ROOT:/app:ro" \
  "$IMAGE" bash -seu <<'CONTAINER_TEST'
install -d -m 0755 /etc/gole
printf 'tls-production-sentinel\n' > /etc/gole/nginx.conf
chmod 0644 /etc/gole/nginx.conf
before="$(sha256sum /etc/gole/nginx.conf | cut -d' ' -f1)"
DOMAIN=gole.co.kr APP_ROOT=/app /app/infra/gcp/scripts/ensure-nginx-config.sh
DOMAIN=gole.co.kr APP_ROOT=/app /app/infra/gcp/scripts/ensure-nginx-config.sh
after="$(sha256sum /etc/gole/nginx.conf | cut -d' ' -f1)"
[ "$before" = "$after" ]
grep -qx 'tls-production-sentinel' /etc/gole/nginx.conf

unlink /etc/gole/nginx.conf
DOMAIN=gole.co.kr APP_ROOT=/app /app/infra/gcp/scripts/ensure-nginx-config.sh
grep -q 'listen 80' /etc/gole/nginx.conf
initial_http_hash="$(sha256sum /etc/gole/nginx.conf | cut -d' ' -f1)"
DOMAIN=gole.co.kr APP_ROOT=/app /app/infra/gcp/scripts/ensure-nginx-config.sh
[ "$(sha256sum /etc/gole/nginx.conf | cut -d' ' -f1)" = "$initial_http_hash" ]

unlink /etc/gole/nginx.conf
install -d /var/lib/docker/volumes/gole_letsencrypt/_data/live/gole.co.kr
printf 'certificate\n' > /var/lib/docker/volumes/gole_letsencrypt/_data/live/gole.co.kr/fullchain.pem
printf 'private-key\n' > /var/lib/docker/volumes/gole_letsencrypt/_data/live/gole.co.kr/privkey.pem
cat > /usr/local/bin/openssl <<'FAKE_OPENSSL'
#!/bin/sh
exit 0
FAKE_OPENSSL
chmod 0755 /usr/local/bin/openssl
DOMAIN=gole.co.kr APP_ROOT=/app /app/infra/gcp/scripts/ensure-nginx-config.sh
grep -q 'listen 443 ssl' /etc/gole/nginx.conf
echo 'Nginx bootstrap reboot/idempotency tests passed.'
CONTAINER_TEST
