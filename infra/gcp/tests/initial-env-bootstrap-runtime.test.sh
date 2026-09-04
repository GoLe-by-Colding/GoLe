#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
IMAGE="python@sha256:9d2e5553305c7c7b0097999bb17187c69b921ccd6bc9d40e4bb5ebe652c00285"

docker run --rm --interactive \
  --volume "$ROOT:/source:ro" \
  "$IMAGE" bash -seu <<'CONTAINER_TEST'
sha='0123456789abcdef0123456789abcdef01234567'
release="/var/lib/gole/releases/$sha"
install -d -m 0755 /etc/gole /usr/local/libexec/gole /usr/local/sbin /usr/local/bin \
  "$release/infra/gcp"
printf 'root:root\n' > /etc/gole/deploy-user
printf 'MINIO_ROOT_USER=test\nMINIO_ROOT_PASSWORD=test-password\n' > /etc/gole/infra.env
printf 'PROJECT_ID=test-project-123\n' > /etc/gole/cloud-broker.conf
chmod 0600 /etc/gole/infra.env /etc/gole/cloud-broker.conf
install -m 0660 -o root -g root /dev/null /run/lock/gole-production-rollout.lock
install -m 0755 /source/infra/gcp/scripts/gole-hostctl.sh /usr/local/sbin/gole-hostctl
install -m 0755 /source/infra/gcp/scripts/validate-production-env.py \
  /usr/local/libexec/gole/validate-production-env.py
ln -s /usr/local/bin/python3 /usr/bin/python3
printf '#!/bin/sh\ncat >/dev/null\n' > /usr/local/libexec/gole/validate-production-compose.py
printf '#!/bin/sh\n[ "$1" = "%s" ]\n' "$sha" > /usr/local/libexec/gole/verify-github-release.py
chmod 0755 /usr/local/libexec/gole/*.py
touch "$release/infra/gcp/docker-compose.yml"
printf '%s\n' "$sha" > "$release/.gole-source-sha"
chown -R root:root /var/lib/gole
chmod -R go-w /var/lib/gole

cat > /usr/bin/git <<EOF
#!/bin/sh
case "\$*" in
  *'ls-remote '*'refs/heads/main'*) printf '%s\\n' '$sha' ;;
  *) exit 91 ;;
esac
EOF

cat > /usr/bin/gcloud <<'FAKE_GCLOUD'
#!/bin/sh
set -eu
[ "$1" = secrets ] && [ "$2" = versions ] && [ "$3" = access ] || exit 92
version="$4"
shift 4
output=''
for argument in "$@"; do
  case "$argument" in
    --secret=gole-production-env|--project=test-project-123|--quiet) ;;
    --out-file=*) output="${argument#--out-file=}" ;;
    *) exit 93 ;;
  esac
done
[ -n "$output" ] || exit 94
printf 'called:%s\n' "$version" >> /tmp/gcloud-calls
case "$version" in
  100|102) cp /fixtures/production.env "$output" ;;
  101) cp /fixtures/development.env "$output" ;;
  *) exit 95 ;;
esac
FAKE_GCLOUD

cat > /usr/local/bin/docker <<'FAKE_DOCKER'
#!/bin/sh
if [ "$1" = inspect ]; then
  [ -e /tmp/existing-production-container ]
  exit
fi
[ "$1" = compose ] || exit 96
printf '{}\n'
FAKE_DOCKER
cat > /usr/local/bin/systemctl <<'FAKE_SYSTEMCTL'
#!/bin/sh
if [ "$1" = poweroff ] && [ "$2" = --no-block ]; then
  printf 'poweroff\n' >> /tmp/poweroff-calls
  exit 0
fi
exit 97
FAKE_SYSTEMCTL
chmod 0755 /usr/bin/git /usr/bin/gcloud /usr/local/bin/docker /usr/local/bin/systemctl
install -d /fixtures
install -m 0600 /source/infra/gcp/tests/fixtures/production.env /fixtures/production.env
install -m 0600 /source/infra/gcp/tests/fixtures/development.env /fixtures/development.env

# Even marker-empty state is not a new host if a fixed production container
# already exists. Reject before fetching or writing a Secret candidate.
touch /tmp/existing-production-container
if printf '100\n' | /source/infra/gcp/scripts/bootstrap-production-env.sh \
  --sha "$sha" --version-stdin --dry-run >/dev/null 2>&1; then
  echo 'initial bootstrap accepted an existing production container' >&2
  exit 1
fi
rm -f /tmp/existing-production-container

dry_output="$(printf '100\n' | /source/infra/gcp/scripts/bootstrap-production-env.sh \
  --sha "$sha" --version-stdin --dry-run 2>&1)"
[ ! -e /etc/gole/gole.env ]
[ ! -e /etc/gole/gole.env.version ]
[ ! -e /etc/gole/initial-deploy.pending ]
! grep -Fq '100' <<<"$dry_output"
! grep -Fq 'abcdefghijklmnop' <<<"$dry_output"

if rejected_output="$(printf '101\n' | /source/infra/gcp/scripts/bootstrap-production-env.sh \
  --sha "$sha" --version-stdin 2>&1)"; then
  echo 'development fixture was installed during initial bootstrap' >&2
  exit 1
fi
[ ! -e /etc/gole/gole.env ]
[ ! -e /etc/gole/gole.env.version ]
! grep -Fq '101' <<<"$rejected_output"
! grep -Fq 'developer@example.test' <<<"$rejected_output"

install_output="$(printf '102\n' | /source/infra/gcp/scripts/bootstrap-production-env.sh \
  --sha "$sha" --version-stdin 2>&1)"
[ "$(stat -c '%U:%G:%a' /etc/gole/gole.env)" = 'root:root:600' ]
[ "$(stat -c '%U:%G:%a' /etc/gole/gole.env.version)" = 'root:root:644' ]
[ "$(stat -c '%U:%G:%a' /etc/gole/initial-deploy.pending)" = 'root:root:600' ]
[ "$(tr -d '\n' < /etc/gole/gole.env.version)" = 102 ]
SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-is-uninitialized
! grep -Fq '102' <<<"$install_output"
! grep -Fq 'abcdefghijklmnop' <<<"$install_output"
if find /etc/gole -maxdepth 1 -type f -name '.secret.*' | grep -q .; then
  echo 'initial bootstrap left a plaintext root candidate behind' >&2
  exit 1
fi

calls_before="$(wc -l < /tmp/gcloud-calls)"
if printf '102\n' | /source/infra/gcp/scripts/bootstrap-production-env.sh \
  --sha "$sha" --version-stdin >/dev/null 2>&1; then
  echo 'initial bootstrap replaced an existing environment' >&2
  exit 1
fi
[ "$(wc -l < /tmp/gcloud-calls)" = "$calls_before" ]

# A first deployment has no LKG image or SHA. Any post-mutation failure must
# retain its audit journal and fail closed by powering off, never bless a
# partial deployment marker.
initial_request='50000000-0000-0000-0000-000000000005'
SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-begin \
  all "$sha" 0 "$initial_request"
SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-rollback "$initial_request"
[ "$(wc -l < /tmp/poweroff-calls | tr -d ' ')" = 1 ]
[ -e /etc/gole/deployment.transaction ]
[ -e /etc/gole/initial-deploy.pending ]
[ ! -e /etc/gole/deployed.sha ]

echo 'Initial production environment root-broker runtime tests passed.'
CONTAINER_TEST
