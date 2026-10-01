"""러너에서 LLM 게이트웨이를 부르는 쪽. 요청 JSON 을 SSH stdin 으로 보내고 stdout JSON 을 받는다.

서버의 authorized_keys 가 이 키를 forced command 로 묶어 두므로 원격 명령은 보내지 않는다. 호스트 키는
known_hosts 로 고정한다 — 처음 보는 서버에는 붙지 않는다(StrictHostKeyChecking=yes).

로컬에서 서버 없이 시험할 때는 PROMOTION_GATEWAY=local 로 같은 처리를 프로세스 안에서 돌린다.
"""

from __future__ import annotations

import base64
import importlib.util
import json
import os
import subprocess
from pathlib import Path
from typing import Any, Callable, Mapping, Protocol

GATEWAY_SCRIPT = Path(__file__).resolve().parents[2] / "gateway" / "gole_llm_gateway.py"
DEFAULT_TIMEOUT_SECONDS = 20 * 60


class GatewayError(RuntimeError):
    pass


class Gateway(Protocol):
    def call(self, request: Mapping[str, Any]) -> dict[str, Any]: ...


def _checked(response: Any) -> dict[str, Any]:
    if not isinstance(response, dict):
        raise GatewayError("게이트웨이 응답이 JSON 객체가 아니다")
    if not response.get("ok"):
        raise GatewayError(str(response.get("error") or "게이트웨이 실패"))
    return response


class SshGateway:
    def __init__(
        self,
        target: str,
        key_file: Path,
        known_hosts: Path,
        port: int = 22,
        timeout: float = DEFAULT_TIMEOUT_SECONDS,
        runner: Callable[..., subprocess.CompletedProcess] = subprocess.run,
    ):
        self._command = [
            "ssh",
            "-i",
            str(key_file),
            "-p",
            str(port),
            "-o",
            "BatchMode=yes",
            "-o",
            "IdentitiesOnly=yes",
            "-o",
            f"UserKnownHostsFile={known_hosts}",
            "-o",
            "StrictHostKeyChecking=yes",
            "-o",
            "ServerAliveInterval=30",
            target,
        ]
        self._timeout = timeout
        self._runner = runner

    def call(self, request: Mapping[str, Any]) -> dict[str, Any]:
        try:
            completed = self._runner(
                self._command,
                input=json.dumps(request, ensure_ascii=False).encode("utf-8"),
                capture_output=True,
                timeout=self._timeout,
            )
        except subprocess.TimeoutExpired as error:
            raise GatewayError(f"게이트웨이 시간 초과({int(self._timeout)}초)") from error
        if completed.returncode != 0:
            tail = completed.stderr.decode("utf-8", "replace")[-300:]
            raise GatewayError(f"ssh 종료 코드 {completed.returncode}: {tail}")
        try:
            return _checked(json.loads(completed.stdout.decode("utf-8")))
        except ValueError as error:
            raise GatewayError("게이트웨이 응답 해석 실패") from error


class LocalGateway:
    """서버 없이 이 기기의 claude/codex 로 같은 처리를 돌린다(개발·시험용)."""

    def __init__(self) -> None:
        spec = importlib.util.spec_from_file_location("gole_llm_gateway", GATEWAY_SCRIPT)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        self._module = module

    def call(self, request: Mapping[str, Any]) -> dict[str, Any]:
        try:
            response = self._module.handle(dict(request))
        except Exception as error:  # noqa: BLE001 — SSH 경로의 main() 처럼 어떤 실패든 GatewayError 하나로
            raise GatewayError(f"{type(error).__name__}: {error}") from error
        return _checked(response)


def from_env(env: Mapping[str, str] | None = None) -> Gateway:
    env = os.environ if env is None else env
    if env.get("PROMOTION_GATEWAY") == "local":
        return LocalGateway()
    target = (env.get("PROMOTION_GATEWAY_TARGET") or "").strip()
    key = (env.get("PROMOTION_GATEWAY_KEY_FILE") or "").strip()
    known_hosts = (env.get("PROMOTION_GATEWAY_KNOWN_HOSTS_FILE") or "").strip()
    if not (target and key and known_hosts):
        raise ValueError("PROMOTION_GATEWAY_TARGET·KEY_FILE·KNOWN_HOSTS_FILE_REQUIRED")
    return SshGateway(target, Path(key), Path(known_hosts), int(env.get("PROMOTION_GATEWAY_PORT") or 22))


def encode_png(path: Path) -> dict[str, str]:
    return {"data": base64.b64encode(Path(path).read_bytes()).decode("ascii")}


def decode_png(image: Mapping[str, Any]) -> bytes:
    data = base64.b64decode(image["data"], validate=True)
    if not data.startswith(b"\x89PNG"):
        raise GatewayError("게이트웨이가 PNG 가 아닌 이미지를 돌려줬다")
    return data
