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
from gole_promotion_agent.runtime import PromotionHarness
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
