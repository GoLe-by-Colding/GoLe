"""Harness: 실행 한도·재개·보존 정리를 소유하고 Brain과 Hands를 조립한다."""

from __future__ import annotations

import json
import shutil
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Mapping, Sequence

from gole_agent_runtime.privacy import private_execution
from gole_promotion_agent import policy
from gole_promotion_agent.brain import build_graph
from gole_promotion_agent.checkpoints import SessionSaver
from gole_promotion_agent.ports import (
    Camera,
    ConversationFactory,
    DraftPublisher,
    ReleaseScanner,
    RouteCatalog,
)
from gole_promotion_agent.session import EphemeralSession, Stage

CANDIDATE_TIMEOUT_SECONDS = 15 * 60


class InactiveRequest(Exception):
    pass


@dataclass(frozen=True)
class CandidateOutcome:
    sha: str
    outcome: str
    resumed: bool
    post_id: str | None = None
    error: str | None = None


@dataclass(frozen=True)
class RunResult:
    reason: str
    candidates: tuple[CandidateOutcome, ...] = field(default_factory=tuple)

    @property
    def failed(self) -> tuple[CandidateOutcome, ...]:
        return tuple(item for item in self.candidates if item.outcome == "failed")


class PromotionHarness:
    def __init__(
        self,
        scanner: ReleaseScanner,
        routes: RouteCatalog,
        camera: Camera,
        publisher: DraftPublisher,
        conversation_factory: ConversationFactory,
        sessions_root: Path,
    ):
        self._scanner = scanner
        self._routes = routes
        self._camera = camera
        self._publisher = publisher
        self._conversations = conversation_factory
        self._root = Path(sessions_root)

    # --- 보존 ---

    def cleanup_expired(self, now: float | None = None) -> int:
        if not self._root.is_dir():
            return 0
        cutoff = (now or time.time()) - policy.RETENTION_DAYS * 86_400
        removed = 0
        for entry in self._root.iterdir():
            if entry.is_dir() and entry.stat().st_mtime < cutoff:
                shutil.rmtree(entry, ignore_errors=True)
                removed += 1
        return removed

    # --- 실행 ---

    @private_execution
    def run(self) -> RunResult:
        self._root.mkdir(parents=True, exist_ok=True)
        self.cleanup_expired()

        pending = self._publisher.pending_count()
        if pending >= policy.MAX_PENDING_REVIEW:
            # 사람 검토가 병목인 설계다. 큐를 더 밀어 넣지 않는다(스펙 D18).
            return RunResult(f"검토 대기 {pending}건으로 생성을 건너뜀")

        candidates = self._scanner.candidates()
        if not candidates:
            return RunResult("새로 홍보할 릴리스가 없음")

        history = self._publisher.history(policy.HISTORY_LIMIT)
        outcomes: list[CandidateOutcome] = []
        try:
            for candidate in candidates:
                outcomes.append(self._run_candidate(candidate.sha, candidate.subject, history))
        finally:
            self._camera.close()
        return RunResult(f"후보 {len(candidates)}건 처리", tuple(outcomes))

    def _run_candidate(
        self, sha: str, subject: str, history: Sequence[Mapping[str, Any]]
    ) -> CandidateOutcome:
        session_dir = self._root / sha
        session_dir.mkdir(parents=True, exist_ok=True)
        saver = SessionSaver(session_dir, sha)
        resumed = saver.has_progress()
        deadline = time.monotonic() + CANDIDATE_TIMEOUT_SECONDS

        def guard() -> None:
            if time.monotonic() >= deadline:
                raise InactiveRequest()

        config = {
            "configurable": {"thread_id": sha, "checkpoint_ns": ""},
            "recursion_limit": policy.MAX_TURNS * 3 + 8,
            "callbacks": [],
        }

        if resumed:
            # 재개할 때는 동결된 시스템 프롬프트를 되살린다 — 사이에 이력이 바뀌었더라도
            # 같은 대화에는 같은 맥락이 실려야 한다(스펙 D18).
            stored = saver.get_tuple(config)
            values = stored.checkpoint.get("channel_values", {}) if stored else {}
            system = values.get("system") or policy.build_system_prompt(history)
            session = EphemeralSession(
                Stage.DRAFTING if values.get("draft") else Stage.EXPLORING
            )
        else:
            system = policy.build_system_prompt(history)
            session = EphemeralSession()
            self._write_manifest(session_dir, sha, subject)

        graph = build_graph(
            self._conversations(system=system),
            self._scanner,
            self._routes,
            self._camera,
            self._publisher,
            session_dir,
            guard,
            session.advance,
        ).compile(checkpointer=saver)

        try:
            if resumed:
                state = graph.invoke(None, config)
            else:
                state = graph.invoke(
                    {
                        "sha": sha,
                        "subject": subject,
                        "system": system,
                        "transcript": [
                            {
                                "role": "user",
                                "text": (
                                    f"방금 배포된 릴리스 {sha} 를 조사해 홍보 초안을 만들지 "
                                    f"판단해. 릴리스 제목은 탐색 단서로만 써: {subject}"
                                ),
                            }
                        ],
                        "turns": 0,
                        "captures": [],
                    },
                    config,
                )
        except InactiveRequest:
            self._append_event(session_dir, "cancelled", {})
            return CandidateOutcome(sha, "failed", resumed, error="TIMEOUT")
        except Exception as error:
            self._append_event(session_dir, "failed", {"type": type(error).__name__})
            return CandidateOutcome(sha, "failed", resumed, error=type(error).__name__)

        outcome = state.get("outcome", "skipped")
        if outcome == "submitted":
            saver.discard()
        self._write_manifest(session_dir, sha, subject, done=outcome)
        self._append_event(session_dir, outcome, {"postId": state.get("post_id")})
        return CandidateOutcome(sha, outcome, resumed, state.get("post_id"))

    # --- 기록 ---

    def _write_manifest(
        self, session_dir: Path, sha: str, subject: str, done: str | None = None
    ) -> None:
        payload = {"sha": sha, "subject": subject, "schema": 1}
        if done:
            payload["done"] = done
        (session_dir / "manifest.json").write_text(
            json.dumps(payload, ensure_ascii=False), encoding="utf-8"
        )

    def _append_event(self, session_dir: Path, kind: str, payload: Mapping[str, Any]) -> None:
        """감사 로그에는 경로와 단계만 남긴다 — 이미지도 캡션 원문도 넣지 않는다."""
        with (session_dir / "session.jsonl").open("a", encoding="utf-8") as handle:
            handle.write(
                json.dumps(
                    {"at": time.strftime("%Y-%m-%dT%H:%M:%S%z"), "type": kind, **payload},
                    ensure_ascii=False,
                )
            )
            handle.write("\n")
