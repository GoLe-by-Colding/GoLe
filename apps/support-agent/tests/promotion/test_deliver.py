"""지인 서버의 deliver(D29) — 기획·검증·제작 뒤 납품 한 묶음의 모양과 실패 알림을 고정한다."""

import base64
import json

from gole_promotion_core import CONTRACT_VERSION, contract
from gole_promotion_core.gateway import GatewayError
from gole_promotion_core.order import Order
from gole_promotion_deliver.deliver import DELIVERY_FAILED, Delivered, deliver

from .test_drafter import DRAFT, POLISHED, SKIP, FakeGateway

ORIGINAL = base64.b64encode(b"\x89PNG-original").decode()
SHA = "b" * 40


def order(**overrides) -> Order:
    data = dict(
        contract_version=CONTRACT_VERSION,
        run_key="gh-9-1",
        category="FEATURE",
        sha=SHA,
        data_source="DEMO",
        run_url="https://github.com/o/r/actions/runs/9",
        agent_sha="c" * 40,
        release_title="컬렉션 정리",
        plan={"engine": "codex", "prompt": "기획", "json_schema": dict(contract.OUTPUT_SCHEMA), "images": []},
        make_prefix="[절차]",
        make_rules="[규칙]",
        shots=[{"route": "/", "captured_at": "t1", "data": ORIGINAL},
               {"route": "/collection", "captured_at": "t2", "data": ORIGINAL}],
        memory_context={"feedbackIds": [], "guidelines": [], "stages": [
            {"stage": "PLAN", "routes": ["/", "/collection"], "feedbackIds": [], "guidelineIds": []}]},
        image_stage={"feedbackIds": [], "guidelineIds": ["g-1"]},
    )
    return Order.model_validate(data | overrides)


class Recorder:
    def __init__(self, response=Delivered(200, "SUBMITTED", "SUBMITTED")):
        self.response = response
        self.payloads = []
        self.notes = []

    def post(self, payload):
        # 실제 HTTP 처럼 JSON 으로 오갈 수 있어야 한다.
        self.payloads.append(json.loads(json.dumps(payload)))
        return self.response

    def notify(self, content):
        self.notes.append(content)


def run(order_, gateway, recorder=None):
    recorder = recorder or Recorder()
    code = deliver(order_, gateway, recorder.post, recorder.notify, log=lambda _: None)
    return code, recorder


def test_draft_isDeliveredWithPolishedImageOriginalAndImageStage():
    gateway = FakeGateway(DRAFT)
    code, recorder = run(order(), gateway)

    assert code == 0 and recorder.notes == []
    payload = recorder.payloads[0]
    assert payload["run"]["outcome"] == "SUBMITTED" and payload["run"]["reasonCode"] == "SUBMITTED"
    assert len(payload["run"]["calls"]) == 2
    make = gateway.requests[1]
    assert make["prompt"] == contract.edit_prompt("[절차]", "내 레고 세트, 한곳에", "[규칙]")
    assert make["images"] == [{"data": ORIGINAL}]
    image = payload["draft"]["images"][0]
    assert base64.b64decode(image["data"]) == POLISHED
    assert image["original"] == ORIGINAL and image["edit"] == make["prompt"]
    capture = payload["draft"]["captures"][0]
    assert capture["route"] == "/collection" and capture["polishFailed"] is False
    assert capture["dataSource"] == "DEMO" and capture["headline"] == "내 레고 세트, 한곳에"
    assert payload["draft"]["provenance"]["releaseTitle"] == "컬렉션 정리"
    assert payload["run"]["memoryContext"]["stages"][-1] == {
        "stage": "IMAGE_EDIT", "routes": ["/collection"], "feedbackIds": [], "guidelineIds": ["g-1"]}
    assert "reflection" not in payload


def test_failedPolish_sendsOriginalAsTheImage():
    _, recorder = run(order(), FakeGateway(DRAFT, polish_fails=True))

    image = recorder.payloads[0]["draft"]["images"][0]
    assert image == {"data": ORIGINAL}
    assert recorder.payloads[0]["draft"]["captures"][0]["polishFailed"] is True


def test_planFailure_deliversOnlyTheRunAndNotifiesWhenBackendSaysFailed():
    class Down:
        def call(self, request):
            raise GatewayError("CODEX_USAGE_LIMIT", {"engine": "codex", "input_tokens": 5})

    code, recorder = run(order(), Down(), Recorder(Delivered(201, "FAILED", "GATEWAY_FAILED")))

    run_ = recorder.payloads[0]["run"]
    assert code == 0
    assert (run_["outcome"], run_["reasonCode"], run_["detail"]) == ("FAILED", "GATEWAY_FAILED", "CODEX_USAGE_LIMIT")
    assert run_["calls"][0]["inputTokens"] == 5 and run_["calls"][0]["ok"] is False
    assert "draft" not in recorder.payloads[0]
    assert len(recorder.notes) == 1 and "GATEWAY_FAILED" in recorder.notes[0] and "gh-9-1" in recorder.notes[0]


def test_skip_and_invalidChoice_sendNoDraft():
    _, skipped = run(order(), FakeGateway(SKIP), Recorder(Delivered(201, "SKIPPED", "MODEL_SKIPPED")))
    assert skipped.payloads[0]["run"]["reasonCode"] == "MODEL_SKIPPED" and "draft" not in skipped.payloads[0]
    assert len(skipped.notes) == 1 and skipped.notes[0].startswith("⏭️") and "MODEL_SKIPPED" in skipped.notes[0]

    _, invalid = run(order(), FakeGateway(DRAFT | {"pick": {"image": 9, "label": "x", "headline": "y"}}))
    assert invalid.payloads[0]["run"]["reasonCode"] == "CHOICE_INVALID" and "draft" not in invalid.payloads[0]


def test_contractMismatch_callsNoModel():
    gateway = FakeGateway(DRAFT)
    _, recorder = run(order(contract_version=CONTRACT_VERSION + 1), gateway)

    assert gateway.requests == []
    assert recorder.payloads[0]["run"]["detail"] == "GATEWAY_CONTRACT_MISMATCH"


def test_unreachableBackend_returnsDeliveryFailedAndNotifies():
    code, recorder = run(order(), FakeGateway(DRAFT), Recorder(Delivered(None)))

    assert code == DELIVERY_FAILED
    assert len(recorder.notes) == 1 and "배달 실패" in recorder.notes[0]


def test_queueFullAtBackend_isNotified():
    _, recorder = run(order(), FakeGateway(DRAFT), Recorder(Delivered(201, "SKIPPED", "QUEUE_FULL")))

    assert len(recorder.notes) == 1 and "QUEUE_FULL" in recorder.notes[0]


def test_reflection_isValidatedAgainstThisRunsFeedback():
    proposal = {"kind": "PROCEDURE", "content": "빈 화면은 고르지 않는다", "targets": ["SCREEN_SELECTION"],
                "categories": ["FEATURE"], "sourceFeedbackIds": ["fb-1"]}
    _, good = run(order(reflection_feedback_ids=["fb-1"]), FakeGateway(DRAFT | {"reflection": [proposal]}))
    assert good.payloads[0]["reflection"]["feedbackIds"] == ["fb-1"]
    assert good.payloads[0]["reflection"]["runKey"] == "gh-9-1"

    # 이번에 읽지 않은 반려를 근거로 들면 성찰만 버리고 초안은 낸다.
    foreign = proposal | {"sourceFeedbackIds": ["fb-other"]}
    _, bad = run(order(reflection_feedback_ids=["fb-1"]), FakeGateway(DRAFT | {"reflection": [foreign]}))
    assert "reflection" not in bad.payloads[0]
    assert bad.payloads[0]["run"]["detail"] == "MEMORY_REFLECTION_FAILED"
    assert "draft" in bad.payloads[0]
