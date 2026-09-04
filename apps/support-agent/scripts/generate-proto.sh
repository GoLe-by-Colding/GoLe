#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
GENERATED_DIR="$ROOT/apps/support-agent/generated"

mkdir -p "$GENERATED_DIR"
uv run --project "$ROOT/apps/support-agent" python -m grpc_tools.protoc \
  -I"$ROOT/apps/api/src/main/proto" \
  --python_out="$GENERATED_DIR" \
  --grpc_python_out="$GENERATED_DIR" \
  "$ROOT/apps/api/src/main/proto/gole/support/v1/support_agent.proto"
