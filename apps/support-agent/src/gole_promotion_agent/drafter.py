"""홍보 초안 한 편을 만든다. 러너 안 데모 스택을 찍고, 판단·다듬기는 게이트웨이의 CLI 에 맡긴다.

    후보 화면 일괄 캡처 ─▶ 게이트웨이 claude: 화면 고르기·캡션 ─▶ 게이트웨이 codex: 고른 화면 다듬기
                       ─▶ 검증 ─▶ BackendPublisher: 다듬은 이미지 + 원본 + 지시문 제출(검토 요청)

모델은 만들기만 하고 검증·제출은 여기서 한다. 운영 관리자 자격증명은 게이트웨이로 가지 않는다 —
서버로 가는 것은 데모 화면 캡처와 프롬프트뿐이다.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Mapping, Sequence

from pydantic import ValidationError

from gole_promotion_agent import drafting, policy
from gole_promotion_agent.gateway_client import Gateway, GatewayError, decode_png, encode_png


@dataclass(frozen=True)
class RunResult:
    """실행 결과. 공개 Actions 로그에는 outcome·code 만 나가고 detail 은 관리자 전용 원장에만 간다(D23)."""

    outcome: str  # submitted · skipped · failed
    code: str  # 원장의 RunReasonCode 와 같은 이름
    detail: str | None = None  # 모델이 쓴 사유·내부 오류 요약. 공개 로그에 찍지 않는다
    post_id: str | None = None


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
        claude_model: str | None = None,
        codex_model: str | None = None,
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
        self._claude_model = claude_model
        self._codex_model = codex_model
        self._log = log

    def run(self) -> RunResult:
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

        try:
            draft = self._choose(shots, subject, diff)
        except GatewayError as error:
            return RunResult("failed", "GATEWAY_FAILED", str(error))
        except (ValidationError, ValueError) as error:
            # pydantic 오류 문자열에는 모델 출력 일부(input_value)가 들어 있다 — 원장에만 남긴다.
            return RunResult("failed", "CHOICE_INVALID", str(error))
        if draft.decision == "skip":
            return RunResult("skipped", "MODEL_SKIPPED", draft.skip_reason)

        picked = [shots[pick.image - 1] for pick in draft.picks]
        polished = [self._polish(shot, index) for index, shot in enumerate(picked, start=1)]

        media_keys = list(self._publisher.upload([path or shot.path for shot, path in zip(picked, polished)]))
        # 다듬은 사진의 원본만 따로 올린다. 검토 화면이 게시 이미지와 나란히 대조한다.
        originals = [shot.path for shot, path in zip(picked, polished) if path is not None]
        original_keys = iter(self._publisher.upload(originals) if originals else ())
        captures = []
        for pick, shot, path in zip(draft.picks, picked, polished):
            capture = {"label": pick.label, "route": shot.route, "capturedAt": shot.captured_at}
            if path is not None:
                capture |= {"originalMediaKey": next(original_keys), "edit": drafting.POLISH_PROMPT}
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
            return [
                Shot(item["route"], out / item["file"], item["captured_at"])
                for item in json.loads(manifest.read_text(encoding="utf-8"))
                if (out / item["file"]).exists()
            ]
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
                [{"route": s.route, "file": s.path.name, "captured_at": s.captured_at} for s in shots],
                ensure_ascii=False,
            ),
            encoding="utf-8",
        )
        return shots

    def _choose(self, shots: Sequence[Shot], subject: str | None, diff: str | None) -> drafting.DraftOutput:
        response = self._gateway.call(
            {
                "engine": "claude",
                "model": self._claude_model,
                "system": drafting.system_prompt(
                    self._publisher.history(policy.HISTORY_LIMIT), self._demo, self._service
                ),
                "prompt": drafting.task_prompt(
                    [shot.route for shot in shots], service=self._service, subject=subject, diff=diff
                ),
                "json_schema": drafting.OUTPUT_SCHEMA,
                "images": [encode_png(shot.path) for shot in shots],
            }
        )
        (self._run_dir / "choice.json").write_text(
            json.dumps(response.get("structured"), ensure_ascii=False, indent=2), encoding="utf-8"
        )
        draft = drafting.DraftOutput.model_validate(response.get("structured") or {})
        if any(pick.image > len(shots) for pick in draft.picks):
            raise ValueError("없는 후보 번호를 골랐다")
        return draft

    def _polish(self, shot: Shot, index: int) -> Path | None:
        """다듬은 이미지 경로. 실패하면 None — 그 자리는 원본을 그대로 올린다."""
        try:
            response = self._gateway.call(
                {
                    "engine": "codex",
                    "model": self._codex_model,
                    "prompt": drafting.POLISH_PROMPT,
                    "images": [encode_png(shot.path)],
                    "want_images": True,
                }
            )
            data = decode_png(response["images"][0])
        except (GatewayError, KeyError, IndexError, ValueError) as error:
            self._log(f"[promotion-agent] 다듬기 실패 {shot.route}, 원본을 올림: {error}")
            return None
        destination = self._run_dir / "polished" / f"{index:02d}-{_slug(shot.route)}.png"
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(data)
        return destination


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


def run_payload(
    result: RunResult,
    calls: Sequence[Mapping[str, Any]],
    *,
    service: bool,
    sha: str | None,
    env: Mapping[str, str],
) -> dict[str, Any]:
    """원장(POST /api/admin/promotion-runs) 한 줄. 모델이 쓴 사유는 detail 에만, 잘라서 담는다."""
    run_id, attempt = env.get("GITHUB_RUN_ID"), env.get("GITHUB_RUN_ATTEMPT") or "1"
    run_key = f"gh-{run_id}-{attempt}" if run_id and run_id.isdigit() and attempt.isdigit() else f"local-{uuid.uuid4().hex[:16]}"
    agent_sha = (env.get("PROMOTION_AGENT_CODE_SHA") or "").lower() or None
    return {
        "runKey": run_key,
        "category": "SERVICE" if service else "FEATURE",
        "sourceCommitSha": None if service else sha,
        "outcome": result.outcome.upper(),
        "reasonCode": result.code,
        "detail": policy.clean_detail(result.detail),
        "promotionPostId": result.post_id,
        "agentSha": agent_sha if agent_sha and policy.AGENT_SHA_PATTERN.fullmatch(agent_sha) else None,
        "runUrl": env.get("PROMOTION_AGENT_RUN_URL") or None,
        "calls": list(calls)[: policy.MAX_RUN_CALLS],
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
    try:
        result = Drafter(
            repo=Path(arguments.repo),
            run_dir=run_dir,
            publisher=publisher,
            gateway=meter,
            camera=camera,
            routes=routes,
            demo=demo,
            service=arguments.service,
            sha=arguments.sha,
            claude_model=os.environ.get("PROMOTION_AGENT_CLAUDE_MODEL") or None,
            codex_model=os.environ.get("PROMOTION_AGENT_CODEX_MODEL") or None,
        ).run()
    except Exception as error:
        # 예외로 끝나도 그때까지 쓴 사용량은 원장에 남긴다. 예외 문구에는 내부 사정이 섞일 수 있어 이름만 담는다.
        _close_run(publisher, run_dir, meter, RunResult("failed", "ERROR", type(error).__name__), arguments)
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
    summary = {k: v for k, v in payload.items() if k != "detail"} | {"totals": meter.totals(), "recorded": recorded}
    (run_dir / "run.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"[promotion-agent] {result.outcome} ({result.code})")
    if not recorded:
        print(f"[promotion-agent] 실행 원장 기록 실패: {status}")
    return 1 if result.outcome == "failed" else 0

if __name__ == "__main__":
    raise SystemExit(main())
