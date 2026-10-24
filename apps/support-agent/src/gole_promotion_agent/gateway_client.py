"""러너가 지인 서버에 주문을 넣는 쪽(promotion-review D29). 주문서 JSON 을 SSH stdin 으로 보내고 "접수"만 받는다.

인증은 authorized_keys 의 forced command 로 묶인 전용 키 하나다(D26·D28). 그 키로는 서버의 `run.sh` 만 돌고 셸이
열리지 않는다. 원격 명령은 `submit <run_key>` 하나만 받는다. 호스트 키는 known_hosts 로 고정한다(StrictHostKeyChecking=yes).

로컬에서 서버 없이 시험할 때는 PROMOTION_GATEWAY=local 로 deliver 를 이 프로세스에서 바로 돌린다 — 이 기기의 codex 로
만들고 PROMOTION_AGENT_API_URL 의 납품 창구로 PROMOTION_AGENT_TOKEN 을 써서 보낸다.
"""

from __future__ import annotations

import importlib.util
import json
import os
import subprocess
from pathlib import Path
from typing import Any, Callable, Mapping, Protocol

from gole_promotion_core.gateway import Gateway, GatewayError
from gole_promotion_core.ledger import RunResult
from gole_promotion_core.order import Order

GATEWAY_SCRIPT = Path(__file__).resolve().parents[2] / "gateway" / "gole_llm_gateway.py"
# 접수만 기다린다. 서버는 주문을 파일로 쓰고 바로 답한다.
DEFAULT_TIMEOUT_SECONDS = 5 * 60


class Submitter(Protocol):
    def submit(self, order: Order) -> RunResult | None:
        """주문을 넣는다. 서버가 나중에 처리하면 None, 이 자리에서 끝까지 처리했으면 그 결과(원장은 백엔드가 남겼다)."""
        ...


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
            "-T", target,
        ]
        self._timeout = timeout
        self._runner = runner

    def submit(self, order: Order) -> None:
        try:
            completed = self._runner(
                [*self._command, f"submit {order.run_key}"],
                input=order.model_dump_json().encode("utf-8"),
                capture_output=True,
                timeout=self._timeout,
            )
        except subprocess.TimeoutExpired as error:
            raise GatewayError(f"게이트웨이 시간 초과({int(self._timeout)}초)") from error
        if completed.returncode != 0:
            # ssh stderr 에는 서버 주소가 섞인다. 공개 저장소의 Actions 로그로 가므로 종료 코드만 남긴다.
            raise GatewayError(f"ssh 실패(종료 코드 {completed.returncode})")
        try:
            _checked(json.loads(completed.stdout.decode("utf-8")))
        except ValueError as error:
            raise GatewayError("게이트웨이 응답 해석 실패") from error
        return None


class LocalGateway:
    """서버 없이 이 기기의 codex 로 모델 호출을 돌린다(개발·시험용)."""

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


class LocalSubmitter:
    """주문을 서버에 넣는 대신 이 프로세스에서 deliver 로 끝까지 처리한다(개발·시험용). 결과를 바로 돌려준다."""

    def __init__(self, gateway: Gateway, post: Callable[[Mapping[str, Any]], Any],
                 notify: Callable[[str], None] = lambda _content: None, log: Callable[[str], None] = print):
        self._gateway, self._post, self._notify, self._log = gateway, post, notify, log

    def submit(self, order: Order) -> RunResult:
        # 러너가 서버 코드를 끌어오는 방향만 허용한다. 서버(deliver)는 러너를 모른다(D29).
        from gole_promotion_deliver.deliver import DELIVERY_FAILED, deliver

        sent: dict[str, Any] = {}
        answer: list[Any] = []

        def post(payload: Mapping[str, Any]):
            sent.update(payload)
            answer.append(self._post(payload))
            return answer[0]

        if deliver(order, self._gateway, post, self._notify, self._log) == DELIVERY_FAILED:
            raise GatewayError("DELIVERY_FAILED")
        delivered = answer[0]
        return RunResult(
            delivered.outcome.lower(), delivered.reason, delivered.detail, delivered.post_id,
            sent["run"].get("memoryContext"), order.run_key, delivered=True,
        )


def from_env(env: Mapping[str, str] | None = None) -> Submitter:
    env = os.environ if env is None else env
    if env.get("PROMOTION_GATEWAY") == "local":
        from gole_promotion_deliver.deliver import http_post

        api_url, token = (env.get("PROMOTION_AGENT_API_URL") or "").strip(), (env.get("PROMOTION_AGENT_TOKEN") or "").strip()
        if not (api_url and token):
            raise ValueError("로컬 납품에는 PROMOTION_AGENT_API_URL·PROMOTION_AGENT_TOKEN 이 필요하다")
        return LocalSubmitter(LocalGateway(), http_post(api_url, token))
    target = (env.get("PROMOTION_GATEWAY_TARGET") or "").strip()
    known_hosts = (env.get("PROMOTION_GATEWAY_KNOWN_HOSTS_FILE") or "").strip()
    key = (env.get("PROMOTION_GATEWAY_KEY_FILE") or "").strip()
    if not (target and known_hosts and key):
        raise ValueError("PROMOTION_GATEWAY_TARGET·KNOWN_HOSTS_FILE·KEY_FILE 이 필요하다")
    return SshGateway(target, Path(known_hosts), Path(key), port=int(env.get("PROMOTION_GATEWAY_PORT") or 22))
