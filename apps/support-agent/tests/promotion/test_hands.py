"""외부 세계를 만지는 손(hands) — 릴리스 스캐너·라우트 목록·브라우저 카메라·백엔드 제출 계약."""

from pathlib import Path

import pytest

from gole_promotion_agent import policy
from gole_promotion_agent.hands import AppRouteCatalog, ReleaseScanner

from .gitrepo import commit, git


def test_release_scanner_returns_only_given_release(repo: Path):
    target = commit(repo, "feat(web): 지정 릴리스", web=True)
    commit(repo, "feat(web): 그 뒤 릴리스", web=True)

    scanner = ReleaseScanner(repo, target)

    assert scanner.touches_web() and scanner.subject() == "feat(web): 지정 릴리스"


def test_release_scanner_skips_release_without_web_changes(repo: Path):
    target = commit(repo, "fix(api): 백엔드만", web=False)

    assert not ReleaseScanner(repo, target).touches_web()


def _release_merge(repo: Path, *, web: bool) -> str:
    """dev 에서 작업한 뒤 main 에 머지 커밋으로 릴리스한다(2026-10-03 부터의 릴리스 모양)."""
    commit(repo, "chore: 기준선", web=False)
    git(repo, "switch", "-q", "-c", "dev")
    commit(repo, "feat: dev 작업", web=web)
    git(repo, "switch", "-q", "main")
    git(repo, "-c", "user.email=t@t.test", "-c", "user.name=t", "merge", "-q", "--no-ff", "-m", "release", "dev")
    return git(repo, "rev-parse", "HEAD")


def test_release_scanner_sees_web_changes_in_release_merge_commit(repo: Path):
    scanner = ReleaseScanner(repo, _release_merge(repo, web=True))

    assert scanner.touches_web() and "apps/web/src/app/page.tsx" in scanner.diff()


def test_release_scanner_skips_release_merge_without_web_changes(repo: Path):
    assert not ReleaseScanner(repo, _release_merge(repo, web=False)).touches_web()


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
    camera.capture("/", tmp_path / "shot.png")
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
    git(repo, "add", "-A")
    git(repo, "-c", "user.email=t@t.test", "-c", "user.name=t", "commit", "-q", "-m", "big")
    sha = git(repo, "rev-parse", "HEAD")

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


def test_demo_route_catalog_drops_prices(repo: Path):
    for route in ("prices", "search"):
        page = repo / "apps/web/src/app/(main)" / route / "page.tsx"
        page.parent.mkdir(parents=True, exist_ok=True)
        page.write_text("export default function Page() { return null; }", encoding="utf-8")

    assert "/prices" in AppRouteCatalog(repo).routes()
    assert "/prices" not in AppRouteCatalog(repo, demo=True).routes()
