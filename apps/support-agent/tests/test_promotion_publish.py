from datetime import datetime, timedelta, timezone

from gole_promotion_agent import policy
from gole_promotion_agent.ports import ToolCall, Turn
from gole_promotion_agent.runtime import run_publish
from gole_promotion_agent.tools import PublishToolset

NOW = datetime(2026, 9, 30, 12, 0, tzinfo=timezone.utc)


class FakeBoard:
    def __init__(self, approved=(), published=()):
        self.approved = [dict(p) for p in approved]
        self.published_posts = [dict(p) for p in published]
        self.calls: list[str] = []

    def posts(self, status, limit):
        return tuple(self.approved if status == "APPROVED" else self.published_posts)

    def publish(self, post_id):
        self.calls.append(post_id)
        post = next(p for p in self.approved if p["id"] == post_id)
        self.approved.remove(post)
        self.published_posts.append({**post, "publishedAt": NOW.isoformat()})
        return {"id": post_id, "externalPostId": "stub-1"}


def _call(name, **arguments):
    return ToolCall("1", name, arguments)


def _toolset(board):
    return PublishToolset(board, clock=lambda: NOW)


def test_publish_now_rejects_post_that_is_not_approved():
    board = FakeBoard(approved=[{"id": "a", "caption": "승인됨"}])

    outcome = _toolset(board).run(_call("publish_now", post_id="draft-1", reason="r"), {})

    assert outcome.is_error and board.calls == []


def test_publish_now_publishes_approved_post():
    board = FakeBoard(approved=[{"id": "a", "caption": "승인됨"}])
    toolset = _toolset(board)

    outcome = toolset.run(_call("publish_now", post_id="a", reason="오래 기다림"), {})

    assert not outcome.is_error
    assert board.calls == ["a"]
    assert toolset.published == [{"id": "a", "reason": "오래 기다림"}]


def test_publish_now_enforces_per_run_limit():
    board = FakeBoard(approved=[{"id": "a"}, {"id": "b"}])
    toolset = _toolset(board)
    toolset.run(_call("publish_now", post_id="a", reason="r"), {})

    outcome = toolset.run(_call("publish_now", post_id="b", reason="r"), {})

    assert policy.MAX_PUBLISH_PER_RUN == 1
    assert outcome.is_error and board.calls == ["a"]


def test_publish_now_enforces_min_interval_since_last_publish():
    recent = (NOW - timedelta(hours=1)).isoformat()
    board = FakeBoard(approved=[{"id": "a"}], published=[{"id": "old", "publishedAt": recent}])

    outcome = _toolset(board).run(_call("publish_now", post_id="a", reason="r"), {})

    assert outcome.is_error and "간격" in outcome.text
    assert board.calls == []


def test_publish_now_allows_after_interval():
    old = (NOW - timedelta(hours=policy.MIN_PUBLISH_INTERVAL_HOURS + 1)).isoformat()
    board = FakeBoard(approved=[{"id": "a"}], published=[{"id": "old", "publishedAt": old}])

    outcome = _toolset(board).run(_call("publish_now", post_id="a", reason="r"), {})

    assert not outcome.is_error and board.calls == ["a"]


def test_invalid_input_goes_back_to_model_as_error():
    outcome = _toolset(FakeBoard()).run(_call("publish_now", post_id=""), {})

    assert outcome.is_error and "INVALID_TOOL_INPUT" in outcome.text


def test_run_publish_lets_model_retry_after_guard_error():
    """모델이 틀린 id 를 고르면 오류를 보고 고쳐서 다시 부른다."""
    board = FakeBoard(approved=[{"id": "a", "caption": "새 기능"}])
    plan = [
        [("list_approved_posts", {})],
        [("publish_now", {"post_id": "wrong", "reason": "r"})],
        [("publish_now", {"post_id": "a", "reason": "가장 오래 기다림"})],
    ]

    class Scripted:
        def advance(self, transcript):
            step = sum(1 for entry in transcript if entry.get("role") == "assistant")
            if step >= len(plan):
                return Turn("올렸어.", (), "end_turn")
            calls = tuple(ToolCall(f"{step}", n, a) for n, a in plan[step])
            return Turn("", calls, "tool_use")

    seen_tools = []

    def factory(*, system, tools):
        seen_tools.extend(tool["name"] for tool in tools)
        return Scripted()

    result = run_publish(board, factory)

    assert seen_tools == ["list_approved_posts", "list_recent_published", "publish_now"]
    assert board.calls == ["a"]
    assert result.published == ({"id": "a", "reason": "가장 오래 기다림"},)
    assert result.summary == "올렸어."
