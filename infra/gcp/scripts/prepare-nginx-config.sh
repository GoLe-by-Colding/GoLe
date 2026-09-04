#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="/app"
DOMAIN="gole.co.kr"
NGINX_CONFIG="/etc/gole/nginx.conf"
REQUEST_ID="${1:-}"

die() {
  echo "$*" >&2
  exit 1
}

if [ "$#" -ne 1 ] ||
  [[ ! "$REQUEST_ID" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$ ]]; then
  die "usage: prepare-nginx-config.sh REQUEST_UUID"
fi
if [ "$ROOT" != "/app" ] || [ -L "$ROOT" ] || [ ! -d "$ROOT/.git" ]; then
  die "/app must be the production Git checkout"
fi
if [ ! -f "$NGINX_CONFIG" ] || [ -L "$NGINX_CONFIG" ] ||
  [ "$(stat -c '%U:%G:%a' "$NGINX_CONFIG")" != "root:root:644" ]; then
  die "current Nginx configuration is missing or invalid"
fi

template="$ROOT/infra/gcp/nginx-http.conf.template"
if grep -Eq '^[[:space:]]*listen[[:space:]]+443([[:space:]]|;)' "$NGINX_CONFIG"; then
  template="$ROOT/infra/gcp/nginx-https.conf.template"
fi
if [ ! -f "$template" ] || [ -L "$template" ]; then
  die "source-controlled Nginx template is missing or invalid"
fi

compact_request_id="${REQUEST_ID//-/}"
candidate="/tmp/gole-nginx.${compact_request_id}"
if [ -e "$candidate" ] || [ -L "$candidate" ]; then
  die "Nginx candidate already exists"
fi
umask 077
(set -o noclobber; : > "$candidate") || die "could not create Nginx candidate"
cleanup() {
  rm -f -- "$candidate"
}
trap cleanup EXIT
sed "s/__DOMAIN__/${DOMAIN//\//\\/}/g" "$template" > "$candidate"
chmod 0600 "$candidate"

trap - EXIT
echo "Source-controlled Nginx candidate staged for privileged validation."
