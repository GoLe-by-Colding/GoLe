"""배포된 릴리스를 근거로 홍보 게시 초안을 만드는 일회성 에이전트.

문의(`gole_support_agent`)·사진(`gole_brick_filter`)과 같은 패키지에 있지만 실행 형태가 다르다.
저쪽은 상시 기동 gRPC 서비스이고 이쪽은 하루 한 번 도는 배치다. 공유하는 것은 구조 관용구와
관측 격리(`gole_agent_runtime`)이지 프로세스·이미지·자원 한도가 아니다. 설계 근거는
`.kiro/specs/promotion-review/spec.md` D9~D19를 본다.
"""
