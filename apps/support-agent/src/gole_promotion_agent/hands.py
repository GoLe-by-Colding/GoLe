"""외부 세계를 만지는 구현. git·브라우저·모델 SDK·백엔드 HTTP가 전부 여기 있다.

SDK import는 전부 지연시킨다 — 드라이런 경로가 이 모듈을 쓰더라도 외부 클라이언트가
인스턴스화되지 않아야 한다(스펙 D16).
"""

from __future__ import annotations

import base64
import os
import subprocess
from pathlib import Path
from typing import Any, Callable, Iterable, Mapping, Sequence
from urllib.parse import urlsplit

from gole_promotion_agent import policy
from gole_promotion_agent.ports import Candidate, DraftRequest, ToolCall, Turn


class ProviderUnavailable(Exception):
    pass


# --------------------------------------------------------------------------- git


def _git(repo: Path, *args: str, limit: int | None = None) -> str:
    # 컨테이너 uid 와 /repo 소유자가 다르면 git 이 dubious ownership 으로 거부한다.
    # 저장소의 다른 코드(bootstrap-host.sh)도 같은 이유로 safe.directory 를 붙인다.
    result = subprocess.run(
        ["git", "-c", f"safe.directory={repo}", *args],
        cwd=str(repo),
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=60,
        check=True,
    )
    return result.stdout if limit is None else result.stdout[:limit]


class CommitNotFound(Exception):
    """관리자가 지정한 커밋이 이 체크아웃에 없다."""


class PinnedReleaseScanner:
    """관리자가 지정한 커밋 하나만 내놓는다(스펙 D20).

    **로컬 휴리스틱을 일절 보지 않는다** — 이미 홍보했는지, 건너뛴 적이 있는지,
    `apps/web/src` 를 건드렸는지, 7일 창 안인지 모두 무시한다. 그 판단은 Java 가 접수
    시점에 점유(`claimedSourceCommitSha`)와 3회 상한으로 이미 끝냈고, 여기서 한 번 더
    거르면 **관리자 눈에는 아무 일도 안 일어난 것처럼 보인다** — 이번 변경이 없애려는
    바로 그 증상이다.

    자동 경로는 그대로 `GitReleaseScanner` 가 담당한다. 원장은 그쪽 휴리스틱이다.
    """

    def __init__(self, repo: Path, sha: str):
        if not policy.SHA_PATTERN.match(sha):
            raise ValueError("INVALID_SHA")
        self._repo = Path(repo)
        self._sha = sha
        self._delegate = GitReleaseScanner(repo, lambda _sha: False)

    def candidates(self) -> tuple[Candidate, ...]:
        try:
            listed = _git(self._repo, "log", "-1", "--format=%H%x09%s", self._sha)
        except subprocess.CalledProcessError as failure:
            # 운영 체크아웃은 squash 전용 main 에 고정돼 있어 지정한 SHA 가 없을 수 있다.
            raise CommitNotFound(self._sha) from failure
        sha, _, subject = listed.strip().partition("	")
        if not policy.SHA_PATTERN.match(sha):
            raise CommitNotFound(self._sha)
        return (Candidate(sha, subject),)

    def diff(self, sha: str) -> str:
        return self._delegate.diff(sha)


class GitReleaseScanner:
    """이미 홍보한 지점까지 뒤로 걸으며 후보를 고른다(스펙 D11).

    시간창 대신 이 방식을 쓰는 이유는 실행이 밀려도 릴리스를 빠뜨리지 않기 때문이다.
    """

    def __init__(
        self,
        repo: Path,
        is_promoted: Callable[[str], bool],
        is_skipped: Callable[[str], bool] | None = None,
        retryable: Callable[[], tuple[str, ...]] | None = None,
        ref: str = "HEAD",
        max_commits: int = policy.MAX_WALK_COMMITS,
        max_days: int = policy.MAX_WALK_DAYS,
        max_candidates: int = policy.MAX_DRAFTS_PER_RUN,
    ):
        self._repo = Path(repo)
        self._is_promoted = is_promoted
        # 건너뛴 커밋은 경계가 아니라 개별 제외 대상이다 — 아래 candidates() 참고.
        self._is_skipped = is_skipped or (lambda _sha: False)
        # 실패·중단으로 "다시 볼 것"으로 남은 커밋이다. 홍보 경계보다 오래되면 walk 가 닿지 못하므로
        # 원장에서 따로 받아 되살린다 — 없으면 앞선 실패가 조용히 영영 사라진다.
        self._retryable = retryable or (lambda: ())
        self._ref = ref
        self._max_commits = max_commits
        self._max_days = max_days
        self._max_candidates = max_candidates
        self._cache: tuple[Candidate, ...] | None = None

    def _touches_web(self, sha: str) -> bool:
        changed = _git(
            self._repo,
            "diff-tree",
            "--no-commit-id",
            "--name-only",
            "-r",
            # --root 가 없으면 부모 없는 최초 커밋이 조용히 빈 diff 를 낸다.
            "--root",
            sha,
            "--",
            "apps/web/src",
        )
        return bool(changed.strip())

    def candidates(self) -> tuple[Candidate, ...]:
        if self._cache is not None:
            return self._cache
        listed = _git(
            self._repo,
            "log",
            f"-n{self._max_commits}",
            f"--since={self._max_days} days ago",
            "--format=%H%x09%s",
            self._ref,
        )
        window: list[tuple[str, str]] = []
        for line in listed.splitlines():
            sha, _, subject = line.partition("\t")
            if policy.SHA_PATTERN.match(sha):
                window.append((sha, subject))

        chosen: set[str] = set()
        for sha, _subject in window:
            if self._is_promoted(sha):
                # 여기서부터는 이미 홍보한 영역이다. 다만 경계 너머에 "다시 볼 것"으로 남은 커밋이
                # 있을 수 있어, 아래에서 원장을 보고 되살린다.
                break
            if self._is_skipped(sha):
                # 이미 평가해서 홍보하지 않기로 한 커밋이다. **break 가 아니라 continue 다** —
                # 건너뛴 커밋은 "여기까지 처리했다"는 경계가 아니라 개별 제외 대상이라,
                # break 하면 그보다 오래된 후보가 통째로 조용히 사라진다.
                continue
            if self._touches_web(sha):
                chosen.add(sha)

        # 실패·중단으로 끝난 커밋을 되살린다. walk 가 홍보 경계에서 멈추므로, 그보다 오래된
        # 실패는 경계에 가려 영영 후보가 되지 못한다 — 건너뛴 커밋에 대해 이미 막아 둔 함정이
        # 실패 경로에만 남아 있었다. 탐색 창 밖으로 밀려난 것은 되살리지 않는다.
        in_window = {sha for sha, _ in window}
        for sha in self._retryable():
            if sha in in_window and not self._is_promoted(sha):
                chosen.add(sha)

        # 오래된 것부터 처리한다 — 이야기 순서가 시간 순서와 같아야 한다.
        ordered = [Candidate(sha, subject) for sha, subject in reversed(window) if sha in chosen]
        self._cache = tuple(ordered)[: self._max_candidates]
        return self._cache

    def diff(self, sha: str) -> str:
        if not policy.SHA_PATTERN.match(sha):
            raise ValueError("INVALID_SHA")
        patch = _git(
            self._repo,
            "diff-tree",
            "--no-commit-id",
            "--patch",
            "--stat",
            "-r",
            "--root",
            sha,
            "--",
            "apps/web/src",
            limit=policy.MAX_DIFF_CHARS + 1,
        )
        if not patch.strip():
            raise ValueError("NO_WEB_CHANGES")
        if len(patch) > policy.MAX_DIFF_CHARS:
            return patch[: policy.MAX_DIFF_CHARS] + policy.DIFF_TRUNCATED_NOTICE
        return patch


# ------------------------------------------------------------------------ routes


class AppRouteCatalog:
    """`apps/web/src/app/**/page.tsx`에서 정적 공개 라우트만 열거한다."""

    def __init__(self, repo: Path):
        self._app_dir = Path(repo) / "apps" / "web" / "src" / "app"

    def routes(self) -> tuple[str, ...]:
        if not self._app_dir.is_dir():
            return ()
        found: set[str] = set()
        for page in self._app_dir.rglob("page.tsx"):
            route = self._route_of(page)
            if route is not None and policy.is_public_capture_route(route):
                found.add(route)
        return tuple(sorted(found))

    def _route_of(self, page: Path) -> str | None:
        relative = page.relative_to(self._app_dir).as_posix()
        directory = relative[: -len("/page.tsx")] if relative != "page.tsx" else ""
        segments = [
            segment
            for segment in directory.split("/")
            if segment and not (segment.startswith("(") and segment.endswith(")"))
        ]
        if any(segment.startswith("[") or segment.endswith("]") for segment in segments):
            # 동적 세그먼트는 실제 인스턴스를 고르는 규칙이 없어 제외한다(스펙 D12·T8).
            return None
        return "/" + "/".join(segments) if segments else "/"


# ----------------------------------------------------------------------- browser


class PlaywrightCamera:
    """선언적 캡처(스펙 D12). 호출마다 새 컨텍스트를 열어 상태를 남기지 않는다."""

    def __init__(
        self,
        base_url: str,
        allowed_routes: Iterable[str],
        session_provider: Callable[[], Mapping[str, Any]] | None = None,
    ):
        self._base_url = base_url.rstrip("/")
        self._allowed = frozenset(allowed_routes)
        # None 이면 익명으로 찍는다 — 드라이런과 단위 테스트가 백엔드 없이 살아야 한다.
        self._session_provider = session_provider
        self._playwright: Any = None
        self._browser: Any = None

    def _ensure_browser(self) -> Any:
        if self._browser is None:
            from playwright.sync_api import sync_playwright

            self._playwright = sync_playwright().start()
            self._browser = self._playwright.chromium.launch(
                args=["--disable-dev-shm-usage", "--no-sandbox"]
            )
        return self._browser

    def _guard(self, route_handler: Any) -> None:
        request = route_handler.request
        if request.method.upper() not in policy.READ_ONLY_METHODS:
            route_handler.abort("blockedbyclient")
            return
        route_handler.continue_()

    def capture(
        self,
        route: str,
        interactions: Sequence[Mapping[str, Any]],
        destination: Path,
    ) -> None:
        if route not in self._allowed:
            raise ValueError("ROUTE_NOT_ALLOWED")
        destination = Path(destination)
        destination.parent.mkdir(parents=True, exist_ok=True)
        context = self._ensure_browser().new_context(viewport={"width": 1440, "height": 1024})
        try:
            context.set_default_timeout(policy.INTERACTION_TIMEOUT_SECONDS * 1000)
            context.route("**/*", self._guard)
            # 네비게이션 전에 심어야 첫 페인트부터 로그인 상태다. 컨텍스트는 이 호출이
            # 끝나면 버려지므로 토큰이 캡처 사이에 남지 않는다.
            if self._session_provider is not None:
                context.add_init_script(policy.session_init_script(self._session_provider()))
            context.add_init_script(policy.capture_chrome_script())
            page = context.new_page()
            page.goto(
                f"{self._base_url}{route}",
                wait_until="domcontentloaded",
                timeout=policy.NAVIGATION_TIMEOUT_SECONDS * 1000,
            )
            page.wait_for_timeout(1_000)
            for index, step in enumerate(interactions):
                self._interact(page, step, index)
            # 라우트 허용 검사는 **이동 시작점에만** 걸린다. 상호작용이 클릭으로 다른 화면에 데려갈 수
            # 있는데, 봇은 로그인 상태라 그 끝이 /profile·/notifications 같은 사설 화면일 수 있다.
            # 찍기 직전에 지금 서 있는 곳을 다시 확인한다(D12·D19).
            self._assert_still_allowed(page.url)
            page.screenshot(path=str(destination), animations="disabled")
        finally:
            context.close()

    def _assert_still_allowed(self, current: str) -> None:
        """상호작용 뒤에도 허용된 공개 화면에 서 있는지 확인한다."""
        if not current.startswith(f"{self._base_url}/") and current != self._base_url:
            raise ValueError("NAVIGATED_OFF_SITE")
        path = urlsplit(current).path or "/"
        if path != "/" and path.endswith("/"):
            path = path.rstrip("/")
        if path not in self._allowed or not policy.is_public_capture_route(path):
            raise ValueError("NAVIGATED_TO_FORBIDDEN_ROUTE")

    def _interact(self, page: Any, step: Mapping[str, Any], index: int) -> None:
        kind = step.get("kind")
        timeout = policy.INTERACTION_TIMEOUT_SECONDS * 1000
        try:
            if kind == "click":
                page.get_by_role(step["role"], name=step["name"], exact=True).click(timeout=timeout)
            elif kind == "select":
                page.get_by_label(step["label"], exact=True).select_option(
                    step["value"], timeout=timeout
                )
            elif kind == "scroll":
                target = 0 if step["to"] == "top" else "document.body.scrollHeight"
                page.evaluate(f"window.scrollTo(0, {target})")
            else:
                raise ValueError("UNKNOWN_INTERACTION")
            page.wait_for_timeout(500)
        except Exception as error:  # 부분 성공 화면을 남기지 않는다.
            raise ValueError(f"INTERACTION_FAILED: {kind} #{index}") from error

    def close(self) -> None:
        if self._browser is not None:
            self._browser.close()
            self._browser = None
        if self._playwright is not None:
            self._playwright.stop()
            self._playwright = None


# ------------------------------------------------------------------------- model


def _blocks_from_transcript(transcript: Sequence[Mapping[str, Any]]) -> list[dict[str, Any]]:
    """전사에서 요청 메시지를 다시 만든다 — 재개했을 때도 같은 맥락이 나와야 한다."""
    retained = 0
    keep: set[int] = set()
    # 뒤에서부터 세어 최근 이미지만 남긴다(스펙 D17).
    for position in range(len(transcript) - 1, -1, -1):
        entry = transcript[position]
        if entry.get("role") != "tool":
            continue
        for order in range(len(entry.get("results", [])) - 1, -1, -1):
            if not entry["results"][order].get("image_path"):
                continue
            retained += 1
            if retained <= policy.MAX_CONTEXT_IMAGES:
                keep.add((position << 8) | order)

    messages: list[dict[str, Any]] = []
    for position, entry in enumerate(transcript):
        role = entry.get("role")
        if role == "user":
            messages.append({"role": "user", "content": [{"type": "text", "text": entry["text"]}]})
        elif role == "assistant":
            content: list[dict[str, Any]] = []
            if entry.get("text"):
                content.append({"type": "text", "text": entry["text"]})
            for call in entry.get("calls", []):
                content.append(
                    {
                        "type": "tool_use",
                        "id": call["id"],
                        "name": call["name"],
                        "input": call.get("arguments", {}),
                    }
                )
            messages.append({"role": "assistant", "content": content or [{"type": "text", "text": "."}]})
        elif role == "tool":
            blocks: list[dict[str, Any]] = []
            for order, result in enumerate(entry.get("results", [])):
                inner: list[dict[str, Any]] = [{"type": "text", "text": result["text"]}]
                image_path = result.get("image_path")
                if image_path:
                    if (position << 8) | order in keep:
                        data = base64.b64encode(Path(image_path).read_bytes()).decode("ascii")
                        inner.append(
                            {
                                "type": "image",
                                "source": {
                                    "type": "base64",
                                    "media_type": "image/png",
                                    "data": data,
                                },
                            }
                        )
                    else:
                        inner.append(
                            {
                                "type": "text",
                                "text": f"이전 스크린샷은 파일 경로 요약으로 대체됨: {image_path}",
                            }
                        )
                blocks.append(
                    {
                        "type": "tool_result",
                        "tool_use_id": result["call_id"],
                        "content": inner,
                        "is_error": bool(result.get("is_error")),
                    }
                )
            messages.append({"role": "user", "content": blocks})
    return messages


class AnthropicConversation:
    def __init__(self, client: Any, system: str, model: str):
        self._client = client
        self._system = system
        self._model = model

    def advance(self, transcript: Sequence[Mapping[str, Any]]) -> Turn:
        try:
            message = self._client.messages.create(
                model=self._model,
                max_tokens=policy.MAX_TOKENS,
                # 브레이크포인트가 없으면 캐싱이 **아예 걸리지 않는다.** 이력을 시스템 프롬프트에
                # 둔 이유(D18)가 여기서 값을 받는다 — tools + system 이 안정 접두사가 되어
                # 매 턴 같은 앞부분을 다시 계산하지 않는다. 확인은 응답의
                # usage.cache_read_input_tokens 가 0 이 아닌지로 한다.
                system=[
                    {
                        "type": "text",
                        "text": self._system,
                        "cache_control": {"type": "ephemeral"},
                    }
                ],
                tools=list(policy.tool_schemas()),
                messages=_blocks_from_transcript(transcript),
            )
        except Exception as error:
            raise ProviderUnavailable(str(type(error).__name__)) from None
        if message.stop_reason == "refusal":
            return Turn("", (), "refusal")
        text = "".join(
            block.text for block in message.content if getattr(block, "type", "") == "text"
        )
        calls = tuple(
            ToolCall(block.id, block.name, dict(block.input))
            for block in message.content
            if getattr(block, "type", "") == "tool_use"
        )
        return Turn(text, calls, message.stop_reason or "end_turn")


def anthropic_conversation_factory() -> Callable[..., AnthropicConversation]:
    """이중 opt-in — 환경이 명시적으로 허용하지 않으면 기동하지 않는다(스펙 D16)."""
    if os.environ.get("PROMOTION_AGENT_ANTHROPIC_ENABLED") != "true":
        raise ValueError("EXTERNAL_DISABLED")
    key = os.environ.get("ANTHROPIC_API_KEY")
    if not key:
        raise ValueError("ANTHROPIC_KEY_REQUIRED")
    from anthropic import Anthropic

    # 예전 0 은 429·5xx 한 번에 후보 하나가 통째로 실패한다는 뜻이었다. 하루 한 번 도는
    # 배치라 재시도가 사람을 기다리게 하지 않는다.
    client = Anthropic(api_key=key, max_retries=2)
    model = os.environ.get("PROMOTION_AGENT_MODEL", policy.DEFAULT_MODEL)

    def factory(*, system: str) -> AnthropicConversation:
        return AnthropicConversation(client, system, model)

    return factory


# ----------------------------------------------------------------------- backend


class BackendPublisher:
    """봇 전용 ADMIN 계정으로 로그인해 초안을 만든다. 토큰은 여기 밖으로 나가지 않는다."""

    def __init__(self, base_url: str, email: str, password: str, client: Any | None = None):
        self._base_url = base_url.rstrip("/")
        self._email = email
        self._password = password
        self._client = client
        self._session: dict[str, Any] | None = None

    def _http(self) -> Any:
        if self._client is None:
            import httpx

            self._client = httpx.Client(base_url=self._base_url, timeout=30.0)
        return self._client

    def _sign_in(self) -> dict[str, Any]:
        if self._session is None:
            response = self._http().post(
                "/api/v1/accounts/sessions",
                json={"email": self._email, "password": self._password},
            )
            response.raise_for_status()
            session = response.json()
            if session.get("role") != "ADMIN":
                raise ValueError("BOT_ACCOUNT_NOT_ADMIN")
            if not session.get("sessionToken"):
                raise ValueError("SESSION_TOKEN_MISSING")
            self._session = dict(session)
        return self._session

    def _headers(self) -> dict[str, str]:
        return {"Authorization": f"Bearer {self._sign_in()['sessionToken']}"}

    def browser_session(self) -> Mapping[str, Any]:
        """캡처 컨텍스트에 심을 세션. 제출과 같은 토큰을 재사용해 로그인을 두 번 하지 않는다."""
        return self._sign_in()

    def exists(self, sha: str) -> bool:
        response = self._http().get(
            "/api/admin/promotion-posts/exists",
            params={"sourceCommitSha": sha},
            headers=self._headers(),
        )
        response.raise_for_status()
        return bool(response.json().get("exists"))

    def _list(self, status: str | None, limit: int) -> list[dict[str, Any]]:
        params: dict[str, Any] = {"limit": limit}
        if status:
            params["status"] = status
        response = self._http().get(
            "/api/admin/promotion-posts", params=params, headers=self._headers()
        )
        response.raise_for_status()
        return list(response.json())

    def pending_count(self) -> int:
        return len(self._list("PENDING_REVIEW", policy.MAX_PENDING_REVIEW + 1))

    def history(self, limit: int) -> tuple[Mapping[str, Any], ...]:
        # 발행이 스텁인 동안 PUBLISHED 는 계속 0건이라 전체 상태를 본다(스펙 D18).
        return tuple(self._list(None, limit))

    def upload(self, paths: Sequence[Path]) -> tuple[str, ...]:
        files = [
            ("files", (Path(path).name, Path(path).read_bytes(), "image/png")) for path in paths
        ]
        response = self._http().post(
            "/api/v1/media/images/batch", files=files, headers=self._headers()
        )
        response.raise_for_status()
        return tuple(item["key"] for item in response.json())

    def create(self, sha: str, caption: str, media_keys: Sequence[str]) -> str:
        response = self._http().post(
            "/api/admin/promotion-posts",
            json={
                "channel": "THREADS",
                "caption": caption,
                "mediaKeys": list(media_keys),
                "sourceCommitSha": sha,
            },
            headers=self._headers(),
        )
        response.raise_for_status()
        return response.json()["id"]

    def finalize(self, post_id: str) -> None:
        response = self._http().post(
            f"/api/admin/promotion-posts/{post_id}/submit", headers=self._headers()
        )
        response.raise_for_status()


class BackendDraftRequestQueue:
    """관리자 요청을 백엔드에서 집어오고 결과를 회신한다(스펙 D20).

    `BackendPublisher` 의 세션을 그대로 재사용한다 — 같은 봇 ADMIN 계정이고, 로그인을 두 번
    할 이유가 없다.
    """

    def __init__(self, publisher: "BackendPublisher"):
        self._publisher = publisher

    def claim(self) -> DraftRequest | None:
        response = self._publisher._http().post(
            "/api/admin/promotion-posts/requests/claim", headers=self._publisher._headers()
        )
        if response.status_code == 204:
            return None
        response.raise_for_status()
        payload = response.json()
        return DraftRequest(
            id=payload["id"],
            lease_token=payload["leaseToken"],
            source_commit_sha=payload.get("sourceCommitSha"),
        )

    def succeed(self, request: DraftRequest, promotion_post_id: str) -> None:
        response = self._publisher._http().post(
            f"/api/admin/promotion-posts/requests/{request.id}/succeed",
            json={"leaseToken": request.lease_token, "promotionPostId": promotion_post_id},
            headers=self._publisher._headers(),
        )
        response.raise_for_status()

    def fail(self, request: DraftRequest, code: str) -> None:
        response = self._publisher._http().post(
            f"/api/admin/promotion-posts/requests/{request.id}/fail",
            # 코드만 보낸다 — 예외 원문에는 diff·캡션·경로가 섞일 수 있다.
            json={"leaseToken": request.lease_token, "failureCode": code[:64]},
            headers=self._publisher._headers(),
        )
        response.raise_for_status()
