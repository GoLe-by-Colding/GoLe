#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
IMAGE="python@sha256:9d2e5553305c7c7b0097999bb17187c69b921ccd6bc9d40e4bb5ebe652c00285"

docker run --rm --interactive --volume "$ROOT:/source:ro" "$IMAGE" bash -seu <<'CONTAINER_TEST'
legacy_sha=1111111111111111111111111111111111111111
expected_sha=2222222222222222222222222222222222222222
request_id=10000000-0000-4000-8000-000000000001
compact_request_id="${request_id//-/}"
release="/var/lib/gole/releases/$expected_sha"
environment_backup="/var/backups/gole-env/gole.env.20260905T000000Z.v6.$request_id"

groupadd --system --gid 10001 golecloud
groupadd goledeploy
useradd --system --create-home --home-dir /home/goledeploy \
  --shell /bin/bash --gid goledeploy goledeploy
install -d -m 0755 /etc/gole /usr/local/libexec/gole /usr/local/sbin /test-bin \
  /run/gole-cloud-broker /var/backups/gole-images "$release/infra/gcp"
install -d -m 0700 /var/backups/gole-env
chown root:golecloud /run/gole-cloud-broker
chmod 0710 /run/gole-cloud-broker
touch /run/gole-cloud-broker/policy-heartbeat
chown root:golecloud /run/gole-cloud-broker/policy-heartbeat
chmod 0600 /run/gole-cloud-broker/policy-heartbeat
python3 - <<'PY' &
import os, socket, time
path = "/run/gole-cloud-broker/broker.sock"
sock = socket.socket(socket.AF_UNIX)
sock.bind(path)
os.chmod(path, 0o660)
while True:
    time.sleep(60)
PY
socket_pid=$!
trap 'kill "$socket_pid" 2>/dev/null || true' EXIT
for _attempt in 1 2 3 4 5; do [ -S /run/gole-cloud-broker/broker.sock ] && break; sleep 1; done
chown root:golecloud /run/gole-cloud-broker/broker.sock

touch "$release/infra/gcp/docker-compose.yml"
printf '%s\n' "$expected_sha" > "$release/.gole-source-sha"
chown -R root:root /var/lib/gole
chmod -R go-w /var/lib/gole
printf 'goledeploy:goledeploy\n' > /etc/gole/deploy-user
printf 'MINIO_ROOT_USER=test\nMINIO_ROOT_PASSWORD=test-password\n' > /etc/gole/infra.env
printf 'PROJECT_ID=project-72a52bf1-06aa-4519-b2c\n' > /etc/gole/cloud-broker.conf
install -m 0600 -o root -g root /source/infra/gcp/tests/fixtures/discord.env /etc/gole/discord.env
install -m 0600 -o root -g root /source/infra/gcp/tests/fixtures/production.env /etc/gole/gole.env
chmod 0600 /etc/gole/infra.env /etc/gole/cloud-broker.conf
printf '6\n' > /etc/gole/gole.env.version
printf '%s\n' "$expected_sha" > /etc/gole/deployed.sha
chmod 0644 /etc/gole/gole.env.version /etc/gole/deployed.sha
install -m 0755 /source/infra/gcp/scripts/gole-hostctl.sh /usr/local/sbin/gole-hostctl
printf '#!/bin/sh\nexit 0\n' > /usr/local/libexec/gole/validate-production-env.py
printf '#!/bin/sh\ncat >/dev/null\n' > /usr/local/libexec/gole/validate-production-compose.py
chmod 0755 /usr/local/libexec/gole/*.py

cat > /usr/local/sbin/gole-metadata-firewall <<'FAKE_FIREWALL'
#!/bin/sh
set -eu
[ "$1" = --full ]
grep -qx 'state=metadata-ratchet-armed' /etc/gole/deployment.transaction
grep -qx 'state=ratcheting' /etc/gole/metadata-migration.pending
touch /tmp/metadata-full
if [ -e /tmp/firewall-failure ]; then exit 90; fi
FAKE_FIREWALL
chmod 0755 /usr/local/sbin/gole-metadata-firewall

cat > /test-bin/iptables <<'FAKE_IPTABLES'
#!/bin/sh
printf 'v4 %s\n' "$*" >> /tmp/iptables.calls
case " $* " in
  *' -C OUTPUT -d 169.254.169.254/32 -j GOLE_METADATA_OUTPUT '*|\
  *' -C GOLE_METADATA_OUTPUT -m owner --uid-owner 0 -j RETURN '*|\
  *' -C GOLE_METADATA_OUTPUT -j REJECT '*) exit 0 ;;
  *' -t raw -C PREROUTING -j GOLE_METADATA_INPUT '*|\
  *' -t raw -C GOLE_METADATA_INPUT -d 169.254.169.254/32 -j DROP '*)
    [ -e /tmp/metadata-full ] && exit 0 || exit 1
    ;;
esac
exit 1
FAKE_IPTABLES
cat > /test-bin/ip6tables <<'FAKE_IP6TABLES'
#!/bin/sh
printf 'v6 %s\n' "$*" >> /tmp/iptables.calls
case " $* " in
  *' -C OUTPUT -d fd20:ce::254/128 -j GOLE_METADATA_OUTPUT '*) exit 0 ;;
  *' -t raw -C PREROUTING -j GOLE_METADATA_INPUT '*|\
  *' -t raw -C GOLE_METADATA_INPUT -d fd20:ce::254/128 -j DROP '*)
    [ -e /tmp/metadata-full ] && exit 0 || exit 1
    ;;
esac
exit 1
FAKE_IP6TABLES
cat > /test-bin/systemctl <<'FAKE_SYSTEMCTL'
#!/bin/sh
case "$1 ${2:-} ${3:-}" in
  'is-active --quiet gole-metadata-firewall.service'|\
  'is-active --quiet gole-cloud-broker.service'|\
  'is-active --quiet gole-cost-guard-watchdog.timer') exit 0 ;;
  'poweroff --no-block ')
    touch /tmp/poweroff-requested
    exit 0
    ;;
esac
exit 1
FAKE_SYSTEMCTL
cat > /test-bin/curl <<'FAKE_CURL'
#!/bin/sh
case "$*" in
  *169.254.169.254*) [ ! -e /tmp/metadata-full ] && exit 0 || exit 1 ;;
  *'http://www.gole.co.kr/__gole-canonical-check?source=runtime'*)
    printf '301|https://gole.co.kr/__gole-canonical-check?source=runtime' ;;
  *'https://www.gole.co.kr/__gole-canonical-check?source=runtime'*)
    printf '301|https://gole.co.kr/__gole-canonical-check?source=runtime' ;;
  *'https://gole.co.kr/'*)
    printf 'HTTP/2 200\r\nStrict-Transport-Security: max-age=31536000\r\n\r\n' ;;
esac
exit 0
FAKE_CURL
cat > /test-bin/sleep <<'FAKE_SLEEP'
#!/bin/sh
/usr/local/bin/python3 - <<'PY'
import os
import time

path = "/run/gole-cloud-broker/policy-heartbeat"
current = os.stat(path)
next_mtime = max(current.st_mtime_ns + 1, time.time_ns())
os.utime(path, ns=(current.st_atime_ns, next_mtime))
PY
exit 0
FAKE_SLEEP
cat > /test-bin/docker <<'FAKE_DOCKER'
#!/bin/sh
set -eu
if [ "$1" = compose ]; then
  case "$*" in
    *'config --format json'*)
      printf '%s\n' '{"services":{"mongo":{"image":"gole/runtime:local"},"mongo-init":{"image":"gole/runtime:local"},"redis":{"image":"gole/runtime:local"},"minio":{"image":"gole/runtime:local"},"minio-init":{"image":"gole/runtime:local"},"support-agent":{"image":"gole/runtime:local"},"backend":{"image":"gole/runtime:local"},"frontend":{"image":"gole/runtime:local"},"nginx":{"image":"gole/runtime:local"},"budget-relay":{"image":"gole/runtime:local"}}}'
      ;;
    *' ps -a -q mongo-init'*) printf 'gole-mongo-init\n' ;;
    *' ps -a -q minio-init'*) printf 'gole-minio-init\n' ;;
    *) exit 0 ;;
  esac
  exit 0
fi
if [ "$1" = image ] && [ "$2" = inspect ]; then
  printf 'sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n'
  exit 0
fi
if [ "$1" = inspect ]; then
  for final_argument do :; done
  service="${final_argument#gole-}"
  case "$*" in
    *'.Config.Image'*gole-budget-relay)
      if [ -e /tmp/wrong-budget-image ]; then printf 'gole/budget-relay:wrong\n'
      else printf 'gole/budget-relay:local\n'; fi
      ;;
    *'.Config.Env'*gole-budget-relay)
      printf 'GOLE_CLOUD_BROKER_SOCKET=/run/gole-cloud-broker/broker.sock\n'
      ;;
    *'.Mounts'*gole-budget-relay)
      printf 'bind|/run/gole-cloud-broker|/run/gole-cloud-broker|false\n'
      ;;
    *'com.docker.compose.project'*) printf 'gole|%s\n' "$service" ;;
    *'.State.Status}}:{{.State.ExitCode'*) printf 'exited:0\n' ;;
    *'{{.Image}}'*)
      printf 'sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n'
      ;;
    *'NetworkSettings.Networks'*)
      case "$service" in
        backend) printf 'gole_edge\ngole_agent\ngole_data\n' ;;
        mongo|redis|minio) printf 'gole_data\n' ;;
        support-agent) printf 'gole_agent\n' ;;
        frontend|nginx|budget-relay) printf 'gole_edge\n' ;;
      esac
      ;;
    *'HostConfig.PortBindings'*)
      case "$service" in
        backend) printf '%s\n' '{"8080/tcp":[{"HostIp":"127.0.0.1","HostPort":"8080"}]}' ;;
        frontend) printf '%s\n' '{"3000/tcp":[{"HostIp":"127.0.0.1","HostPort":"3000"}]}' ;;
        nginx) printf '%s\n' '{"443/tcp":[{"HostIp":"","HostPort":"443"}],"80/tcp":[{"HostIp":"","HostPort":"80"}]}' ;;
        *) printf '{}\n' ;;
      esac
      ;;
    *'{{json .Mounts}}'*)
      case "$service" in
        mongo) printf '%s\n' '[{"Type":"volume","Name":"gole_mongo-data","Destination":"/data/db","RW":true}]' ;;
        redis) printf '%s\n' '[{"Type":"volume","Name":"gole_redis-data","Destination":"/data","RW":true}]' ;;
        minio) printf '%s\n' '[{"Type":"volume","Name":"gole_minio-data","Destination":"/data","RW":true}]' ;;
      esac
      ;;
    *'HostConfig.NanoCpus'*)
      case "$service" in
        mongo) printf '1000000000|1879048192\n' ;;
        mongo-init|mongo-init-1) printf '250000000|268435456\n' ;;
        redis) printf '500000000|402653184\n' ;;
        minio) printf '750000000|805306368\n' ;;
        minio-init|minio-init-1) printf '250000000|134217728\n' ;;
        support-agent) printf '250000000|201326592\n' ;;
        backend) printf '1500000000|2147483648\n' ;;
        budget-relay) printf '250000000|134217728\n' ;;
        frontend) printf '750000000|671088640\n' ;;
        nginx) printf '500000000|201326592\n' ;;
        *) exit 102 ;;
      esac
      ;;
    *'HostConfig.LogConfig.Type'*) printf 'local|10m|3\n' ;;
    *'HostConfig.SecurityOpt'*) printf '["no-new-privileges:true"]\n' ;;
    *'HostConfig.RestartPolicy.Name'*) printf 'unless-stopped\n' ;;
    *) printf 'running:healthy\n' ;;
  esac
  exit 0
fi
if [ "$1" = exec ]; then
  case "$*" in
    *gole-budget-relay*169.254.169.254*) [ ! -e /tmp/metadata-full ] ;;
    *gole-budget-relay*st_mtime_ns*)
      counter="$(cat /tmp/container-heartbeat 2>/dev/null || printf 100)"
      counter=$((counter + 1))
      printf '%s\n' "$counter" > /tmp/container-heartbeat
      printf '%s\n' "$counter"
      ;;
    *gole-nginx*) exit 0 ;;
    *) exit 0 ;;
  esac
  exit $?
fi
if [ "$1" = image ] && [ "$2" = rm ]; then
  if [ -e /tmp/kill-on-image-cleanup ]; then
    rm -f /tmp/kill-on-image-cleanup
    kill -KILL "$PPID"
  fi
  exit 0
fi
exit 91
FAKE_DOCKER
chmod 0755 /test-bin/*
install -m 0755 /test-bin/docker /usr/local/bin/docker
install -m 0755 /test-bin/curl /usr/local/bin/curl
export PATH="/test-bin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

reset_transaction() {
  rm -f /tmp/metadata-full /tmp/poweroff-requested /tmp/firewall-failure \
    /tmp/wrong-budget-image /tmp/container-heartbeat /tmp/kill-on-image-cleanup
  install -m 0600 -o root -g root \
    /source/infra/gcp/tests/fixtures/production.env /etc/gole/gole.env
  install -m 0600 -o root -g root \
    /source/infra/gcp/tests/fixtures/development.env "$environment_backup"
  printf '6\n' > /etc/gole/gole.env.version
  chmod 0644 /etc/gole/gole.env.version
  candidate_hash="$(sha256sum /etc/gole/gole.env | cut -d' ' -f1)"
  cat > /etc/gole/gole.env.transaction <<EOF
state=committed
previous_version=5
requested_version=6
request_id=$request_id
backup_file=$environment_backup
candidate_sha256=$candidate_hash
EOF
  chmod 0600 /etc/gole/gole.env.transaction
  printf 'state=pending\nlegacy_sha=%s\n' "$legacy_sha" > /etc/gole/metadata-migration.pending
  chmod 0644 /etc/gole/metadata-migration.pending
  cat > /etc/gole/deployment.transaction <<EOF
state=runtime-verified
target=all
request_id=$request_id
new_sha=$expected_sha
previous_sha=$legacy_sha
EOF
  chmod 0600 /etc/gole/deployment.transaction
  cat > "/var/backups/gole-images/images.$compact_request_id" <<EOF
target=all
request_id=$request_id
mode=legacy-adoption
image_count=9
image.mongo=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
image.mongo-init=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
image.redis=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
image.minio=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
image.minio-init=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
image.support-agent=absent
image.backend=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
image.frontend=sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
image.nginx=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
image.budget-relay=sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
EOF
  chmod 0600 "/var/backups/gole-images/images.$compact_request_id"
}

# A pre-ratchet failure leaves the old rollback-capable transaction and pending
# firewall untouched, without requesting poweroff.
reset_transaction
touch /tmp/wrong-budget-image
if SUDO_USER=goledeploy /usr/local/sbin/gole-hostctl deployment-finalize "$request_id" \
  >/tmp/pre-ratchet.out 2>&1; then
  echo 'metadata ratchet accepted an untrusted budget relay image' >&2
  exit 1
fi
grep -qx 'state=runtime-verified' /etc/gole/deployment.transaction
grep -qx 'state=pending' /etc/gole/metadata-migration.pending
[ ! -e /tmp/metadata-full ]
[ ! -e /tmp/poweroff-requested ]
grep -qx 'state=committed' /etc/gole/gole.env.transaction

# Simulate power loss after the ratcheting marker fsync but before the
# deployment journal advances. Rollback must be rejected, and recovery must
# continue forward from runtime-verified instead.
reset_transaction
sed -i 's/state=pending/state=ratcheting/' /etc/gole/metadata-migration.pending
touch /tmp/metadata-full
if SUDO_USER=goledeploy /usr/local/sbin/gole-hostctl deployment-rollback "$request_id" \
  >/tmp/crash-window-rollback.out 2>&1; then
  echo 'rollback reopened the marker/journal crash window' >&2
  exit 1
fi
grep -qx 'state=runtime-verified' /etc/gole/deployment.transaction
grep -qx 'state=ratcheting' /etc/gole/metadata-migration.pending
[ -e /tmp/poweroff-requested ]
rm -f /tmp/poweroff-requested
recovery="$(SUDO_USER=goledeploy /usr/local/sbin/gole-hostctl deployment-recover)"
[ "$recovery" = RECOVERED ]
[ ! -e /etc/gole/metadata-migration.pending ]
[ ! -e /etc/gole/deployment.transaction ]
[ ! -e /etc/gole/gole.env.transaction ]
[ "$(cat /etc/gole/gole.env.version)" = 6 ]
[ ! -e /tmp/poweroff-requested ]

# A failure after the durable ratchet marker never reopens metadata or invokes
# the legacy rollback path; it leaves the forward-recovery journal and powers off.
reset_transaction
touch /tmp/firewall-failure
if SUDO_USER=goledeploy /usr/local/sbin/gole-hostctl deployment-finalize "$request_id" \
  >/tmp/post-ratchet.out 2>&1; then
  echo 'metadata ratchet ignored a firewall failure' >&2
  exit 1
fi
grep -qx 'state=metadata-ratchet-armed' /etc/gole/deployment.transaction
grep -qx 'state=ratcheting' /etc/gole/metadata-migration.pending
[ -e /tmp/metadata-full ]
[ -e /tmp/poweroff-requested ]
grep -qx 'state=committed' /etc/gole/gole.env.transaction

# Reboot recovery completes forward from the armed state. It removes both the
# pending marker and transaction only after full rules and both heartbeats pass.
rm -f /tmp/firewall-failure /tmp/poweroff-requested
if ! recovery="$(SUDO_USER=goledeploy /usr/local/sbin/gole-hostctl deployment-recover \
  2>/tmp/recovery.err)"; then
  cat /tmp/recovery.err >&2
  cat /tmp/iptables.calls >&2
  exit 1
fi
[ "$recovery" = RECOVERED ]
[ ! -e /etc/gole/metadata-migration.pending ]
[ ! -e /etc/gole/deployment.transaction ]
[ ! -e /etc/gole/gole.env.transaction ]
[ ! -e "/var/backups/gole-images/images.$compact_request_id" ]
[ ! -e /tmp/poweroff-requested ]

# A crash after the verified journal write but before marker removal also
# remains runner-gated until recovery removes the marker and journal in order.
reset_transaction
sed -i 's/state=runtime-verified/state=metadata-ratchet-verified/' \
  /etc/gole/deployment.transaction
sed -i 's/state=pending/state=ratcheting/' /etc/gole/metadata-migration.pending
touch /tmp/metadata-full
recovery="$(SUDO_USER=goledeploy /usr/local/sbin/gole-hostctl deployment-recover)"
[ "$recovery" = RECOVERED ]
[ ! -e /etc/gole/metadata-migration.pending ]
[ ! -e /etc/gole/deployment.transaction ]
[ ! -e /etc/gole/gole.env.transaction ]
[ ! -e "/var/backups/gole-images/images.$compact_request_id" ]
[ ! -e /tmp/poweroff-requested ]

# A SIGKILL after cleanup-pending is fsynced must never turn a committed new
# LKG into a rollback. Recovery re-proves the full runtime and only resumes the
# request-scoped image/manifest cleanup.
reset_transaction
touch /tmp/kill-on-image-cleanup
if SUDO_USER=goledeploy /usr/local/sbin/gole-hostctl deployment-finalize "$request_id" \
  >/tmp/cleanup-kill.out 2>&1; then
  echo 'deployment finalize survived the injected cleanup SIGKILL' >&2
  exit 1
fi
grep -qx 'state=cleanup-pending' /etc/gole/deployment.transaction
[ ! -e /etc/gole/metadata-migration.pending ]
[ ! -e /etc/gole/gole.env.transaction ]
[ -e "/var/backups/gole-images/images.$compact_request_id" ]
recovery="$(SUDO_USER=goledeploy /usr/local/sbin/gole-hostctl deployment-recover)"
[ "$recovery" = RECOVERED ]
[ ! -e /etc/gole/deployment.transaction ]
[ ! -e "/var/backups/gole-images/images.$compact_request_id" ]
[ ! -e /tmp/poweroff-requested ]

echo 'Metadata isolation ratchet transaction and recovery tests passed.'
CONTAINER_TEST
