from __future__ import annotations

import json
import sqlite3
import time
import uuid
from contextlib import contextmanager
from pathlib import Path

from gole_agent_worker.contracts import Conflict, LeaseLost, NotFound, Purged, Submission, TERMINAL


class Store:
    def __init__(self, path: str, *, clock=time.time, max_attempts=3,
                 lease_seconds=15.0, backoff_seconds=2.0):
        if path == ":memory:" or max_attempts < 1 or lease_seconds <= 0 or backoff_seconds < 0:
            raise ValueError("INVALID_STORE_CONFIGURATION")
        self.path = str(Path(path).absolute())
        self.clock = clock
        self.max_attempts = max_attempts
        self.lease_seconds = lease_seconds
        self.backoff_seconds = backoff_seconds
        Path(self.path).parent.mkdir(parents=True, exist_ok=True)
        with self.connection() as db:
            db.execute("PRAGMA journal_mode=WAL")
            db.executescript("""
                CREATE TABLE IF NOT EXISTS tombstones (
                    caller TEXT NOT NULL, owner TEXT NOT NULL, idempotency_key TEXT NOT NULL,
                    receipt_id TEXT NOT NULL, purged_at REAL NOT NULL,
                    PRIMARY KEY(caller, owner, idempotency_key)
                );
                CREATE TABLE IF NOT EXISTS jobs (
                    id TEXT PRIMARY KEY, caller TEXT NOT NULL, owner TEXT NOT NULL,
                    idempotency_key TEXT NOT NULL, submission TEXT NOT NULL,
                    state TEXT NOT NULL, attempts INTEGER NOT NULL DEFAULT 0,
                    fence INTEGER NOT NULL DEFAULT 0, lease_until REAL NOT NULL DEFAULT 0,
                    available_at REAL NOT NULL, created_at REAL NOT NULL, updated_at REAL NOT NULL,
                    result TEXT NOT NULL DEFAULT '', error TEXT NOT NULL DEFAULT '',
                    UNIQUE(caller, owner, idempotency_key)
                );
                CREATE INDEX IF NOT EXISTS jobs_ready ON jobs(state, available_at);
                CREATE TABLE IF NOT EXISTS events (
                    seq INTEGER PRIMARY KEY AUTOINCREMENT, job_id TEXT NOT NULL,
                    state TEXT NOT NULL, attempt INTEGER NOT NULL, code TEXT NOT NULL,
                    at REAL NOT NULL
                );
                CREATE TABLE IF NOT EXISTS checkpoints (
                    job_id TEXT NOT NULL, ns TEXT NOT NULL, id TEXT NOT NULL,
                    parent TEXT, type TEXT NOT NULL, data BLOB NOT NULL,
                    metadata_type TEXT NOT NULL, metadata BLOB NOT NULL,
                    PRIMARY KEY(job_id, ns, id)
                );
                CREATE TABLE IF NOT EXISTS writes (
                    job_id TEXT NOT NULL, ns TEXT NOT NULL, checkpoint_id TEXT NOT NULL,
                    task_id TEXT NOT NULL, idx INTEGER NOT NULL, channel TEXT NOT NULL,
                    type TEXT NOT NULL, data BLOB NOT NULL,
                    PRIMARY KEY(job_id, ns, checkpoint_id, task_id, idx)
                );
            """)
        Path(self.path).chmod(0o600)

    @contextmanager
    def connection(self):
        db = sqlite3.connect(self.path, timeout=10, isolation_level=None)
        db.row_factory = sqlite3.Row
        try:
            yield db
        finally:
            db.close()

    @contextmanager
    def transaction(self):
        with self.connection() as db:
            db.execute("BEGIN IMMEDIATE")
            try:
                yield db
                db.commit()
            except BaseException:
                db.rollback()
                raise

    def event(self, db, job_id, state, attempt, code=""):
        db.execute("INSERT INTO events(job_id,state,attempt,code,at) VALUES(?,?,?,?,?)",
                   (job_id, state, attempt, code, self.clock()))

    def submit(self, caller: str, submission: Submission):
        canonical = submission.canonical()
        with self.transaction() as db:
            if db.execute("SELECT 1 FROM tombstones WHERE caller=? AND owner=? AND idempotency_key=?",
                          (caller, submission.owner, submission.key)).fetchone():
                raise Purged()
            old = db.execute("SELECT * FROM jobs WHERE caller=? AND owner=? AND idempotency_key=?",
                             (caller, submission.owner, submission.key)).fetchone()
            if old:
                if old["submission"] != canonical:
                    raise Conflict()
                return dict(old)
            now = self.clock()
            job_id = str(uuid.uuid4())
            db.execute("""INSERT INTO jobs(id,caller,owner,idempotency_key,submission,state,
                       available_at,created_at,updated_at) VALUES(?,?,?,?,?,'QUEUED',?,?,?)""",
                       (job_id, caller, submission.owner, submission.key, canonical, now, now, now))
            self.event(db, job_id, "QUEUED", 0)
            return dict(db.execute("SELECT * FROM jobs WHERE id=?", (job_id,)).fetchone())

    def get(self, caller, owner, job_id):
        with self.connection() as db:
            row = db.execute("SELECT * FROM jobs WHERE id=? AND caller=? AND owner=?",
                             (job_id, caller, owner)).fetchone()
            if row is None:
                raise NotFound()
            return dict(row)

    def cancel(self, caller, owner, job_id):
        with self.transaction() as db:
            row = db.execute("SELECT * FROM jobs WHERE id=? AND caller=? AND owner=?",
                             (job_id, caller, owner)).fetchone()
            if row is None:
                raise NotFound()
            if row["state"] not in TERMINAL:
                db.execute("UPDATE jobs SET state='CANCELLED', fence=fence+1, lease_until=0, updated_at=? WHERE id=?",
                           (self.clock(), job_id))
                self.event(db, job_id, "CANCELLED", row["attempts"])
            return dict(db.execute("SELECT * FROM jobs WHERE id=?", (job_id,)).fetchone())

    def _retry(self, db, row, code, retryable):
        now = self.clock()
        state = "RETRY_WAIT" if retryable and row["attempts"] < self.max_attempts else "FAILED"
        delay = min(60.0, self.backoff_seconds * (2 ** min(row["attempts"] - 1, 20)))
        db.execute("UPDATE jobs SET state=?, error=?, available_at=?, lease_until=0, updated_at=? WHERE id=?",
                   (state, code, now + delay, now, row["id"]))
        self.event(db, row["id"], state, row["attempts"], code)

    def claim(self):
        with self.transaction() as db:
            now = self.clock()
            expired = db.execute("SELECT * FROM jobs WHERE state='RUNNING' AND lease_until<=?", (now,)).fetchall()
            for row in expired:
                self._retry(db, row, "LEASE_EXPIRED", True)
            now = self.clock()
            row = db.execute("""SELECT * FROM jobs WHERE state IN ('QUEUED','RETRY_WAIT')
                             AND available_at<=? ORDER BY created_at,id LIMIT 1""", (now,)).fetchone()
            if row is None:
                return None
            db.execute("""UPDATE jobs SET state='RUNNING', attempts=attempts+1, fence=fence+1,
                       lease_until=?,updated_at=?,error='' WHERE id=?""",
                       (now + self.lease_seconds, now, row["id"]))
            self.event(db, row["id"], "RUNNING", row["attempts"] + 1)
            return dict(db.execute("SELECT * FROM jobs WHERE id=?", (row["id"],)).fetchone())

    def assert_lease(self, db, job_id, fence):
        row = db.execute("SELECT * FROM jobs WHERE id=?", (job_id,)).fetchone()
        if (row is None or row["state"] != "RUNNING" or row["fence"] != fence
                or row["lease_until"] <= self.clock()):
            raise LeaseLost()
        return row

    def heartbeat(self, job_id, fence):
        with self.transaction() as db:
            self.assert_lease(db, job_id, fence)
            db.execute("UPDATE jobs SET lease_until=? WHERE id=?",
                       (self.clock() + self.lease_seconds, job_id))

    def finish(self, job_id, fence, result):
        encoded = json.dumps(result, ensure_ascii=False, allow_nan=False)
        if len(encoded.encode()) > 16_000:
            raise ValueError("RESULT_TOO_LARGE")
        with self.transaction() as db:
            row = self.assert_lease(db, job_id, fence)
            db.execute("UPDATE jobs SET state='SUCCEEDED',result=?,lease_until=0,updated_at=? WHERE id=?",
                       (encoded, self.clock(), job_id))
            self.event(db, job_id, "SUCCEEDED", row["attempts"])

    def fail(self, job_id, fence, *, retryable: bool, code: str):
        if code not in {"PROVIDER_TRANSIENT", "PROVIDER_FAILED", "EXECUTION_TIMEOUT"}:
            raise ValueError("INVALID_ERROR_CODE")
        with self.transaction() as db:
            row = self.assert_lease(db, job_id, fence)
            self._retry(db, row, code, retryable)

    def purge(self, caller, owner, key):
        # 원문 없는 영속 파기 원장: Mongo rollback 뒤 같은 요청을 다시 호출할 수 있다.
        with self.transaction() as db:
            args = (caller, owner, key)
            existing = db.execute("SELECT * FROM tombstones WHERE caller=? AND owner=? AND idempotency_key=?",
                                  args).fetchone()
            if existing:
                return dict(existing)
            job = db.execute("SELECT id FROM jobs WHERE caller=? AND owner=? AND idempotency_key=?", args).fetchone()
            if not job and db.execute("SELECT 1 FROM jobs WHERE caller=? AND idempotency_key=?",
                                      (caller, key)).fetchone():
                raise NotFound()
            if job:
                for table in ("checkpoints", "writes", "events"):
                    db.execute(f"DELETE FROM {table} WHERE job_id=?", (job["id"],))
                db.execute("DELETE FROM jobs WHERE id=?", (job["id"],))
            db.execute("INSERT INTO tombstones VALUES(?,?,?,?,?)",
                       (*args, str(uuid.uuid4()), self.clock()))
            return dict(db.execute("SELECT * FROM tombstones WHERE caller=? AND owner=? AND idempotency_key=?",
                                   args).fetchone())
