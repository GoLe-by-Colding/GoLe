#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
IMAGE="ubuntu@sha256:33ceb71981b602c1a7443a53469e4dba065f7503eab3078a2d7a57a2ab987517"

docker run --rm --interactive --volume "$ROOT:/source:ro" "$IMAGE" bash -seu <<'CONTAINER_TEST'
new_sha=2222222222222222222222222222222222222222
request_id=10000000-0000-4000-8000-000000000001
compact_request_id="${request_id//-/}"
release="/var/lib/gole/releases/$new_sha"
state_root=/tmp/docker-state

install -d -m 0755 /etc/gole /usr/local/libexec/gole /usr/local/sbin /test-bin \
  /var/backups/gole-images /var/backups/gole-nginx /run/lock "$release/infra/gcp" \
  "$state_root/containers" "$state_root/networks" "$state_root/volumes" "$state_root/images"
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
install -m 0660 -o root -g root /dev/null /run/lock/gole-production-rollout.lock

install -m 0755 /source/infra/gcp/scripts/gole-hostctl.sh /usr/local/sbin/gole-hostctl
printf '#!/bin/sh\nexit 0\n' > /usr/local/libexec/gole/validate-production-env.py
printf '#!/bin/sh\ncat >/dev/null\n' > /usr/local/libexec/gole/validate-production-compose.py
printf '#!/bin/sh\nexit 0\n' > /usr/local/libexec/gole/verify-github-release.py
chmod 0755 /usr/local/libexec/gole/*.py

write_transaction() {
  cat > /etc/gole/deployment.transaction <<EOF
state=$1
target=all
request_id=$request_id
new_sha=$new_sha
previous_sha=0
EOF
  chmod 0600 /etc/gole/deployment.transaction
}

write_snapshot() {
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

write_nginx_transaction() {
  backup="/var/backups/gole-nginx/nginx.conf.$request_id"
  printf 'trusted-old-config\n' > "$backup"
  printf 'candidate-config\n' > /etc/gole/nginx.conf
  chmod 0600 "$backup"
  chmod 0644 /etc/gole/nginx.conf
  cat > /etc/gole/nginx.conf.transaction <<EOF
state=installed
request_id=$request_id
backup_file=$backup
candidate_sha256=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
deploy_sha=$new_sha
EOF
  chmod 0600 /etc/gole/nginx.conf.transaction
}

seed_docker_state() {
  rm -rf "$state_root/containers" "$state_root/networks" "$state_root/volumes" "$state_root/images"
  mkdir -p "$state_root/containers" "$state_root/networks" "$state_root/volumes" "$state_root/images"
  printf 'gole|backend|gole-backend\n' > "$state_root/containers/abcdefabcdef"
  printf 'gole|mongo-init|gole-mongo-init-1\n' > "$state_root/containers/123456789abc"
  printf 'gole|edge\n' > "$state_root/networks/gole_edge"
  printf 'gole|data\n' > "$state_root/networks/gole_data"
  for volume in \
    gole_mongo-data gole_redis-data gole_minio-data gole_certbot-webroot \
    gole_letsencrypt gole_budget-relay-state; do
    touch "$state_root/volumes/$volume"
  done
  for image in support-agent backend frontend budget-relay; do
    touch "$state_root/images/$image"
  done
}

cat > /test-bin/docker <<'FAKE_DOCKER'
#!/usr/bin/env bash
set -Eeuo pipefail
state_root=/tmp/docker-state
printf '%s\n' "$*" >> "$state_root/calls"

if [ "${1:-}" = compose ]; then
  if [[ " $* " == *' config --format json '* ]]; then
    printf '{}\n'
    exit 0
  fi
  if [[ " $* " == *' down --timeout 30 '* ]]; then
    [[ " $* " != *' -v '* ]] && [[ " $* " != *' --volumes '* ]] &&
      [[ " $* " != *' --remove-orphans '* ]] || exit 90
    grep -qx 'state=initial-reset-armed' /etc/gole/deployment.transaction || exit 91
    if [ -e "$state_root/fail-down-once" ]; then
      rm -f "$state_root/fail-down-once"
      exit 92
    fi
    rm -rf "$state_root/containers" "$state_root/networks"
    mkdir -p "$state_root/containers" "$state_root/networks"
    exit 0
  fi
  exit 1
fi

case "${1:-}" in
  ps)
    find "$state_root/containers" -mindepth 1 -maxdepth 1 -type f -printf '%f\n' | sort
    ;;
  inspect)
    object="${@: -1}"
    file="$state_root/containers/$object"
    if [ ! -f "$file" ]; then
      for candidate in "$state_root"/containers/*; do
        [ -f "$candidate" ] || continue
        IFS='|' read -r _ _ name < "$candidate"
        if [ "$name" = "$object" ]; then file="$candidate"; break; fi
      done
    fi
    [ -f "$file" ] || exit 1
    if [[ " $* " == *' --format '* ]]; then
      IFS='|' read -r project service name < "$file"
      if [[ "$*" == *'{{.Name}}'* ]]; then
        printf '%s|%s|/%s\n' "$project" "$service" "$name"
      else
        printf '%s|%s\n' "$project" "$service"
      fi
    fi
    ;;
  network)
    case "${2:-}" in
      ls) find "$state_root/networks" -mindepth 1 -maxdepth 1 -type f -printf '%f\n' | sort ;;
      inspect)
        name="${@: -1}"
        file="$state_root/networks/$name"
        [ -f "$file" ] || exit 1
        if [[ " $* " == *' --format '* ]]; then
          IFS='|' read -r project network < "$file"
          if [[ "$*" == *'{{.Name}}'* ]]; then
            printf '%s|%s|%s\n' "$name" "$project" "$network"
          else
            printf '%s|%s\n' "$project" "$network"
          fi
        fi
        ;;
      *) exit 1 ;;
    esac
    ;;
  volume)
    [ "${2:-}" = inspect ] || exit 1
    name="${@: -1}"
    [ -f "$state_root/volumes/$name" ] || exit 1
    printf '%s|local\n' "$name"
    ;;
  image)
    action="${2:-}"
    ref="${@: -1}"
    case "$ref" in
      gole/support-agent:local) key=support-agent ;;
      gole/backend:local) key=backend ;;
      gole/frontend:local) key=frontend ;;
      gole/budget-relay:local) key=budget-relay ;;
      gole/rollback-*:*) key=rollback ;;
      *) key=unknown ;;
    esac
    case "$action" in
      inspect) [ -f "$state_root/images/$key" ] ;;
      rm) rm -f "$state_root/images/$key" ;;
      *) exit 1 ;;
    esac
    ;;
  *) exit 1 ;;
esac
FAKE_DOCKER

cat > /test-bin/systemctl <<'FAKE_SYSTEMCTL'
#!/bin/sh
set -eu
if [ "${1:-}" = poweroff ]; then
  printf 'poweroff\n' >> /tmp/poweroff.calls
  exit 0
fi
exit 0
FAKE_SYSTEMCTL
chmod 0755 /test-bin/*
install -m 0755 /test-bin/docker /usr/local/bin/docker
export PATH="/test-bin:/usr/sbin:/usr/bin:/sbin:/bin"

# A failed first-deployment rollback must never report a nonexistent LKG as a
# success to deploy.sh. It fails nonzero, requests one poweroff, and preserves
# the root journal for the explicit reset operation.
write_transaction mutation-armed
write_snapshot
if SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-rollback "$request_id" \
  >/tmp/initial-rollback.out 2>&1; then
  echo 'initial mutation rollback falsely reported success' >&2
  exit 1
fi
[ "$(wc -l < /tmp/poweroff.calls)" -eq 1 ]
grep -qx 'state=mutation-armed' /etc/gole/deployment.transaction
rm -f /tmp/poweroff.calls

# Only root may authorize the destructive reset, and rejection happens before
# the fail-closed boundary without touching the transaction.
if SUDO_USER=goledeploy /usr/local/sbin/gole-hostctl deployment-reset-initial-failure \
  >/tmp/non-root-reset.out 2>&1; then
  echo 'deploy user was allowed to reset an initial deployment' >&2
  exit 1
fi
grep -qx 'state=mutation-armed' /etc/gole/deployment.transaction
[ ! -e /tmp/poweroff.calls ]

# An injected Compose-down failure leaves the durable reset state, retains all
# volumes and initial environment markers, and powers off exactly once.
write_nginx_transaction
seed_docker_state
touch "$state_root/fail-down-once"
if SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-reset-initial-failure \
  >/tmp/reset-failure.out 2>&1; then
  echo 'injected initial reset failure unexpectedly succeeded' >&2
  exit 1
fi
grep -qx 'state=initial-reset-armed' /etc/gole/deployment.transaction
[ ! -e /etc/gole/nginx.conf.transaction ]
grep -qx 'trusted-old-config' /etc/gole/nginx.conf
[ "$(wc -l < /tmp/poweroff.calls)" -eq 1 ]
for volume in \
  gole_mongo-data gole_redis-data gole_minio-data gole_certbot-webroot \
  gole_letsencrypt gole_budget-relay-state; do
  [ -f "$state_root/volumes/$volume" ]
done

# Retrying the same root-only operation is idempotent. It removes only the
# reviewed project containers/networks and local candidate tags, never volumes
# or the installed secret/initial authorization, then retires the journal last.
rm -f /tmp/poweroff.calls
SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-reset-initial-failure
[ ! -e /etc/gole/deployment.transaction ]
[ ! -e "/var/backups/gole-images/images.$compact_request_id" ]
[ -e /etc/gole/initial-deploy.pending ]
[ -e /etc/gole/gole.env ]
[ -e /etc/gole/gole.env.version ]
[ ! -e /tmp/poweroff.calls ]
[ ! -s <(find "$state_root/containers" -mindepth 1 -maxdepth 1 -print) ]
[ ! -s <(find "$state_root/networks" -mindepth 1 -maxdepth 1 -print) ]
for volume in \
  gole_mongo-data gole_redis-data gole_minio-data gole_certbot-webroot \
  gole_letsencrypt gole_budget-relay-state; do
  [ -f "$state_root/volumes/$volume" ]
done
for image in support-agent backend frontend budget-relay; do
  [ ! -e "$state_root/images/$image" ]
done
grep -Eq 'compose .* down --timeout 30$' "$state_root/calls"
if grep -Eq 'compose .* down .*(-v|--volumes|--remove-orphans)' "$state_root/calls"; then
  echo 'initial reset used a volume/orphan deletion option' >&2
  exit 1
fi

echo 'Initial deployment explicit reset runtime test passed.'
CONTAINER_TEST
