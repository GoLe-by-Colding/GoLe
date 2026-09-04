#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
IMAGE="python@sha256:9d2e5553305c7c7b0097999bb17187c69b921ccd6bc9d40e4bb5ebe652c00285"

docker run --rm --interactive \
  --volume "$ROOT:/source:ro" \
  "$IMAGE" bash -seu <<'CONTAINER_TEST'
python3 - <<'PY' >/tmp/startup.sh
from pathlib import Path
import re
import textwrap

source = Path("/source/infra/gcp/terraform/main.tf").read_text(encoding="utf-8")
match = re.search(
    r"startup-script = <<-EOT\n(?P<body>.*?)\n\s+EOT\n",
    source,
    flags=re.DOTALL,
)
if match is None:
    raise SystemExit("Terraform startup script was not found")
body = textwrap.dedent(match.group("body"))
values = {
    "repository_url": "https://github.com/GoLe-by-Colding/GoLe.git",
    "bootstrap_source_sha": "a" * 40,
    "domain": "gole.co.kr",
    "deploy_user": "goledeploy",
    "project_id": "test-project-123",
    "vm_cost_start": "2026-09-01T19:57:05+09:00",
    "hard_stop_at": "2026-10-28T01:50:00+09:00",
    "credit_deadline": "2026-10-28T23:59:59+09:00",
    "runtime_rate_transition_at": "2026-09-06T00:00:00+09:00",
    "billing_account_id": "000000-000000-000000",
    "github_runner_name": "gole-gcp-production",
    "github_runner_labels": "gole-gcp-production",
}
for name, value in values.items():
    body = body.replace("${jsonencode(var." + name + ")}", repr(value))
body = body.replace("${var.bootstrap_source_sha}", values["bootstrap_source_sha"])
if "${" in body or "%{" in body:
    raise SystemExit("unresolved Terraform interpolation in startup fixture")
print(body)
PY
chmod 0700 /tmp/startup.sh

mkdir -p /fixture/infra/gcp/scripts /test-bin
cat >/fixture/infra/gcp/scripts/bootstrap-host.sh <<'FAKE_BOOTSTRAP'
#!/bin/sh
set -eu
if [ "$(cat /tmp/fail-stage)" = bootstrap ]; then
  exit 51
fi
mkdir -p /etc/gole /usr/local/sbin
printf 'bootstrap_source_sha=%s\n' aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa \
  >/etc/gole/host-bootstrap.complete
chmod 0644 /etc/gole/host-bootstrap.complete
printf '#!/bin/sh\nexit 0\n' >/usr/local/sbin/gole-hostctl
chmod 0755 /usr/local/sbin/gole-hostctl
FAKE_BOOTSTRAP
chmod 0755 /fixture/infra/gcp/scripts/bootstrap-host.sh

cat >/test-bin/apt-get <<'FAKE_APT'
#!/bin/sh
[ "$(cat /tmp/fail-stage)" != apt ]
FAKE_APT
cat >/test-bin/curl <<'FAKE_CURL'
#!/bin/sh
if [ "$(cat /tmp/fail-stage)" = metadata ]; then
  exit 52
fi
printf '%s\n' 11111111-2222-3333-4444-555555555555
FAKE_CURL
cat >/test-bin/systemctl <<'FAKE_SYSTEMCTL'
#!/bin/sh
[ "$1" = poweroff ] && [ "$2" = --no-block ] || exit 53
printf 'poweroff\n' >>/tmp/poweroff-calls
FAKE_SYSTEMCTL
chmod 0755 /test-bin/*

cat >/usr/bin/git <<'FAKE_GIT'
#!/bin/sh
set -eu
case " $* " in
  *' fetch '*)
    [ "$(cat /tmp/fail-stage)" != fetch ]
    ;;
  *' rev-parse '*)
    printf '%s\n' aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
    ;;
  *' archive '*)
    tar -cf - -C /fixture .
    ;;
  *) ;;
esac
FAKE_GIT
chmod 0755 /usr/bin/git

rm -f /usr/bin/python3
cat >/usr/bin/python3 <<'FAKE_PYTHON'
#!/bin/sh
if [ "$(cat /tmp/fail-stage)" = github-api ]; then
  exit 54
fi
cat >/dev/null
FAKE_PYTHON
chmod 0755 /usr/bin/python3

for stage in apt metadata fetch github-api bootstrap; do
  rm -rf /etc/gole /usr/local/sbin/gole-hostctl /tmp/poweroff-calls \
    /run/gole-startup-repository.* /run/gole-startup-tree.*
  printf '%s\n' "$stage" >/tmp/fail-stage
  if PATH="/test-bin:$PATH" bash /tmp/startup.sh >/tmp/output 2>&1; then
    echo "startup unexpectedly accepted injected failure: $stage" >&2
    exit 1
  fi
  [ "$(wc -l </tmp/poweroff-calls | tr -d ' ')" -eq 1 ] || {
    echo "startup did not request exactly one poweroff: $stage" >&2
    exit 1
  }
  ! find /run -maxdepth 1 \
    \( -name 'gole-startup-repository.*' -o -name 'gole-startup-tree.*' \) \
    -print -quit | grep -q .
done

# A valid durable completion marker is the only early path that may remain on.
mkdir -p /etc/gole /usr/local/sbin
printf 'bootstrap_source_sha=%s\n' bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb \
  >/etc/gole/host-bootstrap.complete
chmod 0644 /etc/gole/host-bootstrap.complete
printf '#!/bin/sh\nexit 0\n' >/usr/local/sbin/gole-hostctl
chmod 0755 /usr/local/sbin/gole-hostctl
rm -f /tmp/poweroff-calls
PATH="/test-bin:$PATH" bash /tmp/startup.sh
[ ! -e /tmp/poweroff-calls ]

echo 'Terraform startup fail-closed runtime tests passed.'
CONTAINER_TEST
