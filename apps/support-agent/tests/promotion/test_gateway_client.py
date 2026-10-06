"""러너 쪽 게이트웨이 클라이언트. 호스트 키를 고정하고, 실패는 예외 하나로 모은다."""

import json
import subprocess

import pytest

from gole_promotion_agent import gateway_client
from gole_promotion_agent.gateway_client import GatewayError, SshGateway


def _ssh(stdout=b"", returncode=0, stderr=b""):
    calls = []

    def runner(command, **kwargs):
        calls.append({"command": command, **kwargs})
        return subprocess.CompletedProcess(command, returncode, stdout, stderr)

    return runner, calls


def test_ssh_pinsHostKeyAndSendsRequestOnStdin(tmp_path):
    runner, calls = _ssh(json.dumps({"ok": True, "text": "hi"}).encode())
    gateway = SshGateway("me@host", tmp_path / "known_hosts", key_file=tmp_path / "key", port=2222, runner=runner)

    assert gateway.call({"engine": "claude", "prompt": "x"})["text"] == "hi"
    command = calls[0]["command"]
    assert "StrictHostKeyChecking=yes" in command and "BatchMode=yes" in command
    assert command[command.index("-p") + 1] == "2222"
    assert command[-2:] == ["me@host", "~/gole-llm-gateway/run.sh"]
    assert calls[0]["env"] is None
    assert json.loads(calls[0]["input"])["prompt"] == "x"


@pytest.mark.parametrize(
    "stdout,returncode",
    [(b"", 255), (b"not json", 0), (json.dumps({"ok": False, "error": "codex 실패"}).encode(), 0)],
)
def test_ssh_failuresRaiseGatewayError(tmp_path, stdout, returncode):
    runner, _ = _ssh(stdout, returncode)

    with pytest.raises(GatewayError):
        SshGateway("me@host", tmp_path / "kh", key_file=tmp_path / "k", runner=runner).call({"engine": "claude"})


def test_failedCall_keepsUsageOnTheError(tmp_path):
    # 실패한 호출도 토큰을 쓴다 — 실행 기록이 그 몫을 놓치지 않게 예외에 싣는다.
    usage = {"engine": "codex", "input_tokens": 10}
    runner, _ = _ssh(json.dumps({"ok": False, "error": "codex 가 이미지를 만들지 않았다", "usage": usage}).encode())

    with pytest.raises(GatewayError) as raised:
        SshGateway("me@host", tmp_path / "kh", key_file=tmp_path / "k", runner=runner).call({"engine": "codex"})

    assert raised.value.usage == usage


def test_fromEnv_requiresAllSshSettings():
    with pytest.raises(ValueError):
        gateway_client.from_env({"PROMOTION_GATEWAY_TARGET": "me@host"})


def test_decodePng_rejectsNonPng():
    import base64

    with pytest.raises(GatewayError):
        gateway_client.decode_png({"data": base64.b64encode(b"GIF89a").decode()})


def test_localGateway_turnsAnyFailureIntoGatewayError(monkeypatch):
    """로컬 경로에서 게이트웨이가 예외를 던지면 drafter 가 원본으로 대신하지 못하고 통째로 죽었다."""
    gateway = gateway_client.LocalGateway()

    def boom(request):
        raise PermissionError("denied")

    monkeypatch.setattr(gateway._module, "handle", boom)

    with pytest.raises(GatewayError):
        gateway.call({"engine": "codex", "prompt": "x"})


def test_ssh_failureMessage_hidesServerAddress(tmp_path):
    runner, _ = _ssh(b"", 255, b"ssh: connect to host 203.0.113.7 port 22: Connection refused")

    with pytest.raises(GatewayError) as error:
        SshGateway("me@host", tmp_path / "kh", key_file=tmp_path / "k", runner=runner).call({"engine": "claude"})

    assert "203.0.113.7" not in str(error.value)


def test_password_goesThroughSshpassEnvNeverArgv(tmp_path):
    """비밀번호는 프로세스 목록(ps)에 보이는 인자가 아니라 SSHPASS 환경변수로만 넘긴다."""
    runner, calls = _ssh(json.dumps({"ok": True}).encode())

    SshGateway("me@host", tmp_path / "kh", password="pw-123", runner=runner).call({"engine": "claude"})

    command, env = calls[0]["command"], calls[0]["env"]
    assert command[:3] == ["sshpass", "-e", "ssh"]
    assert "pw-123" not in " ".join(command)
    assert env["SSHPASS"] == "pw-123"
    assert "StrictHostKeyChecking=yes" in command and "BatchMode=yes" not in command


def test_fromEnv_prefersKeyOverPassword(tmp_path):
    base = {"PROMOTION_GATEWAY_TARGET": "me@host", "PROMOTION_GATEWAY_KNOWN_HOSTS_FILE": str(tmp_path / "kh")}

    with_password = gateway_client.from_env({**base, "PROMOTION_GATEWAY_PASSWORD": "pw"})
    with_both = gateway_client.from_env({**base, "PROMOTION_GATEWAY_PASSWORD": "pw", "PROMOTION_GATEWAY_KEY_FILE": "k"})

    assert with_password._command[0] == "sshpass"
    assert with_both._command[0] == "ssh"
    with pytest.raises(ValueError):
        gateway_client.from_env(base)
