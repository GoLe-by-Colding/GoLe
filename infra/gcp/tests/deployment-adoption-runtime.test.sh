#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
IMAGE="python@sha256:9d2e5553305c7c7b0097999bb17187c69b921ccd6bc9d40e4bb5ebe652c00285"

# The former one-shot marker writer trusted /app and could create an LKG marker
# without proving current origin/main and a successful push CI run. Existing
# hosts must use the single root-owned migrate-and-adopt transaction instead.
docker run --rm --interactive \
  --volume "$ROOT:/source:ro" \
  "$IMAGE" bash -seu <<'CONTAINER_TEST'
install -d -m 0755 /etc/gole /usr/local/sbin
printf 'root:root\n' > /etc/gole/deploy-user
install -m 0755 /source/infra/gcp/scripts/gole-hostctl.sh /usr/local/sbin/gole-hostctl

sha='0123456789abcdef0123456789abcdef01234567'
for retired in deployment-adopt-existing deployment-adoption-check; do
  if output="$(SUDO_USER=root /usr/local/sbin/gole-hostctl "$retired" "$sha" 2>&1)"; then
    echo "retired unsafe adoption operation remained callable: $retired" >&2
    exit 1
  fi
  grep -q 'unsupported host operation' <<<"$output"
done
[ ! -e /etc/gole/deployed.sha ]

grep -q 'create_release "$adoption_sha" historical-main' \
  /source/infra/gcp/scripts/gole-hostctl.sh
grep -q 'deployment-migrate-adopt-secret' /source/infra/gcp/scripts/migrate-and-adopt-existing.sh
echo 'Unsafe marker-only adoption is retired; transactional adoption provenance is required.'
CONTAINER_TEST
