#!/usr/bin/env bash
set -Eeuo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
SUDOERS="$REPO_ROOT/infra/gcp/sudoers/gole-deploy"
DEPLOY="$REPO_ROOT/scripts/deploy.sh"

python3 - "$SUDOERS" "$DEPLOY" <<'PY'
import re
import sys

sudoers_path, deploy_path = sys.argv[1:]
sudoers = open(sudoers_path, encoding="utf-8").read()
deploy = open(deploy_path, encoding="utf-8").read()

uuid_pattern = (
    r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"
    r"[0-9a-f]{4}-[0-9a-f]{12}"
)
verify_arguments = rf"^[0-9a-f]{{40}} {uuid_pattern}$"
verify_rule = (
    "/usr/local/sbin/gole-hostctl deployment-verify-initial-http-commit "
    + verify_arguments
    + ", \\\n"
)

if sudoers.count(verify_rule) != 1:
    raise SystemExit("initial HTTP commit sudoers rule is missing or not exact")

compiled = re.compile(verify_arguments)
valid_sha = "2" * 40
valid_uuid = "12345678-1234-1234-1234-123456789abc"
accepted = f"{valid_sha} {valid_uuid}"
if compiled.fullmatch(accepted) is None:
    raise SystemExit("valid initial HTTP commit arguments are not accepted")
for rejected in (
    accepted + " extra",
    accepted.upper(),
    "2" * 39 + " " + valid_uuid,
    valid_sha + " " + valid_uuid[:-1],
    valid_sha + ";id " + valid_uuid,
):
    if compiled.fullmatch(rejected) is not None:
        raise SystemExit(f"unsafe initial HTTP commit arguments matched: {rejected!r}")

no_argument_commands = (
    "certificate-issue",
    "deployment-complete-initial-tls",
    "deployment-fail-closed-initial-tls",
)
for command in no_argument_commands:
    exact_rule = f'/usr/local/sbin/gole-hostctl {command} "",'
    matching_lines = [
        line.strip().removesuffix("\\").rstrip()
        for line in sudoers.splitlines()
        if f"/usr/local/sbin/gole-hostctl {command}" in line
    ]
    if matching_lines != [exact_rule]:
        raise SystemExit(f"{command} must have one exact no-argument sudoers rule")
    if f'sudo -n "$HOSTCTL" {command}' not in deploy:
        raise SystemExit(f"deploy.sh does not invoke {command} through hostctl")

expected_verify_call = (
    'sudo -n "$HOSTCTL" deployment-verify-initial-http-commit "$deployed_sha" \\\n'
    '      "$DEPLOYMENT_TRANSACTION_ID"'
)
if expected_verify_call not in deploy:
    raise SystemExit("deploy.sh initial HTTP verification argv changed")

if re.search(
    r"gole-hostctl (?:certificate-issue|deployment-complete-initial-tls|"
    r"deployment-fail-closed-initial-tls) (?!\"\")",
    sudoers,
):
    raise SystemExit("an initial TLS no-argument command has a broader sudoers rule")
PY

if command -v visudo >/dev/null 2>&1; then
  sudoers_candidate="$(mktemp)"
  trap 'rm -f -- "$sudoers_candidate"' EXIT
  sed "s/__DEPLOY_USER__/$(id -un)/g" "$SUDOERS" > "$sudoers_candidate"
  chmod 0440 "$sudoers_candidate"
  visudo -cf "$sudoers_candidate" >/dev/null
fi

echo 'Initial TLS least-privilege sudoers policy tests passed.'
