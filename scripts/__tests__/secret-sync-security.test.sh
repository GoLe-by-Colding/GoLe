#!/usr/bin/env bash
set -Eeuo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORKFLOW="$REPO_ROOT/.github/workflows/secret-sync.yml"
APPLY_SCRIPT="$REPO_ROOT/infra/gcp/scripts/apply-secret-env.sh"
DEPLOY_SCRIPT="$REPO_ROOT/scripts/deploy.sh"
HOSTCTL_SCRIPT="$REPO_ROOT/infra/gcp/scripts/gole-hostctl.sh"

python3 - "$WORKFLOW" "$APPLY_SCRIPT" "$DEPLOY_SCRIPT" "$HOSTCTL_SCRIPT" <<'PY'
import pathlib
import sys

workflow = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
apply_script = pathlib.Path(sys.argv[2]).read_text(encoding="utf-8")
deploy_script = pathlib.Path(sys.argv[3]).read_text(encoding="utf-8")
hostctl_script = pathlib.Path(sys.argv[4]).read_text(encoding="utf-8")

checks = {
    "워크플로 입력에 env 본문이 없음": "env_file:" not in workflow.lower()
    and "secret_value:" not in workflow.lower(),
    "Secret Manager 버전만 배포 입력으로 사용함": "secret_version:" in workflow,
    "Secret Sync는 main ref에서만 실행함": "github.ref == 'refs/heads/main'" in workflow,
    "Secret Sync는 고정 운영 대상만 허용함": "inputs.target == 'gole-production'" in workflow,
    "Secret Sync는 root 소유 LKG SHA만 실행함":
    'gole-hostctl deployment-read-sha' in workflow
    and 'git reset --hard "$deployed_sha"' in workflow
    and 'git reset --hard "$GITHUB_SHA"' not in workflow,
    "Secret Sync는 Actions 실행 조회 권한을 명시함": "actions: read" in workflow,
    "Secret Sync가 checkout 전 호스트 rollout lock을 획득함":
    'exec 7>>/run/lock/gole-production-rollout.lock' in workflow
    and workflow.index('exec 7>>/run/lock/gole-production-rollout.lock')
    < workflow.index('git reset --hard "$deployed_sha"'),
    "Secret Sync 실패가 checkout을 LKG로 복원함":
    "restore_checkout_on_failure" in workflow
    and 'git reset --hard "$deployed_sha"' in workflow,
    "Secret Sync가 LKG 스크립트만 실행함":
    'bash /app/infra/gcp/scripts/apply-secret-env.sh' in workflow
    and 'GOLE_ROLLOUT_LOCK_HELD=1' in workflow,
    "Secret 평문은 root 0600 후보에만 저장함":
    'mktemp /etc/gole/.secret.XXXXXXXX' in hostctl_script
    and '--out-file="$ROOT_SECRET_CANDIDATE"' in hostctl_script
    and 'chmod 0600 "$ROOT_SECRET_CANDIDATE"' in hostctl_script
    and "gcloud" not in apply_script,
    "root Secret 조회 stdout과 stderr를 폐기함":
    "--quiet >/dev/null 2>&1" in hostctl_script,
    "Secret Sync 실패 시 컨테이너 로그를 생략함": 'if [ -n "${SECRET_SYNC_REQUEST_ID:-}" ]' in deploy_script,
    "문의 webhook은 전용 Secret 우선·운영방 fallback으로 root overlay에 설치함":
    "secrets.DISCORD_SUPPORT_WEBHOOK_URL" in workflow
    and 'DISCORD_SUPPORT_WEBHOOK_URL:-$operations' in workflow
    and "gole-hostctl discord-overlay-install" in workflow,
    "host 이관 완료 전 Secret Sync job을 차단함":
    "vars.GOLE_PRODUCTION_HOST_READY == 'true'" in workflow,
    "Secret Sync 운영·시드·결제 정책은 root validator가 강제함": all(
        marker in hostctl_script
        for marker in (
            'validate_production_environment "$ROOT_SECRET_CANDIDATE"',
            'validate_production_compose "$ROOT_SECRET_CANDIDATE"',
        )
    ),
    "PortOne·GA/GTM GitHub vars가 root Secret 경계를 우회하지 않음":
    "vars.NEXT_PUBLIC_PORTONE" not in workflow
    and "vars.NEXT_PUBLIC_GA" not in workflow
    and "vars.NEXT_PUBLIC_GTM" not in workflow,
    "환경 복원은 root marker의 LKG SHA만 재배포함":
    'lkg_sha="$(read_deployed_sha)"' in hostctl_script
    and 'restart_strict_lkg_services "$lkg_sha"' in hostctl_script
    and 'recover_environment_services_or_poweroff "$lkg_sha"' in hostctl_script,
    "환경 설치와 version marker가 복구 journal로 묶임": all(
        marker in hostctl_script
        for marker in (
            "recover_environment_transaction",
            "begin_environment_transaction",
            "mark_environment_transaction_ready",
            "commit_environment_transaction",
            "finalize_environment_transaction",
            "abort_environment_transaction",
        )
    ),
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
