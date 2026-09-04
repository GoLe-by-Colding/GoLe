#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
PROVIDER_FILE="$ROOT/infra/gcp/terraform/versions.tf"

python3 - "$PROVIDER_FILE" <<'PY'
import pathlib
import re
import sys

source = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
matches = re.findall(r'provider\s+"google"\s*\{(?P<body>.*?)\n\}', source, re.DOTALL)
if len(matches) != 1:
    raise SystemExit("expected exactly one default google provider block")
body = matches[0]

required = {
    "project": "var.project_id",
    "billing_project": "var.project_id",
    "user_project_override": "true",
}
for key, value in required.items():
    if not re.search(rf"^\s*{key}\s*=\s*{re.escape(value)}\s*$", body, re.MULTILINE):
        raise SystemExit(f"google provider is missing exact {key} = {value}")

# Authentication remains external so user ADC, short-lived tokens and
# GOOGLE_IMPERSONATE_SERVICE_ACCOUNT all continue to use the same provider.
for forbidden in ("credentials", "access_token", "impersonate_service_account"):
    if re.search(rf"^\s*{forbidden}\s*=", body, re.MULTILINE):
        raise SystemExit(f"google provider hard-codes authentication: {forbidden}")
PY

echo 'Terraform Google provider quota-project and external-auth contract passed.'
