#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
IMAGE="python@sha256:9d2e5553305c7c7b0097999bb17187c69b921ccd6bc9d40e4bb5ebe652c00285"

docker run --rm --interactive --volume "$ROOT:/source:ro" "$IMAGE" bash -seu <<'CONTAINER_TEST'
expected_sha=0123456789abcdef0123456789abcdef01234567
release="/var/lib/gole/releases/$expected_sha"

groupadd --system --gid 10001 golecloud
install -d -m 0755 /app/.git /etc/gole /usr/local/libexec/gole /usr/local/sbin \
  /test-bin "$release/infra/gcp"
printf 'services: {}\n' > "$release/infra/gcp/docker-compose.yml"
cat > "$release/infra/gcp/nginx-https.conf.template" <<'EOF'
server {
    listen 80;
    server_name __DOMAIN__ www.__DOMAIN__;
    location / { return 301 https://$host$request_uri; }
}
server {
    listen 443 ssl;
    server_name __DOMAIN__ www.__DOMAIN__;
    add_header Strict-Transport-Security "max-age=31536000" always;
}
EOF
printf '%s\n' "$expected_sha" > "$release/.gole-source-sha"
chown -R root:root /var/lib/gole
chmod -R go-w /var/lib/gole
sed 's/__DOMAIN__/gole.co.kr/g' "$release/infra/gcp/nginx-https.conf.template" \
  > /etc/gole/nginx.conf
chmod 0644 /etc/gole/nginx.conf
printf 'root:root\n' > /etc/gole/deploy-user
printf 'MINIO_ROOT_USER=test-user\nMINIO_ROOT_PASSWORD=test-password-value\n' \
  > /etc/gole/infra.env
install -m 0600 -o root -g root /source/infra/gcp/tests/fixtures/development.env \
  /etc/gole/gole.env
install -m 0600 -o root -g root /source/infra/gcp/tests/fixtures/discord.env \
  /etc/gole/discord.env
chmod 0600 /etc/gole/infra.env
printf '5\n' > /etc/gole/gole.env.version
printf '%s\n' "$expected_sha" > /etc/gole/deployed.sha
printf 'state=pending\nlegacy_sha=%s\n' "$expected_sha" \
  > /etc/gole/metadata-migration.pending
chmod 0644 /etc/gole/gole.env.version /etc/gole/deployed.sha \
  /etc/gole/metadata-migration.pending
install -d -m 0710 -o root -g golecloud /run/gole-cloud-broker
touch /run/gole-cloud-broker/policy-heartbeat
chown root:golecloud /run/gole-cloud-broker/policy-heartbeat
chmod 0600 /run/gole-cloud-broker/policy-heartbeat

install -m 0755 /source/infra/gcp/scripts/gole-hostctl.sh /usr/local/sbin/gole-hostctl
printf '#!/bin/sh\ntouch /tmp/strict-env-validator-called\nexit 99\n' \
  > /usr/local/libexec/gole/validate-production-env.py
printf '#!/bin/sh\ncat >/dev/null\n' > /usr/local/libexec/gole/validate-production-compose.py
chmod 0755 /usr/local/libexec/gole/*.py

cat > /test-bin/git <<EOF
#!/bin/sh
case "\$*" in
  *'status --porcelain'*) exit 0 ;;
  *'rev-parse --verify HEAD'*) printf '%s\\n' '$expected_sha' ;;
  *) exit 90 ;;
esac
EOF
cat > /test-bin/docker <<'EOF'
#!/bin/sh
set -eu
case "$1" in
  compose)
    case "$*" in
      *'config --format json'*) printf '{"services":{}}\n' ;;
      *) exit 0 ;;
    esac
    ;;
  inspect)
    case "$*" in
      *gole-backend|*gole-frontend|*gole-budget-relay) printf 'running:healthy\n' ;;
      *gole-nginx) printf 'running:missing\n' ;;
      *) exit 91 ;;
    esac
    ;;
  exec) [ "$2" = gole-nginx ] ;;
  *) exit 92 ;;
esac
EOF
cat > /test-bin/systemctl <<'EOF'
#!/bin/sh
[ "$1" = is-active ] && [ "$2" = --quiet ] &&
  case "$3" in
    gole-cost-guard-watchdog.timer|gole-cloud-broker.service) exit 0 ;;
    *) exit 1 ;;
  esac
EOF
cat > /test-bin/iptables <<'EOF'
#!/bin/sh
case " $* " in
  *' -t raw -C PREROUTING -j GOLE_METADATA_INPUT '*) exit 1 ;;
  *' -C OUTPUT -d 169.254.169.254/32 -j GOLE_METADATA_OUTPUT '*|\
  *' -C GOLE_METADATA_OUTPUT -m owner --uid-owner 0 -j RETURN '*|\
  *' -C GOLE_METADATA_OUTPUT -j REJECT '*) exit 0 ;;
  *) exit 1 ;;
esac
EOF
cat > /test-bin/ip6tables <<'EOF'
#!/bin/sh
case " $* " in
  *' -t raw -C PREROUTING -j GOLE_METADATA_INPUT '*) exit 1 ;;
  *' -C OUTPUT -d fd20:ce::254/128 -j GOLE_METADATA_OUTPUT '*) exit 0 ;;
  *) exit 1 ;;
esac
EOF
cat > /test-bin/curl <<'EOF'
#!/bin/sh
case "$*" in
  *'http://gole.co.kr/__gole-legacy-transport-check?source=adoption'*)
    printf '301|https://gole.co.kr/__gole-legacy-transport-check?source=adoption' ;;
  *'http://www.gole.co.kr/__gole-legacy-transport-check?source=adoption'*)
    if [ "${FAKE_BAD_LEGACY_WWW:-0}" = 1 ]; then
      printf '301|https://attacker.invalid/'
    else
      printf '301|https://www.gole.co.kr/__gole-legacy-transport-check?source=adoption'
    fi
    ;;
  *'-fsSI '*'https://gole.co.kr/'*)
    if [ "${FAKE_MISSING_HSTS:-0}" = 1 ]; then
      printf 'HTTP/2 200\r\n\r\n'
    else
      printf 'HTTP/2 200\r\nStrict-Transport-Security: max-age=31536000\r\n\r\n'
    fi
    ;;
  *'-fsSI '*'https://www.gole.co.kr/'*)
    if [ "${FAKE_MISSING_WWW_HSTS:-0}" = 1 ]; then
      printf 'HTTP/2 200\r\n\r\n'
    else
      printf 'HTTP/2 200\r\nStrict-Transport-Security: max-age=31536000\r\n\r\n'
    fi
    ;;
  *'https://www.gole.co.kr/'*) printf '200|' ;;
  *'https://gole.co.kr/'*) printf '200|' ;;
  *'http://127.0.0.1:8080/actuator/health/readiness'*|\
  *'http://127.0.0.1:3000/icon.svg'*) exit 0 ;;
  *) exit 93 ;;
esac
EOF
chmod 0755 /test-bin/*
install -m 0755 /test-bin/docker /usr/local/bin/docker
export PATH="/test-bin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

SUDO_USER=root /usr/local/sbin/gole-hostctl \
  deployment-verify-adopted-runtime "$expected_sha"
[ ! -e /tmp/strict-env-validator-called ]

cp /etc/gole/nginx.conf /tmp/nginx.conf.valid
printf '# drift\n' >> /etc/gole/nginx.conf
if SUDO_USER=root /usr/local/sbin/gole-hostctl \
  deployment-verify-adopted-runtime "$expected_sha" >/tmp/config-drift.out 2>&1; then
  echo 'legacy adopted verifier accepted an unreviewed Nginx config' >&2
  exit 1
fi
grep -q 'does not match the reviewed release' /tmp/config-drift.out
cp /tmp/nginx.conf.valid /etc/gole/nginx.conf

if FAKE_BAD_LEGACY_WWW=1 SUDO_USER=root /usr/local/sbin/gole-hostctl \
  deployment-verify-adopted-runtime "$expected_sha" >/tmp/redirect-drift.out 2>&1; then
  echo 'legacy adopted verifier accepted a redirected www route' >&2
  exit 1
fi
grep -q 'legacy HTTP www redirect changed' /tmp/redirect-drift.out

if FAKE_MISSING_HSTS=1 SUDO_USER=root /usr/local/sbin/gole-hostctl \
  deployment-verify-adopted-runtime "$expected_sha" >/tmp/hsts-drift.out 2>&1; then
  echo 'legacy adopted verifier accepted a response without HSTS' >&2
  exit 1
fi
grep -q 'legacy HTTPS apex response is missing HSTS' /tmp/hsts-drift.out

if FAKE_MISSING_WWW_HSTS=1 SUDO_USER=root /usr/local/sbin/gole-hostctl \
  deployment-verify-adopted-runtime "$expected_sha" >/tmp/www-hsts-drift.out 2>&1; then
  echo 'legacy adopted verifier accepted a www response without HSTS' >&2
  exit 1
fi
grep -q 'legacy HTTPS www response is missing HSTS' /tmp/www-hsts-drift.out

echo 'Legacy adopted transport runtime tests passed.'
CONTAINER_TEST
