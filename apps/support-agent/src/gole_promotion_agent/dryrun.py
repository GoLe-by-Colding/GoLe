"""유료 호출 없이 전체 흐름을 돌리기 위한 대체 구현(스펙 D16).

이 모듈은 외부 SDK를 import하지 않는다 — 드라이런에서 나가는 경로가 물리적으로 없어야 한다.
"""

from __future__ import annotations

import base64
import json
import re
from pathlib import Path
from typing import Any, Mapping, Sequence

from gole_promotion_agent.ports import ToolCall, Turn

# 1x1 PNG. 실제 촬영 대신 형식만 맞춘 파일을 남긴다.
_PIXEL = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="
)


class ScriptedConversation:
    """고정 순서로 도구를 부르는 가짜 판단.

    대상 SHA를 생성자가 아니라 전사에서 읽는다 — 포트 계약이 "전사의 순수 함수"이고,
    후보마다 다른 대화를 같은 팩토리로 만들 수 있어야 하기 때문이다.
    """

    def __init__(self, route: str = "/"):
        self._route = route

    def _plan_for(self, sha: str) -> list[tuple[str, list[tuple[str, dict[str, Any]]]]]:
        route = self._route
        return [
            ("무엇이 바뀌었는지 본다.", [("read_release_diff", {"sha": sha})]),
            ("찍을 수 있는 화면을 본다.", [("list_routes", {})]),
            (
                "대표 화면을 찍는다.",
                [("capture", {"route": route, "interactions": [], "label": "메인"})],
            ),
            (
                "초안을 낸다.",
                [
                    (
                        "submit_promotion_draft",
                        {
                            "sha": sha,
                            "caption": "드라이런으로 만든 초안이야. 실제로는 나가지 않아.",
                            "screenshot_labels": ["메인"],
                        },
                    )
                ],
            ),
        ]

    def advance(self, transcript: Sequence[Mapping[str, Any]]) -> Turn:
        opening = next(
            (entry["text"] for entry in transcript if entry.get("role") == "user"), ""
        )
        found = re.search(r"[0-9a-f]{40}", opening)
        if found is None:
            return Turn("대상 릴리스를 찾지 못했어.", (), "end_turn")
        plan = self._plan_for(found.group(0))
        step = sum(1 for entry in transcript if entry.get("role") == "assistant")
        if step >= len(plan):
            return Turn("드라이런을 마쳤어.", (), "end_turn")
        text, calls = plan[step]
        return Turn(
            text,
            tuple(
                ToolCall(f"dry-{step}-{index}", name, arguments)
                for index, (name, arguments) in enumerate(calls)
            ),
            "tool_use",
        )


def scripted_conversation_factory(route: str = "/"):
    def factory(*, system: str) -> ScriptedConversation:
        # system 프롬프트는 드라이런에서도 만들어지지만 모델에 보내지 않는다.
        return ScriptedConversation(route)

    return factory


class FakeCamera:
    """브라우저 없이 형식만 맞춘 PNG를 남긴다."""

    def __init__(self, allowed_routes: Sequence[str]):
        self._allowed = frozenset(allowed_routes)
        self.calls: list[tuple[str, tuple[Any, ...]]] = []

    def capture(
        self, route: str, interactions: Sequence[Mapping[str, Any]], destination: Path
    ) -> None:
        if route not in self._allowed:
            raise ValueError("ROUTE_NOT_ALLOWED")
        self.calls.append((route, tuple(json.dumps(i, sort_keys=True) for i in interactions)))
        destination = Path(destination)
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(_PIXEL)

    def close(self) -> None:
        return None


class RecordingPublisher:
    """네트워크를 타지 않고 세션 디렉터리에 기록만 남긴다."""

    def __init__(self, session_root: Path, promoted: Sequence[str] = (), pending: int = 0):
        self._root = Path(session_root)
        self._promoted = set(promoted)
        self._pending = pending
        self._sequence = 0

    def _record(self, action: str, payload: Mapping[str, Any]) -> None:
        self._root.mkdir(parents=True, exist_ok=True)
        with (self._root / "dry-run.jsonl").open("a", encoding="utf-8") as handle:
            handle.write(json.dumps({"action": action, **payload}, ensure_ascii=False))
            handle.write("\n")

    def exists(self, sha: str) -> bool:
        return sha in self._promoted

    def pending_count(self) -> int:
        return self._pending

    def history(self, limit: int) -> tuple[Mapping[str, Any], ...]:
        return ()

    def upload(self, paths: Sequence[Path]) -> tuple[str, ...]:
        keys = tuple(f"dry-run/{Path(path).name}" for path in paths)
        self._record("upload", {"keys": list(keys)})
        return keys

    def create(self, sha: str, caption: str, media_keys: Sequence[str]) -> str:
        self._sequence += 1
        post_id = f"dry-run-{self._sequence}"
        self._record(
            "create",
            {"id": post_id, "sha": sha, "caption": caption, "mediaKeys": list(media_keys)},
        )
        return post_id

    def finalize(self, post_id: str) -> None:
        self._record("finalize", {"id": post_id})
