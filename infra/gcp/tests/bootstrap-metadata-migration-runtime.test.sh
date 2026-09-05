#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
IMAGE="ubuntu@sha256:33ceb71981b602c1a7443a53469e4dba065f7503eab3078a2d7a57a2ab987517"

docker run --rm --interactive --volume "$ROOT:/source:ro" "$IMAGE" bash -seu <<'CONTAINER_TEST'
legacy_sha=1111111111111111111111111111111111111111
wrong_sha=2222222222222222222222222222222222222222
bootstrap_sha=3333333333333333333333333333333333333333
install -d -m 0755 /app/.git /etc/gole /etc/systemd/system /test-bin
touch /etc/systemd/system/actions.runner.GoLe.legacy.service
sed -n '1,/^# Everything copied below/p' \
  /source/infra/gcp/scripts/bootstrap-host.sh > /tmp/bootstrap-prefix.sh

cat > /test-bin/systemctl <<'EOF'
#!/bin/sh
printf '%s\n' "$*" >> /tmp/systemctl.calls
case "$1" in
  show)
    case " $* " in
      *' --property=ControlGroup '*) printf '/system.slice/%s\n' "${4:-unknown.service}" ;;
      *' --property=ActiveState '*) printf 'inactive\n' ;;
      *) exit 1 ;;
    esac
    exit 0 ;;
  is-active) exit 1 ;;
  *) exit 0 ;;
esac
EOF
cat > /test-bin/docker <<'EOF'
#!/bin/sh
case "$*" in
  *'com.docker.compose.project'*'com.docker.compose.service'*)
    printf 'gole:budget-relay\n' ;;
  *'.State.Status'*) printf 'running:healthy\n' ;;
  *'.Config.Env'*) printf 'LEGACY_RELAY=true\n' ;;
  *) exit 1 ;;
esac
EOF
cat > /usr/bin/git <<EOF
#!/bin/sh
case " \$* " in
  *' rev-parse --verify HEAD '*) printf '%s\n' '$legacy_sha' ;;
  *' status --porcelain=v1 --untracked-files=all '*) exit 0 ;;
  *) exit 90 ;;
esac
EOF
chmod 0755 /test-bin/systemctl /test-bin/docker /usr/bin/git
export PATH="/test-bin:/usr/sbin:/usr/bin:/sbin:/bin"

run_prefix() {
  env \
    BOOTSTRAP_SOURCE_SHA="$bootstrap_sha" \
    GCP_PROJECT_ID=test-project \
    GCP_VM_COST_START=2026-09-01T19:57:05+09:00 \
    GCP_HARD_STOP_AT=2026-10-28T01:50:00+09:00 \
    GCP_CREDIT_DEADLINE=2026-10-28T23:59:59+09:00 \
    GCP_RUNTIME_RATE_TRANSITION_AT=2026-09-06T00:00:00+09:00 \
    GCP_EXPECTED_BUDGET_ID=00000000-0000-4000-8000-000000000001 \
    GCP_EXPECTED_BILLING_ACCOUNT_ID=ABCDEF-123456-ABCDEF \
    GOLE_METADATA_MIGRATION_SOURCE_SHA="${1:-}" \
    bash /tmp/bootstrap-prefix.sh
}

if run_prefix >/tmp/missing.out 2>&1; then
  echo 'legacy bootstrap accepted an omitted migration SHA' >&2
  exit 1
fi
grep -q 'legacy budget relay detected' /tmp/missing.out
[ ! -e /etc/gole/metadata-migration.pending ]
grep -q '^disable --now actions.runner.GoLe.legacy.service$' /tmp/systemctl.calls

if run_prefix "$wrong_sha" >/tmp/wrong.out 2>&1; then
  echo 'legacy bootstrap accepted a mismatched checkout SHA' >&2
  exit 1
fi
grep -q 'legacy checkout does not match' /tmp/wrong.out
[ ! -e /etc/gole/metadata-migration.pending ]

run_prefix "$legacy_sha"
[ "$(stat -c '%U:%G:%a' /etc/gole/metadata-migration.pending)" = root:root:644 ]
grep -qx 'state=pending' /etc/gole/metadata-migration.pending
grep -qx "legacy_sha=$legacy_sha" /etc/gole/metadata-migration.pending
[ "$(wc -l < /etc/gole/metadata-migration.pending)" -eq 2 ]
grep -Fq 'sync -f /etc/gole' /tmp/bootstrap-prefix.sh

echo 'Legacy bootstrap metadata-migration detection contract passed.'
CONTAINER_TEST
