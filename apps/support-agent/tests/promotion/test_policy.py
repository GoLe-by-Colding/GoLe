"""코드로 거는 규칙(policy). 프롬프트와 무관하게 지켜져야 한다 — 사설·데모 시세 화면, 세션 주입, 이력 안내."""

import json

import pytest

from gole_promotion_agent import policy


def test_private_routes_are_excluded_even_though_login_now_succeeds():
    """로그인해서 찍기 때문에 새로 필요해진 제외다(스펙 D12·D19).

    익명일 때 이 라우트들은 로그인 게이트만 보여 무해했지만, 인증 뒤에는 봇 계정의
    개인 정보가 그대로 렌더링된다.
    """
    assert policy.is_public_capture_route("/profile") is False
    assert policy.is_public_capture_route("/profile/security") is False
    assert policy.is_public_capture_route("/notifications") is False
    # 로그인이 필요하지만 개인 정보가 아닌 기능 화면은 이제 찍을 수 있어야 한다.
    assert policy.is_public_capture_route("/sell") is True
    assert policy.is_public_capture_route("/brick-filter") is True


def test_session_init_script_survives_quotes_in_token():
    script = policy.session_init_script(
        {"accountId": "bot", "sessionToken": 'to"ken\\', "role": "ADMIN"}
    )

    # 스크립트가 문자열 리터럴 하나로 닫혀야 한다 — 토큰의 따옴표가 깨뜨리면 안 된다.
    assert script.startswith("window.localStorage.setItem(")
    assert script.endswith(");")
    payload = json.loads(json.loads(script.split(", ", 1)[1].rstrip(");")))
    assert payload["sessionToken"] == 'to"ken\\'


def test_session_init_script_only_carries_allowed_fields():
    """백엔드 응답을 통째로 브라우저에 심지 않는다."""
    script = policy.session_init_script(
        {"accountId": "bot", "sessionToken": "t", "role": "ADMIN", "secret": "leak-me"}
    )

    assert "leak-me" not in script


def test_session_init_script_refuses_empty_token():
    """토큰이 비면 익명으로 조용히 찍히는 대신 실패해야 한다."""
    with pytest.raises(ValueError):
        policy.session_init_script({"accountId": "bot", "sessionToken": "", "role": "ADMIN"})


def test_capture_chrome_script_hides_operator_only_elements():
    script = policy.capture_chrome_script()

    assert policy.CAPTURE_HIDE_ATTRIBUTE in script
    assert "display:none !important" in script


def test_history_carries_history_including_rejections():
    prompt = policy.render_history(
        [
            {"status": "PENDING_REVIEW", "caption": "어제 올린 글이야."},
            {"status": "DRAFT", "caption": "반려된 글", "rejectionReason": "닉네임이 보임"},
        ]
    )

    assert "어제 올린 글이야." in prompt
    assert "닉네임이 보임" in prompt
    assert "반복하지 마" in prompt


def test_history_without_history_says_so():
    assert "첫 글" in policy.render_history([])


def test_demo_capture_excludes_price_screens():
    """데모 데이터로 찍을 때 시세 화면은 가짜 체결가가 사실처럼 읽히므로 막는다."""
    from gole_promotion_agent import policy

    assert policy.is_public_capture_route("/prices")
    assert not policy.is_public_capture_route("/prices", demo=True)
    assert policy.is_public_capture_route("/search", demo=True)


def test_demo_prompt_forbids_numbers_in_caption():
    from gole_promotion_agent import policy

    demo = policy.screen_note(True)
    bot = policy.screen_note(False)

    assert "데모 데이터" in demo and "가격" in demo
    assert "자기 데이터가 없다" in bot and "데모 데이터" not in bot
