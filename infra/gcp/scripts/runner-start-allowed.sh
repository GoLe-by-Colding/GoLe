#!/usr/bin/env bash
set -Eeuo pipefail

# This command is intentionally readable/executable by the unprivileged runner
# account. It exposes no secret: it only prevents a runner restart while the
# one-way metadata firewall ratchet is incomplete.
MARKER="/etc/gole/metadata-migration.pending"
DEPLOYMENT_TRANSACTION="/etc/gole/deployment.transaction"
DEPLOYED_SHA="/etc/gole/deployed.sha"

# A failed first deployment has no LKG to serve. Its root transaction is kept
# for an explicit IAP recovery, so the runner must not immediately accept a new
# job and repeat the same partial mutation after a reboot. Existing deployments
# may start the runner with a transaction so the normal CD recovery can restore
# their already-proven LKG.
if [ -e "$DEPLOYMENT_TRANSACTION" ] || [ -L "$DEPLOYMENT_TRANSACTION" ]; then
  if [ ! -f "$DEPLOYMENT_TRANSACTION" ] || [ -L "$DEPLOYMENT_TRANSACTION" ] ||
    [ "$(stat -c '%U:%G:%a' "$DEPLOYMENT_TRANSACTION" 2>/dev/null || true)" != "root:root:600" ]; then
    exit 1
  fi
  if [ ! -f "$DEPLOYED_SHA" ] || [ -L "$DEPLOYED_SHA" ] ||
    [ "$(stat -c '%U:%G:%a' "$DEPLOYED_SHA" 2>/dev/null || true)" != "root:root:644" ] ||
    ! grep -Eq '^[0-9a-f]{40}$' "$DEPLOYED_SHA" 2>/dev/null; then
    exit 1
  fi
fi

if [ ! -e "$MARKER" ] && [ ! -L "$MARKER" ]; then
  exit 0
fi
if [ ! -f "$MARKER" ] || [ -L "$MARKER" ] ||
  [ "$(stat -c '%U:%G:%a' "$MARKER" 2>/dev/null || true)" != "root:root:644" ]; then
  exit 1
fi

state="$(sed -n 's/^state=//p' "$MARKER" 2>/dev/null || true)"
legacy_sha="$(sed -n 's/^legacy_sha=//p' "$MARKER" 2>/dev/null || true)"
[ "$(grep -Ec '^(state|legacy_sha)=' "$MARKER" 2>/dev/null || true)" -eq 2 ] || exit 1
[ "$(wc -l < "$MARKER" 2>/dev/null || true)" -eq 2 ] || exit 1
[ "$(tail -c 1 "$MARKER" 2>/dev/null | wc -l)" -eq 1 ] || exit 1
[ "$(grep -Ec '^state=' "$MARKER" 2>/dev/null || true)" -eq 1 ] || exit 1
[ "$(grep -Ec '^legacy_sha=' "$MARKER" 2>/dev/null || true)" -eq 1 ] || exit 1
[[ "$legacy_sha" =~ ^[0-9a-f]{40}$ ]] || exit 1

case "$state" in
  pending) exit 0 ;;
  ratcheting) exit 1 ;;
  *) exit 1 ;;
esac
