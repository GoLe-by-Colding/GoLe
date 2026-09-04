#!/usr/bin/env bash
set -Eeuo pipefail

RUNTIME_DIRECTORY="${GOLE_CLOUD_BROKER_RUNTIME_DIRECTORY:-/run/gole-cloud-broker}"
SOCKET_PATH="$RUNTIME_DIRECTORY/broker.sock"
HEARTBEAT_PATH="$RUNTIME_DIRECTORY/policy-heartbeat"
MAX_ATTEMPTS="${GOLE_CLOUD_BROKER_READY_ATTEMPTS:-30}"

[ "$(id -u)" -eq 0 ] || {
  echo "cloud broker readiness must run as root" >&2
  exit 1
}
[[ "$MAX_ATTEMPTS" =~ ^[1-9][0-9]{0,2}$ ]] || exit 1
[ -d "$RUNTIME_DIRECTORY" ] && [ ! -L "$RUNTIME_DIRECTORY" ] &&
  [ "$(stat -c '%U:%G:%a' "$RUNTIME_DIRECTORY")" = "root:golecloud:710" ] || {
    echo "cloud broker runtime directory is invalid" >&2
    exit 1
  }

heartbeat_revision() {
  python3 - "$HEARTBEAT_PATH" <<'PY'
import os
import sys

try:
    print(os.stat(sys.argv[1], follow_symlinks=False).st_mtime_ns)
except FileNotFoundError:
    print(0)
PY
}

broker_roundtrip() {
  python3 - "$SOCKET_PATH" <<'PY'
import json
import socket
import sys

path = sys.argv[1]
request = b'{"operation":"readiness"}\n'
response = b""

try:
    with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as connection:
        connection.settimeout(2)
        connection.connect(path)
        connection.sendall(request)
        while not response.endswith(b"\n"):
            chunk = connection.recv(4096)
            if not chunk:
                break
            response += chunk
            if len(response) > 4096:
                raise ValueError("oversized readiness response")
    decoded = json.loads(response)
except (OSError, ValueError, json.JSONDecodeError):
    raise SystemExit(1)

expected = {
    "ok": True,
    "result": {"ready": True, "protocol_version": 1},
}
if decoded != expected:
    raise SystemExit(1)
PY
}

before="$(heartbeat_revision)"
for ((attempt = 1; attempt <= MAX_ATTEMPTS; attempt++)); do
  after="$(heartbeat_revision)"
  if [ -S "$SOCKET_PATH" ] && [ ! -L "$SOCKET_PATH" ] &&
    [ "$(stat -c '%U:%G:%a' "$SOCKET_PATH" 2>/dev/null || true)" = "root:golecloud:660" ] &&
    [ -f "$HEARTBEAT_PATH" ] && [ ! -L "$HEARTBEAT_PATH" ] &&
    [ "$(stat -c '%U:%G:%a' "$HEARTBEAT_PATH" 2>/dev/null || true)" = "root:golecloud:600" ] &&
    [[ "$after" =~ ^[1-9][0-9]*$ ]] && [ "$after" != "$before" ] &&
    broker_roundtrip; then
    exit 0
  fi
  sleep 1
done

echo "cloud broker socket round trip or advancing policy heartbeat is not ready" >&2
exit 1
