"""러너와 서버가 같이 지키는 상한. 백엔드도 같은 상한으로 다시 자르고 검증한다 — 신뢰 경계 양쪽에서 막는다."""

from __future__ import annotations

import re

# 캡션 (스펙 D13)
MAX_CAPTION = 450
MAX_RATIONALE = 300

# 게이트 (스펙 D18)
MAX_PENDING_REVIEW = 5
# 실행 원장(D23).
MAX_RUN_DETAIL = 300
MAX_RUN_CALLS = 20
AGENT_SHA_PATTERN = re.compile(r"[0-9a-f]{7,40}")
_CONTROL = re.compile(r"[\x00-\x1f\x7f-\x9f]")


def clean_detail(text: str | None) -> str | None:
    """모델이 쓴 사유를 원장에 담을 모양으로: 제어문자 제거, 공백 정리, 글자 단위 300자. 비면 None."""
    if text is None:
        return None
    cleaned = " ".join(_CONTROL.sub(" ", str(text)).split())
    return cleaned[:MAX_RUN_DETAIL] or None
