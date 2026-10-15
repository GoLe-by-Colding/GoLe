"""러너 쪽 게이트웨이 클라이언트. 호스트 키를 고정하고 주문서만 넣으며, 실패는 예외 하나로 모은다(D29)."""

import json
import subprocess

import pytest

from gole_promotion_agent import gateway_client
from gole_promotion_agent.gateway_client import SshGateway
from gole_promotion_core.gateway import GatewayError, decode_png

from .test_deliver import order


def _ssh(stdout=b"", returncode=0, stderr=b""):
    calls = []

    def runner(command, **kwargs):
        calls.append({"command": command, **kwargs})
        return subprocess.CompletedProcess(command, returncode, stdout, stderr)

    return runner, calls


def test_ssh_pinsHostKeyAndSubmitsTheOrderOnStdin(tmp_path):
    runner, calls = _ssh(json.dumps({"ok": True, "job": "gh-9-1"}).encode())
    gateway = SshGateway("me@host", tmp_path / "known_hosts", tmp_path / "key", port=2222, runner=runner)

    assert gateway.submit(order()) is None  # 접수만 — 결과는 서버가 납품한다
    command = calls[0]["command"]
    assert "StrictHostKeyChecking=yes" in command and "BatchMode=yes" in command
    assert command[command.index("-p") + 1] == "2222"
    assert command[0] == "ssh" and command[-2:] == ["me@host", "submit gh-9-1"]
    assert json.loads(calls[0]["input"])["run_key"] == "gh-9-1"


@pytest.mark.parametrize(
    "stdout,returncode",
    [(b"", 255), (b"not json", 0), (json.dumps({"ok": False, "error": "codex 실패"}).encode(), 0)],
)
def test_ssh_failuresRaiseGatewayError(tmp_path, stdout, returncode):
    runner, _ = _ssh(stdout, returncode)

    with pytest.raises(GatewayError):
        SshGateway("me@host", tmp_path / "kh", tmp_path / "k", runner=runner).submit(order())


def test_serverRefusal_keepsTheFixedCode(tmp_path):
    runner, _ = _ssh(json.dumps({"ok": False, "error": "GATEWAY_BUSY"}).encode())

    with pytest.raises(GatewayError) as raised:
        SshGateway("me@host", tmp_path / "kh", tmp_path / "k", runner=runner).submit(order())

    assert str(raised.value) == "GATEWAY_BUSY"


def test_fromEnv_requiresAllSshSettings():
    with pytest.raises(ValueError):
        gateway_client.from_env({"PROMOTION_GATEWAY_TARGET": "me@host"})


def test_decodePng_rejectsNonPng():
    import base64

    with pytest.raises(GatewayError):
        decode_png({"data": base64.b64encode(b"GIF89a").decode()})


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
        SshGateway("me@host", tmp_path / "kh", tmp_path / "k", runner=runner).submit(order())

    assert "203.0.113.7" not in str(error.value)


def test_fromEnv_needsKeyFile_passwordIsNoLongerAccepted(tmp_path):
    # D26: 비밀번호 접속은 셸 전체를 연다. 전용 키(forced command)만 받는다.
    base = {"PROMOTION_GATEWAY_TARGET": "me@host", "PROMOTION_GATEWAY_KNOWN_HOSTS_FILE": str(tmp_path / "kh")}

    with pytest.raises(ValueError):
        gateway_client.from_env({**base, "PROMOTION_GATEWAY_PASSWORD": "pw"})
    assert gateway_client.from_env({**base, "PROMOTION_GATEWAY_KEY_FILE": "k"})._command[0] == "ssh"


def test_fromEnv_localNeedsTheDeliveryWindow():
    # 로컬 모드도 납품 창구를 탄다 — 주소·토큰이 없으면 시작 전에 막는다.
    with pytest.raises(ValueError):
        gateway_client.from_env({"PROMOTION_GATEWAY": "local"})
    assert isinstance(
        gateway_client.from_env({"PROMOTION_GATEWAY": "local", "PROMOTION_AGENT_API_URL": "http://localhost:8080",
                                 "PROMOTION_AGENT_TOKEN": "t"}),
        gateway_client.LocalSubmitter,
    )
