#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
IMAGE="python@sha256:9d2e5553305c7c7b0097999bb17187c69b921ccd6bc9d40e4bb5ebe652c00285"

docker run --rm --interactive --volume "$ROOT:/source:ro" "$IMAGE" bash -seu <<'CONTAINER_TEST'
groupadd --system --gid 10001 golecloud
runtime=/run/gole-cloud-broker
install -d -m 0710 -o root -g golecloud "$runtime"

start_broker_server() {
  local mode="$1"
  python3 - "$mode" <<'PY' &
import os
import json
import socket
import sys
import time

mode = sys.argv[1]
path = "/run/gole-cloud-broker/broker.sock"
sock = socket.socket(socket.AF_UNIX)
sock.bind(path)
os.chmod(path, 0o660)
sock.listen(4)
sock.settimeout(1)
deadline = time.monotonic() + 30
while time.monotonic() < deadline:
    try:
        connection, _ = sock.accept()
    except TimeoutError:
        continue
    with connection:
        connection.settimeout(2)
        request = b""
        while not request.endswith(b"\n"):
            chunk = connection.recv(4096)
            if not chunk:
                break
            request += chunk
        if mode == "current" and json.loads(request) == {"operation": "readiness"}:
            response = {
                "ok": True,
                "result": {"ready": True, "protocol_version": 1},
            }
        else:
            response = {"ok": False, "error": "broker request rejected"}
        connection.sendall(
            json.dumps(response, separators=(",", ":")).encode("utf-8") + b"\n"
        )
PY
  broker_pid=$!
  for _attempt in 1 2 3 4 5; do [ -S "$runtime/broker.sock" ] && break; sleep 1; done
  chown root:golecloud "$runtime/broker.sock"
}

broker_pid=""
trap 'if [ -n "$broker_pid" ]; then kill "$broker_pid" 2>/dev/null || true; fi' EXIT
start_broker_server current
for _attempt in 1 2 3 4 5; do [ -S "$runtime/broker.sock" ] && break; sleep 1; done
chown root:golecloud "$runtime/broker.sock"
install -m 0600 -o root -g golecloud /dev/null "$runtime/policy-heartbeat"

( sleep 1; printf 'ok\n' > "$runtime/policy-heartbeat" ) &
GOLE_CLOUD_BROKER_READY_ATTEMPTS=5 \
  bash /source/infra/gcp/scripts/verify-cloud-broker-ready.sh

if GOLE_CLOUD_BROKER_READY_ATTEMPTS=1 \
  bash /source/infra/gcp/scripts/verify-cloud-broker-ready.sh >/dev/null 2>&1; then
  echo 'broker readiness accepted a non-advancing heartbeat' >&2
  exit 1
fi

kill "$broker_pid"
wait "$broker_pid" 2>/dev/null || true
broker_pid=""
rm -f "$runtime/broker.sock"
start_broker_server stale
( sleep 1; printf 'ok\n' > "$runtime/policy-heartbeat" ) &
if GOLE_CLOUD_BROKER_READY_ATTEMPTS=3 \
  bash /source/infra/gcp/scripts/verify-cloud-broker-ready.sh >/dev/null 2>&1; then
  echo 'broker readiness accepted a stale protocol process after heartbeat advance' >&2
  exit 1
fi

chmod 0755 "$runtime"
if GOLE_CLOUD_BROKER_READY_ATTEMPTS=1 \
  bash /source/infra/gcp/scripts/verify-cloud-broker-ready.sh >/dev/null 2>&1; then
  echo 'broker readiness accepted an unsafe runtime directory' >&2
  exit 1
fi

echo 'Cloud broker boot-readiness gate passed.'
CONTAINER_TEST
