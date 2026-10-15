"""반려 경험의 성찰과 확정 지침 적용. 외부 모델/서버 없이 실제 러너를 통과시킨다."""

import json

import pytest
from pydantic import ValidationError

from gole_promotion_agent import memory
from gole_promotion_core import contract
from gole_promotion_core.ledger import RunResult, UsageMeter, close_run as _close_run
from gole_promotion_agent.fakes import FakeCamera
from gole_promotion_core.publisher import BackendPublisher

from .gitrepo import commit
from .test_drafter import ARGS, DRAFT, ROUTES, SKIP, FakeGateway, FakePublisher, RecordingPublisher, _drafter

PLAN_STAGE = {"stage": "PLAN", "routes": ["/", "/collection", "/search"], "feedbackIds": [], "guidelineIds": []}
IMAGE_STAGE = {"stage": "IMAGE_EDIT", "routes": ["/collection"], "feedbackIds": [], "guidelineIds": []}


def feedback(id="feedback-1", targets=None):
    return {
        "id": id, "category": "SERVICE", "reason": "화면 글자가 너무 작아 읽을 수 없음",
        "targets": targets or ["CAPTION", "SCREEN_SELECTION", "IMAGE_EDIT"],
        "snapshot": {"caption": "옛 캡션", "mediaUrls": ["https://gole.test/image.png"],
                     "captures": [{"route": "/collection", "edit": "과도하게 축소"}]},
    }


def guideline(id="guideline-1", targets=None, status="ACTIVE", content="화면 글자가 읽힐 크기를 유지한다"):
    return {
        "id": id, "kind": "PROCEDURE", "content": content,
        "targets": targets or ["IMAGE_EDIT"], "categories": ["SERVICE"],
        "sourceFeedbackIds": ["feedback-1"], "status": status,
    }


class ReflectingGateway(FakeGateway):
    """한 호출이 초안과 함께 성찰 제안(reflection)을 낸다(D25)."""

    def __init__(self, proposals=None, choice=DRAFT):
        super().__init__(choice | {"reflection": proposals or []})


@pytest.mark.parametrize("gate", ["QUEUE_FULL", "NO_WEB_CHANGE", "ALREADY_DRAFTED"])
def test_generationGates_skipModelAndLeaveExperienceForNextRun(tmp_path, repo, gate):
    gateway = ReflectingGateway()
    publisher = FakePublisher(pending=5 if gate == "QUEUE_FULL" else 0,
                              exists=gate == "ALREADY_DRAFTED",
                              memory_context={"unreflectedFeedback": [feedback()]})
    camera = FakeCamera(ROUTES)
    options = {"camera": camera, "run_key": "test-reflection"}
    if gate != "QUEUE_FULL":
        options.update(service=False, sha=commit(repo, "변경", web=gate != "NO_WEB_CHANGE"))

    result = _drafter(tmp_path, gateway, publisher, **options).run()

    assert result.code == gate and camera.calls == []
    assert gateway.requests == [] and publisher.reflections == []
    assert result.run_key == "test-reflection"


def test_proposedGuidelines_waitForHuman_thenEachStageRecordsOnlyItsTargets(tmp_path):
    text = guideline("text", ["CAPTION", "SCREEN_SELECTION"], content="글은 짧게 쓴다")
    image = guideline("image")
    pending = guideline("pending", status="PROPOSED", content="아직 승인하지 않은 제안")
    retired = guideline("retired", status="RETIRED", content="해제한 지침")
    context = {"feedback": [feedback()], "guidelines": [text, image, pending, retired]}
    publisher, gateway = FakePublisher(memory_context=context), ReflectingGateway()

    result = _drafter(tmp_path, gateway, publisher).run()

    plan, make = (request["prompt"] for request in gateway.requests)
    assert text["content"] in plan and image["content"] not in plan  # 단계마다 자기 targets 의 지침만
    assert image["content"] in make and text["content"] not in make
    assert plan.count("화면 글자가 너무 작아") == 1 and "실제 픽셀을 보지 못했다" in plan
    assert all("아직 승인하지 않은 제안" not in p and "해제한 지침" not in p for p in (plan, make))
    assert publisher.created[0][3]["captures"][0]["edit"] == make
    assert result.memory_context["feedbackIds"] == ["feedback-1"]
    assert [item["id"] for item in result.memory_context["guidelines"]] == ["text", "image"]
    assert result.memory_context["stages"] == [
        PLAN_STAGE | {"feedbackIds": ["feedback-1"], "guidelineIds": ["text"]},
        IMAGE_STAGE | {"feedbackIds": ["feedback-1"], "guidelineIds": ["image"]},
    ]


def test_reflection_isSavedFromTheSameCall_andFeedbackAppearsOnce(tmp_path):
    proposal = {key: value for key, value in guideline(status="PROPOSED").items() if key not in {"id", "status"}}
    context = {"feedback": [feedback()], "unreflectedFeedback": [feedback()]}
    gateway, publisher = ReflectingGateway([proposal]), FakePublisher(memory_context=context)

    result = _drafter(tmp_path, gateway, publisher, run_key="same-run").run()

    assert publisher.reflections == [{"feedbackIds": ["feedback-1"], "runKey": "same-run", "proposals": [proposal]}]
    plan = gateway.requests[0]["prompt"]
    assert plan.count("화면 글자가 너무 작아") == 1  # 에피소드에 실었으니 성찰은 ID로만 가리킨다
    assert '"feedbackIds": ["feedback-1"]' in plan
    assert result.outcome == "submitted" and result.detail is None


def test_noUnreflectedFeedback_asksForEmptyReflectionAndSavesNothing(tmp_path):
    gateway, publisher = ReflectingGateway(), FakePublisher()

    _drafter(tmp_path, gateway, publisher).run()

    assert "미처리 반려가 없다" in gateway.requests[0]["prompt"] and publisher.reflections == []


@pytest.mark.parametrize("failure", ["contract", "shape", "save"])
def test_reflectionFailure_keepsExperienceUnprocessedButStillSubmits(tmp_path, failure):
    context = {"feedback": [feedback()], "unreflectedFeedback": [feedback()], "guidelines": [guideline()]}
    publisher = FakePublisher(memory_context=context)
    proposals = {
        "contract": [{**guideline(), "sourceFeedbackIds": ["not-in-this-batch"]}],
        "shape": [{"kind": "BASH"}],
        "save": [],
    }[failure]
    gateway = ReflectingGateway(proposals)
    if failure == "save":
        publisher.reflect = lambda _payload: (_ for _ in ()).throw(ConnectionError("비공개 HTTP 오류"))

    result = _drafter(tmp_path, gateway, publisher).run()

    assert result.outcome == "submitted" and result.detail == "MEMORY_REFLECTION_FAILED"
    assert context["unreflectedFeedback"] == [feedback()]
    if failure != "save":
        assert publisher.reflections == []


def test_memoryFetchFailure_preventsCapturesAndModels(tmp_path):
    publisher, gateway, camera = FakePublisher(), ReflectingGateway(), FakeCamera(ROUTES)
    publisher.memory_context = lambda *_args: (_ for _ in ()).throw(ConnectionError("private"))

    result = _drafter(tmp_path, gateway, publisher, camera=camera).run()

    assert (result.outcome, result.code, result.detail) == ("failed", "ERROR", "MEMORY_CONTEXT_FAILED")
    assert gateway.requests == [] and camera.calls == []


def test_brokenImage_quotingPrivateMemory_doesNotLeakToPublicLogs(tmp_path, capsys):
    sentinel = "비공개-반려지침-노출금지"
    publisher = FakePublisher(memory_context={"guidelines": [guideline(content=sentinel)]})

    class QuotingGateway(FakeGateway):
        def call(self, request):
            if not request.get("want_images"):
                return super().call(request)
            assert sentinel in request["prompt"]
            return {"ok": True, "images": [{"name": "x.png", "data": request["prompt"]}]}

    result = _drafter(tmp_path, QuotingGateway(DRAFT), publisher, log=print).run()

    public_log = capsys.readouterr().out
    assert result.outcome == "submitted" and "이미지 제작 실패" in public_log
    assert sentinel not in public_log and "홍보 작업 기억" not in public_log
    assert all(path.parent.name == "captures" for path in publisher.uploaded[0])


def test_longEditPrompt_failsExplicitlyBeforeCodexInsteadOfTruncating(tmp_path):
    context = {"guidelines": [guideline(str(i), content=str(i) + "가" * 999) for i in range(8)]}
    publisher, gateway = FakePublisher(memory_context=context), ReflectingGateway()

    result = _drafter(tmp_path, gateway, publisher).run()

    assert result.outcome == "failed" and result.detail.startswith("EDIT_PROMPT_TOO_LONG ")
    lengths = json.loads(result.detail.removeprefix("EDIT_PROMPT_TOO_LONG "))
    assert lengths["total"] == sum(value for key, value in lengths.items() if key != "total") > 6_000
    assert gateway.requests == [] and publisher.created == []
    assert result.memory_context == {"feedbackIds": [], "guidelines": [], "stages": []}


def test_unusedImageGuidelines_areAbsentFromLedgerWhenCodexSkips(tmp_path):
    publisher = FakePublisher(memory_context={"guidelines": [guideline()]})
    gateway = FakeGateway(SKIP)

    result = _drafter(tmp_path, gateway, publisher).run()

    assert result.memory_context == {"feedbackIds": [], "guidelines": [], "stages": [PLAN_STAGE]}


def test_sameRunnerReexecution_doesNotRetainRetiredGuidelines(tmp_path):
    context = {"guidelines": [guideline()]}
    publisher, gateway = FakePublisher(memory_context=context), ReflectingGateway()
    drafter = _drafter(tmp_path, gateway, publisher)
    assert drafter.run().memory_context["guidelines"]
    context["guidelines"][0]["status"] = "RETIRED"
    gateway.requests.clear()

    result = drafter.run()

    assert result.memory_context == {"feedbackIds": [], "guidelines": [], "stages": [PLAN_STAGE, IMAGE_STAGE]}
    assert all("화면 글자가 읽힐 크기" not in json.dumps(request, ensure_ascii=False) for request in gateway.requests)


@pytest.mark.parametrize("patch", [
    {"kind": "BASH"}, {"targets": ["ADMIN"]}, {"categories": ["ANY"]}, {"content": " "},
    {"content": "가" * 1001}, {"targets": []}, {"categories": []}, {"sourceFeedbackIds": []},
    {"sourceFeedbackIds": ["f", "f"]},
])
def test_proposalTrustBoundary_rejectsInvalidModelValues(patch):
    data = {key: value for key, value in guideline().items() if key not in {"id", "status"}}
    with pytest.raises(ValidationError):
        contract.Proposal.model_validate(data | patch)


def test_contextRejectsOverBudgetFeedbackButKeepsAllGuidelines():
    for context in ({"feedback": [feedback()] * 4}, {"unreflectedFeedback": [feedback()] * 4}):
        with pytest.raises(ValidationError):
            memory.Context.model_validate(context)
    assert len(memory.Context.model_validate({"guidelines": [guideline(str(i)) for i in range(12)]}).guidelines) == 12


def test_coreSeedGuideline_needsDocumentInsteadOfFeedback():
    core = guideline() | {"kind": "KNOWLEDGE", "sourceFeedbackIds": [], "sourceType": "CORE_SEED",
                          "sourceReference": ".kiro/steering/brand-identity.md@promotion-core-v1"}
    assert memory.Guideline.model_validate(core).sourceFeedbackIds == []
    for patch in ({"sourceReference": None}, {"kind": "PROCEDURE"}, {"sourceFeedbackIds": ["feedback-1"]},
                  {"sourceType": "FEEDBACK"}):
        with pytest.raises(ValidationError):
            memory.Guideline.model_validate(core | patch)


def test_stageMemory_queriesOnce_freezesAllGuidelines_andRecordsEachStage(tmp_path):
    plan_feedback = [feedback(f"plan-{i}", ["CAPTION"]) for i in range(2)]
    image_feedback = [feedback("image-0", ["IMAGE_EDIT"])]
    active = [guideline(f"text-{i}", ["CAPTION"], content=f"짧은 문장 규칙 {i}") for i in range(10)]
    active.append(guideline("image", content="실행 시작에 확정한 제작 절차"))
    publisher = FakePublisher(memory_context={"feedback": plan_feedback + image_feedback, "guidelines": active})
    gateway = ReflectingGateway()

    result = _drafter(tmp_path, gateway, publisher).run()

    assert result.outcome == "submitted"
    assert publisher.memory_requests == [("SERVICE", ["/", "/collection", "/search"])]
    plan, make = gateway.requests
    assert all(f"짧은 문장 규칙 {i}" in plan["prompt"] for i in range(10))
    edit = publisher.created[0][3]["captures"][0]["edit"]
    assert "실행 시작에 확정한 제작 절차" in edit and "짧은 문장 규칙 0" not in edit
    assert "image-0" in edit and "plan-0" not in edit
    assert len(result.memory_context["feedbackIds"]) == 3 and len(result.memory_context["guidelines"]) == 11
    assert result.memory_context["stages"] == [
        PLAN_STAGE | {"feedbackIds": ["plan-0", "plan-1"], "guidelineIds": [f"text-{i}" for i in range(10)]},
        IMAGE_STAGE | {"feedbackIds": ["image-0"], "guidelineIds": ["image"]},
    ]


def test_promptSeparatesFactsProceduresAndEpisodes():
    core = guideline("fact") | {"kind": "KNOWLEDGE", "content": "GoLe의 브랜드 색은 파랑"}
    context = memory.Context.model_validate({"guidelines": [core, guideline()], "feedback": [feedback()]})
    parts = memory.render_parts(context.feedback, context.guidelines)

    assert "GoLe의 브랜드 색은 파랑" in parts["knowledge"] and "KNOWLEDGE" in parts["knowledge"]
    assert "화면 글자가 읽힐 크기" in parts["procedure"] and "PROCEDURE" in parts["procedure"]
    assert "화면 글자가 너무 작아" in parts["episodes"] and "EPISODIC" in parts["episodes"]
    assert "GoLe의 브랜드 색은 파랑" not in parts["episodes"]


def test_editBudgetCountsAstralCharactersLikeJava(tmp_path):
    assert contract.utf16_length("한글😀") == 4
    publisher = FakePublisher(memory_context={"guidelines": [
        guideline(str(i), content="😀" * 500) for i in range(6)
    ]})
    gateway = ReflectingGateway()
    result = _drafter(tmp_path, gateway, publisher).run()

    assert result.outcome == "failed" and result.detail.startswith("EDIT_PROMPT_TOO_LONG ")
    assert gateway.requests == []


def test_feedbackProjection_doesNotRecursivelyIncludePastEditPromptsOrRationale():
    entry = feedback()
    entry["snapshot"]["captures"][0]["edit"] = "이전 작업 기억 중첩" * 10_000
    entry["snapshot"]["provenance"] = {"rationale": "과거 판단 중첩" * 10_000}
    context = memory.Context.model_validate({"feedback": [entry] * 3, "unreflectedFeedback": [entry]})

    rendered = memory.render(context.feedback, [])
    reflected = memory.reflection_prompt(context)

    assert len(rendered) < 6_000
    assert all("중첩" not in prompt for prompt in (rendered, reflected))
    assert all("/collection" in prompt and "옛 캡션" in prompt for prompt in (rendered, reflected))


def test_runSummary_neverContainsPrivateMemory(tmp_path, capsys):
    publisher = RecordingPublisher()
    snapshot = {"feedbackIds": ["private-feedback"], "guidelines": [{"content": "비공개 지침"}]}
    result = RunResult("submitted", "SUBMITTED", post_id="post-1", memory_context=snapshot, run_key="shared-key")

    assert _close_run(publisher, tmp_path, UsageMeter(None), result, ARGS) == 0

    assert publisher.recorded[0]["memoryContext"] == snapshot
    assert publisher.recorded[0]["runKey"] == "shared-key"
    public = (tmp_path / "run.json").read_text(encoding="utf-8") + capsys.readouterr().out
    assert "memoryContext" not in public and "비공개 지침" not in public and "private-feedback" not in public


def test_publisherMemoryRequests_useAdminHeadersRepeatedRoutesAndExactReflectionPayload():
    calls = []

    class Response:
        def __init__(self, data):
            self.data = data

        def raise_for_status(self):
            pass

        def json(self):
            return self.data

    class Client:
        def post(self, path, **kwargs):
            if path == "/api/v1/accounts/sessions":
                return Response({"role": "ADMIN", "sessionToken": "private-session"})
            calls.append((path, kwargs))
            return Response([])

        def get(self, path, **kwargs):
            calls.append((path, kwargs))
            return Response({"feedback": [], "guidelines": [], "unreflectedFeedback": []})

    publisher = BackendPublisher("https://gole.test", "bot", "pw", client=Client())
    context = publisher.memory_context("FEATURE", ["/", "/collection"])
    payload = {"feedbackIds": ["feedback-1"], "runKey": "same-key", "proposals": []}

    assert context["feedback"] == [] and publisher.reflect(payload) == []
    assert calls[0][1]["params"] == [("category", "FEATURE"), ("routes", "/"), ("routes", "/collection")]
    assert calls[1][1]["json"] == payload
    assert all(kwargs["headers"] == {"Authorization": "Bearer private-session"} for _, kwargs in calls)


@pytest.mark.parametrize("url", ["https://gole.co.kr", "http://example.com", "http://localhost@evil.test",
                                  "http://user:pw@localhost:8080", "http://localhost:8080/api"])
def test_e2eHelper_refusesNonLocalApiOrigins(url):
    from argparse import ArgumentTypeError
    from .run_memory_e2e import local_api_url

    with pytest.raises(ArgumentTypeError):
        local_api_url(url)


def test_e2eHelper_passesThroughRealGatewayWithoutLaunchingAnyModelCli(tmp_path):
    import base64
    import io
    from PIL import Image
    from .run_memory_e2e import FakeCliGateway, png

    gateway = FakeCliGateway("apply", tmp_path)
    response = gateway.call({"engine": "codex", "model": "fake-codex", "prompt": "골라서 만들어",
                             "json_schema": contract.OUTPUT_SCHEMA, "want_images": True,
                             "images": [{"data": base64.b64encode(png()).decode()}] * 2})

    assert response["structured"]["decision"] == "draft" and response["text"] == ""
    assert base64.b64decode(response["images"][0]["data"]) == png()
    with Image.open(io.BytesIO(png())) as image:
        assert image.size == (390, 844)
    assert response["structured"]["pick"]["headline"] == "내 레고 세트, 한곳에"
    assert list(tmp_path.iterdir()) == []
