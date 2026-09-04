#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
IMAGE="python@sha256:9d2e5553305c7c7b0097999bb17187c69b921ccd6bc9d40e4bb5ebe652c00285"

docker run --rm --interactive \
  --volume "$ROOT:/source:ro" \
  "$IMAGE" bash -seu <<'CONTAINER_TEST'
expected_sha='0123456789abcdef0123456789abcdef01234567'
release="/var/lib/gole/releases/$expected_sha"
install -d -m 0755 /etc/gole /usr/local/libexec/gole /usr/local/sbin /test-bin \
  "$release/infra/gcp"
touch "$release/infra/gcp/docker-compose.yml"
printf '%s\n' "$expected_sha" > "$release/.gole-source-sha"
chown -R root:root /var/lib/gole
chmod -R go-w /var/lib/gole
printf 'root:root\n' > /etc/gole/deploy-user
printf 'MINIO_ROOT_USER=test\nMINIO_ROOT_PASSWORD=test-password\n' > /etc/gole/infra.env
install -m 0600 -o root -g root /source/infra/gcp/tests/fixtures/discord.env \
  /etc/gole/discord.env
install -m 0600 /source/infra/gcp/tests/fixtures/production.env /etc/gole/gole.env
chmod 0600 /etc/gole/infra.env /etc/gole/gole.env
printf '6\n' > /etc/gole/gole.env.version
chmod 0644 /etc/gole/gole.env.version
printf '%s\n' "$expected_sha" > /etc/gole/deployed.sha
chmod 0644 /etc/gole/deployed.sha
install -m 0755 /source/infra/gcp/scripts/gole-hostctl.sh /usr/local/sbin/gole-hostctl
printf '#!/bin/sh\nexit 0\n' > /usr/local/libexec/gole/validate-production-env.py
printf '#!/bin/sh\ncat >/dev/null\n' > /usr/local/libexec/gole/validate-production-compose.py
chmod 0755 /usr/local/libexec/gole/*.py

cat > /test-bin/docker <<'EOF'
#!/bin/sh
set -eu
printf '%s\n' "$*" >> /tmp/root-docker-calls
case "$1" in
  compose)
    case "$*" in
      *' config --format json') printf '{}\n' ;;
      *' exec -T mongo mongosh --quiet --norc --eval '*)
        if [ -f /tmp/fake-seller-gap-count ]; then cat /tmp/fake-seller-gap-count
        else printf '0\n'; fi
        ;;
      *) exit 0 ;;
    esac
    ;;
  inspect)
    if [ "${FAKE_UNHEALTHY:-0}" = 1 ]; then printf 'running:unhealthy\n';
    else printf 'running:healthy\n'; fi
    ;;
  exec) [ "$2" = gole-nginx ] ;;
  *) exit 91 ;;
esac
EOF
cat > /test-bin/curl <<'EOF'
#!/bin/sh
case "$*" in
  *'http://www.gole.co.kr/__gole-canonical-check?source=runtime'*)
    printf '301|https://gole.co.kr/__gole-canonical-check?source=runtime'
    ;;
  *'https://www.gole.co.kr/__gole-canonical-check?source=runtime'*)
    printf '301|https://gole.co.kr/__gole-canonical-check?source=runtime'
    ;;
  *'https://gole.co.kr/'*)
    printf 'HTTP/2 200\r\nStrict-Transport-Security: max-age=31536000\r\n\r\n'
    ;;
esac
exit 0
EOF
cat > /test-bin/systemctl <<'EOF'
#!/bin/sh
[ "$1" = is-active ] && [ "$2" = --quiet ] && [ "$3" = gole-cost-guard-watchdog.timer ]
EOF
chmod 0755 /test-bin/docker /test-bin/curl /test-bin/systemctl
install -m 0755 /test-bin/docker /usr/local/bin/docker
export PATH="/test-bin:$PATH"

output="$(SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-verify-runtime "$expected_sha")"
[ -z "$output" ]
[ "$(grep -c '^inspect --format' /tmp/root-docker-calls)" -eq 5 ]
for container in gole-backend gole-frontend gole-budget-relay gole-support-agent gole-nginx; do
  grep -Fq "$container" /tmp/root-docker-calls
done
if SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-verify-runtime \
  1111111111111111111111111111111111111111 >/dev/null 2>&1; then
  echo 'runtime verifier accepted the wrong LKG SHA' >&2
  exit 1
fi
if FAKE_UNHEALTHY=1 SUDO_USER=root /usr/local/sbin/gole-hostctl \
  deployment-verify-runtime "$expected_sha" >/dev/null 2>&1; then
  echo 'runtime verifier accepted an unhealthy container' >&2
  exit 1
fi

request_id='10000000-0000-4000-8000-000000000001'
cat > /etc/gole/deployment.transaction <<EOF
state=budget-updated
target=all
request_id=$request_id
new_sha=$expected_sha
previous_sha=$expected_sha
EOF
chmod 0600 /etc/gole/deployment.transaction
SUDO_USER=root /usr/local/sbin/gole-hostctl \
  deployment-verify-candidate-runtime "$expected_sha" "$request_id"
grep -Fq 'exec -T mongo mongosh --quiet --norc --eval' /tmp/root-docker-calls

sed -i 's/^state=verified$/state=budget-updated/' /etc/gole/deployment.transaction
printf '1\n' > /tmp/fake-seller-gap-count
if SUDO_USER=root /usr/local/sbin/gole-hostctl \
  deployment-verify-candidate-runtime "$expected_sha" "$request_id" >/tmp/preflight.out 2>&1; then
  echo 'candidate verifier accepted an active listing with incomplete seller identity' >&2
  exit 1
fi
grep -q 'active listings with incomplete verified seller identity remain: 1' /tmp/preflight.out
! grep -Eq 'sellerId|phoneNumber|phoneVerifiedAt|[0-9a-f]{24}' /tmp/preflight.out

echo 'Deployment runtime root-helper contract passed.'
CONTAINER_TEST
