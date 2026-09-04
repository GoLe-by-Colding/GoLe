#!/usr/bin/env bash
set -Eeuo pipefail

# Initial package is pinned and verified. GitHub's service may self-update it after
# registration so security fixes are not blocked by the image bootstrap cadence.
RUNNER_VERSION="2.337.0"
RUNNER_SHA256_X64="70920811a4f8ad4328818682bca5c6469c1c942fab52448868071d0063816613"
RUNNER_ROOT="/opt/actions-runner"
RUNNER_SERVICE="gole-github-runner.service"
RUNNER_SERVICE_FILE="/etc/systemd/system/$RUNNER_SERVICE"
DEPLOY_IDENTITY_FILE="/etc/gole/deploy-user"
RUNNER_BOOTSTRAP_FILE="/etc/gole/github-runner-bootstrap.conf"
RUNNER_REGISTRATION_FILE="/etc/gole/github-runner-registration.conf"

die() {
  echo "$*" >&2
  exit 1
}

usage() {
  cat >&2 <<'EOF'
Usage: sudo register-github-runner.sh --token-stdin

Reads one short-lived GitHub Actions runner registration token from stdin.
The token is never written to disk and must not be passed as a command argument.
EOF
  exit 2
}

if [ "$(id -u)" -ne 0 ]; then
  die "run as root"
fi
if [ "${1:-}" != "--token-stdin" ] || [ "$#" -ne 1 ]; then
  usage
fi
if [ "$(uname -m)" != "x86_64" ]; then
  die "this deployment runner must be Linux X64"
fi
if [ ! -r "$DEPLOY_IDENTITY_FILE" ] || [ ! -r "$RUNNER_BOOTSTRAP_FILE" ]; then
  die "run bootstrap-host.sh before registering the runner"
fi

IFS=: read -r DEPLOY_USER DEPLOY_GROUP < "$DEPLOY_IDENTITY_FILE"
REPOSITORY_URL=""
RUNNER_NAME=""
RUNNER_LABELS=""
while IFS='=' read -r key value; do
  case "$key" in
    repository_url) REPOSITORY_URL="$value" ;;
    runner_name) RUNNER_NAME="$value" ;;
    runner_labels) RUNNER_LABELS="$value" ;;
    *) die "invalid runner bootstrap configuration" ;;
  esac
done < "$RUNNER_BOOTSTRAP_FILE"

[[ "$DEPLOY_USER" =~ ^[a-z_][a-z0-9_-]{0,31}$ ]] || die "invalid deploy user"
[[ "$DEPLOY_GROUP" =~ ^[a-z_][a-z0-9_-]{0,31}$ ]] || die "invalid deploy group"
[[ "$REPOSITORY_URL" =~ ^https://github\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+\.git$ ]] ||
  die "invalid repository URL"
[[ "$RUNNER_NAME" =~ ^[A-Za-z0-9._-]{1,64}$ ]] || die "invalid runner name"
[[ "$RUNNER_LABELS" =~ ^[A-Za-z0-9._-]+(,[A-Za-z0-9._-]+)*$ ]] || die "invalid runner labels"
case ",$RUNNER_LABELS," in
  *,gole-gcp-production,*) ;;
  *) die "runner labels must include gole-gcp-production" ;;
esac
id "$DEPLOY_USER" >/dev/null 2>&1 || die "deploy user does not exist"
[ "$(id -gn "$DEPLOY_USER")" = "$DEPLOY_GROUP" ] || die "deploy group does not match"
if [ "$DEPLOY_USER" != "goledeploy" ] ||
  [ "$(id -nG "$DEPLOY_USER" | tr ' ' '\n' | sort -u | tr '\n' ' ')" != "$DEPLOY_GROUP " ]; then
  die "runner account must be the isolated goledeploy user with no supplemental groups"
fi

ensure_runner_service() {
  local unit_candidate
  if [ ! -f "$RUNNER_ROOT/runsvc.sh" ] || [ -L "$RUNNER_ROOT/runsvc.sh" ] ||
    [ ! -x "$RUNNER_ROOT/runsvc.sh" ]; then
    die "runner service command is missing or invalid"
  fi
  unit_candidate="$(mktemp)"
  cat > "$unit_candidate" <<EOF
[Unit]
Description=GoLe repository-scoped GitHub Actions runner
After=network-online.target
Wants=network-online.target

[Service]
User=$DEPLOY_USER
Group=$DEPLOY_GROUP
WorkingDirectory=$RUNNER_ROOT
ExecStart=$RUNNER_ROOT/runsvc.sh
KillMode=process
KillSignal=SIGINT
TimeoutStopSec=5min
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF
  install -m 0644 -o root -g root "$unit_candidate" "$RUNNER_SERVICE_FILE"
  rm -f -- "$unit_candidate"
  printf '%s\n' "$RUNNER_SERVICE" > "$RUNNER_ROOT/.service"
  chown "$DEPLOY_USER:$DEPLOY_GROUP" "$RUNNER_ROOT/.service"
  chmod 0644 "$RUNNER_ROOT/.service"
  systemctl daemon-reload
  systemctl enable --now "$RUNNER_SERVICE"
  systemctl is-active --quiet "$RUNNER_SERVICE"
}

retire_nonstandard_runner_services() {
  local existing_user exec_start legacy_root resolved_root runner_service runner_service_file
  for runner_service_file in /etc/systemd/system/actions.runner.*.service; do
    [ -e "$runner_service_file" ] || continue
    runner_service="$(basename "$runner_service_file")"
    if [ -L "$runner_service_file" ] || [ ! -f "$runner_service_file" ]; then
      die "legacy runner service file is invalid"
    fi
    existing_user="$(systemctl show -p User --value "$runner_service" 2>/dev/null || true)"
    if [ -z "$existing_user" ] || [ "$existing_user" = root ]; then
      die "legacy runner service identity is unsafe"
    fi
    exec_start="$(sed -n 's/^ExecStart=//p' "$runner_service_file")"
    [[ "$exec_start" =~ ^(/home/[A-Za-z0-9._-]+/[A-Za-z0-9._/-]+|/opt/[A-Za-z0-9._/-]+)/runsvc\.sh$ ]] ||
      die "legacy runner service command is outside an allowed runner directory"
    legacy_root="${exec_start%/runsvc.sh}"
    resolved_root="$(readlink -f -- "$legacy_root")"
    if [ "$resolved_root" != "$legacy_root" ] || [ -L "$legacy_root" ]; then
      die "legacy runner service ownership cannot be verified"
    fi
    systemctl disable --now "$runner_service"
    rm -f -- "$runner_service_file"
    [ ! -e "$runner_service_file" ] || die "legacy runner service was not retired"
  done
  systemctl daemon-reload
}

if [ -f "$RUNNER_ROOT/.runner" ]; then
  if [ ! -f "$RUNNER_REGISTRATION_FILE" ]; then
    die "runner is configured but its non-secret registration marker is missing"
  fi
  if ! grep -Fqx "repository_url=$REPOSITORY_URL" "$RUNNER_REGISTRATION_FILE" ||
    ! grep -Fqx "runner_name=$RUNNER_NAME" "$RUNNER_REGISTRATION_FILE" ||
    ! grep -Fqx "runner_labels=$RUNNER_LABELS" "$RUNNER_REGISTRATION_FILE"; then
    die "configured runner does not match the requested repository, name, or labels"
  fi
  retire_nonstandard_runner_services
  chown -R "$DEPLOY_USER:$DEPLOY_GROUP" "$RUNNER_ROOT"
  chmod 0750 "$RUNNER_ROOT"
  ensure_runner_service >/dev/null
  echo "GitHub Actions runner is already configured; ensured its service is running."
  exit 0
fi

RUNNER_TOKEN=""
if ! IFS= read -r RUNNER_TOKEN && [ -z "$RUNNER_TOKEN" ]; then
  die "no registration token was provided on stdin"
fi
if [[ ! "$RUNNER_TOKEN" =~ ^[A-Za-z0-9._-]{20,512}$ ]]; then
  die "registration token format is invalid"
fi
if IFS= read -r _extra_token_input; then
  die "stdin must contain exactly one registration token"
fi
trap 'RUNNER_TOKEN=""; if [ -n "${download_dir:-}" ]; then rm -rf -- "$download_dir"; fi' EXIT

download_dir="$(mktemp -d)"
archive="$download_dir/actions-runner-linux-x64.tar.gz"
curl --fail --location --proto '=https' --retry 3 --show-error --silent \
  "https://github.com/actions/runner/releases/download/v${RUNNER_VERSION}/actions-runner-linux-x64-${RUNNER_VERSION}.tar.gz" \
  --output "$archive"
printf '%s  %s\n' "$RUNNER_SHA256_X64" "$archive" | sha256sum --check --status ||
  die "GitHub Actions runner checksum mismatch"

if [ -e "$RUNNER_ROOT" ] || [ -L "$RUNNER_ROOT" ]; then
  die "unconfigured runner root already exists; inspect it before retrying"
fi
install -d -m 0750 -o root -g root "$RUNNER_ROOT"
tar --extract --gzip --file "$archive" --directory "$RUNNER_ROOT" --no-same-owner
"$RUNNER_ROOT/bin/installdependencies.sh"
chown -R "$DEPLOY_USER:$DEPLOY_GROUP" "$RUNNER_ROOT"
chmod 0750 "$RUNNER_ROOT"

# An older human-account runner must be stopped and its systemd unit removed
# before the same repository/name is replaced. Its directory and credentials
# are left untouched for forensic recovery; --replace invalidates the old
# registration at GitHub.
retire_nonstandard_runner_services

(
  # Runner.Listener treats ACTIONS_RUNNER_INPUT_TOKEN as a secret, masks it and
  # removes it from its environment immediately after parsing. Unlike --token,
  # the value is never exposed in the process command line. runuser resets the
  # root environment and whitelists only this one masked input.
  export ACTIONS_RUNNER_INPUT_TOKEN="$RUNNER_TOKEN"
  runuser -u "$DEPLOY_USER" --whitelist-environment=ACTIONS_RUNNER_INPUT_TOKEN -- \
    "$RUNNER_ROOT/config.sh" \
    --unattended \
    --replace \
    --url "${REPOSITORY_URL%.git}" \
    --name "$RUNNER_NAME" \
    --labels "$RUNNER_LABELS" \
    --work _work
)
RUNNER_TOKEN=""

cat > "$RUNNER_REGISTRATION_FILE" <<EOF
repository_url=$REPOSITORY_URL
runner_name=$RUNNER_NAME
runner_labels=$RUNNER_LABELS
initial_runner_version=$RUNNER_VERSION
EOF
chown root:root "$RUNNER_REGISTRATION_FILE"
chmod 0644 "$RUNNER_REGISTRATION_FILE"

ensure_runner_service
echo "GitHub Actions runner registered without persisting the short-lived registration token."
