#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
IMAGE="ubuntu@sha256:33ceb71981b602c1a7443a53469e4dba065f7503eab3078a2d7a57a2ab987517"

docker run --rm --interactive \
  --volume "$ROOT:/source:ro" \
  "$IMAGE" bash -seu <<'CONTAINER_TEST'
sha='0123456789abcdef0123456789abcdef01234567'
release="/var/lib/gole/releases/$sha"
install -d -m 0755 /etc/gole /usr/local/sbin /test-bin \
  "$release/infra/gcp" /var/backups/gole-nginx
printf 'root:root\n' > /etc/gole/deploy-user
printf 'old-config\n' > /etc/gole/nginx.conf
chmod 0644 /etc/gole/nginx.conf
install -m 0644 /source/infra/gcp/nginx-http.conf.template \
  "$release/infra/gcp/nginx-http.conf.template"
install -m 0644 /source/infra/gcp/nginx-https.conf.template \
  "$release/infra/gcp/nginx-https.conf.template"
printf '%s\n' "$sha" > "$release/.gole-source-sha"
chown -R root:root "$release"
chmod -R go-w "$release"
install -m 0755 /source/infra/gcp/scripts/gole-hostctl.sh /usr/local/sbin/gole-hostctl

cat > /test-bin/docker <<'FAKE_DOCKER'
#!/bin/sh
[ "$1" = run ] && [ "${FAKE_NGINX_INVALID:-0}" = 0 ]
FAKE_DOCKER
cat > /test-bin/sync <<'FAKE_SYNC'
#!/bin/sh
state="$(sed -n 's/^state=//p' /etc/gole/nginx.conf.transaction 2>/dev/null || true)"
printf '%s|state=%s\n' "$*" "$state" >> /tmp/nginx-sync.calls
if [ -e /tmp/kill-after-nginx-config-sync ] &&
  [ "$*" = '-f /etc/gole/nginx.conf' ]; then
  rm -f /tmp/kill-after-nginx-config-sync
  kill -KILL "$PPID"
fi
exit 0
FAKE_SYNC
chmod 0755 /test-bin/docker /test-bin/sync
export PATH="/test-bin:$PATH"

seed_deployment() {
  state="$1"
  request="$2"
  cat > /etc/gole/deployment.transaction <<EOF
state=$state
target=all
request_id=$request
new_sha=$sha
previous_sha=0000000000000000000000000000000000000000
EOF
  chmod 0600 /etc/gole/deployment.transaction
}

request_one='10000000-0000-4000-8000-000000000001'
compact="${request_one//-/}"
printf 'runner-controlled-config\n' > "/tmp/gole-nginx.$compact"
chmod 0600 "/tmp/gole-nginx.$compact"
seed_deployment built "$request_one"
SUDO_USER=root /usr/local/sbin/gole-hostctl nginx-transaction-begin "$request_one" "$sha"
! grep -q 'runner-controlled-config' /etc/gole/nginx.conf
grep -q 'server_name gole.co.kr' /etc/gole/nginx.conf
[ "$(stat -c '%U:%G:%a' /etc/gole/nginx.conf.transaction)" = 'root:root:600' ]
backup_sync_line="$(grep -nF -- "-f /var/backups/gole-nginx/nginx.conf.$request_one|state=" \
  /tmp/nginx-sync.calls | head -n 1 | cut -d: -f1)"
prepared_sync_line="$(grep -nF -- '-f /etc/gole/nginx.conf.transaction|state=prepared' \
  /tmp/nginx-sync.calls | head -n 1 | cut -d: -f1)"
config_sync_line="$(grep -nF -- '-f /etc/gole/nginx.conf|state=prepared' \
  /tmp/nginx-sync.calls | head -n 1 | cut -d: -f1)"
installed_sync_line="$(grep -nF -- '-f /etc/gole/nginx.conf.transaction|state=installed' \
  /tmp/nginx-sync.calls | head -n 1 | cut -d: -f1)"
[ "$backup_sync_line" -lt "$prepared_sync_line" ]
[ "$prepared_sync_line" -lt "$config_sync_line" ]
[ "$config_sync_line" -lt "$installed_sync_line" ]
SUDO_USER=root /usr/local/sbin/gole-hostctl nginx-transaction-abort "$request_one"
grep -qx 'old-config' /etc/gole/nginx.conf
SUDO_USER=root /usr/local/sbin/gole-hostctl nginx-transaction-finish-recovery "$request_one"

request_two='20000000-0000-4000-8000-000000000002'
seed_deployment built "$request_two"
SUDO_USER=root /usr/local/sbin/gole-hostctl nginx-transaction-begin "$request_two" "$sha"
touch /tmp/kill-after-nginx-config-sync
if SUDO_USER=root /usr/local/sbin/gole-hostctl nginx-transaction-recover \
  >/tmp/nginx-restore-kill.out 2>&1; then
  echo 'Nginx rollback survived the injected restored-config SIGKILL' >&2
  exit 1
fi
grep -qx 'state=installed' /etc/gole/nginx.conf.transaction
grep -qx old-config /etc/gole/nginx.conf
recovery="$(SUDO_USER=root /usr/local/sbin/gole-hostctl nginx-transaction-recover)"
[ "$recovery" = "RECOVERY_REQUIRED:$request_two" ]
grep -qx 'old-config' /etc/gole/nginx.conf
SUDO_USER=root /usr/local/sbin/gole-hostctl nginx-transaction-finish-recovery "$request_two"

request_three='30000000-0000-4000-8000-000000000003'
seed_deployment built "$request_three"
SUDO_USER=root /usr/local/sbin/gole-hostctl nginx-transaction-begin "$request_three" "$sha"
seed_deployment verified "$request_three"
SUDO_USER=root /usr/local/sbin/gole-hostctl nginx-transaction-commit "$request_three"
recovery="$(SUDO_USER=root /usr/local/sbin/gole-hostctl nginx-transaction-recover)"
[ "$recovery" = "RECOVERY_REQUIRED:$request_three" ]
grep -qx 'old-config' /etc/gole/nginx.conf
SUDO_USER=root /usr/local/sbin/gole-hostctl nginx-transaction-finish-recovery "$request_three"

request_four='40000000-0000-4000-8000-000000000004'
seed_deployment built "$request_four"
SUDO_USER=root /usr/local/sbin/gole-hostctl nginx-transaction-begin "$request_four" "$sha"
seed_deployment verified "$request_four"
SUDO_USER=root /usr/local/sbin/gole-hostctl nginx-transaction-commit "$request_four"
printf '%s\n' "$sha" > /etc/gole/deployed.sha
chmod 0644 /etc/gole/deployed.sha
seed_deployment runtime-verified "$request_four"
SUDO_USER=root /usr/local/sbin/gole-hostctl nginx-transaction-finalize "$request_four"
[ ! -e /etc/gole/nginx.conf.transaction ]
grep -q 'server_name gole.co.kr' /etc/gole/nginx.conf

# SIGKILL after the activated config fsync but before the installed journal
# write leaves prepared, so recovery must restore the durable backup.
request_five='50000000-0000-4000-8000-000000000005'
printf 'old-config\n' > /etc/gole/nginx.conf
seed_deployment built "$request_five"
touch /tmp/kill-after-nginx-config-sync
if SUDO_USER=root /usr/local/sbin/gole-hostctl nginx-transaction-begin \
  "$request_five" "$sha" >/tmp/nginx-config-kill.out 2>&1; then
  echo 'Nginx transaction survived the injected config-sync SIGKILL' >&2
  exit 1
fi
grep -qx 'state=prepared' /etc/gole/nginx.conf.transaction
recovery="$(SUDO_USER=root /usr/local/sbin/gole-hostctl nginx-transaction-recover)"
[ "$recovery" = "RECOVERY_REQUIRED:$request_five" ]
grep -qx old-config /etc/gole/nginx.conf
SUDO_USER=root /usr/local/sbin/gole-hostctl nginx-transaction-finish-recovery "$request_five"

echo 'Nginx root-owned release transaction runtime tests passed.'
CONTAINER_TEST
