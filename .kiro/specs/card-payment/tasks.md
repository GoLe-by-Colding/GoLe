# 카드결제 추가 (Card Payment) — 작업

백엔드 → 프론트 순서. 커밋은 레이어별로 나눈다.

## 1. 백엔드 — 설정·검증

- [x] 1.1 `application.yml`에 `portone.card-channel-key` 추가 (R4.1)
- [x] 1.2 `PortOnePaymentGatewayAdapter`에 `AllowedChannel` 목록 도입, 생성자에 카드 채널 키 파라미터
      추가 (R3.1, R3.2)
- [x] 1.3 `findPaymentValidationFailure`를 허용 채널 기반으로 교체 (R3.1~R3.3)
- [x] 1.4 `expectedLedger` 로그에 허용 채널 전부 표기 (R3.5)
- [x] 1.5 `PaymentConfigurationGuard`에 "카드 채널 키 ≠ 카카오페이 채널 키" 검사 추가 (R4.2)

## 2. 백엔드 — 운영 가시성

- [x] 2.1 `GetPaymentReadinessUseCase.Snapshot`의 `provider` → `methods` (R5.1)
- [x] 2.2 `PortOneReadinessIndicator`가 열린 결제수단 목록을 계산
- [x] 2.3 `AdminDtos.PaymentReadinessResponse` 반영

## 3. 백엔드 — 테스트

- [x] 3.1 `PortOnePaymentGatewayAdapterTest` — 카드 승인, 교차 오염 2종, 카드 미설정, 카드 환불
- [x] 3.2 `PaymentConfigurationGuardTest` — 같은 채널 키면 기동 거부
- [x] 3.3 `PortOneReadinessIndicatorTest` — `methods` 목록
- [x] 3.4 `AdminDashboardControllerTest` — `Snapshot` 생성자 변경 반영
- [x] 3.5 `./gradlew spotlessApply && ./gradlew cleanTest test`

## 4. 프론트 — shared

- [x] 4.1 `shared/config/env.ts`에 `portOneCardChannelKey`
- [x] 4.2 `shared/lib/portone.ts` — `PortOneMethod`/`PortOneCustomer`, 수단별 채널·payMethod,
      `isCardPaymentAvailable()`, 구매자 정보 누락 시 throw (R1.3, R2.4)

## 5. 프론트 — views

- [x] 5.1 `views/order-detail` — 결제수단 선택 UI (R1.1, R1.2)
- [x] 5.2 카드 선택 시 구매자 정보 폼 + 이메일/연락처 프리필 + 이름 기억 (R2.1~R2.3, R2.5)
- [x] 5.3 "카카오페이" 고정 문구 정리 (order-detail, payment-return)
- [x] 5.4 관리자 대시보드 결제 연동 패널이 `methods`를 표시 (R5.1)
- [x] 5.5 약관·개인정보 문서의 결제 대행사 고지 수정

## 6. 프론트 — 테스트·품질 게이트

- [x] 6.1 `portone-request.spec.ts`에 카드 요청 계약 추가
- [x] 6.2 CI 워크플로에 `NEXT_PUBLIC_PORTONE_CARD_CHANNEL_KEY` 추가
- [x] 6.3 `format:check` → `lint` → `typecheck` → `fsd:lint` → `build`

## 7. 환경 설정

- [x] 7.1 `.env.example` / `apps/web/.env.example`에 카드 채널 키 문서화
- [x] 7.2 로컬 `.env` / `apps/web/.env.development.local`에 실제 채널 키 적용
- [ ] 7.3 수동 확인 — 카드 결제 1건(TEST, 최소 금액)이 `funds_held`까지 가고 환불되는지
      (이니시스 결제창은 자동화할 수 없다 — 사람이 직접 한 번 통과시켜야 한다)
