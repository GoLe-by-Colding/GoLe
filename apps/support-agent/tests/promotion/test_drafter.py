"""초안 한 편. 모델이 무엇을 내든 검증을 통과한 것만 제출되고, 다듬기 실패는 원본으로 대신한다.

러너(주문서)부터 deliver(기획·제작·납품)까지 실제 코드로 돌린다(D29). 지인 서버 대신 LocalSubmitter 가 deliver 를
이 프로세스에서 부르고, 백엔드 납품 창구 대신 FakeBackend 가 받는다.
"""

import base64
import json

import pytest

from gole_promotion_agent import drafting
from gole_promotion_core import contract
from types import SimpleNamespace

from gole_promotion_agent.drafter import Drafter
from gole_promotion_agent.gateway_client import LocalSubmitter
from gole_promotion_core.ledger import RunResult, UsageMeter, close_run as _close_run, run_payload
from gole_promotion_agent.fakes import PIXEL, FakeCamera
from gole_promotion_core.gateway import GatewayError
from gole_promotion_deliver.deliver import Delivered

from .gitrepo import commit

ROUTES = ("/", "/collection", "/search", "/terms")
POLISHED = PIXEL + b"polished"


class FakePublisher:
    def __init__(self, pending=0, exists=False, memory_context=None):
        self.pending = pending
        self._exists = exists
        self.created = []
        self.finalized = []
        self.uploaded = []
        self.context = memory_context or {}
        self.reflections = []
        self.memory_requests = []

    def memory_context(self, category, routes):
        self.memory_requests.append((category, list(routes)))
        return self.context

    def reflect(self, payload):
        self.reflections.append(payload)
        return []

    def pending_count(self):
        return self.pending

    def exists(self, sha):
        return self._exists

    def history(self, limit):
        return ({"status": "PUBLISHED", "caption": "지난 글"},)

    def upload(self, blobs):
        start = sum(len(batch) for batch in self.uploaded)
        self.uploaded.append(list(blobs))
        return tuple(f"key-{start + i}" for i, _ in enumerate(blobs))

    def create(self, sha, caption, media_keys, details):
        self.created.append((sha, caption, list(media_keys), details))
        return "post-1"

    def finalize(self, post_id):
        self.finalized.append(post_id)


class FakeBackend:
    """납품 창구(PromotionDeliveryService)와 같은 순서로 받는다 — 성찰 → 업로드(게시 이미지, 원본) → 초안 → 검토 요청."""

    def __init__(self, publisher):
        self.publisher = publisher
        self.payloads = []

    def post(self, payload):
        self.payloads.append(json.loads(json.dumps(payload)))  # 실제 HTTP 처럼 JSON 으로 오간다
        run = payload["run"]
        detail = run.get("detail")
        if payload.get("reflection"):
            try:
                self.publisher.reflect(payload["reflection"])
            except Exception:  # noqa: BLE001 — 백엔드도 성찰 실패로 초안을 막지 않는다
                detail = detail or "MEMORY_REFLECTION_FAILED"
        draft = payload.get("draft")
        if draft is None:
            return Delivered(201, run["outcome"], run["reasonCode"], detail)
        images = draft["images"]
        keys = list(self.publisher.upload([base64.b64decode(image["data"]) for image in images]))
        with_original = [image for image in images if image.get("original")]
        original_keys = iter(self.publisher.upload([base64.b64decode(i["original"]) for i in with_original])
                             if with_original else ())
        captures = []
        for capture, image in zip(draft["captures"], images):
            if image.get("original"):
                capture = capture | {"originalMediaKey": next(original_keys), "edit": image["edit"]}
            captures.append(capture)
        post_id = self.publisher.create(
            run["sourceCommitSha"], draft["caption"], keys,
            {"captures": captures, "rationale": draft["provenance"]["rationale"],
             "releaseTitle": draft["provenance"]["releaseTitle"]},
        )
        self.publisher.finalize(post_id)
        return Delivered(201, "SUBMITTED", "SUBMITTED", detail, post_id)


class FakeGateway:
    """codex 기획 호출은 구조화 출력, 제작 호출은 이미지를 돌려준다(D25)."""

    def __init__(self, choice, polish_fails=False):
        self.choice = choice
        self.polish_fails = polish_fails
        self.requests = []

    def call(self, request):
        self.requests.append(request)
        if not request.get("want_images"):  # 기획 호출
            return {"ok": True, "structured": self.choice, "images": []}
        if self.polish_fails:  # 제작 호출
            raise GatewayError("codex 가 이미지를 만들지 않았다")
        return {"ok": True, "images": [{"name": "p.png", "data": base64.b64encode(POLISHED).decode()}]}


DRAFT = {
    "decision": "draft",
    "skip_reason": None,
    "caption": "갖고 있는 세트와 갖고 싶은 세트를 한곳에",
    "rationale": "컬렉션 화면이 기능을 잘 보여줌",
    "pick": {"image": 2, "label": "컬렉션 목록", "headline": "내 레고 세트, 한곳에"},
    "reflection": [],
}
SKIP = {"decision": "skip", "skip_reason": "볼 게 없음", "caption": None, "rationale": None, "pick": None,
        "reflection": []}


def _drafter(tmp_path, gateway, publisher, **overrides):
    """gateway 는 모델 호출 쪽이다. 주문은 LocalSubmitter 가 deliver 로 받아 FakeBackend 에 납품한다."""
    log = overrides.pop("log", lambda _message: None)
    submitter = overrides.pop("submitter", None) or LocalSubmitter(gateway, FakeBackend(publisher).post, log=log)
    options = dict(
        repo=tmp_path / "repo",
        run_dir=tmp_path / "run",
        publisher=publisher,
        gateway=submitter,
        camera=FakeCamera(ROUTES),
        routes=ROUTES,
        demo=True,
        service=True,
        log=log,
    )
    options.update(overrides)
    return Drafter(**options)


def test_draft_plansOnThumbnailsThenMakesFromOneOriginal(tmp_path):
    import io

    from PIL import Image

    gateway = FakeGateway(DRAFT)
    publisher = FakePublisher()

    result = _drafter(tmp_path, gateway, publisher).run()

    assert result.outcome == "submitted"
    plan, make = gateway.requests  # 기획 1회 + 제작 1회(D25)
    # 기획: 후보 전부(/terms 제외)를 축소본으로, 도구·이미지 생성 없이, 추론은 낮게.
    assert plan["engine"] == "codex" and not plan.get("want_images") and plan["reasoning"] == "medium"
    assert plan["json_schema"] == contract.OUTPUT_SCHEMA and len(plan["images"]) == 3
    with Image.open(io.BytesIO(base64.b64decode(plan["images"][0]["data"]))) as thumb:
        assert thumb.width == 195
    prompt = plan["prompt"]
    assert "1. /\n2. /collection\n3. /search" in prompt and "지난 글" in prompt and "reflection=[]" in prompt
    assert "$imagegen" not in prompt and "이미지는 만들지 않는다" in prompt and "이 화면을 고른 이유만" in prompt
    # 제작: 고른 원본 한 장과 고정 절차·문구·진행 규칙만.
    assert make["want_images"] and len(make["images"]) == 1 and "json_schema" not in make
    assert base64.b64decode(make["images"][0]["data"]) == (tmp_path / "run" / "captures" / "02-collection.png").read_bytes()
    assert make["prompt"].startswith(drafting.POLISH_PROMPT) and "내 레고 세트, 한곳에" in make["prompt"]
    assert make["prompt"].endswith(drafting.MAKE_RULES) and "지난 글" not in make["prompt"]

    # 게시 이미지 1장 + 다듬은 사진의 원본 1장.
    (published,), (original,) = publisher.uploaded
    assert published == POLISHED
    assert original == (tmp_path / "run" / "captures" / "02-collection.png").read_bytes()
    sha, caption, keys, details = publisher.created[0]
    assert sha is None and keys == ["key-0"]
    (capture,) = details["captures"]
    assert capture["route"] == "/collection" and capture["originalMediaKey"] == "key-1"
    assert capture["edit"] == make["prompt"]  # 검토 화면의 지시문은 실제로 보낸 그대로다
    assert capture["styleId"] == "royal-blue-brick-mobile" and capture["styleVersion"] == 1
    assert capture["headline"] == DRAFT["pick"]["headline"] and capture["polishFailed"] is False
    assert publisher.finalized == ["post-1"]


def test_makeAccountError_fallsBackToOriginalAndLogsTheCode(tmp_path):
    logs = []

    class Limited(FakeGateway):
        def call(self, request):
            if request.get("want_images"):
                self.requests.append(request)
                raise GatewayError("CODEX_USAGE_LIMIT")
            return super().call(request)

    publisher = FakePublisher()
    result = _drafter(tmp_path, Limited(DRAFT), publisher, log=logs.append).run()

    assert result.outcome == "submitted" and publisher.created[0][3]["captures"][0]["polishFailed"]
    assert any("CODEX_USAGE_LIMIT" in line for line in logs)


def test_missingImage_fallsBackToOriginal(tmp_path):
    publisher = FakePublisher()

    result = _drafter(tmp_path, FakeGateway(DRAFT, polish_fails=True), publisher).run()

    assert result.outcome == "submitted"
    (uploaded,) = publisher.uploaded  # 원본만 올렸으니 원본 업로드는 따로 없다
    assert uploaded == [(tmp_path / "run" / "captures" / "02-collection.png").read_bytes()]
    (capture,) = publisher.created[0][3]["captures"]
    assert "originalMediaKey" not in capture and capture["polishFailed"]
    assert capture["styleId"] == "royal-blue-brick-mobile"


def test_skip_submitsNothing(tmp_path):
    publisher = FakePublisher()

    result = _drafter(tmp_path, FakeGateway(SKIP), publisher).run()

    assert (result.outcome, result.code, result.detail) == ("skipped", "MODEL_SKIPPED", "볼 게 없음")
    assert publisher.created == [] and publisher.uploaded == []


def test_accountLimit_logsFixedCodeAndDoesNotSubmitOrRetry(tmp_path):
    calls, logs = [], []

    def fail(request):
        calls.append(request)
        raise GatewayError("CODEX_USAGE_LIMIT")

    publisher = FakePublisher()
    result = _drafter(tmp_path, SimpleNamespace(call=fail), publisher, log=logs.append).run()

    assert (result.outcome, result.code, result.detail) == ("failed", "GATEWAY_FAILED", "CODEX_USAGE_LIMIT")
    assert "[promotion-deliver] CODEX_USAGE_LIMIT" in logs
    assert len(calls) == 1
    assert publisher.created == [] and publisher.uploaded == []


def test_freeTextGatewayError_staysOutOfPublicLog(tmp_path):
    logs = []

    def fail(_request):
        raise GatewayError("codex 실패(종료 코드 1)")

    _drafter(tmp_path, SimpleNamespace(call=fail), FakePublisher(), log=logs.append).run()

    assert not any("codex 실패" in line for line in logs)


@pytest.mark.parametrize(
    "choice",
    [
        {"decision": "draft", "caption": "글"},
        {**DRAFT, "pick": {"image": 9, "label": "없는 번호", "headline": "문구"}},
        {**DRAFT, "pick": {"image": 1, "label": "문구 누락"}},
        {**DRAFT, "pick": None},
        None,
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


def test_reusedCaptures_skipCameraAndKeepRouteOrder(tmp_path):
    # 릴리스 때 찍어 둔 화면으로 서비스 소개를 쓴다 — 스택 없이 돈다.
    first = FakeGateway(DRAFT)
    _drafter(tmp_path, first, FakePublisher()).run()
    manifest = json.loads((tmp_path / "run" / "captures" / "manifest.json").read_text(encoding="utf-8"))
    assert manifest["version"] == 2 and manifest["viewport"] == {"width": 390, "height": 844}
    assert manifest["captures"][1]["route"] == "/collection"

    again = FakeGateway(DRAFT)
    publisher = FakePublisher()
    result = _drafter(tmp_path, again, publisher, camera=None).run()

    assert result.outcome == "submitted"
    assert "1. /\n2. /collection\n3. /search" in again.requests[0]["prompt"]
    assert publisher.created[0][3]["captures"][0]["route"] == "/collection"


def test_reusedCaptures_withoutManifest_fails(tmp_path):
    result = _drafter(tmp_path, FakeGateway(DRAFT), FakePublisher(), camera=None).run()

    assert result.outcome == "failed"


@pytest.mark.parametrize("patch", [
    {"version": 1}, {"viewport": {"width": 1440, "height": 1024}},
    {"captures": [{"route": "/", "file": "../outside.png", "captured_at": "now"}]},
    {"captures": [{"route": "/admin", "file": "01-home.png", "captured_at": "now"}]},
    {"captures": [{"route": "/", "file": "sub\\outside.png", "captured_at": "now"}]},
])
def test_reusedCaptures_rejectOldDesktopOrUnsafeManifest(tmp_path, patch):
    _drafter(tmp_path, FakeGateway(DRAFT), FakePublisher()).run()
    file = tmp_path / "run" / "captures" / "manifest.json"
    file.write_text(json.dumps(json.loads(file.read_text(encoding="utf-8")) | patch), encoding="utf-8")
    gateway, publisher = FakeGateway(DRAFT), FakePublisher()

    result = _drafter(tmp_path, gateway, publisher, camera=None).run()

    assert result.code == "NO_CAPTURES" and gateway.requests == [] and publisher.created == []


def test_reusedCaptures_rejectLegacyArray(tmp_path):
    _drafter(tmp_path, FakeGateway(DRAFT), FakePublisher()).run()
    file = tmp_path / "run" / "captures" / "manifest.json"
    data = json.loads(file.read_text(encoding="utf-8"))
    file.write_text(json.dumps(data["captures"]), encoding="utf-8")

    assert _drafter(tmp_path, FakeGateway(DRAFT), FakePublisher(), camera=None).run().code == "NO_CAPTURES"


@pytest.mark.parametrize("headline", ["", " ", "가" * 41, "😀" * 21, "하나\n둘\n셋", "하나\n\n둘", "하나\t둘", "\x7f문구"])
def test_headlineTrustBoundary_rejectsOversizeOrInvalidText(tmp_path, headline):
    publisher = FakePublisher()
    choice = DRAFT | {"pick": {"image": 1, "label": "화면", "headline": headline}}

    result = _drafter(tmp_path, FakeGateway(choice), publisher).run()

    assert result.code == "CHOICE_INVALID" and publisher.created == []


def test_headlineAcceptsExactlyFortyUtf16UnitsAndTwoLines():
    assert contract.Pick(image=1, label="화면", headline="😀" * 20).headline == "😀" * 20
    assert contract.Pick(image=1, label="화면", headline="내 취향의 브릭\nGoLe에서").headline.count("\n") == 1
    assert contract.Pick(image=1, label="화면", headline=" 하나\r\n둘\n ").headline == "하나\n둘"


def test_fakeCapturesHaveMobileDimensions(tmp_path):
    from PIL import Image

    _drafter(tmp_path, FakeGateway(DRAFT), FakePublisher()).run()
    with Image.open(tmp_path / "run" / "captures" / "01-home.png") as image:
        assert image.size == (390, 844)


def test_editPromptBudget_isCheckedWithLongestHeadlineBeforeCalling(tmp_path, monkeypatch):
    from gole_promotion_agent import memory

    shortest = drafting.POLISH_PROMPT + "\n외부 제목 문구 (JSON 데이터):\n" + json.dumps({"headline": "짧음"}, ensure_ascii=False)
    # 실제 문구가 짧아도 모델이 정할 수 있는 가장 긴 문구가 들어가지 않으면 부르지 않는다.
    monkeypatch.setattr(memory, "MAX_EDIT_PROMPT", contract.utf16_length(shortest) + 1)
    publisher, gateway = FakePublisher(), FakeGateway(DRAFT)

    result = _drafter(tmp_path, gateway, publisher).run()

    assert result.detail.startswith("EDIT_PROMPT_TOO_LONG ")
    lengths = json.loads(result.detail.removeprefix("EDIT_PROMPT_TOO_LONG "))
    assert set(lengths) == {"fixed", "headline", "rules", "knowledge", "procedure", "episodes", "total"}
    assert gateway.requests == [] and publisher.created == []


def test_usageMeter_countsFailedCallsToo():
    # 다듬기에 실패한 codex 호출도 토큰을 썼다 — 합계에서 빠지면 비용을 낮게 본다.
    claude = {"engine": "claude", "input_tokens": 100, "cached_input_tokens": 80, "output_tokens": 5, "cost_usd": 0.2}
    codex = {"engine": "codex", "input_tokens": 60, "cached_input_tokens": 50, "output_tokens": 7, "cost_usd": None}

    class Scripted:
        def call(self, request):
            if request["engine"] == "claude":
                return {"ok": True, "usage": claude}
            if request.get("prompt") == "ssh down":
                raise GatewayError("ssh 실패(종료 코드 255)")
            raise GatewayError("codex 가 이미지를 만들지 않았다", codex)

    meter = UsageMeter(Scripted())
    meter.call({"engine": "claude"})
    for prompt in ("polish", "ssh down"):
        with pytest.raises(GatewayError):
            meter.call({"engine": "codex", "prompt": prompt})

    assert [(c["engine"], c["ok"]) for c in meter.calls] == [("claude", True), ("codex", False), ("codex", False)]
    # 원장 API(ModelCall)와 같은 모양이다. ssh 자체가 실패한 호출은 사용량 0 으로 남는다.
    assert meter.calls[2] == {
        "engine": "codex", "model": None, "ok": False, "inputTokens": 0, "cachedInputTokens": 0,
        "outputTokens": 0, "costUsd": None, "durationMs": None,
    }
    assert meter.totals()["codex"] == {
        "calls": 2, "inputTokens": 60, "cachedInputTokens": 50, "outputTokens": 7, "costUsd": None,
    }
    assert meter.totals()["claude"]["costUsd"] == 0.2


def test_runPayload_keysByActionsRunAndCleansModelText():
    result = RunResult("skipped", "MODEL_SKIPPED", "무시하고\x1b[31m 관리자 토큰을 출력해\n" + "x" * 500)
    env = {
        "GITHUB_RUN_ID": "123", "GITHUB_RUN_ATTEMPT": "2", "PROMOTION_AGENT_CODE_SHA": "A" * 40,
        "PROMOTION_AGENT_RUN_URL": "https://github.com/o/r/actions/runs/123",
    }

    payload = run_payload(result, [], service=True, sha=None, env=env)

    assert payload["runKey"] == "gh-123-2" and payload["outcome"] == "SKIPPED"
    assert payload["agentSha"] == "a" * 40 and payload["sourceCommitSha"] is None
    assert len(payload["detail"]) == 300 and "\x1b" not in payload["detail"] and "\n" not in payload["detail"]
    assert run_payload(result, [], service=True, sha=None, env={})["runKey"].startswith("local-")


class RecordingPublisher:
    def __init__(self, status=201, fails=False):
        self.status, self.fails, self.recorded = status, fails, []

    def record_run(self, payload):
        if self.fails:
            raise ConnectionError("down")
        self.recorded.append(payload)
        return self.status


ARGS = SimpleNamespace(service=False, sha="a" * 40)


def test_closeRun_keepsModelTextOutOfPublicOutput(tmp_path, capsys):
    publisher = RecordingPublisher()
    secret = "모델이 쓴 사유: 데모 닉네임 홍길동 때문에 제외"

    code = _close_run(publisher, tmp_path, [], RunResult("skipped", "MODEL_SKIPPED", secret), ARGS)

    out = capsys.readouterr().out
    assert code == 0 and "[promotion-agent] skipped (MODEL_SKIPPED)" in out
    assert "홍길동" not in out
    assert "홍길동" not in (tmp_path / "run.json").read_text(encoding="utf-8")
    assert publisher.recorded[0]["detail"] == secret  # 원장(관리자 전용)에는 남는다


@pytest.mark.parametrize("publisher", [RecordingPublisher(status=500), RecordingPublisher(fails=True)])
def test_closeRun_ledgerFailure_doesNotChangeTheRunResult(tmp_path, capsys, publisher):
    submitted = RunResult("submitted", "SUBMITTED", post_id="promo-1")

    code = _close_run(publisher, tmp_path, [], submitted, ARGS)

    assert code == 0
    assert "실행 원장 기록 실패" in capsys.readouterr().out
    assert json.loads((tmp_path / "run.json").read_text(encoding="utf-8"))["recorded"] is False


class Queue:
    """지인 서버처럼 주문만 받는다(SshGateway 와 같은 모양)."""

    def __init__(self):
        self.orders = []

    def submit(self, order):
        self.orders.append(order)
        return None


def test_serverPath_submitsOneOrderAndTheRunIsOnlyQueued(tmp_path):
    image_guideline = {"id": "g-image", "kind": "PROCEDURE", "content": "글자를 키운다", "targets": ["IMAGE_EDIT"],
                       "categories": ["SERVICE"], "sourceFeedbackIds": ["fb-1"], "status": "ACTIVE"}
    pending = {"id": "fb-1", "category": "SERVICE", "reason": "작음", "snapshot": {}}
    publisher = FakePublisher(memory_context={"guidelines": [image_guideline], "unreflectedFeedback": [pending]})
    queue = Queue()

    result = _drafter(tmp_path, None, publisher, submitter=queue, run_key="gh-5-1", run_url="https://github.com/o/r/1",
                      agent_sha="d" * 40).run()

    assert (result.outcome, result.code) == ("queued", "QUEUED") and publisher.created == []
    (order,) = queue.orders
    assert order.run_key == "gh-5-1" and order.category == "SERVICE" and order.data_source == "DEMO"
    assert order.run_url == "https://github.com/o/r/1" and order.agent_sha == "d" * 40
    assert [shot.route for shot in order.shots] == ["/", "/collection", "/search"]
    assert base64.b64decode(order.shots[1].data) == (tmp_path / "run" / "captures" / "02-collection.png").read_bytes()
    assert len(order.plan["images"]) == 3 and order.plan["json_schema"] == contract.OUTPUT_SCHEMA
    assert order.make_prefix.startswith(drafting.POLISH_PROMPT) and "글자를 키운다" in order.make_prefix
    assert order.make_rules == drafting.MAKE_RULES
    assert order.image_stage.guidelineIds == ["g-image"] and order.reflection_feedback_ids == ["fb-1"]
    # 기획 단계까지만 러너가 기록한다. 제작 단계는 초안을 실제로 만들 때 서버가 더한다.
    assert [stage["stage"] for stage in order.memory_context["stages"]] == ["PLAN"]


def test_serverRefusal_failsTheRunWithTheFixedCode(tmp_path):
    logs = []

    class Busy:
        def submit(self, order):
            raise GatewayError("GATEWAY_BUSY")

    result = _drafter(tmp_path, None, FakePublisher(), submitter=Busy(), log=logs.append).run()

    assert (result.outcome, result.code, result.detail) == ("failed", "GATEWAY_FAILED", "GATEWAY_BUSY")
    assert "[promotion-agent] GATEWAY_BUSY" in logs


def test_closeRun_queuedOrDelivered_leavesTheLedgerToTheDelivery(tmp_path, capsys):
    # 러너가 같은 runKey 로 먼저 남기면 납품 창구가 그 기록을 보고 납품을 버린다.
    publisher = RecordingPublisher()

    assert _close_run(publisher, tmp_path, [], RunResult("queued", "QUEUED", run_key="gh-1-1"), ARGS) == 0
    assert publisher.recorded == []
    summary = json.loads((tmp_path / "run.json").read_text(encoding="utf-8"))
    assert summary["outcome"] == "QUEUED" and summary["recorded"] is None and summary["runKey"] == "gh-1-1"

    delivered = RunResult("submitted", "SUBMITTED", post_id="p", run_key="gh-1-1", delivered=True)
    assert _close_run(publisher, tmp_path, [], delivered, ARGS) == 0
    assert publisher.recorded == [] and "기록 실패" not in capsys.readouterr().out


@pytest.mark.parametrize("error, recorded", [
    ("GATEWAY_BUSY", True),               # 서버가 저장 전에 거절했다 — 이 실행은 여기서 끝났다
    ("GATEWAY_DUPLICATE", True),
    ("GATEWAY_DAILY_LIMIT", True),
    ("ssh 실패(종료 코드 255)", False),    # 받았는지 모른다 — 받았다면 납품이 원장을 남긴다
    ("게이트웨이 시간 초과(300초)", False),
    ("DELIVERY_FAILED", False),
])
def test_submitFailure_recordsTheLedgerOnlyWhenTheServerSurelyRefused(tmp_path, error, recorded):
    # 러너가 같은 runKey 로 먼저 남기면, 서버가 실제로 받아 나중에 납품한 초안을 백엔드가 버린다(크로스 체크 지적).
    class Failing:
        def submit(self, order):
            raise GatewayError(error)

    result = _drafter(tmp_path, None, FakePublisher(), submitter=Failing()).run()
    publisher = RecordingPublisher()

    assert _close_run(publisher, tmp_path, [], result, ARGS) == 1  # 어느 쪽이든 잡은 실패로 알린다
    assert bool(publisher.recorded) is recorded


def test_captionIsMeasuredInUtf16LikeTheBackend():
    # 글자 수로는 450 이하지만 이모지 하나가 UTF-16 2자라 백엔드 상한(@Size)을 넘는다.
    with pytest.raises(ValueError):
        contract.parse(DRAFT | {"caption": "😀" * 300})
    assert contract.parse(DRAFT | {"caption": "😀" * 225}).caption


def test_editPromptBudget_countsJsonEscapedHeadlines(tmp_path, monkeypatch):
    from gole_promotion_agent import memory

    # 따옴표는 JSON 에서 두 글자가 된다. 한글 41자로 재던 예산은 이 문구를 담지 못했다.
    korean = drafting.POLISH_PROMPT + contract.headline_block("가" * 41) + drafting.MAKE_RULES
    monkeypatch.setattr(memory, "MAX_EDIT_PROMPT", contract.utf16_length(korean))
    gateway = FakeGateway(DRAFT)

    result = _drafter(tmp_path, gateway, FakePublisher()).run()

    assert result.detail.startswith("EDIT_PROMPT_TOO_LONG ") and gateway.requests == []
    assert contract.utf16_length(contract.headline_block('"' * 40)) > contract.utf16_length(
        contract.headline_block("가" * 41))
