#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
umask 077
fixture_dir="$(mktemp -d "${TMPDIR:-/tmp}/gole-dev-env-test.XXXXXX")"

cleanup() {
  rm -rf -- "$fixture_dir"
}
trap cleanup EXIT

printf '%s\n' \
  'GOLE_ENVIRONMENT=production' \
  'MONGODB_URI=mongodb://production.invalid/gole' \
  'REDIS_HOST=production.invalid' \
  'STORAGE_S3_ENDPOINT=https://production-storage.invalid' \
  'STORAGE_S3_SECRET_KEY=must-not-cross-the-boundary' \
  'PORTONE_ENABLED=true' \
  'PORTONE_STORE_ID=store-local-contract' \
  'PORTONE_CHANNEL_KEY=channel-local-contract' \
  'COOLSMS_ENABLED=true' \
  'COOLSMS_API_SECRET=fake-local-contract-secret' \
  'SMTP_USERNAME=production-smtp@example.test' \
  'SMTP_PASSWORD=must-not-copy-smtp-secret' \
  > "$fixture_dir/production.env"

# 최신 production Secret에서 빠진 외부 자격증명은 기존 로컬 파일에 남아 있어도
# 되살리지 않는다. 로컬 전용 관리자/MinIO 값만 명시적으로 보존한다.
printf '%s\n' \
  'GOLE_ADMIN_EMAIL=local-admin@example.test' \
  'MINIO_ROOT_USER=existing-local-minio' \
  'GOOGLE_OAUTH_CLIENT_SECRET=stale-local-oauth-secret' \
  > "$fixture_dir/.env"

output="$(
  GOLE_DEV_ENV_ROOT="$fixture_dir" \
    GOLE_SECRET_SOURCE_FILE="$fixture_dir/production.env" \
    GOLE_SECRET_VERSION=42 \
    bash "$ROOT/scripts/sync-dev-env.sh"
)"

assert_line() {
  if ! grep -Fxq "$1" "$fixture_dir/.env"; then
    echo "로컬 환경에 필요한 안전값이 없습니다: $1" >&2
    exit 1
  fi
}

assert_absent() {
  if grep -Fq "$1" "$fixture_dir/.env"; then
    echo "운영 전용 값이 로컬 환경으로 유입됐습니다." >&2
    exit 1
  fi
}

assert_line 'GOLE_ENVIRONMENT=local'
assert_line 'GOLE_ONBOARDING_PHONE_REQUIRED=false'
assert_line 'GOLE_ONBOARDING_LOG_VERIFICATION_CODES=false'
assert_line 'MONGODB_URI=mongodb://localhost:27017/gole?replicaSet=rs0'
assert_line 'STORAGE_S3_ENDPOINT=http://localhost:19000'
assert_line 'PORTONE_ENABLED=false'
assert_line 'COOLSMS_ENABLED=false'
assert_line 'GOLE_DISCORD_ALERTS_ENABLED=false'
assert_line 'GOLE_SETTLEMENT_MODE=DISABLED'
assert_line 'PORTONE_STORE_ID=store-local-contract'
assert_line 'COOLSMS_API_SECRET=fake-local-contract-secret'
assert_line 'GOLE_ADMIN_EMAIL=local-admin@example.test'
assert_line 'MINIO_ROOT_USER=existing-local-minio'
assert_absent 'production.invalid'
assert_absent 'must-not-cross-the-boundary'
assert_absent 'SMTP_USERNAME='
assert_absent 'SMTP_PASSWORD='
assert_absent 'must-not-copy-smtp-secret'
assert_absent 'GOOGLE_OAUTH_CLIENT_SECRET='
assert_absent 'stale-local-oauth-secret'

if printf '%s' "$output" | grep -Fq 'fake-local-contract-secret'; then
  echo "동기화 로그에 비밀값이 노출됐습니다." >&2
  exit 1
fi
if ! grep -Fxq 'version=42' "$fixture_dir/.env.gcp-version"; then
  echo "적용한 Secret 버전 기록이 없습니다." >&2
  exit 1
fi

if stat -f '%Lp' "$fixture_dir/.env" >/dev/null 2>&1; then
  file_mode="$(stat -f '%Lp' "$fixture_dir/.env")"
else
  file_mode="$(stat -c '%a' "$fixture_dir/.env")"
fi
if [ "$file_mode" != "600" ]; then
  echo "로컬 환경 파일 권한이 0600이 아닙니다: ${file_mode}" >&2
  exit 1
fi

# Phone verification is a deliberate local opt-in only.
GOLE_DEV_ENV_ROOT="$fixture_dir" \
  GOLE_SECRET_SOURCE_FILE="$fixture_dir/production.env" \
  GOLE_SECRET_VERSION=43 \
  bash "$ROOT/scripts/sync-dev-env.sh" --enable-phone-verification >/dev/null
assert_line 'GOLE_ONBOARDING_PHONE_REQUIRED=true'

# Repeated syncs retain only the two newest 0600 plaintext backups.
for version in 44 45; do
  GOLE_DEV_ENV_ROOT="$fixture_dir" \
    GOLE_SECRET_SOURCE_FILE="$fixture_dir/production.env" \
    GOLE_SECRET_VERSION="$version" \
    bash "$ROOT/scripts/sync-dev-env.sh" >/dev/null
done
backup_count="$(find "$fixture_dir" -maxdepth 1 -type f -name '.env.backup.*' | wc -l | tr -d ' ')"
[ "$backup_count" = 2 ] || {
  echo "로컬 환경 백업이 정확히 2개가 아닙니다: $backup_count" >&2
  exit 1
}
while IFS= read -r backup; do
  if stat -f '%Lp' "$backup" >/dev/null 2>&1; then
    backup_mode="$(stat -f '%Lp' "$backup")"
  else
    backup_mode="$(stat -c '%a' "$backup")"
  fi
  [ "$backup_mode" = 600 ] || {
    echo "로컬 환경 백업 권한이 0600이 아닙니다." >&2
    exit 1
  }
done < <(find "$fixture_dir" -maxdepth 1 -type f -name '.env.backup.*')

echo "맥 개발 환경 동기화 보안 계약 통과"
