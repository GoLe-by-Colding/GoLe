"""세션 로컬 체크포인트. lease·fencing은 두지 않는다.

영속 워커의 `FencedSaver`는 여러 워커가 같은 job을 두고 경쟁할 때 필요한 장치인데 여기는
일회성 프로세스 하나다. 필요한 것은 영속성이지 분산 조정이 아니다(스펙 D17).
"""

from __future__ import annotations

import base64
import json
import os
from pathlib import Path
from typing import Any, Iterator

from langgraph.checkpoint.base import WRITES_IDX_MAP, BaseCheckpointSaver, CheckpointTuple

from gole_promotion_agent.policy import SHA_PATTERN


class BinaryInCheckpoint(Exception):
    """이미지 바이트가 상태로 새어 들어오는 것을 막는다."""


def reject_binary(value: Any, depth: int = 0) -> None:
    """픽셀은 파일로만 존재해야 한다 — 상태에 실리면 메모리도 디스크도 터진다.

    `gole_brick_filter`가 생성 직후 `source: b""`로 원본을 지우는 규율의 체크포인트판이다.
    """
    if depth > 12:
        return
    if isinstance(value, (bytes, bytearray, memoryview)):
        raise BinaryInCheckpoint("BINARY_IN_CHECKPOINT")
    if isinstance(value, dict):
        for item in value.values():
            reject_binary(item, depth + 1)
        return
    if isinstance(value, (list, tuple, set, frozenset)):
        for item in value:
            reject_binary(item, depth + 1)


def _read_lines(path: Path) -> Iterator[dict[str, Any]]:
    if not path.exists():
        return
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            try:
                yield json.loads(line)
            except json.JSONDecodeError:
                # 중단으로 마지막 줄이 잘린 경우다. 그 줄만 버리고 나머지를 살린다.
                continue


def _append(path: Path, record: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(record, ensure_ascii=False, separators=(",", ":")))
        handle.write("\n")
        handle.flush()
        os.fsync(handle.fileno())


class SessionSaver(BaseCheckpointSaver):
    """릴리스 SHA 하나의 전사와 진행 상태를 append-only JSONL로 남긴다."""

    def __init__(self, session_dir: Path, thread_id: str):
        super().__init__()
        if not SHA_PATTERN.match(thread_id):
            raise ValueError("INVALID_THREAD_ID")
        self.thread_id = thread_id
        self.session_dir = Path(session_dir)
        self._checkpoints = self.session_dir / "checkpoints.jsonl"
        self._writes = self.session_dir / "writes.jsonl"

    # --- 내부 ---

    def _scope(self, config: dict[str, Any]) -> str:
        values = config["configurable"]
        if values["thread_id"] != self.thread_id:
            raise ValueError("THREAD_MISMATCH")
        return values.get("checkpoint_ns", "")

    def _config(self, ns: str, checkpoint_id: str | None) -> dict[str, Any]:
        return {
            "configurable": {
                "thread_id": self.thread_id,
                "checkpoint_ns": ns,
                "checkpoint_id": checkpoint_id,
            }
        }

    def _index(self, ns: str) -> dict[str, dict[str, Any]]:
        rows: dict[str, dict[str, Any]] = {}
        for row in _read_lines(self._checkpoints):
            if row.get("ns") == ns:
                rows[row["id"]] = row
        return rows

    def _pending(self, ns: str, checkpoint_id: str) -> list[tuple[str, str, Any]]:
        collected: dict[tuple[str, int], dict[str, Any]] = {}
        for row in _read_lines(self._writes):
            if row.get("ns") != ns or row.get("checkpoint_id") != checkpoint_id:
                continue
            key = (row["task_id"], row["idx"])
            # 특수 채널은 같은 task 재시도의 최신 값으로 갱신하고, 일반 채널은 첫 값을 지킨다.
            if key in collected and row["channel"] not in WRITES_IDX_MAP:
                continue
            collected[key] = row
        return [
            (
                row["task_id"],
                row["channel"],
                self.serde.loads_typed((row["type"], base64.b64decode(row["b64"]))),
            )
            for _, row in sorted(collected.items())
        ]

    def _tuple(self, ns: str, row: dict[str, Any]) -> CheckpointTuple:
        return CheckpointTuple(
            config=self._config(ns, row["id"]),
            checkpoint=self.serde.loads_typed((row["type"], base64.b64decode(row["b64"]))),
            metadata=self.serde.loads_typed(
                (row["meta_type"], base64.b64decode(row["meta_b64"]))
            ),
            parent_config=self._config(ns, row["parent"]) if row.get("parent") else None,
            pending_writes=self._pending(ns, row["id"]),
        )

    # --- BaseCheckpointSaver ---

    def get_tuple(self, config: dict[str, Any]) -> CheckpointTuple | None:
        ns = self._scope(config)
        rows = self._index(ns)
        if not rows:
            return None
        checkpoint_id = config["configurable"].get("checkpoint_id")
        if checkpoint_id:
            row = rows.get(checkpoint_id)
            return self._tuple(ns, row) if row else None
        # append 순서가 곧 시간 순서다.
        return self._tuple(ns, list(rows.values())[-1])

    def list(self, config, *, filter=None, before=None, limit=None):  # noqa: A003
        ns = "" if config is None else self._scope(config)
        rows = list(self._index(ns).values())
        for row in reversed(rows if limit is None else rows[-limit:]):
            yield self._tuple(ns, row)

    def put(self, config, checkpoint, metadata, new_versions) -> dict[str, Any]:
        ns = self._scope(config)
        reject_binary(checkpoint.get("channel_values", {}))
        kind, data = self.serde.dumps_typed(checkpoint)
        meta_kind, meta_data = self.serde.dumps_typed(metadata)
        _append(
            self._checkpoints,
            {
                "ns": ns,
                "id": checkpoint["id"],
                "parent": config["configurable"].get("checkpoint_id"),
                "type": kind,
                "b64": base64.b64encode(data).decode("ascii"),
                "meta_type": meta_kind,
                "meta_b64": base64.b64encode(meta_data).decode("ascii"),
            },
        )
        return self._config(ns, checkpoint["id"])

    def put_writes(self, config, writes, task_id, task_path="") -> None:
        ns = self._scope(config)
        checkpoint_id = config["configurable"]["checkpoint_id"]
        for index, (channel, value) in enumerate(writes):
            reject_binary(value)
            kind, data = self.serde.dumps_typed(value)
            _append(
                self._writes,
                {
                    "ns": ns,
                    "checkpoint_id": checkpoint_id,
                    "task_id": task_id,
                    "idx": WRITES_IDX_MAP.get(channel, index),
                    "channel": channel,
                    "type": kind,
                    "b64": base64.b64encode(data).decode("ascii"),
                },
            )

    # --- 수명주기 ---

    def has_progress(self) -> bool:
        return self._checkpoints.exists()

    def discard(self) -> None:
        """완주한 세션의 재개 기록을 지운다. 스크린샷과 감사 로그는 남긴다."""
        for path in (self._checkpoints, self._writes):
            path.unlink(missing_ok=True)
