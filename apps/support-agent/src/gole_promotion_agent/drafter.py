"""홍보 초안 한 편을 만든다. 러너 안 데모 스택을 찍고, 판단·제작은 게이트웨이의 codex 한 번에 맡긴다(D25).

    후보 화면 일괄 캡처 ─▶ 게이트웨이 codex 1회: 화면 하나 고르기·캡션 + 반려 성찰 + 홍보 이미지 제작
                       ─▶ 검증 ─▶ BackendPublisher: 성찰 저장, 다듬은 이미지 + 원본 + 지시문 제출(검토 요청)

모델은 만들기만 하고 검증·제출은 여기서 한다. 운영 관리자 자격증명은 게이트웨이로 가지 않는다 —
서버로 가는 것은 데모 화면 캡처와 프롬프트뿐이다.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import uuid
from dataclasses import dataclass, replace
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Mapping, Sequence

from pydantic import ValidationError

from gole_promotion_agent import drafting, memory, policy
from gole_promotion_agent.gateway_client import Gateway, GatewayError, decode_png, encode_png


@dataclass(frozen=True)
class RunResult:
    """실행 결과. 공개 Actions 로그에는 outcome·code 만 나가고 detail 은 관리자 전용 원장에만 간다(D23)."""

    outcome: str  # submitted · skipped · failed
    code: str  # 원장의 RunReasonCode 와 같은 이름
    detail: str | None = None  # 모델이 쓴 사유·내부 오류 요약. 공개 로그에 찍지 않는다
    post_id: str | None = None
    memory_context: Mapping[str, Any] | None = None
    run_key: str | None = None


@dataclass(frozen=True)
class Shot:
    route: str
    path: Path
    captured_at: str


def _slug(route: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", route.lower()).strip("-") or "home"


class Drafter:
    def __init__(
        self,
        *,
        repo: Path,
        run_dir: Path,
        publisher: Any,
        gateway: Gateway,
        camera: Any | None,  # None 이면 run_dir/captures 의 지난 캡처를 쓴다
        routes: Sequence[str],
        demo: bool,
        service: bool,
        sha: str | None = None,
        codex_model: str | None = None,
        run_key: str | None = None,
        log: Callable[[str], None] = print,
    ):
        if not service and not (sha and policy.SHA_PATTERN.match(sha)):
            raise ValueError("INVALID_SHA")
        self._repo = Path(repo)
        self._run_dir = Path(run_dir)
        self._publisher = publisher
        self._gateway = gateway
        self._camera = camera
        self._routes = policy.promotional_routes(routes)
        self._demo = demo
        self._service = service
        self._sha = sha
        self._codex_model = codex_model
        self.run_key = run_key or execution_run_key(os.environ)
        self._memory = memory.Context()
        self.memory_usage: dict[str, Any] = {"feedbackIds": [], "guidelines": [], "stages": []}
        self._memory_error: str | None = None
        self._log = log

    def run(self) -> RunResult:
        self.memory_usage = {"feedbackIds": [], "guidelines": [], "stages": []}
        self._memory_error = None
        try:
            self._memory = memory.Context.model_validate(
                self._publisher.memory_context("SERVICE" if self._service else "FEATURE", self._routes)
            )
        except Exception as error:  # noqa: BLE001 — 메모리 없이 새 초안을 만들지 않는다
            self._log(f"[promotion-agent] MEMORY_CONTEXT_FAILED ({type(error).__name__})")
            return RunResult("failed", "ERROR", "MEMORY_CONTEXT_FAILED", run_key=self.run_key)
        result = self._draft()
        return replace(
            result, detail=result.detail or self._memory_error,
            memory_context=self.memory_usage, run_key=self.run_key,
        )

    def _reflect(self, structured: Any) -> None:
        """같은 호출이 낸 성찰 제안을 저장한다. 실패해도 초안은 계속 낸다 — 다음 실행이 미처리 경험을 다시 읽는다."""
        pending = self._memory.unreflectedFeedback
        if not pending:
            return
        try:
            proposals = structured.get("reflection") if isinstance(structured, dict) else None
            reflection = memory.Reflection.model_validate({"proposals": proposals})
            self._publisher.reflect(reflection.payload([item.id for item in pending], self.run_key))
        except Exception as error:  # noqa: BLE001 — 다음 실행이 미처리 경험을 다시 읽는다
            self._memory_error = "MEMORY_REFLECTION_FAILED"
            self._log(f"[promotion-agent] MEMORY_REFLECTION_FAILED ({type(error).__name__})")

    def _remember(
        self, stage: str, routes: Sequence[str],
        feedback: Sequence[memory.Feedback], guidelines: Sequence[memory.Guideline],
    ) -> None:
        for item in feedback:
            if item.id not in self.memory_usage["feedbackIds"]:
                self.memory_usage["feedbackIds"].append(item.id)
        known = {item["id"] for item in self.memory_usage["guidelines"]}
        self.memory_usage["guidelines"].extend(item.applied_snapshot() for item in guidelines if item.id not in known)
        self.memory_usage["stages"].append({
            "stage": stage, "routes": list(dict.fromkeys(routes)),
            "feedbackIds": [item.id for item in feedback], "guidelineIds": [item.id for item in guidelines],
        })

    def _draft(self) -> RunResult:
        if self._publisher.pending_count() >= policy.MAX_PENDING_REVIEW:
            return RunResult("skipped", "QUEUE_FULL")

        subject, diff = None, None
        if not self._service:
            from gole_promotion_agent.hands import ReleaseScanner

            scanner = ReleaseScanner(self._repo, self._sha)
            if not scanner.touches_web():
                return RunResult("skipped", "NO_WEB_CHANGE")
            if self._publisher.exists(self._sha):
                return RunResult("skipped", "ALREADY_DRAFTED")
            subject, diff = scanner.subject(), scanner.diff()

        shots = self._capture_all()
        if not shots:
            return RunResult("failed", "NO_CAPTURES")

        category = "SERVICE" if self._service else "FEATURE"
        plan_feedback, plan_guidelines = self._memory.select(category, ("CAPTION", "SCREEN_SELECTION"))
        image_feedback, image_guidelines = self._memory.select(category, ("IMAGE_EDIT",))
        # 제작 지시문은 검토 화면에 남는다(백엔드 상한). 문구는 모델이 정하므로 가장 긴 자리로 호출 전에 잰다.
        parts = _edit_parts(image_feedback, image_guidelines, "가" * 41)
        if memory.utf16_length("".join(parts.values())) > memory.MAX_EDIT_PROMPT:
            lengths = {name: memory.utf16_length(text) for name, text in parts.items()}
            lengths["total"] = sum(lengths.values())
            return RunResult("failed", "ERROR", "EDIT_PROMPT_TOO_LONG " + json.dumps(lengths))

        feedback = list({item.id: item for item in [*plan_feedback, *image_feedback]}.values())
        guidelines = list({item.id: item for item in [*plan_guidelines, *image_guidelines]}.values())
        self._remember("PLAN", [shot.route for shot in shots], plan_feedback, plan_guidelines)
        prompt = drafting.run_prompt(
            drafting.system_prompt(self._publisher.history(policy.HISTORY_LIMIT), self._demo, self._service),
            memory.render(feedback, guidelines),
            drafting.task_prompt([shot.route for shot in shots], service=self._service, subject=subject, diff=diff),
            memory.reflection_prompt(self._memory, [item.id for item in feedback], [item.id for item in guidelines]),
        )
        try:
            response = self._gateway.call({
                "engine": "codex", "model": self._codex_model, "prompt": prompt,
                "json_schema": drafting.OUTPUT_SCHEMA, "want_images": True,
                "images": [encode_png(shot.path) for shot in shots],
            })
        except GatewayError as error:
            if re.fullmatch(r"[A-Z_]+", str(error)):
                self._log(f"[promotion-agent] {error}")  # 계정 정지·한도 같은 고정 코드만 공개 로그에 남긴다
            return RunResult("failed", "GATEWAY_FAILED", str(error))
        structured = response.get("structured")
        (self._run_dir / "choice.json").write_text(json.dumps(structured, ensure_ascii=False, indent=2), encoding="utf-8")
        self._reflect(structured)
        try:
            draft = drafting.parse(structured)
            if any(pick.image > len(shots) for pick in draft.picks):
                raise ValueError("없는 후보 번호를 골랐다")
        except (ValidationError, ValueError) as error:
            # pydantic 오류 문자열에는 모델 출력 일부(input_value)가 들어 있다 — 원장에만 남긴다.
            return RunResult("failed", "CHOICE_INVALID", str(error))
        if draft.decision == "skip":
            return RunResult("skipped", "MODEL_SKIPPED", draft.skip_reason)

        picked = [shots[pick.image - 1] for pick in draft.picks]
        edit_prompts = ["".join(_edit_parts(image_feedback, image_guidelines, pick.headline).values())
                        for pick in draft.picks]
        self._remember("IMAGE_EDIT", [shot.route for shot in picked], image_feedback, image_guidelines)
        polished = [self._polished(response, shot) for shot in picked]

        media_keys = list(self._publisher.upload([path or shot.path for shot, path in zip(picked, polished)]))
        # 다듬은 사진의 원본만 따로 올린다. 검토 화면이 게시 이미지와 나란히 대조한다.
        originals = [shot.path for shot, path in zip(picked, polished) if path is not None]
        original_keys = iter(self._publisher.upload(originals) if originals else ())
        captures = []
        for pick, shot, path, edit_prompt in zip(draft.picks, picked, polished, edit_prompts):
            capture = {"label": pick.label, "route": shot.route, "capturedAt": shot.captured_at,
                       "styleId": drafting.STYLE_ID, "styleVersion": drafting.STYLE_VERSION,
                       "headline": pick.headline, "polishFailed": path is None}
            if path is not None:
                capture |= {"originalMediaKey": next(original_keys), "edit": edit_prompt}
            captures.append(capture)

        post_id = self._publisher.create(
            None if self._service else self._sha,
            draft.caption,
            media_keys,
            {
                "captures": captures,
                "rationale": draft.rationale,
                "releaseTitle": subject or "서비스 소개",
            },
        )
        self._publisher.finalize(post_id)
        return RunResult("submitted", "SUBMITTED", post_id=post_id)

    def _capture_all(self) -> list[Shot]:
        out = self._run_dir / "captures"
        manifest = out / "manifest.json"
        if self._camera is None:
            # 지난 릴리스 때 찍어 둔 화면을 그대로 쓴다(서비스 소개). 화면은 다음 릴리스 전까지 같다.
            if not manifest.exists():
                return []
            data = json.loads(manifest.read_text(encoding="utf-8"))
            if (not isinstance(data, dict) or data.get("version") != policy.CAPTURE_MANIFEST_VERSION
                    or data.get("viewport") != policy.CAPTURE_VIEWPORT or not isinstance(data.get("captures"), list)):
                return []
            reused = []
            for item in data["captures"]:
                if not isinstance(item, dict):
                    return []
                filename, route, captured_at = item.get("file"), item.get("route"), item.get("captured_at")
                if (not isinstance(filename, str) or not filename or Path(filename).name != filename
                        or "/" in filename or "\\" in filename or not filename.endswith(".png")
                        or route not in self._routes or not policy.is_public_capture_route(route, self._demo)
                        or not isinstance(captured_at, str) or not captured_at):
                    return []
                source = (out / filename).resolve()
                if source.parent != out.resolve():
                    return []
                if source.is_file():
                    reused.append(Shot(route, source, captured_at))
            return reused
        shots: list[Shot] = []
        try:
            for index, route in enumerate(self._routes, start=1):
                destination = out / f"{index:02d}-{_slug(route)}.png"
                try:
                    self._camera.capture(route, destination)
                except ValueError as error:
                    # 화면 하나가 안 찍혀도 나머지로 판단한다. 무엇이 빠졌는지는 로그에 남긴다.
                    self._log(f"[promotion-agent] 캡처 실패 {route}: {error}")
                    continue
                shots.append(Shot(route, destination, datetime.now(timezone.utc).isoformat()))
        finally:
            self._camera.close()
        # 다음 서비스 소개 실행이 다시 찍지 않고 쓰도록 무엇을 언제 찍었는지 남긴다.
        manifest.parent.mkdir(parents=True, exist_ok=True)
        manifest.write_text(
            json.dumps(
                {"version": policy.CAPTURE_MANIFEST_VERSION, "viewport": policy.CAPTURE_VIEWPORT,
                 "captures": [{"route": s.route, "file": s.path.name, "captured_at": s.captured_at} for s in shots]},
                ensure_ascii=False,
            ),
            encoding="utf-8",
        )
        return shots

    def _polished(self, response: Mapping[str, Any], shot: Shot) -> Path | None:
        """같은 호출이 만든 홍보 이미지 경로. 없거나 깨졌으면 None — 그 자리는 원본을 그대로 올린다."""
        try:
            data = decode_png(response["images"][0])
        except (GatewayError, KeyError, IndexError, TypeError, ValueError) as error:
            # 모델 오류가 입력 프롬프트를 인용할 수 있다. 작업 기억 원문은 공개 로그에 싣지 않는다.
            self._log(f"[promotion-agent] 이미지 제작 실패 {shot.route}, 원본을 올림 ({type(error).__name__})")
            return None
        destination = self._run_dir / "polished" / f"01-{_slug(shot.route)}.png"
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(data)
        return destination


def _edit_parts(
    feedback: Sequence[memory.Feedback], guidelines: Sequence[memory.Guideline], headline: str,
) -> dict[str, str]:
    """검토 화면에 남기는 제작 지시문 — 고정 절차 + IMAGE_EDIT 작업 기억 + 문구."""
    parts = memory.render_parts(feedback, guidelines)
    parts["fixed"] = drafting.POLISH_PROMPT + parts["fixed"]
    parts["headline"] = "\n외부 제목 문구 (JSON 데이터):\n" + json.dumps({"headline": headline}, ensure_ascii=False)
    return parts


def _required(name: str) -> str:
    value = (os.environ.get(name) or "").strip()
    if not value:
        raise ValueError(f"{name}_REQUIRED")
    return value


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
        "detail": policy.clean_detail(result.detail),
        "promotionPostId": result.post_id,
        "agentSha": agent_sha if agent_sha and policy.AGENT_SHA_PATTERN.fullmatch(agent_sha) else None,
        "runUrl": env.get("PROMOTION_AGENT_RUN_URL") or None,
        "calls": list(calls)[: policy.MAX_RUN_CALLS],
        **({"memoryContext": dict(result.memory_context)} if result.memory_context is not None else {}),
    }


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="gole_promotion_agent")
    parser.add_argument("--repo", required=True, help="찍을 대상 소스(릴리스 worktree)")
    parser.add_argument("--run-dir", required=True)
    parser.add_argument("--sha")
    parser.add_argument("--service", action="store_true")
    parser.add_argument(
        "--reuse-captures", action="store_true", help="run-dir/captures 의 지난 캡처를 쓰고 찍지 않는다"
    )
    arguments = parser.parse_args(argv)

    from gole_agent_runtime.privacy import reject_external_tracing
    from gole_promotion_agent import gateway_client
    from gole_promotion_agent.hands import AppRouteCatalog, BackendPublisher, PlaywrightCamera

    reject_external_tracing()
    data_source = os.environ.get("PROMOTION_AGENT_DATA_SOURCE") or "DEMO"
    demo = data_source == "DEMO"
    publisher = BackendPublisher(
        _required("PROMOTION_AGENT_API_URL").rstrip("/"),
        _required("PROMOTION_AGENT_ADMIN_EMAIL"),
        _required("PROMOTION_AGENT_ADMIN_PASSWORD"),
        run={
            "category": "SERVICE" if arguments.service else "FEATURE",
            "dataSource": data_source,
            "runUrl": os.environ.get("PROMOTION_AGENT_RUN_URL") or None,
        },
    )
    routes = AppRouteCatalog(Path(arguments.repo), demo=demo).routes()
    camera = None
    if not arguments.reuse_captures:
        capture_login = BackendPublisher(
            _required("PROMOTION_AGENT_CAPTURE_API_URL").rstrip("/"),
            _required("PROMOTION_AGENT_CAPTURE_ADMIN_EMAIL"),
            _required("PROMOTION_AGENT_CAPTURE_ADMIN_PASSWORD"),
        )
        # 로그인된 상태로 찍는다 — 로그인 뒤에만 보이는 기능도 홍보 대상이다(D12).
        camera = PlaywrightCamera(
            _required("PROMOTION_AGENT_CAPTURE_SITE").rstrip("/"), routes, capture_login.browser_session
        )
    run_dir = Path(arguments.run_dir)
    meter = UsageMeter(gateway_client.from_env())
    drafter = Drafter(
        repo=Path(arguments.repo),
        run_dir=run_dir,
        publisher=publisher,
        gateway=meter,
        camera=camera,
        routes=routes,
        demo=demo,
        service=arguments.service,
        sha=arguments.sha,
        codex_model=os.environ.get("PROMOTION_AGENT_CODEX_MODEL") or None,
    )
    try:
        result = drafter.run()
    except Exception as error:
        # 예외로 끝나도 그때까지 쓴 사용량은 원장에 남긴다. 예외 문구에는 내부 사정이 섞일 수 있어 이름만 담는다.
        _close_run(publisher, run_dir, meter, RunResult(
            "failed", "ERROR", type(error).__name__, memory_context=drafter.memory_usage, run_key=drafter.run_key,
        ), arguments)
        raise
    return _close_run(publisher, run_dir, meter, result, arguments)


def _close_run(publisher: Any, run_dir: Path, meter: UsageMeter, result: RunResult, arguments: Any) -> int:
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

if __name__ == "__main__":
    raise SystemExit(main())
