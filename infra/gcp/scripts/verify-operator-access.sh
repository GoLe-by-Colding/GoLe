#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_ID=""
ZONE="asia-northeast3-a"
INSTANCE="gole-production"
OPERATOR="coldingcontact@gmail.com"

usage() {
  echo 'usage: verify-operator-access.sh --project PROJECT [--zone ZONE] [--instance NAME]' >&2
  exit 2
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --project) [ "$#" -ge 2 ] || usage; PROJECT_ID="$2"; shift 2 ;;
    --zone) [ "$#" -ge 2 ] || usage; ZONE="$2"; shift 2 ;;
    --instance) [ "$#" -ge 2 ] || usage; INSTANCE="$2"; shift 2 ;;
    *) usage ;;
  esac
done
[[ "$PROJECT_ID" =~ ^[a-z][a-z0-9-]{4,28}[a-z0-9]$ ]] || usage
[ "$ZONE" = asia-northeast3-a ] || usage
[ "$INSTANCE" = gole-production ] || usage

for command_name in gcloud grep python3; do
  command -v "$command_name" >/dev/null 2>&1 || {
    echo "missing required command: $command_name" >&2
    exit 1
  }
done

active_account="$(gcloud auth list --filter=status:ACTIVE --format='value(account)' --quiet)"
[ "$active_account" = "$OPERATOR" ] || {
  echo 'active gcloud identity is not the reviewed production operator' >&2
  exit 1
}

runtime_email="gole-production-runtime@${PROJECT_ID}.iam.gserviceaccount.com"
policy_dir="$(mktemp -d "${TMPDIR:-/tmp}/gole-operator-access.XXXXXX")"
trap 'rm -rf -- "$policy_dir"' EXIT
chmod 0700 "$policy_dir"
gcloud projects get-iam-policy "$PROJECT_ID" --format=json --quiet > "$policy_dir/project.json"
gcloud iam service-accounts get-iam-policy "$runtime_email" --project "$PROJECT_ID" \
  --format=json --quiet > "$policy_dir/service-account.json"
gcloud compute instances get-iam-policy "$INSTANCE" --project "$PROJECT_ID" \
  --zone "$ZONE" --format=json --quiet > "$policy_dir/instance.json"
gcloud secrets get-iam-policy gole-production-env --project "$PROJECT_ID" \
  --format=json --quiet > "$policy_dir/secret.json"
gcloud pubsub subscriptions get-iam-policy gole-billing-budget-discord \
  --project "$PROJECT_ID" --format=json --quiet > "$policy_dir/subscription.json"
gcloud iam roles describe goleBudgetSubscriptionConsumer --project "$PROJECT_ID" \
  --format=json --quiet > "$policy_dir/budget-role.json"
gcloud compute os-login describe-profile --project "$PROJECT_ID" \
  --format=json --quiet > "$policy_dir/os-login-profile.json"
chmod 0600 "$policy_dir"/*.json
python3 - \
  "$policy_dir/project.json" \
  "$policy_dir/service-account.json" \
  "$policy_dir/instance.json" \
  "$policy_dir/secret.json" \
  "$policy_dir/subscription.json" \
  "$policy_dir/budget-role.json" \
  "$policy_dir/os-login-profile.json" \
  "$PROJECT_ID" <<'PY'
import json
import pathlib
import sys

member = "user:coldingcontact@gmail.com"
project_id = sys.argv[8]
runtime_member = (
    f"serviceAccount:gole-production-runtime@{project_id}.iam.gserviceaccount.com"
)

def has_binding(path: str, role: str) -> bool:
    document = json.loads(pathlib.Path(path).read_text(encoding="utf-8"))
    return any(
        isinstance(binding, dict)
        and binding.get("role") == role
        and member in binding.get("members", [])
        and not binding.get("condition")
        for binding in document.get("bindings", [])
    )

required_project = ("roles/compute.osAdminLogin", "roles/iap.tunnelResourceAccessor")
missing = [role for role in required_project if not has_binding(sys.argv[1], role)]
if not has_binding(sys.argv[2], "roles/iam.serviceAccountUser"):
    missing.append("roles/iam.serviceAccountUser@runtime-service-account")
if missing:
    raise SystemExit("operator is missing an exact unconditional IAM binding: " + ", ".join(missing))


def roles_for(path: str, principal: str) -> set[str]:
    document = json.loads(pathlib.Path(path).read_text(encoding="utf-8"))
    return {
        str(binding.get("role"))
        for binding in document.get("bindings", [])
        if isinstance(binding, dict)
        and principal in binding.get("members", [])
        and not binding.get("condition")
    }


# A container or unprivileged host-process compromise must not inherit VM stop
# or broad Secret Manager power from the instance service account.  Only the
# one secret and one Pub/Sub subscription have runtime grants; the temporary
# GTS EAB role is the sole reviewed project-level exception.
project_runtime_roles = roles_for(sys.argv[1], runtime_member)
if project_runtime_roles - {"roles/publicca.externalAccountKeyCreator"}:
    raise SystemExit("runtime identity has an unreviewed project-level IAM role")
if roles_for(sys.argv[3], runtime_member):
    raise SystemExit("runtime identity has an unreviewed instance-level IAM role")
if roles_for(sys.argv[4], runtime_member) != {"roles/secretmanager.secretAccessor"}:
    raise SystemExit("runtime identity production-secret access is not exact")
expected_consumer = f"projects/{project_id}/roles/goleBudgetSubscriptionConsumer"
if roles_for(sys.argv[5], runtime_member) != {expected_consumer}:
    raise SystemExit("runtime identity budget-subscription access is not exact")
budget_role = json.loads(pathlib.Path(sys.argv[6]).read_text(encoding="utf-8"))
if budget_role.get("includedPermissions") != ["pubsub.subscriptions.consume"]:
    raise SystemExit("runtime budget consumer role permissions are not exact")

profile = json.loads(pathlib.Path(sys.argv[7]).read_text(encoding="utf-8"))
expected_username = "coldingcontact_gmail_com"
accounts = [
    account
    for account in profile.get("posixAccounts", [])
    if isinstance(account, dict) and account.get("accountId") == project_id
]
if len(accounts) != 1:
    raise SystemExit("operator has no unique project-scoped OS Login POSIX account")
account = accounts[0]
if (
    account.get("name", "").split("/")[-2:] != ["projects", project_id]
    or account.get("operatingSystemType") != "LINUX"
    or account.get("primary") is not True
    or account.get("username") != expected_username
    or account.get("homeDirectory") != f"/home/{expected_username}"
    or not str(account.get("uid", "")).isdigit()
    or not str(account.get("gid", "")).isdigit()
):
    raise SystemExit("operator OS Login POSIX account is not the reviewed exact profile")
PY

instance_service_account="$(gcloud compute instances describe "$INSTANCE" \
  --project "$PROJECT_ID" --zone "$ZONE" \
  --format='value(serviceAccounts[0].email)' --quiet)"
[ "$instance_service_account" = "$runtime_email" ] || {
  echo 'production instance runtime identity differs from the reviewed service account' >&2
  exit 1
}

# This is a real read-only IAP/SSH round trip, not merely `--dry-run`. Run it
# immediately before the saved Terraform adoption plan is applied so enabling
# OS Login cannot strand the only operator outside the instance.
gcloud compute ssh "$INSTANCE" --project "$PROJECT_ID" --zone "$ZONE" \
  --tunnel-through-iap --command='sudo -n /usr/bin/true' --quiet >/dev/null

echo 'Production operator IAP, passwordless OS Admin Login and service-account access preflight passed.'
