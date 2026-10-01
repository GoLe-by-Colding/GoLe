"""홍보 실행의 손(git·라우트·브라우저·백엔드)과 코드 가드 회귀.

모델 판단은 Claude Code 로 옮겼다. 여기 남은 것은 프롬프트와 무관하게 코드가 지켜야 하는 것이다 —
화면 변경이 없는 릴리스, 사설·데모 시세 화면, 세션 토큰 주입, 검토 화면으로 가는 설명표.
"""

import json
import subprocess
from pathlib import Path

import pytest

from gole_promotion_agent import policy
from gole_promotion_agent.hands import AppRouteCatalog, ReleaseScanner


def _git(repo: Path, *args: str) -> str:
    return subprocess.run(
        ["git", *args], cwd=str(repo), capture_output=True, text=True, check=True
    ).stdout.strip()


def _commit(repo: Path, subject: str, *, web: bool) -> str:
    target = repo / ("apps/web/src/app/page.tsx" if web else "docs/note.md")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(f"// {subject}\n", encoding="utf-8")
    _git(repo, "add", "-A")
    _git(
        repo,
        "-c",
        "user.email=test@gole.test",
        "-c",
        "user.name=test",
        "commit",
        "-q",
        "-m",
        subject,
    )
    return _git(repo, "rev-parse", "HEAD")


@pytest.fixture
def repo(tmp_path: Path) -> Path:
    root = tmp_path / "repo"
    root.mkdir()
    _git(root, "init", "-q", "-b", "main")
    return root


def test_release_scanner_returns_only_given_release(repo: Path):
    target = _commit(repo, "feat(web): 지정 릴리스", web=True)
    _commit(repo, "feat(web): 그 뒤 릴리스", web=True)

    scanner = ReleaseScanner(repo, target)

    assert [(c.sha, c.subject) for c in scanner.candidates()] == [(target, "feat(web): 지정 릴리스")]


def test_release_scanner_skips_release_without_web_changes(repo: Path):
    target = _commit(repo, "fix(api): 백엔드만", web=False)

    assert ReleaseScanner(repo, target).candidates() == ()


def test_route_catalog_excludes_dynamic_and_private_routes(repo: Path):
    app = repo / "apps/web/src/app"
    for relative in [
        "page.tsx",
        "(main)/prices/page.tsx",
        "(main)/listings/[id]/page.tsx",
        "(main)/admin/promotion/page.tsx",
        "login/page.tsx",
    ]:
        target = app / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text("export default function Page() {}\n", encoding="utf-8")

    routes = AppRouteCatalog(repo).routes()

    assert "/" in routes
    assert "/prices" in routes
    assert not [route for route in routes if "[" in route]
    assert "/admin/promotion" not in routes
    assert "/login" not in routes


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


class _FakePage:
    def goto(self, url, **_kwargs):
        self.url = url

    def wait_for_timeout(self, _milliseconds):
        return None

    def screenshot(self, path, **_kwargs):
        Path(path).write_bytes(b"\x89PNG\r\n")


class _FakeContext:
    def __init__(self):
        self.init_scripts: list[str] = []

    def set_default_timeout(self, _milliseconds):
        return None

    def route(self, _pattern, _handler):
        return None

    def add_init_script(self, script):
        self.init_scripts.append(script)

    def new_page(self):
        return _FakePage()

    def close(self):
        return None


class _FakeBrowser:
    def __init__(self):
        self.contexts: list[_FakeContext] = []

    def new_context(self, **_kwargs):
        context = _FakeContext()
        self.contexts.append(context)
        return context


def _capture_with_fake_browser(tmp_path: Path, session_provider) -> list[str]:
    from gole_promotion_agent.hands import PlaywrightCamera

    camera = PlaywrightCamera("http://localhost:3000", ("/",), session_provider)
    browser = _FakeBrowser()
    camera._browser = browser  # 실제 Chromium 없이 조립만 검사한다
    camera.capture("/", [], tmp_path / "shot.png")
    return browser.contexts[0].init_scripts


def test_camera_logs_in_when_session_provider_is_given(tmp_path: Path):
    scripts = _capture_with_fake_browser(
        tmp_path, lambda: {"accountId": "bot", "sessionToken": "tok", "role": "ADMIN"}
    )

    assert any(policy.SESSION_STORAGE_KEY in script for script in scripts)
    assert any(policy.CAPTURE_HIDE_ATTRIBUTE in script for script in scripts)


def test_camera_stays_anonymous_without_session_provider(tmp_path: Path):
    """드라이런·단위 테스트가 백엔드 없이 살아야 하므로 기본값은 익명이다."""
    scripts = _capture_with_fake_browser(tmp_path, None)

    assert not any(policy.SESSION_STORAGE_KEY in script for script in scripts)
    # 운영자 UI 숨김은 로그인 여부와 무관하게 항상 건다.
    assert any(policy.CAPTURE_HIDE_ATTRIBUTE in script for script in scripts)


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


def test_capture_refuses_when_interaction_navigates_to_private_route(tmp_path: Path):
    """클릭이 사설 화면으로 데려가면 찍지 않는다. 시작점 검사만으로는 못 막는다."""
    from gole_promotion_agent.hands import PlaywrightCamera

    camera = PlaywrightCamera("https://gole.co.kr", ("/", "/search"))
    camera._assert_still_allowed("https://gole.co.kr/search")
    for wandered in (
        "https://gole.co.kr/profile",
        "https://gole.co.kr/admin/reports",
        "https://evil.example/search",
    ):
        with pytest.raises(ValueError):
            camera._assert_still_allowed(wandered)


def test_release_diff_is_capped_by_context_budget_and_says_so(repo: Path, monkeypatch):
    """diff 상한은 바이트가 아니라 컨텍스트 예산이어야 한다."""
    monkeypatch.setattr(policy, "MAX_DIFF_CHARS", 400)
    page = repo / "apps/web/src/app/page.tsx"
    page.parent.mkdir(parents=True, exist_ok=True)
    page.write_text("\n".join(f"// line {index}" for index in range(400)), encoding="utf-8")
    _git(repo, "add", "-A")
    _git(repo, "-c", "user.email=t@t.test", "-c", "user.name=t", "commit", "-q", "-m", "big")
    sha = _git(repo, "rev-parse", "HEAD")

    patch = ReleaseScanner(repo, sha).diff()

    assert len(patch) <= policy.MAX_DIFF_CHARS + len(policy.DIFF_TRUNCATED_NOTICE)
    assert patch.endswith(policy.DIFF_TRUNCATED_NOTICE)


def test_backend_publisher_sends_capture_notes_and_provenance():
    """검토 화면이 쓸 설명표·출처가 백엔드 생성 요청 계약 모양으로 나간다."""
    from gole_promotion_agent.hands import BackendPublisher

    sent = {}

    class _Response:
        def __init__(self, payload):
            self._payload = payload

        def raise_for_status(self):
            return None

        def json(self):
            return self._payload

    class _Client:
        def post(self, path, json=None, headers=None, files=None):
            if path == "/api/v1/accounts/sessions":
                return _Response({"role": "ADMIN", "sessionToken": "t", "accountId": "bot"})
            sent.update(json)
            return _Response({"id": "promo-1"})

    publisher = BackendPublisher(
        "https://gole.test",
        "bot@gole.test",
        "pw",
        client=_Client(),
        run={"category": "FEATURE", "dataSource": "DEMO", "runUrl": "https://github.com/o/r/actions/runs/1"},
    )
    details = {
        "captures": [{"label": "목록", "route": "/market", "actions": "'필터' 클릭", "capturedAt": "2026-09-30T00:00:00+00:00"}],
        "rationale": "필터가 새로 생겼어.",
        "releaseTitle": "feat(web): 필터",
    }

    assert publisher.create("a" * 40, "캡션", ["k1"], details) == "promo-1"
    assert sent["category"] == "FEATURE"
    assert sent["captures"] == [
        {
            "label": "목록",
            "route": "/market",
            "actions": "'필터' 클릭",
            "dataSource": "DEMO",
            "capturedAt": "2026-09-30T00:00:00+00:00",
            "originalMediaKey": None,
            "edit": None,
        }
    ]

    # AI 로 다듬은 사진이면 원본 키와 지시문이 같이 나간다(검토 화면이 나란히 대조한다).
    details["captures"][0] |= {"originalMediaKey": "raw-1", "edit": "목업에 넣음"}
    publisher.create("a" * 40, "캡션", ["k1"], details)
    assert sent["captures"][0]["originalMediaKey"] == "raw-1"
    assert sent["captures"][0]["edit"] == "목업에 넣음"
    assert sent["provenance"] == {
        "releaseTitle": "feat(web): 필터",
        "rationale": "필터가 새로 생겼어.",
        "runUrl": "https://github.com/o/r/actions/runs/1",
    }


def test_describe_interactions_reads_like_steps():
    from gole_promotion_agent import policy

    text = policy.describe_interactions(
        [{"kind": "click", "role": "button", "name": "필터"}, {"kind": "scroll", "to": "bottom"}]
    )

    assert text == "'필터' 클릭 → 맨 아래로 스크롤"


def test_demo_capture_excludes_price_screens():
    """데모 데이터로 찍을 때 시세 화면은 가짜 체결가가 사실처럼 읽히므로 막는다."""
    from gole_promotion_agent import policy

    assert policy.is_public_capture_route("/prices")
    assert not policy.is_public_capture_route("/prices", demo=True)
    assert policy.is_public_capture_route("/search", demo=True)


def test_demo_route_catalog_drops_prices(repo: Path):
    for route in ("prices", "search"):
        page = repo / "apps/web/src/app/(main)" / route / "page.tsx"
        page.parent.mkdir(parents=True, exist_ok=True)
        page.write_text("export default function Page() { return null; }", encoding="utf-8")

    assert "/prices" in AppRouteCatalog(repo).routes()
    assert "/prices" not in AppRouteCatalog(repo, demo=True).routes()


def test_demo_prompt_forbids_numbers_in_caption():
    from gole_promotion_agent import policy

    demo = policy.screen_note(True)
    bot = policy.screen_note(False)

    assert "데모 데이터" in demo and "가격" in demo
    assert "자기 데이터가 없다" in bot and "데모 데이터" not in bot
