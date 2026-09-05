#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

assert_private_compose() {
  local compose_file="$1"
  if grep -Eq 'mc[[:space:]]+anonymous[[:space:]]+set[[:space:]]+(download|public)' "$compose_file"; then
    echo "FAIL: MinIO anonymous download policy is forbidden: $compose_file" >&2
    exit 1
  fi
  grep -Eq 'mc[[:space:]]+anonymous[[:space:]]+set[[:space:]]+none' "$compose_file" || {
    echo "FAIL: MinIO init must idempotently remove an existing anonymous policy: $compose_file" >&2
    exit 1
  }
}

assert_private_compose "$ROOT/docker-compose.yml"
assert_private_compose "$ROOT/infra/gcp/docker-compose.yml"

grep -Fq 'docker compose run --rm --no-deps minio-init' "$ROOT/package.json" || {
  echo "FAIL: local startup must remove legacy anonymous bucket policy" >&2
  exit 1
}

if python3 - "$ROOT/apps/web/next.config.ts" <<'PY'
import pathlib
import re
import sys

source = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
raise SystemExit(0 if re.search(r"img-src[^\n;]*(?<!\S)https:(?!//)", source) else 1)
PY
then
  echo "FAIL: wildcard HTTPS images bypass the same-origin media trust boundary" >&2
  exit 1
fi

echo "PASS: media storage and browser image sources are private by default"
