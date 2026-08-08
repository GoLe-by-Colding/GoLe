# 운영자 콘솔 (Admin Console) — 구현 태스크

> 순서는 헥사고날 구현 순서(domain → port in → port out → service → adapter out → adapter in)와
> FSD 레이어 순서(entities → features → widgets → views → app)를 따른다.

## A. account — 계정 정지 / 권한 (R6)

- [x] A1. `AccountStatus.SUSPENDED` 추가 (R6.2)
- [x] A2. `Account`: `role` final 제거, `suspendedReason` 필드, `suspend/reinstate/changeRole/isSuspended/ensureNotSuspended` (R6.2, 6.6, 6.7)
- [x] A3. `AccountSuspendedException`(403 `ACCOUNT_SUSPENDED`) (R6.4)
- [x] A4. `SessionStorePort.revokeAllForAccount` + `RedisSessionStoreAdapter` 계정→토큰 인덱스 (R6.3)
- [x] A5. `AccountRepositoryPort.findRecent(query, limit)` / `countByRole(role)` + Mongo 어댑터 구현 (R6.1, 6.9)
- [x] A6. `AccountDocument.suspendedReason` 매핑 + 도메인 변환
- [x] A7. `AccountService.signIn`에 `ensureNotSuspended` (R6.4)
- [x] A8. `AccountService.resolve`에서 정지 계정 → `Optional.empty()` (R6.5)
- [x] A9. `ManageAccountsUseCase` 인바운드 포트 + `AccountAdminService` 구현 (자기대상/마지막 관리자 가드 포함) (R6.1, 6.8, 6.9)

## B. community / listing / catalog — 모더레이션 포트 (R4, R5, R7)

- [x] B1. `ModeratePostUseCase` + `CommunityService` 구현 (R5.2, 5.3)
- [x] B2. `Listing.takedown()`(상태 무관·멱등) 도메인 메서드 (R4.3, 4.4)
- [x] B3. `ModerateListingUseCase` + `ListingService` 구현 (R4.2)
- [x] B4. `UpdateLegoSetUseCase`(update / setFeatured) + `CatalogService` 구현 (R7.3, 7.4)

## C. admin — 감사 로그 + 읽기 모델 (R8, R9)

- [x] C1. 도메인 `AdminAction` / `AdminActionType` / `AdminTargetType` (R8.1)
- [x] C2. 인바운드 포트 `RecordAdminActionUseCase` / `ListAdminActionsUseCase` (R8.1, 8.3)
- [x] C3. 아웃바운드 포트 `AdminAuditPort` (append/findRecent) (R8.4 — 수정·삭제 메서드 없음)
- [x] C4. 아웃바운드 포트 `AdminReadModelPort` + 읽기 모델 record (R9.2)
- [x] C5. `AdminAuditService` — 기록 실패 삼킴(R8.5)
- [x] C6. `AdminActionDocument` + `AdminActionMongoRepository` + `AdminAuditPersistenceAdapter`
- [x] C7. `MongoAdminReadModelAdapter` — 컨트롤러의 `MongoTemplate` 의존 제거 (R9.2)

## D. admin — 웹 어댑터 재구성 (R1~R7)

- [x] D1. `AdminAuthInterceptor`에 actor 이메일 속성 추가 + `AdminActor` 헬퍼
- [x] D2. `AdminDashboardController` — `/overview`, `/audit` (R2.2, 8.3)
- [x] D3. `AdminModerationController` — listings/posts/reports 조회 + takedown/remove/resolve/dismiss (R3, R4, R5)
- [x] D4. `AdminAccountController` — 목록 + suspend/reinstate/role (R6)
- [x] D5. `AdminCatalogController` — 목록/등록/수정/추천 토글 (R7)
- [x] D6. 기존 단일 `AdminController` 제거, `AdminDtos` 정리(사유 요청 DTO 추가)
- [x] D7. 모든 조치 경로에 `AdminAuditService.record(...)` 연결 (R8.1, 8.2)

## E. 프론트엔드 (R1~R8)

- [x] E1. `entities/admin` API 클라이언트 갱신 — suspend/reinstate/role, 사유 파라미터, audit, catalog update/featured, orders status 필터
- [x] E2. `features/admin-moderation` — `ReasonPrompt`(사유 필수 입력 모달) + 조치 훅
- [x] E3. `widgets/admin-shell` — 권한 게이트 + 좌측 내비 + 미처리 신고 뱃지 (R1.3, 1.4, 2.1, 2.3)
- [x] E4. `widgets/admin-bar` — 온사이트 어드민 모드(경로 컨텍스트 조치, 접기, ADMIN 아닐 때 미렌더) (R1.6, 1.7)
- [x] E5. `views/admin` 섹션 분해 — dashboard / reports / listings / orders / community / accounts / catalog / audit
- [x] E6. `app/(main)/admin/**` 라우트 + `layout.tsx`(noindex) (R1.5, 2.1)
- [x] E7. `(main)/layout.tsx`에 `AdminBar` 배치
- [x] E8. 헤더 관리자 진입점 유지·정리 (R1.1)

## F. 검증

- [x] F1. 백엔드 단위 테스트 — Account 전이, AccountAdminService 가드, 모더레이션 포트, AdminAuditService
- [x] F2. `./gradlew test` + spotless 통과
- [x] F3. 프론트 `pnpm --filter web lint` + `build` + steiger(FSD) 통과
- [x] F4. E2E `admin-console.spec.ts` — 화면 게이트(비로그인/USER/noindex/어드민바 미노출) 5건 통과.
      API 가드(401/400/`ADMIN_SELF_TARGET`)는 `E2E_WITH_BACKEND=1` + `GOLE_ADMIN_*` 설정 시 실행
- [x] F5. 레거시 잠금(`lockedUntil=9999`) → `SUSPENDED` 전환 스크립트 (`scripts/migrate-legacy-locks.js`, 설계 §5)

## H. 로컬 구동 (실제 동작 검증)

- [x] H1. `dev:api`에 로컬 관리자 자격증명 기본값 주입 — 없으면 시더 no-op이라 admin 로그인 불가였음
- [x] H2. `ReportSeeder` — 시드 매물·게시글 대상 PENDING 신고 4건(멱등)
- [x] H3. CORS 허용 오리진 기본값에 `http://localhost:3010` 추가
- [x] H4. `AdminAuthInterceptor` 프리플라이트(OPTIONS) 예외 — 없으면 브라우저에서만 조용히 실패
- [x] H5. 실동작 검증 — 정지→기존토큰 401→재로그인 403→해제→재로그인 성공, 사유없는 내림 400, 감사 로그 적재, 콘솔 4개 화면 + 온사이트 어드민 바 스크린샷 확인

## G. 문서

- [x] G1. `lego-marketplace/requirements.md` 요구사항 10(admin) 수용 기준 확장
- [x] G2. `admin-and-payments/tasks.md` 후속 TODO 정리(본 스펙으로 이관)
- [x] G3. README `admin` 행 갱신
