"""홍보 초안 한 편의 주문서를 만들어 지인 서버에 넣는다(promotion-review D29). 러너는 접수까지만 기다린다.

    건너뛰기 판정 ─▶ 작업 기억 조회 ─▶ 후보 화면 일괄 캡처 ─▶ 기획 요청(축소본)·제작 지시문 틀 조립 ─▶ 주문서 submit
    (지인 서버: codex 기획 → 검증·성찰 → codex 제작 → 납품 창구로 원장·초안 제출 — gole_promotion_deliver)

운영 관리자 자격증명은 러너에만 있다. 서버로 가는 것은 데모 화면 캡처와 조립을 끝낸 프롬프트뿐이고, 서버는
납품 창구 하나만 여는 홍보 토큰을 쓴다.
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import re
from dataclasses import replace
from pathlib import Path
from typing import Any, Callable, Sequence

from gole_promotion_agent import drafting, memory, policy
from gole_promotion_agent.captures import capture_all, thumbnail
from gole_promotion_agent.gateway_client import Submitter
from gole_promotion_core import CONTRACT_VERSION, contract, limits
from gole_promotion_core.gateway import GatewayError
from gole_promotion_core.ledger import RunResult, close_run, execution_run_key
from gole_promotion_core.order import ImageStage, Order, Shot


class Drafter:
    def __init__(
        self,
        *,
        repo: Path,
        run_dir: Path,
        publisher: Any,
        gateway: Submitter,
        camera: Any | None,  # None 이면 run_dir/captures 의 지난 캡처를 쓴다
        routes: Sequence[str],
        demo: bool,
        service: bool,
        sha: str | None = None,
        codex_model: str | None = None,
        run_key: str | None = None,
        run_url: str | None = None,
        agent_sha: str | None = None,
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
        self._run_url = run_url
        self._agent_sha = agent_sha
        self._memory = memory.Context()
        self.memory_usage: dict[str, Any] = {"feedbackIds": [], "guidelines": [], "stages": []}
        self._log = log

    def run(self) -> RunResult:
        self.memory_usage = {"feedbackIds": [], "guidelines": [], "stages": []}
        try:
            self._memory = memory.Context.model_validate(
                self._publisher.memory_context("SERVICE" if self._service else "FEATURE", self._routes)
            )
        except Exception as error:  # noqa: BLE001 — 메모리 없이 새 초안을 만들지 않는다
            self._log(f"[promotion-agent] MEMORY_CONTEXT_FAILED ({type(error).__name__})")
            return RunResult("failed", "ERROR", "MEMORY_CONTEXT_FAILED", run_key=self.run_key)
        result = self._draft()
        # 이 자리에서 납품까지 끝났으면(로컬) 기록은 제작 단계까지 더한 납품 쪽이 정본이다.
        context = result.memory_context if result.delivered else self.memory_usage
        return replace(result, memory_context=context, run_key=self.run_key)

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
        if self._publisher.pending_count() >= limits.MAX_PENDING_REVIEW:
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
        # 제작 지시문은 검토 화면에 남는다(백엔드 상한). 문구는 모델이 정하므로 가장 긴 자리로 주문 전에 잰다.
        parts = _edit_parts(image_feedback, image_guidelines, "가" * 41)
        if contract.utf16_length("".join(parts.values())) > memory.MAX_EDIT_PROMPT:
            lengths = {name: contract.utf16_length(text) for name, text in parts.items()}
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
        order = Order(
            contract_version=CONTRACT_VERSION,
            run_key=self.run_key,
            category=category,
            sha=None if self._service else self._sha,
            data_source="DEMO" if self._demo else "PRODUCTION",
            run_url=self._run_url,
            agent_sha=self._agent_sha,
            release_title=subject or "서비스 소개",
            model=self._codex_model,
            # 기획 호출(D25): 고르기용 축소본만 싣고 도구 없이 한 번에 답하게 한다.
            plan={
                "engine": "codex", "model": self._codex_model, "reasoning": "medium", "prompt": prompt,
                "json_schema": contract.OUTPUT_SCHEMA, "images": [thumbnail(shot.path) for shot in shots],
            },
            make_prefix=parts["fixed"] + parts["knowledge"] + parts["procedure"] + parts["episodes"],
            make_rules=parts["rules"],
            shots=[
                Shot(route=shot.route, captured_at=shot.captured_at,
                     data=base64.b64encode(shot.path.read_bytes()).decode("ascii"))
                for shot in shots
            ],
            memory_context=self.memory_usage,
            image_stage=ImageStage(
                feedbackIds=[item.id for item in image_feedback],
                guidelineIds=[item.id for item in image_guidelines],
                guidelines=[item.applied_snapshot() for item in image_guidelines],
            ),
            reflection_feedback_ids=[item.id for item in self._memory.unreflectedFeedback],
        )
        try:
            finished = self._gateway.submit(order)
        except GatewayError as error:
            if re.fullmatch(r"[A-Z_]+", str(error)):
                self._log(f"[promotion-agent] {error}")  # 서버 거절(GATEWAY_BUSY 등) 같은 고정 코드만 공개 로그에 남긴다
            return RunResult("failed", "GATEWAY_FAILED", str(error))
        return finished if finished is not None else RunResult("queued", "QUEUED")


def _edit_parts(
    feedback: Sequence[memory.Feedback], guidelines: Sequence[memory.Guideline], headline: str,
) -> dict[str, str]:
    """제작 호출의 지시문이자 검토 화면에 남는 기록 — 고정 절차 + IMAGE_EDIT 작업 기억 + 문구 + 진행 규칙."""
    parts = memory.render_parts(feedback, guidelines)
    parts["fixed"] = drafting.POLISH_PROMPT + parts["fixed"]
    parts["headline"] = contract.headline_block(headline)
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
    from gole_promotion_agent.hands import AppRouteCatalog, PlaywrightCamera
    from gole_promotion_agent.publisher import BackendPublisher

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
    drafter = Drafter(
        repo=Path(arguments.repo),
        run_dir=run_dir,
        publisher=publisher,
        gateway=gateway_client.from_env(),
        camera=camera,
        routes=routes,
        demo=demo,
        service=arguments.service,
        sha=arguments.sha,
        codex_model=os.environ.get("PROMOTION_AGENT_CODEX_MODEL") or None,
        run_url=os.environ.get("PROMOTION_AGENT_RUN_URL") or None,
        agent_sha=os.environ.get("PROMOTION_AGENT_CODE_SHA") or None,
    )
    try:
        result = drafter.run()
    except Exception as error:
        # 예외로 끝나도 원장에 남긴다. 예외 문구에는 내부 사정이 섞일 수 있어 이름만 담는다.
        close_run(publisher, run_dir, [], RunResult(
            "failed", "ERROR", type(error).__name__, memory_context=drafter.memory_usage, run_key=drafter.run_key,
        ), arguments)
        raise
    return close_run(publisher, run_dir, [], result, arguments)


if __name__ == "__main__":
    raise SystemExit(main())
