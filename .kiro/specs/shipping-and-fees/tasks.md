# 배송 추적 기반 거래 보호 · 수수료 — 구현 태스크

> 선행 조건: `admin-console` 리팩터링(병행 진행 중)이 끝나고 `./gradlew test`가
> 다시 컴파일되어야 한다. 현재는 `AccountRepositoryPort.countByRole` 등으로 테스트가 깨져 있어
> 새 작업의 검증이 불가능하다.

## A. 수수료 외부화 (완료 — 2026-08-04)
- [x] A1 `FeePolicy` 값 객체(요율 범위·하한>상한 금지 불변식, 반올림·클램프) + 테스트 9건
- [x] A2 `FeeProperties` `@ConfigurationProperties("gole.fee")` +
      application.yml `GOLE_FEE_RATE`/`GOLE_FEE_MIN`/`GOLE_FEE_MAX` 플레이스홀더
- [x] A3 `Settlement`에 `feeRate` 추가, `compute(..., FeePolicy, ...)`로 시그니처 변경
- [x] A4 `SettlementDocument.getFeeRate()` 레거시 보정(null → 0.05)
      ※ 참고: 이 임베디드 문서는 현재 **아무도 쓰지 않는다**(`StubSettlementAdapter`가
      로그만 남기고 저장하지 않음). 실 정산 도입 시 곧바로 쓸 수 있도록 필드만 준비해 둔 상태다.
- [x] A5 `PLATFORM_FEE_RATE` `@Deprecated` 처리(삭제 안 함 — 설정 기본값 출처)
- [x] A6 소액 거래 방어 테스트 포함(하한 > 거래액이어도 payout 음수 불가)
- [ ] A6b 환불 주문 무수수료 **회귀 테스트**(R5.5) — 현재 `REFUNDED`는 정산을 만들지 않아
      동작상 충족이나, 이를 고정하는 테스트가 아직 없다
- [ ] A7 관리자 수수료 집계 API(R5.6) — `admin` 컨텍스트, 병행 리팩터링 종료 후

## B. shipping 컨텍스트 — 도메인·포트
- [ ] B1 `WaybillNumber` 값 객체(정규화·검증 불변식) + 테스트
- [ ] B2 `Carrier` enum, `DeliveryStatus` enum(원문 `rawStatus` 보존)
- [ ] B3 `Shipment` 애그리거트(상태 역행 금지, 운송장 변경 이력, `deliveredAt` 1회)
- [ ] B4 in-port: `RegisterWaybillUseCase`, `TrackShipmentUseCase`, `GetShipmentUseCase`
- [ ] B5 out-port: `ShipmentRepositoryPort`, `DeliveryTrackerPort`, `TrackerCachePort`
- [ ] B6 `ShipmentService`(등록 시 판매자 검증 R1.2, 추적·전이, 알림 발행)
- [ ] B7 도메인·서비스 테스트(가짜 트래커로 전이 검증)

## C. shipping 컨텍스트 — 어댑터
- [ ] C1 Mongo 영속성 어댑터 + 도큐먼트
- [ ] C2 `StubDeliveryTrackerAdapter` — 경과시간 기반 상태 시뮬레이션(기본 빈)
- [ ] C3 `RedisTrackerCacheAdapter` — 상태별 TTL, 장애 흡수
- [ ] C4 `ShipmentController` — 운송장 등록/조회
- [ ] C5 알림 연동(운송장 등록 R1.5, 배송완료 R2.4)

## P. 파이프라인 엔진 (R7/R9 — 무개입 원칙의 핵심)
- [ ] P1 `@ConfigurationProperties("gole.pipeline")` — 상태별 타임아웃 전부 외부화(R9.1)
- [ ] P2 `orders`에 `(status, statusChangedAt)`, `shipments`에 `(status, deliveredAt)` 인덱스
      ※ 인덱스 **이름 확정 후** 배포 전 기존 인덱스 충돌 확인(`follows` 부팅실패 전례)
- [ ] P3 `PipelineRule` 추상 + 규칙 구현(결제 만료 / 미발송 독촉 / 미발송 자동환불 /
      배송정체 / 자동 구매확정 / 추적불가)
- [ ] P4 `OrderPipelineScheduler` — 규칙 순회, 건별 예외 격리(R7.4)
- [ ] P5 자동 전이는 기존 유스케이스 호출로만 구현(새 경로 금지) + 멱등성 테스트(R7.3)
- [ ] P6 자동 전이 시 알림 발행(R7.5)
- [ ] P7 고정 `Clock` 기반 타임아웃 시나리오 테스트(각 규칙별 경계값)

## Q. 예외 큐 (R7.6)
- [ ] Q1 예외 사유 모델(분쟁 / 배송정체 / 미접수 / 추적불가 / 판정지연)
- [ ] Q2 예외 큐 조회 API — 정상 진행 건은 제외
- [ ] Q3 `admin-console` 스펙과 **화면 통합**(별도 관리자 UI 신설 금지)

## S. CS 연락처 (R8)
- [ ] S1 `PhoneNumber` 값 객체(정규화·검증 불변식) + 테스트
- [ ] S2 주문 생성 시 구매자 연락처 수집(R8.1), 판매자 연락처(R8.2)
- [ ] S3 응답 DTO **기본 마스킹** + 전체 번호 전용 엔드포인트(당사자/ADMIN 게이트) (R8.4)
- [ ] S4 운영자 전체 번호 열람 감사 로그 — 기존 `admin_actions` 재사용 (R8.5)
- [ ] S5 목적 외 사용 금지 고지 문구(R8.6)

## D. order 연동
- [ ] D1 `OrderStatus`에 `DISPUTED` 추가 + 전이 규칙(FUNDS_HELD에서만 진입)
- [ ] D2 `OpenDisputeUseCase` / `ResolveDisputeUseCase`(환불 또는 완료)
- [ ] D3 `AutoCompleteOrdersScheduler` — DELIVERED + N일 + 무분쟁 → 기존 `CompleteOrderUseCase` 호출
- [ ] D4 미발송 장기화 시 구매자 일방 환불(R4.5)
- [ ] D5 자동 구매확정 멱등·타이머 정지 테스트(고정 `Clock`)

## E. 프론트
- [ ] E1 `entities/shipment` 타입 + API
- [ ] E2 `features/register-waybill` — 판매자 운송장 입력
- [ ] E3 `features/open-dispute` — 구매자 분쟁 제기
- [ ] E4 `widgets/shipment-tracker` — 배송 타임라인(표현 전용, props 주입)
- [ ] E5 `views/order-detail` 조립
- [ ] E6 관리자 분쟁 화면 — 배송 사실 근거 표시(R4.3)

## F. 실 트래커 연동 (사용자 입력 대기)
- [ ] F1 **사용자에게 요청** — 트래커 서비스 선정, API base URL, 키/시크릿, 지원 택배사 코드
- [ ] F2 `RestDeliveryTrackerAdapter` + `@ConditionalOnProperty` 게이트
- [ ] F3 택배사별 원문 상태 → `DeliveryStatus` 매핑 테이블 + 테스트
- [ ] F4 실 송장으로 엔드투엔드 스모크

## 검증/배포
- [ ] V1 `./gradlew test` + 통합 테스트 통과
- [ ] V2 프론트 build / typecheck / fsd:lint 통과
- [ ] V3 스텁 트래커로 전 구간 로컬 시나리오(등록 → 이동중 → 배송완료 → 자동확정 → 정산)
- [ ] V4 커밋·배포

## 후속
- [ ] 판매자 정산 실송금(현재 `StubSettlementAdapter`는 로그만 남긴다)
- [ ] 수수료 프로모션(신규 판매자 면제, 카테고리별 차등)
- [ ] 반품 배송(역방향 운송장)
