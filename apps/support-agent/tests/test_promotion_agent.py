"""홍보 에이전트 회귀. 과거에 아무도 잡지 못한 결함을 직접 겨냥한다.

가장 중요한 것은 후보 선정이다 — 옛 구현은 `feat(` 커밋만 후보로 봤는데 운영 checkout은
squash 전용 `main`에 고정돼 있어 영구히 0건을 냈고, 0건이 예외가 아니라 정상 종료라
매일 초록으로 실패했다(스펙 D11).
"""

import json
import subprocess
from pathlib import Path

import pytest

from gole_promotion_agent import policy
from gole_promotion_agent.checkpoints import BinaryInCheckpoint, SessionSaver, reject_binary
from gole_promotion_agent.dryrun import FakeCamera, RecordingPublisher, ScriptedConversation
from gole_promotion_agent.hands import AppRouteCatalog, GitReleaseScanner, _blocks_from_transcript
from gole_promotion_agent.ports import ToolCall, Turn
from gole_promotion_agent.runtime import PromotionHarness, retry_ledger, skipped_ledger
from gole_promotion_agent.session import EphemeralSession, Stage


# --------------------------------------------------------------------- 도움 함수


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


# ------------------------------------------------------------------- 후보 선정


def test_release_scanner_finds_squash_release_commits(repo: Path):
    """옛 `feat(` 필터가 영구 0건을 내던 실패를 직접 겨냥한다."""
    first = _commit(repo, "chore(release): 첫 릴리스를 반영함 (#1)", web=True)
    second = _commit(repo, "chore(release): 둘째 릴리스를 반영함 (#2)", web=True)

    scanner = GitReleaseScanner(repo, lambda _sha: False)
    shas = [candidate.sha for candidate in scanner.candidates()]

    assert shas == [first, second], "squash 릴리스 커밋도 후보가 되어야 한다"


def test_release_scanner_stops_walking_at_promoted_release(repo: Path):
    """이미 홍보한 지점에서 멈춘다 — 시간창이 아니라 이력이 경계다."""
    _commit(repo, "chore(release): 아주 예전 릴리스", web=True)
    promoted = _commit(repo, "chore(release): 여기까지 홍보했다", web=True)
    newer = _commit(repo, "chore(release): 새 릴리스 하나", web=True)
    newest = _commit(repo, "chore(release): 새 릴리스 둘", web=True)

    scanner = GitReleaseScanner(repo, lambda sha: sha == promoted)
    shas = [candidate.sha for candidate in scanner.candidates()]

    assert shas == [newer, newest]


def test_release_scanner_returns_nothing_when_head_already_promoted(repo: Path):
    _commit(repo, "chore(release): 예전 것", web=True)
    head = _commit(repo, "chore(release): 방금 홍보함", web=True)

    scanner = GitReleaseScanner(repo, lambda sha: sha == head)

    assert scanner.candidates() == ()


def test_release_scanner_excludes_already_skipped_release(repo: Path):
    """한 번 건너뛴 커밋을 다시 유료로 평가하지 않는다(스펙 D11).

    건너뛴 결과는 백엔드에 남지 않아 `/exists` 가 계속 거짓이므로, 원장이 없으면 탐색 창에
    있는 동안 매 실행마다 모델을 다시 부른다.
    """
    skipped = _commit(repo, "chore(release): 홍보 가치가 없어 건너뛴 것", web=True)
    kept = _commit(repo, "chore(release): 아직 평가하지 않은 것", web=True)

    scanner = GitReleaseScanner(repo, lambda _sha: False, lambda sha: sha == skipped)

    assert [candidate.sha for candidate in scanner.candidates()] == [kept]


def test_skipped_release_does_not_swallow_older_candidates(repo: Path):
    """건너뛴 커밋은 경계가 아니라 개별 제외 대상이다 — break 면 아래가 통째로 사라진다."""
    older = _commit(repo, "chore(release): 아직 평가하지 않은 옛 릴리스", web=True)
    skipped = _commit(repo, "chore(release): 건너뛴 릴리스", web=True)
    newer = _commit(repo, "chore(release): 아직 평가하지 않은 새 릴리스", web=True)

    scanner = GitReleaseScanner(repo, lambda _sha: False, lambda sha: sha == skipped)
    shas = [candidate.sha for candidate in scanner.candidates()]

    assert shas == [older, newer], "건너뛴 커밋보다 오래된 후보가 살아남아야 한다"


def test_skipped_ledger_reads_manifest_written_by_harness(tmp_path: Path):
    """원장은 별도 저장소가 아니라 harness 가 이미 쓰는 manifest 다."""
    sessions = tmp_path / "sessions"
    done = "a" * 40
    submitted = "b" * 40
    for sha, outcome in ((done, "skipped"), (submitted, "submitted")):
        directory = sessions / sha
        directory.mkdir(parents=True)
        (directory / "manifest.json").write_text(
            json.dumps({"sha": sha, "subject": "x", "schema": 1, "done": outcome}),
            encoding="utf-8",
        )

    is_skipped = skipped_ledger(sessions)

    assert is_skipped(done) is True
    assert is_skipped(submitted) is False
    assert is_skipped("c" * 40) is False, "세션이 없으면 건너뛴 적이 없는 것이다"


def test_release_scanner_skips_releases_without_web_changes(repo: Path):
    _commit(repo, "chore(release): 문서만 고침", web=False)
    web = _commit(repo, "chore(release): 화면을 고침", web=True)

    scanner = GitReleaseScanner(repo, lambda _sha: False)

    assert [candidate.sha for candidate in scanner.candidates()] == [web]


def test_release_scanner_caps_candidates_per_run(repo: Path):
    for index in range(policy.MAX_DRAFTS_PER_RUN + 3):
        _commit(repo, f"chore(release): 릴리스 {index}", web=True)

    scanner = GitReleaseScanner(repo, lambda _sha: False)

    assert len(scanner.candidates()) == policy.MAX_DRAFTS_PER_RUN


def test_release_diff_rejects_foreign_sha(repo: Path):
    _commit(repo, "chore(release): 화면", web=True)
    scanner = GitReleaseScanner(repo, lambda _sha: False)

    with pytest.raises(ValueError):
        scanner.diff("not-a-sha")


# ----------------------------------------------------------------------- 라우트


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


# ------------------------------------------------------------------- 인증된 캡처


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


# ----------------------------------------------------------------- 체크포인트


def test_reject_binary_blocks_image_bytes():
    with pytest.raises(BinaryInCheckpoint):
        reject_binary({"captures": [{"data": b"\x89PNG"}]})


def test_session_saver_refuses_to_persist_image_bytes(tmp_path: Path):
    sha = "a" * 40
    saver = SessionSaver(tmp_path, sha)
    config = {"configurable": {"thread_id": sha, "checkpoint_ns": ""}}

    with pytest.raises(BinaryInCheckpoint):
        saver.put(
            config,
            {"id": "c1", "channel_values": {"screenshot": b"\x89PNG\r\n"}},
            {},
            {},
        )
    assert not (tmp_path / "checkpoints.jsonl").exists()


def test_session_saver_round_trips_across_process_restart(tmp_path: Path):
    sha = "b" * 40
    config = {"configurable": {"thread_id": sha, "checkpoint_ns": ""}}
    checkpoint = {"id": "c1", "channel_values": {"sha": sha, "turns": 3}}

    SessionSaver(tmp_path, sha).put(config, checkpoint, {"step": 1}, {})

    # 새 인스턴스 = 프로세스 재시작
    restored = SessionSaver(tmp_path, sha).get_tuple(config)

    assert restored is not None
    assert restored.checkpoint["channel_values"]["turns"] == 3
    stored = (tmp_path / "checkpoints.jsonl").read_text(encoding="utf-8")
    assert "\\x89PNG" not in stored and "iVBORw0" not in stored


def test_session_saver_rejects_other_thread(tmp_path: Path):
    saver = SessionSaver(tmp_path, "c" * 40)
    with pytest.raises(ValueError):
        saver.get_tuple({"configurable": {"thread_id": "d" * 40, "checkpoint_ns": ""}})


# --------------------------------------------------------------- 컨텍스트 압축


def test_transcript_keeps_only_recent_images(tmp_path: Path):
    png = tmp_path / "shot.png"
    png.write_bytes(b"\x89PNG\r\n\x1a\n")
    transcript = [{"role": "user", "text": "시작"}]
    for index in range(policy.MAX_CONTEXT_IMAGES + 2):
        transcript.append(
            {"role": "assistant", "text": "", "calls": [{"id": f"t{index}", "name": "capture"}]}
        )
        transcript.append(
            {
                "role": "tool",
                "results": [
                    {"call_id": f"t{index}", "text": "찍었어", "image_path": str(png)}
                ],
            }
        )

    messages = _blocks_from_transcript(transcript)
    images = [
        block
        for message in messages
        for entry in message["content"]
        if entry.get("type") == "tool_result"
        for block in entry["content"]
        if block.get("type") == "image"
    ]

    assert len(images) == policy.MAX_CONTEXT_IMAGES


# --------------------------------------------------------------------- 이력·게이트


def test_system_prompt_carries_history_including_rejections():
    prompt = policy.build_system_prompt(
        [
            {"status": "PENDING_REVIEW", "caption": "어제 올린 글이야."},
            {"status": "DRAFT", "caption": "반려된 글", "rejectionReason": "닉네임이 보임"},
        ]
    )

    assert "어제 올린 글이야." in prompt
    assert "닉네임이 보임" in prompt
    assert "반복하지 마" in prompt


def test_system_prompt_without_history_says_so():
    assert "첫 글" in policy.build_system_prompt([])


# ------------------------------------------------------------------ 캡처 캐시


class _CountingCamera(FakeCamera):
    def __init__(self, routes):
        super().__init__(routes)
        self.shots = 0

    def capture(self, route, interactions, destination):
        self.shots += 1
        super().capture(route, interactions, destination)


def _harness(tmp_path: Path, repo: Path, camera, publisher, conversation):
    return PromotionHarness(
        GitReleaseScanner(repo, publisher.exists),
        AppRouteCatalog(repo),
        camera,
        publisher,
        lambda *, system: conversation,
        tmp_path / "sessions",
    )


def _repo_with_page(repo: Path) -> str:
    page = repo / "apps/web/src/app/page.tsx"
    page.parent.mkdir(parents=True, exist_ok=True)
    page.write_text("export default function Page() {}\n", encoding="utf-8")
    return _commit(repo, "chore(release): 홈 화면을 고침", web=True)


def test_capture_is_not_repeated_for_same_route_and_interactions(tmp_path: Path, repo: Path):
    _repo_with_page(repo)
    camera = _CountingCamera(("/",))
    publisher = RecordingPublisher(tmp_path / "sessions")

    class TwiceThenSubmit(ScriptedConversation):
        def _plan_for(self, sha):
            shot = ("capture", {"route": "/", "interactions": [], "label": "첫째"})
            again = ("capture", {"route": "/", "interactions": [], "label": "둘째"})
            return [
                ("한 번 찍는다.", [shot]),
                ("같은 화면을 또 요청한다.", [again]),
                (
                    "초안을 낸다.",
                    [
                        (
                            "submit_promotion_draft",
                            {"sha": sha, "caption": "좋아.", "screenshot_labels": ["첫째"]},
                        )
                    ],
                ),
            ]

    result = _harness(tmp_path, repo, camera, publisher, TwiceThenSubmit("/")).run()

    assert [item.outcome for item in result.candidates] == ["submitted"]
    assert camera.shots == 1, "같은 (route, interactions)는 다시 찍지 않아야 한다"


# ------------------------------------------------------------------------ 재개


class _FlakyPublisher(RecordingPublisher):
    """업로드 직후 한 번만 터진다 — 고아 STAGED 이미지(07a0354c)를 재현한다."""

    def __init__(self, session_root: Path):
        super().__init__(session_root)
        self.uploads = 0
        self.creates = 0
        self.explode = True

    def upload(self, paths):
        self.uploads += 1
        return super().upload(paths)

    def create(self, sha, caption, media_keys):
        if self.explode:
            self.explode = False
            raise RuntimeError("BACKEND_DOWN")
        self.creates += 1
        return super().create(sha, caption, media_keys)


def test_resume_does_not_reupload_after_crash(tmp_path: Path, repo: Path):
    _repo_with_page(repo)
    publisher = _FlakyPublisher(tmp_path / "sessions")
    camera = _CountingCamera(("/",))

    first = _harness(tmp_path, repo, camera, publisher, ScriptedConversation("/")).run()
    assert [item.outcome for item in first.candidates] == ["failed"]
    assert publisher.uploads == 1

    second = _harness(tmp_path, repo, camera, publisher, ScriptedConversation("/")).run()

    assert [item.outcome for item in second.candidates] == ["submitted"]
    assert second.candidates[0].resumed is True
    assert publisher.uploads == 1, "재개는 이미 올린 이미지를 다시 올리지 않아야 한다"
    assert publisher.creates == 1
    assert camera.shots == 1, "재개는 이미 찍은 화면을 다시 찍지 않아야 한다"


# ------------------------------------------------------------------- 큐 게이트


def test_run_skips_when_review_queue_is_full(tmp_path: Path, repo: Path):
    _repo_with_page(repo)
    publisher = RecordingPublisher(tmp_path / "sessions", pending=policy.MAX_PENDING_REVIEW)
    camera = _CountingCamera(("/",))

    result = _harness(tmp_path, repo, camera, publisher, ScriptedConversation("/")).run()

    assert result.candidates == ()
    assert camera.shots == 0
    assert "검토 대기" in result.reason


# --------------------------------------------------------------------- 세션 단계


def test_session_rejects_transition_after_terminal():
    session = EphemeralSession()
    session.advance(Stage.EXPLORING)
    session.advance(Stage.SKIPPED)

    with pytest.raises(ValueError):
        session.advance(Stage.DRAFTING)


# --------------------------------------------------------------------- 드라이런


def test_dry_run_records_without_network(tmp_path: Path, repo: Path):
    _repo_with_page(repo)
    publisher = RecordingPublisher(tmp_path / "sessions")

    result = _harness(
        tmp_path, repo, FakeCamera(("/",)), publisher, ScriptedConversation("/")
    ).run()

    assert [item.outcome for item in result.candidates] == ["submitted"]
    recorded = [
        json.loads(line)
        for line in (tmp_path / "sessions" / "dry-run.jsonl").read_text(encoding="utf-8").splitlines()
    ]
    assert [entry["action"] for entry in recorded] == ["upload", "create", "finalize"]


# ------------------------------------------------------- 실패는 다시 본다 (리뷰 후속)


def _manifest(sessions: Path, sha: str, done: str) -> None:
    (sessions / sha).mkdir(parents=True, exist_ok=True)
    (sessions / sha / "manifest.json").write_text(
        json.dumps({"sha": sha, "subject": "x", "schema": 1, "done": done}, ensure_ascii=False),
        encoding="utf-8",
    )


def test_failed_release_survives_the_promoted_boundary(repo: Path, tmp_path: Path):
    """실패한 옛 후보가 나중 성공에 가려 영영 사라지면 안 된다.

    건너뛴 커밋에 대해서는 이미 막아 둔 함정(`test_skipped_release_does_not_swallow_older_candidates`)이
    실패 경로에만 남아 있었다. 홍보 경계에서 walk 가 멈추므로 원장으로 되살려야 한다.
    """
    failed = _commit(repo, "chore(release): 처리 중 실패한 옛 릴리스", web=True)
    promoted = _commit(repo, "chore(release): 그 뒤에 홍보 성공한 릴리스", web=True)

    sessions = tmp_path / "sessions"
    _manifest(sessions, failed, "failed")

    blind = GitReleaseScanner(repo, lambda sha: sha == promoted)
    assert blind.candidates() == (), "원장이 없으면 경계에 가려 사라진다(고치기 전 동작)"

    scanner = GitReleaseScanner(
        repo, lambda sha: sha == promoted, None, retry_ledger(sessions)
    )
    assert [c.sha for c in scanner.candidates()] == [failed]


def test_deferred_release_is_retried_but_skipped_one_is_not(repo: Path, tmp_path: Path):
    """예산 소진(deferred)과 판단(skipped)을 다르게 다룬다."""
    deferred = _commit(repo, "chore(release): 턴을 다 써서 결론을 못 낸 릴리스", web=True)
    skipped = _commit(repo, "chore(release): 홍보 가치가 없다고 판단한 릴리스", web=True)

    sessions = tmp_path / "sessions"
    _manifest(sessions, deferred, "deferred")
    _manifest(sessions, skipped, "skipped")

    scanner = GitReleaseScanner(
        repo, lambda _sha: False, skipped_ledger(sessions), retry_ledger(sessions)
    )
    assert [c.sha for c in scanner.candidates()] == [deferred]


def test_retry_ledger_ignores_submitted_and_broken_manifests(tmp_path: Path):
    sessions = tmp_path / "sessions"
    _manifest(sessions, "a" * 40, "submitted")
    _manifest(sessions, "b" * 40, "failed")
    (sessions / "stray").mkdir(parents=True, exist_ok=True)
    (sessions / "broken").mkdir(parents=True, exist_ok=True)
    (sessions / "broken" / "manifest.json").write_text("{", encoding="utf-8")

    assert retry_ledger(sessions)() == ("b" * 40,)


def test_turn_budget_exhaustion_defers_instead_of_skipping(tmp_path: Path, repo: Path):
    """20턴을 다 써도 '안 하기로 했음'으로 기록하지 않는다."""
    _repo_with_page(repo)
    publisher = RecordingPublisher(tmp_path / "sessions")

    class _NeverFinishes(ScriptedConversation):
        def advance(self, transcript):
            # 매 턴 같은 도구만 불러 결론에 도달하지 않는다.
            return Turn("", (ToolCall(f"c{len(transcript)}", "list_routes", {}),), "tool_use")

    result = _harness(tmp_path, repo, FakeCamera(("/",)), publisher, _NeverFinishes("/")).run()

    assert [item.outcome for item in result.candidates] == ["deferred"]
    manifest = json.loads(
        (tmp_path / "sessions" / result.candidates[0].sha / "manifest.json").read_text(
            encoding="utf-8"
        )
    )
    assert manifest["done"] == "deferred"
    assert skipped_ledger(tmp_path / "sessions")(result.candidates[0].sha) is False


def test_last_allowed_turn_can_submit(tmp_path: Path, repo: Path, monkeypatch):
    """마지막 턴에 모델이 결정한 제출을 버리지 않는다."""
    _repo_with_page(repo)
    monkeypatch.setattr(policy, "MAX_TURNS", 4)
    publisher = RecordingPublisher(tmp_path / "sessions")

    result = _harness(
        tmp_path, repo, FakeCamera(("/",)), publisher, ScriptedConversation("/")
    ).run()

    assert [item.outcome for item in result.candidates] == ["submitted"]


def test_deferred_checkpoint_actually_continues_next_run(tmp_path: Path, repo: Path, monkeypatch):
    """종료된 보류 체크포인트를 다시 읽는 것만으로는 재개가 되지 않는다."""
    _repo_with_page(repo)
    monkeypatch.setattr(policy, "MAX_TURNS", 2)
    publisher = RecordingPublisher(tmp_path / "sessions")
    camera = _CountingCamera(("/",))

    first = _harness(tmp_path, repo, camera, publisher, ScriptedConversation("/")).run()
    assert [item.outcome for item in first.candidates] == ["deferred"]

    second = _harness(tmp_path, repo, camera, publisher, ScriptedConversation("/")).run()

    assert [item.outcome for item in second.candidates] == ["submitted"]
    assert second.candidates[0].resumed is True
    assert camera.shots == 1


def test_interrupted_manifest_is_retryable(tmp_path: Path):
    """강제 종료는 실패 표식을 쓸 기회조차 없으므로 미완료 후보도 복구한다."""
    sessions = tmp_path / "sessions"
    sha = "a" * 40
    (sessions / sha).mkdir(parents=True)
    (sessions / sha / "manifest.json").write_text(
        json.dumps({"sha": sha, "subject": "중단된 릴리스", "schema": 1}), encoding="utf-8"
    )

    assert retry_ledger(sessions)() == (sha,)


def test_run_stops_before_filling_review_queue(tmp_path: Path, repo: Path):
    """처음 한 번 검사한 여유보다 많은 초안을 같은 배치가 밀어 넣지 않는다."""
    _repo_with_page(repo)
    _commit(repo, "chore(release): 두 번째 화면", web=True)
    publisher = RecordingPublisher(tmp_path / "sessions", pending=policy.MAX_PENDING_REVIEW - 1)
    camera = _CountingCamera(("/",))

    result = _harness(tmp_path, repo, camera, publisher, ScriptedConversation("/")).run()

    assert [item.outcome for item in result.candidates] == ["submitted"]
    assert camera.shots == 1


def test_candidate_initialization_failure_does_not_abort_later_candidates(tmp_path: Path, repo: Path):
    """한 후보의 모델 초기화 실패도 실패 원장에 남기고 다음 후보를 처리한다."""
    _repo_with_page(repo)
    _commit(repo, "chore(release): 두 번째 화면", web=True)
    publisher = RecordingPublisher(tmp_path / "sessions")
    calls = 0

    def factory(*, system):
        nonlocal calls
        calls += 1
        if calls == 1:
            raise RuntimeError("PROVIDER_INIT_FAILED")
        return ScriptedConversation("/")

    result = PromotionHarness(
        GitReleaseScanner(repo, publisher.exists), AppRouteCatalog(repo), FakeCamera(("/",)),
        publisher, factory, tmp_path / "sessions"
    ).run()

    assert [item.outcome for item in result.candidates] == ["failed", "submitted"]
    assert retry_ledger(tmp_path / "sessions")() == (result.candidates[0].sha,)


# ----------------------------------------------------------- 캡처 이탈 / 컨텍스트


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

    patch = GitReleaseScanner(repo, lambda _s: False).diff(sha)

    assert len(patch) <= policy.MAX_DIFF_CHARS + len(policy.DIFF_TRUNCATED_NOTICE)
    assert patch.endswith(policy.DIFF_TRUNCATED_NOTICE)


def test_model_request_marks_a_cache_breakpoint(tmp_path: Path):
    """캐싱은 브레이크포인트가 있어야 걸린다 — 스펙 D18 이 주장하던 것."""
    from gole_promotion_agent.hands import AnthropicConversation

    captured: dict[str, object] = {}

    class _Messages:
        def create(self, **kwargs):
            captured.update(kwargs)

            class _Reply:
                stop_reason = "end_turn"
                content: list[object] = []

            return _Reply()

    class _Client:
        messages = _Messages()

    AnthropicConversation(_Client(), "SYSTEM", "claude-opus-5").advance([])

    system = captured["system"]
    assert isinstance(system, list) and system[0]["cache_control"] == {"type": "ephemeral"}
    assert system[0]["text"] == "SYSTEM"
