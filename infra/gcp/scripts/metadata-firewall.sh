#!/usr/bin/env bash
set -Eeuo pipefail

# GCE does not distinguish Linux UIDs when minting an attached service-account
# token. Deny metadata to every non-root host process and to every container;
# only the root-owned cloud broker can obtain a token.
METADATA_V4="169.254.169.254/32"
METADATA_V6="fd20:ce::254/128"

[ "$(id -u)" -eq 0 ] || {
  echo "metadata firewall must run as root" >&2
  exit 1
}

ensure_v4() {
  iptables -w -t raw -N GOLE_METADATA_INPUT 2>/dev/null || true
  iptables -w -t raw -F GOLE_METADATA_INPUT
  iptables -w -t raw -A GOLE_METADATA_INPUT -d "$METADATA_V4" -j DROP
  iptables -w -t raw -C PREROUTING -j GOLE_METADATA_INPUT 2>/dev/null ||
    iptables -w -t raw -I PREROUTING 1 -j GOLE_METADATA_INPUT

  iptables -w -N GOLE_METADATA_OUTPUT 2>/dev/null || true
  iptables -w -F GOLE_METADATA_OUTPUT
  iptables -w -A GOLE_METADATA_OUTPUT -m owner --uid-owner 0 -j RETURN
  iptables -w -A GOLE_METADATA_OUTPUT -j REJECT
  iptables -w -C OUTPUT -d "$METADATA_V4" -j GOLE_METADATA_OUTPUT 2>/dev/null ||
    iptables -w -I OUTPUT 1 -d "$METADATA_V4" -j GOLE_METADATA_OUTPUT
}

ensure_v6() {
  command -v ip6tables >/dev/null 2>&1 || return 0
  ip6tables -w -t raw -N GOLE_METADATA_INPUT 2>/dev/null || true
  ip6tables -w -t raw -F GOLE_METADATA_INPUT
  ip6tables -w -t raw -A GOLE_METADATA_INPUT -d "$METADATA_V6" -j DROP
  ip6tables -w -t raw -C PREROUTING -j GOLE_METADATA_INPUT 2>/dev/null ||
    ip6tables -w -t raw -I PREROUTING 1 -j GOLE_METADATA_INPUT

  ip6tables -w -N GOLE_METADATA_OUTPUT 2>/dev/null || true
  ip6tables -w -F GOLE_METADATA_OUTPUT
  ip6tables -w -A GOLE_METADATA_OUTPUT -m owner --uid-owner 0 -j RETURN
  ip6tables -w -A GOLE_METADATA_OUTPUT -j REJECT
  ip6tables -w -C OUTPUT -d "$METADATA_V6" -j GOLE_METADATA_OUTPUT 2>/dev/null ||
    ip6tables -w -I OUTPUT 1 -d "$METADATA_V6" -j GOLE_METADATA_OUTPUT
}

ensure_v4
ensure_v6
