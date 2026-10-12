"""홍보 초안 한 편을 만든다. 러너 안 데모 스택을 찍고, 판단·제작은 게이트웨이의 codex 두 번(기획·제작)에 맡긴다(D25).

    후보 화면 일괄 캡처 ─▶ codex 기획(축소본): 화면 하나 고르기·캡션·반려 성찰 ─▶ codex 제작(원본 1장): 홍보 이미지
                       ─▶ 검증 ─▶ BackendPublisher: 성찰 저장, 다듬은 이미지 + 원본 + 지시문 제출(검토 요청)

모델은 만들기만 하고 검증·제출은 여기서 한다. 운영 관리자 자격증명은 게이트웨이로 가지 않는다 —
서버로 가는 것은 데모 화면 캡처와 프롬프트뿐이다.
"""

from __future__ import annotations

import argparse
import json
import os
import re
from dataclasses import replace
from pathlib import Path
from typing import Any, Callable, Sequence

from pydantic import ValidationError

from gole_promotion_agent import drafting, memory, policy
from gole_promotion_agent.captures import Shot, _slug, capture_all, thumbnail
from gole_promotion_agent.gateway_client import Gateway, GatewayError, decode_png, encode_png
from gole_promotion_agent.ledger import RunResult, UsageMeter, close_run, execution_run_key


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

        shots = capture_all(self._run_dir, self._camera, self._routes, self._demo, self._log)
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

        self._remember("PLAN", [shot.route for shot in shots], plan_feedback, plan_guidelines)
        prompt = drafting.plan_prompt(
            drafting.system_prompt(self._publisher.history(policy.HISTORY_LIMIT), self._demo, self._service),
            memory.render(plan_feedback, plan_guidelines),
            drafting.task_prompt([shot.route for shot in shots], service=self._service, subject=subject, diff=diff),
            memory.reflection_prompt(
                self._memory, [item.id for item in plan_feedback], [item.id for item in plan_guidelines],
            ),
        )
        try:
            # 기획 호출(D25): 고르기용 축소본만 싣고 도구 없이 한 번에 답하게 한다 — 무거운 제작 단계에 후보를 끌고 가지 않는다.
            response = self._gateway.call({
                "engine": "codex", "model": self._codex_model, "reasoning": "medium", "prompt": prompt,
                "json_schema": drafting.OUTPUT_SCHEMA, "images": [thumbnail(shot.path) for shot in shots],
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
        polished = [self._make(shot, prompt) for shot, prompt in zip(picked, edit_prompts)]

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

    def _make(self, shot: Shot, prompt: str) -> Path | None:
        """제작 호출(D25): 고른 원본 한 장을 홍보 이미지로. 실패하면 None — 그 자리는 원본을 그대로 올린다."""
        try:
            response = self._gateway.call({
                "engine": "codex", "model": self._codex_model, "prompt": prompt,
                "images": [encode_png(shot.path)], "want_images": True,
            })
            data = decode_png(response["images"][0])
        except (GatewayError, KeyError, IndexError, TypeError, ValueError) as error:
            # 모델 오류가 입력 프롬프트를 인용할 수 있다. 작업 기억 원문은 공개 로그에 싣지 않는다.
            code = f" {error}" if isinstance(error, GatewayError) and re.fullmatch(r"[A-Z_]+", str(error)) else ""
            self._log(f"[promotion-agent] 이미지 제작 실패 {shot.route}, 원본을 올림 ({type(error).__name__}{code})")
            return None
        destination = self._run_dir / "polished" / f"01-{_slug(shot.route)}.png"
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(data)
        return destination


def _edit_parts(
    feedback: Sequence[memory.Feedback], guidelines: Sequence[memory.Guideline], headline: str,
) -> dict[str, str]:
    """제작 호출의 지시문이자 검토 화면에 남는 기록 — 고정 절차 + IMAGE_EDIT 작업 기억 + 문구 + 진행 규칙."""
    parts = memory.render_parts(feedback, guidelines)
    parts["fixed"] = drafting.POLISH_PROMPT + parts["fixed"]
    parts["headline"] = "\n외부 제목 문구 (JSON 데이터):\n" + json.dumps({"headline": headline}, ensure_ascii=False)
    parts["rules"] = drafting.MAKE_RULES
    return parts


def _required(name: str) -> str:
    value = (os.environ.get(name) or "").strip()
    if not value:
        raise ValueError(f"{name}_REQUIRED")
    return value


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
        close_run(publisher, run_dir, meter, RunResult(
            "failed", "ERROR", type(error).__name__, memory_context=drafter.memory_usage, run_key=drafter.run_key,
        ), arguments)
        raise
    return close_run(publisher, run_dir, meter, result, arguments)


if __name__ == "__main__":
    raise SystemExit(main())
