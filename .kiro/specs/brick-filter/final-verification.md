# 2026-09-13 통합 검증

## 반영 단위

- `4e23662`: HEIF native 정규화 및 설정 생성자 바인딩
- `7e9671e`: 공개 NormalizeImageUseCase
- `7cb3795`: Java quota/복구/헥사고날 및 사진 gRPC
- `fc4d5cd`: Python Brain/Hands/Session, 영속 기한, 관측 격리
- `1045860`: 직접 의존성 고정과 컨테이너 smoke
- `2845e32`: 상품·게시글 업로드/등록 중복 방지
- `51ff08b`: 회원 브릭 화면과 상태 복구 및 PNG 결과 제한

## 실행 증거

| 범위 | 결과 |
|---|---|
| Python 전체 locked pytest | 97 통과, 실패/스킵 0 |
| Java isolated clean 단위 | 1,190 통과, 실패/스킵 0 |
| Java 전체 integrationTest 재실행 | 115 통과, 실패 0, 알림톡 실발송 1 스킵 |
| 브릭 실제 Mongo 통합 | 11 통과, 위 통합에 포함 |
| native HEIC media 회귀 | 58 통과, 별도 Normalize 2건은 전체 단위에 포함 |
| Java→Python→LangGraph→fake editor | 두 모드 각 1회 통과 |
| Linux arm64 worker Docker build | 통과 |
| 무네트워크·읽기 전용 컨테이너 smoke | proto/support/durable/image/privacy 통과 |
| web format/lint/typecheck/FSD | 통과 |
| core typecheck/format 및 계약 | 통과, 계약 8건 |
| 실제 TSX 폼 핸들러 fake | 4건 통과, DOM/저장소 E2E 아님 |
| 격리 web production build | 통과, 기존 dev 캐시 변경 없음 |
| Orca worker fake API UI | 12개 통과, 최종 PNG 제한 추가 전 기준 |

실제 운영 키나 유료 모델 요청은 사용하지 않았다. 테스트는 공유 작업 트리에서 실행했으며 미커밋 Sentry/RN 변경을 포함한 전체 CI의 원격 성공을 주장하지 않는다. 이번 기능의 커밋에는 해당 미커밋 설정·의존성을 넣지 않았다.

## 남은 검증과 보존한 상태

최종 PNG 제한 후 main 브라우저 재검증은 `runtime_unavailable`로 두 번 실패했다. viewport 명령은 375 성공을 반환했지만 실제 innerWidth는 648로 관측되어 375px 완료로 표시하지 않는다. main이 만든 테스트 탭만 닫아 fake 상태를 제거했고 사용자 탭은 유지했다. 실제 생성 품질·계정 접근 권한·전체 로그인부터 생성까지·실기기·운영 배포는 미검증이다.

API :8090 health UP, web :3000/brick-filter 200, Metro :8081 running을 확인했다. 기존 사용자 서버는 재시작하거나 종료하지 않았다. 단발 테스트 컨테이너는 --rm으로 종료했고 로컬 빌드 이미지만 남겼다.

Orca 웹 재시도 worker는 완료 보고 뒤 기존 외부 터미널을 보존했다. 초기 실행 실패 두 dispatch는 identity_unproven 상태로 보존했으며 프로세스 종료를 검증했다고 주장하지 않는다. 미디어는 main이 직접 독립 검증했다.

브라우저 상세: [web-verification.md](web-verification.md). Python 실행 구조: [../agent-worker/design.md](../agent-worker/design.md). 팀 기록은 GoLe-obsidian의 2026-09-13 실행 기한·관측 경계 개발로그와 승찬 일지에 남겼다.
