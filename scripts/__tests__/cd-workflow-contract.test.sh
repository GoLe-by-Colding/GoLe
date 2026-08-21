#!/usr/bin/env bash
#
# cd.yml 운영 알림 계약 테스트 — 워크플로를 실행하지 않고 정적으로만 검증한다.
#
#   실행:  bash scripts/__tests__/cd-workflow-contract.test.sh
#
# 검증하는 계약:
#   1. 배포 step이 DISCORD_SUPPRESS_NOTIFICATIONS를 명시적으로 주입한다.
#      (누락되면 application.yml 기본값 true가 이겨 ERROR 경보까지 무음이 된다)
#   2. 배포 후 readiness 실패 알림은 deploy.sh가 이미 알린 경우와 겹치지 않는다.
#      (배포 실패 알림은 어느 경로로든 실행당 정확히 한 건)
#   3. webhook URL은 secrets 참조로만 등장한다 — 리터럴 URL이 커밋되면 실패시킨다.
set -Eeuo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORKFLOW="$REPO_ROOT/.github/workflows/cd.yml"

if ! python3 -c 'import yaml' >/dev/null 2>&1; then
  printf '· python3 + PyYAML 없음 — cd.yml 계약 테스트를 건너뛴다\n'
  exit 0
fi

python3 - "$WORKFLOW" <<'PY'
import sys, yaml

workflow = yaml.safe_load(open(sys.argv[1], encoding="utf-8"))
steps = workflow["jobs"]["deploy"]["steps"]
by_id = {s.get("id"): s for s in steps if s.get("id")}
failures = []


def check(label, ok, detail=""):
    if ok:
        print(f"  ok   {label}")
    else:
        print(f"  FAIL {label}{' — ' + detail if detail else ''}")
        failures.append(label)


deploy = by_id.get("deploy")
check("배포 step에 id: deploy 가 있다", deploy is not None)

if deploy:
    env = deploy.get("env", {})
    check(
        "배포 step이 DISCORD_SUPPRESS_NOTIFICATIONS를 명시한다",
        "DISCORD_SUPPRESS_NOTIFICATIONS" in env,
        "누락되면 운영 ERROR 경보가 전부 무음으로 나간다",
    )
    value = str(env.get("DISCORD_SUPPRESS_NOTIFICATIONS", ""))
    check(
        "무음 기본값이 false 다",
        "'false'" in value or value.strip() == "false",
        f"실제: {value!r}",
    )

notify = [s for s in steps if "readiness" in s.get("name", "").lower()
          and s.get("if")]
check("배포 후 readiness 실패 알림 step이 있다", len(notify) == 1)

if len(notify) == 1:
    condition = notify[0]["if"]
    check(
        "알림 조건이 deploy 성공 케이스로 좁혀져 있다",
        "steps.deploy.outcome == 'success'" in condition,
        f"실제: {condition!r} — deploy.sh 실패 알림과 중복될 수 있다",
    )
    check("알림 조건이 failure() 에서만 돈다", "failure()" in condition)

raw = open(sys.argv[1], encoding="utf-8").read()
check(
    "webhook 리터럴 URL이 없다",
    "discord.com/api/webhooks" not in raw,
    "webhook URL은 secrets 로만 주입해야 한다",
)

sys.exit(1 if failures else 0)
PY

printf '✔ cd.yml 운영 알림 계약 테스트 통과\n'
