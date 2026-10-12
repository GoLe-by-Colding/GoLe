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
    monkeypatch.setattr(gateway, "USAGE_PATH", tmp_path / "usage.jsonl")
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


def test_codex_returnsStructuredOutputAndAttachesInbox(tmp_path):
    def write_output(command, kwargs):
        Path(command[command.index("-o") + 1]).write_text('{"decision": "skip"}', encoding="utf-8")

    runner, calls = _recording(on_call=write_output)

    response = gateway.handle(
        {"engine": "codex", "prompt": "골라", "json_schema": {"type": "object"}, "images": [{"data": PNG_B64}]},
        runner=runner, root=tmp_path,
    )

    assert response["ok"] and response["structured"] == {"decision": "skip"}
    command = calls[0]["command"]
    assert command[command.index("-i") + 1].endswith("img-01.png") and "in/img-01.png" in calls[0]["input"]
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
    assert calls[0]["input"].startswith("$imagegen\n")
    assert "내장 image_gen" in calls[0]["input"]
    assert "API/CLI 이미지 생성으로 대체하지 않는다" in calls[0]["input"]
    assert command[command.index("-i") + 1].endswith("img-01.png")
    assert command[command.index("-s") + 1] == "workspace-write"


@pytest.mark.parametrize("makes_image", [True, False])
def test_codexOneCall_returnsStructuredAndImagesButNoFreeText(tmp_path, makes_image):
    # D25: 고르기·성찰·제작을 한 번에. 이미지가 없어도 구조화 출력은 돌려준다 — 호출 쪽이 원본을 쓴다.
    def write_output(command, kwargs):
        if makes_image:
            (Path(kwargs["cwd"]) / "out" / "pick-1.png").write_bytes(PNG)
        Path(command[command.index("-o") + 1]).write_text('{"decision": "skip"}', encoding="utf-8")

    runner, calls = _recording(on_call=write_output)

    response = gateway.handle(
        {"engine": "codex", "prompt": "골라서 만들어", "want_images": True,
         "json_schema": {"type": "object"}, "images": [{"data": PNG_B64}] * 2},
        runner=runner, root=tmp_path,
    )

    assert response["ok"] and response["structured"] == {"decision": "skip"} and response["text"] == ""
    assert len(response["images"]) == (1 if makes_image else 0)
    assert "--output-schema" in calls[0]["command"]


def test_codexUsageLimit_reportsFixedCode(tmp_path):
    runner = lambda command, **kw: subprocess.CompletedProcess(  # noqa: E731
        command, 1, "", "ERROR: You've hit your usage limit. Try again later."
    )

    response = gateway.handle({"engine": "codex", "prompt": "x"}, runner=runner, root=tmp_path)

    assert not response["ok"] and response["error"] == "CODEX_USAGE_LIMIT"


@pytest.mark.parametrize("returncode,stdout,stderr,code", [
    (1, json.dumps({"type": "error", "codex_error_info": "usage_limit_exceeded"}), "", "CODEX_USAGE_LIMIT"),
    (1, "", "stream error: rate_limit_exceeded", "CODEX_RATE_LIMIT"),
    (1, "", "Your authentication session could not be refreshed automatically.", "CODEX_AUTH_REQUIRED"),
    (1, "", "Error: Not logged in", "CODEX_AUTH_REQUIRED"),
    (1, json.dumps({"type": "turn.failed", "error": {"message": "server_overloaded"}}), "", "CODEX_OVERLOADED"),
    (0, json.dumps({"type": "turn.failed", "error": {"message": "context_window_exceeded"}}), "", "CODEX_CONTEXT_TOO_LARGE"),
])
def test_codexFailures_areClassifiedIntoFixedCodes(tmp_path, returncode, stdout, stderr, code):
    runner = lambda command, **kw: subprocess.CompletedProcess(command, returncode, stdout, stderr)  # noqa: E731

    response = gateway.handle({"engine": "codex", "prompt": "x"}, runner=runner, root=tmp_path)

    assert not response["ok"] and response["error"] == code


class _Clock:
    def __init__(self):
        self.t = 1_000_000.0

    def __call__(self):
        return self.t


def _recorded(guard, ok=True, error=None, input_tokens=0):
    guard.record({"ok": ok, "error": error, "usage": {"input_tokens": input_tokens, "output_tokens": 1}})


def test_accountGuard_coolsDownAfterAccountErrorsThenAllowsAgain(tmp_path, monkeypatch):
    clock = _Clock()
    guard = gateway._AccountGuard(tmp_path / "usage.jsonl", now=clock)
    _recorded(guard, ok=False, error="CODEX_USAGE_LIMIT", input_tokens=10)

    assert guard.refusal() == "CODEX_COOLDOWN"
    clock.t += gateway.COOLDOWN_SECONDS["CODEX_USAGE_LIMIT"]
    assert guard.refusal() is None
    _recorded(guard, ok=False, error="codex 실패(종료 코드 1)")  # 코드 결함은 계정을 쉬게 하지 않는다
    assert guard.refusal() is None


def test_accountGuard_refusesOverDailyBudgetAndForgetsAfterADay(tmp_path, monkeypatch):
    monkeypatch.setattr(gateway, "DAILY_INPUT_TOKENS", 100)
    clock = _Clock()
    guard = gateway._AccountGuard(tmp_path / "usage.jsonl", now=clock)
    _recorded(guard, input_tokens=60)
    assert guard.refusal() is None
    _recorded(guard, input_tokens=40)

    assert guard.refusal() == "GATEWAY_BUDGET_EXCEEDED"
    clock.t += 24 * 3600
    assert guard.refusal() is None


def test_accountGuard_keepsOnlyAWeekOfRecords(tmp_path):
    clock = _Clock()
    path = tmp_path / "usage.jsonl"
    guard = gateway._AccountGuard(path, now=clock)
    _recorded(guard, input_tokens=1)
    clock.t += gateway.KEEP_SECONDS
    _recorded(guard, input_tokens=2)

    assert [json.loads(line)["input"] for line in path.read_text(encoding="utf-8").splitlines()] == [2]


def test_main_refusesWithoutCallingTheCliWhileCoolingDown(tmp_path, monkeypatch, capsys):
    monkeypatch.setattr(gateway, "LOCK_PATH", tmp_path / "gw.lock")
    calls = []

    def limited(request):
        calls.append(request)
        return {"ok": False, "error": "CODEX_USAGE_LIMIT", "usage": {"input_tokens": 5}}

    monkeypatch.setattr(gateway, "handle", limited)
    for _ in range(2):
        monkeypatch.setattr(sys, "stdin", io.TextIOWrapper(io.BytesIO(b'{"engine":"codex","prompt":"x"}')))
        gateway.main()

    first, second = (json.loads(line) for line in capsys.readouterr().out.replace("}{", "}\n{").splitlines())
    assert first["error"] == "CODEX_USAGE_LIMIT" and second["error"] == "CODEX_COOLDOWN"
    assert len(calls) == 1


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


def test_codex_withoutImagesWhenAsked_fails(tmp_path):
    runner, _ = _recording()

    response = gateway.handle({"engine": "codex", "prompt": "그려", "want_images": True},
                              runner=runner, root=tmp_path)

    assert not response["ok"]


@pytest.mark.parametrize(
    "request_",
    [
        {"engine": "bash", "prompt": "x"},
        {"engine": "claude", "prompt": "x"},  # D25: codex 하나만 받는다
        {"engine": "codex", "prompt": ""},
        {"engine": "codex", "prompt": "x", "images": [{"data": base64.b64encode(b"GIF89a").decode()}]},
        {"engine": "codex", "prompt": "x", "images": [{"data": PNG_B64}] * 25},
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

    response = gateway.handle({"engine": "codex", "prompt": "x"}, runner=runner, root=tmp_path)

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
    monkeypatch.setattr(sys, "stdin", io.TextIOWrapper(io.BytesIO(b'{"engine":"codex","prompt":"x"}')))

    gateway.main()

    out = json.loads(capsys.readouterr().out)
    assert out["error"] == "게이트웨이 오류(서버 로그 참고)"
    assert "secret-path" in (tmp_path / "gw.log").read_text(encoding="utf-8")


@pytest.mark.parametrize("model", ["--dangerously-skip-permissions", "-x", "a b", "", 3])
def test_flagLikeModel_isRejectedBeforeAnyCli(tmp_path, model):
    runner, calls = _recording()

    with pytest.raises(gateway.BadRequest):
        gateway.handle({"engine": "codex", "prompt": "x", "model": model}, runner=runner, root=tmp_path)
    assert calls == []


def test_reasoning_isPassedAsConfigOverrideOnlyFromTheAllowedList(tmp_path):
    runner, calls = _recording()

    gateway.handle({"engine": "codex", "prompt": "x", "reasoning": "medium"}, runner=runner, root=tmp_path)

    command = calls[0]["command"]
    assert command[command.index("-c") + 1] == 'model_reasoning_effort="medium"'
    with pytest.raises(gateway.BadRequest):
        gateway.handle({"engine": "codex", "prompt": "x", "reasoning": 'high"\nsandbox_mode="x'}, runner=runner, root=tmp_path)
    assert len(calls) == 1


def test_systemAndPrompt_goThroughStdinNotArguments(tmp_path):
    runner, calls = _recording()

    gateway.handle({"engine": "codex", "prompt": "x", "system": "--dangerously-bypass-approvals-and-sandbox",
                    "model": "gpt-6.1-sol"}, runner=runner, root=tmp_path)

    command = calls[0]["command"]
    assert "--dangerously-bypass-approvals-and-sandbox" not in command
    assert calls[0]["input"].startswith("--dangerously-bypass-approvals-and-sandbox\n\nx")
    assert command[command.index("-m") + 1] == "gpt-6.1-sol"
