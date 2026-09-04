#!/usr/bin/env bash
set -Eeuo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORKFLOW="$REPO_ROOT/.github/workflows/secret-sync.yml"
APPLY_SCRIPT="$REPO_ROOT/infra/gcp/scripts/apply-secret-env.sh"
DEPLOY_SCRIPT="$REPO_ROOT/scripts/deploy.sh"

python3 - "$WORKFLOW" "$APPLY_SCRIPT" "$DEPLOY_SCRIPT" <<'PY'
import pathlib
import sys

workflow = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
apply_script = pathlib.Path(sys.argv[2]).read_text(encoding="utf-8")
deploy_script = pathlib.Path(sys.argv[3]).read_text(encoding="utf-8")

checks = {
    "워크플로 입력에 env 본문이 없음": "env_file:" not in workflow.lower()
    and "secret_value:" not in workflow.lower(),
    "Secret Manager 버전만 배포 입력으로 사용함": "secret_version:" in workflow,
    "Secret 평문을 임시 파일로 직접 저장함": '--out-file="$candidate"' in apply_script,
    "Secret 조회 stdout과 stderr를 폐기함": "--quiet >/dev/null 2>&1" in apply_script,
    "Secret Sync 실패 시 컨테이너 로그를 생략함": 'if [ -n "${SECRET_SYNC_REQUEST_ID:-}" ]' in deploy_script,
    "배포 스크립트에서 xtrace를 사용하지 않음": "set -x" not in apply_script
    and "set -o xtrace" not in apply_script
    and "set -x" not in deploy_script
    and "set -o xtrace" not in deploy_script,
}

failed = []
for label, passed in checks.items():
    print(f"  {'ok  ' if passed else 'FAIL'} {label}")
    if not passed:
        failed.append(label)

raise SystemExit(1 if failed else 0)
PY

printf 'Secret Sync 로그 보안 계약 테스트 통과\n'
