#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
MINIO_CONTAINER="gole-backup-test-$(date +%s)-$$"
# 운영(backup-data.sh)은 아직 quay 캐시 이미지를 쓴다. quay 에서 사라져 CI 는 같은 릴리스를 소스에서
# 빌드한 GHCR 이미지로 같은 백업 헬퍼를 검증한다 → infra/images/minio/README.md
MC_IMAGE="ghcr.io/gole-by-colding/mc:RELEASE.2025-08-13T08-35-41Z@sha256:47a390e3b5f38ad99c025f1f956b04344feee74f802673f9379452b6e617b69f"
MINIO_ADMIN_TIMEOUT=30s
MINIO_ADMIN_KILL_AFTER=5s
export MINIO_ROOT_USER=gole-backup-test
export MINIO_ROOT_PASSWORD=gole-backup-test-only-password

# Exercise the production helper bodies against disposable real MinIO, not a
# Docker mock. Only replace the host's secret file with these test credentials.
timeout() {
  local args=()
  while [ "$#" -gt 0 ]; do
    if [ "$1" = --env-file ]; then
      [ "$2" = /etc/gole/infra.env ] || return 1
      args+=(--env MINIO_ROOT_USER --env MINIO_ROOT_PASSWORD)
      shift 2
    else
      args+=("$1")
      shift
    fi
  done
  set -- "${args[@]}"
  if type -P timeout >/dev/null 2>&1; then
    command timeout "$@"
  elif command -v gtimeout >/dev/null 2>&1; then
    command gtimeout "$@"
  else
    # macOS has no GNU timeout; bound the test client without changing the
    # production Linux timeout behavior. These are the helper's fixed options.
    [ "$1" = --foreground ] && [ "$2" = --kill-after=5s ] && [ "$3" = 30s ]
    shift 3
    python3 -c 'import subprocess, sys; sys.exit(subprocess.run(sys.argv[1:], timeout=35).returncode)' "$@"
  fi
}
# Load only the two reviewed functions, without running the root-only script.
eval "$(sed -n '/^run_minio_freeze()/,/^write_minio_recovery_marker()/p' \
  "$ROOT/infra/gcp/scripts/backup-data.sh" | sed '$d')"
cleanup() {
  command docker rm -f "$MINIO_CONTAINER" >/dev/null 2>&1 || true
}
trap cleanup EXIT
command docker run -d --name "$MINIO_CONTAINER" \
  --env MINIO_ROOT_USER --env MINIO_ROOT_PASSWORD \
  ghcr.io/gole-by-colding/minio:RELEASE.2025-09-07T16-13-09Z@sha256:fd4bf518becea030ae14e629d74ac3c41182c548689c3896937b72439d2d1ef8 \
  server /data >/dev/null
for attempt in $(seq 1 30); do
  if run_minio_unfreeze_and_prove >/dev/null 2>&1; then break; fi
  [ "$attempt" -lt 30 ] || { run_minio_unfreeze_and_prove; exit 1; }
  sleep 1
done
run_minio_freeze
run_minio_unfreeze_and_prove
echo 'Real MinIO backup freeze/unfreeze and S3 proof passed without a gole_data network.'
