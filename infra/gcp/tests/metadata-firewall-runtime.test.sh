#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf -- "$TEST_ROOT"' EXIT
install -d "$TEST_ROOT/bin"

cat > "$TEST_ROOT/bin/id" <<'EOF'
#!/bin/sh
[ "$1" = -u ] && printf '0\n'
EOF
cat > "$TEST_ROOT/bin/iptables" <<'EOF'
#!/usr/bin/env bash
printf 'v4 %s\n' "$*" >> "$FIREWALL_LOG"
[[ " $* " != *' -C '* ]]
EOF
cat > "$TEST_ROOT/bin/ip6tables" <<'EOF'
#!/usr/bin/env bash
printf 'v6 %s\n' "$*" >> "$FIREWALL_LOG"
[[ " $* " != *' -C '* ]]
EOF
chmod 0755 "$TEST_ROOT/bin/id" "$TEST_ROOT/bin/iptables" "$TEST_ROOT/bin/ip6tables"
export FIREWALL_LOG="$TEST_ROOT/firewall.log"

PATH="$TEST_ROOT/bin:$PATH" bash "$ROOT/infra/gcp/scripts/metadata-firewall.sh"

grep -Eq 'v4 -w -t raw -A GOLE_MI_[0-9]+_[0-9]+ -d 169\.254\.169\.254/32 -j DROP' "$FIREWALL_LOG"
grep -Eq 'v4 -w -t raw -I PREROUTING 1 -j GOLE_MI_[0-9]+_[0-9]+' "$FIREWALL_LOG"
grep -Eq 'v4 -w -A GOLE_MO_[0-9]+_[0-9]+ -m owner --uid-owner 0 -j RETURN' "$FIREWALL_LOG"
grep -Eq 'v4 -w -A GOLE_MO_[0-9]+_[0-9]+ -j REJECT' "$FIREWALL_LOG"
grep -Eq 'v4 -w -I OUTPUT 1 -d 169\.254\.169\.254/32 -j GOLE_MO_[0-9]+_[0-9]+' "$FIREWALL_LOG"
grep -Eq 'v4 -w -E GOLE_MO_[0-9]+_[0-9]+ GOLE_METADATA_OUTPUT' "$FIREWALL_LOG"
grep -Eq 'v4 -w -t raw -E GOLE_MI_[0-9]+_[0-9]+ GOLE_METADATA_INPUT' "$FIREWALL_LOG"
grep -Eq 'v6 -w -t raw -A GOLE_MI_[0-9]+_[0-9]+ -d fd20:ce::254/128 -j DROP' "$FIREWALL_LOG"

# The replacement path must be active before an old chain is flushed. This is
# the regression guard against a brief metadata-token exposure on reapply.
output_insert_line="$(grep -nE 'v4 -w -I OUTPUT 1 .* -j GOLE_MO_' "$FIREWALL_LOG" | head -1 | cut -d: -f1)"
output_flush_line="$(grep -nF 'v4 -w -F GOLE_METADATA_OUTPUT' "$FIREWALL_LOG" | head -1 | cut -d: -f1)"
input_insert_line="$(grep -nE 'v4 -w -t raw -I PREROUTING 1 -j GOLE_MI_' "$FIREWALL_LOG" | head -1 | cut -d: -f1)"
input_flush_line="$(grep -nF 'v4 -w -t raw -F GOLE_METADATA_INPUT' "$FIREWALL_LOG" | head -1 | cut -d: -f1)"
[ "$output_insert_line" -lt "$output_flush_line" ]
[ "$input_insert_line" -lt "$input_flush_line" ]

grep -Fq 'Before=network.target docker.service gole-cloud-broker.service gole-github-runner.service' \
  "$ROOT/infra/gcp/systemd/gole-metadata-firewall.service"
grep -Fq 'runuser -u "$DEPLOY_USER" -- curl -fsS --max-time 2' \
  "$ROOT/infra/gcp/scripts/verify-host-bootstrap.sh"
grep -Fq 'service-accounts/default/token' \
  "$ROOT/infra/gcp/scripts/verify-host-bootstrap.sh"

echo 'Metadata token isolation contract passed.'
