"""지인 서버의 게이트웨이 컨테이너에서 주문 하나를 처리해 백엔드 납품 창구로 보낸다(promotion-review D29).

기획 codex → 검증 → 제작 codex → 결과 한 묶음을 홍보 토큰으로 POST. 러너 패키지(gole_promotion_agent)는 쓰지 않고
공용 계약(gole_promotion_core)만 쓴다. HTTP 는 표준 라이브러리(urllib)로 한다 — 이미지에 pydantic 외 의존성을 두지 않는다.
"""
