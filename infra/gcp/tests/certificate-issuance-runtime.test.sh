#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
IMAGE="ubuntu@sha256:33ceb71981b602c1a7443a53469e4dba065f7503eab3078a2d7a57a2ab987517"

docker run --rm --interactive \
  --volume "$ROOT:/source:ro" \
  "$IMAGE" bash -seu <<'CONTAINER_TEST'
sha='0123456789abcdef0123456789abcdef01234567'
deployed_sha='89abcdef0123456789abcdef0123456789abcdef'
release="/var/lib/gole/releases/$sha"
deployed_release="/var/lib/gole/releases/$deployed_sha"
issuer='/source/infra/gcp/scripts/issue-certificate.sh'
transaction='/var/lib/gole/certificate/nginx.transaction'
backup='/var/backups/gole-certificate/nginx.conf.before-https'
certificate_root='/var/lib/docker/volumes/gole_letsencrypt/_data/live/gole.co.kr'

install -d -m 0755 /etc/gole /var/lib/gole/releases /usr/local/bin /run/lock \
  "$release/infra/gcp" "$certificate_root"
install -m 0660 -o root -g root /dev/null /run/lock/gole-production-rollout.lock
install -m 0644 /source/infra/gcp/docker-compose.yml \
  "$release/infra/gcp/docker-compose.yml"
install -m 0644 /source/infra/gcp/nginx-https.conf.template \
  "$release/infra/gcp/nginx-https.conf.template"
printf '%s\n' "$sha" >"$release/.gole-source-sha"
chown -R root:root "$release"
chmod -R go-w "$release"
printf 'MINIO_ROOT_USER=test\nMINIO_ROOT_PASSWORD=test-password\n' >/etc/gole/infra.env
printf 'POLICY=test\n' >/etc/gole/gole.env
chmod 0600 /etc/gole/infra.env /etc/gole/gole.env
printf 'certificate\n' >"$certificate_root/fullchain.pem"
printf 'private-key\n' >"$certificate_root/privkey.pem"

cat >/usr/local/bin/id <<'FAKE_ID'
#!/bin/sh
if [ "${1:-}" = -u ] && [ -n "${FAKE_ID_UID:-}" ]; then
  printf '%s\n' "$FAKE_ID_UID"
else
  exec /usr/bin/id "$@"
fi
FAKE_ID

cat >/usr/local/bin/gcloud <<'FAKE_GCLOUD'
#!/bin/sh
set -eu
printf '%s\n' "$*" >>/tmp/gcloud.calls
case "$*" in
  'publicca external-account-keys create --project project-72a52bf1-06aa-4519-b2c --verbosity=error --format=value(keyId,b64MacKey)')
    printf 'test-eab-kid\ttest-eab-secret-never-log\n'
    ;;
  *) exit 90 ;;
esac
FAKE_GCLOUD

cat >/usr/local/bin/openssl <<'FAKE_OPENSSL'
#!/bin/sh
set -eu
printf '%s\n' "$*" >>/tmp/openssl.calls
case " $* " in
  *' x509 -checkend 86400 -noout -in '*) : ;;
  *' x509 -checkhost gole.co.kr -noout -in '*) : ;;
  *' x509 -checkhost www.gole.co.kr -noout -in '*) : ;;
  *) exit 91 ;;
esac
FAKE_OPENSSL

cat >/usr/local/bin/curl <<'FAKE_CURL'
#!/usr/bin/env bash
set -eu
headers=''
previous=''
url=''
for argument in "$@"; do
  if [ "$previous" = --dump-header ]; then
    headers="$argument"
  fi
  previous="$argument"
  case "$argument" in http://* | https://*) url="$argument" ;; esac
done
printf '%s\n' "$url" >>/tmp/curl.calls
if [ -n "$headers" ]; then
  printf 'HTTP/2 200\r\nStrict-Transport-Security: max-age=31536000\r\n\r\n' >"$headers"
fi
case "$url" in
  http://www.gole.co.kr/ | https://www.gole.co.kr/)
    printf '301\thttps://gole.co.kr/'
    ;;
  https://gole.co.kr/) printf '200' ;;
  *) exit 92 ;;
esac
FAKE_CURL

cat >/usr/local/bin/systemctl <<'FAKE_SYSTEMCTL'
#!/bin/sh
printf '%s\n' "$*" >>/tmp/systemctl.calls
exit 0
FAKE_SYSTEMCTL

cat >/usr/local/bin/sync <<'FAKE_SYNC'
#!/bin/sh
set -eu
state="$(/usr/bin/sed -n 's/^state=//p' \
  /var/lib/gole/certificate/nginx.transaction 2>/dev/null || true)"
printf '%s|state=%s\n' "$*" "$state" >>/tmp/sync.calls
if [ -e /tmp/kill-on-certificate-config-sync ] &&
  [ "$*" = '-f /etc/gole/nginx.conf' ] && [ "$state" = prepared ]; then
  rm -f /tmp/kill-on-certificate-config-sync
  kill -KILL "$PPID"
fi
exit 0
FAKE_SYNC

cat >/usr/local/bin/docker <<'FAKE_DOCKER'
#!/usr/bin/env bash
set -eu
printf '%s\n' "$*" >>/tmp/docker.calls
joined=" $* "
case "$joined" in
  *' for account in "$1"/'*)
    [ -e /tmp/gts-account ] && exit 0
    exit 42
    ;;
  *' if [ ! -f "$1" ]; then exit 42;'*)
    exit 42
    ;;
  *' certbot register '*)
    previous=''
    eab_mount=''
    for argument in "$@"; do
      if [ "$previous" = --volume ]; then
        eab_mount="$argument"
      fi
      previous="$argument"
    done
    eab_file="${eab_mount%%:*}"
    [ -f "$eab_file" ] && [ ! -L "$eab_file" ]
    [ "$(/usr/bin/stat -c '%U:%G:%a' "$eab_file")" = root:root:600 ]
    /usr/bin/grep -Fqx 'eab-kid = test-eab-kid' "$eab_file"
    /usr/bin/grep -Fqx 'eab-hmac-key = test-eab-secret-never-log' "$eab_file"
    touch /tmp/gts-account
    ;;
  *' up -d --no-build --no-deps --force-recreate --wait nginx '*)
    count="$(cat /tmp/nginx-up-count 2>/dev/null || printf 0)"
    count=$((count + 1))
    printf '%s\n' "$count" >/tmp/nginx-up-count
    config_sha="$(/usr/bin/sha256sum /etc/gole/nginx.conf | /usr/bin/awk '{print $1}')"
    printf '%s:%s\n' "$count" "$config_sha" >>/tmp/nginx-up-configs
    if [ "${FAIL_NGINX_UP_AT:-0}" -eq "$count" ]; then
      exit 55
    fi
    ;;
  *' exec -T nginx nginx -t '*) : ;;
  *' certbot certonly '*) : ;;
  *' run --rm --network none '*) : ;;
  *) : ;;
esac
FAKE_DOCKER

chmod 0755 /usr/local/bin/id /usr/local/bin/gcloud /usr/local/bin/openssl \
  /usr/local/bin/curl /usr/local/bin/systemctl /usr/local/bin/sync \
  /usr/local/bin/docker

reset_case() {
  rm -rf /var/lib/gole/certificate /var/backups/gole-certificate \
    /run/gole-certificate
  rm -f /tmp/docker.calls /tmp/curl.calls /tmp/gcloud.calls \
    /tmp/openssl.calls /tmp/systemctl.calls /tmp/sync.calls \
    /tmp/nginx-up-count /tmp/nginx-up-configs \
    /tmp/gts-account /tmp/kill-on-certificate-config-sync
  printf 'old-http-config\n' >/etc/gole/nginx.conf
  chown root:root /etc/gole/nginx.conf
  chmod 0644 /etc/gole/nginx.conf
}

invoke_locked() {
  local output="$1"
  shift
  exec 8>>/run/lock/gole-production-rollout.lock
  flock -n 8
  set +e
  env \
    GOLE_ROLLOUT_LOCK_HELD=1 \
    GOLE_TRUSTED_RELEASE_ROOT="$release" \
    DOMAIN=gole.co.kr \
    EMAIL=coldingcontact@gmail.com \
    GCP_PROJECT_ID=project-72a52bf1-06aa-4519-b2c \
    "$@" bash "$issuer" >"$output" 2>&1
  INVOKE_STATUS=$?
  set -e
  flock -u 8
  exec 8>&-
}

reset_case
set +e
env FAKE_ID_UID=1000 \
  GOLE_ROLLOUT_LOCK_HELD=1 \
  GOLE_TRUSTED_RELEASE_ROOT="$release" \
  DOMAIN=gole.co.kr EMAIL=coldingcontact@gmail.com \
  GCP_PROJECT_ID=project-72a52bf1-06aa-4519-b2c \
  bash "$issuer" >/tmp/non-root.out 2>&1
non_root_status=$?
set -e
[ "$non_root_status" -ne 0 ]
grep -Fq 'must run as root' /tmp/non-root.out

set +e
env GOLE_ROLLOUT_LOCK_HELD=1 \
  GOLE_TRUSTED_RELEASE_ROOT="$release" \
  DOMAIN=gole.co.kr EMAIL=coldingcontact@gmail.com \
  GCP_PROJECT_ID=project-72a52bf1-06aa-4519-b2c \
  bash "$issuer" >/tmp/no-lock.out 2>&1 8>&-
no_lock_status=$?
set -e
[ "$no_lock_status" -ne 0 ]
grep -Fq 'does not own the production rollout lock' /tmp/no-lock.out

reset_case
invoke_locked /tmp/success.out
[ "$INVOKE_STATUS" -eq 0 ]
grep -Fq 'server_name gole.co.kr www.gole.co.kr' /etc/gole/nginx.conf
[ ! -e "$transaction" ] && [ ! -L "$transaction" ]
[ ! -e "$backup" ] && [ ! -L "$backup" ]
if find /run/gole-certificate -maxdepth 1 -name 'gts-eab.*' -print -quit | grep -q .; then
  echo 'one-time EAB file survived successful registration' >&2
  exit 1
fi
if grep -R -Fq 'test-eab-secret-never-log' \
  /tmp/success.out /tmp/docker.calls /tmp/gcloud.calls; then
  echo 'one-time EAB secret was written to logs' >&2
  exit 1
fi
grep -Fq 'x509 -checkend 86400' /tmp/openssl.calls
grep -Fq 'x509 -checkhost gole.co.kr' /tmp/openssl.calls
grep -Fq 'x509 -checkhost www.gole.co.kr' /tmp/openssl.calls
grep -Fxq 'https://gole.co.kr/' /tmp/curl.calls
grep -Fxq 'http://www.gole.co.kr/' /tmp/curl.calls
grep -Fxq 'https://www.gole.co.kr/' /tmp/curl.calls
[ "$(grep -c -- 'up -d --no-build --no-deps --force-recreate --wait nginx' /tmp/docker.calls)" -eq 2 ]
backup_sync_line="$(grep -nF -- "-f $backup|state=" /tmp/sync.calls | head -n1 | cut -d: -f1)"
prepared_sync_line="$(grep -nF -- "-f $transaction|state=prepared" /tmp/sync.calls | head -n1 | cut -d: -f1)"
config_sync_line="$(grep -nF -- '-f /etc/gole/nginx.conf|state=prepared' /tmp/sync.calls | head -n1 | cut -d: -f1)"
installed_sync_line="$(grep -nF -- "-f $transaction|state=installed" /tmp/sync.calls | head -n1 | cut -d: -f1)"
[ "$backup_sync_line" -lt "$prepared_sync_line" ]
[ "$prepared_sync_line" -lt "$config_sync_line" ]
[ "$config_sync_line" -lt "$installed_sync_line" ]

reset_case
touch /tmp/gts-account
invoke_locked /tmp/activation-failure.out FAIL_NGINX_UP_AT=2
[ "$INVOKE_STATUS" -ne 0 ]
grep -Fxq 'old-http-config' /etc/gole/nginx.conf
[ ! -e "$transaction" ] && [ ! -e "$backup" ]
[ ! -s /tmp/systemctl.calls ]
[ "$(cat /tmp/nginx-up-count)" -eq 3 ]

# SIGKILL after the replacement config fsync leaves a prepared durable journal.
# The following invocation must restore it before it retries issuance.
reset_case
touch /tmp/gts-account /tmp/kill-on-certificate-config-sync
invoke_locked /tmp/killed.out
[ "$INVOKE_STATUS" -ne 0 ]
grep -Fxq 'state=prepared' "$transaction"
[ -f "$backup" ]
candidate_sha="$(sha256sum /etc/gole/nginx.conf | awk '{print $1}')"
old_sha="$(sha256sum "$backup" | awk '{print $1}')"
[ "$candidate_sha" != "$old_sha" ]
rm -f /tmp/docker.calls /tmp/nginx-up-count /tmp/nginx-up-configs
invoke_locked /tmp/recovered.out
[ "$INVOKE_STATUS" -eq 0 ]
first_recovery_sha="$(head -n1 /tmp/nginx-up-configs | cut -d: -f2)"
[ "$first_recovery_sha" = "$old_sha" ]
grep -Fq 'server_name gole.co.kr www.gole.co.kr' /etc/gole/nginx.conf
[ ! -e "$transaction" ] && [ ! -e "$backup" ]
[ ! -s /tmp/systemctl.calls ]

# If a durable stale transaction cannot restart the restored Nginx, retain both
# recovery artifacts and request a VM poweroff instead of continuing uncertainly.
reset_case
touch /tmp/gts-account /tmp/kill-on-certificate-config-sync
invoke_locked /tmp/killed-for-fail-closed.out
[ "$INVOKE_STATUS" -ne 0 ]
rm -f /tmp/nginx-up-count /tmp/nginx-up-configs /tmp/systemctl.calls
invoke_locked /tmp/recovery-failure.out FAIL_NGINX_UP_AT=1
[ "$INVOKE_STATUS" -ne 0 ]
grep -Fxq 'old-http-config' /etc/gole/nginx.conf
[ -f "$transaction" ] && [ -f "$backup" ]
grep -Fxq 'poweroff --no-block' /tmp/systemctl.calls

# The public operator entry point is the root-owned hostctl only. It derives
# project/release/domain/contact itself, owns FD 8 on the shared rollout lock,
# rejects concurrent production journals, and passes a clean environment to
# the durable issuer (including when that issuer has its own stale journal).
groupadd goledeploy
printf 'goledeploy:goledeploy\n' >/etc/gole/deploy-user
chown root:goledeploy /run/lock/gole-production-rollout.lock
install -m 0600 /source/infra/gcp/tests/fixtures/discord.env /etc/gole/discord.env
printf 'PROJECT_ID=project-72a52bf1-06aa-4519-b2c\n' >/etc/gole/cloud-broker.conf
chmod 0600 /etc/gole/cloud-broker.conf
install -d -m 0755 "$deployed_release/infra/gcp"
install -m 0644 /source/infra/gcp/docker-compose.yml \
  "$deployed_release/infra/gcp/docker-compose.yml"
printf '%s\n' "$deployed_sha" >"$deployed_release/.gole-source-sha"
chown -R root:root "$deployed_release"
chmod -R go-w "$deployed_release"
printf '%s\n' "$deployed_sha" >/etc/gole/deployed.sha
chmod 0644 /etc/gole/deployed.sha
printf 'bootstrap_source_sha=%s\n' "$sha" >/etc/gole/host-bootstrap.complete
chmod 0644 /etc/gole/host-bootstrap.complete
install -d -m 0755 /usr/local/libexec/gole /usr/local/sbin
install -m 0755 /source/infra/gcp/scripts/gole-hostctl.sh /usr/local/sbin/gole-hostctl
printf '#!/bin/sh\nexit 0\n' >/usr/local/libexec/gole/validate-production-env.py
printf '#!/bin/sh\ncat >/dev/null\n' >/usr/local/libexec/gole/validate-production-compose.py
cat >/usr/local/libexec/gole/verify-github-release.py <<'FAKE_RELEASE_VERIFIER'
#!/bin/sh
[ "$1" = --historical-main ] &&
  { [ "$2" = 0123456789abcdef0123456789abcdef01234567 ] ||
    [ "$2" = 89abcdef0123456789abcdef0123456789abcdef ]; }
FAKE_RELEASE_VERIFIER
cat >/usr/local/libexec/gole/issue-certificate.sh <<'FAKE_HOST_ISSUER'
#!/bin/sh
set -eu
[ "$#" -eq 0 ]
[ "$(id -u)" -eq 0 ]
[ "${GOLE_ROLLOUT_LOCK_HELD:-}" = 1 ]
[ "$(readlink -f /proc/$$/fd/8)" = /run/lock/gole-production-rollout.lock ]
[ "$GOLE_TRUSTED_RELEASE_ROOT" = /var/lib/gole/releases/89abcdef0123456789abcdef0123456789abcdef ]
[ "$DOMAIN" = gole.co.kr ]
[ "$EMAIL" = coldingcontact@gmail.com ]
[ "$GCP_PROJECT_ID" = project-72a52bf1-06aa-4519-b2c ]
[ -z "${SUDO_USER+x}" ]
[ -z "${MALICIOUS_PARENT_VALUE+x}" ]
printf 'hostctl-issuer-ok\n' >/tmp/hostctl-issuer.ok
FAKE_HOST_ISSUER
chmod 0755 /usr/local/libexec/gole/*.py /usr/local/libexec/gole/issue-certificate.sh

# With no deployment transaction, a root operator uses the current deployed
# immutable release rather than the older host-bootstrap release.
MALICIOUS_PARENT_VALUE=must-not-cross SUDO_USER=root \
  /usr/local/sbin/gole-hostctl certificate-issue
[ -e /tmp/hostctl-issuer.ok ]

rm -f /tmp/hostctl-issuer.ok
if SUDO_USER=goledeploy /usr/local/sbin/gole-hostctl certificate-issue \
  >/tmp/hostctl-runner-without-transaction.out 2>&1; then
  echo 'deploy user issued a certificate without the initial TLS transaction' >&2
  exit 1
fi
[ ! -e /tmp/hostctl-issuer.ok ]

cat >/etc/gole/deployment.transaction <<EOF
state=initial-http-verified
target=all
request_id=10000000-0000-4000-8000-000000000001
new_sha=$deployed_sha
previous_sha=0
EOF
chmod 0600 /etc/gole/deployment.transaction
rm -f /tmp/hostctl-issuer.ok
SUDO_USER=goledeploy /usr/local/sbin/gole-hostctl certificate-issue
[ -e /tmp/hostctl-issuer.ok ]
rm -f /etc/gole/deployment.transaction

cat >/etc/gole/deployment.transaction <<EOF
state=initial-http-verified
target=all
request_id=10000000-0000-4000-8000-000000000001
new_sha=$sha
previous_sha=0
EOF
chmod 0600 /etc/gole/deployment.transaction
rm -f /tmp/hostctl-issuer.ok
if SUDO_USER=goledeploy /usr/local/sbin/gole-hostctl certificate-issue \
  >/tmp/hostctl-mismatched-deployed-sha.out 2>&1; then
  echo 'deploy user issued a certificate for a transaction that was not deployed' >&2
  exit 1
fi
[ ! -e /tmp/hostctl-issuer.ok ]
rm -f /etc/gole/deployment.transaction

cat >/etc/gole/deployment.transaction <<EOF
state=prepared
target=all
request_id=10000000-0000-4000-8000-000000000001
new_sha=$deployed_sha
previous_sha=$deployed_sha
EOF
chmod 0600 /etc/gole/deployment.transaction
rm -f /tmp/hostctl-issuer.ok
if SUDO_USER=root /usr/local/sbin/gole-hostctl certificate-issue \
  >/tmp/hostctl-busy.out 2>&1; then
  echo 'certificate issuance accepted an active deployment transaction' >&2
  exit 1
fi
[ ! -e /tmp/hostctl-issuer.ok ]

echo 'Certificate issuance durable transaction runtime tests passed.'
CONTAINER_TEST
