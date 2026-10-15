"""러너에서 LLM 게이트웨이를 부르는 쪽. 요청 JSON 을 SSH stdin 으로 보내고 stdout JSON 을 받는다.

인증은 authorized_keys 의 forced command 로 묶인 전용 키 하나다(promotion-review D26·D28). 그 키로는 서버의
`run.sh`(게이트웨이 컨테이너 하나)만 돌고 셸이 열리지 않는다. 원격 명령은 서버가 무시한다.
호스트 키는 known_hosts 로 고정한다 — 처음 보는 서버에는 붙지 않는다(StrictHostKeyChecking=yes).

로컬에서 서버 없이 시험할 때는 PROMOTION_GATEWAY=local 로 같은 처리를 프로세스 안에서 돌린다.
"""

from __future__ import annotations

import importlib.util
import json
import os
import subprocess
from pathlib import Path
from typing import Any, Callable, Mapping

from gole_promotion_core.gateway import Gateway, GatewayError

GATEWAY_SCRIPT = Path(__file__).resolve().parents[2] / "gateway" / "gole_llm_gateway.py"
DEFAULT_TIMEOUT_SECONDS = 20 * 60
# forced command 가 이긴다 — 서버는 이 값을 무시한다. 로그에서 알아보기 위한 이름일 뿐이다.
REMOTE_COMMAND = "gole-llm-gateway"


def _checked(response: Any) -> dict[str, Any]:
    if not isinstance(response, dict):
        raise GatewayError("게이트웨이 응답이 JSON 객체가 아니다")
    if not response.get("ok"):
        usage = response.get("usage")
        raise GatewayError(str(response.get("error") or "게이트웨이 실패"), usage if isinstance(usage, dict) else None)
    return response


class SshGateway:
    def __init__(
        self,
        target: str,
        known_hosts: Path,
        key_file: Path,
        *,
        port: int = 22,
        timeout: float = DEFAULT_TIMEOUT_SECONDS,
        runner: Callable[..., subprocess.CompletedProcess] = subprocess.run,
    ):
        self._command = [
            "ssh", "-i", str(key_file), "-o", "BatchMode=yes", "-o", "IdentitiesOnly=yes",
            "-p", str(port),
            "-o", f"UserKnownHostsFile={known_hosts}",
            "-o", "StrictHostKeyChecking=yes",
            "-o", "ServerAliveInterval=30",
            "-T", target, REMOTE_COMMAND,
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
            # ssh stderr 에는 서버 주소가 섞인다. 공개 저장소의 Actions 로그로 가므로 종료 코드만 남긴다.
            raise GatewayError(f"ssh 실패(종료 코드 {completed.returncode})")
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
    known_hosts = (env.get("PROMOTION_GATEWAY_KNOWN_HOSTS_FILE") or "").strip()
    key = (env.get("PROMOTION_GATEWAY_KEY_FILE") or "").strip()
    if not (target and known_hosts and key):
        raise ValueError("PROMOTION_GATEWAY_TARGET·KNOWN_HOSTS_FILE·KEY_FILE 이 필요하다")
    return SshGateway(target, Path(known_hosts), Path(key), port=int(env.get("PROMOTION_GATEWAY_PORT") or 22))
