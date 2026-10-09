#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
python3 "$ROOT/infra/gcp/tests/verify_docker_inspect_contract.py"
