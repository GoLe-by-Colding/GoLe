"""SSH 게이트웨이. 서버에 홀로 복사되는 파일이라 표준 라이브러리만 쓰고, 요청 하나가 서버에 남기는 것이 없어야 한다."""

import ast
import base64
import json
import subprocess
import sys
from pathlib import Path

import pytest

GATEWAY = Path(__file__).resolve().parents[1] / "gateway"
sys.path.insert(0, str(GATEWAY))

import gole_llm_gateway as gateway  # noqa: E402

PNG = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="
)
PNG_B64 = base64.b64encode(PNG).decode()


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
    assert modules <= {"__future__", "base64", "json", "os", "shutil", "subprocess", "sys",
                       "tempfile", "time", "pathlib", "typing", "fcntl"}


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
    command = calls[0]["command"]
    # 프롬프트는 stdin 으로 간다 — 인자에 두면 Windows shim 이 깨뜨려 -i 첨부까지 사라졌다.
    assert "다듬어" in calls[0]["input"] and not any("다듬어" in a for a in command)
    assert command[command.index("-i") + 1].endswith("img-01.png")
    assert command[command.index("-s") + 1] == "workspace-write"


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
