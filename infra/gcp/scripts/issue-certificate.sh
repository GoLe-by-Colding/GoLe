#!/usr/bin/env bash
set -Eeuo pipefail

cd "$(dirname "$0")/../../.."
ROOT="$(pwd)"
DOMAIN="${DOMAIN:-gole.co.kr}"
EMAIL="${EMAIL:-coldingcontact@gmail.com}"
COMPOSE=(docker compose --env-file /etc/gole/infra.env --env-file /etc/gole/gole.env -f "$ROOT/infra/gcp/docker-compose.yml")

"${COMPOSE[@]}" --profile certificate run --rm certbot certonly \
  --webroot -w /var/www/certbot \
  -d "$DOMAIN" -d "www.$DOMAIN" \
  --non-interactive --agree-tos --email "$EMAIL"

rendered="$(mktemp)"
trap 'rm -f "$rendered"' EXIT
sed "s/__DOMAIN__/${DOMAIN//\//\\/}/g" "$ROOT/infra/gcp/nginx-https.conf.template" > "$rendered"
sudo install -m 0644 "$rendered" /etc/gole/nginx.conf
# install(1)은 파일 inode를 교체한다. bind mount가 새 파일을 보도록 컨테이너를 재생성한다.
"${COMPOSE[@]}" up -d --no-deps --force-recreate nginx
"${COMPOSE[@]}" exec nginx nginx -t

