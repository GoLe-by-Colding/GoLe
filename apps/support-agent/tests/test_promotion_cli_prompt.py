"""Claude Code 경로의 프롬프트·출력 계약."""

import pytest
from pydantic import ValidationError

from gole_promotion_agent import cli_prompt


def test_systemPrompt_namesOnlyTheTwoCommandsAndDemoRules():
    prompt = cli_prompt.system_prompt([{"status": "PUBLISHED", "caption": "직전 글"}], demo=True, service=False)

    assert cli_prompt.CAPTURE_COMMAND in prompt
    assert cli_prompt.COMPOSE_COMMAND in prompt
    assert "데모 데이터" in prompt
    assert "직전 글" in prompt
    # 옛 도구 이름이 남아 있으면 모델이 없는 도구를 찾는다.
    for stale in ("submit_promotion_draft", "read_release_diff", "list_routes"):
        assert stale not in prompt


def test_servicePrompt_hasNoDiff():
    assert "diff" not in cli_prompt.task_prompt(service=True)
    assert "@@ -1" in cli_prompt.task_prompt(service=False, subject="feat", diff="@@ -1 +1 @@")


def test_draftOutput_requiresCompleteDraft():
    with pytest.raises(ValidationError):
        cli_prompt.DraftOutput.model_validate({"decision": "draft", "caption": "글"})
    with pytest.raises(ValidationError):
        cli_prompt.DraftOutput.model_validate({"decision": "skip"})

    draft = cli_prompt.DraftOutput.model_validate(
        {
            "decision": "draft",
            "caption": "글",
            "rationale": "이유",
            "images": [{"card": "card-01.png"}, {"capture": "목록"}],
        }
    )
    assert [image.card or image.capture for image in draft.images] == ["card-01.png", "목록"]


@pytest.mark.parametrize(
    "image", [{}, {"card": "card-01.png", "capture": "목록"}, {"card": "../secret.png"}]
)
def test_draftImage_rejectsAmbiguousOrPathLike(image):
    with pytest.raises(ValidationError):
        cli_prompt.DraftImage.model_validate(image)
