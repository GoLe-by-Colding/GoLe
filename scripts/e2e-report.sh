#!/usr/bin/env bash
# GoLe E2E 실행 + 리포트를 gole.kscold.com/test-report/에 배포
# 사용: bash scripts/e2e-report.sh [local|live]
#   local  — 로컬 dev 서버 대상 (기본)
#   live   — https://gole.kscold.com 대상 (read-only, 쓰기 플로우 skip)

set -e
MODE="${1:-local}"
REPORT_DIR="$(cd "$(dirname "$0")/.." && pwd)/apps/web/playwright-report"
BUCKET_DIR="/Users/kscold/Desktop/bucket/playwright-report"

cd "$(dirname "$0")/.."

echo "▶ E2E 실행 (mode=$MODE)"
if [ "$MODE" = "live" ]; then
  E2E_BASE_URL=https://gole.kscold.com pnpm --filter web exec playwright test --reporter=html || true
else
  pnpm --filter web exec playwright test --reporter=html || true
fi

echo "▶ 리포트 배포 → $BUCKET_DIR"
mkdir -p "$BUCKET_DIR"
rsync -a --delete "$REPORT_DIR/" "$BUCKET_DIR/"

echo "✓ 리포트 공개: https://gole.kscold.com/test-report/"
