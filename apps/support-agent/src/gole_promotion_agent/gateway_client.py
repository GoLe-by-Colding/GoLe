"""러너에서 LLM 게이트웨이를 부르는 쪽. 요청 JSON 을 SSH stdin 으로 보내고 stdout JSON 을 받는다.

인증은 둘 중 하나다.
- 키: authorized_keys 의 forced command 로 묶인 전용 키. 셸이 열리지 않아 더 안전하다.
- 비밀번호: 서버가 키 로그인을 막아 둔 경우. sshpass 로 넣고, 비밀번호는 인자가 아니라 SSHPASS
  환경변수로만 넘긴다. 이 방식은 셸 전체 권한이라 production 환경을 main 전용으로 묶어 둔다.
어느 쪽이든 원격 명령으로 게이트웨이를 실행한다(forced command 가 있으면 그쪽이 이긴다). 호스트 키는
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
REMOTE_COMMAND = "~/gole-llm-gateway/run.sh"


class GatewayError(RuntimeError):
    def __init__(self, message: str, usage: Mapping[str, Any] | None = None):
        super().__init__(message)
        # 실패한 호출도 토큰을 쓴다. ssh 자체가 실패했으면 None.
        self.usage = usage


class Gateway(Protocol):
    def call(self, request: Mapping[str, Any]) -> dict[str, Any]: ...


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
        *,
        key_file: Path | None = None,
        password: str | None = None,
        port: int = 22,
        timeout: float = DEFAULT_TIMEOUT_SECONDS,
        runner: Callable[..., subprocess.CompletedProcess] = subprocess.run,
    ):
        if (key_file is None) == (password is None):
            raise ValueError("key_file 과 password 중 하나만 준다")
        common = [
            "-p",
            str(port),
            "-o",
            f"UserKnownHostsFile={known_hosts}",
            "-o",
            "StrictHostKeyChecking=yes",
            "-o",
            "ServerAliveInterval=30",
        ]
        if key_file is not None:
            auth = ["ssh", "-i", str(key_file), "-o", "BatchMode=yes", "-o", "IdentitiesOnly=yes"]
            self._env = None
        else:
            # BatchMode 는 비밀번호 입력을 막으므로 쓰지 않는다. 비밀번호 외 방식은 시도하지 않는다.
            auth = ["sshpass", "-e", "ssh", "-o", "PreferredAuthentications=password", "-o", "PubkeyAuthentication=no"]
            self._env = {**os.environ, "SSHPASS": password}
        self._command = [*auth, *common, "-T", target, REMOTE_COMMAND]
        self._timeout = timeout
        self._runner = runner

    def call(self, request: Mapping[str, Any]) -> dict[str, Any]:
        try:
            completed = self._runner(
                self._command,
                input=json.dumps(request, ensure_ascii=False).encode("utf-8"),
                capture_output=True,
                timeout=self._timeout,
                env=self._env,
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
    password = env.get("PROMOTION_GATEWAY_PASSWORD") or ""
    if not (target and known_hosts and (key or password)):
        raise ValueError("PROMOTION_GATEWAY_TARGET·KNOWN_HOSTS_FILE 과 KEY_FILE 또는 PASSWORD 가 필요하다")
    return SshGateway(
        target,
        Path(known_hosts),
        # 키가 있으면 키를 쓴다 — 셸이 열리지 않는 쪽이 더 안전하다.
        key_file=Path(key) if key else None,
        password=None if key else password,
        port=int(env.get("PROMOTION_GATEWAY_PORT") or 22),
    )


def encode_png(path: Path) -> dict[str, str]:
    return {"data": base64.b64encode(Path(path).read_bytes()).decode("ascii")}


def decode_png(image: Mapping[str, Any]) -> bytes:
    data = base64.b64decode(image["data"], validate=True)
    if not data.startswith(b"\x89PNG"):
        raise GatewayError("게이트웨이가 PNG 가 아닌 이미지를 돌려줬다")
    return data
