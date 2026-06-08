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

## 프론트
- [x] 12. `Session.role` + 헤더 관리자 진입점 (R4.1)
- [x] 13. `entities/admin` + `views/admin` + `/admin` 라우트(ADMIN 게이트) (R4.2)
- [x] 14. 포트원 브라우저 결제 연동(`shared/lib/portone`, 주문 상세) (R5.1, R5.2)

## 검증 / 배포
- [x] 15. 백엔드 test + 프론트 build/lint
- [x] 16. E2E: admin 200 / no-token 401 / USER 403 / 세트 등록 201
- [x] 17. 표준 deploy.sh 배포 + 관리자 부트스트랩

## 후속 (TODO)
- [ ] 매물/주문/회원/게시글 모더레이션 API + UI
- [ ] 비밀번호 변경/재설정
- [ ] 포트원 라이브 자격증명 주입 및 실결제 검증
