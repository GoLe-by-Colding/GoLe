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
PUBSUB_CONSUMER_ROLE_ID="${PUBSUB_CONSUMER_ROLE_ID:-goleBudgetSubscriptionConsumer}"

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
EXPECTED_RUNTIME_SERVICE_ACCOUNT="${EXPECTED_RUNTIME_SERVICE_ACCOUNT:-gole-production-runtime@${PROJECT_ID}.iam.gserviceaccount.com}"

if [ -z "$COMPUTE_SERVICE_ACCOUNT" ]; then
  echo "${INSTANCE_NAME} VM에 연결된 서비스 계정을 찾지 못했습니다." >&2
  exit 1
fi
if [ "$COMPUTE_SERVICE_ACCOUNT" != "$EXPECTED_RUNTIME_SERVICE_ACCOUNT" ]; then
  echo "${INSTANCE_NAME} VM이 전용 runtime 서비스 계정을 사용하지 않습니다." >&2
  echo "현재: ${COMPUTE_SERVICE_ACCOUNT}" >&2
  echo "예상: ${EXPECTED_RUNTIME_SERVICE_ACCOUNT}" >&2
  echo "서비스 계정을 먼저 교체한 뒤 Budget IAM을 구성하세요." >&2
  exit 1
fi

echo "▶ Billing Budget/Pub/Sub/IAM API 활성화"
gcloud services enable \
  billingbudgets.googleapis.com \
  iam.googleapis.com \
  pubsub.googleapis.com \
  --project "$PROJECT_ID"

ensure_custom_role() {
  local role_id="$1"
  local title="$2"
  local description="$3"
  local permissions="$4"

  if gcloud iam roles describe "$role_id" --project "$PROJECT_ID" >/dev/null 2>&1; then
    gcloud iam roles update "$role_id" \
      --project "$PROJECT_ID" \
      --title "$title" \
      --description "$description" \
      --permissions "$permissions" \
      --stage=GA >/dev/null
  else
    gcloud iam roles create "$role_id" \
      --project "$PROJECT_ID" \
      --title "$title" \
      --description "$description" \
      --permissions "$permissions" \
      --stage=GA >/dev/null
  fi
}

echo "▶ 비용 이벤트 소비용 최소 권한 custom role 준비"
ensure_custom_role \
  "$PUBSUB_CONSUMER_ROLE_ID" \
  'GoLe budget subscription consumer' \
  'Consumes only the GoLe billing budget Pub/Sub subscription' \
  'pubsub.subscriptions.consume'

PUBSUB_CONSUMER_ROLE="projects/${PROJECT_ID}/roles/${PUBSUB_CONSUMER_ROLE_ID}"

echo "▶ 비용 알림 Pub/Sub topic/subscription 준비"
if ! gcloud pubsub topics describe "$TOPIC_NAME" --project "$PROJECT_ID" >/dev/null 2>&1; then
  gcloud pubsub topics create "$TOPIC_NAME" --project "$PROJECT_ID"
fi
# Cloud Billing publishes as this Google-managed identity. The Budget API can
# otherwise accept a topic while later deliveries fail silently.
gcloud pubsub topics add-iam-policy-binding "$TOPIC_NAME" \
  --project "$PROJECT_ID" \
  --member='serviceAccount:billing-budget-alert@system.gserviceaccount.com' \
  --role='roles/pubsub.publisher' >/dev/null
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
  --role="$PUBSUB_CONSUMER_ROLE" >/dev/null

# 같은 subscription에 남아 있는 기존 predefined subscriber 권한은 custom role로 대체한다.
legacy_subscriber_member="serviceAccount:${COMPUTE_SERVICE_ACCOUNT}"
subscription_policy="$(
  gcloud pubsub subscriptions get-iam-policy "$SUBSCRIPTION_NAME" \
    --project "$PROJECT_ID" \
    --format=json
)"
if jq -e \
  --arg member "$legacy_subscriber_member" \
  '.bindings[]? |
   select(.role == "roles/pubsub.subscriber") |
   (.members // []) |
   index($member)' <<<"$subscription_policy" >/dev/null; then
  gcloud pubsub subscriptions remove-iam-policy-binding "$SUBSCRIPTION_NAME" \
    --project "$PROJECT_ID" \
    --member="$legacy_subscriber_member" \
    --role='roles/pubsub.subscriber' >/dev/null
fi

# VM 정지는 metadata credential을 가진 앱/relay가 아니라 root cloud broker가
# local systemd poweroff로 집행한다. runtime 서비스 계정에는 VM 정지 IAM
# 권한을 만들거나 부여하지 않는다.

TOPIC_RESOURCE="projects/${PROJECT_ID}/topics/${TOPIC_NAME}"
PROJECT_RESOURCE="projects/${PROJECT_NUMBER}"

create_budget() {
  local display_name="$1"
  gcloud billing budgets create \
    --billing-account "$BILLING_ACCOUNT" \
    --display-name "$display_name" \
    --budget-amount="${BUDGET_AMOUNT_KRW}KRW" \
    --start-date "$BUDGET_START_DATE" \
    --end-date "$BUDGET_END_DATE" \
    --filter-projects "$PROJECT_RESOURCE" \
    --credit-types-treatment=exclude-all-credits \
    --threshold-rule=percent=0.5,basis=current-spend \
    --threshold-rule=percent=0.75,basis=current-spend \
    --threshold-rule=percent=0.85,basis=current-spend \
    --threshold-rule=percent=0.9,basis=current-spend \
    --threshold-rule=percent=0.95,basis=current-spend \
    --threshold-rule=percent=1.0,basis=current-spend \
    --notifications-rule-pubsub-topic "$TOPIC_RESOURCE" \
    --format=json
}

budget_matches_target() {
  jq -e \
    --arg project "$PROJECT_RESOURCE" \
    --arg start_date "$BUDGET_START_DATE" \
    --arg end_date "$BUDGET_END_DATE" \
    '($start_date | split("-") | map(tonumber)) as $start |
     ($end_date | split("-") | map(tonumber)) as $end |
     (.budgetFilter.projects == [$project]) and
     (.budgetFilter.creditTypesTreatment == "EXCLUDE_ALL_CREDITS") and
     ([.budgetFilter.customPeriod.startDate.year,
       .budgetFilter.customPeriod.startDate.month,
       .budgetFilter.customPeriod.startDate.day] == $start) and
     ([.budgetFilter.customPeriod.endDate.year,
       .budgetFilter.customPeriod.endDate.month,
       .budgetFilter.customPeriod.endDate.day] == $end)' >/dev/null
}

REPLACEMENT_DISPLAY="${BUDGET_DISPLAY_NAME} (${BUDGET_START_DATE})"
BUDGET_LIST="$(gcloud billing budgets list --billing-account "$BILLING_ACCOUNT" --format=json)"
BUDGET_RESOURCE="$(
  jq -r \
    --arg current "$BUDGET_DISPLAY_NAME" \
    --arg legacy 'HE Testbed free-trial guardrail' \
    --arg replacement "$REPLACEMENT_DISPLAY" \
    --arg project "$PROJECT_RESOURCE" \
    '[.[] |
      select(.displayName == $current or .displayName == $legacy or .displayName == $replacement) |
      select((.budgetFilter.projects // []) | index($project)) |
      {name, rank: (if .displayName == $replacement then 1 else 0 end)}] |
     sort_by(.rank) | .[0].name // empty' <<<"$BUDGET_LIST"
)"

if [ -z "$BUDGET_RESOURCE" ]; then
  echo "▶ ${BUDGET_START_DATE}~${BUDGET_END_DATE} 총 ${BUDGET_AMOUNT_KRW} KRW guardrail 생성"
  created_budget="$(create_budget "$BUDGET_DISPLAY_NAME")"
  BUDGET_RESOURCE="$(jq -r '.name // empty' <<<"$created_budget")"
  if [ -z "$BUDGET_RESOURCE" ] || ! budget_matches_target <<<"$created_budget"; then
    echo "생성된 budget이 요청한 기간/프로젝트와 일치하지 않습니다." >&2
    exit 1
  fi
else
  existing_budget="$(gcloud billing budgets describe "$BUDGET_RESOURCE" --format=json)"
  if ! budget_matches_target <<<"$existing_budget"; then
    echo "▶ immutable 기간이 다른 기존 budget을 무중단 교체"
    replacement_resource="$(
      jq -r \
        --arg replacement "$REPLACEMENT_DISPLAY" \
        --arg project "$PROJECT_RESOURCE" \
        '[.[] |
          select(.displayName == $replacement) |
          select((.budgetFilter.projects // []) | index($project))] |
         .[0].name // empty' <<<"$BUDGET_LIST"
    )"
    if [ -z "$replacement_resource" ]; then
      replacement_budget="$(create_budget "$REPLACEMENT_DISPLAY")"
      replacement_resource="$(jq -r '.name // empty' <<<"$replacement_budget")"
    else
      replacement_budget="$(gcloud billing budgets describe "$replacement_resource" --format=json)"
    fi
    if [ -z "$replacement_resource" ] || ! budget_matches_target <<<"$replacement_budget"; then
      echo "교체 budget 검증에 실패해 기존 budget을 유지합니다." >&2
      exit 1
    fi

    # 새 budget과 Pub/Sub 연결을 먼저 검증한 뒤에만 이전 budget을 제거한다.
    gcloud billing budgets delete "$BUDGET_RESOURCE" --quiet
    BUDGET_RESOURCE="$replacement_resource"
  fi
fi

echo "▶ 기존 budget의 금액/임계치/Discord 연결 갱신"
# customPeriod 날짜는 immutable이다. 위에서 필요하면 create-first로 교체하고,
# 여기서는 gcloud 583 updateMask 버그를 피하려고 변경 가능한 필드만 REST PATCH한다.
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
      {thresholdPercent: 0.85, spendBasis: "CURRENT_SPEND"},
      {thresholdPercent: 0.9, spendBasis: "CURRENT_SPEND"},
      {thresholdPercent: 0.95, spendBasis: "CURRENT_SPEND"},
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

final_budget="$(gcloud billing budgets describe "$BUDGET_RESOURCE" --format=json)"
actual_budget_resource="$(jq -r '.name // empty' <<<"$final_budget")"
if [ -z "$actual_budget_resource" ]; then
  echo "갱신된 budget의 실제 리소스 ID를 확인하지 못했습니다." >&2
  exit 1
fi
actual_budget_id="${actual_budget_resource##*/}"

echo "✔ Budget resource: ${actual_budget_resource}"
echo "✔ Budget ID: ${actual_budget_id}"
echo "✔ ${SUBSCRIPTION_NAME} → Discord relay 입력 준비 완료"
