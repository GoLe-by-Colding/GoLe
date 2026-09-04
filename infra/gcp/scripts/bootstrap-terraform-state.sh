#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PROJECT_ID=""
BUCKET_NAME=""
LOCATION="asia-northeast3"
TERRAFORM_PRINCIPAL=""
APPLY=false

usage() {
  cat >&2 <<'EOF'
Usage: bootstrap-terraform-state.sh --project PROJECT --bucket GLOBALLY_UNIQUE_BUCKET \
  --terraform-principal user:operator@example.com [--location REGION] [--apply]

Without --apply this performs read-only discovery and prints the intended action.
EOF
}

while (($#)); do
  case "$1" in
    --project) PROJECT_ID="${2:-}"; shift 2 ;;
    --bucket) BUCKET_NAME="${2:-}"; shift 2 ;;
    --location) LOCATION="${2:-}"; shift 2 ;;
    --terraform-principal) TERRAFORM_PRINCIPAL="${2:-}"; shift 2 ;;
    --apply) APPLY=true; shift ;;
    --help|-h) usage; exit 0 ;;
    *) usage; exit 2 ;;
  esac
done

[[ "$PROJECT_ID" =~ ^[a-z][a-z0-9-]{4,28}[a-z0-9]$ ]] || {
  echo "유효한 project ID가 필요합니다." >&2
  exit 2
}
[[ "$BUCKET_NAME" =~ ^[a-z0-9][a-z0-9._-]{1,61}[a-z0-9]$ ]] || {
  echo "유효한 globally unique bucket 이름이 필요합니다." >&2
  exit 2
}
[[ "$LOCATION" =~ ^[a-z][a-z0-9-]+$ ]] || {
  echo "유효한 bucket location이 필요합니다." >&2
  exit 2
}
[[ "$TERRAFORM_PRINCIPAL" =~ ^(user|serviceAccount):[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+$ ]] || {
  echo "user: 또는 serviceAccount: 형식의 Terraform principal이 필요합니다." >&2
  exit 2
}
command -v gcloud >/dev/null || { echo "gcloud CLI가 필요합니다." >&2; exit 1; }

PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')"
[[ "$PROJECT_NUMBER" =~ ^[0-9]+$ ]] || { echo "project number를 확인할 수 없습니다." >&2; exit 1; }

work_dir="$(mktemp -d)"
cleanup() {
  find "$work_dir" -type f -delete 2>/dev/null || true
  rmdir "$work_dir" 2>/dev/null || true
}
trap cleanup EXIT

bucket_uri="gs://${BUCKET_NAME}"
if gcloud storage buckets describe "$bucket_uri" --format=json > "$work_dir/metadata.json" 2>/dev/null; then
  exists=true
  python3 "$ROOT/scripts/verify-terraform-state-bucket.py" \
    "$work_dir/metadata.json" \
    --bucket "$BUCKET_NAME" \
    --project-number "$PROJECT_NUMBER" \
    --location "$LOCATION" \
    --principal "$TERRAFORM_PRINCIPAL" \
    --identity-only
else
  exists=false
fi

if [[ "$APPLY" != true ]]; then
  if [[ "$exists" == true ]]; then
    gcloud storage buckets get-iam-policy "$bucket_uri" --format=json > "$work_dir/iam.json"
    python3 "$ROOT/scripts/verify-terraform-state-bucket.py" \
      "$work_dir/metadata.json" "$work_dir/iam.json" \
      --bucket "$BUCKET_NAME" \
      --project-number "$PROJECT_NUMBER" \
      --location "$LOCATION" \
      --principal "$TERRAFORM_PRINCIPAL"
    echo "DRY-RUN: 기존 bucket을 읽기 전용 검증함: $BUCKET_NAME"
  else
    echo "DRY-RUN: 보안 설정된 Terraform state bucket을 생성할 예정임: $BUCKET_NAME"
  fi
  echo "DRY-RUN: --apply 없이는 bucket/IAM을 변경하지 않음"
  exit 0
fi

if [[ "$exists" != true ]]; then
  gcloud storage buckets create "$bucket_uri" \
    --project="$PROJECT_ID" \
    --location="$LOCATION" \
    --default-storage-class=STANDARD \
    --uniform-bucket-level-access \
    --public-access-prevention \
    --soft-delete-duration=7d
fi

# Reassert all mutable controls so reruns converge instead of trusting an old bucket.
gcloud storage buckets update "$bucket_uri" \
  --uniform-bucket-level-access \
  --public-access-prevention \
  --soft-delete-duration=7d \
  --versioning \
  --lifecycle-file="$ROOT/terraform/state-lifecycle.json"
gcloud storage buckets add-iam-policy-binding "$bucket_uri" \
  --member="$TERRAFORM_PRINCIPAL" \
  --role=roles/storage.objectAdmin >/dev/null

gcloud storage buckets describe "$bucket_uri" --format=json > "$work_dir/metadata.json"
gcloud storage buckets get-iam-policy "$bucket_uri" --format=json > "$work_dir/iam.json"
python3 "$ROOT/scripts/verify-terraform-state-bucket.py" \
  "$work_dir/metadata.json" "$work_dir/iam.json" \
  --bucket "$BUCKET_NAME" \
  --project-number "$PROJECT_NUMBER" \
  --location "$LOCATION" \
  --principal "$TERRAFORM_PRINCIPAL"
