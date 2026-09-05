#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
IMAGE="ubuntu@sha256:33ceb71981b602c1a7443a53469e4dba065f7503eab3078a2d7a57a2ab987517"

docker run --rm --interactive --volume "$ROOT:/source:ro" "$IMAGE" bash -seu <<'CONTAINER_TEST'
new_sha=2222222222222222222222222222222222222222
request_id=10000000-0000-4000-8000-000000000001
compact_request_id="${request_id//-/}"
release="/var/lib/gole/releases/$new_sha"

install -d -m 0755 /etc/gole /usr/local/libexec/gole /usr/local/sbin /test-bin \
  /var/backups/gole-images /var/backups/gole-nginx "$release/infra/gcp"
touch "$release/infra/gcp/docker-compose.yml"
printf '%s\n' "$new_sha" > "$release/.gole-source-sha"
chown -R root:root /var/lib/gole
chmod -R go-w /var/lib/gole
printf 'root:root\n' > /etc/gole/deploy-user
printf 'MINIO_ROOT_USER=test\nMINIO_ROOT_PASSWORD=test-password\n' > /etc/gole/infra.env
install -m 0600 /source/infra/gcp/tests/fixtures/production.env /etc/gole/gole.env
install -m 0600 /source/infra/gcp/tests/fixtures/discord.env /etc/gole/discord.env
printf '6\n' > /etc/gole/gole.env.version
chmod 0600 /etc/gole/infra.env /etc/gole/gole.env /etc/gole/discord.env
chmod 0644 /etc/gole/gole.env.version
env_hash="$(sha256sum /etc/gole/gole.env | cut -d' ' -f1)"
printf 'version=6\nenv_sha256=%s\n' "$env_hash" > /etc/gole/initial-deploy.pending
chmod 0600 /etc/gole/initial-deploy.pending

install -m 0755 /source/infra/gcp/scripts/gole-hostctl.sh /usr/local/sbin/gole-hostctl
printf '#!/bin/sh\nexit 0\n' > /usr/local/libexec/gole/validate-production-env.py
printf '#!/bin/sh\ncat >/dev/null\n' > /usr/local/libexec/gole/validate-production-compose.py
chmod 0755 /usr/local/libexec/gole/*.py

cat > /test-bin/docker <<'FAKE_DOCKER'
#!/bin/sh
printf '%s\n' "$*" >> /tmp/docker.calls
if [ "$1" = compose ]; then
  case "$*" in
    *' config --format json'*) printf '{}\n'; exit 0 ;;
    *' up '*)
      grep -qx 'state=mutation-armed' /etc/gole/deployment.transaction || exit 96
      touch /tmp/compose-mutation-attempted
      exit 97
      ;;
    *) exit 0 ;;
  esac
fi
if [ "$1" = image ] && [ "$2" = rm ]; then
  state="$(sed -n 's/^state=//p' /etc/gole/deployment.transaction 2>/dev/null || true)"
  printf 'image-rm|state=%s\n' "$state" >> /tmp/order.calls
  if [ -e /tmp/kill-cleanup ]; then
    rm -f /tmp/kill-cleanup
    hostctl_pid="$(ps -o ppid= -p "$PPID" | tr -d ' ')"
    kill -KILL "$hostctl_pid" "$PPID"
  fi
  exit 0
fi
if [ "$1" = inspect ]; then
  exit 1
fi
exit 0
FAKE_DOCKER
cat > /test-bin/systemctl <<'FAKE_SYSTEMCTL'
#!/bin/sh
case "$1" in
  is-active) exit 0 ;;
  poweroff) touch /tmp/poweroff-requested; exit 0 ;;
esac
exit 1
FAKE_SYSTEMCTL
cat > /test-bin/curl <<'FAKE_CURL'
#!/bin/sh
exit 0
FAKE_CURL
cat > /test-bin/sync <<'FAKE_SYNC'
#!/bin/sh
state="$(sed -n 's/^state=//p' /etc/gole/deployment.transaction 2>/dev/null || true)"
printf '%s|state=%s\n' "$*" "$state" >> /tmp/sync.calls
case "$*" in
  '-f /etc/gole/deployment.transaction') printf 'transaction-sync|state=%s\n' "$state" >> /tmp/order.calls ;;
esac
exit 0
FAKE_SYNC
chmod 0755 /test-bin/*
install -m 0755 /test-bin/docker /usr/local/bin/docker
export PATH="/test-bin:/usr/sbin:/usr/bin:/sbin:/bin"

write_initial_snapshot() {
  cat > "/var/backups/gole-images/images.$compact_request_id" <<EOF
target=all
request_id=$request_id
mode=initial
image_count=0
image.mongo=absent
image.mongo-init=absent
image.redis=absent
image.minio=absent
image.minio-init=absent
image.support-agent=absent
image.backend=absent
image.frontend=absent
image.nginx=absent
image.budget-relay=absent
EOF
  chmod 0600 "/var/backups/gole-images/images.$compact_request_id"
}

write_transaction() {
  state="$1"
  cat > /etc/gole/deployment.transaction <<EOF
state=$state
target=all
request_id=$request_id
new_sha=$new_sha
previous_sha=0
EOF
  chmod 0600 /etc/gole/deployment.transaction
}

write_installed_nginx_transaction() {
  backup="/var/backups/gole-nginx/nginx.conf.$request_id"
  printf 'old-config\n' > "$backup"
  chmod 0600 "$backup"
  cat > /etc/gole/nginx.conf.transaction <<EOF
state=installed
request_id=$request_id
backup_file=$backup
candidate_sha256=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
deploy_sha=$new_sha
EOF
  chmod 0600 /etc/gole/nginx.conf.transaction
}

# The snapshot manifest must reach durable storage before the deployment
# journal is allowed to advertise snapshotted.
rm -f "/var/backups/gole-images/images.$compact_request_id" /tmp/sync.calls
write_transaction prepared
SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-images-snapshot all "$request_id"
manifest_sync_line="$(grep -nF -- "-f /var/backups/gole-images/images.$compact_request_id|state=prepared" \
  /tmp/sync.calls | head -n 1 | cut -d: -f1)"
snapshot_state_line="$(grep -nF -- '-f /etc/gole/deployment.transaction|state=snapshotted' \
  /tmp/sync.calls | head -n 1 | cut -d: -f1)"
[ -n "$manifest_sync_line" ] && [ -n "$snapshot_state_line" ] &&
  [ "$manifest_sync_line" -lt "$snapshot_state_line" ]
SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-rollback "$request_id"
[ ! -e /etc/gole/deployment.transaction ]

# The journal must become mutation-armed before the first Compose up. The
# installed Nginx sub-transaction is mandatory; once present, the injected
# first mutation fails and leaves an unambiguous fail-closed state.
write_initial_snapshot
write_transaction nginx-installed
if SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-compose-up \
  rollout-all-apps "$new_sha" "$request_id" >/tmp/missing-nginx.out 2>&1; then
  echo 'rollout accepted a missing installed Nginx transaction' >&2
  exit 1
fi
grep -qx 'state=nginx-installed' /etc/gole/deployment.transaction
[ ! -e /tmp/compose-mutation-attempted ]
write_installed_nginx_transaction
if SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-compose-up \
  rollout-all-apps "$new_sha" "$request_id" >/tmp/mutation.out 2>&1; then
  echo 'injected Compose mutation unexpectedly succeeded' >&2
  exit 1
fi
grep -qx 'state=mutation-armed' /etc/gole/deployment.transaction
[ -e /tmp/compose-mutation-attempted ]
rm -f /etc/gole/nginx.conf.transaction \
  "/var/backups/gole-nginx/nginx.conf.$request_id"

# Before mutation-armed, rollback must not call Compose, stop, or recreate any
# service. Kill the helper immediately after rollback-restored is fsynced and
# prove that reboot recovery completes cleanup for previous_sha=0.
rm -f /tmp/docker.calls /tmp/compose-mutation-attempted /tmp/poweroff-requested /tmp/order.calls
write_initial_snapshot
write_transaction built
touch /tmp/kill-cleanup
if SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-rollback "$request_id" \
  >/tmp/rollback-cleanup-kill.out 2>&1; then
  echo 'rollback survived the injected terminal-cleanup SIGKILL' >&2
  exit 1
fi
grep -qx 'state=rollback-restored' /etc/gole/deployment.transaction
[ -e "/var/backups/gole-images/images.$compact_request_id" ]
rollback_state_line="$(grep -nF 'transaction-sync|state=rollback-restored' /tmp/order.calls |
  head -n 1 | cut -d: -f1)"
cleanup_line="$(grep -nF 'image-rm|state=rollback-restored' /tmp/order.calls |
  head -n 1 | cut -d: -f1)"
[ -n "$rollback_state_line" ] && [ -n "$cleanup_line" ] &&
  [ "$rollback_state_line" -lt "$cleanup_line" ]
if grep -Eq ' compose .* (up|run) | stop ' /tmp/docker.calls; then
  echo 'pre-mutation rollback recreated a container' >&2
  exit 1
fi
recovery="$(SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-recover)"
[ "$recovery" = RECOVERED ]
[ ! -e /etc/gole/deployment.transaction ]
[ ! -e "/var/backups/gole-images/images.$compact_request_id" ]
[ ! -e /tmp/poweroff-requested ]

# The public cleanup command must load the matching terminal transaction. It
# may not erase rollback provenance from an in-flight built deployment.
write_initial_snapshot
write_transaction built
if SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-images-cleanup \
  all "$request_id" >/tmp/unsafe-cleanup.out 2>&1; then
  echo 'direct image cleanup accepted a non-terminal deployment' >&2
  exit 1
fi
[ -e "/var/backups/gole-images/images.$compact_request_id" ]
sed -i 's/^state=built$/state=rollback-restored/' /etc/gole/deployment.transaction
SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-images-cleanup all "$request_id"
[ ! -e "/var/backups/gole-images/images.$compact_request_id" ]
[ -e /etc/gole/deployment.transaction ]
recovery="$(SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-recover)"
[ "$recovery" = RECOVERED ]
[ ! -e /etc/gole/deployment.transaction ]

echo 'Deployment mutation boundary and durable cleanup recovery tests passed.'
CONTAINER_TEST
