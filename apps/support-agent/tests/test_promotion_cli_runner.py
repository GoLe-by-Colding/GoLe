"""Claude Code 래퍼. 모델이 무엇을 내든 검증을 통과한 것만 제출되고, 자격증명은 넘어가지 않아야 한다."""

import json
import subprocess
from pathlib import Path

import pytest

from gole_promotion_agent import cli_runner
from gole_promotion_agent.dryrun import _PIXEL


class FakePublisher:
    def __init__(self, pending=0, exists=False):
        self.pending = pending
        self._exists = exists
        self.created = []
        self.finalized = []
        self.uploaded = []

    def pending_count(self):
        return self.pending

    def exists(self, sha):
        return self._exists

    def history(self, limit):
        return ({"status": "PUBLISHED", "caption": "지난 글"},)

    def upload(self, paths):
        self.uploaded.append([Path(p).name for p in paths])
        return tuple(f"key-{i}" for i, _ in enumerate(paths))

    def create(self, sha, caption, media_keys, details):
        self.created.append((sha, caption, list(media_keys), details))
        return "post-1"

    def finalize(self, post_id):
        self.finalized.append(post_id)


def _fake_claude(structured, *, files=True, subtype="success"):
    """claude 대신 돈다 — 캡처·카드 파일을 남기고 구조화 출력을 돌려준다."""
    calls = []

    def runner(command, *, cwd, env, input, **kwargs):
        calls.append({"command": command, "env": env, "input": input, "cwd": cwd})
        out = Path(cwd) / "out"
        if files:
            (out / "captures").mkdir(parents=True, exist_ok=True)
            (out / "captures" / "k1.png").write_bytes(_PIXEL)
            (out / "captures" / "k1.json").write_text(
                json.dumps(
                    {
                        "label": "목록",
                        "file": "k1.png",
                        "route": "/listings",
                        "interactions": [{"kind": "scroll", "to": "bottom"}],
                        "captured_at": "2026-10-01T00:00:00+00:00",
                    }
                ),
                encoding="utf-8",
            )
            (out / "cards").mkdir(parents=True, exist_ok=True)
            (out / "cards" / "card-01.png").write_bytes(_PIXEL)
            (out / "cards" / "card-01.json").write_text(
                json.dumps({"file": "card-01.png", "capture": "목록", "headline": "골라 보기"}),
                encoding="utf-8",
            )
        payload = {"type": "result", "subtype": subtype, "is_error": subtype != "success",
                   "session_id": "s-1", "structured_output": structured}
        return subprocess.CompletedProcess(command, 0, json.dumps(payload), "")

    return runner, calls


def _drafter(tmp_path, runner, publisher, **overrides):
    options = dict(
        repo=tmp_path / "repo",
        run_dir=tmp_path / "run",
        publisher=publisher,
        capture_site="http://localhost:3000",
        capture_session={"sessionToken": "demo-token"},
        demo=True,
        service=True,
        runner=runner,
        base_env={"PATH": "/usr/bin", "HOME": "/home/agent",
                  "PROMOTION_AGENT_ADMIN_PASSWORD": "prod-secret", "GITHUB_TOKEN": "gh"},
    )
    options.update(overrides)
    return cli_runner.ClaudeCodeDrafter(**options)


DRAFT = {
    "decision": "draft",
    "caption": "원하는 부품만 골라 보세요",
    "rationale": "필터가 잘 보이는 화면",
    "images": [{"card": "card-01.png"}, {"capture": "목록"}],
}


def test_draft_isValidatedThenSubmitted(tmp_path):
    runner, calls = _fake_claude(DRAFT)
    publisher = FakePublisher()

    result = _drafter(tmp_path, runner, publisher).run()

    assert result.outcome == "submitted"
    assert result.session_id == "s-1"
    assert publisher.uploaded == [["card-01.png", "k1.png"]]
    sha, caption, keys, details = publisher.created[0]
    assert sha is None  # 서비스 홍보는 출처 릴리스가 없다
    assert caption == DRAFT["caption"]
    assert [c["label"] for c in details["captures"]] == ["목록 · 카드", "목록"]
    assert details["captures"][0]["route"] == "/listings"
    assert details["captures"][0]["actions"] == "맨 아래로 스크롤"
    assert publisher.finalized == ["post-1"]


def test_claudeEnv_dropsProductionCredentialsAndSessionFileIsRemoved(tmp_path):
    runner, calls = _fake_claude(DRAFT)

    _drafter(tmp_path, runner, FakePublisher()).run()

    env = calls[0]["env"]
    assert "PROMOTION_AGENT_ADMIN_PASSWORD" not in env
    assert "GITHUB_TOKEN" not in env
    assert env["PROMOTION_AGENT_DATA_SOURCE"] == "DEMO"
    assert not Path(env["PROMOTION_AGENT_CAPTURE_SESSION_FILE"]).exists()
    command = calls[0]["command"]
    assert "--permission-mode" in command and "dontAsk" in command
    assert "Read(./out/**)" in command
    assert "지난 글" in (tmp_path / "run" / "private" / "system.md").read_text(encoding="utf-8")


def test_skip_submitsNothing(tmp_path):
    runner, _ = _fake_claude({"decision": "skip", "skip_reason": "보여줄 화면 없음"}, files=False)
    publisher = FakePublisher()

    result = _drafter(tmp_path, runner, publisher).run()

    assert (result.outcome, result.reason) == ("skipped", "보여줄 화면 없음")
    assert publisher.created == []


@pytest.mark.parametrize(
    "structured",
    [
        {"decision": "draft", "caption": "글"},
        {**DRAFT, "images": [{"card": "card-09.png"}]},
        {**DRAFT, "images": [{"capture": "찍지않음"}]},
    ],
)
def test_contractViolations_fail(tmp_path, structured):
    runner, _ = _fake_claude(structured)
    publisher = FakePublisher()

    result = _drafter(tmp_path, runner, publisher).run()

    assert result.outcome == "failed"
    assert publisher.created == []


def test_claudeError_fails(tmp_path):
    runner, _ = _fake_claude(None, subtype="error_max_turns")

    assert _drafter(tmp_path, runner, FakePublisher()).run().outcome == "failed"


def test_timeout_fails(tmp_path):
    def runner(command, **kwargs):
        raise subprocess.TimeoutExpired(command, 1)

    result = _drafter(tmp_path, runner, FakePublisher(), timeout=1).run()

    assert result.outcome == "failed"
    assert "시간 초과" in result.reason


def test_fullReviewQueue_skipsWithoutCallingClaude(tmp_path):
    runner, calls = _fake_claude(DRAFT)

    result = _drafter(tmp_path, runner, FakePublisher(pending=5)).run()

    assert result.outcome == "skipped"
    assert calls == []


def test_featureWithoutWebChanges_skipsWithoutCallingClaude(tmp_path):
    repo = tmp_path / "repo"
    repo.mkdir()
    git = lambda *args: subprocess.run(  # noqa: E731
        ["git", *args], cwd=repo, capture_output=True, text=True, check=True
    ).stdout.strip()
    git("init", "-q")
    git("config", "user.email", "t@t")
    git("config", "user.name", "t")
    (repo / "docs").mkdir()
    (repo / "docs" / "note.md").write_text("x", encoding="utf-8")
    git("add", ".")
    git("commit", "-qm", "docs: note")
    runner, calls = _fake_claude(DRAFT)

    result = _drafter(tmp_path, runner, FakePublisher(), service=False, sha=git("rev-parse", "HEAD")).run()

    assert result.outcome == "skipped"
    assert calls == []
