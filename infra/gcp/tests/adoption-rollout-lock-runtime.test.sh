#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
IMAGE="python@sha256:9d2e5553305c7c7b0097999bb17187c69b921ccd6bc9d40e4bb5ebe652c00285"

docker run --rm --interactive \
  --volume "$ROOT:/source:ro" \
  "$IMAGE" bash -seu <<'CONTAINER_TEST'
groupadd goledeploy
useradd --create-home --gid goledeploy goledeploy
install -d -m 0755 /etc/gole /usr/local/libexec/gole /usr/local/sbin /test-bin
printf 'goledeploy:goledeploy\n' > /etc/gole/deploy-user
install -m 0755 /source/infra/gcp/scripts/gole-hostctl.sh /usr/local/sbin/gole-hostctl
install -m 0755 /source/infra/gcp/scripts/validate-production-env.py \
  /usr/local/libexec/gole/validate-production-env.py
install -m 0660 -o root -g goledeploy /dev/null /run/lock/gole-production-rollout.lock

cat > /test-bin/sudo <<'EOF'
#!/bin/sh
echo 'hostctl must not run while another rollout owns the lock' >&2
exit 90
EOF
cat > /test-bin/git <<'EOF'
#!/bin/sh
case "$*" in
  *'status --porcelain'*) exit 0 ;;
  *'rev-parse --verify HEAD') printf '%s\n' "$EXPECTED_SHA" ;;
  *) exit 91 ;;
esac
EOF
cat > /test-bin/gcloud <<'EOF'
#!/bin/sh
echo 'Secret Manager must not be queried while another rollout owns the lock' >&2
exit 92
EOF
chmod 0755 /test-bin/sudo /test-bin/git /test-bin/gcloud

exec 7>>/run/lock/gole-production-rollout.lock
flock -n 7
expected_sha='0123456789abcdef0123456789abcdef01234567'
if env \
  PATH="/test-bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin" \
  GCP_PROJECT_ID=test-project EXPECTED_SHA="$expected_sha" \
  bash /source/infra/gcp/scripts/migrate-and-adopt-existing.sh \
    --sha "$expected_sha" \
    --request-id 10000000-0000-0000-0000-000000000001 \
    >/tmp/concurrent.out 2>&1; then
  echo 'concurrent adoption rollout was accepted' >&2
  exit 1
fi
grep -q '다른 운영 rollout' /tmp/concurrent.out

echo 'Existing deployment adoption lock runtime test passed.'
CONTAINER_TEST
