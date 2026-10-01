"""외부 세계를 만지는 구현. git·브라우저·백엔드 HTTP가 전부 여기 있다.

SDK import는 전부 지연시킨다 — promotion extra 없이도 이 모듈을 import 해 테스트할 수 있어야 한다.
"""

from __future__ import annotations

from datetime import datetime, timezone
import subprocess
from pathlib import Path
from typing import Any, Callable, Iterable, Mapping, Sequence
from urllib.parse import urlsplit

from gole_promotion_agent import policy
from gole_promotion_agent.ports import Candidate


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


class ReleaseScanner:
    """트리거가 정해 준 릴리스 하나를 본다. 화면(apps/web/src) 변경이 없으면 후보가 없다(건너뜀)."""

    def __init__(self, repo: Path, sha: str):
        if not policy.SHA_PATTERN.match(sha):
            raise ValueError("INVALID_SHA")
        self._repo = Path(repo)
        self._sha = sha
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
        if self._cache is None:
            subject = _git(self._repo, "log", "-1", "--format=%s", self._sha).strip()
            self._cache = (
                (Candidate(self._sha, subject),) if self._touches_web(self._sha) else ()
            )
        return self._cache

    def diff(self) -> str:
        patch = _git(
            self._repo,
            "diff-tree",
            "--no-commit-id",
            "--patch",
            "--stat",
            "-r",
            "--root",
            self._sha,
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

    def __init__(self, repo: Path, demo: bool = False):
        self._app_dir = Path(repo) / "apps" / "web" / "src" / "app"
        self._demo = demo

    def routes(self) -> tuple[str, ...]:
        if not self._app_dir.is_dir():
            return ()
        found: set[str] = set()
        for page in self._app_dir.rglob("page.tsx"):
            route = self._route_of(page)
            if route is not None and policy.is_public_capture_route(route, self._demo):
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


# ----------------------------------------------------------------------- backend


class BackendPublisher:
    """봇 전용 ADMIN 계정으로 로그인해 초안을 만든다. 토큰은 여기 밖으로 나가지 않는다."""

    def __init__(
        self,
        base_url: str,
        email: str,
        password: str,
        client: Any | None = None,
        run: Mapping[str, Any] | None = None,
    ):
        """run: 실행 단위로 같은 값 — category(FEATURE/SERVICE)·dataSource(DEMO/PRODUCTION)·runUrl."""
        self._run = dict(run or {})
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

    def create(
        self, sha: str, caption: str, media_keys: Sequence[str], details: Mapping[str, Any]
    ) -> str:
        data_source = self._run.get("dataSource", "PRODUCTION")
        captured_fallback = datetime.now(timezone.utc).isoformat()
        response = self._http().post(
            "/api/admin/promotion-posts",
            json={
                "channel": "THREADS",
                "caption": caption,
                "mediaKeys": list(media_keys),
                "sourceCommitSha": sha,
                "category": self._run.get("category", "FEATURE"),
                "captures": [
                    {
                        "label": item["label"],
                        "route": item["route"],
                        "actions": item.get("actions", ""),
                        "dataSource": data_source,
                        "capturedAt": item.get("capturedAt") or captured_fallback,
                        # AI 로 다듬은 사진이면 원본 키와 지시문. 검토 화면이 나란히 대조한다.
                        "originalMediaKey": item.get("originalMediaKey"),
                        "edit": item.get("edit"),
                    }
                    for item in details.get("captures", [])
                ],
                "provenance": {
                    "releaseTitle": details.get("releaseTitle"),
                    "rationale": details.get("rationale"),
                    "runUrl": self._run.get("runUrl"),
                },
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
