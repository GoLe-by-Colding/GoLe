"""`python -m gole_promotion_deliver <order.json>` — 지인 서버의 run.sh 가 주문마다 게이트웨이 컨테이너 안에서 부른다(D29).

환경(서버의 promotion.env → --env-file): PROMOTION_AGENT_API_URL, PROMOTION_AGENT_TOKEN, DISCORD_OPERATIONS_WEBHOOK_URL.
"""

from __future__ import annotations

import importlib.util
import os
import sys
from pathlib import Path
from typing import Any, Mapping, Sequence

from pydantic import ValidationError

from gole_promotion_core.gateway import GatewayError
from gole_promotion_core.order import Order
from gole_promotion_deliver.deliver import deliver, discord, http_post

GATEWAY_SCRIPT = Path(os.environ.get("GOLE_GATEWAY_SCRIPT", "/opt/gateway/gole_llm_gateway.py"))


class InProcessGateway:
    """같은 컨테이너의 게이트웨이를 SSH 없이 부른다. 잠금·계정 가드는 게이트웨이의 call() 이 그대로 건다."""

    def __init__(self, script: Path = GATEWAY_SCRIPT):
        spec = importlib.util.spec_from_file_location("gole_llm_gateway", script)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        self._module = module

    def call(self, request: Mapping[str, Any]) -> dict[str, Any]:
        response = self._module.call(dict(request))
        if not response.get("ok"):
            usage = response.get("usage")
            raise GatewayError(str(response.get("error") or "게이트웨이 실패"), usage if isinstance(usage, dict) else None)
        return response


def main(argv: Sequence[str] | None = None) -> int:
    args = list(sys.argv[1:] if argv is None else argv)
    notify = discord(os.environ.get("DISCORD_OPERATIONS_WEBHOOK_URL") or None)
    api_url, token = os.environ.get("PROMOTION_AGENT_API_URL", ""), os.environ.get("PROMOTION_AGENT_TOKEN", "")
    if len(args) != 1 or not api_url or not token:
        print("[promotion-deliver] 주문서 경로·PROMOTION_AGENT_API_URL·PROMOTION_AGENT_TOKEN 이 필요하다")
        notify("❌ GoLe 홍보 초안 배달 실패 · 서버 설정 누락(promotion.env)")
        return 2
    try:
        order = Order.model_validate_json(Path(args[0]).read_bytes())
    except (OSError, ValidationError):
        print("[promotion-deliver] 주문서를 읽지 못함")
        notify("❌ GoLe 홍보 초안 배달 실패 · 주문서를 읽지 못함")
        return 2
    return deliver(order, InProcessGateway(), http_post(api_url, token), notify)


if __name__ == "__main__":
    raise SystemExit(main())
