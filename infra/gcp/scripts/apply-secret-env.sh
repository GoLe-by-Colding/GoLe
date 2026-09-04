#!/usr/bin/env bash
set -Eeuo pipefail

TARGET="${1:-}"
SECRET_VERSION="${2:-}"
REQUEST_ID="${3:-}"
PROJECT_ID="${GCP_PROJECT_ID:-project-72a52bf1-06aa-4519-b2c}"
SECRET_NAME="${GOLE_SECRET_NAME:-gole-production-env}"
APP_ENV_FILE="/etc/gole/gole.env"
INFRA_ENV_FILE="/etc/gole/infra.env"
BACKUP_DIR="/var/backups/gole-env"
ROOT="/app"

if [ "$TARGET" != "gole-production" ]; then
  echo "지원하지 않는 배포 대상입니다." >&2
  exit 2
fi
if [[ ! "$SECRET_VERSION" =~ ^[0-9]+$ ]]; then
  echo "Secret Manager 버전은 숫자여야 합니다." >&2
  exit 2
fi
if [[ ! "$REQUEST_ID" =~ ^[0-9a-fA-F-]{36}$ ]]; then
  echo "배포 요청 ID가 올바르지 않습니다." >&2
  exit 2
fi

umask 077
candidate="$(mktemp /tmp/gole-env.XXXXXX)"
rollback_file=""
cleanup() {
  rm -f "$candidate"
}
trap cleanup EXIT

echo "GoLe 환경 변수 동기화 시작: target=${TARGET}, version=${SECRET_VERSION}, request=${REQUEST_ID}"

# --out-file을 사용해 시크릿 평문이 stdout이나 Actions 로그로 흐르지 않게 한다.
if ! gcloud secrets versions access "$SECRET_VERSION" \
  --secret="$SECRET_NAME" \
  --project="$PROJECT_ID" \
  --out-file="$candidate" \
  --quiet >/dev/null 2>&1; then
  echo "Secret Manager 버전을 가져오지 못했습니다." >&2
  exit 1
fi
chmod 0600 "$candidate"

python3 - "$candidate" <<'PY'
import pathlib
import re
import sys

path = pathlib.Path(sys.argv[1])
raw = path.read_bytes()
if not raw or len(raw) > 128 * 1024 or b"\x00" in raw:
    raise SystemExit("환경 변수 파일 크기 또는 내용이 올바르지 않습니다.")
try:
    text = raw.decode("utf-8-sig").replace("\r\n", "\n").replace("\r", "\n")
except UnicodeDecodeError as exc:
    raise SystemExit("환경 변수 파일은 UTF-8이어야 합니다.") from exc

keys = set()
for number, line in enumerate(text.splitlines(), start=1):
    stripped = line.lstrip()
    if not stripped or stripped.startswith("#"):
        continue
    match = re.fullmatch(r"([A-Za-z_][A-Za-z0-9_]*)=(.*)", line)
    if not match:
        raise SystemExit(f"환경 변수 {number}번째 줄 형식이 올바르지 않습니다.")
    key = match.group(1)
    if key in keys:
        raise SystemExit(f"중복 환경 변수 키가 있습니다: {key}")
    keys.add(key)

required = {"MONGODB_URI", "MONGODB_DATABASE", "REDIS_HOST", "REDIS_PORT", "GOLE_ENVIRONMENT"}
missing = sorted(required - keys)
if missing:
    raise SystemExit("필수 환경 변수 키가 없습니다: " + ", ".join(missing))

path.write_text(text if text.endswith("\n") else text + "\n", encoding="utf-8")
PY

cd "$ROOT"
if ! docker compose \
  --env-file "$INFRA_ENV_FILE" \
  --env-file "$candidate" \
  -f infra/gcp/docker-compose.yml config --quiet >/dev/null 2>&1; then
  echo "새 환경 변수로 Docker Compose 구성을 만들 수 없습니다." >&2
  exit 1
fi

sudo install -d -m 0700 -o root -g root "$BACKUP_DIR"
if sudo test -f "$APP_ENV_FILE"; then
  rollback_file="${BACKUP_DIR}/gole.env.$(date -u +%Y%m%dT%H%M%SZ).v${SECRET_VERSION}.${REQUEST_ID}"
  sudo cp "$APP_ENV_FILE" "$rollback_file"
  sudo chown root:root "$rollback_file"
  sudo chmod 0600 "$rollback_file"
fi

sudo install -m 0640 -o root -g kscold "$candidate" "$APP_ENV_FILE"

if ! DEPLOY_SHA="${GITHUB_SHA:-}" bash "$ROOT/scripts/deploy.sh" backend; then
  echo "새 환경 변수 배포에 실패해 직전 파일로 롤백합니다." >&2
  if [ -z "$rollback_file" ] || ! sudo test -f "$rollback_file"; then
    echo "복원할 서버 백업이 없어 자동 롤백할 수 없습니다." >&2
    exit 1
  fi

  sudo install -m 0640 -o root -g kscold "$rollback_file" "$APP_ENV_FILE"
  if ! DEPLOY_SHA="${GITHUB_SHA:-}" bash "$ROOT/scripts/deploy.sh" backend; then
    echo "직전 환경 변수 파일 복원 후 재기동도 실패했습니다." >&2
    exit 1
  fi
  echo "직전 환경 변수로 서비스는 복구됐지만 요청한 버전 배포는 실패했습니다." >&2
  exit 1
fi

printf '%s\n' "$SECRET_VERSION" | sudo tee /etc/gole/gole.env.version >/dev/null
sudo chmod 0644 /etc/gole/gole.env.version
sudo find "$BACKUP_DIR" -type f -name 'gole.env.*' -mtime +30 -delete

echo "GoLe 환경 변수 동기화 완료: version=${SECRET_VERSION}, request=${REQUEST_ID}"
