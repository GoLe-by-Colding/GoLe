# 구조 개편 검증 — 2026-09-14

- 초기 기준선: proto 생성 전 수집 오류. 생성 후 49 passed와 이미지 oversized 테스트 setup/teardown 오류 2건.
- 원인: pytest가 바이트 입력을 테스트 ID로 써 Windows 환경변수 32767자 한도를 초과함. 짧은 ID 지정 후 50 passed.
- 구조 개편 후: 전체 61 passed. 기존 작업자 테스트는 이동 단계마다 36 passed.
- 최신 dev(51ff08b2)의 작업 기한·privacy·사진 gRPC 변경 통합 후: **108 passed, 0 failed, 0 skipped**, 21.06초.
- 명령: `uv run --project apps/support-agent pytest apps/support-agent/tests`.
- Python `compileall` 및 `git diff --check` 통과.
- 기존 v1 그래프의 prepare/execute/완료 지점 × 문의/데모 6가지 체크포인트 재개를 검증함.
- DB 없는 그래프 실행, 취소 시 provider 미호출, 미등록 작업 거절, 기존 import·DB 경로, 에이전트 의존 경계를 검증함.
- 실제 loopback gRPC·프로세스 재시작·기한·privacy 회귀는 기존 테스트를 함께 실행함.
- 검증하지 않음: 실제 유료 모델·Threads 게시·운영 DB·컨테이너 재빌드·Java 통합·웹 E2E. Python 구조 개편만 대상으로 함.
- dev 대비 gRPC·DB 스키마·의존성·기동 명령 변경 없음. 최신 dev의 deadline 마이그레이션은 그대로 보존함.
- 구조 평가(주관): 개편 전 6/10 → 개편 후 8/10. 작업 분기와 저장소 직접 접근은 분리했으며,
  향후 작업별 다단계 그래프와 버전 선택은 별도 기능 범위라 이번에 구현하지 않음.
