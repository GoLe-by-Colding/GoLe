# 카드결제 추가 (Card Payment) — 설계

## 설계 원칙

**채널과 결제수단은 한 쌍이다.** 이 스펙의 위험은 검증을 "넓히는" 데 있다. 단순히
`EASY_PAY` 외에 `CARD`도 허용하면, 카카오페이 채널에서 온 카드 결제도 통과한다 — 그건 우리가
계약하지 않은 경로다. 그래서 허용 목록을 `(채널 키, 결제수단 유형, 제공자)` 튜플로 만들고,
**원장의 채널 키로 어느 튜플인지 먼저 정한 뒤 그 튜플이 요구하는 수단만** 인정한다.
검증은 넓어지지 않는다. 같은 폭의 검증이 두 벌 생길 뿐이다.

## 백엔드

### 설정

```yaml
portone:
  channel-key: ${PORTONE_CHANNEL_KEY:}            # 카카오페이 (기존)
  card-channel-key: ${PORTONE_CARD_CHANNEL_KEY:}  # 카드/KG이니시스 (신규, 선택)
  channel-type: ${PORTONE_CHANNEL_TYPE:TEST}      # 두 채널에 공통 적용 (R3.3)
```

`card-channel-key`가 비면 카드는 닫힌다(R4.1). 채널 유형을 채널마다 따로 두지 않는 이유는
R3.3 — TEST와 LIVE를 동시에 여는 구성은 사고이지 기능이 아니다.

### `PortOnePaymentGatewayAdapter` — 허용 채널 목록

생성자에서 허용 채널을 한 번 만들고 불변으로 들고 있는다.

```java
/** 채널 키와 그 채널이 낼 수 있는 결제수단을 한 쌍으로 묶는다. 분리되면 R3.1이 무너진다. */
private record AllowedChannel(String key, PaymentMethodType type, String provider, String label) {}
```

| 채널 | type | provider | label |
|---|---|---|---|
| `channel-key` | `EASY_PAY` | `KAKAOPAY` | `간편결제/KAKAOPAY` |
| `card-channel-key` (설정된 경우만) | `CARD` | `null` | `카드` |

`provider`가 `null`인 것은 의도적이다. `PortOnePaymentMethodMapper`는 간편결제가 아닌 수단에
대해 항상 `PaymentMethod.of(type)`(provider 없음)을 돌려주므로, 카드 원장의 provider는 언제나
`null`이다. 매퍼와 검증이 **같은 경로로 같은 값을 읽는다**는 성질(#53에서 얻은 교훈)을 유지한다.

### 검증 흐름 변경

`findPaymentValidationFailure`만 바뀐다. `findPaymentProvenanceFailure`(ID·상점·V2·통화·금액)는
결제수단과 무관하므로 그대로다.

```
채널 객체 존재?           → 없으면 "결제 채널 정보 누락"
채널 키가 허용 목록에 있나? → 없으면 "결제 채널 키 불일치 또는 누락"   ← 여기서 튜플이 정해진다
채널 유형이 설정과 같나?   → 다르면 "결제 채널 유형 불일치 또는 누락"
method 객체 존재?         → 없으면 "결제수단 정보 누락"
매퍼로 읽은 type == 튜플의 type?      → 다르면 "결제수단 유형 불일치 또는 누락"
매퍼로 읽은 provider == 튜플의 provider? → 다르면 "간편결제 제공자 불일치 또는 누락"
```

마지막 검사의 사유 문구는 튜플의 provider가 있을 때만 "간편결제 제공자"로 쓴다. 카드 튜플은
provider가 `null`이고 매퍼도 항상 `null`을 주므로 이 검사가 카드에서 실패할 일은 없지만,
사유 문구가 거짓말을 하지 않게 분기해 둔다.

### 로그 (R3.5)

`expectedLedger()`가 채널 하나만 찍던 것을 허용 채널 전부로 바꾼다.

```
기대: id=… storeId=… version=V2 currency=KRW amount=… channel.type=TEST
      허용 채널: [channel-key-584…=간편결제/KAKAOPAY, channel-key-283…=카드]
```

관측값(`observedLedger`)은 그대로다 — 정규화하지 않은 원문을 보여준다.

### `PaymentConfigurationGuard`

기존 5개 검사 뒤에 하나 추가한다(R4.2).

```java
if (portOneEnabled && !cardChannelKey.isBlank() && cardChannelKey.trim().equals(channelKey.trim())) {
    throw new IllegalStateException("PORTONE_CARD_CHANNEL_KEY must differ from PORTONE_CHANNEL_KEY");
}
```

카드 채널 키 자체는 필수가 아니므로 blank 검사는 하지 않는다.

### `PortOneReadinessIndicator` / 관리자 API (R5.1)

`Snapshot.provider`(`String`, 항상 `"KAKAOPAY"`)를 **`methods`(`List<String>`)로 교체**한다.
값은 `["KAKAOPAY"]` 또는 `["KAKAOPAY", "CARD"]`. 고정 문자열 하나로는 "지금 무엇이 열려 있는지"를
표현할 수 없다.

`AdminDtos.PaymentReadinessResponse`, 프론트 `AdminPaymentReadiness` 타입, 대시보드 패널 문구가
함께 바뀐다.

## 프론트

### `shared/config/env.ts`

```ts
readonly portOneCardChannelKey: string;   // 없으면 "" → 카드 닫힘
```

### `shared/lib/portone.ts`

`buildPortOnePaymentRequest`에 결제수단을 넘긴다.

```ts
export type PortOneMethod = "kakaopay" | "card";

export interface PortOneCustomer {          // 카드에만 필요
  readonly fullName: string;
  readonly email: string;
  readonly phoneNumber: string;
}

export interface PortOnePayParams {
  readonly paymentId: string;
  readonly orderName: string;
  readonly totalAmount: number;
  readonly method: PortOneMethod;           // 기본 "kakaopay"
  readonly customer?: PortOneCustomer;      // method === "card"이면 필수
}
```

- `kakaopay` → `channelKey: portOneChannelKey`, `payMethod: "EASY_PAY"`, `customer` 없음
- `card` → `channelKey: portOneCardChannelKey`, `payMethod: "CARD"`, `customer` 포함

`isCardPaymentAvailable()`을 함께 내보낸다 — 카드 채널 키가 있고 결제가 스텁이 아닐 때만 참(R1.2).

`getPortOneConfigurationError()`는 카카오페이 채널만 검사한다. 카드 채널이 없는 것은 오류가
아니라 "카드가 닫힌 상태"다.

카드인데 `customer`가 없거나 필드가 비면 `throw` — 결제창을 열기 전에 막는다(R2.4).

### `views/order-detail`

`payment_pending` + 구매자 화면에서:

1. 카드가 사용 가능하면 결제수단 라디오 2개를 보여준다. 아니면 기존과 동일하게 버튼만.
2. 카드를 고르고 결제하기를 누르면 → 구매자 정보 폼(이름/이메일/연락처)을 편다.
   - 이메일: `GET /me`로 미리 채움
   - 연락처: `GET /orders/{id}/contacts`의 `buyerPhone`으로 미리 채움
   - 이름: `localStorage["gole.buyer-name"]`으로 미리 채움(R2.3)
3. 확인을 누르면 결제창을 연다.

카카오페이는 1단계에서 바로 결제창이다(R2.5).

프리필 두 건은 **카드를 고른 순간** 한 번만 가져온다. 화면 진입마다 부르면 카카오페이만 쓰는
구매자에게도 불필요한 왕복이 생긴다.

### 문구

"카카오페이"를 고정으로 박아 둔 안내를 결제수단에 맞게 고친다.

| 위치 | 현재 | 변경 |
|---|---|---|
| 결제 전 안내 | "카카오페이 결제창이 열립니다" | 선택한 수단에 따라 |
| 결제 대기 | "카카오페이 결제를 기다리고 있어요" | "결제를 기다리고 있어요" |
| 환불 진행 | "카카오페이 환불을 처리하고 있어요" | "환불을 처리하고 있어요" |
| 환불 확인 | "카카오페이 결제수단에 반영되기까지" | "결제수단에 반영되기까지" |
| 모바일 복귀 | "카카오페이 승인 결과를…" | "결제 승인 결과를…" |

약관·개인정보 문서의 "PortOne 및 카카오페이"는 **"PortOne(카카오페이·KG이니시스)"** 로 고친다.
결제 대행사를 실제와 다르게 고지하면 안 된다.

## 테스트

### 백엔드

`PortOnePaymentGatewayAdapterTest`에 카드 채널을 아는 어댑터 픽스처를 추가한다.

- 카드 채널의 `PaymentMethodCard` PAID 원장을 승인하고 `PaymentMethod(CARD, null)`을 돌려준다
- **카카오페이 채널 키 + 카드 원장** → 수동 검토 (R3.1, 교차 오염 방지)
- **카드 채널 키 + 간편결제 원장** → 수동 검토 (R3.1, 반대 방향)
- 카드 채널을 설정하지 않은 어댑터에서 카드 원장 → 수동 검토 (R4.1)
- 카드 결제의 전액 환불이 기존 경로로 성공 (R6)

`PaymentConfigurationGuardTest`에 R4.2(같은 키면 기동 거부)를 추가한다.
`PortOneReadinessIndicatorTest`는 `methods` 목록으로 바꾼다.

### 프론트

`portone-request.spec.ts`에 카드 요청 조립 계약을 추가한다 — 채널 키·`payMethod: "CARD"`·
`customer` 3필드. 구매자 정보가 비면 던지는 것도 함께 확인한다.

CI 워크플로에 `NEXT_PUBLIC_PORTONE_CARD_CHANNEL_KEY`를 추가해야 계약 테스트가 돈다.
