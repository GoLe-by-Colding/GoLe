#!/usr/bin/env bash
set -Eeuo pipefail

# GCE does not distinguish Linux UIDs when minting an attached service-account
# token. Deny metadata to every non-root host process and to every container;
# preserve TCP/UDP DNS on the same GCE address for systemd-resolved and Docker.
# only the root-owned cloud broker can obtain a token.
METADATA_V4="169.254.169.254/32"
METADATA_V6="fd20:ce::254/128"
MIGRATION_MARKER="/etc/gole/metadata-migration.pending"
REQUESTED_MODE="auto"

[ "$(id -u)" -eq 0 ] || {
  echo "metadata firewall must run as root" >&2
  exit 1
}

if [ "$#" -gt 1 ]; then
  echo "usage: gole-metadata-firewall [--full]" >&2
  exit 2
fi
if [ "$#" -eq 1 ]; then
  [ "$1" = "--full" ] || {
    echo "usage: gole-metadata-firewall [--full]" >&2
    exit 2
  }
  REQUESTED_MODE="full"
fi

read_migration_state() {
  local key value seen_legacy=0 seen_state=0
  MIGRATION_STATE=""
  MIGRATION_LEGACY_SHA=""
  [ -e "$MIGRATION_MARKER" ] || return 1
  if [ ! -f "$MIGRATION_MARKER" ] || [ -L "$MIGRATION_MARKER" ] ||
    [ "$(stat -c '%U:%G:%a' "$MIGRATION_MARKER")" != "root:root:644" ]; then
    return 2
  fi
  while IFS='=' read -r key value || [ -n "${key}${value}" ]; do
    case "$key" in
      state)
        [ "$seen_state" -eq 0 ] || return 2
        MIGRATION_STATE="$value"
        seen_state=1
        ;;
      legacy_sha)
        [ "$seen_legacy" -eq 0 ] || return 2
        MIGRATION_LEGACY_SHA="$value"
        seen_legacy=1
        ;;
      *) return 2 ;;
    esac
  done < "$MIGRATION_MARKER"
  [ "$seen_state$seen_legacy" = "11" ] || return 2
  [[ "$MIGRATION_STATE" =~ ^(pending|ratcheting)$ ]] || return 2
  [[ "$MIGRATION_LEGACY_SHA" =~ ^[0-9a-f]{40}$ ]] || return 2
}

marker_status=1
if read_migration_state; then
  marker_status=0
else
  marker_status=$?
fi
if [ "$REQUESTED_MODE" = full ]; then
  FIREWALL_MODE="full"
elif [ "$marker_status" -eq 0 ] && [ "$MIGRATION_STATE" = pending ]; then
  FIREWALL_MODE="pending"
else
  # A malformed marker is never interpreted as permission to expose metadata.
  # Apply the full rule set first, then fail the unit so the host is visibly
  # unhealthy while remaining closed.
  FIREWALL_MODE="full"
fi

replace_output_chain() {
  local tool="$1" address="$2" candidate
  candidate="GOLE_MO_$$_${RANDOM}"

  # Build a complete replacement before it is reachable. Insert it ahead of
  # the old chain, then retire the old chain. At every command boundary at
  # least one rejecting path remains active, including on interruption.
  "$tool" -w -N "$candidate"
  "$tool" -w -A "$candidate" -p udp --dport 53 -j RETURN
  "$tool" -w -A "$candidate" -p tcp --dport 53 -j RETURN
  "$tool" -w -A "$candidate" -m owner --uid-owner 0 -j RETURN
  "$tool" -w -A "$candidate" -j REJECT
  "$tool" -w -I OUTPUT 1 -d "$address" -j "$candidate"
  while "$tool" -w -C OUTPUT -d "$address" -j GOLE_METADATA_OUTPUT 2>/dev/null; do
    "$tool" -w -D OUTPUT -d "$address" -j GOLE_METADATA_OUTPUT
  done
  "$tool" -w -F GOLE_METADATA_OUTPUT 2>/dev/null || true
  "$tool" -w -X GOLE_METADATA_OUTPUT 2>/dev/null || true
  "$tool" -w -E "$candidate" GOLE_METADATA_OUTPUT
}

replace_input_chain_full() {
  local tool="$1" address="$2" candidate
  candidate="GOLE_MI_$$_${RANDOM}"

  "$tool" -w -t raw -N "$candidate"
  "$tool" -w -t raw -A "$candidate" -d "$address" -p udp --dport 53 -j RETURN
  "$tool" -w -t raw -A "$candidate" -d "$address" -p tcp --dport 53 -j RETURN
  "$tool" -w -t raw -A "$candidate" -d "$address" -j DROP
  "$tool" -w -t raw -I PREROUTING 1 -j "$candidate"
  while "$tool" -w -t raw -C PREROUTING -j GOLE_METADATA_INPUT 2>/dev/null; do
    "$tool" -w -t raw -D PREROUTING -j GOLE_METADATA_INPUT
  done
  "$tool" -w -t raw -F GOLE_METADATA_INPUT 2>/dev/null || true
  "$tool" -w -t raw -X GOLE_METADATA_INPUT 2>/dev/null || true
  "$tool" -w -t raw -E "$candidate" GOLE_METADATA_INPUT
}

ensure_family() {
  local tool="$1" address="$2"
  replace_output_chain "$tool" "$address"
  if [ "$FIREWALL_MODE" = full ]; then
    replace_input_chain_full "$tool" "$address"
  elif "$tool" -w -t raw -C PREROUTING -j GOLE_METADATA_INPUT 2>/dev/null; then
    # Security isolation is monotonic: a stale/full host is never downgraded
    # merely because a pending marker appeared.
    echo "refusing to reopen container metadata access" >&2
    return 1
  fi
}

ensure_v4() {
  ensure_family iptables "$METADATA_V4"
}

ensure_v6() {
  command -v ip6tables >/dev/null 2>&1 || return 0
  ensure_family ip6tables "$METADATA_V6"
}

ensure_v4
ensure_v6

if [ "$marker_status" -eq 2 ]; then
  echo "metadata migration marker is invalid; full isolation was applied" >&2
  exit 1
fi
