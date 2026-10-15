"""러너가 외부 세계를 만지는 구현 — git·브라우저. 백엔드 HTTP 는 gole_promotion_core.publisher 에 있다.

SDK import는 전부 지연시킨다 — promotion extra 없이도 이 모듈을 import 해 테스트할 수 있어야 한다.
"""

from __future__ import annotations

import subprocess
from pathlib import Path
from typing import Any, Callable, Iterable, Mapping
from urllib.parse import urlsplit

from gole_promotion_agent import policy


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
        self._repo = Path(repo)
        self._sha = sha

    def touches_web(self) -> bool:
        changed = _git(
            self._repo,
            "diff-tree",
            "--no-commit-id",
            "--name-only",
            "-r",
            # --root 가 없으면 부모 없는 최초 커밋이 조용히 빈 diff 를 낸다.
            "--root",
            # 릴리스는 dev → main 머지 커밋이다. 부모가 둘이면 diff-tree 가 아무것도 내지 않으므로
            # 직전 main(첫 부모)과 비교해 이번 릴리스 전체 변경을 본다. 일반 커밋에는 영향이 없다.
            "-m",
            "--first-parent",
            self._sha,
            "--",
            "apps/web/src",
        )
        return bool(changed.strip())

    def subject(self) -> str:
        return _git(self._repo, "log", "-1", "--format=%s", self._sha).strip()

    def diff(self) -> str:
        patch = _git(
            self._repo,
            "diff-tree",
            "--no-commit-id",
            "--patch",
            "--stat",
            "-r",
            "--root",
            "-m",
            "--first-parent",
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
        # None 이면 익명으로 찍는다 — 단위 테스트가 백엔드 없이 살아야 한다.
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

    def capture(self, route: str, destination: Path) -> None:
        if route not in self._allowed:
            raise ValueError("ROUTE_NOT_ALLOWED")
        destination = Path(destination)
        destination.parent.mkdir(parents=True, exist_ok=True)
        context = self._ensure_browser().new_context(viewport=dict(policy.CAPTURE_VIEWPORT), is_mobile=True, has_touch=True)
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
            # 라우트 허용 검사는 **이동 시작점에만** 걸린다. 리다이렉트가 다른 화면에 데려갈 수 있는데,
            # 로그인 상태라 그 끝이 /profile·/notifications 같은 사설 화면일 수 있다.
            # 찍기 직전에 지금 서 있는 곳을 다시 확인한다(D12·D19).
            self._assert_still_allowed(page.url)
            page.screenshot(path=str(destination), animations="disabled")
        finally:
            context.close()

    def _assert_still_allowed(self, current: str) -> None:
        """이동이 끝난 뒤에도 허용된 공개 화면에 서 있는지 확인한다."""
        if not current.startswith(f"{self._base_url}/") and current != self._base_url:
            raise ValueError("NAVIGATED_OFF_SITE")
        path = urlsplit(current).path or "/"
        if path != "/" and path.endswith("/"):
            path = path.rstrip("/")
        if path not in self._allowed or not policy.is_public_capture_route(path):
            raise ValueError("NAVIGATED_TO_FORBIDDEN_ROUTE")

    def close(self) -> None:
        if self._browser is not None:
            self._browser.close()
            self._browser = None
        if self._playwright is not None:
            self._playwright.stop()
            self._playwright = None
