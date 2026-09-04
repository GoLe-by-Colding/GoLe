#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ROOT="${GOLE_DEPLOY_ROOT:-$SCRIPT_ROOT}"
cd "$ROOT"
TARGET="${1:-all}"
DEPLOY_SHA="${DEPLOY_SHA:-}"
ROLLBACK_SHA="${ROLLBACK_SHA:-}"
COMPOSE=(docker compose --env-file /etc/gole/infra.env --env-file /etc/gole/gole.env -f "$ROOT/infra/gcp/docker-compose.yml")
HOSTCTL="/usr/local/sbin/gole-hostctl"
PREPARE_NGINX_SCRIPT="$ROOT/infra/gcp/scripts/prepare-nginx-config.sh"
# Isolated contract tests run outside /app and may inject rootless fakes. These
# overrides are deliberately ignored on the production checkout.
if [ "$ROOT" != "/app" ]; then
  HOSTCTL="${GOLE_TEST_HOSTCTL:-$HOSTCTL}"
  PREPARE_NGINX_SCRIPT="${GOLE_TEST_PREPARE_NGINX:-$PREPARE_NGINX_SCRIPT}"
fi
HOST_DOCKER_CONTROL=0
if [ "$ROOT" = "/app" ] ||
  { [ "$ROOT" != "/app" ] && [ "${GOLE_TEST_HOST_DOCKER_CONTROL:-0}" = "1" ]; }; then
  HOST_DOCKER_CONTROL=1
fi
ROLLOUT_LOCK="/run/lock/gole-production-rollout.lock"
PREVIOUS_SHA=""
DEPLOY_MUTATED=0
ROLLBACK_SUCCEEDED=0
DEPLOYMENT_TRANSACTION_ID=""
ROLLBACK_IMAGES=()
DEPLOYMENT_IMAGES_SNAPSHOTTED=0
NGINX_TRANSACTION_ACTIVE=0
NGINX_TRANSACTION_ID=""
DEPLOYMENT_MARKER_ADVANCED=0
DEPLOYMENT_TRANSACTION_ACTIVE=0

log() { printf '\n▶ %s\n' "$*"; }

require_exact_production_env() {
  local variable_name="$1" expected_value="$2"
  if [ "${!variable_name:-}" != "$expected_value" ]; then
    echo "운영 배포 실행 문맥이 올바르지 않습니다: ${variable_name}" >&2
    exit 1
  fi
}

validate_production_invocation() {
  if [ "$TARGET" != "all" ]; then
    echo "운영 배포는 전체 Docker Compose 대상(all)만 허용합니다." >&2
    exit 1
  fi
  if [[ ! "$DEPLOY_SHA" =~ ^[0-9a-f]{40}$ ]]; then
    echo "운영 DEPLOY_SHA는 CI가 검증한 40자리 Git SHA여야 합니다." >&2
    exit 1
  fi
  if [ "${GOLE_ROLLOUT_LOCK_HELD:-0}" != "1" ]; then
    echo "운영 배포는 CD workflow가 획득한 rollout lock을 상속해야 합니다." >&2
    exit 1
  fi

  # Accidental SSH/manual deploys must not be able to select a partial target or
  # silently fall back to origin/main. These runner-provided values bind /app to
  # the single protected-main CD job; hostctl separately proves current main and
  # its successful push CI before any root-owned mutation.
  require_exact_production_env GITHUB_ACTIONS true
  require_exact_production_env GITHUB_SERVER_URL https://github.com
  require_exact_production_env GITHUB_REPOSITORY GoLe-by-Colding/GoLe
  require_exact_production_env GITHUB_WORKFLOW CD
  require_exact_production_env GITHUB_WORKFLOW_REF \
    GoLe-by-Colding/GoLe/.github/workflows/cd.yml@refs/heads/main
  require_exact_production_env GITHUB_REF refs/heads/main
  require_exact_production_env GITHUB_REF_NAME main
  require_exact_production_env GITHUB_REF_TYPE branch
  require_exact_production_env GITHUB_REF_PROTECTED true
  require_exact_production_env GITHUB_JOB deploy
  require_exact_production_env RUNNER_ENVIRONMENT self-hosted
  require_exact_production_env RUNNER_NAME gole-gcp-production
  require_exact_production_env RUNNER_OS Linux
  require_exact_production_env RUNNER_ARCH X64
  require_exact_production_env GITHUB_SHA "$DEPLOY_SHA"
  require_exact_production_env GITHUB_WORKFLOW_SHA "$DEPLOY_SHA"

  case "${GITHUB_EVENT_NAME:-}" in
    workflow_run | workflow_dispatch) ;;
    *)
      echo "운영 배포는 main의 workflow_run 또는 workflow_dispatch에서만 허용합니다." >&2
      exit 1
      ;;
  esac
}

case "$TARGET" in
  all | backend | frontend) ;;
  *)
    echo "알 수 없는 대상: $TARGET (all|backend|frontend)" >&2
    exit 1
    ;;
esac

PRODUCTION_DEPLOY_CONTEXT=0
TEST_PRODUCTION_DEPLOY_CONTEXT=0
if [ "$SCRIPT_ROOT" = "/app" ] || [ "$ROOT" = "/app" ]; then
  if [ "$SCRIPT_ROOT" != "/app" ] || [ "$ROOT" != "/app" ]; then
    echo "운영 deploy.sh는 /app checkout과 /app 작업 경로에서만 실행해야 합니다." >&2
    exit 1
  fi
  PRODUCTION_DEPLOY_CONTEXT=1
elif [ "${GOLE_TEST_PRODUCTION_DEPLOY_CONTEXT:-0}" = "1" ]; then
  # Runtime contract tests may exercise the fail-closed gate from a disposable
  # checkout. This switch can only add production restrictions, never bypass
  # the real /app detection above.
  PRODUCTION_DEPLOY_CONTEXT=1
  TEST_PRODUCTION_DEPLOY_CONTEXT=1
fi

if [ "$PRODUCTION_DEPLOY_CONTEXT" = "1" ]; then
  validate_production_invocation
fi
if [ "$TEST_PRODUCTION_DEPLOY_CONTEXT" = "1" ] &&
  [ "${GOLE_TEST_VALIDATE_PRODUCTION_INVOCATION_ONLY:-0}" = "1" ]; then
  exit 0
fi

install_discord_overlay() {
  local deploy operations support variable_name
  local -a required=(
    DISCORD_OPERATIONS_WEBHOOK_URL
    DISCORD_ACCOUNT_WEBHOOK_URL
    DISCORD_PAYMENT_WEBHOOK_URL
  )
  [ "$HOST_DOCKER_CONTROL" = 1 ] || return 0
  if [ "${GOLE_ROLLOUT_LOCK_HELD:-0}" = 1 ]; then
    # CD/Secret Sync installed the overlay in the immediately preceding step,
    # before taking fd7. Revalidate only: attempting the install here would
    # conflict with the already-held rollout lock.
    sudo -n "$HOSTCTL" discord-overlay-verify
    return
  fi
  for variable_name in "${required[@]}"; do
    if [ -z "${!variable_name:-}" ]; then
      echo "필수 Discord GitHub secret 누락: ${variable_name}" >&2
      return 1
    fi
  done
  operations="$DISCORD_OPERATIONS_WEBHOOK_URL"
  deploy="${DISCORD_DEPLOY_WEBHOOK_URL:-$operations}"
  support="${DISCORD_SUPPORT_WEBHOOK_URL:-$operations}"
  DISCORD_SUPPRESS_NOTIFICATIONS="${DISCORD_SUPPRESS_NOTIFICATIONS:-false}"
  [[ "$DISCORD_SUPPRESS_NOTIFICATIONS" =~ ^(true|false)$ ]] || {
    echo "Discord suppress 설정은 true 또는 false여야 합니다." >&2
    return 1
  }
  # Never put webhook values in argv or logs. The root helper validates and
  # normalizes this fixed-key request before atomically installing 0600 state.
  set +x
  printf '%s\n' \
    'GOLE_DISCORD_ALERTS_ENABLED=true' \
    "DISCORD_DEPLOY_WEBHOOK_URL=$deploy" \
    "DISCORD_OPERATIONS_WEBHOOK_URL=$operations" \
    "DISCORD_ACCOUNT_WEBHOOK_URL=$DISCORD_ACCOUNT_WEBHOOK_URL" \
    "DISCORD_PAYMENT_WEBHOOK_URL=$DISCORD_PAYMENT_WEBHOOK_URL" \
    "DISCORD_SUPPORT_WEBHOOK_URL=$support" \
    "DISCORD_SUPPRESS_NOTIFICATIONS=$DISCORD_SUPPRESS_NOTIFICATIONS" |
    sudo -n "$HOSTCTL" discord-overlay-install
}

notify_discord() {
  local webhook_url="${DISCORD_DEPLOY_WEBHOOK_URL:-${DISCORD_OPERATIONS_WEBHOOK_URL:-}}"
  local avatar_url="${DISCORD_AVATAR_URL:-https://gole.co.kr/icon.svg}"
  local notification_flags=""
  local message="$1"
  if [ -z "$webhook_url" ]; then return 0; fi
  if [ "${DISCORD_SUPPRESS_NOTIFICATIONS:-true}" = "true" ]; then
    notification_flags=',"flags":4096'
  fi
  curl -fsS --max-time 5 -H 'Content-Type: application/json' \
    --data "{\"content\":\"${message}\",\"avatar_url\":\"${avatar_url}\",\"allowed_mentions\":{\"parse\":[]}${notification_flags}}" \
    "$webhook_url" >/dev/null || true
}

DEPLOY_RESULT_NOTIFIED=0
notify_deploy_result_once() {
  if [ "$DEPLOY_RESULT_NOTIFIED" = "1" ]; then return 0; fi
  DEPLOY_RESULT_NOTIFIED=1
  notify_discord "$1"
}

snapshot_container_image() {
  local container_name="$1"
  local target_image="$2"
  local image_id rollback_tag
  image_id="$(docker inspect --format '{{.Image}}' "$container_name" 2>/dev/null || true)"
  if [ -z "$image_id" ]; then
    return 0
  fi
  rollback_tag="${target_image}-gole-rollback-${GITHUB_RUN_ID:-$$}"
  docker image tag "$image_id" "$rollback_tag"
  ROLLBACK_IMAGES+=("${target_image}|${rollback_tag}")
}

snapshot_deployment_images() {
  if [ "$HOST_DOCKER_CONTROL" = "1" ]; then
    sudo -n "$HOSTCTL" deployment-images-snapshot "$TARGET" "$DEPLOYMENT_TRANSACTION_ID"
  else
    case "$TARGET" in
      backend)
        snapshot_container_image gole-support-agent gole/support-agent:local
        snapshot_container_image gole-backend gole/backend:local
        ;;
      frontend) snapshot_container_image gole-frontend gole/frontend:local ;;
      all)
        snapshot_container_image gole-support-agent gole/support-agent:local
        snapshot_container_image gole-backend gole/backend:local
        snapshot_container_image gole-frontend gole/frontend:local
        snapshot_container_image gole-budget-relay gole/budget-relay:local
        ;;
    esac
  fi
  DEPLOYMENT_IMAGES_SNAPSHOTTED=1
}

cleanup_rollback_images() {
  local entry rollback_tag
  if [ "$DEPLOYMENT_IMAGES_SNAPSHOTTED" = "0" ]; then return 0; fi
  if [ "$HOST_DOCKER_CONTROL" = "1" ]; then
    sudo -n "$HOSTCTL" deployment-images-cleanup "$TARGET" "$DEPLOYMENT_TRANSACTION_ID" || true
    DEPLOYMENT_IMAGES_SNAPSHOTTED=0
    return 0
  fi
  for entry in "${ROLLBACK_IMAGES[@]-}"; do
    if [ -z "$entry" ]; then continue; fi
    rollback_tag="${entry#*|}"
    docker image rm "$rollback_tag" >/dev/null 2>&1 || true
  done
  DEPLOYMENT_IMAGES_SNAPSHOTTED=0
}

compose_build_target() {
  local requested_sha="$1"
  shift
  if [ "$HOST_DOCKER_CONTROL" = "1" ]; then
    sudo -n "$HOSTCTL" deployment-compose-build "$TARGET" "$requested_sha" \
      "$DEPLOYMENT_TRANSACTION_ID"
  else
    "${COMPOSE[@]}" build "$@"
  fi
}

compose_up_phase() {
  local phase="$1" requested_sha="$2"
  shift 2
  if [ "$HOST_DOCKER_CONTROL" = "1" ]; then
    sudo -n "$HOSTCTL" deployment-compose-up "$phase" "$requested_sha" \
      "$DEPLOYMENT_TRANSACTION_ID"
  else
    "${COMPOSE[@]}" "$@"
  fi
}

compose_show_status() {
  local requested_sha
  requested_sha="$(git rev-parse HEAD 2>/dev/null || true)"
  if [ "$HOST_DOCKER_CONTROL" = "1" ] && [[ "$requested_sha" =~ ^[0-9a-f]{40}$ ]]; then
    sudo -n "$HOSTCTL" deployment-compose-ps "$requested_sha" "$DEPLOYMENT_TRANSACTION_ID"
  elif [ "$HOST_DOCKER_CONTROL" = "0" ]; then
    "${COMPOSE[@]}" ps
  fi
}

budget_relay_healthy() {
  if [ "$HOST_DOCKER_CONTROL" = "1" ]; then
    sudo -n "$HOSTCTL" deployment-budget-healthy
  else
    [ "$(docker inspect --format '{{.State.Health.Status}}' gole-budget-relay 2>/dev/null)" = "healthy" ]
  fi
}

rollback_deployment() {
  local entry target_image rollback_tag available_services service
  local rollback_services=()

  if [ "$HOST_DOCKER_CONTROL" = "1" ]; then
    [ "$DEPLOYMENT_TRANSACTION_ACTIVE" = "1" ] || return 1
    sudo -n "$HOSTCTL" deployment-rollback "$DEPLOYMENT_TRANSACTION_ID" || return 1
    DEPLOYMENT_TRANSACTION_ACTIVE=0
    DEPLOYMENT_IMAGES_SNAPSHOTTED=0
    NGINX_TRANSACTION_ACTIVE=0
    ROLLBACK_SUCCEEDED=1
    return 0
  fi
  if [ -z "$PREVIOUS_SHA" ] || [ "$DEPLOYMENT_IMAGES_SNAPSHOTTED" = "0" ]; then
    return 1
  fi
  if [ "$HOST_DOCKER_CONTROL" = "0" ] && [ -z "${ROLLBACK_IMAGES[0]-}" ]; then return 1; fi

  log "직전 정상 커밋과 이미지로 자동 복구"
  git reset --hard "$PREVIOUS_SHA" || return 1
  if [ "$HOST_DOCKER_CONTROL" = "0" ]; then
    for entry in "${ROLLBACK_IMAGES[@]-}"; do
      if [ -z "$entry" ]; then continue; fi
      target_image="${entry%%|*}"
      rollback_tag="${entry#*|}"
      docker image tag "$rollback_tag" "$target_image" || return 1
    done

    available_services="$("${COMPOSE[@]}" config --services)" || return 1
    case "$TARGET" in
      backend)
        for service in support-agent backend nginx; do
          if grep -qx "$service" <<<"$available_services"; then rollback_services+=("$service"); fi
        done
        ;;
      frontend)
        for service in frontend nginx; do
          if grep -qx "$service" <<<"$available_services"; then rollback_services+=("$service"); fi
        done
        ;;
      all)
        for service in support-agent backend frontend budget-relay nginx; do
          if grep -qx "$service" <<<"$available_services"; then rollback_services+=("$service"); fi
        done
        ;;
    esac
    if [ "${#rollback_services[@]}" -eq 0 ]; then return 1; fi
    "${COMPOSE[@]}" up -d --no-build --remove-orphans --wait "${rollback_services[@]}" || return 1
  fi
  curl -fsS --max-time 15 http://127.0.0.1:8080/actuator/health/readiness >/dev/null || return 1
  curl -fsS --max-time 15 http://127.0.0.1:3000/icon.svg >/dev/null || return 1
  if [ "$TARGET" = "all" ]; then
    budget_relay_healthy || return 1
    sudo -n "$HOSTCTL" deployment-record-sha "$PREVIOUS_SHA" || return 1
  fi
  ROLLBACK_SUCCEEDED=1
  cleanup_rollback_images
  return 0
}

on_deploy_exit() {
  local status=$?
  if [ -n "${NGINX_CANDIDATE:-}" ]; then
    rm -f -- "$NGINX_CANDIDATE"
  fi
  if [ "$status" -ne 0 ]; then
    set +e
    compose_show_status || true
    if [ -n "${SECRET_SYNC_REQUEST_ID:-}" ]; then
      echo "Secret Sync 실패 로그는 민감정보 보호를 위해 Actions에 출력하지 않습니다." >&2
    elif [ "$HOST_DOCKER_CONTROL" = "0" ]; then
      "${COMPOSE[@]}" logs --tail=100 support-agent backend frontend budget-relay nginx || true
    fi
    notify_deploy_result_once "❌ GoLe ${TARGET} 배포 실패 (exit ${status}) · gole.co.kr"
    nginx_config_restored=1
    preserve_initial_nginx_commit=0
    if [ "$HOST_DOCKER_CONTROL" = "0" ] && [ "$NGINX_TRANSACTION_ACTIVE" = "1" ]; then
      # 최초 배포는 되돌릴 앱/SHA가 없다. 모든 smoke 뒤 marker까지 이미 기록됐다면
      # 유효한 committed journal을 다음 실행이 완결하게 두고 설정을 역행시키지 않는다.
      if [ -z "$PREVIOUS_SHA" ] && [ "$DEPLOYMENT_MARKER_ADVANCED" = "1" ]; then
        preserve_initial_nginx_commit=1
      elif ! sudo -n "$HOSTCTL" nginx-transaction-abort "$NGINX_TRANSACTION_ID"; then
        nginx_config_restored=0
      fi
    fi
    if [ "$HOST_DOCKER_CONTROL" = "1" ] && [ "$DEPLOYMENT_TRANSACTION_ACTIVE" = "1" ] &&
      rollback_deployment; then
      notify_discord "↩️ GoLe ${TARGET} 배포 실패 후 root LKG release로 자동 복구함 · gole.co.kr"
    elif [ "$HOST_DOCKER_CONTROL" = "0" ] && [ "$DEPLOY_MUTATED" = "1" ] &&
      [ "$nginx_config_restored" = "1" ] && rollback_deployment; then
      if [ "$NGINX_TRANSACTION_ACTIVE" = "1" ]; then
        sudo -n "$HOSTCTL" nginx-transaction-finish-recovery "$NGINX_TRANSACTION_ID" || true
      fi
      notify_discord "↩️ GoLe ${TARGET} 배포 실패 후 직전 정상 버전으로 자동 복구함 · gole.co.kr"
    elif [ "$DEPLOY_MUTATED" = "0" ] && [ "$NGINX_TRANSACTION_ACTIVE" = "1" ] &&
      [ "$nginx_config_restored" = "1" ] && [ "$preserve_initial_nginx_commit" = "0" ]; then
      sudo -n "$HOSTCTL" nginx-transaction-finish-recovery "$NGINX_TRANSACTION_ID" || true
    elif [ "$HOST_DOCKER_CONTROL" = "1" ] && [ "$DEPLOYMENT_TRANSACTION_ACTIVE" = "1" ]; then
      notify_discord "🛑 배포 복구를 증명하지 못해 GCP VM을 안전 정지함 · gole.co.kr"
      sudo -n "$HOSTCTL" deployment-fail-closed "$DEPLOYMENT_TRANSACTION_ID" || true
    elif [ "$DEPLOY_MUTATED" = "0" ] && [ -n "$PREVIOUS_SHA" ]; then
      # CD 부트스트랩이 먼저 새 커밋을 checkout한 뒤 빌드가 실패한 경우에도 다음
      # 배포의 rollback 기준이 흐려지지 않도록 코드만 직전 SHA로 되돌린다.
      git reset --hard "$PREVIOUS_SHA" || true
    fi
  fi
  if [ "$ROLLBACK_SUCCEEDED" != "1" ] && [ "$HOST_DOCKER_CONTROL" = "0" ]; then
    cleanup_rollback_images
  fi
}
trap on_deploy_exit EXIT

# This is the only runner→root secret bridge. The helper takes no argv values,
# holds the rollout lock itself, and completes before recovery/build/container
# mutation. Role destinations may intentionally share the GoLe room.
install_discord_overlay

# CD와 Secret Sync가 서로 다른 workflow여도 호스트 전체 변이 구간은 하나만 실행한다.
# apply-secret-env.sh가 이미 같은 lock을 보유한 채 backend 재기동을 호출할 때만 재진입한다.
if [ "${GOLE_ROLLOUT_LOCK_HELD:-0}" != "1" ]; then
  if [ ! -f "$ROLLOUT_LOCK" ] || [ -L "$ROLLOUT_LOCK" ]; then
    echo "운영 rollout lock이 설치되지 않았습니다." >&2
    exit 1
  fi
  exec 7>>"$ROLLOUT_LOCK"
  if ! flock -n 7; then
    echo "다른 운영 배포 또는 환경 동기화가 진행 중입니다." >&2
    exit 1
  fi
elif { [ "$ROOT" = "/app" ] && [ ! -e /proc/self/fd/7 ]; } || ! flock -n 7; then
  echo "부모 rollout lock을 확인할 수 없습니다." >&2
  exit 1
fi

complete_pending_initial_tls() {
  local certificate_status=0 completion_status=0
  [ "$HOST_DOCKER_CONTROL" = 1 ] || return 1
  # sudo closes the runner's inherited fd7. Release it first so the root-only
  # certificate action can acquire the same host lock as fd8, then reacquire
  # before touching the deployment journal again.
  flock -u 7
  sudo -n "$HOSTCTL" certificate-issue || certificate_status=$?
  if ! flock -n 7; then
    echo "인증서 작업 뒤 운영 rollout lock을 다시 획득하지 못했습니다." >&2
    return 1
  fi
  if [ "$certificate_status" -ne 0 ]; then
    sudo -n "$HOSTCTL" deployment-fail-closed-initial-tls || true
    return "$certificate_status"
  fi
  sudo -n "$HOSTCTL" deployment-complete-initial-tls || completion_status=$?
  if [ "$completion_status" -ne 0 ]; then
    sudo -n "$HOSTCTL" deployment-fail-closed-initial-tls || true
    return "$completion_status"
  fi
}

if [ -r /proc/sys/kernel/random/uuid ]; then
  DEPLOYMENT_TRANSACTION_ID="$(tr 'A-F' 'a-f' < /proc/sys/kernel/random/uuid)"
else
  DEPLOYMENT_TRANSACTION_ID="$(uuidgen | tr 'A-F' 'a-f')"
fi
[[ "$DEPLOYMENT_TRANSACTION_ID" =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$ ]] || {
  echo "배포 transaction ID를 만들지 못했습니다." >&2
  exit 1
}

# SIGKILL이나 호스트 재시작으로 이전 rollout이 중단됐으면 새 checkout/build
# 전에 root journal과 immutable LKG release로 전체 서비스를 복구한다.
if [ -x "$HOSTCTL" ]; then
  deployment_recovery_state="$(sudo -n "$HOSTCTL" deployment-recover)"
  case "$deployment_recovery_state" in
    NONE | RECOVERED) ;;
    INITIAL_TLS_REQUIRED)
      log "중단된 최초 배포의 Google Trust Services TLS 완결"
      complete_pending_initial_tls
      ;;
    *)
      echo "중단된 배포 transaction을 안전하게 복구하지 못했습니다." >&2
      exit 1
      ;;
  esac
fi

notify_discord "🚀 GoLe ${TARGET} 배포 시작 · gole.co.kr"

if [ "$TARGET" = "all" ]; then
  required_cost_guard_variables=(
    GCP_BUDGET_PUBSUB_SUBSCRIPTION
    GCP_PROJECT_ID
    GCP_CREDIT_AMOUNT_KRW
    GCP_CREDIT_DEADLINE
    GCP_FIXED_HOURLY_COST_KRW
    GCP_HARD_STOP_ENABLED
    GCP_HARD_STOP_DRY_RUN
    GCP_HARD_STOP_BILLING_COST_KRW
    GCP_HARD_STOP_MIN_RESERVE_KRW
    GCP_HARD_STOP_ALL_IN_COST_KRW
    GCP_COST_GUARD_WARNING_KRW
    GCP_COST_GUARD_DANGER_KRW
    GCP_HARD_STOP_NETWORK_GIB
    GCP_COST_GUARD_NETWORK_WARNING_GIB
    GCP_COST_GUARD_NETWORK_DANGER_GIB
    GCP_HARD_STOP_MAX_RUNTIME_HOURS
    GCP_COST_GUARD_RUNTIME_WARNING_HOURS
    GCP_COST_GUARD_RUNTIME_DANGER_HOURS
    GCP_HARD_STOP_EXPECTED_BUDGET_KRW
    GCP_HARD_STOP_BUDGET_ID
    GCP_HARD_STOP_BILLING_ACCOUNT_ID
    GCP_HARD_STOP_BUDGET_DISPLAY_NAME
    GCP_HARD_STOP_PERIOD_START
    GCP_VM_COST_START
    GCP_HARD_STOP_AT
    GCP_HARD_STOP_ARM_ID
    GCP_INSTANCE_ZONE
    GCP_INSTANCE_NAME
    GCP_VAT_RATE
    GCP_NETWORK_EGRESS_KRW_PER_GIB
    GCP_STOPPED_RESOURCE_HOURLY_COST_KRW
    GCP_COST_GUARD_INTERVAL_SECONDS
    GCP_HARD_STOP_RETRY_SECONDS
    BUDGET_HTTP_TIMEOUT_SECONDS
  )
  for variable_name in "${required_cost_guard_variables[@]}"; do
    if [ -z "${!variable_name:-}" ]; then
      echo "필수 비용 가드 repository variable 누락: ${variable_name}" >&2
      exit 1
    fi
  done
  if [ "$GCP_HARD_STOP_ENABLED" != "true" ] || [ "$GCP_HARD_STOP_DRY_RUN" != "false" ]; then
    echo "운영 비용 가드는 enabled=true, dry_run=false로 명시적으로 무장해야 합니다." >&2
    exit 1
  fi
fi

log "CI가 검증한 origin/main 동기화"
# root 소유 marker가 마지막으로 smoke를 통과한 유일한 rollback 기준이다. 테스트/최초
# bootstrap 호환 경로에서만 호출자가 넘긴 SHA 또는 현재 HEAD를 사용한다.
if [ -x "$HOSTCTL" ]; then
  if PREVIOUS_SHA="$(sudo -n "$HOSTCTL" deployment-read-sha 2>/dev/null)"; then
    if [ "${GOLE_INITIAL_DEPLOY:-0}" = "1" ]; then
      echo "이미 정상 배포 marker가 있어 최초 배포 모드를 거부합니다." >&2
      exit 1
    fi
  elif [ "${GOLE_INITIAL_DEPLOY:-0}" = "1" ] &&
    sudo -n "$HOSTCTL" deployment-is-uninitialized; then
    # 신규 계정/VM의 최초 배포는 되돌릴 운영 버전이 없다. marker는 아래의 모든
    # smoke와 비용 가드를 통과한 뒤에만 생성한다.
    PREVIOUS_SHA=""
  else
    echo "마지막 정상 배포 SHA를 읽지 못해 배포를 중단합니다." >&2
    exit 1
  fi
elif [ "$ROOT" = "/app" ]; then
  echo "서버의 제한 권한 도우미가 설치되지 않았습니다." >&2
  exit 1
else
  PREVIOUS_SHA="${ROLLBACK_SHA:-$(git rev-parse HEAD)}"
fi
git fetch --prune origin main

# 운영 checkout에 수동 변경이 있으면 덮어쓰지 않는다. 깨끗한 경우에만 원격 main을
# 정확히 반영해, 승인된 force push나 계정 이전 뒤에도 배포가 막히지 않게 한다.
if [ -n "$(git status --porcelain=v1 --untracked-files=all)" ]; then
  echo "운영 checkout에 커밋되지 않은 변경이 있어 배포를 중단한다" >&2
  git status --short >&2
  exit 1
fi

deploy_ref="origin/main"
if [ -n "$DEPLOY_SHA" ]; then
  if [[ ! "$DEPLOY_SHA" =~ ^[0-9a-f]{40}$ ]]; then
    echo "DEPLOY_SHA는 40자리 Git SHA여야 한다" >&2
    exit 1
  fi
  if ! git cat-file -e "${DEPLOY_SHA}^{commit}" 2>/dev/null; then
    git fetch --no-tags origin "$DEPLOY_SHA"
  fi
  git cat-file -e "${DEPLOY_SHA}^{commit}"
  deploy_ref="$DEPLOY_SHA"
fi

git reset --hard "$deploy_ref"
if [ -n "$DEPLOY_SHA" ] && [ "$(git rev-parse HEAD)" != "$DEPLOY_SHA" ]; then
  echo "CI가 검증한 커밋으로 checkout하지 못했다" >&2
  exit 1
fi

case "$TARGET" in
  backend)
    SERVICES=(support-agent backend nginx)
    ;;
  frontend)
    SERVICES=(frontend nginx)
    ;;
  all)
    SERVICES=(support-agent backend frontend budget-relay nginx)
    ;;
esac

current_deploy_sha="$(git rev-parse HEAD)"
if [ "$HOST_DOCKER_CONTROL" = "1" ]; then
  transaction_previous_sha="${PREVIOUS_SHA:-0}"
  sudo -n "$HOSTCTL" deployment-begin "$TARGET" "$current_deploy_sha" \
    "$transaction_previous_sha" "$DEPLOYMENT_TRANSACTION_ID"
  DEPLOYMENT_TRANSACTION_ACTIVE=1
fi
snapshot_deployment_images

log "Docker Compose build"
compose_build_target "$current_deploy_sha" "${SERVICES[@]}"

# 빌드가 오래 걸리는 동안 main이 전진했다면 아직 live container를 건드리기 전
# 중단한다. 뒤처진 성공 CI가 최신 배포를 잠시라도 되돌리는 일을 막는다.
if [ -n "$DEPLOY_SHA" ]; then
  git fetch --prune origin main
  if [ "$(git rev-parse refs/remotes/origin/main)" != "$DEPLOY_SHA" ]; then
    echo "빌드 중 main이 갱신되어 뒤처진 배포 후보를 폐기합니다." >&2
    exit 1
  fi
fi

if [ "$TARGET" = "all" ] && [ -x "$HOSTCTL" ]; then
  log "source-controlled Nginx 설정 사전 검증 및 stage"
  NGINX_TRANSACTION_ID="$DEPLOYMENT_TRANSACTION_ID"
  if [ "$HOST_DOCKER_CONTROL" = "0" ]; then
    NGINX_CANDIDATE="/tmp/gole-nginx.${NGINX_TRANSACTION_ID//-/}"
    bash "$PREPARE_NGINX_SCRIPT" "$NGINX_TRANSACTION_ID"
  fi
  current_deploy_sha="$(git rev-parse HEAD)"
  NGINX_TRANSACTION_ACTIVE=1
  sudo -n "$HOSTCTL" nginx-transaction-begin "$NGINX_TRANSACTION_ID" "$current_deploy_sha"
fi

log "Docker Compose rolling update"
DEPLOY_MUTATED=1
if [ "$TARGET" = "all" ]; then
  # 비용 가드는 기존 정상 컨테이너를 유지한 채 애플리케이션부터 검증한다. 새 앱이
  # 실패해도 예산 보호가 끊기지 않으며, 비용 가드는 마지막에 독립 교체한다.
  compose_up_phase rollout-all-apps "$current_deploy_sha" \
    up -d --remove-orphans --wait support-agent backend frontend nginx
else
  compose_up_phase "rollout-$TARGET" "$current_deploy_sha" \
    up -d --remove-orphans --wait "${SERVICES[@]}"
fi

# backend/frontend 컨테이너가 재생성되면 내부 IP가 바뀔 수 있다. Nginx도 매번
# 재생성해 Docker DNS를 다시 조회하게 하고, 오래된 upstream으로 인한 502를 막는다.
log "Nginx upstream refresh"
compose_up_phase refresh-nginx "$current_deploy_sha" \
  up -d --no-deps --force-recreate --wait nginx
if [ "$HOST_DOCKER_CONTROL" = "0" ]; then "${COMPOSE[@]}" exec -T nginx nginx -t; fi

log "runtime smoke checks"
curl -fsS --max-time 15 http://127.0.0.1:8080/actuator/health/readiness >/dev/null
curl -fsS --max-time 15 http://127.0.0.1:8080/api/v1/catalog/sets/featured >/dev/null
curl -fsS --max-time 15 http://127.0.0.1:3000/icon.svg >/dev/null
if [ -n "$PREVIOUS_SHA" ]; then
  curl -fsS --max-time 15 --resolve gole.co.kr:443:127.0.0.1 \
    https://gole.co.kr/actuator/health/readiness >/dev/null
  curl -fsS --max-time 15 --resolve gole.co.kr:443:127.0.0.1 \
    https://gole.co.kr/icon.svg >/dev/null
fi

if [ "$TARGET" = "all" ]; then
  log "비용 가드 독립 업데이트"
  compose_up_phase rollout-budget "$current_deploy_sha" up -d --no-deps --wait budget-relay
  budget_relay_healthy

  log "비용 가드 호스트 watchdog 활성화"
  sudo -n "$HOSTCTL" watchdog-install
  sudo -n "$HOSTCTL" watchdog-active
fi

compose_show_status
if [ "$TARGET" = "all" ]; then
  # Marker를 전진시키기 전에 root 경계 안에서 모든 컨테이너, canonical redirect,
  # HSTS와 watchdog까지 검증한다. 최초 배포도 실패한 SHA marker를 남기지 않는다.
  if [ "$HOST_DOCKER_CONTROL" = "1" ]; then
    sudo -n "$HOSTCTL" deployment-verify-candidate-runtime "$current_deploy_sha" \
      "$DEPLOYMENT_TRANSACTION_ID"
  fi
  # 모든 container/readiness/비용 가드 검증 뒤에만 LKG marker를 전진시킨다. 이 기록이
  # 실패하면 DEPLOY_MUTATED=1 상태라 EXIT trap이 직전 SHA와 이미지로 되돌린다.
  deployed_sha="$(git rev-parse HEAD)"
  [ "$deployed_sha" = "${DEPLOY_SHA:-$deployed_sha}" ]
  if [ "$NGINX_TRANSACTION_ACTIVE" = "1" ]; then
    sudo -n "$HOSTCTL" nginx-transaction-commit "$NGINX_TRANSACTION_ID"
  fi
  sudo -n "$HOSTCTL" deployment-record-sha "$deployed_sha" "$DEPLOYMENT_TRANSACTION_ID"
  DEPLOYMENT_MARKER_ADVANCED=1
  if [ "$HOST_DOCKER_CONTROL" = "1" ] && [ -z "$PREVIOUS_SHA" ]; then
    # A fresh disk has no certificate yet. Prove the HTTP-only app first,
    # retire the deployment Nginx journal, issue GTS under the same durable
    # deployment transaction, then require the full TLS/HSTS proof.
    sudo -n "$HOSTCTL" deployment-verify-initial-http-commit "$deployed_sha" \
      "$DEPLOYMENT_TRANSACTION_ID"
    sudo -n "$HOSTCTL" nginx-transaction-finalize "$NGINX_TRANSACTION_ID"
    NGINX_TRANSACTION_ACTIVE=0
    complete_pending_initial_tls
  else
    # CI/runner가 Docker 소켓을 직접 읽지 않는다. root helper가 marker, clean
    # checkout, 모든 컨테이너, readiness, Nginx와 watchdog을 한 번에 검증한다.
    if [ "$HOST_DOCKER_CONTROL" = "1" ]; then
      sudo -n "$HOSTCTL" deployment-verify-commit "$deployed_sha" \
        "$DEPLOYMENT_TRANSACTION_ID"
    fi
    if [ "$NGINX_TRANSACTION_ACTIVE" = "1" ]; then
      sudo -n "$HOSTCTL" nginx-transaction-finalize "$NGINX_TRANSACTION_ID"
      NGINX_TRANSACTION_ACTIVE=0
    fi
    sudo -n "$HOSTCTL" deployment-finalize "$DEPLOYMENT_TRANSACTION_ID"
  fi
  DEPLOYMENT_TRANSACTION_ACTIVE=0
  DEPLOYMENT_IMAGES_SNAPSHOTTED=0
elif [ "$HOST_DOCKER_CONTROL" = "1" ]; then
  sudo -n "$HOSTCTL" deployment-verify-candidate-runtime "$current_deploy_sha" \
    "$DEPLOYMENT_TRANSACTION_ID"
  sudo -n "$HOSTCTL" deployment-finalize-partial "$DEPLOYMENT_TRANSACTION_ID"
  DEPLOYMENT_TRANSACTION_ACTIVE=0
  DEPLOYMENT_IMAGES_SNAPSHOTTED=0
fi
DEPLOY_MUTATED=0
if [ -n "${NGINX_CANDIDATE:-}" ]; then
  rm -f -- "$NGINX_CANDIDATE"
fi
cleanup_rollback_images
notify_deploy_result_once "✅ GoLe ${TARGET} 배포 및 헬스체크 완료 · gole.co.kr"
