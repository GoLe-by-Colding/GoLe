"""Harness: 실행 한도·재개·보존 정리를 소유하고 Brain과 Hands를 조립한다."""

from __future__ import annotations

import json
import shutil
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Callable, Mapping, Sequence

from gole_agent_runtime.privacy import private_execution
from gole_promotion_agent import policy
from gole_promotion_agent.brain import build_graph
from gole_promotion_agent.checkpoints import SessionSaver
from gole_promotion_agent.ports import (
    Camera,
    ConversationFactory,
    DraftPublisher,
    DraftRequest,
    DraftRequestQueue,
    ReleaseScanner,
    RouteCatalog,
)
from gole_promotion_agent.session import EphemeralSession, Stage

CANDIDATE_TIMEOUT_SECONDS = 15 * 60


class InactiveRequest(Exception):
    pass


def skipped_ledger(sessions_root: Path) -> Callable[[str], bool]:
    """이미 평가해서 건너뛴 커밋인지 판정한다(스펙 D11).

    건너뛴 결과는 백엔드에 남지 않는다 — 초안을 만들지 않았으므로 `/exists` 가 계속 거짓이다.
    그래서 원장이 없으면 같은 커밋이 탐색 창에 남아 있는 동안 매 실행마다 유료로 재평가된다.
    별도 저장소를 두지 않고 세션 디렉터리의 manifest 를 그대로 원장으로 쓴다.

    보존(RETENTION_DAYS)과 탐색 창(MAX_WALK_DAYS)이 같은 7일이라, 원장이 지워질 무렵이면
    그 커밋은 이미 창 밖이다.
    """
    root = Path(sessions_root)

    def is_skipped(sha: str) -> bool:
        manifest = root / sha / "manifest.json"
        try:
            payload = json.loads(manifest.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            return False
        return payload.get("done") == "skipped"

    return is_skipped


def retry_ledger(sessions_root: Path) -> Callable[[], tuple[str, ...]]:
    """다음 실행이 다시 봐야 할 커밋을 모은다(실패·중단).

    스캐너는 이미 홍보한 커밋을 만나면 거기서 walk 를 멈춘다. 그 경계보다 오래된 실패는
    경계에 가려 영영 후보가 되지 못하므로, 세션 디렉터리의 manifest 를 원장 삼아 되살린다.
    보존 기간이 탐색 창과 같아서(RETENTION_DAYS == MAX_WALK_DAYS) 창 밖 커밋은 저절로 빠진다.
    """
    root = Path(sessions_root)
    retryable = {"failed", "deferred"}

    def pending() -> tuple[str, ...]:
        if not root.is_dir():
            return ()
        found: list[str] = []
        for entry in sorted(root.iterdir()):
            if not entry.is_dir():
                continue
            try:
                payload = json.loads((entry / "manifest.json").read_text(encoding="utf-8"))
            except (OSError, ValueError):
                continue
            if payload.get("done") in retryable and isinstance(payload.get("sha"), str):
                found.append(payload["sha"])
        return tuple(found)

    return pending


def _safe_failure_code(error: BaseException) -> str:
    """관리자에게 보일 실패 사유를 고른다(스펙 D20).

    이 패키지가 직접 정한 고정 코드만 통과시키고 나머지는 예외 종류 이름으로 줄인다 —
    예외 원문에는 diff·캡션·파일 경로가 섞일 수 있다.
    """
    text = str(error).strip()
    if text in policy.SAFE_FAILURE_CODES:
        return text
    return type(error).__name__


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
        queue: DraftRequestQueue | None = None,
        pinned_scanner_factory: Callable[[str], ReleaseScanner] | None = None,
    ):
        self._scanner = scanner
        self._routes = routes
        self._camera = camera
        self._publisher = publisher
        self._conversations = conversation_factory
        self._root = Path(sessions_root)
        # 큐가 없으면 지금까지처럼 타이머 발 자동 실행만 한다 — 기존 동작을 바꾸지 않는다.
        self._queue = queue
        self._pinned_scanner_factory = pinned_scanner_factory

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

        # 관리자 요청이 먼저다. 사람이 기다리고 있는 일을 타이머 몫 뒤로 미루지 않는다(D20).
        request = self._queue.claim() if self._queue else None
        if request is not None:
            return self._run_requested(request)

        return self._run_automatic()

    def _run_automatic(self) -> RunResult:
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

    def _run_requested(self, request: DraftRequest) -> RunResult:
        """관리자가 남긴 요청 하나를 처리하고 **반드시 결과를 회신한다**(스펙 D20).

        회신이 빠지면 요청이 lease 만료까지 IN_PROGRESS 로 남고, 관리자 화면에는 영영
        "처리 중"만 보인다 — 조용한 0건과 똑같이 나쁘다. 그래서 성공·실패 어느 쪽이든
        코드를 붙여 돌려준다.
        """
        assert self._queue is not None
        scanner = self._scanner
        if request.source_commit_sha:
            if self._pinned_scanner_factory is None:
                self._queue.fail(request, "PINNED_SCANNER_UNAVAILABLE")
                return RunResult("지정 커밋 실행을 구성하지 못함")
            try:
                scanner = self._pinned_scanner_factory(request.source_commit_sha)
            except Exception:
                self._queue.fail(request, "COMMIT_NOT_FOUND")
                return RunResult("지정한 커밋을 찾지 못함")

        try:
            candidates = scanner.candidates()
        except Exception:
            self._queue.fail(request, "COMMIT_NOT_FOUND")
            return RunResult("지정한 커밋을 찾지 못함")

        if not candidates:
            # 자동 선정 요청인데 후보가 없을 때다. 관리자에게는 사유가 보여야 한다.
            self._queue.fail(request, "NO_CANDIDATES")
            return RunResult("요청을 받았으나 새로 홍보할 릴리스가 없음")

        history = self._publisher.history(policy.HISTORY_LIMIT)
        outcomes: list[CandidateOutcome] = []
        try:
            for candidate in candidates:
                outcomes.append(self._run_candidate(candidate.sha, candidate.subject, history))
        finally:
            self._camera.close()

        submitted = next((item for item in outcomes if item.post_id), None)
        if submitted is not None:
            self._queue.succeed(request, submitted.post_id or "")
        else:
            first = outcomes[0]
            self._queue.fail(request, (first.error or first.outcome or "UNKNOWN")[:64])
        return RunResult(f"요청 처리 — 후보 {len(candidates)}건", tuple(outcomes))

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
            # 실패도 manifest 에 남긴다. 남기지 않으면 다음 실행의 스캐너가 홍보 경계에서 멈출 때
            # 이 커밋이 경계 너머에 가려 영영 후보가 되지 못한다.
            self._write_manifest(session_dir, sha, subject, done="failed")
            self._append_event(session_dir, "cancelled", {})
            return CandidateOutcome(sha, "failed", resumed, error="TIMEOUT")
        except Exception as error:
            reason = _safe_failure_code(error)
            self._write_manifest(session_dir, sha, subject, done="failed")
            self._append_event(session_dir, "failed", {"type": reason})
            return CandidateOutcome(sha, "failed", resumed, error=reason)

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
