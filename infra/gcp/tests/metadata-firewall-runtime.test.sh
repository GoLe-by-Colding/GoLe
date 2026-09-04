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

grep -Fq 'v4 -w -t raw -A GOLE_METADATA_INPUT -d 169.254.169.254/32 -j DROP' "$FIREWALL_LOG"
grep -Fq 'v4 -w -t raw -I PREROUTING 1 -j GOLE_METADATA_INPUT' "$FIREWALL_LOG"
grep -Fq 'v4 -w -A GOLE_METADATA_OUTPUT -m owner --uid-owner 0 -j RETURN' "$FIREWALL_LOG"
grep -Fq 'v4 -w -A GOLE_METADATA_OUTPUT -j REJECT' "$FIREWALL_LOG"
grep -Fq 'v4 -w -I OUTPUT 1 -d 169.254.169.254/32 -j GOLE_METADATA_OUTPUT' "$FIREWALL_LOG"
grep -Fq 'v6 -w -t raw -A GOLE_METADATA_INPUT -d fd20:ce::254/128 -j DROP' "$FIREWALL_LOG"

grep -Fq 'Before=network.target docker.service gole-cloud-broker.service gole-github-runner.service' \
  "$ROOT/infra/gcp/systemd/gole-metadata-firewall.service"
grep -Fq 'runuser -u "$DEPLOY_USER" -- curl -fsS --max-time 2' \
  "$ROOT/infra/gcp/scripts/verify-host-bootstrap.sh"
grep -Fq 'service-accounts/default/token' \
  "$ROOT/infra/gcp/scripts/verify-host-bootstrap.sh"

echo 'Metadata token isolation contract passed.'
