"""지인 서버 진입점 gateway/server/run.sh(D28) — 가짜 docker·flock 으로 굽기·토큰 회수 순서를 고정한다."""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

import pytest

RUN_SH = Path(__file__).resolve().parents[2] / "gateway" / "server" / "run.sh"

# 호출을 기록하고, $FAKE 폴더의 파일로 상황을 흉내 낸다.
FAKE_DOCKER = r"""#!/bin/sh
F="$FAKE"
echo "$*" >> "$F/calls"
case "$1" in
  pull) exit "$(cat "$F/pull_rc" 2>/dev/null || echo 0)" ;;
  image)
    case "$2" in
      inspect)
        if [ "$3" = "-f" ]; then [ -f "$F/base_id" ] || exit 1; cat "$F/base_id"; exit 0; fi
        [ -f "$F/local" ]; exit $? ;;
      prune) exit 0 ;;
    esac ;;
  build) [ -f "$F/build_fail" ] && exit 1; cp auth.json "$F/baked"; touch "$F/local"; exit 0 ;;
  create) echo cid1 ;;
  start) cat >/dev/null; printf '{"ok":true}'; exit "$(cat "$F/start_rc" 2>/dev/null || echo 0)" ;;
  wait) echo 0 ;;
  cp) [ -f "$F/token" ] || exit 1; cp "$F/token" "$3" ;;
  run) grep -q '^{' ;;
  rm) exit 0 ;;
esac
"""
FAKE_FLOCK = '#!/bin/sh\nexit "$(cat "$FAKE/flock_rc" 2>/dev/null || echo 0)"\n'


@pytest.fixture
def server(tmp_path):
    sh = shutil.which("sh")
    if sh is None:
        pytest.skip("sh 가 없다")
    fake, bin_dir, home = tmp_path / "fake", tmp_path / "bin", tmp_path / "opt"
    for d in (fake, bin_dir, home):
        d.mkdir()
    for name, body in (("docker", FAKE_DOCKER), ("flock", FAKE_FLOCK)):
        (bin_dir / name).write_text(body, encoding="utf-8", newline="\n")
        (bin_dir / name).chmod(0o755)
    # 평상시: 이미 구운 :local 이 지금 stable 과 맞고, 토큰은 그대로다.
    (home / "auth.json").write_text('{"t":1}')
    (home / "base.digest").write_text("sha1\n")
    (fake / "base_id").write_text("sha1")
    (fake / "local").touch()
    (fake / "token").write_text('{"t":1}')

    def run():
        env = {**os.environ, "FAKE": str(fake), "GOLE_GATEWAY_DIR": str(home),
               "PATH": f"{bin_dir}{os.pathsep}{os.environ['PATH']}"}
        completed = subprocess.run([sh, str(RUN_SH)], input='{"engine":"codex"}', env=env,
                                   capture_output=True, text=True, encoding="utf-8", timeout=60)
        calls = (fake / "calls").read_text().splitlines() if (fake / "calls").exists() else []
        return completed.stdout, [c.split()[0] if c.split()[0] != "image" else " ".join(c.split()[:2]) for c in calls]

    return home, fake, run


def test_sameToken_answersWithoutRebaking(server):
    home, _, run = server
    out, calls = run()
    assert out == '{"ok":true}'
    assert "build" not in calls
    assert (home / "auth.json").read_text() == '{"t":1}'


def test_refreshedToken_replacesKeyAndRebakesOnce(server):
    home, fake, run = server
    (fake / "token").write_text('{"t":2}')
    _, calls = run()
    assert calls.count("build") == 1 and calls.index("build") > calls.index("cp")
    assert (home / "auth.json").read_text() == '{"t":2}'
    assert (fake / "baked").read_text() == '{"t":2}'
    assert not (home / "auth.new").exists()
    if sys.platform != "win32":
        assert (home / "auth.json").stat().st_mode & 0o777 == 0o600


def test_unreadableToken_keepsCurrentKey(server):
    home, fake, run = server
    (fake / "token").write_text("not json")
    _, calls = run()
    assert "build" not in calls
    assert (home / "auth.json").read_text() == '{"t":1}'


def test_newStable_isBakedBeforeTheRequest(server):
    home, fake, run = server
    (fake / "base_id").write_text("sha2")
    _, calls = run()
    assert calls.index("build") < calls.index("create")
    assert (home / "base.digest").read_text().strip() == "sha2"


def test_droppedConnection_stillWaitsAndRecoversToken(server):
    home, fake, run = server
    (fake / "start_rc").write_text("1")
    (fake / "token").write_text('{"t":2}')
    _, calls = run()
    assert calls.index("start") < calls.index("wait") < calls.index("cp")
    assert (home / "auth.json").read_text() == '{"t":2}'


def test_noImageAtAll_isAFixedCode(server):
    _, fake, run = server
    (fake / "pull_rc").write_text("1")
    (fake / "base_id").unlink()
    (fake / "local").unlink()
    out, calls = run()
    assert json.loads(out)["error"] == "GATEWAY_IMAGE_UNAVAILABLE"
    assert "create" not in calls


def test_failedBuild_fallsBackToTheImageAlreadyBaked(server):
    _, fake, run = server
    (fake / "base_id").write_text("sha2")
    (fake / "build_fail").touch()
    out, calls = run()
    assert out == '{"ok":true}' and "create" in calls


@pytest.mark.parametrize("setup, code", [
    (lambda home, fake: (fake / "flock_rc").write_text("1"), "GATEWAY_BUSY"),
    (lambda home, fake: (home / "auth.json").unlink(), "GATEWAY_MISCONFIGURED"),
])
def test_refusals_answerInTheGatewayShape(server, setup, code):
    home, fake, run = server
    setup(home, fake)
    out, calls = run()
    response = json.loads(out)
    assert response["ok"] is False and response["error"] == code and response["images"] == []
    assert "create" not in calls
