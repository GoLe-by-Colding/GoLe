#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
SANDBOX="$(mktemp -d)"
cleanup() { rm -rf -- "$SANDBOX"; }
trap cleanup EXIT
mkdir -p "$SANDBOX/bin"

cat > "$SANDBOX/metadata.json" <<'JSON'
{"name":"gole-tfstate-example","projectNumber":"123456789","location":"ASIA-NORTHEAST3","storageClass":"STANDARD","iamConfiguration":{"uniformBucketLevelAccess":{"enabled":true},"publicAccessPrevention":"enforced"},"versioning":{"enabled":true},"softDeletePolicy":{"retentionDurationSeconds":"604800"},"lifecycle":{"rule":[{"action":{"type":"Delete"},"condition":{"daysSinceNoncurrentTime":14,"numNewerVersions":10}}]}}
JSON
cat > "$SANDBOX/iam.json" <<'JSON'
{"bindings":[{"role":"roles/storage.objectAdmin","members":["user:operator@example.com"]}]}
JSON
cat > "$SANDBOX/bin/gcloud" <<'SH'
#!/usr/bin/env bash
set -Eeuo pipefail
printf '%s\n' "$*" >> "$FAKE_GCLOUD_LOG"
case "$1 $2 $3" in
  "projects describe project-test-123")
    printf '%s\n' "${FAKE_PROJECT_NUMBER:-123456789}"
    ;;
  "storage buckets describe")
    cat "$FAKE_METADATA"
    ;;
  "storage buckets get-iam-policy")
    cat "$FAKE_IAM"
    ;;
  "storage buckets update"|"storage buckets add-iam-policy-binding")
    ;;
  *)
    printf 'unexpected fake gcloud invocation: %s\n' "$*" >&2
    exit 91
    ;;
esac
SH
chmod 0755 "$SANDBOX/bin/gcloud"

export PATH="$SANDBOX/bin:$PATH"
export FAKE_GCLOUD_LOG="$SANDBOX/gcloud.log"
export FAKE_METADATA="$SANDBOX/metadata.json"
export FAKE_IAM="$SANDBOX/iam.json"

args=(
  --project project-test-123
  --bucket gole-tfstate-example
  --terraform-principal user:operator@example.com
)

bash "$ROOT/infra/gcp/scripts/bootstrap-terraform-state.sh" "${args[@]}" >/dev/null
if grep -Eq 'buckets (create|update|add-iam-policy-binding)' "$FAKE_GCLOUD_LOG"; then
  echo "dry-run mutated the state bucket" >&2
  exit 1
fi

: > "$FAKE_GCLOUD_LOG"
bash "$ROOT/infra/gcp/scripts/bootstrap-terraform-state.sh" "${args[@]}" --apply >/dev/null
grep -q '^storage buckets update ' "$FAKE_GCLOUD_LOG"
grep -q '^storage buckets add-iam-policy-binding ' "$FAKE_GCLOUD_LOG"
if grep -q '^storage buckets create ' "$FAKE_GCLOUD_LOG"; then
  echo "idempotent apply tried to recreate an existing bucket" >&2
  exit 1
fi

: > "$FAKE_GCLOUD_LOG"
if FAKE_PROJECT_NUMBER=987654321 \
  bash "$ROOT/infra/gcp/scripts/bootstrap-terraform-state.sh" "${args[@]}" --apply >/dev/null 2>&1; then
  echo "bucket from another project passed identity validation" >&2
  exit 1
fi
if grep -Eq 'buckets (create|update|add-iam-policy-binding)' "$FAKE_GCLOUD_LOG"; then
  echo "foreign bucket was mutated before identity validation" >&2
  exit 1
fi

echo "Terraform state bucket bootstrap runtime contract passed."
