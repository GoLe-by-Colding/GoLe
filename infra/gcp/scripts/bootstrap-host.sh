#!/usr/bin/env bash
set -Eeuo pipefail

DOMAIN="${DOMAIN:-gole.co.kr}"
APP_ROOT="${APP_ROOT:-/app}"

if [ "$(id -u)" -ne 0 ]; then
  echo "run as root" >&2
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y ca-certificates curl gnupg openssl

install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
. /etc/os-release
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${UBUNTU_CODENAME:-$VERSION_CODENAME} stable" > /etc/apt/sources.list.d/docker.list
apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

install -d -m 0755 /etc/gole /opt/gole
if [ ! -f /etc/gole/infra.env ]; then
  umask 077
  printf 'MINIO_ROOT_USER=gole-%s\nMINIO_ROOT_PASSWORD=%s\n' \
    "$(openssl rand -hex 8)" "$(openssl rand -base64 36 | tr -d '\n')" > /etc/gole/infra.env
fi
sed "s/__DOMAIN__/${DOMAIN//\//\\/}/g" "$APP_ROOT/infra/gcp/nginx-http.conf.template" > /etc/gole/nginx.conf
chmod 0644 /etc/gole/nginx.conf
systemctl enable --now docker

echo "Host bootstrap complete. Create /etc/gole/gole.env, then run /app/scripts/deploy.sh."
