"""주문 하나를 처리한다. 러너의 drafter 가 기획 호출 뒤에 하던 일을 그대로 옮겼고, 마지막 제출만 납품 한 번이다."""

from __future__ import annotations

import base64
import copy
import json
import re
from dataclasses import dataclass
from typing import Any, Callable, Mapping

from pydantic import ValidationError

from gole_promotion_core import CONTRACT_VERSION, contract
from gole_promotion_core.gateway import Gateway, GatewayError, decode_png
from gole_promotion_core.ledger import RunResult, UsageMeter, run_payload
from gole_promotion_core.order import Order

Post = Callable[[Mapping[str, Any]], "Delivered"]
Notify = Callable[[str], None]

# 종료 코드. run.sh 는 이 둘을 "deliver 가 이미 알렸다"로 읽고, 그 밖의 0 아닌 코드(비정상 종료)만 따로 알린다.
CONFIG_INVALID = 2
DELIVERY_FAILED = 3

_FIXED_CODE = re.compile(r"[A-Z_]+")


@dataclass(frozen=True)
class Delivered:
    """납품 결과(백엔드가 남긴 원장 한 줄의 요점). status 가 None 이면 백엔드에 닿지 못했다."""

    status: int | None
    outcome: str | None = None
    reason: str | None = None
    detail: str | None = None
    post_id: str | None = None


@dataclass(frozen=True)
class _Draft:
    caption: str
    rationale: str
    captures: list[dict[str, Any]]
    images: list[dict[str, Any]]


def deliver(order: Order, gateway: Gateway, post: Post, notify: Notify, log: Callable[[str], None] = print) -> int:
    """주문 하나를 끝까지. 백엔드가 받았으면 0, 못 받았으면 DELIVERY_FAILED(서버가 주문을 남겨 둔다). 둘 다 여기서 알린다."""
    meter = UsageMeter(gateway)
    memory_context = copy.deepcopy(order.memory_context)  # 주문서(러너 쪽 기록)를 고쳐 쓰지 않는다
    reflection, reflection_error, draft = None, None, None
    if order.contract_version != CONTRACT_VERSION:
        result = RunResult("failed", "ERROR", "GATEWAY_CONTRACT_MISMATCH")
    else:
        result, reflection, reflection_error, draft = _work(order, meter, memory_context, log)
    if reflection_error and not result.detail:
        result = RunResult(result.outcome, result.code, reflection_error)
    payload = {
        "run": _run(order, result, meter, memory_context),
        **({"reflection": reflection} if reflection is not None else {}),
        **({"draft": _draft_request(order, draft)} if draft is not None else {}),
    }
    delivered = post(payload)
    kind = "서비스 소개" if order.category == "SERVICE" else "기능 홍보"
    if delivered.status is None or delivered.status >= 300:
        log(f"[promotion-deliver] 납품 실패 status={delivered.status}")
        notify(f"❌ GoLe 홍보 초안 배달 실패 · {kind} · HTTP {delivered.status or '연결 실패'} · {order.run_key}")
        return DELIVERY_FAILED
    log(f"[promotion-deliver] {delivered.outcome} ({delivered.reason})")
    if delivered.outcome == "FAILED" or delivered.reason == "QUEUE_FULL":
        notify(f"❌ GoLe 홍보 초안 {'실패' if delivered.outcome == 'FAILED' else '보류'} · {kind} · "
               f"{delivered.reason} · {order.run_key}")
    return 0


def _work(order: Order, meter: UsageMeter, memory_context: dict[str, Any], log: Callable[[str], None]):
    try:
        response = meter.call(order.plan)
    except GatewayError as error:
        if _FIXED_CODE.fullmatch(str(error)):
            log(f"[promotion-deliver] {error}")  # 계정 정지·한도 같은 고정 코드만 로그에 남긴다
        return RunResult("failed", "GATEWAY_FAILED", str(error)), None, None, None
    structured = response.get("structured")
    reflection, reflection_error = _reflection(order, structured)
    try:
        parsed = contract.parse(structured)
        if any(pick.image > len(order.shots) for pick in parsed.picks):
            raise ValueError("없는 후보 번호를 골랐다")
    except (ValidationError, ValueError) as error:
        # pydantic 오류 문자열에는 모델 출력 일부가 들어 있다 — 원장(관리자 전용)에만 남긴다.
        return RunResult("failed", "CHOICE_INVALID", str(error)), reflection, reflection_error, None
    if parsed.decision == "skip":
        return RunResult("skipped", "MODEL_SKIPPED", parsed.skip_reason), reflection, reflection_error, None

    captures, images, routes = [], [], []
    for pick in parsed.picks:
        shot = order.shots[pick.image - 1]
        edit = contract.edit_prompt(order.make_prefix, pick.headline, order.make_rules)
        polished = _make(meter, order, shot.data, edit, shot.route, log)
        routes.append(shot.route)
        captures.append({
            "label": pick.label, "route": shot.route, "dataSource": order.data_source, "capturedAt": shot.captured_at,
            "styleId": contract.STYLE_ID, "styleVersion": contract.STYLE_VERSION, "headline": pick.headline,
            "polishFailed": polished is None,
        })
        images.append({"data": polished or shot.data} | (
            {"original": shot.data, "edit": edit} if polished is not None else {}))
    _remember_image_stage(memory_context, order, routes)
    draft = _Draft(parsed.caption or "", parsed.rationale or "", captures, images)
    return RunResult("submitted", "SUBMITTED"), reflection, reflection_error, draft


def _remember_image_stage(memory_context: dict[str, Any], order: Order, routes: list[str]) -> None:
    """러너의 기획 단계 기록에 제작 단계를 더한다. 같은 반려·지침은 한 번만 남는다."""
    stage = order.image_stage
    feedback_ids = memory_context.setdefault("feedbackIds", [])
    feedback_ids.extend(item for item in stage.feedbackIds if item not in feedback_ids)
    guidelines = memory_context.setdefault("guidelines", [])
    known = {item.get("id") for item in guidelines}
    guidelines.extend(item for item in stage.guidelines if item.get("id") not in known)
    memory_context.setdefault("stages", []).append({
        "stage": "IMAGE_EDIT", "routes": list(dict.fromkeys(routes)),
        "feedbackIds": stage.feedbackIds, "guidelineIds": stage.guidelineIds,
    })


def _reflection(order: Order, structured: Any) -> tuple[dict[str, Any] | None, str | None]:
    """같은 호출이 낸 성찰 제안. 검증에 실패해도 초안은 계속 낸다 — 다음 실행이 미처리 반려를 다시 읽는다."""
    if not order.reflection_feedback_ids:
        return None, None
    try:
        proposals = structured.get("reflection") if isinstance(structured, dict) else None
        reflection = contract.Reflection.model_validate({"proposals": proposals})
        return reflection.payload(order.reflection_feedback_ids, order.run_key), None
    except (ValidationError, ValueError):
        return None, "MEMORY_REFLECTION_FAILED"


def _make(meter: UsageMeter, order: Order, original: str, prompt: str, route: str, log) -> str | None:
    """제작 호출(D25): 고른 원본 한 장을 홍보 이미지로. 실패하면 None — 그 자리는 원본을 그대로 올린다."""
    try:
        response = meter.call({
            "engine": "codex", "model": order.model, "prompt": prompt,
            "images": [{"data": original}], "want_images": True,
        })
        data = decode_png(response["images"][0])
    except (GatewayError, KeyError, IndexError, TypeError, ValueError) as error:
        # 모델 오류가 입력 프롬프트(작업 기억)를 인용할 수 있다. 예외 이름과 고정 코드만 남긴다.
        code = f" {error}" if isinstance(error, GatewayError) and _FIXED_CODE.fullmatch(str(error)) else ""
        log(f"[promotion-deliver] 이미지 제작 실패 {route}, 원본을 올림 ({type(error).__name__}{code})")
        return None
    return base64.b64encode(data).decode("ascii")


def _run(order: Order, result: RunResult, meter: UsageMeter, memory_context: dict[str, Any]) -> dict[str, Any]:
    """원장 한 줄(RecordRunRequest). 초안이 있으면 SUBMITTED 로 보내고, 백엔드가 큐·중복·오류에 따라 바꾼다."""
    run = RunResult(result.outcome, result.code, result.detail, memory_context=memory_context, run_key=order.run_key)
    env = {"PROMOTION_AGENT_CODE_SHA": order.agent_sha or "", "PROMOTION_AGENT_RUN_URL": order.run_url or ""}
    return run_payload(run, meter.calls, service=order.category == "SERVICE", sha=order.sha, env=env)


def _draft_request(order: Order, draft: _Draft) -> dict[str, Any]:
    return {
        "channel": "THREADS",
        "caption": draft.caption,
        "captures": draft.captures,
        "provenance": {"releaseTitle": order.release_title, "rationale": draft.rationale, "runUrl": order.run_url},
        "images": draft.images,
    }


def http_post(api_url: str, token: str, timeout: float = 120.0) -> Post:
    """납품 창구 POST. 응답 본문의 결과·사유만 읽는다 — 원장 원문은 로그에 남기지 않는다."""
    from urllib import error, request

    def post(payload: Mapping[str, Any]) -> Delivered:
        req = request.Request(
            api_url.rstrip("/") + "/api/internal/promotion-agent/deliveries",
            data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            headers={"Content-Type": "application/json", "Authorization": f"Bearer {token}"},
            method="POST",
        )
        try:
            with request.urlopen(req, timeout=timeout) as response:
                body = json.loads(response.read().decode("utf-8") or "{}")
                return Delivered(response.status, body.get("outcome"), body.get("reasonCode"),
                                 body.get("detail"), body.get("promotionPostId"))
        except error.HTTPError as failed:
            return Delivered(failed.code)
        except (error.URLError, OSError, ValueError):
            return Delivered(None)

    return post


def discord(webhook_url: str | None, timeout: float = 10.0) -> Notify:
    """운영 채널에 한 줄. 웹훅이 없거나 보내지 못해도 결과는 바꾸지 않는다."""
    from urllib import request

    def notify(content: str) -> None:
        if not webhook_url:
            return
        req = request.Request(
            webhook_url,
            data=json.dumps({"content": content, "allowed_mentions": {"parse": []}}).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            request.urlopen(req, timeout=timeout).close()
        except OSError:
            pass

    return notify
