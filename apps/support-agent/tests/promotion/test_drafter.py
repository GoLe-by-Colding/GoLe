"""초안 한 편. 모델이 무엇을 내든 검증을 통과한 것만 제출되고, 다듬기 실패는 원본으로 대신한다."""

import base64
import json
from pathlib import Path

import pytest

from gole_promotion_agent import drafting
from gole_promotion_agent.drafter import Drafter, UsageMeter
from gole_promotion_agent.fakes import PIXEL, FakeCamera
from gole_promotion_agent.gateway_client import GatewayError

from .gitrepo import commit

ROUTES = ("/", "/collection", "/search", "/terms")
POLISHED = PIXEL + b"polished"


class FakePublisher:
    def __init__(self, pending=0, exists=False):
        self.pending = pending
        self._exists = exists
        self.created = []
        self.finalized = []
        self.uploaded = []

    def pending_count(self):
        return self.pending

    def exists(self, sha):
        return self._exists

    def history(self, limit):
        return ({"status": "PUBLISHED", "caption": "지난 글"},)

    def upload(self, paths):
        start = sum(len(batch) for batch in self.uploaded)
        self.uploaded.append([Path(p) for p in paths])
        return tuple(f"key-{start + i}" for i, _ in enumerate(paths))

    def create(self, sha, caption, media_keys, details):
        self.created.append((sha, caption, list(media_keys), details))
        return "post-1"

    def finalize(self, post_id):
        self.finalized.append(post_id)


class FakeGateway:
    def __init__(self, choice, polish_fails=False):
        self.choice = choice
        self.polish_fails = polish_fails
        self.requests = []

    def call(self, request):
        self.requests.append(request)
        if request["engine"] == "claude":
            return {"ok": True, "structured": self.choice, "text": "", "images": []}
        if self.polish_fails:
            raise GatewayError("codex 가 이미지를 만들지 않았다")
        return {"ok": True, "images": [{"name": "p.png", "data": base64.b64encode(POLISHED).decode()}]}


DRAFT = {
    "decision": "draft",
    "caption": "갖고 있는 세트와 갖고 싶은 세트를 한곳에",
    "rationale": "컬렉션 화면이 기능을 잘 보여줌",
    "picks": [{"image": 2, "label": "컬렉션 목록"}, {"image": 1, "label": "홈"}],
}


def _drafter(tmp_path, gateway, publisher, **overrides):
    options = dict(
        repo=tmp_path / "repo",
        run_dir=tmp_path / "run",
        publisher=publisher,
        gateway=gateway,
        camera=FakeCamera(ROUTES),
        routes=ROUTES,
        demo=True,
        service=True,
        log=lambda _message: None,
    )
    options.update(overrides)
    return Drafter(**options)


def test_draft_sendsAllPromotionalScreensOnceThenPolishesPicks(tmp_path):
    gateway = FakeGateway(DRAFT)
    publisher = FakePublisher()

    result = _drafter(tmp_path, gateway, publisher).run()

    assert result.outcome == "submitted"
    choose, *polish = gateway.requests
    assert choose["engine"] == "claude" and len(choose["images"]) == 3  # /terms 는 후보가 아니다
    assert "1. /\n2. /collection\n3. /search" in choose["prompt"]
    assert "지난 글" in choose["system"]
    assert [r["engine"] for r in polish] == ["codex", "codex"] and polish[0]["want_images"]
    assert polish[0]["prompt"] == drafting.POLISH_PROMPT

    # 게시 이미지 2장 + 다듬은 사진의 원본 2장을 한 번에 올린다.
    published, originals = publisher.uploaded
    assert [p.parent.name for p in published] == ["polished", "polished"]
    assert [p.parent.name for p in originals] == ["captures", "captures"]
    assert published[0].read_bytes() == POLISHED
    sha, caption, keys, details = publisher.created[0]
    assert sha is None and keys == ["key-0", "key-1"]
    first, second = details["captures"]
    assert first["route"] == "/collection" and first["originalMediaKey"] == "key-2"
    assert first["edit"] == drafting.POLISH_PROMPT
    assert second["route"] == "/" and second["originalMediaKey"] == "key-3"
    assert publisher.finalized == ["post-1"]


def test_polishFailure_fallsBackToOriginal(tmp_path):
    publisher = FakePublisher()

    result = _drafter(tmp_path, FakeGateway(DRAFT, polish_fails=True), publisher).run()

    assert result.outcome == "submitted"
    (uploaded,) = publisher.uploaded  # 원본만 올렸으니 원본 업로드는 따로 없다
    assert [p.parent.name for p in uploaded] == ["captures", "captures"]
    assert all("originalMediaKey" not in c for c in publisher.created[0][3]["captures"])


def test_skip_submitsNothing(tmp_path):
    publisher = FakePublisher()

    result = _drafter(tmp_path, FakeGateway({"decision": "skip", "skip_reason": "볼 게 없음"}), publisher).run()

    assert (result.outcome, result.reason) == ("skipped", "볼 게 없음")
    assert publisher.created == [] and publisher.uploaded == []


@pytest.mark.parametrize(
    "choice",
    [
        {"decision": "draft", "caption": "글"},
        {**DRAFT, "picks": [{"image": 9, "label": "없는 번호"}]},
        {**DRAFT, "picks": [{"image": 1, "label": "a"}, {"image": 1, "label": "b"}]},
    ],
)
def test_contractViolations_fail(tmp_path, choice):
    publisher = FakePublisher()

    result = _drafter(tmp_path, FakeGateway(choice), publisher).run()

    assert result.outcome == "failed"
    assert publisher.created == []


def test_fullReviewQueue_skipsWithoutCapturingOrCalling(tmp_path):
    gateway = FakeGateway(DRAFT)
    camera = FakeCamera(ROUTES)

    result = _drafter(tmp_path, gateway, FakePublisher(pending=5), camera=camera).run()

    assert result.outcome == "skipped"
    assert gateway.requests == [] and camera.calls == []


def test_featureWithoutWebChanges_skipsWithoutCalling(tmp_path, repo):
    sha = commit(repo, "docs: note", web=False)
    gateway = FakeGateway(DRAFT)

    result = _drafter(tmp_path, gateway, FakePublisher(), service=False, sha=sha).run()

    assert result.outcome == "skipped" and gateway.requests == []


def test_choiceIsKeptForTheArtifact(tmp_path):
    _drafter(tmp_path, FakeGateway(DRAFT), FakePublisher()).run()

    assert json.loads((tmp_path / "run" / "choice.json").read_text(encoding="utf-8"))["picks"][0]["image"] == 2


def test_reusedCaptures_skipCameraAndKeepRouteOrder(tmp_path):
    # 릴리스 때 찍어 둔 화면으로 서비스 소개를 쓴다 — 스택 없이 돈다.
    first = FakeGateway(DRAFT)
    _drafter(tmp_path, first, FakePublisher()).run()
    assert json.loads((tmp_path / "run" / "captures" / "manifest.json").read_text(encoding="utf-8"))[1]["route"] == "/collection"

    again = FakeGateway(DRAFT)
    publisher = FakePublisher()
    result = _drafter(tmp_path, again, publisher, camera=None).run()

    assert result.outcome == "submitted"
    assert "1. /\n2. /collection\n3. /search" in again.requests[0]["prompt"]
    assert publisher.created[0][3]["captures"][0]["route"] == "/collection"


def test_reusedCaptures_withoutManifest_fails(tmp_path):
    result = _drafter(tmp_path, FakeGateway(DRAFT), FakePublisher(), camera=None).run()

    assert result.outcome == "failed"


def test_usageMeter_countsFailedCallsToo():
    # 다듬기에 실패한 codex 호출도 토큰을 썼다 — 합계에서 빠지면 비용을 낮게 본다.
    claude = {"engine": "claude", "input_tokens": 100, "cached_input_tokens": 80, "output_tokens": 5, "cost_usd": 0.2}
    codex = {"engine": "codex", "input_tokens": 60, "cached_input_tokens": 50, "output_tokens": 7, "cost_usd": None}

    class Scripted:
        def call(self, request):
            if request["engine"] == "claude":
                return {"ok": True, "usage": claude}
            raise GatewayError("codex 가 이미지를 만들지 않았다", codex)

    meter = UsageMeter(Scripted())
    meter.call({"engine": "claude"})
    with pytest.raises(GatewayError):
        meter.call({"engine": "codex"})

    report = meter.report("submitted", "abc123")
    assert [c["ok"] for c in report["calls"]] == [True, False]
    assert report["totals"]["codex"] == {
        "calls": 1, "cost_usd": None, "input_tokens": 60, "cached_input_tokens": 50, "output_tokens": 7,
    }
    assert report["totals"]["claude"]["cost_usd"] == 0.2
    assert report["agentSha"] == "abc123" and report["outcome"] == "submitted"
