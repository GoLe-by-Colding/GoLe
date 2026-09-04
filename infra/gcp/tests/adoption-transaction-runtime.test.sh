#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
IMAGE="python@sha256:9d2e5553305c7c7b0097999bb17187c69b921ccd6bc9d40e4bb5ebe652c00285"

docker run --rm --interactive \
  --volume "$ROOT:/source:ro" \
  "$IMAGE" bash -seu <<'CONTAINER_TEST'
export EXPECTED_SHA='0123456789abcdef0123456789abcdef01234567'
release="/var/lib/gole/releases/$EXPECTED_SHA"
install -d -m 0755 /app/.git /app/infra/gcp /etc/gole /usr/local/libexec/gole \
  /usr/local/sbin /test-bin "$release/infra/gcp"
touch /app/infra/gcp/docker-compose.yml "$release/infra/gcp/docker-compose.yml"
printf '%s\n' "$EXPECTED_SHA" > "$release/.gole-source-sha"
chown -R root:root /var/lib/gole
chmod -R go-w /var/lib/gole
printf 'root:root\n' > /etc/gole/deploy-user
printf 'MINIO_ROOT_USER=test\nMINIO_ROOT_PASSWORD=test-password\n' > /etc/gole/infra.env
printf 'PROJECT_ID=test-project-123\n' > /etc/gole/cloud-broker.conf
chmod 0600 /etc/gole/infra.env /etc/gole/cloud-broker.conf
install -m 0755 /source/infra/gcp/scripts/gole-hostctl.sh /usr/local/sbin/gole-hostctl
install -m 0755 /source/infra/gcp/scripts/validate-production-env.py \
  /usr/local/libexec/gole/validate-production-env.py
ln -s /usr/local/bin/python3 /usr/bin/python3
printf '#!/bin/sh\ncat >/dev/null\n' > /usr/local/libexec/gole/validate-production-compose.py
printf '#!/bin/sh\n[ "$1" = "--historical-main" ] && [ "$2" = "%s" ]\n' \
  "$EXPECTED_SHA" > /usr/local/libexec/gole/verify-github-release.py
chmod 0755 /usr/local/libexec/gole/*.py

cat > /usr/bin/git <<EOF
#!/bin/sh
case "\$*" in
  *'ls-remote '*'refs/heads/main'*) printf '%s\\n' '$EXPECTED_SHA' ;;
  *) exit 90 ;;
esac
EOF
chmod 0755 /usr/bin/git

cat > /test-bin/git <<'FAKE_GIT'
#!/bin/sh
case "$*" in
  *'status --porcelain'*) ;;
  *'rev-parse --verify HEAD') printf '%s\n' "$EXPECTED_SHA" ;;
  *) exit 91 ;;
esac
FAKE_GIT

cat > /test-bin/docker <<'FAKE_DOCKER'
#!/bin/sh
if [ "$1" = compose ]; then
  case "$*" in
    *'config --services'*) printf 'support-agent\nbackend\nfrontend\nbudget-relay\nnginx\n' ;;
    *'exec -T mongo'*) printf '0\n' ;;
  esac
  exit 0
fi
if [ "$1" = inspect ]; then
  case "$*" in
    *'com.docker.compose.project'*gole-backend|*'com.docker.compose.project'*gole-budget-relay)
      printf 'gole\n' ;;
    *'com.docker.compose.service'*gole-backend) printf 'backend\n' ;;
    *'com.docker.compose.service'*gole-budget-relay) printf 'budget-relay\n' ;;
    *'.Config.Env'*gole-backend)
      cat <<'ENV'
DISCORD_DEPLOY_WEBHOOK_URL=https://discord.com/api/webhooks/100000000000000001/ABCDEFGHIJKLMNOPQRSTUVWXYZabcdef_1000000001
DISCORD_OPERATIONS_WEBHOOK_URL=https://discord.com/api/webhooks/100000000000000002/ABCDEFGHIJKLMNOPQRSTUVWXYZabcdef_1000000002
DISCORD_ACCOUNT_WEBHOOK_URL=https://discord.com/api/webhooks/100000000000000003/ABCDEFGHIJKLMNOPQRSTUVWXYZabcdef_1000000003
DISCORD_PAYMENT_WEBHOOK_URL=https://discord.com/api/webhooks/100000000000000004/ABCDEFGHIJKLMNOPQRSTUVWXYZabcdef_1000000004
DISCORD_SUPPRESS_NOTIFICATIONS=false
ENV
      ;;
    *'.Config.Env'*gole-budget-relay)
      printf 'DISCORD_OPERATIONS_WEBHOOK_URL=https://discord.com/api/webhooks/100000000000000002/ABCDEFGHIJKLMNOPQRSTUVWXYZabcdef_1000000002\n'
      ;;
    *) printf 'running:healthy\n' ;;
  esac
  exit 0
fi
if [ "$1" = exec ]; then exit 0; fi
exit 92
FAKE_DOCKER

cat > /usr/bin/gcloud <<'FAKE_GCLOUD'
#!/bin/sh
set -eu
[ "$1" = secrets ] && [ "$2" = versions ] && [ "$3" = access ] && [ "$4" = 6 ] || exit 93
shift 4
for argument in "$@"; do
  case "$argument" in
    --secret=gole-production-env|--project=test-project-123|--quiet) ;;
    --out-file=*) output="${argument#--out-file=}" ;;
    *) exit 94 ;;
  esac
done
[ -n "${output:-}" ] || exit 95
cp /source/infra/gcp/tests/fixtures/production.env "$output"
FAKE_GCLOUD

cat > /test-bin/curl <<'FAKE_CURL'
#!/bin/sh
case "$*" in
  *'http://www.gole.co.kr/__gole-canonical-check?source=runtime'*)
    printf '301|https://gole.co.kr/__gole-canonical-check?source=runtime' ;;
  *'https://www.gole.co.kr/__gole-canonical-check?source=runtime'*)
    printf '301|https://gole.co.kr/__gole-canonical-check?source=runtime' ;;
  *'-fsSI '*'https://gole.co.kr/'*)
    printf 'HTTP/2 200\r\nStrict-Transport-Security: max-age=31536000\r\n\r\n' ;;
esac
exit 0
FAKE_CURL

cat > /test-bin/systemctl <<'FAKE_SYSTEMCTL'
#!/bin/sh
[ "$1" = is-active ] && [ "$2" = --quiet ] &&
  [ "$3" = gole-cost-guard-watchdog.timer ]
FAKE_SYSTEMCTL

chmod 0755 /test-bin/git /test-bin/docker /test-bin/curl /test-bin/systemctl
chmod 0755 /usr/bin/gcloud
install -m 0755 /test-bin/docker /usr/local/bin/docker
export PATH="/test-bin:$PATH"

reset_legacy_deployment() {
  rm -f /etc/gole/deployed.sha /etc/gole/gole.adoption.transaction \
    /etc/gole/initial-deploy.pending /etc/gole/gole.env.transaction \
    /etc/gole/nginx.conf.transaction
  install -m 0600 -o root -g root \
    /source/infra/gcp/tests/fixtures/development.env /etc/gole/gole.env
  printf '5\n' > /etc/gole/gole.env.version
  chmod 0644 /etc/gole/gole.env.version
}

new_candidate() {
  candidate="$1"
  install -m 0600 -o root -g root \
    /source/infra/gcp/tests/fixtures/production.env "$candidate"
}

reset_legacy_deployment
old_hash="$(sha256sum /etc/gole/gole.env | cut -d' ' -f1)"
request_abort='10000000-0000-0000-0000-000000000001'
candidate_abort=/tmp/gole-env.ADOPT01
new_candidate "$candidate_abort"
candidate_hash="$(sha256sum "$candidate_abort" | cut -d' ' -f1)"
transaction_output="$(SUDO_USER=root /usr/local/sbin/gole-hostctl adoption-transaction-begin \
  "$candidate_abort" 6 "$request_abort" "$EXPECTED_SHA" 2>&1)"
[ "$(sha256sum /etc/gole/gole.env | cut -d' ' -f1)" = "$candidate_hash" ]
[ "$(cat /etc/gole/gole.env.version)" = 5 ]
[ ! -e /etc/gole/deployed.sha ]
grep -qx 'state=installed' /etc/gole/gole.adoption.transaction

# The root helper staged the candidate before validation/install. Mutating the
# caller-owned temporary file afterward must not alter the installed env.
printf 'GOLE_ENVIRONMENT=local\nSMTP_PASSWORD=leaked-secret-value\n' > "$candidate_abort"
[ "$(sha256sum /etc/gole/gole.env | cut -d' ' -f1)" = "$candidate_hash" ]
SUDO_USER=root /usr/local/sbin/gole-hostctl adoption-transaction-mark-ready "$request_abort"
SUDO_USER=root /usr/local/sbin/gole-hostctl adoption-transaction-abort "$request_abort"
[ "$(sha256sum /etc/gole/gole.env | cut -d' ' -f1)" = "$old_hash" ]
[ "$(cat /etc/gole/gole.env.version)" = 5 ]
[ ! -e /etc/gole/deployed.sha ]
SUDO_USER=root /usr/local/sbin/gole-hostctl adoption-transaction-finish-recovery "$request_abort"
[ ! -e /etc/gole/gole.adoption.transaction ]

# Equivalent to SIGKILL after candidate installation: the next invocation must
# restore the legacy env/version and retain a journal until services are checked.
reset_legacy_deployment
request_kill_installed='20000000-0000-0000-0000-000000000002'
candidate_kill_installed=/tmp/gole-env.ADOPT02
new_candidate "$candidate_kill_installed"
SUDO_USER=root /usr/local/sbin/gole-hostctl adoption-transaction-begin \
  "$candidate_kill_installed" 6 "$request_kill_installed" "$EXPECTED_SHA"
recovery="$(SUDO_USER=root /usr/local/sbin/gole-hostctl adoption-transaction-recover)"
[ "$recovery" = "RECOVERY_REQUIRED:$request_kill_installed" ]
[ "$(sha256sum /etc/gole/gole.env | cut -d' ' -f1)" = "$old_hash" ]
[ "$(cat /etc/gole/gole.env.version)" = 5 ]
[ ! -e /etc/gole/deployed.sha ]
SUDO_USER=root /usr/local/sbin/gole-hostctl \
  adoption-transaction-finish-recovery "$request_kill_installed"

# Equivalent to SIGKILL after readiness but before the two markers: readiness
# alone is never enough to retain the new environment.
reset_legacy_deployment
request_kill_ready='30000000-0000-0000-0000-000000000003'
candidate_kill_ready=/tmp/gole-env.ADOPT03
new_candidate "$candidate_kill_ready"
SUDO_USER=root /usr/local/sbin/gole-hostctl adoption-transaction-begin \
  "$candidate_kill_ready" 6 "$request_kill_ready" "$EXPECTED_SHA"
SUDO_USER=root /usr/local/sbin/gole-hostctl adoption-transaction-mark-ready "$request_kill_ready"
recovery="$(SUDO_USER=root /usr/local/sbin/gole-hostctl adoption-transaction-recover)"
[ "$recovery" = "RECOVERY_REQUIRED:$request_kill_ready" ]
[ "$(sha256sum /etc/gole/gole.env | cut -d' ' -f1)" = "$old_hash" ]
[ ! -e /etc/gole/deployed.sha ]
SUDO_USER=root /usr/local/sbin/gole-hostctl \
  adoption-transaction-finish-recovery "$request_kill_ready"

# Once health was recorded and both markers were atomically written, a crash
# before journal deletion completes forward instead of rolling back.
reset_legacy_deployment
request_commit='40000000-0000-0000-0000-000000000004'
candidate_commit=/tmp/gole-env.ADOPT04
new_candidate "$candidate_commit"
committed_hash="$(sha256sum "$candidate_commit" | cut -d' ' -f1)"
SUDO_USER=root /usr/local/sbin/gole-hostctl adoption-transaction-begin \
  "$candidate_commit" 6 "$request_commit" "$EXPECTED_SHA"
SUDO_USER=root /usr/local/sbin/gole-hostctl adoption-transaction-mark-ready "$request_commit"
SUDO_USER=root /usr/local/sbin/gole-hostctl adoption-transaction-commit "$request_commit"
[ "$(cat /etc/gole/gole.env.version)" = 6 ]
[ "$(cat /etc/gole/deployed.sha)" = "$EXPECTED_SHA" ]
recovery="$(SUDO_USER=root /usr/local/sbin/gole-hostctl adoption-transaction-recover)"
[ "$recovery" = COMMITTED ]
[ "$(sha256sum /etc/gole/gole.env | cut -d' ' -f1)" = "$committed_hash" ]
[ ! -e /etc/gole/gole.adoption.transaction ]

all_output="$transaction_output $recovery"
! grep -Fq 'leaked-secret-value' <<<"$all_output"
! grep -Fq 'abcdefghijklmnop' <<<"$all_output"
! grep -Fq "$candidate_hash" <<<"$all_output"

# The complete one-time migration proves the clean legacy SHA/containers,
# preserves only existing same-purpose routes, and maps the missing legacy
# support route to the trusted GoLe operations room without exposing values.
reset_legacy_deployment
request_migrate='50000000-0000-0000-0000-000000000005'
if ! migrate_output="$(SUDO_USER=root /usr/local/sbin/gole-hostctl \
  deployment-migrate-adopt-secret "$EXPECTED_SHA" 6 "$request_migrate" 2>&1)"; then
  printf '%s\n' "$migrate_output" >&2
  exit 1
fi
[ "$(cat /etc/gole/deployed.sha)" = "$EXPECTED_SHA" ]
[ "$(cat /etc/gole/gole.env.version)" = 6 ]
[ "$(stat -c '%U:%G:%a' /etc/gole/discord.env)" = root:root:600 ]
operations_route="$(sed -n 's/^DISCORD_OPERATIONS_WEBHOOK_URL=//p' /etc/gole/discord.env)"
support_route="$(sed -n 's/^DISCORD_SUPPORT_WEBHOOK_URL=//p' /etc/gole/discord.env)"
[ "$support_route" = "$operations_route" ]
! grep -Fq 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdef' <<<"$migrate_output"

echo 'Existing deployment migration transaction runtime tests passed.'
CONTAINER_TEST
