#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$ROOT"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

terraform_startup="infra/gcp/terraform/main.tf"
bootstrap="infra/gcp/scripts/bootstrap-host.sh"
hostctl="infra/gcp/scripts/gole-hostctl.sh"

grep -q 'variable "bootstrap_source_sha"' infra/gcp/terraform/variables.tf ||
  fail "Terraform must require an immutable bootstrap source SHA"
grep -q 'BOOTSTRAP_SOURCE_SHA=.*bootstrap_source_sha' "$terraform_startup" ||
  fail "startup must pass the reviewed commit to host bootstrap"

# The initial root trust anchor must always be a fresh root-only repository.
# The runner-owned /app checkout is never an input to Git fetch/archive or the
# root bootstrap executable, even during migration of an existing VM.
for file in "$terraform_startup" "$bootstrap"; do
  grep -Eq 'mktemp -d /run/gole-(startup|bootstrap)-repository\.XXXXXX' "$file" ||
    fail "$file must create a fresh root-only bootstrap repository"
  grep -q 'GIT_CONFIG_NOSYSTEM=1' "$file" ||
    fail "$file must ignore system Git configuration"
  grep -q 'GIT_CONFIG_GLOBAL=/dev/null' "$file" ||
    fail "$file must ignore root/caller global Git configuration"
  grep -q -- '--no-replace-objects' "$file" ||
    fail "$file must disable replace refs while archiving"
done

if rg -n '(archive|tar)[^\n]*(/app|APP_ROOT)|bash\s+(/app|"?\$APP_ROOT)' "$terraform_startup" "$bootstrap"; then
  fail "root bootstrap must never archive or execute runner-owned /app"
fi
if rg -n '^(ExecStart|ExecStop|ExecReload)=/app/' infra/gcp/systemd >/dev/null; then
  fail "systemd must never execute runner-owned /app source as root"
fi

create_release_body="$(sed -n '/^create_release()/,/^}/p' "$hostctl")"
grep -q 'env -i' <<<"$create_release_body" ||
  fail "root release Git must clear the sudo caller environment"
grep -q 'HOME=/root' <<<"$create_release_body" ||
  fail "root release Git must use a fixed HOME"
grep -q 'PATH=/usr/bin:/bin' <<<"$create_release_body" ||
  fail "root release Git must use a fixed executable path"
grep -q 'GIT_CONFIG_NOSYSTEM=1' <<<"$create_release_body" ||
  fail "root release Git must ignore system Git configuration"
grep -q 'GIT_CONFIG_GLOBAL=/dev/null' <<<"$create_release_body" ||
  fail "root release Git must ignore global Git configuration"
grep -q -- '--no-replace-objects' <<<"$create_release_body" ||
  fail "root release archive must disable replace refs"
if grep -Eq '(^|[^_])git (ls-remote|init|fetch|rev-parse|archive|--git-dir)' <<<"$create_release_body"; then
  fail "every create_release Git command must cross trusted_git"
fi
if grep -q '\$APP_ROOT' <<<"$create_release_body"; then
  fail "immutable releases must not use the runner checkout"
fi

grep -q 'refs/heads/main:refs/gole/bootstrap' "$terraform_startup" ||
  fail "startup must fetch the current main ref into the root repository"
grep -q 'refs/heads/main:refs/gole/bootstrap' "$bootstrap" ||
  fail "migration bootstrap must fetch current main into the root repository"
grep -q 'head_sha.*sha' "$terraform_startup" ||
  fail "startup must independently require successful CI for the exact SHA"
grep -q 'head_sha.*sha' "$bootstrap" ||
  fail "migration bootstrap must independently require successful CI for the exact SHA"

watchdog_body="$(sed -n '/^install_cost_guard_watchdog()/,/^}/p' "$hostctl")"
if grep -q '\$APP_ROOT' <<<"$watchdog_body"; then
  fail "runner-triggerable watchdog activation must not install from /app"
fi
grep -q 'root:root:755' <<<"$watchdog_body" ||
  fail "watchdog activation must verify the installed root-owned executable"
grep -q '/usr/local/sbin/gole-register-github-runner' "$bootstrap" ||
  fail "runner registration must be installed as reviewed root-owned code"
grep -q '/usr/local/sbin/gole-bootstrap-production-env' "$bootstrap" ||
  fail "initial environment bootstrap must be installed as reviewed root-owned code"
grep -q '/etc/gole/host-bootstrap.complete' "$bootstrap" ||
  fail "bootstrap must write its completion marker last"

# The unprivileged clone cannot create /app under root-owned /. Provision its
# empty destination after preserving the old checkout and before invoking Git.
python3 - "$bootstrap" <<'PY'
import pathlib
import sys
source = pathlib.Path(sys.argv[1]).read_text()
prepare = 'install -d -m 0755 -o "$DEPLOY_USER" -g "$DEPLOY_GROUP" "$APP_ROOT"'
assert source.index('mv -- "$APP_ROOT" "$app_checkout_backup"') < source.index(prepare)
assert source.index(prepare) < source.index('git clone --no-tags "$REPOSITORY_URL" "$APP_ROOT"')
PY

# Runtime regression: hostile caller Git environment, a global insteadOf rule,
# alternates and a replace ref must not change the archive obtained by the
# exact env -i command used by bootstrap/hostctl.
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT
git init -q "$tmp/upstream-work"
git -C "$tmp/upstream-work" config user.email test@example.invalid
git -C "$tmp/upstream-work" config user.name test
printf 'reviewed\n' > "$tmp/upstream-work/payload"
git -C "$tmp/upstream-work" add payload
git -C "$tmp/upstream-work" commit -qm reviewed
reviewed_sha="$(git -C "$tmp/upstream-work" rev-parse HEAD)"
upstream_branch="$(git -C "$tmp/upstream-work" symbolic-ref --short HEAD)"
git clone -q --bare "$tmp/upstream-work" "$tmp/upstream.git"

git init -q "$tmp/attacker"
git -C "$tmp/attacker" config user.email attacker@example.invalid
git -C "$tmp/attacker" config user.name attacker
printf 'attacker\n' > "$tmp/attacker/payload"
git -C "$tmp/attacker" add payload
git -C "$tmp/attacker" commit -qm attacker
attacker_sha="$(git -C "$tmp/attacker" rev-parse HEAD)"
git -C "$tmp/attacker" replace "$reviewed_sha" "$attacker_sha" 2>/dev/null || true
cat > "$tmp/evil.gitconfig" <<EOF
[url "file://$tmp/attacker/"]
    insteadOf = file://$tmp/upstream.git
EOF

mkdir -m 0700 "$tmp/root.git" "$tmp/archive"
env \
  HOME="$tmp/attacker" \
  GIT_CONFIG_GLOBAL="$tmp/evil.gitconfig" \
  GIT_CONFIG_SYSTEM="$tmp/evil.gitconfig" \
  GIT_DIR="$tmp/attacker/.git" \
  GIT_WORK_TREE="$tmp/attacker" \
  GIT_OBJECT_DIRECTORY="$tmp/attacker/.git/objects" \
  GIT_ALTERNATE_OBJECT_DIRECTORIES="$tmp/attacker/.git/objects" \
  GIT_REPLACE_REF_BASE=refs/replace/ \
  env -i HOME=/root PATH=/usr/bin:/bin GIT_CONFIG_NOSYSTEM=1 \
    GIT_CONFIG_GLOBAL=/dev/null git init --bare "$tmp/root.git" >/dev/null
env -i HOME=/root PATH=/usr/bin:/bin GIT_CONFIG_NOSYSTEM=1 \
  GIT_CONFIG_GLOBAL=/dev/null git --git-dir="$tmp/root.git" remote add origin \
  "file://$tmp/upstream.git"
env -i HOME=/root PATH=/usr/bin:/bin GIT_CONFIG_NOSYSTEM=1 \
  GIT_CONFIG_GLOBAL=/dev/null git --git-dir="$tmp/root.git" fetch --no-tags --force \
  origin "refs/heads/$upstream_branch:refs/gole/bootstrap" >/dev/null 2>&1
env -i HOME=/root PATH=/usr/bin:/bin GIT_CONFIG_NOSYSTEM=1 \
  GIT_CONFIG_GLOBAL=/dev/null git --no-replace-objects --git-dir="$tmp/root.git" \
  archive --format=tar "$reviewed_sha" | tar -x -C "$tmp/archive"
[ "$(cat "$tmp/archive/payload")" = reviewed ] ||
  fail "hostile Git environment changed the trusted archive"

echo "Bootstrap source trust contract passed."
