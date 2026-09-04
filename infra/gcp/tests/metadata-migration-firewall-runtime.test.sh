#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
IMAGE="ubuntu@sha256:33ceb71981b602c1a7443a53469e4dba065f7503eab3078a2d7a57a2ab987517"

docker run --rm --interactive --volume "$ROOT:/source:ro" "$IMAGE" bash -seu <<'CONTAINER_TEST'
install -d -m 0755 /etc/gole /test-bin
cat > /test-bin/iptables <<'FAKE_IPTABLES'
#!/bin/sh
printf 'v4 %s\n' "$*" >> "$FIREWALL_LOG"
if [ "${SIMULATE_OLD_RULES:-0}" = 1 ]; then
  case " $* " in
    *' -C OUTPUT -d 169.254.169.254/32 -j GOLE_METADATA_OUTPUT '*)
      [ -e /tmp/v4-old-output ] && exit 0 || exit 1 ;;
    *' -D OUTPUT -d 169.254.169.254/32 -j GOLE_METADATA_OUTPUT '*)
      rm -f /tmp/v4-old-output; exit 0 ;;
    *' -t raw -C PREROUTING -j GOLE_METADATA_INPUT '*)
      [ -e /tmp/v4-old-input ] && exit 0 || exit 1 ;;
    *' -t raw -D PREROUTING -j GOLE_METADATA_INPUT '*)
      rm -f /tmp/v4-old-input; exit 0 ;;
  esac
fi
case " $* " in *' -C '*) exit 1 ;; esac
exit 0
FAKE_IPTABLES
cat > /test-bin/ip6tables <<'FAKE_IP6TABLES'
#!/bin/sh
printf 'v6 %s\n' "$*" >> "$FIREWALL_LOG"
if [ "${SIMULATE_OLD_RULES:-0}" = 1 ]; then
  case " $* " in
    *' -C OUTPUT -d fd20:ce::254/128 -j GOLE_METADATA_OUTPUT '*)
      [ -e /tmp/v6-old-output ] && exit 0 || exit 1 ;;
    *' -D OUTPUT -d fd20:ce::254/128 -j GOLE_METADATA_OUTPUT '*)
      rm -f /tmp/v6-old-output; exit 0 ;;
    *' -t raw -C PREROUTING -j GOLE_METADATA_INPUT '*)
      [ -e /tmp/v6-old-input ] && exit 0 || exit 1 ;;
    *' -t raw -D PREROUTING -j GOLE_METADATA_INPUT '*)
      rm -f /tmp/v6-old-input; exit 0 ;;
  esac
fi
case " $* " in *' -C '*) exit 1 ;; esac
exit 0
FAKE_IP6TABLES
chmod 0755 /test-bin/iptables /test-bin/ip6tables
export PATH="/test-bin:/usr/bin:/bin"
export FIREWALL_LOG=/tmp/firewall.log
legacy_sha=0123456789abcdef0123456789abcdef01234567

printf 'state=pending\nlegacy_sha=%s\n' "$legacy_sha" > /etc/gole/metadata-migration.pending
chmod 0644 /etc/gole/metadata-migration.pending
: > "$FIREWALL_LOG"
bash /source/infra/gcp/scripts/metadata-firewall.sh
grep -Eq 'v4 -w -A GOLE_MO_[0-9]+_[0-9]+ -j REJECT' "$FIREWALL_LOG"
grep -Eq 'v6 -w -A GOLE_MO_[0-9]+_[0-9]+ -j REJECT' "$FIREWALL_LOG"
! grep -Eq 'raw -A GOLE_MI_[0-9]+_[0-9]+ -d 169\.254\.169\.254/32 -j DROP' "$FIREWALL_LOG"
! grep -Eq 'raw -A GOLE_MI_[0-9]+_[0-9]+ -d fd20:ce::254/128 -j DROP' "$FIREWALL_LOG"

: > "$FIREWALL_LOG"
bash /source/infra/gcp/scripts/metadata-firewall.sh --full
grep -Eq 'raw -A GOLE_MI_[0-9]+_[0-9]+ -d 169\.254\.169\.254/32 -j DROP' "$FIREWALL_LOG"
grep -Eq 'raw -A GOLE_MI_[0-9]+_[0-9]+ -d fd20:ce::254/128 -j DROP' "$FIREWALL_LOG"

sed -i 's/state=pending/state=ratcheting/' /etc/gole/metadata-migration.pending
: > "$FIREWALL_LOG"
bash /source/infra/gcp/scripts/metadata-firewall.sh
grep -Eq 'raw -A GOLE_MI_[0-9]+_[0-9]+ -d 169\.254\.169\.254/32 -j DROP' "$FIREWALL_LOG"

printf 'state=unknown\nlegacy_sha=%s\n' "$legacy_sha" > /etc/gole/metadata-migration.pending
: > "$FIREWALL_LOG"
if bash /source/infra/gcp/scripts/metadata-firewall.sh >/dev/null 2>&1; then
  echo 'malformed metadata marker did not fail the firewall unit' >&2
  exit 1
fi
grep -Eq 'raw -A GOLE_MI_[0-9]+_[0-9]+ -d 169\.254\.169\.254/32 -j DROP' "$FIREWALL_LOG"

printf 'state=pending\nlegacy_sha=%s\nunknown=value' "$legacy_sha" > /etc/gole/metadata-migration.pending
: > "$FIREWALL_LOG"
if bash /source/infra/gcp/scripts/metadata-firewall.sh >/dev/null 2>&1; then
  echo 'unterminated unknown marker field was ignored' >&2
  exit 1
fi
grep -Eq 'raw -A GOLE_MI_[0-9]+_[0-9]+ -d 169\.254\.169\.254/32 -j DROP' "$FIREWALL_LOG"

# Reapplying full isolation builds and inserts both replacement chains before
# deleting or flushing the active stable chains.
printf 'state=ratcheting\nlegacy_sha=%s\n' "$legacy_sha" > /etc/gole/metadata-migration.pending
touch /tmp/v4-old-output /tmp/v4-old-input /tmp/v6-old-output /tmp/v6-old-input
: > "$FIREWALL_LOG"
SIMULATE_OLD_RULES=1 bash /source/infra/gcp/scripts/metadata-firewall.sh
assert_replacement_order() {
  family="$1" address="$2"
  output_insert="$(grep -nE "^$family -w -I OUTPUT 1 -d ${address//./\\.} -j GOLE_MO_" "$FIREWALL_LOG" | head -1 | cut -d: -f1)"
  output_delete="$(grep -nF "$family -w -D OUTPUT -d $address -j GOLE_METADATA_OUTPUT" "$FIREWALL_LOG" | head -1 | cut -d: -f1)"
  output_flush="$(grep -nF "$family -w -F GOLE_METADATA_OUTPUT" "$FIREWALL_LOG" | head -1 | cut -d: -f1)"
  input_insert="$(grep -nE "^$family -w -t raw -I PREROUTING 1 -j GOLE_MI_" "$FIREWALL_LOG" | head -1 | cut -d: -f1)"
  input_delete="$(grep -nF "$family -w -t raw -D PREROUTING -j GOLE_METADATA_INPUT" "$FIREWALL_LOG" | head -1 | cut -d: -f1)"
  input_flush="$(grep -nF "$family -w -t raw -F GOLE_METADATA_INPUT" "$FIREWALL_LOG" | head -1 | cut -d: -f1)"
  [ "$output_insert" -lt "$output_delete" ] && [ "$output_delete" -lt "$output_flush" ]
  [ "$input_insert" -lt "$input_delete" ] && [ "$input_delete" -lt "$input_flush" ]
}
assert_replacement_order v4 169.254.169.254/32
assert_replacement_order v6 fd20:ce::254/128

# A pending marker can never downgrade a host where full isolation is already
# active. The old raw jump remains untouched and bootstrap fails visibly.
printf 'state=pending\nlegacy_sha=%s\n' "$legacy_sha" > /etc/gole/metadata-migration.pending
touch /tmp/v4-old-input /tmp/v6-old-input
: > "$FIREWALL_LOG"
if SIMULATE_OLD_RULES=1 bash /source/infra/gcp/scripts/metadata-firewall.sh \
  >/dev/null 2>&1; then
  echo 'pending mode downgraded an existing full metadata firewall' >&2
  exit 1
fi
grep -Fq 'v4 -w -t raw -C PREROUTING -j GOLE_METADATA_INPUT' "$FIREWALL_LOG"
! grep -Fq 'v4 -w -t raw -D PREROUTING -j GOLE_METADATA_INPUT' "$FIREWALL_LOG"
! grep -Fq 'v4 -w -t raw -F GOLE_METADATA_INPUT' "$FIREWALL_LOG"

echo 'Metadata migration pending/full firewall runtime contract passed.'
CONTAINER_TEST
