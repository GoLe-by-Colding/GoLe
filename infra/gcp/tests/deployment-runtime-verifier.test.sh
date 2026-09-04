#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
IMAGE="python@sha256:9d2e5553305c7c7b0097999bb17187c69b921ccd6bc9d40e4bb5ebe652c00285"

docker run --rm --interactive \
  --volume "$ROOT:/source:ro" \
  "$IMAGE" bash -seu <<'CONTAINER_TEST'
expected_sha='0123456789abcdef0123456789abcdef01234567'
release="/var/lib/gole/releases/$expected_sha"
groupadd --system --gid 10001 golecloud
install -d -m 0755 /etc/gole /usr/local/libexec/gole /usr/local/sbin /test-bin \
  "$release/infra/gcp"
install -d -m 0710 -o root -g golecloud /run/gole-cloud-broker
install -m 0600 -o root -g golecloud /dev/null /run/gole-cloud-broker/policy-heartbeat
python3 - <<'PY' &
import os
import socket
import time

sock = socket.socket(socket.AF_UNIX)
sock.bind('/run/gole-cloud-broker/broker.sock')
os.chmod('/run/gole-cloud-broker/broker.sock', 0o660)
time.sleep(60)
PY
socket_pid=$!
trap 'kill "$socket_pid" 2>/dev/null || true' EXIT
for _attempt in 1 2 3 4 5; do
  [ -S /run/gole-cloud-broker/broker.sock ] && break
  sleep 1
done
chown root:golecloud /run/gole-cloud-broker/broker.sock
touch "$release/infra/gcp/docker-compose.yml"
printf '%s\n' "$expected_sha" > "$release/.gole-source-sha"
chown -R root:root /var/lib/gole
chmod -R go-w /var/lib/gole
printf 'root:root\n' > /etc/gole/deploy-user
printf 'MINIO_ROOT_USER=test\nMINIO_ROOT_PASSWORD=test-password\n' > /etc/gole/infra.env
install -m 0600 -o root -g root /source/infra/gcp/tests/fixtures/discord.env \
  /etc/gole/discord.env
install -m 0600 /source/infra/gcp/tests/fixtures/production.env /etc/gole/gole.env
chmod 0600 /etc/gole/infra.env /etc/gole/gole.env
printf '6\n' > /etc/gole/gole.env.version
chmod 0644 /etc/gole/gole.env.version
printf '%s\n' "$expected_sha" > /etc/gole/deployed.sha
chmod 0644 /etc/gole/deployed.sha
install -m 0755 /source/infra/gcp/scripts/gole-hostctl.sh /usr/local/sbin/gole-hostctl
printf '#!/bin/sh\nexit 0\n' > /usr/local/libexec/gole/validate-production-env.py
printf '#!/bin/sh\ncat >/dev/null\n' > /usr/local/libexec/gole/validate-production-compose.py
chmod 0755 /usr/local/libexec/gole/*.py

cat > /test-bin/docker <<'EOF'
#!/bin/sh
set -eu
printf '%s\n' "$*" >> /tmp/root-docker-calls
case "$1" in
  compose)
    case "$*" in
      *' config --format json')
        printf '%s\n' '{"services":{"mongo":{"image":"gole/runtime:local"},"mongo-init":{"image":"gole/runtime:local"},"redis":{"image":"gole/runtime:local"},"minio":{"image":"gole/runtime:local"},"minio-init":{"image":"gole/runtime:local"},"support-agent":{"image":"gole/runtime:local"},"backend":{"image":"gole/runtime:local"},"frontend":{"image":"gole/runtime:local"},"nginx":{"image":"gole/runtime:local"},"budget-relay":{"image":"gole/runtime:local"}}}'
        ;;
      *' config --services')
        printf '%s\n' mongo mongo-init redis minio minio-init support-agent backend frontend nginx budget-relay
        ;;
      *' exec -T mongo mongosh --quiet --norc --eval '*)
        if [ -f /tmp/fake-seller-gap-count ]; then cat /tmp/fake-seller-gap-count
        else printf '0\n'; fi
        ;;
      *' ps -a -q mongo-init') printf 'gole-mongo-init\n' ;;
      *' ps -a -q minio-init') printf 'gole-minio-init\n' ;;
      *) exit 0 ;;
    esac
    ;;
  image)
    case "$2" in
      inspect) printf 'sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n' ;;
      tag|rm) exit 0 ;;
      *) exit 1 ;;
    esac
    ;;
  inspect)
    for final_argument do :; done
    service="${final_argument#gole-}"
    case "$*" in
      *'.Config.Image'*gole-budget-relay) printf 'gole/budget-relay:local\n' ;;
      *'.Config.Env'*gole-budget-relay)
        printf 'GOLE_CLOUD_BROKER_SOCKET=/run/gole-cloud-broker/broker.sock\n' ;;
      *'.Mounts'*gole-budget-relay)
        printf 'bind|/run/gole-cloud-broker|/run/gole-cloud-broker|false\n' ;;
      *'com.docker.compose.project'*) printf 'gole|%s\n' "$service" ;;
      *'.State.ExitCode'*) printf 'exited:0\n' ;;
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
          mongo)
            if [ "${FAKE_VOLUME_DRIFT:-0}" = 1 ]; then
              printf '%s\n' '[{"Type":"volume","Name":"gole_mongo-data-v2","Destination":"/data/db","RW":true}]'
            else
              printf '%s\n' '[{"Type":"volume","Name":"gole_mongo-data","Destination":"/data/db","RW":true}]'
            fi
            ;;
          redis) printf '%s\n' '[{"Type":"volume","Name":"gole_redis-data","Destination":"/data","RW":true}]' ;;
          minio) printf '%s\n' '[{"Type":"volume","Name":"gole_minio-data","Destination":"/data","RW":true}]' ;;
        esac
        ;;
      *'HostConfig.NanoCpus'*)
        if [ "${FAKE_RESOURCE_DRIFT:-0}" = 1 ] && [ "$service" = backend ]; then
          printf '0|0\n'
        else
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
            *) exit 92 ;;
          esac
        fi
        ;;
      *'HostConfig.LogConfig.Type'*) printf 'local|10m|3\n' ;;
      *'HostConfig.SecurityOpt'*) printf '["no-new-privileges:true"]\n' ;;
      *'HostConfig.RestartPolicy.Name'*) printf 'unless-stopped\n' ;;
      *)
        if [ "${FAKE_UNHEALTHY:-0}" = 1 ]; then printf 'running:unhealthy\n';
        else printf 'running:healthy\n'; fi ;;
    esac
    ;;
  exec)
    case "$*" in
      *gole-budget-relay*169.254.169.254*) exit 1 ;;
      *gole-budget-relay*st_mtime_ns*)
        counter="$(cat /tmp/container-heartbeat 2>/dev/null || printf 100)"
        counter=$((counter + 1))
        printf '%s\n' "$counter" > /tmp/container-heartbeat
        printf '%s\n' "$counter"
        ;;
      *gole-nginx*) exit 0 ;;
      *) exit 0 ;;
    esac
    ;;
  *) exit 91 ;;
esac
EOF
cat > /test-bin/curl <<'EOF'
#!/bin/sh
case "$*" in
  *169.254.169.254*)
    if [ -e /tmp/allow-metadata-once ]; then
      rm -f /tmp/allow-metadata-once
      exit 0
    fi
    exit 1
    ;;
  *'http://www.gole.co.kr/__gole-canonical-check?source=runtime'*)
    printf '301|https://gole.co.kr/__gole-canonical-check?source=runtime'
    ;;
  *'http://www.gole.co.kr/__gole-canonical-check?source=initial'*)
    printf '301|https://gole.co.kr/__gole-canonical-check?source=initial'
    ;;
  *'https://www.gole.co.kr/__gole-canonical-check?source=runtime'*)
    printf '301|https://gole.co.kr/__gole-canonical-check?source=runtime'
    ;;
  *'https://gole.co.kr/'*)
    printf 'HTTP/2 200\r\nStrict-Transport-Security: max-age=31536000\r\n\r\n'
    ;;
esac
exit 0
EOF
cat > /test-bin/sleep <<'EOF'
#!/bin/sh
/usr/local/bin/python3 - <<'PY'
import os
import time

path = '/run/gole-cloud-broker/policy-heartbeat'
current = os.stat(path)
next_mtime = max(current.st_mtime_ns + 1, time.time_ns())
os.utime(path, ns=(current.st_atime_ns, next_mtime))
PY
EOF
cat > /test-bin/systemctl <<'EOF'
#!/bin/sh
if [ "$1" = is-active ] && [ "$2" = --quiet ]; then
  case "$3" in
    gole-cost-guard-watchdog.timer|gole-metadata-firewall.service|gole-cloud-broker.service) exit 0 ;;
  esac
fi
exit 1
EOF
cat > /test-bin/iptables <<'EOF'
#!/bin/sh
case " $* " in *' -C '*) exit 0 ;; esac
exit 1
EOF
cat > /test-bin/ip6tables <<'EOF'
#!/bin/sh
case " $* " in *' -C '*) exit 0 ;; esac
exit 1
EOF
chmod 0755 /test-bin/docker /test-bin/curl /test-bin/systemctl \
  /test-bin/iptables /test-bin/ip6tables /test-bin/sleep
install -m 0755 /test-bin/docker /usr/local/bin/docker
export PATH="/test-bin:$PATH"

output="$(SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-verify-runtime "$expected_sha")"
[ -z "$output" ]
[ "$(grep -c '^inspect --format' /tmp/root-docker-calls)" -ge 8 ]
for container in gole-backend gole-frontend gole-budget-relay gole-support-agent gole-nginx; do
  grep -Fq "$container" /tmp/root-docker-calls
done
if SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-verify-runtime \
  1111111111111111111111111111111111111111 >/dev/null 2>&1; then
  echo 'runtime verifier accepted the wrong LKG SHA' >&2
  exit 1
fi
if FAKE_UNHEALTHY=1 SUDO_USER=root /usr/local/sbin/gole-hostctl \
  deployment-verify-runtime "$expected_sha" >/dev/null 2>&1; then
  echo 'runtime verifier accepted an unhealthy container' >&2
  exit 1
fi
if FAKE_VOLUME_DRIFT=1 SUDO_USER=root /usr/local/sbin/gole-hostctl \
  deployment-verify-runtime "$expected_sha" >/tmp/volume-drift.out 2>&1; then
  echo 'runtime verifier accepted a redirected Mongo volume' >&2
  exit 1
fi
grep -q 'strict runtime persistent volume changed: mongo' /tmp/volume-drift.out
if FAKE_RESOURCE_DRIFT=1 SUDO_USER=root /usr/local/sbin/gole-hostctl \
  deployment-verify-runtime "$expected_sha" >/tmp/resource-drift.out 2>&1; then
  echo 'runtime verifier accepted missing backend resource limits' >&2
  exit 1
fi
grep -q 'strict runtime resource limits changed: backend' /tmp/resource-drift.out

request_id='10000000-0000-4000-8000-000000000001'
cat > /etc/gole/deployment.transaction <<EOF
state=budget-updated
target=all
request_id=$request_id
new_sha=$expected_sha
previous_sha=$expected_sha
EOF
chmod 0600 /etc/gole/deployment.transaction
SUDO_USER=root /usr/local/sbin/gole-hostctl \
  deployment-verify-candidate-runtime "$expected_sha" "$request_id"
grep -Fq 'exec -T mongo mongosh --quiet --norc --eval' /tmp/root-docker-calls

sed -i 's/^state=verified$/state=budget-updated/' /etc/gole/deployment.transaction
printf '1\n' > /tmp/fake-seller-gap-count
if SUDO_USER=root /usr/local/sbin/gole-hostctl \
  deployment-verify-candidate-runtime "$expected_sha" "$request_id" >/tmp/preflight.out 2>&1; then
  echo 'candidate verifier accepted an active listing with incomplete seller identity' >&2
  exit 1
fi
grep -q 'active listings with incomplete verified seller identity remain: 1' /tmp/preflight.out
! grep -Eq 'sellerId|phoneNumber|phoneVerifiedAt|[0-9a-f]{24}' /tmp/preflight.out

# Simulate SIGKILL after the committed Nginx journal was removed but before
# deployment-finalize. The exact new LKG/full runtime is re-proved and recovery
# advances cleanup-pending instead of recreating the previous application.
rm -f /tmp/fake-seller-gap-count
cat > /etc/gole/deployment.transaction <<EOF
state=runtime-verified
target=all
request_id=$request_id
new_sha=$expected_sha
previous_sha=1111111111111111111111111111111111111111
EOF
chmod 0600 /etc/gole/deployment.transaction
compact_request_id="${request_id//-/}"
install -d -m 0700 /var/backups/gole-images
cat > "/var/backups/gole-images/images.$compact_request_id" <<EOF
target=all
request_id=$request_id
mode=strict
image_count=10
image.mongo=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
image.mongo-init=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
image.redis=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
image.minio=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
image.minio-init=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
image.support-agent=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
image.backend=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
image.frontend=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
image.nginx=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
image.budget-relay=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
EOF
chmod 0600 "/var/backups/gole-images/images.$compact_request_id"
recovery="$(SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-recover)"
[ "$recovery" = RECOVERED ]
[ ! -e /etc/gole/deployment.transaction ]
[ ! -e "/var/backups/gole-images/images.$compact_request_id" ]

# If the commit-window proof fails, recovery must use the existing rollback
# path rather than blessing cleanup-pending. A one-shot metadata reachability
# failure is consumed before rollback verifies the exact previous runtime.
cat > /etc/gole/deployment.transaction <<EOF
state=runtime-verified
target=all
request_id=$request_id
new_sha=$expected_sha
previous_sha=$expected_sha
EOF
chmod 0600 /etc/gole/deployment.transaction
cat > "/var/backups/gole-images/images.$compact_request_id" <<EOF
target=all
request_id=$request_id
mode=strict
image_count=10
image.mongo=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
image.mongo-init=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
image.redis=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
image.minio=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
image.minio-init=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
image.support-agent=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
image.backend=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
image.frontend=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
image.nginx=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
image.budget-relay=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
EOF
chmod 0600 "/var/backups/gole-images/images.$compact_request_id"
touch /tmp/allow-metadata-once
recovery="$(SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-recover)"
[ "$recovery" = RECOVERED ]
[ ! -e /etc/gole/deployment.transaction ]
grep -Fq ' up -d --no-build --no-deps --force-recreate --wait backend' \
  /tmp/root-docker-calls

write_initial_snapshot() {
  cat > "/var/backups/gole-images/images.$compact_request_id" <<EOF
target=all
request_id=$request_id
mode=initial
image_count=0
image.mongo=absent
image.mongo-init=absent
image.redis=absent
image.minio=absent
image.minio-init=absent
image.support-agent=absent
image.backend=absent
image.frontend=absent
image.nginx=absent
image.budget-relay=absent
EOF
  chmod 0600 "/var/backups/gole-images/images.$compact_request_id"
}

write_committed_nginx_transaction() {
  local config_hash
  printf 'committed-initial-config\n' > /etc/gole/nginx.conf
  chmod 0644 /etc/gole/nginx.conf
  install -d -m 0700 /var/backups/gole-nginx
  printf 'previous-initial-config\n' > "/var/backups/gole-nginx/nginx.conf.$request_id"
  chmod 0600 "/var/backups/gole-nginx/nginx.conf.$request_id"
  config_hash="$(sha256sum /etc/gole/nginx.conf | cut -d' ' -f1)"
  cat > /etc/gole/nginx.conf.transaction <<EOF
state=committed
request_id=$request_id
backup_file=/var/backups/gole-nginx/nginx.conf.$request_id
candidate_sha256=$config_hash
deploy_sha=$expected_sha
EOF
  chmod 0600 /etc/gole/nginx.conf.transaction
}

# On the first deployment, deployed.sha is intentionally durable before the
# final runtime proof. A crash in marker-recorded must re-prove the exact new
# runtime and finish forward; there is no previous LKG to roll back to.
cat > /etc/gole/deployment.transaction <<EOF
state=marker-recorded
target=all
request_id=$request_id
new_sha=$expected_sha
previous_sha=0
EOF
chmod 0600 /etc/gole/deployment.transaction
write_initial_snapshot
write_committed_nginx_transaction
recovery="$(SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-recover)"
[ "$recovery" = INITIAL_TLS_REQUIRED ]
grep -qx 'state=initial-http-verified' /etc/gole/deployment.transaction
[ ! -e /etc/gole/nginx.conf.transaction ]
[ -e "/var/backups/gole-images/images.$compact_request_id" ]
SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-complete-initial-tls
[ ! -e /etc/gole/deployment.transaction ]
[ ! -e "/var/backups/gole-images/images.$compact_request_id" ]

# The adjacent crash window, after runtime-verified but before Nginx journal
# finalization, uses the same forward-only proof and cleanup path.
cat > /etc/gole/deployment.transaction <<EOF
state=runtime-verified
target=all
request_id=$request_id
new_sha=$expected_sha
previous_sha=0
EOF
chmod 0600 /etc/gole/deployment.transaction
write_initial_snapshot
write_committed_nginx_transaction
recovery="$(SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-recover)"
[ "$recovery" = RECOVERED ]
[ ! -e /etc/gole/deployment.transaction ]
[ ! -e /etc/gole/nginx.conf.transaction ]
[ ! -e "/var/backups/gole-images/images.$compact_request_id" ]

echo 'Deployment runtime root-helper contract passed.'
CONTAINER_TEST
