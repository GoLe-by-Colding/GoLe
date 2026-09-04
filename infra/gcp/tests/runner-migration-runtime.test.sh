#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
IMAGE="ubuntu@sha256:33ceb71981b602c1a7443a53469e4dba065f7503eab3078a2d7a57a2ab987517"

docker run --rm --interactive \
  --volume "$ROOT:/source:ro" \
  "$IMAGE" bash -seu <<'CONTAINER_TEST'
groupadd goledeploy
useradd --system --create-home --home-dir /home/goledeploy \
  --shell /bin/bash --gid goledeploy goledeploy
install -d -m 0755 /etc/gole /etc/systemd/system /test-bin
install -d -m 0750 -o goledeploy -g goledeploy /opt/actions-runner
install -d -m 0755 /home/kscold/actions-runner
printf 'goledeploy:goledeploy\n' > /etc/gole/deploy-user
cat > /etc/gole/github-runner-bootstrap.conf <<'EOF'
repository_url=https://github.com/GoLe-by-Colding/GoLe.git
runner_name=gole-production
runner_labels=gole-gcp-production
EOF
cat > /etc/gole/github-runner-registration.conf <<'EOF'
repository_url=https://github.com/GoLe-by-Colding/GoLe.git
runner_name=gole-production
runner_labels=gole-gcp-production
initial_runner_version=2.337.0
EOF

standard_service='gole-github-runner.service'
legacy_service='actions.runner.GoLe.legacy-production.service'
touch /opt/actions-runner/.runner
printf '%s\n' "$legacy_service" > /home/kscold/actions-runner/.service
cat > "/etc/systemd/system/$legacy_service" <<EOF
[Service]
User=kscold
ExecStart=/home/kscold/actions-runner/runsvc.sh
EOF

cat > /test-bin/systemctl <<'EOF'
#!/bin/sh
set -eu
if [ "$1" = show ]; then
  service="${5:-${4:-}}"
  sed -n 's/^User=//p' "/etc/systemd/system/$service"
  exit 0
fi
exit 0
EOF
cat > /test-bin/uname <<'EOF'
#!/bin/sh
[ "${1:-}" = -m ] && { echo x86_64; exit 0; }
exec /usr/bin/uname "$@"
EOF

touch /opt/actions-runner/runsvc.sh /home/kscold/actions-runner/runsvc.sh
chmod 0755 /opt/actions-runner/runsvc.sh /home/kscold/actions-runner/runsvc.sh \
  /test-bin/systemctl
chmod 0755 /test-bin/uname
export PATH="/test-bin:$PATH"

bash /source/infra/gcp/scripts/register-github-runner.sh --token-stdin </dev/null

[ ! -e "/etc/systemd/system/$legacy_service" ]
[ -d /home/kscold/actions-runner ]
[ "$(cat /opt/actions-runner/.service)" = "$standard_service" ]
grep -qx 'User=goledeploy' "/etc/systemd/system/$standard_service"
[ "$(find /etc/systemd/system -maxdepth 1 -name 'actions.runner.*.service' | wc -l)" -eq 0 ]
grep -qx 'ExecStart=/opt/actions-runner/runsvc.sh' "/etc/systemd/system/$standard_service"
[ "$(stat -c '%U:%G:%a' "/etc/systemd/system/$standard_service")" = 'root:root:644' ]

echo 'Dedicated runner service migration runtime test passed.'
CONTAINER_TEST
