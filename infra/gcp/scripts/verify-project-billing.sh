#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_ID=""
BILLING_ACCOUNT_ID=""

usage() {
  cat >&2 <<'EOF'
Usage: verify-project-billing.sh --project PROJECT_ID --billing-account 000000-000000-000000

Reads Cloud Billing project metadata and fails unless the project is linked to
the exact enabled billing account. This command does not mutate Google Cloud.
EOF
}

while (($#)); do
  case "$1" in
    --project)
      (($# >= 2)) || { usage; exit 2; }
      PROJECT_ID="$2"
      shift 2
      ;;
    --billing-account)
      (($# >= 2)) || { usage; exit 2; }
      BILLING_ACCOUNT_ID="$2"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      usage
      exit 2
      ;;
  esac
done

[[ "$PROJECT_ID" =~ ^[a-z][a-z0-9-]{4,28}[a-z0-9]$ ]] || {
  echo '유효한 GCP project ID가 필요함' >&2
  exit 2
}
[[ "$BILLING_ACCOUNT_ID" =~ ^[0-9A-F]{6}-[0-9A-F]{6}-[0-9A-F]{6}$ ]] || {
  echo '결제 계정 ID는 000000-000000-000000 형식이어야 함' >&2
  exit 2
}

for command_name in gcloud python3; do
  command -v "$command_name" >/dev/null 2>&1 || {
    echo "필수 명령을 찾을 수 없음: $command_name" >&2
    exit 1
  }
done

# Do not echo or persist the response. Besides avoiding accidental disclosure,
# parsing the typed JSON prevents a missing/false billingEnabled value from
# being accepted as a truthy string.
gcloud billing projects describe "$PROJECT_ID" \
  --project="$PROJECT_ID" --format=json --quiet |
  python3 -c '
import json
import sys

project_id = sys.argv[1]
expected_account = f"billingAccounts/{sys.argv[2]}"
expected_name = f"projects/{project_id}/billingInfo"

try:
    document = json.load(sys.stdin)
except (json.JSONDecodeError, UnicodeDecodeError):
    raise SystemExit("Cloud Billing 응답이 유효한 JSON이 아님")

if not isinstance(document, dict):
    raise SystemExit("Cloud Billing 응답 형식이 객체가 아님")
if document.get("name") != expected_name:
    raise SystemExit("Cloud Billing 응답의 project resource가 요청값과 다름")
if document.get("projectId") != project_id:
    raise SystemExit("Cloud Billing 응답의 projectId가 요청값과 다름")
if document.get("billingAccountName") != expected_account:
    raise SystemExit("프로젝트가 검토된 결제 계정에 연결되어 있지 않음")
if document.get("billingEnabled") is not True:
    raise SystemExit("프로젝트 결제가 활성화되어 있지 않음")
' "$PROJECT_ID" "$BILLING_ACCOUNT_ID"

printf '프로젝트 결제 연결 검증을 통과함: %s -> billingAccounts/%s\n' \
  "$PROJECT_ID" "$BILLING_ACCOUNT_ID"
