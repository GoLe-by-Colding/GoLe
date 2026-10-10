# 홍보 메모리 구현 단계

- [x] STEP 1: 요구사항/설계/API 계약 확정.
- [x] STEP 2: 도메인 → 인바운드/아웃바운드 포트 → 서비스 → Mongo 어댑터 → 관리자 API 순서로 경험/지침 구현. 반려 원자성, maker-checker, 조회 예산, 실행 원장 호환 검증. 전체 단위 1,279통과/2스킵, 전체 통합 131통과/1스킵, 0실패.
- [x] STEP 3: Python에서 사전 게이트 전 성찰 제안, 제한된 작업 기억 조립, Claude/Codex 단계별 전달, 원장 기록과 실패 격리 구현 및 테스트. 2026-10-09 Python 전체 218건 통과, 0실패, 0스킵.
- [x] STEP 4: 관리자 화면에서 근거/제안 수정/확정/기각/활성 해제 구현. 기존 컴포넌트/스타일 재사용. format/lint/typecheck/fsd/build 통과, 관련 E2E 83건 통과.
- [x] STEP 5: 홍보 피드백→성찰→사람 확정→다음 생성 반영→해제 시 제외 E2E 작성 및 실행. 메모리 통합 10건 및 관련 E2E 83건 통과. 전체 E2E 220통과/10스킵/0실패/0flaky(retries=0, 172.4초), 스킵은 E2E_BASE_URL 배포전용 live-smoke 10건.
- [x] STEP 6: 전체 관련 게이트와 E2E 실제 실행 결과(통과/실패/스킵) 확인, 볼트 개발 로그·일지·아키텍처/도메인/화면 문서 기록. Python 218통과, actionlint/spotlessCheck 통과, 웹/코어 게이트 통과, 전체 E2E 220통과/10스킵. 루트가 Orca API :8080/WEB :3000 및 전용 Docker project gole-promotion-memory의 Mongo :27018/Redis :16379/MinIO :19000 LISTEN을 확인했으며 이어 사용하도록 유지한다. 실제 모델·운영 발행·실 HEIC·CoolSMS 발송·배포 스모크는 미검증.
- [x] PR #231 충돌 해결: dev e55fe539의 PromotionMediaPort 구조와 반려 저장 의존성을 함께 유지하고 새 메모리 컨트롤러/테스트를 promotion 컨텍스트 및 common AdminActor로 정렬함. 홍보 단위 93건·아키텍처 9건·홍보 통합 16건 모두 0실패/0스킵, Python 홍보 112건·spotlessCheck·웹 typecheck 통과. 전체 E2E·운영 모델·발행은 재검증하지 않음. 관리자 승인 버전 경합과 6,000자 프롬프트 제한은 이번 충돌 해결 범위에서 변경하지 않음.

- [x] PR #231 승인 동시성 보완: 버전 기반 수정/확정과 Mongo 원자적 조건부 저장, 409 최신 화면 갱신을 한 단위로 연결한다. 검증: 홍보 단위 95건·아키텍처 9건·실제 Mongo 통합 19건·관리자/메모리 E2E 46건 모두 0실패/0스킵, HTTP 입력 경계 3건 통과. spotlessCheck·웹 lint/typecheck/FSD/build·코어 typecheck/check:platform·변경 파일 Prettier 통과. 전체 웹 format:check는 기존 Windows CRLF 파일 380개에서 실패했으며 최신 전체 결과는 PR CI로 확인한다.

- [x] PR #232 마스코트 dev 병합 후 재충돌 해결: dev 623ddf17을 통합하고 홍보 지침/마스코트 감사 유형·대상·라벨·색상을 모두 유지함. 마스코트 E2E의 동일 문구 두 요소 선택 오류는 exact 선택으로 수정함(테스트 오류). 단위/아키텍처 128건·Mongo 통합 22건·관리자/메모리 E2E 48건 모두 0실패/0스킵(retries=0), spotlessCheck·웹 타입/린트/FSD/빌드·코어 타입/플랫폼·충돌 및 수정 파일 Prettier 통과. 전체 E2E와 운영 모델/배포는 로컬에서 재실행하지 않음.
