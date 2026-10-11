# 구현과 검증

- [x] 1. 초기 지식의 출처/멱등 등록/API/관리자 UI를 한 단위로 연결하고 도메인·서비스·Mongo·웹 검증.
- [x] 2. 활성 지침 전체 조회·단계별 기억·선택 화면 경험·원장 추적을 연결하고 Python·API·웹 검증.
- [x] 3. 모바일 캡처/재사용 버전·문구 계약·파일 절차/imagegen·검토 자료를 연결하고 전체 관련 회귀 검증.
- [x] 4. 실제 API E2E와 품질 게이트를 실행하고 스킵/미검증/서버 상태를 기록. 볼트 개발 로그·관련 아키텍처/화면 문서 갱신.

로컬 E2E·실제 모델/gateway 실행 확인을 마친 뒤 사용자 요청에 따라 작업 단위별 커밋과 dev 대상 PR을 만든다. 사용자가 선택한 실행 범위는 로컬 검토 큐까지이며 운영 배포와 Threads 발행은 제외한다. 기존 HEIF 수정은 보존한다.

## 실행 근거 (2026-10-11)

- Python 홍보 135통과/0스킵. backend 단위 1,658건/0실패/2스킵, 통합 179건/0실패/1스킵. spotlessCheck 및 actionlint 통과.
- web lint/typecheck/FSD/build와 core typecheck/check:platform 통과. 전체 format:check는 기존 CRLF(web 397파일/core 86파일)로 실패했으며 변경 파일 검사와 전체 end-of-line auto 검사는 통과했다.
- 실제 API 홍보 메모리 E2E 1건 통과 후 전체 로컬 E2E 295통과/10스킵/0실패(총 305건, 재시도 0). 배포 대상 전용 live smoke는 로컬 범위에서 제외한다.
- 실제 Claude claude-opus-5-5 → Codex gpt-6.1-sol 내장 imagegen → 이미지 업로드 → PENDING_REVIEW 제출 → 실행 원장 저장과 관리자 큐 표시를 확인했다. runKey: local-live-8b6f1bb5b4eb47e8, 게시물: 8c736c9b-e042-4cc2-9d8a-4ae06c54cbb0, 원장: 8ade138f-07f7-4d76-abe4-98916215505a.
- /parts/new 화면 1장에 royal-blue-brick-mobile v1/두 줄 headline/polishFailed=false, 원본·생성 이미지 조회 및 PLAN/IMAGE_EDIT 기록을 확인했다. 생성 이미지의 글자·로고 보존은 최종 발행 전 사람이 대조한다.
- Orca API :8080(e2e)·WEB :3000 및 로컬 Docker를 유지했다. 운영 데이터·실결제·실제 Threads 발행은 검증하지 않았다.
