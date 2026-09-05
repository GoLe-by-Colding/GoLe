#!/usr/bin/env bash
set -Eeuo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
DEPLOY_SCRIPT="$REPO_ROOT/scripts/deploy.sh"
VALID_SHA='2222222222222222222222222222222222222222'
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf -- "$TEST_ROOT"' EXIT

set_valid_production_context() {
  export GOLE_TEST_PRODUCTION_DEPLOY_CONTEXT=1
  export GOLE_TEST_VALIDATE_PRODUCTION_INVOCATION_ONLY=1
  export GOLE_ROLLOUT_LOCK_HELD=1
  export DEPLOY_SHA="$VALID_SHA"
  export GITHUB_ACTIONS=true
  export GITHUB_SERVER_URL=https://github.com
  export GITHUB_REPOSITORY=GoLe-by-Colding/GoLe
  export GITHUB_WORKFLOW=CD
  export GITHUB_WORKFLOW_REF=GoLe-by-Colding/GoLe/.github/workflows/cd.yml@refs/heads/main
  export GITHUB_REF=refs/heads/main
  export GITHUB_REF_NAME=main
  export GITHUB_REF_TYPE=branch
  export GITHUB_REF_PROTECTED=true
  export GITHUB_JOB=deploy
  export RUNNER_ENVIRONMENT=self-hosted
  export RUNNER_NAME=gole-gcp-production
  export RUNNER_OS=Linux
  export RUNNER_ARCH=X64
  export GITHUB_SHA="$VALID_SHA"
  export GITHUB_WORKFLOW_SHA="$VALID_SHA"
  export GITHUB_EVENT_NAME=workflow_run
  export GOLE_PRODUCTION_ENV_SECRET_VERSION=6
}

run_validation() {
  local target="$1" variable_name="${2:-}" variable_value="${3:-}"
  set +e
  (
    set_valid_production_context
    if [ -n "$variable_name" ]; then
      if [ "$variable_value" = __UNSET__ ]; then
        unset "$variable_name"
      else
        export "$variable_name=$variable_value"
      fi
    fi
    bash "$DEPLOY_SCRIPT" "$target"
  ) >"$TEST_ROOT/output" 2>&1
  VALIDATION_STATUS=$?
  set -e
}

assert_accepts() {
  local label="$1" target="$2" variable_name="${3:-}" variable_value="${4:-}"
  run_validation "$target" "$variable_name" "$variable_value"
  if [ "$VALIDATION_STATUS" -ne 0 ]; then
    echo "FAIL: $label" >&2
    cat "$TEST_ROOT/output" >&2
    exit 1
  fi
}

assert_rejects() {
  local label="$1" target="$2" variable_name="${3:-}" variable_value="${4:-}"
  run_validation "$target" "$variable_name" "$variable_value"
  if [ "$VALIDATION_STATUS" -eq 0 ]; then
    echo "FAIL: $label" >&2
    exit 1
  fi
}

assert_accepts "workflow_run 전체 배포 문맥" all
assert_accepts "workflow_dispatch 전체 배포 문맥" all GITHUB_EVENT_NAME workflow_dispatch

assert_rejects "운영 backend 부분 배포 거부" backend
assert_rejects "운영 frontend 부분 배포 거부" frontend
assert_rejects "DEPLOY_SHA 누락 거부" all DEPLOY_SHA __UNSET__
assert_rejects "DEPLOY_SHA 축약값 거부" all DEPLOY_SHA 2222222
assert_rejects "DEPLOY_SHA 대문자값 거부" all DEPLOY_SHA AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
assert_rejects "부모 rollout lock 누락 거부" all GOLE_ROLLOUT_LOCK_HELD __UNSET__
assert_rejects "허용되지 않은 push event 거부" all GITHUB_EVENT_NAME push
assert_rejects "운영 Secret 버전 누락 거부" all GOLE_PRODUCTION_ENV_SECRET_VERSION __UNSET__
assert_rejects "운영 Secret 버전 latest 거부" all GOLE_PRODUCTION_ENV_SECRET_VERSION latest

identity_variables=(
  GITHUB_ACTIONS
  GITHUB_SERVER_URL
  GITHUB_REPOSITORY
  GITHUB_WORKFLOW
  GITHUB_WORKFLOW_REF
  GITHUB_REF
  GITHUB_REF_NAME
  GITHUB_REF_TYPE
  GITHUB_REF_PROTECTED
  GITHUB_JOB
  RUNNER_ENVIRONMENT
  RUNNER_NAME
  RUNNER_OS
  RUNNER_ARCH
  GITHUB_SHA
  GITHUB_WORKFLOW_SHA
)
for variable_name in "${identity_variables[@]}"; do
  assert_rejects "${variable_name} 누락 거부" all "$variable_name" __UNSET__
  assert_rejects "${variable_name} 불일치 거부" all "$variable_name" invalid
done

echo 'Production deploy invocation boundary contract passed.'
