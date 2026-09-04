#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf -- "$TEST_ROOT"' EXIT
install -d "$TEST_ROOT/bin"

cat > "$TEST_ROOT/bin/gcloud" <<'EOF'
#!/usr/bin/env bash
set -eu
printf '%s\n' "$*" >> "$GCLOUD_LOG"
case "$1 $2" in
  'auth list') printf 'coldingcontact@gmail.com\n' ;;
  'projects get-iam-policy')
    if [ "${OMIT_IAP:-0}" = 1 ]; then
      printf '%s\n' '{"bindings":[{"role":"roles/compute.osAdminLogin","members":["user:coldingcontact@gmail.com"]}]}'
    elif [ "${ADD_RUNTIME_STOP:-0}" = 1 ]; then
      printf '%s\n' '{"bindings":[{"role":"roles/compute.osAdminLogin","members":["user:coldingcontact@gmail.com"]},{"role":"roles/iap.tunnelResourceAccessor","members":["user:coldingcontact@gmail.com"]},{"role":"projects/test-project/roles/goleProductionInstanceStopper","members":["serviceAccount:gole-production-runtime@test-project.iam.gserviceaccount.com"]}]}'
    else
      printf '%s\n' '{"bindings":[{"role":"roles/compute.osAdminLogin","members":["user:coldingcontact@gmail.com"]},{"role":"roles/iap.tunnelResourceAccessor","members":["user:coldingcontact@gmail.com"]}]}'
    fi
    ;;
  'iam service-accounts')
    printf '%s\n' '{"bindings":[{"role":"roles/iam.serviceAccountUser","members":["user:coldingcontact@gmail.com"]}]}'
    ;;
  'compute instances')
    if [ "$3" = get-iam-policy ]; then
      printf '%s\n' '{"bindings":[]}'
    else
      printf 'gole-production-runtime@test-project.iam.gserviceaccount.com\n'
    fi
    ;;
  'secrets get-iam-policy')
    printf '%s\n' '{"bindings":[{"role":"roles/secretmanager.secretAccessor","members":["serviceAccount:gole-production-runtime@test-project.iam.gserviceaccount.com"]}]}'
    ;;
  'pubsub subscriptions')
    printf '%s\n' '{"bindings":[{"role":"projects/test-project/roles/goleBudgetSubscriptionConsumer","members":["serviceAccount:gole-production-runtime@test-project.iam.gserviceaccount.com"]}]}'
    ;;
  'iam roles')
    printf '%s\n' '{"includedPermissions":["pubsub.subscriptions.consume"]}'
    ;;
  'compute ssh') : ;;
  *) exit 90 ;;
esac
EOF
chmod 0755 "$TEST_ROOT/bin/gcloud"
export GCLOUD_LOG="$TEST_ROOT/gcloud.log"

PATH="$TEST_ROOT/bin:$PATH" bash "$ROOT/infra/gcp/scripts/verify-operator-access.sh" \
  --project test-project
grep -q 'compute ssh gole-production .*--tunnel-through-iap --command=true' "$GCLOUD_LOG"
if OMIT_IAP=1 PATH="$TEST_ROOT/bin:$PATH" \
  bash "$ROOT/infra/gcp/scripts/verify-operator-access.sh" --project test-project \
  >/dev/null 2>&1; then
  echo 'operator preflight accepted a missing IAP role' >&2
  exit 1
fi
if ADD_RUNTIME_STOP=1 PATH="$TEST_ROOT/bin:$PATH" \
  bash "$ROOT/infra/gcp/scripts/verify-operator-access.sh" --project test-project \
  >/dev/null 2>&1; then
  echo 'operator preflight accepted runtime VM stop authority' >&2
  exit 1
fi

echo 'Operator access and runtime identity boundary preflight passed.'
