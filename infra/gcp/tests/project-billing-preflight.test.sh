#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
SANDBOX="$(mktemp -d)"
cleanup() { rm -rf -- "$SANDBOX"; }
trap cleanup EXIT
mkdir -p "$SANDBOX/bin"

write_response() {
  printf '%s\n' "$1" > "$SANDBOX/response.json"
}

cat > "$SANDBOX/bin/gcloud" <<'SH'
#!/usr/bin/env bash
set -Eeuo pipefail
printf '%s\n' "$*" >> "$FAKE_GCLOUD_LOG"
[[ "$1 $2 $3" == 'billing projects describe' ]] || exit 91
[ "${FAKE_GCLOUD_FAIL:-0}" != 1 ] || exit 42
cat "$FAKE_BILLING_RESPONSE"
SH
chmod 0755 "$SANDBOX/bin/gcloud"

export PATH="$SANDBOX/bin:$PATH"
export FAKE_GCLOUD_LOG="$SANDBOX/gcloud.log"
export FAKE_BILLING_RESPONSE="$SANDBOX/response.json"
SCRIPT="$ROOT/infra/gcp/scripts/verify-project-billing.sh"
PROJECT_ID=project-test-123
BILLING_ACCOUNT_ID=01B490-1BC53A-33E611
EXPECTED_NAME="projects/${PROJECT_ID}/billingInfo"
EXPECTED_ACCOUNT="billingAccounts/${BILLING_ACCOUNT_ID}"

write_response "{\"name\":\"$EXPECTED_NAME\",\"projectId\":\"$PROJECT_ID\",\"billingAccountName\":\"$EXPECTED_ACCOUNT\",\"billingEnabled\":true}"
bash "$SCRIPT" --project "$PROJECT_ID" --billing-account "$BILLING_ACCOUNT_ID" >/dev/null
grep -Fxq "billing projects describe $PROJECT_ID --project=$PROJECT_ID --format=json --quiet" \
  "$FAKE_GCLOUD_LOG"

assert_rejected() {
  description="$1"
  response="$2"
  write_response "$response"
  if output="$(bash "$SCRIPT" --project "$PROJECT_ID" \
    --billing-account "$BILLING_ACCOUNT_ID" 2>&1)"; then
    printf '잘못된 결제 응답을 허용함: %s\n' "$description" >&2
    exit 1
  fi
  if [[ "$output" == *DO_NOT_PRINT_THIS_VALUE* ]]; then
    echo '검증 실패 시 원본 응답의 추가 필드를 출력함' >&2
    exit 1
  fi
}

assert_rejected '다른 project resource' \
  "{\"name\":\"projects/other-project/billingInfo\",\"projectId\":\"$PROJECT_ID\",\"billingAccountName\":\"$EXPECTED_ACCOUNT\",\"billingEnabled\":true,\"credential\":\"DO_NOT_PRINT_THIS_VALUE\"}"
assert_rejected '다른 projectId' \
  "{\"name\":\"$EXPECTED_NAME\",\"projectId\":\"other-project\",\"billingAccountName\":\"$EXPECTED_ACCOUNT\",\"billingEnabled\":true}"
assert_rejected '다른 결제 계정' \
  "{\"name\":\"$EXPECTED_NAME\",\"projectId\":\"$PROJECT_ID\",\"billingAccountName\":\"billingAccounts/AAAAAA-BBBBBB-CCCCCC\",\"billingEnabled\":true}"
assert_rejected '결제 비활성화' \
  "{\"name\":\"$EXPECTED_NAME\",\"projectId\":\"$PROJECT_ID\",\"billingAccountName\":\"$EXPECTED_ACCOUNT\",\"billingEnabled\":false}"
assert_rejected '문자열 true' \
  "{\"name\":\"$EXPECTED_NAME\",\"projectId\":\"$PROJECT_ID\",\"billingAccountName\":\"$EXPECTED_ACCOUNT\",\"billingEnabled\":\"true\"}"
assert_rejected '필드 누락' \
  "{\"name\":\"$EXPECTED_NAME\",\"projectId\":\"$PROJECT_ID\",\"billingEnabled\":true}"
assert_rejected 'JSON 아닌 응답' 'not-json'

write_response "{\"name\":\"$EXPECTED_NAME\",\"projectId\":\"$PROJECT_ID\",\"billingAccountName\":\"$EXPECTED_ACCOUNT\",\"billingEnabled\":true}"
if FAKE_GCLOUD_FAIL=1 bash "$SCRIPT" --project "$PROJECT_ID" \
  --billing-account "$BILLING_ACCOUNT_ID" >/dev/null 2>&1; then
  echo 'gcloud 조회 실패를 허용함' >&2
  exit 1
fi

if bash "$SCRIPT" --project INVALID_PROJECT \
  --billing-account "$BILLING_ACCOUNT_ID" >/dev/null 2>&1; then
  echo '잘못된 project ID를 허용함' >&2
  exit 1
fi
if bash "$SCRIPT" --project "$PROJECT_ID" \
  --billing-account not-an-account >/dev/null 2>&1; then
  echo '잘못된 billing account ID를 허용함' >&2
  exit 1
fi

echo 'Project billing binding read-only preflight contract passed.'
