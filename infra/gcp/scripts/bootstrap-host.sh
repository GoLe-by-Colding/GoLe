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
apt-get install -y ca-certificates curl gnupg nginx certbot python3-certbot-nginx openjdk-21-jdk openssl

install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
. /etc/os-release
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${UBUNTU_CODENAME:-$VERSION_CODENAME} stable" > /etc/apt/sources.list.d/docker.list
curl -fsSL https://deb.nodesource.com/setup_22.x | bash -
apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin nodejs
npm install -g pnpm@10.30.3 pm2

install -d -m 0755 /etc/gole /opt/gole
if [ ! -f /etc/gole/infra.env ]; then
  umask 077
  printf 'MINIO_ROOT_USER=gole-%s\nMINIO_ROOT_PASSWORD=%s\n' \
    "$(openssl rand -hex 8)" "$(openssl rand -base64 36 | tr -d '\n')" > /etc/gole/infra.env
fi
install -m 0644 "$APP_ROOT/infra/gcp/docker-compose.yml" /opt/gole/compose.yml
docker compose --env-file /etc/gole/infra.env -f /opt/gole/compose.yml up -d

for _ in $(seq 1 30); do
  if docker exec gole-mongo mongosh --quiet --eval 'db.adminCommand("ping").ok' >/dev/null 2>&1; then break; fi
  sleep 2
done
docker exec gole-mongo mongosh --quiet --eval \
  'try { rs.status().ok } catch (e) { rs.initiate({_id:"rs0",members:[{_id:0,host:"localhost:27017"}]}) }'

sed "s/__DOMAIN__/${DOMAIN//\//\\/}/g" "$APP_ROOT/infra/gcp/nginx.conf.template" > /etc/nginx/sites-available/gole
ln -sfn /etc/nginx/sites-available/gole /etc/nginx/sites-enabled/gole
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl enable --now nginx docker

echo "Host bootstrap complete. Create /etc/gole/gole.env, build /app, and configure the GitHub runner."
