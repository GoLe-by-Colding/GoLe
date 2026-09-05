#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="${GOLE_DEV_ENV_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
PROJECT_ID="${GCP_PROJECT_ID:-project-72a52bf1-06aa-4519-b2c}"
SECRET_NAME="${GOLE_SECRET_NAME:-gole-production-env}"
IMPERSONATE_SERVICE_ACCOUNT="${GOLE_SECRET_SERVICE_ACCOUNT:-kscold-control-secrets@${PROJECT_ID}.iam.gserviceaccount.com}"
TARGET_FILE="${ROOT}/.env"
VERSION_FILE="${ROOT}/.env.gcp-version"
PHONE_REQUIRED=false

usage() {
  echo "usage: scripts/sync-dev-env.sh [--enable-phone-verification]" >&2
  exit 2
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --enable-phone-verification)
      PHONE_REQUIRED=true
      shift
      ;;
    *) usage ;;
  esac
done

for command_name in python3 install; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "필수 명령을 찾지 못했습니다: ${command_name}" >&2
    exit 1
  fi
done

umask 077
work_dir="$(mktemp -d "${TMPDIR:-/tmp}/gole-dev-env.XXXXXX")"
secret_file="${work_dir}/production.env"
generated_file="${work_dir}/local.env"

cleanup() {
  rm -rf -- "$work_dir"
}
trap cleanup EXIT

# 테스트에서는 로컬 fixture를 명시할 수 있다. 실제 사용 시에는 Secret Manager의 최신
# ENABLED 버전을 먼저 숫자로 고정한 다음 그 버전만 내려받아 도중 교체 경쟁을 막는다.
if [ -n "${GOLE_SECRET_SOURCE_FILE:-}" ]; then
  secret_version="${GOLE_SECRET_VERSION:-local}"
  cp -- "$GOLE_SECRET_SOURCE_FILE" "$secret_file"
else
  if ! command -v gcloud >/dev/null 2>&1; then
    echo "gcloud CLI를 찾지 못했습니다." >&2
    exit 1
  fi

  secret_version="$(
    gcloud secrets versions list "$SECRET_NAME" \
      --project="$PROJECT_ID" \
      --impersonate-service-account="$IMPERSONATE_SERVICE_ACCOUNT" \
      --filter='state=ENABLED' \
      --sort-by='~createTime' \
      --limit=1 \
      --format='value(name)' \
      --quiet 2>/dev/null
  )"
  if [[ ! "$secret_version" =~ ^[0-9]+$ ]]; then
    echo "Secret Manager의 최신 ENABLED 버전을 확인하지 못했습니다." >&2
    exit 1
  fi

  if ! gcloud secrets versions access "$secret_version" \
    --secret="$SECRET_NAME" \
    --project="$PROJECT_ID" \
    --impersonate-service-account="$IMPERSONATE_SERVICE_ACCOUNT" \
    --out-file="$secret_file" \
    --quiet >/dev/null 2>&1; then
    echo "Secret Manager 버전을 내려받지 못했습니다." >&2
    exit 1
  fi
fi
chmod 0600 "$secret_file"

# 운영 파일은 절대 source하지 않는다. 로컬에서도 검증할 외부 연동 값만 허용목록으로
# 복사하고, SMTP 자격증명·데이터 저장소 주소와 실제 발송·결제·정산 플래그는 제외하거나
# 로컬 안전값으로 강제한다.
imported_count="$(python3 - "$secret_file" "$TARGET_FILE" "$generated_file" "$PHONE_REQUIRED" <<'PY'
import pathlib
import secrets
import sys

source_path = pathlib.Path(sys.argv[1])
existing_path = pathlib.Path(sys.argv[2])
target_path = pathlib.Path(sys.argv[3])
phone_required = sys.argv[4]
if phone_required not in {"true", "false"}:
    raise SystemExit("전화 인증 opt-in 값이 올바르지 않습니다.")


def parse_env(path: pathlib.Path, *, required: bool) -> dict[str, str]:
    if not path.exists():
        if required:
            raise SystemExit("환경 변수 원본을 찾지 못했습니다.")
        return {}
    raw = path.read_bytes()
    if not raw or len(raw) > 128 * 1024 or b"\x00" in raw:
        raise SystemExit("환경 변수 파일 크기 또는 내용이 올바르지 않습니다.")
    try:
        text = raw.decode("utf-8-sig").replace("\r\n", "\n").replace("\r", "\n")
    except UnicodeDecodeError as exc:
        raise SystemExit("환경 변수 파일은 UTF-8이어야 합니다.") from exc

    result: dict[str, str] = {}
    for number, line in enumerate(text.splitlines(), start=1):
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        key, separator, value = line.partition("=")
        if (
            not separator
            or not key
            or not (key[0].isalpha() or key[0] == "_")
            or not all(character.isalnum() or character == "_" for character in key)
        ):
            raise SystemExit(f"환경 변수 {number}번째 줄 형식이 올바르지 않습니다.")
        if key in result:
            raise SystemExit(f"중복 환경 변수 키가 있습니다: {key}")
        result[key] = value
    return result


production = parse_env(source_path, required=True)
existing = parse_env(existing_path, required=False)
required_production_keys = {
    "GOLE_ENVIRONMENT",
    "MONGODB_URI",
    "REDIS_HOST",
    "STORAGE_S3_ENDPOINT",
}
missing = sorted(required_production_keys - production.keys())
if missing:
    raise SystemExit("운영 환경 원본에 필수 키가 없습니다: " + ", ".join(missing))

# 값이 추가되더라도 자동으로 로컬에 유입되지 않도록 명시적 allowlist를 유지한다.
external_credentials = (
    "PORTONE_API_SECRET",
    "PORTONE_WEBHOOK_SECRET",
    "PORTONE_STORE_ID",
    "PORTONE_CHANNEL_KEY",
    "PORTONE_CARD_CHANNEL_KEY",
    "PORTONE_CHANNEL_TYPE",
    "COOLSMS_API_KEY",
    "COOLSMS_API_SECRET",
    "COOLSMS_PF_ID",
    "GOOGLE_OAUTH_CLIENT_ID",
    "GOOGLE_OAUTH_CLIENT_SECRET",
    "KAKAO_OAUTH_CLIENT_ID",
    "KAKAO_OAUTH_CLIENT_SECRET",
    "NAVER_OAUTH_CLIENT_ID",
    "NAVER_OAUTH_CLIENT_SECRET",
    "SMTP_HOST",
    "SMTP_PORT",
    "SHIPPING_TRACKER_CLIENT_ID",
    "SHIPPING_TRACKER_CLIENT_SECRET",
    "SHIPPING_TRACKER_API_BASE",
)

minio_user = existing.get("MINIO_ROOT_USER") or "gole-local"
minio_password = existing.get("MINIO_ROOT_PASSWORD") or secrets.token_urlsafe(32)
local_values = {
    "MONGODB_PORT": "27017",
    "REDIS_PORT": "6379",
    "MINIO_API_PORT": "19000",
    "MINIO_CONSOLE_PORT": "19001",
    "MINIO_ROOT_USER": minio_user,
    "MINIO_ROOT_PASSWORD": minio_password,
    "SERVER_PORT": "8080",
    "MONGODB_URI": "mongodb://localhost:27017/gole?replicaSet=rs0",
    "MONGODB_DATABASE": "gole",
    "REDIS_HOST": "localhost",
    "STORAGE_S3_ENDPOINT": "http://localhost:19000",
    "STORAGE_S3_ACCESS_KEY": minio_user,
    "STORAGE_S3_SECRET_KEY": minio_password,
    "STORAGE_S3_BUCKET": "gole-local",
    "STORAGE_S3_REGION": "us-east-1",
    "STORAGE_PUBLIC_BASE_URL": "http://localhost:8080",
    "GOLE_ENVIRONMENT": "local",
    "GOLE_ONBOARDING_PHONE_REQUIRED": phone_required,
    "GOLE_ONBOARDING_LOG_VERIFICATION_CODES": "false",
    "GOLE_WEB_ALLOWED_ORIGINS": "http://localhost:3000",
    "GOLE_SESSION_COOKIE_SECURE": "false",
    "GOLE_VERIFICATION_EMAIL_ENABLED": "false",
    "GOLE_MAIL_HEALTH_ENABLED": "false",
    "PORTONE_ENABLED": "false",
    "GOLE_SETTLEMENT_MODE": "DISABLED",
    "GOLE_SETTLEMENT_PAYOUT_CONTRACT_VERIFIED": "false",
    "COOLSMS_ENABLED": "false",
    "SHIPPING_TRACKER_ENABLED": "false",
    "GOLE_DISCORD_ALERTS_ENABLED": "false",
    "DISCORD_SUPPRESS_NOTIFICATIONS": "true",
    "GOLE_CATALOG_SEED": "true",
    "GOLE_LISTING_SEED": "true",
    "GOLE_PRICING_SEED": "true",
    "GOLE_PRICING_INCLUDE_DEMO": "true",
    "GOLE_PRICING_INCLUDE_LEGACY": "true",
    "GOLE_COMMUNITY_SEED": "true",
    "GOLE_REPORT_SEED": "true",
    "GOLE_REVIEW_SEED": "true",
    "GOLE_MEDIA_SEED": "true",
}

# 로컬에서만 쓰는 관리자 계정은 기존 값을 보존하되 운영 관리자 비밀은 가져오지 않는다.
for key in ("GOLE_ADMIN_EMAIL", "GOLE_ADMIN_PASSWORD"):
    if key in existing:
        local_values[key] = existing[key]

imported = 0
for key in external_credentials:
    if key in production:
        local_values[key] = production[key]
        imported += 1

lines = [
    "# scripts/sync-dev-env.sh가 생성한 맥 개발 전용 환경입니다.",
    "# 외부 연동 자격증명은 최신 GCP Secret에서 가져오되 모든 부작용 기능은 꺼져 있습니다.",
]
lines.extend(f"{key}={value}" for key, value in local_values.items())
target_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(imported)
PY
)"
chmod 0600 "$generated_file"

secret_hash="$(python3 - "$secret_file" <<'PY'
import hashlib
import pathlib
import sys

print(hashlib.sha256(pathlib.Path(sys.argv[1]).read_bytes()).hexdigest())
PY
)"

if [ -f "$TARGET_FILE" ]; then
  backup_file="${TARGET_FILE}.backup.$(date -u +%Y%m%dT%H%M%SZ).$$"
  cp -p -- "$TARGET_FILE" "$backup_file"
  chmod 0600 "$backup_file"
fi

# Plaintext development backups are useful for one-step recovery but must not
# accumulate forever. Keep exactly the two newest regular, non-symlink files.
python3 - "$ROOT" <<'PY'
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1]).resolve()
pattern = re.compile(r"^\.env\.backup\.\d{8}T\d{6}Z\.\d+$")
backups = []
for path in root.glob(".env.backup.*"):
    if not pattern.fullmatch(path.name) or path.is_symlink() or not path.is_file():
        continue
    path.chmod(0o600)
    backups.append(path)
backups.sort(key=lambda item: (item.stat().st_mtime_ns, item.name), reverse=True)
for stale in backups[2:]:
    stale.unlink()
PY

next_target="${TARGET_FILE}.next.$$"
next_version="${VERSION_FILE}.next.$$"
install -m 0600 "$generated_file" "$next_target"
printf 'version=%s\nsha256=%s\n' "$secret_version" "$secret_hash" > "$next_version"
chmod 0600 "$next_version"
mv -f -- "$next_target" "$TARGET_FILE"
mv -f -- "$next_version" "$VERSION_FILE"

echo "맥 개발 환경 동기화 완료: GCP Secret v${secret_version}, 외부 연동 키 ${imported_count}개"
echo "실제 결제·문자·메일·Discord·배송 조회는 모두 비활성 상태입니다."
