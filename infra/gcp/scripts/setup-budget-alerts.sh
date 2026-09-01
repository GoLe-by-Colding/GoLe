#!/usr/bin/env bash
set -Eeuo pipefail

cd "$(dirname "$0")/../../.."

PROJECT_ID="${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null)}"
ZONE="${ZONE:-asia-northeast3-a}"
INSTANCE_NAME="${INSTANCE_NAME:-gole-production}"
BUDGET_DISPLAY_NAME="${BUDGET_DISPLAY_NAME:-GoLe production credit guard}"
BUDGET_AMOUNT_KRW="${BUDGET_AMOUNT_KRW:-370000}"
BUDGET_START_DATE="${BUDGET_START_DATE:-2026-09-01}"
BUDGET_END_DATE="${BUDGET_END_DATE:-2026-10-28}"
TOPIC_NAME="${TOPIC_NAME:-gole-billing-budget}"
SUBSCRIPTION_NAME="${SUBSCRIPTION_NAME:-gole-billing-budget-discord}"

for required_command in gcloud jq curl; do
  if ! command -v "$required_command" >/dev/null 2>&1; then
    echo "필수 명령을 찾지 못했습니다: $required_command" >&2
    exit 1
  fi
done

if [ -z "$PROJECT_ID" ] || [ "$PROJECT_ID" = "(unset)" ]; then
  echo "PROJECT_ID 또는 gcloud 기본 프로젝트가 필요합니다." >&2
  exit 1
fi

PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')"
BILLING_ACCOUNT="${BILLING_ACCOUNT:-$(
  gcloud billing projects describe "$PROJECT_ID" \
    --format='value(billingAccountName)' | sed 's#^billingAccounts/##'
)}"

if [ -z "$BILLING_ACCOUNT" ]; then
  echo "프로젝트에 연결된 결제 계정을 찾지 못했습니다." >&2
  exit 1
fi

COMPUTE_SERVICE_ACCOUNT="${COMPUTE_SERVICE_ACCOUNT:-$(
  gcloud compute instances describe "$INSTANCE_NAME" --zone "$ZONE" \
    --format='value(serviceAccounts[0].email)'
)}"

echo "▶ Billing Budget/Pub/Sub API 활성화"
gcloud services enable \
  billingbudgets.googleapis.com \
  pubsub.googleapis.com \
  --project "$PROJECT_ID"

echo "▶ 비용 알림 Pub/Sub topic/subscription 준비"
if ! gcloud pubsub topics describe "$TOPIC_NAME" --project "$PROJECT_ID" >/dev/null 2>&1; then
  gcloud pubsub topics create "$TOPIC_NAME" --project "$PROJECT_ID"
fi
if ! gcloud pubsub subscriptions describe "$SUBSCRIPTION_NAME" --project "$PROJECT_ID" >/dev/null 2>&1; then
  gcloud pubsub subscriptions create "$SUBSCRIPTION_NAME" \
    --project "$PROJECT_ID" \
    --topic "$TOPIC_NAME" \
    --ack-deadline=60 \
    --message-retention-duration=7d
fi

# 앱 VM에는 topic 관리 권한 없이 이 subscription을 소비할 최소 권한만 준다.
gcloud pubsub subscriptions add-iam-policy-binding "$SUBSCRIPTION_NAME" \
  --project "$PROJECT_ID" \
  --member="serviceAccount:${COMPUTE_SERVICE_ACCOUNT}" \
  --role='roles/pubsub.subscriber' >/dev/null

TOPIC_RESOURCE="projects/${PROJECT_ID}/topics/${TOPIC_NAME}"
PROJECT_RESOURCE="projects/${PROJECT_NUMBER}"

BUDGET_RESOURCE="$(
  gcloud billing budgets list --billing-account "$BILLING_ACCOUNT" --format=json |
    jq -r --arg current "$BUDGET_DISPLAY_NAME" --arg legacy 'HE Testbed free-trial guardrail' \
      '[.[] | select(.displayName == $current or .displayName == $legacy)] | .[0].name // empty'
)"

if [ -n "$BUDGET_RESOURCE" ]; then
  echo "▶ 기존 budget 기간을 유지하고 총 ${BUDGET_AMOUNT_KRW} KRW guardrail/Discord 연결 갱신"
  # gcloud 583에도 기존 custom-period budget updateMask 버그가 있어 REST PATCH를 쓴다.
  # 기존 기간은 이미 발생한 비용을 누락하지 않도록 유지하고 금액/경보 연결만 갱신한다.
  access_token="$(gcloud auth print-access-token)"
  payload="$(jq -nc \
    --arg name "$BUDGET_RESOURCE" \
    --arg display_name "$BUDGET_DISPLAY_NAME" \
    --arg amount "$BUDGET_AMOUNT_KRW" \
    --arg topic "$TOPIC_RESOURCE" \
    '{
      name: $name,
      displayName: $display_name,
      amount: {specifiedAmount: {currencyCode: "KRW", units: $amount}},
      thresholdRules: [
        {thresholdPercent: 0.5, spendBasis: "CURRENT_SPEND"},
        {thresholdPercent: 0.75, spendBasis: "CURRENT_SPEND"},
        {thresholdPercent: 0.9, spendBasis: "CURRENT_SPEND"},
        {thresholdPercent: 1.0, spendBasis: "CURRENT_SPEND"}
      ],
      notificationsRule: {
        pubsubTopic: $topic,
        schemaVersion: "1.0",
        enableProjectLevelRecipients: true
      }
    }')"
  curl -fsS --request PATCH \
    -H "Authorization: Bearer ${access_token}" \
    -H "x-goog-user-project: ${PROJECT_ID}" \
    -H 'Content-Type: application/json' \
    "https://billingbudgets.googleapis.com/v1/${BUDGET_RESOURCE}?updateMask=displayName,amount,thresholdRules,notificationsRule" \
    --data "$payload" >/dev/null
else
  echo "▶ ${BUDGET_START_DATE}~${BUDGET_END_DATE} 총 ${BUDGET_AMOUNT_KRW} KRW guardrail 생성"
  gcloud billing budgets create \
    --billing-account "$BILLING_ACCOUNT" \
    --display-name "$BUDGET_DISPLAY_NAME" \
    --budget-amount="${BUDGET_AMOUNT_KRW}KRW" \
    --start-date "$BUDGET_START_DATE" \
    --end-date "$BUDGET_END_DATE" \
    --filter-projects "$PROJECT_RESOURCE" \
    --credit-types-treatment=exclude-all-credits \
    --threshold-rule=percent=0.5,basis=current-spend \
    --threshold-rule=percent=0.75,basis=current-spend \
    --threshold-rule=percent=0.9,basis=current-spend \
    --threshold-rule=percent=1.0,basis=current-spend \
    --notifications-rule-pubsub-topic "$TOPIC_RESOURCE"
fi

echo "✔ ${SUBSCRIPTION_NAME} → Discord relay 입력 준비 완료"
