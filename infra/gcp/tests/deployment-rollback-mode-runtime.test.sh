#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
IMAGE="ubuntu@sha256:33ceb71981b602c1a7443a53469e4dba065f7503eab3078a2d7a57a2ab987517"

docker run --rm --interactive --volume "$ROOT:/source:ro" "$IMAGE" bash -seu <<'CONTAINER_TEST'
previous_sha=1111111111111111111111111111111111111111
new_sha=2222222222222222222222222222222222222222
request_id=10000000-0000-4000-8000-000000000001
compact_request_id="${request_id//-/}"
release="/var/lib/gole/releases/$previous_sha"
state_root=/tmp/docker-state

image_id() {
  local fill
  printf -v fill '%64s' ''
  fill="${fill// /$1}"
  printf 'sha256:%s\n' "$fill"
}

mongo_id="$(image_id a)"
redis_id="$(image_id b)"
minio_id="$(image_id c)"
minio_init_id="$(image_id d)"
backend_id="$(image_id e)"
frontend_id="$(image_id f)"
budget_id="$(image_id 1)"
nginx_id="$(image_id 2)"
support_id="$(image_id 3)"
conflict_id="$(image_id 4)"
candidate_id="$(image_id 8)"
drift_id="$(image_id 9)"

install -d -m 0755 /etc/gole /usr/local/libexec/gole /usr/local/sbin /test-bin \
  /var/backups/gole-images "$release/infra/gcp"
touch "$release/infra/gcp/docker-compose.yml"
printf '%s\n' "$previous_sha" > "$release/.gole-source-sha"
chown -R root:root /var/lib/gole
chmod -R go-w /var/lib/gole
printf 'root:root\n' > /etc/gole/deploy-user
printf 'MINIO_ROOT_USER=test\nMINIO_ROOT_PASSWORD=test-password\n' > /etc/gole/infra.env
install -m 0600 /source/infra/gcp/tests/fixtures/production.env /etc/gole/gole.env
install -m 0600 /source/infra/gcp/tests/fixtures/discord.env /etc/gole/discord.env
chmod 0600 /etc/gole/infra.env /etc/gole/gole.env /etc/gole/discord.env
install -m 0755 /source/infra/gcp/scripts/gole-hostctl.sh /usr/local/sbin/gole-hostctl
printf '#!/bin/sh\nexit 0\n' > /usr/local/libexec/gole/validate-production-env.py
cat > /usr/local/libexec/gole/validate-production-compose.py <<'EOF'
#!/bin/sh
printf '%s\n' "$*" >> /tmp/validator.calls
cat >/dev/null
EOF
chmod 0755 /usr/local/libexec/gole/*.py

# The pinned Ubuntu fixture deliberately has no Python runtime. These hostctl
# paths only ask Python to test service membership or return a service image,
# so model those two operations without broadening the test image.
cat > /test-bin/python3 <<'EOF'
#!/bin/bash
set -eu
[ "$1" = - ]
model="$2"
service="$3"
program="$(cat)"
if [[ "$program" == *'get("image")'* ]]; then
  awk -F'|' -v service="$service" '$1 == service { print $2; found=1; exit } END { exit !found }' "$model"
else
  awk -F'|' -v service="$service" '$1 == service { found=1 } END { exit !found }' "$model"
fi
EOF

cat > /test-bin/docker <<'EOF'
#!/bin/bash
set -eu

state_root=/tmp/docker-state
mkdir -p "$state_root/refs" "$state_root/services"
printf '%s\n' "$*" >> "$state_root/docker.calls"

ref_file() {
  local key
  key="$(printf '%s' "$1" | sha256sum | cut -d' ' -f1)"
  printf '%s/refs/%s\n' "$state_root" "$key"
}

resolve_ref() {
  local file ref="$1"
  if [[ "$ref" =~ ^sha256:[0-9a-f]{64}$ ]]; then
    printf '%s\n' "$ref"
    return
  fi
  file="$(ref_file "$ref")"
  [ -f "$file" ] || return 1
  cat "$file"
}

store_ref() {
  local file
  file="$(ref_file "$1")"
  printf '%s\n' "$2" > "$file"
  printf '%s\n' "$1" > "$file.name"
}

find_service() {
  local directory object="$1"
  for directory in "$state_root"/services/*; do
    [ -d "$directory" ] || continue
    if [ "$(cat "$directory/id")" = "$object" ] ||
      [ "$(cat "$directory/name")" = "$object" ]; then
      basename "$directory"
      return
    fi
  done
  return 1
}

override_image() {
  local override="$1" service="$2"
  awk -v service="$service" '
    $0 == "  " service ":" { selected=1; next }
    selected && /^  [^ ]/ { exit }
    selected && $1 == "image:" { print $2; found=1; exit }
    END { exit !found }
  ' "$override"
}

if [ "${1:-}" = compose ]; then
  shift
  arguments=("$@")
  compose_action=""
  compose_override=""
  compose_files=()
  for ((index=0; index<${#arguments[@]}; index++)); do
    case "${arguments[$index]}" in
      -f)
        index=$((index + 1))
        compose_files+=("${arguments[$index]}")
        ;;
      config|ps|pull|up|run) compose_action="${arguments[$index]}" ;;
    esac
  done
  compose_service="${arguments[${#arguments[@]}-1]}"
  if [ "${#compose_files[@]}" -gt 1 ]; then
    compose_override="${compose_files[${#compose_files[@]}-1]}"
  fi
  model_file="$state_root/model"
  if [[ "${compose_files[0]:-}" == *'/releases/2222222222222222222222222222222222222222/'* ]] &&
    [ -f "$state_root/candidate-model" ]; then
    model_file="$state_root/candidate-model"
  fi
  printf 'action=%s service=%s override=%s\n' \
    "$compose_action" "$compose_service" "$compose_override" >> "$state_root/compose.calls"
  case "$compose_action" in
    config)
      if [[ " $* " == *' --services '* ]]; then
        cut -d'|' -f1 "$model_file"
      else
        cat "$model_file"
      fi
      ;;
    ps)
      if [ -f "$state_root/services/$compose_service/id" ]; then
        cat "$state_root/services/$compose_service/id"
      fi
      ;;
    up)
      printf 'up-%s\n' "$compose_service" >> "$state_root/transaction.events"
      if [ -n "$compose_override" ]; then
        [ -f "$compose_override" ]
        cp "$compose_override" "$state_root/last-override"
        printf '%s\n' "$compose_override" >> "$state_root/override.paths"
        image_ref="$(override_image "$compose_override" "$compose_service")"
      else
        image_ref="$(awk -F'|' -v service="$compose_service" '$1 == service {print $2; exit}' "$model_file")"
      fi
      resolved="$(resolve_ref "$image_ref")"
      if [ -f "$state_root/drift-service" ] &&
        [ "$(cat "$state_root/drift-service")" = "$compose_service" ]; then
        resolved="$(cat "$state_root/drift-image")"
      fi
      mkdir -p "$state_root/services/$compose_service"
      printf '%s\n' "$resolved" > "$state_root/services/$compose_service/image"
      case "$compose_service" in
        mongo-init|minio-init)
          printf 'exited\n' > "$state_root/services/$compose_service/status"
          printf 'missing\n' > "$state_root/services/$compose_service/health"
          printf 'false\n' > "$state_root/services/$compose_service/running"
          printf '0\n' > "$state_root/services/$compose_service/exit-code"
          ;;
        *)
          printf 'running\n' > "$state_root/services/$compose_service/status"
          printf 'healthy\n' > "$state_root/services/$compose_service/health"
          printf 'true\n' > "$state_root/services/$compose_service/running"
          ;;
      esac
      ;;
    pull)
      printf 'pull\n' >> "$state_root/transaction.events"
      exit 0
      ;;
    *) exit 1 ;;
  esac
  exit 0
fi

case "${1:-}" in
  image)
    case "${2:-}" in
      inspect)
        printf 'resolve-%s\n' "${@: -1}" >> "$state_root/transaction.events"
        resolve_ref "${@: -1}"
        ;;
      tag) store_ref "$4" "$(resolve_ref "$3")" ;;
      rm)
        file="$(ref_file "${@: -1}")"
        rm -f "$file" "$file.name"
        if [ -e "$state_root/kill-on-image-cleanup" ]; then
          rm -f "$state_root/kill-on-image-cleanup"
          hostctl_pid="$(ps -o ppid= -p "$PPID" | tr -d ' ')"
          kill -KILL "$hostctl_pid" "$PPID"
        fi
        ;;
      *) exit 1 ;;
    esac
    ;;
  inspect)
    object="${@: -1}"
    service="$(find_service "$object")" || exit 1
    format=""
    for ((index=1; index<=$#; index++)); do
      if [ "${!index}" = --format ]; then
        next=$((index + 1))
        format="${!next}"
      fi
    done
    case "$format" in
      *com.docker.compose.project*) cat "$state_root/services/$service/labels" ;;
      *'.State.ExitCode'*)
        [ "$service" != minio-init ] ||
          printf 'verify-minio-init\n' >> "$state_root/transaction.events"
        printf '%s:%s\n' \
          "$(cat "$state_root/services/$service/status")" \
          "$(cat "$state_root/services/$service/exit-code")"
        ;;
      *'.State.Health'*)
        printf '%s:%s\n' \
          "$(cat "$state_root/services/$service/status")" \
          "$(cat "$state_root/services/$service/health")"
        ;;
      *'.State.Running'*)
        printf 'quiesce-proof-%s\n' "$service" >> "$state_root/transaction.events"
        cat "$state_root/services/$service/running"
        ;;
      *'{{.Image}}'*) cat "$state_root/services/$service/image" ;;
      '') exit 0 ;;
      *) exit 1 ;;
    esac
    ;;
  stop)
    service="$(find_service "${@: -1}")" || exit 1
    printf 'stop-%s\n' "$service" >> "$state_root/transaction.events"
    printf 'false\n' > "$state_root/services/$service/running"
    printf 'exited\n' > "$state_root/services/$service/status"
    ;;
  rm)
    service="$(find_service "${@: -1}")" || exit 1
    rm -rf "$state_root/services/$service"
    ;;
  exec) exit 0 ;;
  *) exit 1 ;;
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
case "$1" in
  poweroff) touch /tmp/poweroff-requested ;;
  *) exit 0 ;;
esac
EOF
cat > /test-bin/iptables <<'EOF'
#!/bin/sh
case " $* " in
  *' -t raw -C '*) exit 1 ;;
  *' -C '*) exit 0 ;;
esac
exit 1
EOF
cp /test-bin/iptables /test-bin/ip6tables
chmod 0755 /test-bin/*
install -m 0755 /test-bin/docker /usr/local/bin/docker
export PATH="/test-bin:/usr/sbin:/usr/bin:/sbin:/bin"

container_id_for() {
  case "$1" in
    mongo) printf 'a00000000001\n' ;;
    mongo-init) printf 'a00000000002\n' ;;
    redis) printf 'a00000000003\n' ;;
    minio) printf 'a00000000004\n' ;;
    minio-init) printf 'a00000000005\n' ;;
    support-agent) printf 'a00000000006\n' ;;
    backend) printf 'a00000000007\n' ;;
    frontend) printf 'a00000000008\n' ;;
    nginx) printf 'a00000000009\n' ;;
    budget-relay) printf 'a0000000000a\n' ;;
  esac
}

ref_file() {
  local key
  key="$(printf '%s' "$1" | sha256sum | cut -d' ' -f1)"
  printf '%s/refs/%s\n' "$state_root" "$key"
}

set_ref() {
  local file
  file="$(ref_file "$1")"
  printf '%s\n' "$2" > "$file"
  printf '%s\n' "$1" > "$file.name"
}

assert_ref() {
  local file
  file="$(ref_file "$1")"
  [ "$(cat "$file")" = "$2" ] || {
    echo "canonical image reference was not restored: $1" >&2
    exit 1
  }
}

set_service() {
  local exit_code=0 health=healthy image="$2" labels="gole|$1" running=true status=running
  local service="$1"
  case "$service" in
    mongo-init|minio-init)
      status=exited
      health=missing
      running=false
      ;;
  esac
  install -d -m 0755 "$state_root/services/$service"
  container_id_for "$service" > "$state_root/services/$service/id"
  printf 'gole-%s\n' "$service" > "$state_root/services/$service/name"
  printf '%s\n' "$image" > "$state_root/services/$service/image"
  printf '%s\n' "$labels" > "$state_root/services/$service/labels"
  printf '%s\n' "$status" > "$state_root/services/$service/status"
  printf '%s\n' "$health" > "$state_root/services/$service/health"
  printf '%s\n' "$running" > "$state_root/services/$service/running"
  printf '%s\n' "$exit_code" > "$state_root/services/$service/exit-code"
}

write_model() {
  local mode="$1"
  cat > "$state_root/model" <<EOF
mongo|mongo:7
mongo-init|mongo:7
redis|redis:7
minio|minio/minio:latest
minio-init|minio/mc:latest
backend|gole/backend:local
frontend|gole/frontend:local
nginx|nginx:1.29-alpine
budget-relay|gole/budget-relay:local
EOF
  if [ "$mode" = strict ]; then
    printf 'support-agent|gole/support-agent:local\n' >> "$state_root/model"
  fi
}

seed_lkg_runtime() {
  local mode="$1"
  set_service mongo "$mongo_id"
  set_service mongo-init "$mongo_id"
  set_service redis "$redis_id"
  set_service minio "$minio_id"
  set_service minio-init "$minio_init_id"
  set_service backend "$backend_id"
  set_service frontend "$frontend_id"
  set_service nginx "$nginx_id"
  set_service budget-relay "$budget_id"
  if [ "$mode" = strict ]; then
    set_service support-agent "$support_id"
  fi
  set_ref mongo:7 "$mongo_id"
  set_ref redis:7 "$redis_id"
  set_ref minio/minio:latest "$minio_id"
  set_ref minio/mc:latest "$minio_init_id"
  set_ref gole/backend:local "$backend_id"
  set_ref gole/frontend:local "$frontend_id"
  set_ref nginx:1.29-alpine "$nginx_id"
  set_ref gole/budget-relay:local "$budget_id"
  if [ "$mode" = strict ]; then
    set_ref gole/support-agent:local "$support_id"
  fi
}

setup_snapshot() {
  local mode="$1"
  rm -rf "$state_root"
  install -d -m 0755 "$state_root/refs" "$state_root/services"
  rm -f /tmp/validator.calls /tmp/poweroff-requested \
    /etc/gole/metadata-migration.pending /etc/gole/deployment.transaction
  rm -f /var/backups/gole-data/MINIO_UNFREEZE_REQUIRED
  rm -f /var/backups/gole-images/images.* /var/backups/gole-images/data-upgrade.*
  printf '%s\n' "$previous_sha" > /etc/gole/deployed.sha
  chmod 0644 /etc/gole/deployed.sha
  cat > /etc/gole/deployment.transaction <<EOF
state=prepared
target=all
request_id=$request_id
new_sha=$new_sha
previous_sha=$previous_sha
EOF
  chmod 0600 /etc/gole/deployment.transaction
  if [ "$mode" = legacy ]; then
    printf 'state=pending\nlegacy_sha=%s\n' "$previous_sha" > /etc/gole/metadata-migration.pending
    chmod 0644 /etc/gole/metadata-migration.pending
  fi
  write_model "$mode"
  seed_lkg_runtime "$mode"
}

mutate_runtime() {
  local mode="$1" reference service
  # Snapshotting alone is still pre-mutation and must never restart services.
  # Model a failed rollout only after the durable transaction crossed that
  # boundary, otherwise hostctl correctly takes its tag-only recovery path.
  sed -i 's/^state=snapshotted$/state=mutated/' /etc/gole/deployment.transaction
  while IFS='|' read -r service reference; do
    if [ "$mode" = strict ]; then
      case "$service" in mongo|mongo-init|redis|minio|minio-init) continue ;; esac
    fi
    set_ref "$reference" "$candidate_id"
    case "$service" in
      mongo-init|minio-init) ;;
      *) printf '%s\n' "$candidate_id" > "$state_root/services/$service/image" ;;
    esac
  done < "$state_root/model"
  if [ "$mode" = legacy ]; then
    set_service support-agent "$candidate_id"
    set_ref gole/support-agent:local "$candidate_id"
  fi
  printf '%s\n' "$new_sha" > /etc/gole/deployed.sha
}

assert_manifest_line() {
  grep -Fqx "$1" "$2" || {
    echo "missing exact image manifest line: $1" >&2
    exit 1
  }
}

assert_legacy_canonical_refs() {
  assert_ref mongo:7 "$mongo_id"
  assert_ref redis:7 "$redis_id"
  assert_ref minio/minio:latest "$minio_id"
  assert_ref minio/mc:latest "$minio_init_id"
  assert_ref gole/backend:local "$backend_id"
  assert_ref gole/frontend:local "$frontend_id"
  assert_ref nginx:1.29-alpine "$nginx_id"
  assert_ref gole/budget-relay:local "$budget_id"
}

dump_failure_context() {
  local status=$?
  trap - ERR
  [ "$status" -eq 0 ] && return
  printf '%s\n' '--- fake Docker calls ---' >&2
  cat "$state_root/docker.calls" >&2 2>/dev/null || true
  printf '%s\n' '--- fake Compose calls ---' >&2
  cat "$state_root/compose.calls" >&2 2>/dev/null || true
  printf '%s\n' '--- rollback override ---' >&2
  cat "$state_root/last-override" >&2 2>/dev/null || true
  printf '%s\n' '--- service images ---' >&2
  for image_file in "$state_root"/services/*/image; do
    [ -f "$image_file" ] || continue
    printf '%s=%s\n' "$(basename "$(dirname "$image_file")")" "$(cat "$image_file")" >&2
  done
  printf '%s\n' '--- captured failure ---' >&2
  cat /tmp/image-drift.out >&2 2>/dev/null || true
  exit "$status"
}
trap dump_failure_context ERR

# The first strict main CD starts from the exact legacy-adoption snapshot mode.
# It must replace every data service/initializer before every application
# service so the new limits, pinned images and isolated networks reach the live
# containers rather than remaining only in the rendered candidate model.
setup_snapshot legacy
SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-images-snapshot all "$request_id"
candidate_release="/var/lib/gole/releases/$new_sha"
install -d -m 0755 "$candidate_release/infra/gcp"
touch "$candidate_release/infra/gcp/docker-compose.yml"
printf '%s\n' "$new_sha" > "$candidate_release/.gole-source-sha"
chown -R root:root "$candidate_release"
chmod -R go-w "$candidate_release"
cp "$state_root/model" "$state_root/candidate-model"
printf 'support-agent|gole/support-agent:local\n' >> "$state_root/candidate-model"
set_ref gole/support-agent:local "$candidate_id"
printf 'current-nginx\n' > /etc/gole/nginx.conf
chmod 0644 /etc/gole/nginx.conf
nginx_backup="/var/backups/gole-nginx/nginx.conf.$request_id"
install -d -m 0700 /var/backups/gole-nginx
printf 'previous-nginx\n' > "$nginx_backup"
chmod 0600 "$nginx_backup"
nginx_hash="$(sha256sum /etc/gole/nginx.conf | cut -d' ' -f1)"
cat > /etc/gole/nginx.conf.transaction <<EOF
state=installed
request_id=$request_id
backup_file=$nginx_backup
candidate_sha256=$nginx_hash
deploy_sha=$new_sha
EOF
chmod 0600 /etc/gole/nginx.conf.transaction
sed -i 's/^state=snapshotted$/state=nginx-installed/' /etc/gole/deployment.transaction
: > "$state_root/compose.calls"
: > "$state_root/docker.calls"
: > "$state_root/transaction.events"
SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-compose-up \
  rollout-all-apps "$new_sha" "$request_id"
grep -qx 'state=mutated' /etc/gole/deployment.transaction
previous_line=0
for service in mongo redis minio mongo-init minio-init support-agent backend frontend; do
  current_line="$(grep -nF "action=up service=$service " \
    "$state_root/compose.calls" | head -1 | cut -d: -f1)"
  [ -n "$current_line" ] && [ "$current_line" -gt "$previous_line" ]
  previous_line="$current_line"
  case "$service" in
    mongo-init|minio-init)
      grep -E "compose .* up .*--no-deps .*--force-recreate .*--abort-on-container-exit .* $service$" \
        "$state_root/docker.calls" >/dev/null
      ;;
    *)
      grep -E "compose .* up .*--no-deps .*--force-recreate .*--wait $service$" \
        "$state_root/docker.calls" >/dev/null
      ;;
  esac
done
[ -d "$state_root/services/support-agent" ]
rm -f /etc/gole/nginx.conf.transaction "$nginx_backup"

# Legacy adoption snapshots every data/app service by its Compose container,
# records support-agent as absent, then restores mutable canonical tags and
# starts every service through one request-scoped rollback override.
setup_snapshot legacy
SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-images-snapshot all "$request_id"
legacy_marker="/var/backups/gole-images/images.$compact_request_id"
assert_manifest_line 'target=all' "$legacy_marker"
assert_manifest_line 'mode=legacy-adoption' "$legacy_marker"
assert_manifest_line 'image_count=9' "$legacy_marker"
assert_manifest_line "image.mongo=$mongo_id" "$legacy_marker"
assert_manifest_line "image.mongo-init=$mongo_id" "$legacy_marker"
assert_manifest_line "image.redis=$redis_id" "$legacy_marker"
assert_manifest_line "image.minio=$minio_id" "$legacy_marker"
assert_manifest_line "image.minio-init=$minio_init_id" "$legacy_marker"
assert_manifest_line 'image.support-agent=absent' "$legacy_marker"
assert_manifest_line "image.backend=$backend_id" "$legacy_marker"
assert_manifest_line "image.frontend=$frontend_id" "$legacy_marker"
assert_manifest_line "image.nginx=$nginx_id" "$legacy_marker"
assert_manifest_line "image.budget-relay=$budget_id" "$legacy_marker"
[ "$(grep -c '^image\.' "$legacy_marker")" -eq 10 ]
for service in mongo mongo-init redis minio minio-init backend frontend nginx budget-relay; do
  grep -Fq "action=ps service=$service " "$state_root/compose.calls"
  grep -Fq "inspect --format {{index .Config.Labels \"com.docker.compose.project\"}}|{{index .Config.Labels \"com.docker.compose.service\"}} $(container_id_for "$service")" \
    "$state_root/docker.calls"
done
if grep -Fq 'action=ps service=support-agent ' "$state_root/compose.calls"; then
  echo 'legacy absent support-agent was resolved from a guessed container' >&2
  exit 1
fi
mutate_runtime legacy
: > "$state_root/compose.calls"
: > "$state_root/docker.calls"
SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-rollback "$request_id"
[ "$(cat /etc/gole/deployed.sha)" = "$previous_sha" ]
[ ! -e /tmp/poweroff-requested ]
[ ! -e /etc/gole/deployment.transaction ]
[ ! -e "$legacy_marker" ]
[ ! -d "$state_root/services/support-agent" ]
assert_legacy_canonical_refs
[ "$(cat "$state_root/services/mongo/image")" = "$mongo_id" ]
[ "$(cat "$state_root/services/redis/image")" = "$redis_id" ]
[ "$(cat "$state_root/services/minio/image")" = "$minio_id" ]
[ "$(cat "$state_root/services/backend/image")" = "$backend_id" ]
[ "$(cat "$state_root/services/frontend/image")" = "$frontend_id" ]
[ "$(cat "$state_root/services/nginx/image")" = "$nginx_id" ]
[ "$(cat "$state_root/services/budget-relay/image")" = "$budget_id" ]
for service in mongo mongo-init redis minio minio-init backend frontend nginx budget-relay; do
  grep -Fq "  $service:" "$state_root/last-override"
  grep -Fq "image: gole/rollback-$service:$compact_request_id" "$state_root/last-override"
done
if grep -Fq '  support-agent:' "$state_root/last-override"; then
  echo 'legacy absent support-agent leaked into the rollback override' >&2
  exit 1
fi
[ "$(sort -u "$state_root/override.paths" | wc -l)" -eq 1 ]
grep -Fq 'action=up service=mongo ' "$state_root/compose.calls"
grep -Fq 'action=up service=mongo-init ' "$state_root/compose.calls"
grep -Fq 'action=up service=minio-init ' "$state_root/compose.calls"
grep -q -- '--allow-legacy-adoption' /tmp/validator.calls

# A power loss after the previous image IDs/health and LKG marker are restored
# must leave rollback-restored durable before any request-scoped tag cleanup.
# Recovery revalidates the previous runtime and resumes cleanup only.
setup_snapshot legacy
SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-images-snapshot all "$request_id"
rollback_kill_marker="/var/backups/gole-images/images.$compact_request_id"
mutate_runtime legacy
touch "$state_root/kill-on-image-cleanup"
if SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-rollback "$request_id" \
  >/tmp/rollback-cleanup-kill.out 2>&1; then
  echo 'rollback survived the injected terminal-cleanup SIGKILL' >&2
  exit 1
fi
grep -qx 'state=rollback-restored' /etc/gole/deployment.transaction
[ "$(cat /etc/gole/deployed.sha)" = "$previous_sha" ]
[ -e "$rollback_kill_marker" ]
recovery="$(SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-recover)"
[ "$recovery" = RECOVERED ]
[ ! -e /etc/gole/deployment.transaction ]
[ ! -e "$rollback_kill_marker" ]
[ ! -e /tmp/poweroff-requested ]

# A failed build can move mutable tags before any container mutation. The
# snapshotted/built journal path must restore only those tags and leave every
# running service, especially the data plane, untouched.
setup_snapshot legacy
SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-images-snapshot all "$request_id"
sed -i 's/^state=snapshotted$/state=built/' /etc/gole/deployment.transaction
while IFS='|' read -r _ reference; do
  set_ref "$reference" "$candidate_id"
done < "$state_root/model"
: > "$state_root/compose.calls"
SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-rollback "$request_id"
[ "$(cat /etc/gole/deployed.sha)" = "$previous_sha" ]
[ ! -e /tmp/poweroff-requested ]
[ ! -e /etc/gole/deployment.transaction ]
assert_legacy_canonical_refs
if grep -Eq 'action=(up|run) service=' "$state_root/compose.calls"; then
  echo 'pre-mutation rollback recreated a service' >&2
  exit 1
fi
[ "$(cat "$state_root/services/mongo/image")" = "$mongo_id" ]
[ "$(cat "$state_root/services/backend/image")" = "$backend_id" ]

# Strict snapshots retain data-plane image provenance, but an ordinary strict
# rollback must not recreate Mongo, Redis, MinIO or either initializer when no
# digest-change transaction marker exists.
setup_snapshot strict
SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-images-snapshot all "$request_id"
strict_marker="/var/backups/gole-images/images.$compact_request_id"
assert_manifest_line 'mode=strict' "$strict_marker"
assert_manifest_line 'image_count=10' "$strict_marker"
assert_manifest_line "image.mongo=$mongo_id" "$strict_marker"
assert_manifest_line "image.mongo-init=$mongo_id" "$strict_marker"
assert_manifest_line "image.redis=$redis_id" "$strict_marker"
assert_manifest_line "image.minio=$minio_id" "$strict_marker"
assert_manifest_line "image.minio-init=$minio_init_id" "$strict_marker"
assert_manifest_line "image.support-agent=$support_id" "$strict_marker"
assert_manifest_line "image.backend=$backend_id" "$strict_marker"
assert_manifest_line "image.frontend=$frontend_id" "$strict_marker"
assert_manifest_line "image.nginx=$nginx_id" "$strict_marker"
assert_manifest_line "image.budget-relay=$budget_id" "$strict_marker"
[ "$(grep -c '^image\.' "$strict_marker")" -eq 10 ]
for service in mongo mongo-init redis minio minio-init support-agent backend frontend nginx budget-relay; do
  grep -Fq "action=ps service=$service " "$state_root/compose.calls"
done
mutate_runtime strict
: > "$state_root/compose.calls"
: > "$state_root/docker.calls"
SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-rollback "$request_id"
[ "$(cat /etc/gole/deployed.sha)" = "$previous_sha" ]
[ ! -e /tmp/poweroff-requested ]
assert_ref gole/support-agent:local "$support_id"
assert_ref gole/backend:local "$backend_id"
assert_ref gole/frontend:local "$frontend_id"
assert_ref nginx:1.29-alpine "$nginx_id"
assert_ref gole/budget-relay:local "$budget_id"
for service in support-agent backend frontend nginx budget-relay; do
  grep -Fq "  $service:" "$state_root/last-override"
done
for service in mongo mongo-init redis minio minio-init; do
  grep -Fq "  $service:" "$state_root/last-override"
done
if grep -Eq 'action=(up|run) service=(mongo|mongo-init|redis|minio|minio-init) ' \
  "$state_root/compose.calls"; then
  echo 'strict rollback recreated a data-plane service' >&2
  exit 1
fi
if grep -q -- '--allow-legacy-adoption' /tmp/validator.calls; then
  echo 'strict rollback weakened Compose validation' >&2
  exit 1
fi

# Once a strict data-image mutation is durably armed, old images alone are not
# an exact rollback: storage engines or initializers may already have changed
# volume semantics. The automatic path must close writes, retain the exact
# backup and all transaction evidence, and power off for explicit root restore.
setup_snapshot strict
SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-images-snapshot all "$request_id"
backup_path=/var/backups/gole-data/20260904T120000Z
install -d -m 0700 "$backup_path"
printf 'mongo-backup-payload\n' > "$backup_path/mongo.archive.gz"
printf 'minio-backup-payload\n' > "$backup_path/minio.tar.gz"
printf 'redis-backup-payload\n' > "$backup_path/redis.tar.gz"
(cd "$backup_path" && sha256sum mongo.archive.gz minio.tar.gz redis.tar.gz > SHA256SUMS)
printf 'format=gole-logical-backup-v1\ncreated_at=20260904T120000Z\n' > "$backup_path/COMPLETE"
chmod 0600 "$backup_path"/*
cat > "/var/backups/gole-images/data-upgrade.$compact_request_id" <<EOF
state=mutation-armed
request_id=$request_id
backup_path=$backup_path
change.mongo=true
candidate.mongo=$candidate_id
change.mongo-init=true
candidate.mongo-init=$candidate_id
change.redis=true
candidate.redis=$candidate_id
change.minio=true
candidate.minio=$candidate_id
change.minio-init=true
candidate.minio-init=$candidate_id
EOF
chmod 0600 "/var/backups/gole-images/data-upgrade.$compact_request_id"
sed -i 's/^state=snapshotted$/state=mutated/' /etc/gole/deployment.transaction
for service in mongo mongo-init redis minio minio-init; do
  printf '%s\n' "$candidate_id" > "$state_root/services/$service/image"
done
: > "$state_root/compose.calls"
if SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-rollback "$request_id" \
  >/tmp/mutated-data-rollback.out 2>&1; then
  echo 'mutated data plane was falsely reported as automatically recovered' >&2
  exit 1
fi
grep -q 'requires explicit logical restore' /tmp/mutated-data-rollback.out
[ -e /tmp/poweroff-requested ]
for service in support-agent backend frontend nginx; do
  [ "$(cat "$state_root/services/$service/running")" = false ]
done
if grep -Eq 'action=up service=(mongo|mongo-init|redis|minio|minio-init|support-agent|backend|frontend|nginx) ' \
  "$state_root/compose.calls"; then
  echo 'mutated data rollback recreated a service' >&2
  exit 1
fi
[ "$(cat "$state_root/services/mongo/image")" = "$candidate_id" ]
[ -e "/var/backups/gole-images/data-upgrade.$compact_request_id" ]
[ -e "/var/backups/gole-images/images.$compact_request_id" ]
[ -e /etc/gole/deployment.transaction ]
[ -e "$backup_path/COMPLETE" ]

# The forward strict path detects changed pinned references itself, creates and
# validates a fresh logical backup before mutation-armed, then updates data in
# mongo/redis/minio order and preserves exited initializer provenance.
setup_snapshot strict
SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-images-snapshot all "$request_id"
candidate_release="/var/lib/gole/releases/$new_sha"
install -d -m 0755 "$candidate_release/infra/gcp" /var/backups/gole-data
touch "$candidate_release/infra/gcp/docker-compose.yml"
printf '%s\n' "$new_sha" > "$candidate_release/.gole-source-sha"
chown -R root:root "$candidate_release"
chmod -R go-w "$candidate_release"
mongo_ref="mongo:7@sha256:$(printf '1%.0s' {1..64})"
redis_ref="redis:7@sha256:$(printf '2%.0s' {1..64})"
minio_ref="minio/minio@sha256:$(printf '3%.0s' {1..64})"
mc_ref="minio/mc:latest@sha256:$(printf '4%.0s' {1..64})"
cat > "$state_root/candidate-model" <<EOF
mongo|$mongo_ref
mongo-init|$mongo_ref
redis|$redis_ref
minio|$minio_ref
minio-init|$mc_ref
backend|gole/backend:local
frontend|gole/frontend:local
nginx|nginx:1.29-alpine
budget-relay|gole/budget-relay:local
support-agent|gole/support-agent:local
EOF
set_ref "$mongo_ref" "$candidate_id"
set_ref "$redis_ref" "$candidate_id"
set_ref "$minio_ref" "$candidate_id"
set_ref "$mc_ref" "$candidate_id"
cat > /usr/local/sbin/gole-backup-data <<'EOF'
#!/bin/bash
set -eu
backup=/var/backups/gole-data/20260904T130000Z
install -d -m 0700 "$backup"
printf 'mongo-forward-backup\n' > "$backup/mongo.archive.gz"
printf 'minio-forward-backup\n' > "$backup/minio.tar.gz"
printf 'redis-forward-backup\n' > "$backup/redis.tar.gz"
(cd "$backup" && sha256sum mongo.archive.gz minio.tar.gz redis.tar.gz > SHA256SUMS)
printf 'format=gole-logical-backup-v1\ncreated_at=20260904T130000Z\n' > "$backup/COMPLETE"
chmod 0600 "$backup"/*
touch /tmp/logical-backup-called
printf 'backup\n' >> /tmp/docker-state/transaction.events
printf '%s\n' "$backup"
EOF
chmod 0755 /usr/local/sbin/gole-backup-data
printf 'current-nginx\n' > /etc/gole/nginx.conf
chmod 0644 /etc/gole/nginx.conf
nginx_backup="/var/backups/gole-nginx/nginx.conf.$request_id"
install -d -m 0700 /var/backups/gole-nginx
printf 'previous-nginx\n' > "$nginx_backup"
chmod 0600 "$nginx_backup"
nginx_hash="$(sha256sum /etc/gole/nginx.conf | cut -d' ' -f1)"
cat > /etc/gole/nginx.conf.transaction <<EOF
state=installed
request_id=$request_id
backup_file=$nginx_backup
candidate_sha256=$nginx_hash
deploy_sha=$new_sha
EOF
chmod 0600 /etc/gole/nginx.conf.transaction
sed -i 's/^state=snapshotted$/state=nginx-installed/' /etc/gole/deployment.transaction
: > "$state_root/compose.calls"
: > "$state_root/transaction.events"
SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-compose-up \
  rollout-all-apps "$new_sha" "$request_id"
grep -qx 'state=mutated' /etc/gole/deployment.transaction
[ -e /tmp/logical-backup-called ]
[ -e "/var/backups/gole-images/data-upgrade.$compact_request_id" ]
grep -qx 'state=mutation-armed' "/var/backups/gole-images/data-upgrade.$compact_request_id"
grep -Fq 'action=pull ' "$state_root/compose.calls"
mongo_up_line="$(grep -nF 'action=up service=mongo ' "$state_root/compose.calls" | head -1 | cut -d: -f1)"
redis_up_line="$(grep -nF 'action=up service=redis ' "$state_root/compose.calls" | head -1 | cut -d: -f1)"
minio_up_line="$(grep -nF 'action=up service=minio ' "$state_root/compose.calls" | head -1 | cut -d: -f1)"
backend_up_line="$(grep -nF 'action=up service=backend ' "$state_root/compose.calls" | head -1 | cut -d: -f1)"
[ "$mongo_up_line" -lt "$redis_up_line" ] && [ "$redis_up_line" -lt "$minio_up_line" ] &&
  [ "$minio_up_line" -lt "$backend_up_line" ]
grep -Fq 'action=up service=mongo-init ' "$state_root/compose.calls"
grep -Fq 'action=up service=minio-init ' "$state_root/compose.calls"
[ "$(cat "$state_root/services/mongo-init/status")" = exited ]
[ "$(cat "$state_root/services/minio-init/status")" = exited ]

# Candidate downloads and immutable identity resolution finish before the
# durable quiesce boundary. The logical backup is taken only after every
# write-capable service is stopped; they remain stopped through the final data
# candidate verification and are reopened only afterward.
pull_event="$(grep -n '^pull$' "$state_root/transaction.events" | head -1 | cut -d: -f1)"
resolve_event="$(grep -n '^resolve-' "$state_root/transaction.events" | head -1 | cut -d: -f1)"
nginx_stop_event="$(grep -n '^stop-nginx$' "$state_root/transaction.events" | cut -d: -f1)"
backend_stop_event="$(grep -n '^stop-backend$' "$state_root/transaction.events" | cut -d: -f1)"
support_stop_event="$(grep -n '^stop-support-agent$' "$state_root/transaction.events" | cut -d: -f1)"
backup_event="$(grep -n '^backup$' "$state_root/transaction.events" | cut -d: -f1)"
mongo_event="$(grep -n '^up-mongo$' "$state_root/transaction.events" | head -1 | cut -d: -f1)"
candidate_verified_event="$(grep -n '^verify-minio-init$' "$state_root/transaction.events" | tail -1 | cut -d: -f1)"
final_quiesce_proof_event="$(grep -n '^quiesce-proof-backend$' "$state_root/transaction.events" | tail -1 | cut -d: -f1)"
backend_event="$(grep -n '^up-backend$' "$state_root/transaction.events" | head -1 | cut -d: -f1)"
[ "$pull_event" -lt "$resolve_event" ]
[ "$resolve_event" -lt "$nginx_stop_event" ]
[ "$nginx_stop_event" -lt "$backend_stop_event" ]
[ "$backend_stop_event" -lt "$support_stop_event" ]
[ "$support_stop_event" -lt "$backup_event" ]
[ "$backup_event" -lt "$mongo_event" ]
[ "$mongo_event" -lt "$candidate_verified_event" ]
[ "$candidate_verified_event" -lt "$final_quiesce_proof_event" ]
[ "$final_quiesce_proof_event" -lt "$backend_event" ]
rm -f /etc/gole/nginx.conf.transaction "$nginx_backup"
# A later application failure cannot claim automatic LKG recovery after this
# durable data boundary. It must stop the candidate app and retain the logical
# recovery point for the explicit root restore path.
: > "$state_root/compose.calls"
rm -f /tmp/poweroff-requested
if SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-rollback "$request_id" \
  >/tmp/forward-data-rollback.out 2>&1; then
  echo 'forward data mutation was falsely reported as automatically recovered' >&2
  exit 1
fi
grep -q 'requires explicit logical restore' /tmp/forward-data-rollback.out
[ -e /tmp/poweroff-requested ]
[ -e "/var/backups/gole-images/data-upgrade.$compact_request_id" ]
[ -e /var/backups/gole-data/20260904T130000Z/COMPLETE ]
[ "$(cat "$state_root/services/backend/running")" = false ]
if grep -Eq 'action=up service=(mongo|mongo-init|redis|minio|minio-init|support-agent|backend|frontend|nginx) ' \
  "$state_root/compose.calls"; then
  echo 'forward data rollback recreated a service' >&2
  exit 1
fi

# With byte-for-byte identical pinned data references, the same strict rollout
# must not call the backup helper, pull a data image, or touch a data service.
setup_snapshot strict
cat > "$state_root/model" <<EOF
mongo|$mongo_ref
mongo-init|$mongo_ref
redis|$redis_ref
minio|$minio_ref
minio-init|$mc_ref
backend|gole/backend:local
frontend|gole/frontend:local
nginx|nginx:1.29-alpine
budget-relay|gole/budget-relay:local
support-agent|gole/support-agent:local
EOF
set_ref "$mongo_ref" "$mongo_id"
set_ref "$redis_ref" "$redis_id"
set_ref "$minio_ref" "$minio_id"
set_ref "$mc_ref" "$minio_init_id"
cp "$state_root/model" "$state_root/candidate-model"
SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-images-snapshot all "$request_id"
printf 'current-nginx\n' > /etc/gole/nginx.conf
chmod 0644 /etc/gole/nginx.conf
nginx_backup="/var/backups/gole-nginx/nginx.conf.$request_id"
printf 'previous-nginx\n' > "$nginx_backup"
chmod 0600 "$nginx_backup"
nginx_hash="$(sha256sum /etc/gole/nginx.conf | cut -d' ' -f1)"
cat > /etc/gole/nginx.conf.transaction <<EOF
state=installed
request_id=$request_id
backup_file=$nginx_backup
candidate_sha256=$nginx_hash
deploy_sha=$new_sha
EOF
chmod 0600 /etc/gole/nginx.conf.transaction
sed -i 's/^state=snapshotted$/state=nginx-installed/' /etc/gole/deployment.transaction
rm -f /tmp/logical-backup-called
: > "$state_root/compose.calls"
SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-compose-up \
  rollout-all-apps "$new_sha" "$request_id"
[ ! -e /tmp/logical-backup-called ]
[ ! -e "/var/backups/gole-images/data-upgrade.$compact_request_id" ]
if grep -Eq 'action=(pull|up) service=(mongo|mongo-init|redis|minio|minio-init) ' \
  "$state_root/compose.calls"; then
  echo 'unchanged strict rollout touched the data plane' >&2
  exit 1
fi
rm -f /etc/gole/nginx.conf.transaction "$nginx_backup"
SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-rollback "$request_id"

# A timed-out MinIO unfreeze is a hard write gate. Even an otherwise safe LKG
# rollback must refuse to recreate backend/public services until a root
# operator has completed the bounded unfreeze recovery and removed its marker.
setup_snapshot strict
SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-images-snapshot all "$request_id"
sed -i 's/^state=snapshotted$/state=mutation-armed/' /etc/gole/deployment.transaction
install -d -m 0700 /var/backups/gole-data
printf 'state=unfreeze-required\n' > /var/backups/gole-data/MINIO_UNFREEZE_REQUIRED
chmod 0600 /var/backups/gole-data/MINIO_UNFREEZE_REQUIRED
: > "$state_root/compose.calls"
if SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-rollback "$request_id" \
  >/tmp/unresolved-minio-rollback.out 2>&1; then
  echo 'rollback reopened writes with unresolved MinIO freeze state' >&2
  exit 1
fi
grep -q 'MinIO unfreeze recovery is unresolved' /tmp/unresolved-minio-rollback.out
[ -e /tmp/poweroff-requested ]
if grep -Eq 'action=up service=(support-agent|backend|frontend|nginx) ' \
  "$state_root/compose.calls"; then
  echo 'unresolved MinIO rollback recreated a write-capable service' >&2
  exit 1
fi
rm -f /var/backups/gole-data/MINIO_UNFREEZE_REQUIRED /tmp/poweroff-requested
SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-rollback "$request_id"

# A Compose project/service label mismatch invalidates a container as LKG
# provenance even when its ID and image look valid.
setup_snapshot strict
printf 'other-project|backend\n' > "$state_root/services/backend/labels"
if SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-images-snapshot all "$request_id" \
  >/tmp/bad-label.out 2>&1; then
  echo 'snapshot accepted a foreign Compose container' >&2
  exit 1
fi
grep -q 'Compose container ownership is invalid: backend' /tmp/bad-label.out
[ ! -e "/var/backups/gole-images/images.$compact_request_id" ]

# The two mongo services share one mutable Compose image reference. Their
# historical containers must therefore resolve to the same immutable image ID.
setup_snapshot legacy
printf '%s\n' "$conflict_id" > "$state_root/services/mongo-init/image"
if SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-images-snapshot all "$request_id" \
  >/tmp/conflicting-ref.out 2>&1; then
  echo 'snapshot accepted conflicting identities for shared mongo:7' >&2
  exit 1
fi
grep -q 'one Compose image reference resolves to conflicting LKG images: mongo:7' \
  /tmp/conflicting-ref.out
[ ! -e "/var/backups/gole-images/images.$compact_request_id" ]

# Initializers are provenance records too: missing history or a non-zero exit
# cannot be replaced by whatever mutable image tag happens to exist now.
setup_snapshot legacy
rm -rf "$state_root/services/mongo-init"
if SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-images-snapshot all "$request_id" \
  >/tmp/missing-init.out 2>&1; then
  echo 'snapshot accepted a missing historical initializer' >&2
  exit 1
fi
grep -q 'historical initializer container is missing: mongo-init' /tmp/missing-init.out
[ ! -e "/var/backups/gole-images/images.$compact_request_id" ]

setup_snapshot legacy
printf '1\n' > "$state_root/services/mongo-init/exit-code"
if SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-images-snapshot all "$request_id" \
  >/tmp/failed-init.out 2>&1; then
  echo 'snapshot accepted a failed historical initializer' >&2
  exit 1
fi
grep -q 'historical initializer did not complete successfully: mongo-init' /tmp/failed-init.out
[ ! -e "/var/backups/gole-images/images.$compact_request_id" ]

# Even if every rollback tag and override is correct, the post-Compose image
# identity check must power off when Docker starts a different image.
setup_snapshot strict
SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-images-snapshot all "$request_id"
mutate_runtime strict
printf 'frontend\n' > "$state_root/drift-service"
printf '%s\n' "$drift_id" > "$state_root/drift-image"
if SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-rollback "$request_id" \
  >/tmp/image-drift.out 2>&1; then
  echo 'rollback accepted a restored container with a different image ID' >&2
  exit 1
fi
[ -e /tmp/poweroff-requested ]
grep -q 'restored service image does not match: frontend' /tmp/image-drift.out
grep -q 'deployment rollback failed; VM powered off' /tmp/image-drift.out
[ -e /etc/gole/deployment.transaction ]
[ -e "/var/backups/gole-images/images.$compact_request_id" ]

echo 'Service-provenance image snapshot and rollback modes passed.'
CONTAINER_TEST
