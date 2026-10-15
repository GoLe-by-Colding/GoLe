"""지인 서버 진입점 gateway/server/run.sh(D28·D29) — 가짜 docker·flock·setsid·curl 로 접수·처리·토큰 회수 순서를 고정한다."""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import time
from pathlib import Path

import pytest

RUN_SH = Path(__file__).resolve().parents[2] / "gateway" / "server" / "run.sh"
ORDER = '{"run_key":"gh-1-1"}'

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
  start) cat > "$F/stdin"; echo "deliver 출력" ;;
  wait) cat "$F/rc" 2>/dev/null || echo 0 ;;
  cp) [ -f "$F/token" ] || exit 1; cp "$F/token" "$3" ;;
  run) grep -q '^{' ;;
  rm) exit 0 ;;
esac
"""
FAKE_FLOCK = '#!/bin/sh\nexit "$(cat "$FAKE/flock_rc" 2>/dev/null || echo 0)"\n'
FAKE_SETSID = '#!/bin/sh\necho "$*" >> "$FAKE/setsid"\n'
# 마지막 인자가 웹훅 주소, --data 다음이 본문이다.
FAKE_CURL = r"""#!/bin/sh
while [ "$#" -gt 0 ]; do
  if [ "$1" = "--data" ]; then printf '%s\n' "$2" >> "$FAKE/notes"; shift; fi
  shift
done
"""


@pytest.fixture
def server(tmp_path):
    sh = shutil.which("sh")
    if sh is None:
        pytest.skip("sh 가 없다")
    fake, bin_dir, home = tmp_path / "fake", tmp_path / "bin", tmp_path / "opt"
    for d in (fake, bin_dir, home):
        d.mkdir()
    for name, body in (("docker", FAKE_DOCKER), ("flock", FAKE_FLOCK), ("setsid", FAKE_SETSID), ("curl", FAKE_CURL)):
        (bin_dir / name).write_text(body, encoding="utf-8", newline="\n")
        (bin_dir / name).chmod(0o755)
    # 평상시: 이미 구운 :local 이 지금 stable 과 맞고, 토큰은 그대로다.
    (home / "auth.json").write_text('{"t":1}')
    (home / "promotion.env").write_text("PROMOTION_AGENT_TOKEN=x\nDISCORD_OPERATIONS_WEBHOOK_URL=https://discord.test/hook\n")
    (home / "base.digest").write_text("sha1\n")
    (fake / "base_id").write_text("sha1")
    (fake / "local").touch()
    (fake / "token").write_text('{"t":1}')

    def run(*args, ssh=None, stdin=""):
        env = {**os.environ, "FAKE": str(fake), "GOLE_GATEWAY_DIR": str(home),
               "PATH": f"{bin_dir}{os.pathsep}{os.environ['PATH']}"}
        env.pop("SSH_ORIGINAL_COMMAND", None)
        if ssh is not None:
            env["SSH_ORIGINAL_COMMAND"] = ssh
        completed = subprocess.run([sh, str(RUN_SH), *args], input=stdin, env=env,
                                   capture_output=True, text=True, encoding="utf-8", timeout=60)
        calls = (fake / "calls").read_text().splitlines() if (fake / "calls").exists() else []
        return completed, [c.split()[0] if c.split()[0] != "image" else " ".join(c.split()[:2]) for c in calls]

    def notes():
        path = fake / "notes"
        return [json.loads(line)["content"] for line in path.read_text().splitlines()] if path.exists() else []

    return home, fake, run, notes


def _queued(home, key="gh-1-1"):
    job = home / "jobs" / key
    job.mkdir(parents=True)
    (job / "order.json").write_text(ORDER)
    (job / "status").write_text("queued\n")
    return job


# ---------------------------------------------------------------- submit (SSH)


def test_submit_storesOrderAndStartsWorkerInTheBackground(server):
    home, fake, run, _ = server
    completed, calls = run(ssh="submit gh-1-1", stdin=ORDER)

    assert json.loads(completed.stdout) == {
        "ok": True, "error": None, "job": "gh-1-1", "text": "", "structured": None, "images": [], "usage": None}
    assert (home / "jobs" / "gh-1-1" / "order.json").read_text() == ORDER
    assert (home / "jobs" / "gh-1-1" / "status").read_text().strip() == "queued"
    started = fake / "setsid"
    for _ in range(50):  # 일꾼은 백그라운드(&)로 뜬다
        if started.exists():
            break
        time.sleep(0.1)
    assert started.read_text().split()[-2:] == ["work", "gh-1-1"]
    assert calls == []  # 접수는 모델·이미지를 건드리지 않는다


def test_submit_sameRunKeyAgain_isAcceptedWithoutASecondWorker(server):
    home, fake, run, _ = server
    _queued(home)
    completed, _ = run(ssh="submit gh-1-1", stdin=ORDER)

    assert json.loads(completed.stdout)["ok"] is True
    assert not (fake / "setsid").exists()


@pytest.mark.parametrize("status", ["failed", None])
def test_submit_sameRunKeyThatWillNotRunAgain_isADuplicate(server, status):
    # 실패했거나(상태 failed) 상태 파일이 없는 주문을 "접수"라고 하면 아무도 처리하지 않는 주문이 된다.
    home, fake, run, _ = server
    job = _queued(home)
    if status is None:
        (job / "status").unlink()
    else:
        (job / "status").write_text(status + "\n")
    completed, _ = run(ssh="submit gh-1-1", stdin=ORDER)

    assert json.loads(completed.stdout)["error"] == "GATEWAY_DUPLICATE"
    assert not (fake / "setsid").exists()


def test_submit_interruptedReceiving_isNeitherAcceptedNorBusy(server):
    # 주문서를 받는 도중 끊기면 임시 폴더만 남는다 — 같은 키 재접수와 다른 주문을 막지 않는다.
    home, fake, run, _ = server
    leftover = home / "jobs" / ".incoming-gh-1-1"
    leftover.mkdir(parents=True)
    (leftover / "order.json").write_text('{"cut')
    completed, _ = run(ssh="submit gh-1-1", stdin=ORDER)

    assert json.loads(completed.stdout)["ok"] is True
    assert (home / "jobs" / "gh-1-1" / "order.json").read_text() == ORDER
    assert not leftover.exists()


def test_submit_lockTimeout_isBusy(server):
    home, fake, run, _ = server
    (fake / "flock_rc").write_text("1")
    completed, _ = run(ssh="submit gh-1-1", stdin=ORDER)

    assert json.loads(completed.stdout)["error"] == "GATEWAY_BUSY"
    assert not (home / "jobs" / "gh-1-1").exists()


def test_submit_whileAnotherOrderIsInFlight_isBusy(server):
    home, fake, run, _ = server
    _queued(home, "gh-0-1")
    completed, _ = run(ssh="submit gh-1-1", stdin=ORDER)

    assert json.loads(completed.stdout)["error"] == "GATEWAY_BUSY"
    assert not (home / "jobs" / "gh-1-1").exists()


def test_submit_dailyLimit_countsOnlyTheLast24Hours(server):
    # 구독 계정 보호: 트리거가 무엇이든 24시간에 3건까지만 받는다.
    home, _, run, _ = server
    now = int(time.time())
    (home / "orders.log").write_text(f"{now - 90000} gh-0-1\n{now - 3600} gh-0-2\n{now - 60} gh-0-3\n")
    completed, _ = run(ssh="submit gh-1-1", stdin=ORDER)
    assert json.loads(completed.stdout)["ok"] is True  # 25시간 전 주문은 세지 않는다 — 2건
    assert (home / "orders.log").read_text().splitlines()[-1].endswith(" gh-1-1")

    (home / "jobs" / "gh-1-1" / "status").write_text("failed\n")  # 실패한 주문도 센다
    completed, _ = run(ssh="submit gh-1-2", stdin=ORDER)
    assert json.loads(completed.stdout)["error"] == "GATEWAY_DAILY_LIMIT"
    assert not (home / "jobs" / "gh-1-2").exists()


@pytest.mark.skipif(sys.platform == "win32", reason="파일 시각을 되돌리는 os.utime 이 MSYS find -mmin 과 어긋날 수 있다")
def test_submit_afterAStaleOrder_marksItFailedNotifiesAndAccepts(server):
    home, fake, run, notes = server
    stale = _queued(home, "gh-0-1")
    old = stale / "status"
    os.utime(old, (old.stat().st_atime - 3 * 3600, old.stat().st_mtime - 3 * 3600))
    completed, _ = run(ssh="submit gh-1-1", stdin=ORDER)

    assert json.loads(completed.stdout)["ok"] is True
    assert old.read_text().strip() == "failed"
    assert any("멈춤" in note and "gh-0-1" in note for note in notes())


@pytest.mark.parametrize("command", [None, "", "work gh-1-1", "submit", "submit ../x", "submit GH-1", "rm -rf /"])
def test_onlySubmitWithAValidRunKey_isAcceptedOverSsh(server, command):
    home, fake, run, _ = server
    completed, calls = run(ssh=command, stdin=ORDER)

    assert json.loads(completed.stdout)["error"] == "GATEWAY_BAD_REQUEST"
    assert not (home / "jobs").exists() or not any((home / "jobs").iterdir())
    assert calls == [] and not (fake / "setsid").exists()


def test_missingServerFiles_isMisconfigured(server):
    home, _, run, _ = server
    (home / "promotion.env").unlink()
    completed, _ = run(ssh="submit gh-1-1", stdin=ORDER)

    assert json.loads(completed.stdout)["error"] == "GATEWAY_MISCONFIGURED"


# ---------------------------------------------------------------- work (background)


def test_work_feedsOrderToDeliverAndClearsTheJob(server):
    home, fake, run, notes = server
    _queued(home)
    _, calls = run("work", "gh-1-1")

    assert (fake / "stdin").read_text() == ORDER
    assert calls.index("create") < calls.index("start") < calls.index("wait") < calls.index("cp")
    assert "gole_promotion_deliver" in (fake / "calls").read_text()
    assert not (home / "jobs" / "gh-1-1").exists()
    assert "build" not in calls and notes() == []
    assert "deliver 출력" in (home / "run.log").read_text()  # 컨테이너 출력은 서버 로그로만 간다


def test_work_refreshedToken_replacesKeyAndRebakesOnce(server):
    home, fake, run, _ = server
    _queued(home)
    (fake / "token").write_text('{"t":2}')
    _, calls = run("work", "gh-1-1")

    assert calls.count("build") == 1 and calls.index("build") > calls.index("cp")
    assert (home / "auth.json").read_text() == '{"t":2}'
    assert (fake / "baked").read_text() == '{"t":2}'
    assert not (home / "auth.new").exists()
    if sys.platform != "win32":
        assert (home / "auth.json").stat().st_mode & 0o777 == 0o600


def test_work_unreadableToken_keepsCurrentKey(server):
    home, fake, run, _ = server
    _queued(home)
    (fake / "token").write_text("not json")
    _, calls = run("work", "gh-1-1")

    assert "build" not in calls
    assert (home / "auth.json").read_text() == '{"t":1}'


def test_work_newStable_isBakedBeforeTheOrder(server):
    home, fake, run, _ = server
    _queued(home)
    (fake / "base_id").write_text("sha2")
    _, calls = run("work", "gh-1-1")

    assert calls.index("build") < calls.index("create")
    assert (home / "base.digest").read_text().strip() == "sha2"


def test_work_failedBuild_fallsBackToTheImageAlreadyBaked(server):
    home, fake, run, _ = server
    _queued(home)
    (fake / "base_id").write_text("sha2")
    (fake / "build_fail").touch()
    _, calls = run("work", "gh-1-1")

    assert "create" in calls and not (home / "jobs" / "gh-1-1").exists()


def test_work_noImageAtAll_failsTheOrderAndNotifies(server):
    home, fake, run, notes = server
    job = _queued(home)
    (fake / "pull_rc").write_text("1")
    (fake / "base_id").unlink()
    (fake / "local").unlink()
    _, calls = run("work", "gh-1-1")

    assert "create" not in calls
    assert (job / "status").read_text().strip() == "failed"
    assert any("GATEWAY_IMAGE_UNAVAILABLE" in note for note in notes())


@pytest.mark.parametrize("rc, extra_note", [("3", False), ("2", False), ("137", True)])
def test_work_failedDelivery_keepsTheOrderAndNotifiesOnlyForCrashes(server, rc, extra_note):
    home, fake, run, notes = server
    job = _queued(home)
    (fake / "rc").write_text(rc)
    run("work", "gh-1-1")

    assert (job / "status").read_text().strip() == "failed"
    assert (job / "order.json").exists()
    assert bool([n for n in notes() if "비정상 종료" in n and rc in n]) is extra_note


def test_work_lockTimeout_failsTheOrder(server):
    home, fake, run, notes = server
    job = _queued(home)
    (fake / "flock_rc").write_text("1")
    _, calls = run("work", "gh-1-1")

    assert "create" not in calls
    assert (job / "status").read_text().strip() == "failed"
    assert any("GATEWAY_BUSY" in note for note in notes())
