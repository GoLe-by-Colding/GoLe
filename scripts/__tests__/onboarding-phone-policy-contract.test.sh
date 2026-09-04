#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

assert_contains() {
  local file="$1"
  local expected="$2"
  if ! grep -Fq -- "$expected" "$ROOT/$file"; then
    echo "$file 에 온보딩 전화 정책 계약이 없습니다: $expected" >&2
    exit 1
  fi
}

# 맥 개발과 실제 GCP 공개 서버 모두 알림톡 준비 전 전화 단계를 기본 비활성화한다.
# 로컬 4단계 검증은 sync 스크립트의 명시적 opt-in 옵션으로만 켠다.
# 아래 문자열은 셸 치환 대상이 아니라 application.yml에 남아 있어야 할 리터럴 계약이다.
# shellcheck disable=SC2016
assert_contains 'apps/api/src/main/resources/application.yml' \
  'phone-verification-required: ${GOLE_ONBOARDING_PHONE_REQUIRED:true}'
assert_contains 'scripts/sync-dev-env.sh' \
  '"GOLE_ONBOARDING_PHONE_REQUIRED": phone_required'
assert_contains 'scripts/sync-dev-env.sh' '--enable-phone-verification'
assert_contains 'infra/gcp/gole.env.example' 'GOLE_ONBOARDING_PHONE_REQUIRED=false'
assert_contains 'infra/gcp/docker-compose.yml' \
  'GOLE_ONBOARDING_PHONE_REQUIRED: "false"'
assert_contains '.github/workflows/cd.yml' 'GOLE_ONBOARDING_PHONE_REQUIRED: "false"'
assert_contains 'infra/gcp/scripts/validate-production-env.py' \
  '"GOLE_ONBOARDING_PHONE_REQUIRED": "false"'
assert_contains '.github/workflows/secret-sync.yml' \
  'Secret Manager의 exact version을 root-owned 후보로 받아 validator를 통과시킨다.'

# 어느 배포 경로에서도 OTP 원문 로그가 암묵적으로 켜지면 안 된다.
# shellcheck disable=SC2016
assert_contains 'apps/api/src/main/resources/application.yml' \
  'log-verification-codes: ${GOLE_ONBOARDING_LOG_VERIFICATION_CODES:false}'
assert_contains 'infra/gcp/gole.env.example' 'GOLE_ONBOARDING_LOG_VERIFICATION_CODES=false'
assert_contains 'infra/gcp/docker-compose.yml' 'GOLE_ONBOARDING_LOG_VERIFICATION_CODES: "false"'
assert_contains '.github/workflows/cd.yml' 'GOLE_ONBOARDING_LOG_VERIFICATION_CODES: "false"'

echo '온보딩 전화 정책 환경 계약 통과'
