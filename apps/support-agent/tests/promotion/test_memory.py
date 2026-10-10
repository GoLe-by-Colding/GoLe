"""반려 경험의 성찰과 확정 지침 적용. 외부 모델/서버 없이 실제 러너를 통과시킨다."""

import json

import pytest
from pydantic import ValidationError

from gole_promotion_agent import memory
from gole_promotion_agent.drafter import RunResult, UsageMeter, _close_run
from gole_promotion_agent.fakes import FakeCamera
from gole_promotion_agent.gateway_client import GatewayError
from gole_promotion_agent.hands import BackendPublisher

from .gitrepo import commit
from .test_drafter import ARGS, DRAFT, ROUTES, FakeGateway, FakePublisher, RecordingPublisher, _drafter


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
    def __init__(self, proposals=None, fails=False):
        super().__init__(DRAFT)
        self.proposals, self.fails = proposals or [], fails

    def call(self, request):
        if "proposals" in request.get("json_schema", {}).get("properties", {}):
            self.requests.append(request)
            if self.fails:
                raise GatewayError("비공개 모델 실패 원문")
            return {"structured": {"proposals": self.proposals}}
        return super().call(request)


@pytest.mark.parametrize("gate", ["QUEUE_FULL", "NO_WEB_CHANGE", "ALREADY_DRAFTED"])
def test_reflect_runsBeforeEveryGenerationGate(tmp_path, repo, gate):
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
    assert len(gateway.requests) == 1 and gateway.requests[0]["engine"] == "claude"
    assert publisher.reflections == [{"feedbackIds": ["feedback-1"], "runKey": "test-reflection", "proposals": []}]
    assert result.run_key == "test-reflection"


def test_proposedGuidelines_waitForHuman_thenBothStagesReceiveOnlyTheirTargets(tmp_path):
    text = guideline("text", ["CAPTION", "SCREEN_SELECTION"], content="글은 짧게 쓴다")
    image = guideline("image")
    pending = guideline("pending", status="PROPOSED", content="아직 승인하지 않은 제안")
    retired = guideline("retired", status="RETIRED", content="해제한 지침")
    context = {"feedback": [feedback()], "guidelines": [text, image, pending, retired]}
    publisher, gateway = FakePublisher(memory_context=context), ReflectingGateway()

    result = _drafter(tmp_path, gateway, publisher).run()

    choose, *polish = gateway.requests
    assert "글은 짧게 쓴다" in choose["system"] and image["content"] not in choose["system"]
    assert "화면 글자가 너무 작아" in choose["system"] and "실제 픽셀을 보지 못했다" in choose["system"]
    assert all(image["content"] in request["prompt"] and text["content"] not in request["prompt"] for request in polish)
    assert all("아직 승인하지 않은 제안" not in json.dumps(request, ensure_ascii=False) for request in gateway.requests)
    assert all("해제한 지침" not in json.dumps(request, ensure_ascii=False) for request in gateway.requests)
    assert result.memory_context["feedbackIds"] == ["feedback-1"]
    assert [item["id"] for item in result.memory_context["guidelines"]] == ["text", "image"]
    assert publisher.created[0][3]["captures"][0]["edit"] == polish[0]["prompt"]


def test_reflectionProposal_doesNotApplyDuringSameRun(tmp_path):
    proposal = {key: value for key, value in guideline(status="PROPOSED").items() if key not in {"id", "status"}}
    context = {"unreflectedFeedback": [feedback()]}
    gateway, publisher = ReflectingGateway([proposal]), FakePublisher(memory_context=context)

    result = _drafter(tmp_path, gateway, publisher).run()

    assert publisher.reflections[0]["proposals"] == [proposal]
    assert all(proposal["content"] not in json.dumps(request, ensure_ascii=False) for request in gateway.requests[1:])
    assert result.memory_context == {"feedbackIds": [], "guidelines": []}


@pytest.mark.parametrize("failure", ["gateway", "contract", "save"])
def test_reflectionFailure_keepsExperienceUnprocessedAndUsesExistingContext(tmp_path, failure):
    context = {"feedback": [feedback()], "unreflectedFeedback": [feedback()], "guidelines": [guideline()]}
    publisher = FakePublisher(memory_context=context)
    proposals = [{**guideline(), "sourceFeedbackIds": ["not-in-this-batch"]}] if failure == "contract" else None
    gateway = ReflectingGateway(proposals, fails=failure == "gateway")
    if failure == "save":
        publisher.reflect = lambda _payload: (_ for _ in ()).throw(ConnectionError("비공개 HTTP 오류"))

    result = _drafter(tmp_path, gateway, publisher).run()

    assert result.outcome == "submitted" and result.detail == "MEMORY_REFLECTION_FAILED"
    assert "화면 글자가 읽힐 크기" in gateway.requests[-1]["prompt"]
    assert context["unreflectedFeedback"] == [feedback()]
    if failure != "save":
        assert publisher.reflections == []


def test_memoryFetchFailure_preventsCapturesAndModels(tmp_path):
    publisher, gateway, camera = FakePublisher(), ReflectingGateway(), FakeCamera(ROUTES)
    publisher.memory_context = lambda *_args: (_ for _ in ()).throw(ConnectionError("private"))

    result = _drafter(tmp_path, gateway, publisher, camera=camera).run()

    assert (result.outcome, result.code, result.detail) == ("failed", "ERROR", "MEMORY_CONTEXT_FAILED")
    assert gateway.requests == [] and camera.calls == []


def test_polishError_quotingPrivateMemory_doesNotLeakToPublicLogs(tmp_path, capsys):
    sentinel = "비공개-반려지침-노출금지"
    publisher = FakePublisher(memory_context={"guidelines": [guideline(content=sentinel)]})

    class QuotingGateway(FakeGateway):
        def call(self, request):
            if request["engine"] == "codex":
                assert sentinel in request["prompt"]
                raise GatewayError(request["prompt"])
            return super().call(request)

    result = _drafter(tmp_path, QuotingGateway(DRAFT), publisher, log=print).run()

    public_log = capsys.readouterr().out
    assert result.outcome == "submitted" and "GatewayError" in public_log
    assert sentinel not in public_log and "홍보 작업 기억" not in public_log
    assert all(path.parent.name == "captures" for path in publisher.uploaded[0])


def test_longEditPrompt_failsExplicitlyBeforeCodexInsteadOfTruncating(tmp_path):
    context = {"guidelines": [guideline(str(i), content=str(i) + "가" * 999) for i in range(8)]}
    publisher, gateway = FakePublisher(memory_context=context), ReflectingGateway()

    result = _drafter(tmp_path, gateway, publisher).run()

    assert (result.outcome, result.detail) == ("failed", "EDIT_PROMPT_TOO_LONG")
    assert [request["engine"] for request in gateway.requests] == ["claude"]
    assert publisher.created == [] and result.memory_context["guidelines"] == []


def test_unusedImageGuidelines_areAbsentFromLedgerWhenClaudeSkips(tmp_path):
    publisher = FakePublisher(memory_context={"guidelines": [guideline()]})
    gateway = FakeGateway({"decision": "skip", "skip_reason": "화면 없음"})

    result = _drafter(tmp_path, gateway, publisher).run()

    assert result.memory_context == {"feedbackIds": [], "guidelines": []}


def test_sameRunnerReexecution_doesNotRetainRetiredGuidelines(tmp_path):
    context = {"guidelines": [guideline()]}
    publisher, gateway = FakePublisher(memory_context=context), ReflectingGateway()
    drafter = _drafter(tmp_path, gateway, publisher)
    assert drafter.run().memory_context["guidelines"]
    context["guidelines"][0]["status"] = "RETIRED"
    gateway.requests.clear()

    result = drafter.run()

    assert result.memory_context == {"feedbackIds": [], "guidelines": []}
    assert all("화면 글자가 읽힐 크기" not in json.dumps(request, ensure_ascii=False) for request in gateway.requests)


@pytest.mark.parametrize("patch", [
    {"kind": "BASH"}, {"targets": ["ADMIN"]}, {"categories": ["ANY"]}, {"content": " "},
    {"content": "가" * 1001}, {"targets": []}, {"categories": []}, {"sourceFeedbackIds": []},
    {"sourceFeedbackIds": ["f", "f"]},
])
def test_proposalTrustBoundary_rejectsInvalidModelValues(patch):
    data = {key: value for key, value in guideline().items() if key not in {"id", "status"}}
    with pytest.raises(ValidationError):
        memory.Proposal.model_validate(data | patch)


def test_contextRejectsOverBudgetFeedbackAndGuidelines():
    for context in ({"feedback": [feedback()] * 4}, {"unreflectedFeedback": [feedback()] * 4},
                    {"guidelines": [guideline()] * 9}):
        with pytest.raises(ValidationError):
            memory.Context.model_validate(context)


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
    from gole_promotion_agent import drafting
    from .run_memory_e2e import FakeCliGateway, png

    gateway = FakeCliGateway("apply", tmp_path)
    choice = gateway.call({"engine": "claude", "model": "fake-claude", "prompt": "골라",
                           "json_schema": drafting.OUTPUT_SCHEMA})
    polished = gateway.call({"engine": "codex", "model": "fake-codex", "prompt": "다듬어",
                             "want_images": True, "images": [{"data": base64.b64encode(png()).decode()}]})

    assert choice["structured"]["decision"] == "draft"
    assert polished["ok"] and base64.b64decode(polished["images"][0]["data"]) == png()
    assert list(tmp_path.iterdir()) == []
