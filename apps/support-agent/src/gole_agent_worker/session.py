from __future__ import annotations

from langgraph.checkpoint.base import BaseCheckpointSaver, CheckpointTuple, WRITES_IDX_MAP

from gole_agent_worker.model import LeaseLost
from gole_agent_worker.store import Store


class FencedSaver(BaseCheckpointSaver):
    """job lease와 동일 트랜잭션에서 checkpoint/pending writes를 저장한다."""

    def __init__(self, store: Store, job_id: str, fence: int):
        super().__init__()
        self.store, self.job_id, self.fence = store, job_id, fence

    def _scope(self, config):
        values = config["configurable"]
        if values["thread_id"] != self.job_id:
            raise LeaseLost()
        return values.get("checkpoint_ns", "")

    def _config(self, ns, checkpoint_id):
        return {"configurable": {"thread_id": self.job_id, "checkpoint_ns": ns,
                                 "checkpoint_id": checkpoint_id}}

    def get_tuple(self, config):
        ns = self._scope(config)
        checkpoint_id = config["configurable"].get("checkpoint_id")
        with self.store.transaction() as db:
            self.store.assert_lease(db, self.job_id, self.fence)
            query = "SELECT * FROM checkpoints WHERE job_id=? AND ns=?"
            args = [self.job_id, ns]
            if checkpoint_id:
                query += " AND id=?"
                args.append(checkpoint_id)
            row = db.execute(query + " ORDER BY id DESC LIMIT 1", args).fetchone()
            if row is None:
                return None
            writes = db.execute("""SELECT * FROM writes WHERE job_id=? AND ns=? AND checkpoint_id=?
                                 ORDER BY task_id,idx""", (self.job_id, ns, row["id"])).fetchall()
            return CheckpointTuple(
                config=self._config(ns, row["id"]),
                checkpoint=self.serde.loads_typed((row["type"], row["data"])),
                metadata=self.serde.loads_typed((row["metadata_type"], row["metadata"])),
                parent_config=self._config(ns, row["parent"]) if row["parent"] else None,
                pending_writes=[(w["task_id"], w["channel"], self.serde.loads_typed((w["type"], w["data"])))
                                for w in writes],
            )

    def put(self, config, checkpoint, metadata, new_versions):
        ns = self._scope(config)
        kind, data = self.serde.dumps_typed(checkpoint)
        metadata_kind, metadata_data = self.serde.dumps_typed(metadata)
        with self.store.transaction() as db:
            self.store.assert_lease(db, self.job_id, self.fence)
            db.execute("INSERT OR REPLACE INTO checkpoints VALUES(?,?,?,?,?,?,?,?)",
                       (self.job_id, ns, checkpoint["id"], config["configurable"].get("checkpoint_id"),
                        kind, data, metadata_kind, metadata_data))
        return self._config(ns, checkpoint["id"])

    def put_writes(self, config, writes, task_id, task_path=""):
        ns = self._scope(config)
        with self.store.transaction() as db:
            self.store.assert_lease(db, self.job_id, self.fence)
            for index, (channel, value) in enumerate(writes):
                kind, data = self.serde.dumps_typed(value)
                # 특수 채널은 같은 task 재시도의 최신 오류/interrupt로 갱신한다.
                operation = "REPLACE" if channel in WRITES_IDX_MAP else "IGNORE"
                db.execute(f"INSERT OR {operation} INTO writes VALUES(?,?,?,?,?,?,?,?)",
                           (self.job_id, ns, config["configurable"]["checkpoint_id"], task_id,
                            WRITES_IDX_MAP.get(channel, index), channel, kind, data))
