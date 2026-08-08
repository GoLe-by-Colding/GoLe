# 관리자 · 결제 — 구현 태스크

> 상태: 전부 구현·배포 완료. (소급 문서화)

## 인증 세션 / RBAC
- [x] 1. `Role`(USER/ADMIN) + `Account.role`/`provisioned()` (R2.1)
- [x] 2. `AccountDocument.role` + 영속성 매핑(null→USER)
- [x] 3. `SessionStorePort` + `RedisSessionStoreAdapter` (R1.1)
- [x] 4. `signIn` 세션 저장 + role 반환, `GetCurrentSessionUseCase` (R1)
- [x] 5. `GET /api/v1/accounts/me` (R1.2)
- [x] 6. `AdminAccountSeeder`(env, 멱등) (R2.2)

## 관리자 API
- [x] 7. `AdminAuthInterceptor` + `AdminWebConfig`(/api/admin/** ADMIN 가드) (R3.1)
- [x] 8. `AdminController` overview (R3.2)
- [x] 9. catalog `CreateLegoSetUseCase`/`ListLegoSetsUseCase` + `CatalogAdminPort` + 세트 GET·POST (R3.3)

## 포트원 결제
- [x] 10. `PortOnePaymentGatewayAdapter`(검증 REST, 설정 게이트) (R5)
- [x] 11. `StubPaymentGatewayAdapter` 기본화(matchIfMissing)
- [x] 11a. `PaymentWebhookController` `POST /api/v1/payments/portone/webhook` — 서버-투-서버 결제 확정(브라우저 미호출 시 누락 방지). `pay()`가 PortOne에 재검증하므로 서명 시크릿 없이도 위조 webhook으로 확정 불가(안전). 이미 처리/대기아님은 ack.

## 프론트
- [x] 12. `Session.role` + 헤더 관리자 진입점 (R4.1)
- [x] 13. `entities/admin` + `views/admin` + `/admin` 라우트(ADMIN 게이트) (R4.2)
- [x] 14. 포트원 브라우저 결제 연동(`shared/lib/portone`, 주문 상세) (R5.1, R5.2)

## 검증 / 배포
- [x] 15. 백엔드 test + 프론트 build/lint
- [x] 16. E2E: admin 200 / no-token 401 / USER 403 / 세트 등록 201
- [x] 17. 표준 deploy.sh 배포 + 관리자 부트스트랩

## 후속 (TODO) — 2026-08-03 실측 감사 반영
- [x] 매물/주문/회원/게시글 모더레이션 API + UI — **1차 완료 후 `admin-console` 스펙으로 재설계·이관.**
- [ ] 비밀번호 변경/재설정 — 미구현 확인(`changePassword`/`resetPassword` 심볼 0건).
      `Account.java`의 `upgradePasswordHash`는 해시 표현 교체용 마이그레이션이지 사용자 비밀번호 변경이 아니다.
- [ ] 포트원 라이브 자격증명 주입 및 실결제 검증

## admin-console 스펙에서 대체된 항목 (2026-08-04)

본 문서는 최초 도입 이력으로만 남긴다. 현재 기준은 `.kiro/specs/admin-console/` 이다.

| 본 스펙에서 만든 것 | 대체된 이유 | 대체물 |
|---|---|---|
| 단일 `AdminController` (`MongoTemplate` 직접 사용) | 웹 계층이 저장소를 직접 알아 NFR-3 위반 | 도메인별 4개 컨트롤러 + `AdminReadModelPort` |
| 게시글 삭제·회원 잠금의 컬렉션 직접 수정 | 도메인 불변식 우회 | `ModeratePostUseCase` / `ManageAccountsUseCase` |
| `/accounts/{id}/lock`·`/unlock` (`lockedUntil`=9999 편법) | 기존 세션(TTL 7일)이 살아 있어 정지가 실효되지 않음 | `/suspend`·`/reinstate` + `AccountStatus.SUSPENDED` + 세션 일괄 폐기 |
| 단일 `/admin` 한 화면 | 운영 동선 없음 | `/admin/**` 8개 섹션 콘솔 + 온사이트 어드민 바 |
| 감사 추적 없음 | 분쟁 시 근거 부재 | `admin_actions` append-only 감사 로그 |
