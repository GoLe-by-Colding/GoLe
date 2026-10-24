"""배포된 릴리스나 서비스 자체를 근거로 홍보 게시 초안을 만드는 일회성 실행.

GitHub 러너에서 돈다(D29). 데모 화면을 찍고, 운영 백엔드에서 작업 기억·이력을 읽어 주문서(기획 요청·제작 지시문 틀·
캡처 원본)를 만든 뒤 SSH 로 지인 서버에 넣고 "접수"만 받는다. codex 두 번(기획·제작, D25)과 검토 큐 납품은 지인 서버의
`gole_promotion_deliver` 가 한다. 문의(`gole_support_agent`)·사진(`gole_brick_filter`)과 같은 패키지에
있지만 저쪽은 상시 기동 gRPC 서비스다. 설계 근거는 `.kiro/specs/promotion-review/spec.md`를 본다.
"""
