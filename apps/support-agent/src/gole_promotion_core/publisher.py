"""백엔드 HTTP 클라이언트. 러너와 서버의 deliver 가 같은 경로를 부른다(D29). 지금 인증은 봇 ADMIN 계정 로그인이다.

httpx import 는 지연시킨다 — promotion extra 없이도 이 모듈을 import 해 테스트할 수 있어야 한다.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any, Mapping, Sequence

from gole_promotion_core import limits


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
        return len(self._list("PENDING_REVIEW", limits.MAX_PENDING_REVIEW + 1))

    def history(self, limit: int) -> tuple[Mapping[str, Any], ...]:
        # 발행이 스텁인 동안 PUBLISHED 는 계속 0건이라 전체 상태를 본다(스펙 D18).
        return tuple(self._list(None, limit))

    def memory_context(self, category: str, routes: Sequence[str]) -> dict[str, Any]:
        response = self._http().get(
            "/api/admin/promotion-memory/context",
            params=[("category", category), *(("routes", route) for route in routes)],
            headers=self._headers(),
        )
        response.raise_for_status()
        return response.json()

    def reflect(self, payload: Mapping[str, Any]) -> list[dict[str, Any]]:
        response = self._http().post(
            "/api/admin/promotion-memory/reflect", json=dict(payload), headers=self._headers()
        )
        response.raise_for_status()
        return response.json()

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
                        "capturedAt": item["capturedAt"],
                        # AI 로 다듬은 사진이면 원본 키와 지시문. 검토 화면이 나란히 대조한다.
                        "originalMediaKey": item.get("originalMediaKey"),
                        "edit": item.get("edit"),
                        "styleId": item.get("styleId"),
                        "styleVersion": item.get("styleVersion"),
                        "headline": item.get("headline"),
                        "polishFailed": item.get("polishFailed", False),
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

    def record_run(self, payload: Mapping[str, Any]) -> int:
        """실행 원장 한 줄(D23). HTTP 상태만 돌려준다 — 응답 본문은 공개 로그로 보내지 않는다."""
        response = self._http().post("/api/admin/promotion-runs", json=dict(payload), headers=self._headers())
        return response.status_code
