#!/usr/bin/env bash
set -Eeuo pipefail

DOMAIN="${DOMAIN:-gole.co.kr}"
APP_ROOT="${APP_ROOT:-/app}"
TEMPLATE_ROOT="${GOLE_TRUSTED_TEMPLATE_ROOT:-$APP_ROOT}"
NGINX_CONFIG="/etc/gole/nginx.conf"

if [ "$(id -u)" -ne 0 ]; then
  echo "run as root" >&2
  exit 1
fi
if [ "$APP_ROOT" != "/app" ]; then
  echo "APP_ROOT must be the dedicated /app path" >&2
  exit 1
fi
if [ "$TEMPLATE_ROOT" != "$APP_ROOT" ]; then
  [[ "$TEMPLATE_ROOT" =~ ^/run/gole-bootstrap\.[A-Za-z0-9]+$ ]] || {
    echo "trusted template root is invalid" >&2
    exit 1
  }
  [ -d "$TEMPLATE_ROOT" ] && [ ! -L "$TEMPLATE_ROOT" ] &&
    [ "$(stat -c '%U:%G:%a' "$TEMPLATE_ROOT")" = "root:root:700" ] || {
    echo "trusted template root metadata is invalid" >&2
    exit 1
  }
fi
if [ "${#DOMAIN}" -gt 253 ] ||
  [[ ! "$DOMAIN" =~ ^([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]{2,63}$ ]]; then
  echo "DOMAIN must be a valid DNS hostname" >&2
  exit 1
fi

install -d -m 0755 -o root -g root /etc/gole
if [ -e "$NGINX_CONFIG" ]; then
  if [ ! -f "$NGINX_CONFIG" ] || [ -L "$NGINX_CONFIG" ] ||
    [ "$(stat -c '%U:%G:%a' "$NGINX_CONFIG")" != "root:root:644" ]; then
    echo "existing Nginx configuration metadata is invalid" >&2
    exit 1
  fi
  echo "Preserving existing Nginx configuration across host bootstrap."
  exit 0
fi

nginx_template="$TEMPLATE_ROOT/infra/gcp/nginx-http.conf.template"
certificate_root="/var/lib/docker/volumes/gole_letsencrypt/_data/live/$DOMAIN"
if [ -s "$certificate_root/fullchain.pem" ] && [ -s "$certificate_root/privkey.pem" ] &&
  openssl x509 -checkend 0 -noout -in "$certificate_root/fullchain.pem" >/dev/null 2>&1; then
  nginx_template="$TEMPLATE_ROOT/infra/gcp/nginx-https.conf.template"
fi

nginx_candidate="$(mktemp /etc/gole/.nginx.conf.XXXXXX)"
cleanup() {
  rm -f -- "$nginx_candidate"
}
trap cleanup EXIT
sed "s/__DOMAIN__/${DOMAIN//\//\\/}/g" "$nginx_template" > "$nginx_candidate"
chown root:root "$nginx_candidate"
chmod 0644 "$nginx_candidate"
mv -f -- "$nginx_candidate" "$NGINX_CONFIG"
