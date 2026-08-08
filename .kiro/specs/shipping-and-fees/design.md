# 배송 추적 기반 거래 보호 · 수수료 — 설계

## 전체 흐름

```
구매자 결제        판매자 발송         택배사              무분쟁 N일
   │                  │                 │                    │
PAYMENT_PENDING → FUNDS_HELD ──운송장등록──> IN_TRANSIT ──> DELIVERED ──> COMPLETED
                      │                                         │           └─ 수수료 확정 + 정산
                      └─ 분쟁 제기 ──> DISPUTED ──운영자 판정──> REFUNDED (수수료 없음)
```

핵심 설계 판단: **배송 추적은 `order`의 하위 개념이 아니라 별도 애그리거트다.**
운송장은 주문과 1:1이지만 생명주기(폴링·상태 전이·재조회)가 완전히 다르고,
외부 API 실패가 주문 정합성을 오염시키면 안 된다.

## 파이프라인 설계 (R7 — 무개입 원칙)

정상 거래에 운영자 개입 지점은 **0개**다. 사람이 필요한 곳은 `DISPUTED` 판정 하나뿐이고,
그것도 "기계가 금전 귀속을 단정하면 안 된다"는 이유로 **의도적으로 남긴** 예외다.

```
                     ┌─ 30분 미결제 ──────────────> PAYMENT_FAILED  [자동]
PAYMENT_PENDING ─────┤
                     └─ 결제승인(webhook) ────────> FUNDS_HELD      [자동]
                                                        │
                     ┌─ 3일 송장없음 ─> 판매자 독촉    │  [자동]
FUNDS_HELD ──────────┤─ 7일 송장없음 ─> REFUNDED      │  [자동]
                     └─ 송장등록 ─────> Shipment(PENDING)
                                             │
                            트래커 폴링 [자동] ▼
                          PENDING → IN_TRANSIT → DELIVERED
                                                     │
                                        7일 무분쟁 [자동]
                                                     ▼
                                                 COMPLETED
                                                     │
                                       수수료 확정 + 정산 [자동]

분쟁 경로:  FUNDS_HELD ──구매자 제기──> DISPUTED ──운영자 판정──> COMPLETED | REFUNDED
                                          (사람이 필요한 유일한 지점)
```

### 파이프라인을 굴리는 엔진

상태별 타임아웃(R9)을 개별 스케줄러로 흩뿌리면 규칙이 코드 곳곳에 숨는다.
대신 **하나의 스케줄러가 정책 테이블을 읽어 도는 구조**로 만든다.

```java
// 각 규칙 = (대상 상태, 경과조건, 수행 액션). 설정에서 임계값을 읽는다.
public interface PipelineRule {
    boolean appliesTo(OrderSnapshot order, Instant now);
    void apply(OrderSnapshot order);
}

OrderPipelineScheduler
  → 만료 후보 조회(상태 + 시각 인덱스)
  → 규칙별 적용, 건별 try/catch (R7.4)
  → 각 액션은 기존 유스케이스 호출 (R7.3 멱등성은 유스케이스가 이미 보장)
```

**설계 판단 — 새 전이 경로를 만들지 않는다.** 자동 구매확정은 `CompleteOrderUseCase`를,
자동 환불은 `RefundOrderUseCase`를 그대로 호출한다. 정산·시세기록·알림·매물복구가
이미 그 안에 붙어 있어서, 자동 경로를 따로 만들면 로직이 두 벌이 되고 반드시 어긋난다.
멱등성도 기존 유스케이스의 `OrderStateException`이 이미 책임진다.

**타임아웃 값은 전부 `@ConfigurationProperties("gole.pipeline")`** 로 뺀다(R9.1).
운영 중 정책 조정이 배포 없이 가능해야 한다.

### 조회 성능

만료 후보 조회가 매 주기 전체 스캔이 되면 안 된다.
`orders`에 `(status, statusChangedAt)` 복합 인덱스를 추가하고,
`shipments`에 `(status, deliveredAt)` 인덱스를 둔다.

> ⚠️ 인덱스 추가 시 **이름 충돌 주의.** 이 프로젝트는 이미 `follows` 컬렉션에서
> 같은 키에 다른 이름의 인덱스가 남아 앱이 부팅 실패한 전례가 있다
> (`uniq_follow` vs `uq_follower_seller`, 2026-08-03). 새 인덱스는 이름을 확정하고
> 배포 전 기존 인덱스 존재 여부를 확인한다.

### 예외 큐 = 운영자가 보는 전부 (R7.6)

운영자 화면은 대시보드가 아니라 **작업 큐**다. 여기 올라오는 것만이 사람이 볼 대상이다:

| 사유 | 올라오는 조건 |
|---|---|
| 분쟁 | `DISPUTED` 진입 즉시 |
| 배송 정체 | `IN_TRANSIT` 14일 초과 |
| 택배사 미접수 | 송장 등록 후 `PENDING` 3일 초과 |
| 추적 불가 | 트래커 `UNKNOWN` 24시간 연속 |
| 판정 지연 | `DISPUTED` 3일 초과(에스컬레이션) |

정상 진행 건은 **목록에 뜨지 않는다.** 큐가 비어 있으면 운영자가 할 일이 없는 게 정상이다.

> 병행 작업 주의: `admin-console` 스펙이 별도로 진행 중이다. 예외 큐는 그 콘솔의
> 한 화면으로 들어가야 하며, 별도 관리자 UI를 새로 만들지 않는다.

### CS 연락처 (R8)

전화번호는 개인정보다. 저장·노출 규칙을 도메인에서 강제한다:

- `PhoneNumber` 값 객체 — 정규화(숫자만)·형식 검증을 생성자 불변식으로 (R8.3)
- 응답 DTO는 **기본이 마스킹**(`010-****-1234`). 전체 번호는 별도 엔드포인트로만 제공하고,
  거래 당사자 또는 ADMIN만 통과시킨다 (R8.4)
- 운영자의 전체 번호 열람은 감사 로그를 남긴다 (R8.5) —
  `admin` 컨텍스트에 이미 `admin_actions` 컬렉션이 있으므로 그것을 재사용한다

**설계 판단:** 마스킹을 화면단이 아니라 **DTO 기본값**으로 둔다. 화면에서 마스킹하면
새 화면을 만들 때마다 빠뜨릴 수 있고, 실수의 대가가 개인정보 노출이다.

## 새 바운디드 컨텍스트: `shipping`

```
shipping/
  domain/model/         Shipment, Carrier, DeliveryStatus, WaybillNumber
  domain/exception/     InvalidWaybillException, ShipmentNotFoundException
  application/port/in/  RegisterWaybillUseCase, TrackShipmentUseCase, GetShipmentUseCase
  application/port/out/ ShipmentRepositoryPort, DeliveryTrackerPort, TrackerCachePort
  application/service/  ShipmentService
  adapter/in/web/       ShipmentController
  adapter/out/tracker/  StubDeliveryTrackerAdapter (기본)
                        RestDeliveryTrackerAdapter (자격증명 주입 시 활성)
  adapter/out/cache/    RedisTrackerCacheAdapter
  adapter/out/persistence/
```

### 도메인 모델

`Shipment` — 주문 1건의 배송. 불변식:
- `orderId`, `sellerId`는 필수이고 변경 불가
- `waybill`이 바뀌면 `history`에 직전 값을 append (R1.4)
- `status`는 `PENDING → IN_TRANSIT → DELIVERED` 단방향. 역행 금지
  (트래커가 일시적으로 이전 상태를 돌려줘도 되돌리지 않는다 — 외부 API 흔들림 방어)
- `deliveredAt`은 `DELIVERED` 전이 시 1회만 기록

`WaybillNumber` — 값 객체. 정규화(공백 제거, 숫자·하이픈만) + 검증을 생성자 불변식으로 (R1.3).

`DeliveryStatus` — `PENDING` / `IN_TRANSIT` / `DELIVERED` / `UNKNOWN`.
택배사 원문은 `rawStatus`로 별도 보존 (R2.2).

### 외부 트래커 격리 (R6)

```java
public interface DeliveryTrackerPort {
    boolean isConfigured();
    TrackingResult track(Carrier carrier, WaybillNumber waybill);
}
```

`PaymentGatewayPort`가 쓰는 것과 **같은 게이트 패턴**을 따른다:
`@ConditionalOnProperty(... matchIfMissing = true)`로 스텁을 기본 빈으로 두고,
`shipping.tracker.enabled=true` + API 키가 있을 때만 실 어댑터가 우선한다.
자격증명이 없어도 앱은 정상 부팅한다.

스텁은 등록 경과 시간으로 상태를 시뮬레이션한다(등록 직후 `PENDING`,
1분 후 `IN_TRANSIT`, 3분 후 `DELIVERED`). 로컬에서 전 구간을 클릭으로 확인할 수 있어야 한다.

**미정 — 사용자 입력 대기:** 실 트래커 서비스 선정과 API 키.
`RestDeliveryTrackerAdapter`는 인터페이스와 스텁이 확정된 뒤 마지막에 붙인다.

### 캐시 (R2.5)

`TrackerCachePort` + `RedisTrackerCacheAdapter`. `trending-sets`의
`RedisTrendingCacheAdapter`와 동일한 방침 — `StringRedisTemplate`, 예외 흡수,
Redis 장애 시 캐시 미스로 처리.

TTL은 상태별로 다르게 준다: `DELIVERED`는 더 이상 바뀌지 않으므로 길게(24h),
`IN_TRANSIT`은 짧게(10분). 배송 완료 건을 반복 조회하는 낭비를 막는다.

## `order` 컨텍스트 변경

`order`는 **`shipping`을 인바운드 포트로만 참조**한다(NFR-3, 기존 컨텍스트 간 의존 관례).

### 자동 구매확정 (R3.2)

```
AutoCompleteOrdersScheduler (@Scheduled)
  → DELIVERED + deliveredAt + N일 경과 + 분쟁 없음 + status == FUNDS_HELD
  → CompleteOrderUseCase.complete(orderId)   // 기존 유스케이스 재사용
```

멱등성은 기존 `CompleteOrderUseCase`가 이미 보장한다(`OrderStateException`).
스케줄러는 예외를 건별로 흡수해 한 건 실패가 배치를 멈추지 않게 한다.

**설계 판단:** 새 "자동완료" 경로를 만들지 않고 기존 완료 유스케이스를 호출한다.
정산·시세기록·알림이 모두 그 안에 이미 붙어 있어, 별도 경로를 만들면 이중 관리가 된다.

### 분쟁 (R4)

`OrderStatus`에 `DISPUTED` 추가:

```
FUNDS_HELD → DISPUTED → COMPLETED
                      → REFUNDED
```

`DISPUTED`는 `FUNDS_HELD`에서만 진입 가능. `COMPLETED`/`REFUNDED`에서는 불가.
자동 구매확정 조회 조건이 `status == FUNDS_HELD`이므로 타이머 정지(R4.2)는 자동으로 성립한다.

## 수수료 (R5)

현재 `Settlement.compute()`가 상수 5%를 쓴다. 이를 정책 객체로 분리한다:

```java
public record FeePolicy(double rate, long minFee, long maxFee) {
    public long feeFor(long gross) { /* 반올림 + 하한·상한 클램프 */ }
}
```

- `@ConfigurationProperties("gole.fee")`로 외부화 (R5.1)
- `Settlement`에 `feeRate` 필드 추가 — 계산에 쓴 요율을 함께 저장해 과거 정산을 재현 가능하게 (R5.2)
- `Settlement.compute(orderId, sellerId, gross, policy, now)`로 시그니처 변경

**마이그레이션:** 기존 `settlements` 도큐먼트에는 `feeRate`가 없다.
읽을 때 null이면 `0.05`(당시 상수)로 보정한다 — `ListingPersistenceAdapter`가
레거시 필드를 보정하는 것과 같은 방식.

`PLATFORM_FEE_RATE` 상수는 **삭제하지 않고 deprecated**로 남겨 기본값 출처로 쓴다.

### 환불 시 수수료 (R5.5)

이미 `REFUNDED`는 정산을 만들지 않으므로 추가 작업이 없다.
단 `DISPUTED → REFUNDED` 경로에서도 동일함을 테스트로 고정한다.

## 프론트 (FSD)

```
entities/shipment/          타입 + api(register/track/get)
features/register-waybill/  판매자 운송장 입력 폼
features/open-dispute/      구매자 분쟁 제기
widgets/shipment-tracker/   배송 상태 타임라인(표현 전용)
views/order-detail/         위 3개 조립 (기존 화면 확장)
views/admin/                분쟁 목록 + 배송 사실 근거 표시 (R4.3)
```

`widgets/shipment-tracker`는 데이터를 스스로 로드하지 않고 props로 받는다
(`widgets/trending-sets`와 동일한 표현 전용 방침).

## 검증 전략

- 도메인 단위 테스트: 운송장 정규화·상태 역행 금지·수수료 클램프·환불 무수수료
- `ShipmentService` 테스트: 가짜 `DeliveryTrackerPort`로 상태 전이
- 자동 구매확정: 고정 `Clock`으로 N일 경과 시뮬레이션 (기존 서비스들이 `Clock` 주입 관례를 이미 씀)
- 통합: 분쟁 → 판정 → 환불 시 정산 미생성 (Testcontainers)
