"""SSH 게이트웨이. 서버에 홀로 복사되는 파일이라 표준 라이브러리만 쓰고, 요청 하나가 서버에 남기는 것이 없어야 한다."""

import ast
import io
import base64
import json
import subprocess
import sys
from pathlib import Path

import pytest

GATEWAY = Path(__file__).resolve().parents[2] / "gateway"
sys.path.insert(0, str(GATEWAY))

import gole_llm_gateway as gateway  # noqa: E402
from gole_promotion_agent.fakes import PIXEL  # noqa: E402

PNG = PIXEL
PNG_B64 = base64.b64encode(PNG).decode()


@pytest.fixture(autouse=True)
def _isolated_server_state(tmp_path, monkeypatch):
    """테스트가 실제 서버 로그(~/.cache)를 더럽히거나 실제 codex 이미지 폴더를 지우지 않게 한다."""
    monkeypatch.setattr(gateway, "LOG_PATH", tmp_path / "gw.log")
    monkeypatch.setenv("CODEX_HOME", str(tmp_path / "codex-home"))


def _recording(stdout="", returncode=0, on_call=None):
    calls = []

    def runner(command, **kwargs):
        calls.append({"command": command, **kwargs})
        if on_call:
            on_call(command, kwargs)
        return subprocess.CompletedProcess(command, returncode, stdout, "")

    return runner, calls


def test_gateway_uses_only_stdlib():
    tree = ast.parse((GATEWAY / "gole_llm_gateway.py").read_text(encoding="utf-8"))
    modules = {n.module for n in ast.walk(tree) if isinstance(n, ast.ImportFrom)} | {
        a.name for n in ast.walk(tree) if isinstance(n, ast.Import) for a in n.names
    }
    # 서버에 단독으로 복사되는 파일이다 — 표준 라이브러리 밖의 import 가 생기면 서버에서 깨진다.
    assert all(name == "__future__" or name.split(".")[0] in sys.stdlib_module_names for name in modules)


def test_claude_returnsStructuredOutputAndOnlyReadsInbox(tmp_path):
    payload = {"type": "result", "subtype": "success", "is_error": False,
               "result": "{}", "structured_output": {"decision": "skip"}}
    runner, calls = _recording(json.dumps(payload))

    response = gateway.handle(
        {"engine": "claude", "prompt": "골라", "json_schema": {"type": "object"},
         "images": [{"data": PNG_B64}]},
        runner=runner, root=tmp_path,
    )

    assert response["ok"] and response["structured"] == {"decision": "skip"}
    command = calls[0]["command"]
    assert "Read(./in/**)" in command and "dontAsk" in command
    assert "in/img-01.png" in calls[0]["input"]
    assert list(tmp_path.iterdir()) == []  # 임시 폴더를 남기지 않는다


def test_codex_returnsGeneratedImages(tmp_path):
    def write_output(command, kwargs):
        workdir = Path(kwargs["cwd"])
        (workdir / "out" / "polished.png").write_bytes(PNG)
        Path(command[command.index("-o") + 1]).write_text("done", encoding="utf-8")

    runner, calls = _recording(on_call=write_output)

    response = gateway.handle(
        {"engine": "codex", "prompt": "다듬어", "want_images": True, "images": [{"data": PNG_B64}]},
        runner=runner, root=tmp_path,
    )

    assert response["ok"]
    assert response["images"] == [{"name": "polished.png", "data": PNG_B64}]
    # 이미지 요청에는 codex 의 글(여기선 "done")을 돌려주지 않는다 — 홈 폴더를 읽은 내용이 새는 통로다.
    assert response["text"] == "" and response["structured"] is None
    command = calls[0]["command"]
    # 프롬프트는 stdin 으로 간다 — 인자에 두면 Windows shim 이 깨뜨려 -i 첨부까지 사라졌다.
    assert "다듬어" in calls[0]["input"] and not any("다듬어" in a for a in command)
    assert command[command.index("-i") + 1].endswith("img-01.png")
    assert command[command.index("-s") + 1] == "workspace-write"


def test_codex_sandboxWithoutShell_takesImageFromGeneratedFolderAndCleansIt(tmp_path, monkeypatch):
    # 비특권 user namespace 를 막은 서버에서는 codex 가 셸을 못 띄워 out/ 로 복사하지 못한다(실서버 재현).
    session = "01a10d13-16a3-7fa3-beca-331c77a53838"
    monkeypatch.setenv("CODEX_HOME", str(tmp_path / "codex"))
    generated = tmp_path / "codex" / "generated_images" / session
    other = tmp_path / "codex" / "generated_images" / "11111111-2222-3333-4444-555555555555"

    def runner(command, **kwargs):
        generated.mkdir(parents=True)
        other.mkdir(parents=True)
        (generated / "exec-1.png").write_bytes(PNG)
        (other / "not-mine.png").write_bytes(PNG)
        return subprocess.CompletedProcess(command, 0, "", f"workdir: x\nsession id: {session}\n")

    response = gateway.handle(
        {"engine": "codex", "prompt": "다듬어", "want_images": True, "images": [{"data": PNG_B64}]},
        runner=runner, root=tmp_path,
    )

    assert response["ok"]
    assert response["images"] == [{"name": "exec-1.png", "data": PNG_B64}]
    # 이번 세션 폴더만 지운다 — 같은 계정의 다른 codex 작업 결과는 건드리지 않는다.
    assert not generated.exists() and other.exists()


CODEX_SESSION = "01a11034-bf52-7101-b491-936357ec887f"
# `codex exec --json` 실측(codex-cli 0.160.1) 모양. 세션 ID 는 stderr 가 아니라 thread.started 에 나온다.
CODEX_EVENTS = "\n".join(
    json.dumps(event)
    for event in (
        {"type": "thread.started", "thread_id": CODEX_SESSION},
        {"type": "turn.started"},
        {"type": "item.completed", "item": {"id": "item_0", "type": "agent_message", "text": "done"}},
        {
            "type": "turn.completed",
            "usage": {"input_tokens": 67549, "cached_input_tokens": 54656, "output_tokens": 228},
        },
    )
)


def test_codexJson_takesImageFromThreadFolderAndReportsUsage(tmp_path, monkeypatch):
    codex_home = tmp_path / "codex"
    codex_home.mkdir()
    (codex_home / "config.toml").write_text('model = "gpt-6.1-sol"\n[profiles.x]\nmodel = "other"\n', encoding="utf-8")
    monkeypatch.setenv("CODEX_HOME", str(codex_home))
    generated = codex_home / "generated_images" / CODEX_SESSION

    def runner(command, **kwargs):
        generated.mkdir(parents=True)
        (generated / "exec-1.png").write_bytes(PNG)
        return subprocess.CompletedProcess(command, 0, CODEX_EVENTS, "Reading prompt from stdin...\n")

    response = gateway.handle(
        {"engine": "codex", "prompt": "다듬어", "want_images": True, "images": [{"data": PNG_B64}]},
        runner=runner, root=tmp_path,
    )

    assert response["ok"] and response["images"] == [{"name": "exec-1.png", "data": PNG_B64}]
    assert not generated.exists()
    usage = response["usage"]
    assert usage["engine"] == "codex" and usage["model"] == "gpt-6.1-sol"
    assert (usage["input_tokens"], usage["cached_input_tokens"], usage["output_tokens"]) == (67549, 54656, 228)
    assert usage["cost_usd"] is None and usage["duration_ms"] >= 0


def test_codexFailureWithoutImages_stillReportsUsage(tmp_path, monkeypatch):
    # 10/6 리허설: 이미지를 못 만든 호출도 토큰은 다 썼다.
    monkeypatch.setenv("CODEX_HOME", str(tmp_path / "codex"))
    calls = []

    def runner(command, **kwargs):
        calls.append(command)
        return subprocess.CompletedProcess(command, 0, CODEX_EVENTS, "")

    response = gateway.handle({"engine": "codex", "prompt": "그려", "want_images": True}, runner=runner, root=tmp_path)

    assert not response["ok"] and response["usage"]["input_tokens"] == 67549
    assert "--json" in calls[0]


def test_claude_reportsUsageIncludingCacheAndCost(tmp_path):
    stdout = json.dumps({
        "subtype": "success", "is_error": False, "result": "ok", "duration_ms": 2498,
        "total_cost_usd": 0.214875,
        "usage": {"input_tokens": 2, "cache_creation_input_tokens": 24467,
                  "cache_read_input_tokens": 95255, "output_tokens": 4},
        "modelUsage": {"claude-opus-5-5": {"inputTokens": 2}},
    })
    runner, _ = _recording(stdout=stdout)

    response = gateway.handle({"engine": "claude", "prompt": "x"}, runner=runner, root=tmp_path)

    assert response["usage"] == {
        "engine": "claude", "model": "claude-opus-5-5",
        "input_tokens": 2 + 24467 + 95255, "cached_input_tokens": 95255, "output_tokens": 4,
        "cost_usd": 0.214875, "duration_ms": 2498,
    }


def test_codex_withoutImagesWhenAsked_fails(tmp_path):
    runner, _ = _recording()

    response = gateway.handle({"engine": "codex", "prompt": "그려", "want_images": True},
                              runner=runner, root=tmp_path)

    assert not response["ok"]


@pytest.mark.parametrize(
    "request_",
    [
        {"engine": "bash", "prompt": "x"},
        {"engine": "claude", "prompt": ""},
        {"engine": "claude", "prompt": "x", "images": [{"data": base64.b64encode(b"GIF89a").decode()}]},
        {"engine": "claude", "prompt": "x", "want_images": True},
        {"engine": "claude", "prompt": "x", "images": [{"data": PNG_B64}] * 13},
    ],
)
def test_badRequests_neverRunACli(tmp_path, request_):
    runner, calls = _recording()

    with pytest.raises(gateway.BadRequest):
        gateway.handle(request_, runner=runner, root=tmp_path)
    assert calls == []


def test_timeout_isReportedNotRaised(tmp_path):
    def runner(command, **kwargs):
        raise subprocess.TimeoutExpired(command, 1)

    response = gateway.handle({"engine": "claude", "prompt": "x"}, runner=runner, root=tmp_path)

    assert not response["ok"] and "시간 초과" in response["error"]


def test_codex_unreadableOutput_isReportedNotRaised(tmp_path, monkeypatch):
    def write_output(command, kwargs):
        (Path(kwargs["cwd"]) / "out" / "p.png").write_bytes(PNG)

    runner, _ = _recording(on_call=write_output)
    monkeypatch.setattr(Path, "read_bytes", lambda self: (_ for _ in ()).throw(PermissionError("denied")))

    response = gateway.handle({"engine": "codex", "prompt": "x", "want_images": True}, runner=runner, root=tmp_path)

    assert not response["ok"] and "읽지 못함" in response["error"]


def test_codexFailure_keepsServerDetailsOutOfTheResponse(tmp_path, monkeypatch):
    """응답은 공개 Actions 로그에 찍힌다 — stderr 의 경로·사용자명은 서버 로그에만 남아야 한다."""
    log = tmp_path / "gw.log"
    monkeypatch.setattr(gateway, "LOG_PATH", log)
    monkeypatch.setenv("CODEX_HOME", str(tmp_path / "codex"))
    runner_with_stderr = lambda command, **kw: subprocess.CompletedProcess(  # noqa: E731
        command, 1, "", "Error: /home/friend/.codex/auth.json permission denied"
    )

    response = gateway.handle({"engine": "codex", "prompt": "x"}, runner=runner_with_stderr, root=tmp_path)

    usage = response.pop("usage")
    assert response == {"ok": False, "error": "codex 실패(종료 코드 1)", "images": [], "text": "", "structured": None}
    # 사용량은 숫자와 모델 이름뿐이다 — 경로가 섞일 자리가 없다.
    assert "/home/friend" not in json.dumps(usage) and usage["engine"] == "codex"
    assert "/home/friend" in log.read_text(encoding="utf-8")


def test_unexpectedError_isGenericInResponse(tmp_path, monkeypatch, capsys):
    monkeypatch.setattr(gateway, "LOG_PATH", tmp_path / "gw.log")
    monkeypatch.setattr(gateway, "LOCK_PATH", tmp_path / "gw.lock")

    def boom(request):
        raise RuntimeError("C:/Users/friend/secret-path")

    monkeypatch.setattr(gateway, "handle", boom)
    monkeypatch.setattr(sys, "stdin", io.TextIOWrapper(io.BytesIO(b'{"engine":"claude","prompt":"x"}')))

    gateway.main()

    out = json.loads(capsys.readouterr().out)
    assert out["error"] == "게이트웨이 오류(서버 로그 참고)"
    assert "secret-path" in (tmp_path / "gw.log").read_text(encoding="utf-8")


@pytest.mark.parametrize("model", ["--dangerously-skip-permissions", "-x", "a b", "", 3])
def test_flagLikeModel_isRejectedBeforeAnyCli(tmp_path, model):
    runner, calls = _recording()

    with pytest.raises(gateway.BadRequest):
        gateway.handle({"engine": "claude", "prompt": "x", "model": model}, runner=runner, root=tmp_path)
    assert calls == []


def test_system_isPassedAsFileNotArgument(tmp_path):
    seen = {}

    def runner(command, **kwargs):
        flag = command.index("--append-system-prompt-file")
        seen["text"] = Path(command[flag + 1]).read_text(encoding="utf-8")
        seen["command"] = command
        return subprocess.CompletedProcess(command, 0, json.dumps({"subtype": "success", "result": "ok"}), "")

    gateway.handle({"engine": "claude", "prompt": "x", "system": "--tools Bash", "model": "sonnet"}, runner=runner, root=tmp_path)

    assert seen["text"] == "--tools Bash"
    assert "--tools Bash" not in seen["command"]
    assert seen["command"][seen["command"].index("--model") + 1] == "sonnet"
