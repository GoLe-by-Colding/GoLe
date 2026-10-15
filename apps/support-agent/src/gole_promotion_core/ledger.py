"""실행 결과와 사용량 원장(D23). 모델이 쓴 글은 detail 에만 담고 공개 출력에는 숫자·코드만 남긴다."""

from __future__ import annotations

import json
import os
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping, Sequence

from gole_promotion_core import limits
from gole_promotion_core.gateway import Gateway, GatewayError


@dataclass(frozen=True)
class RunResult:
    """실행 결과. 공개 Actions 로그에는 outcome·code 만 나가고 detail 은 관리자 전용 원장에만 간다(D23)."""

    outcome: str  # submitted · skipped · failed
    code: str  # 원장의 RunReasonCode 와 같은 이름
    detail: str | None = None  # 모델이 쓴 사유·내부 오류 요약. 공개 로그에 찍지 않는다
    post_id: str | None = None
    memory_context: Mapping[str, Any] | None = None
    run_key: str | None = None


class UsageMeter:
    """게이트웨이 호출마다 사용량을 모은다. 실패한 호출도 토큰을 쓰므로 예외에 실린 몫까지 센다.

    호출 기록은 원장 API(ModelCall)와 같은 모양으로 쌓는다. ssh 자체가 실패해 사용량을 모르면 0 으로 둔다.
    """

    def __init__(self, gateway: Gateway):
        self._gateway = gateway
        self.calls: list[dict[str, Any]] = []

    def call(self, request: Mapping[str, Any]) -> dict[str, Any]:
        try:
            response = self._gateway.call(request)
        except GatewayError as error:
            self._add(request, False, error.usage)
            raise
        self._add(request, True, response.get("usage"))
        return response

    def _add(self, request: Mapping[str, Any], ok: bool, usage: Mapping[str, Any] | None) -> None:
        usage = usage or {}
        self.calls.append(
            {
                "engine": usage.get("engine") or request.get("engine"),
                "model": usage.get("model"),
                "ok": ok,
                "inputTokens": usage.get("input_tokens") or 0,
                "cachedInputTokens": usage.get("cached_input_tokens") or 0,
                "outputTokens": usage.get("output_tokens") or 0,
                "costUsd": usage.get("cost_usd"),
                "durationMs": usage.get("duration_ms"),
            }
        )

    def totals(self) -> dict[str, dict[str, Any]]:
        totals: dict[str, dict[str, Any]] = {}
        for call in self.calls:
            total = totals.setdefault(
                call["engine"] or "unknown",
                {"calls": 0, "inputTokens": 0, "cachedInputTokens": 0, "outputTokens": 0, "costUsd": None},
            )
            total["calls"] += 1
            for key in ("inputTokens", "cachedInputTokens", "outputTokens"):
                total[key] += call[key]
            if call["costUsd"] is not None:
                total["costUsd"] = (total["costUsd"] or 0) + call["costUsd"]
        return totals


def execution_run_key(env: Mapping[str, str]) -> str:
    run_id, attempt = env.get("GITHUB_RUN_ID"), env.get("GITHUB_RUN_ATTEMPT") or "1"
    return f"gh-{run_id}-{attempt}" if run_id and run_id.isdigit() and attempt.isdigit() else f"local-{uuid.uuid4().hex[:16]}"


def run_payload(
    result: RunResult,
    calls: Sequence[Mapping[str, Any]],
    *,
    service: bool,
    sha: str | None,
    env: Mapping[str, str],
) -> dict[str, Any]:
    """원장(POST /api/admin/promotion-runs) 한 줄. 모델이 쓴 사유는 detail 에만, 잘라서 담는다."""
    agent_sha = (env.get("PROMOTION_AGENT_CODE_SHA") or "").lower() or None
    return {
        "runKey": result.run_key or execution_run_key(env),
        "category": "SERVICE" if service else "FEATURE",
        "sourceCommitSha": None if service else sha,
        "outcome": result.outcome.upper(),
        "reasonCode": result.code,
        "detail": limits.clean_detail(result.detail),
        "promotionPostId": result.post_id,
        "agentSha": agent_sha if agent_sha and limits.AGENT_SHA_PATTERN.fullmatch(agent_sha) else None,
        "runUrl": env.get("PROMOTION_AGENT_RUN_URL") or None,
        "calls": list(calls)[: limits.MAX_RUN_CALLS],
        **({"memoryContext": dict(result.memory_context)} if result.memory_context is not None else {}),
    }


def close_run(publisher: Any, run_dir: Path, meter: UsageMeter, result: RunResult, arguments: Any) -> int:
    """원장에 한 줄 남기고 공개 로그에는 결과·코드만 찍는다. 원장 기록이 실패해도 실행 결과는 바꾸지 않는다."""
    payload = run_payload(result, meter.calls, service=arguments.service, sha=arguments.sha, env=os.environ)
    try:
        status = publisher.record_run(payload)
        recorded = status in (200, 201)
    except Exception as error:  # noqa: BLE001 — 기록 실패가 초안 제출을 되돌리면 안 된다
        status, recorded = type(error).__name__, False
    run_dir.mkdir(parents=True, exist_ok=True)
    # Actions 요약이 읽는 파일이다. 공개되므로 모델이 쓴 사유(detail)는 넣지 않는다.
    summary = {k: v for k, v in payload.items() if k not in {"detail", "memoryContext"}} | {"totals": meter.totals(), "recorded": recorded}
    (run_dir / "run.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"[promotion-agent] {result.outcome} ({result.code})")
    if not recorded:
        print(f"[promotion-agent] 실행 원장 기록 실패: {status}")
    return 1 if result.outcome == "failed" else 0
