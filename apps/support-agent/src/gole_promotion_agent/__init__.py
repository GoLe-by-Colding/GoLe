"""배포된 릴리스나 서비스 자체를 근거로 홍보 게시 초안을 만드는 일회성 실행.

초안을 쓰는 두뇌는 self-hosted 서버의 Claude Code(`claude -p`)이고, 이 패키지는 그 손(캡처·합성
명령)과 래퍼(검증·제출)다. 문의(`gole_support_agent`)·사진(`gole_brick_filter`)과 같은 패키지에
있지만 저쪽은 상시 기동 gRPC 서비스다. 설계 근거는 `.kiro/specs/promotion-review/spec.md`를 본다.
"""
