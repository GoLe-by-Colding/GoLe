# 모바일 앱 (React Native) — 설계

## 설계 원칙

**모델과 호출은 한 벌, 화면은 두 벌.** 백엔드 계약(엔드포인트·DTO·enum 대문자 변환 같은 규칙)이
두 곳에 복제되면 반드시 어긋난다. 반대로 UI를 억지로 공유하면 웹은 네이티브 제약에, 앱은 DOM
관성에 끌려간다. 그래서 경계를 **`apiRequest` 아래**에 긋는다.

**플랫폼 의존은 주입한다, 분기하지 않는다.** 코어 안에서 `Platform.OS`나 `typeof window`로
갈라지면 코어가 두 플랫폼을 모두 알게 되어 중립성이 사라진다. 세션 저장소·환경설정은 부트스트랩에서
주입하고, 코어는 인터페이스만 안다.

**설정은 호출 시점에 읽는다.** 모듈 스코프에서 읽으면 import 순서에 따라 부트스트랩보다 먼저
평가될 수 있고, 그 사고는 "API 원점이 빈 문자열"이라는 조용한 형태로 나타난다. `apiRequest`는
항상 함수 안에서 호출되므로 지연 읽기로 충분하고, 이러면 import 순서 위험이 아예 없다.

## 1. `packages/core`

### 구조

```
packages/core/
├── src/
│   ├── runtime/
│   │   ├── config.ts         # configureCore() / requireConfig()
│   │   ├── session-store.ts  # SessionStore 인터페이스 + 주입
│   │   ├── http-client.ts    # apiRequest, ApiError   (web shared/api에서 이동)
│   │   └── upload-client.ts  # uploadImage(s)          (이동 + 어댑터화)
│   ├── lib/
│   │   ├── format.ts         # formatKrw, formatKrwCompact   (이동)
│   │   ├── thumbnail.ts      # thumbnailUrl                   (이동)
│   │   ├── payment-method.ts # paymentMethodLabel             (이동)
│   │   └── payment-channel.ts# 결제수단 → 채널 짝짓기          (portone.ts에서 분리)
│   ├── user/ listing/ order/ pricing/ ... (15 슬라이스)
│   │   ├── model.ts          # entities/<slice>/model/types.ts
│   │   ├── api.ts            # entities/<slice>/api/*.ts
│   │   └── index.ts
│   └── index.ts
└── package.json              # exports 맵으로 슬라이스별 subpath 공개
```

슬라이스별 subpath(`@gole/core/listing`)로 공개한다. 단일 배럴(`@gole/core`)은 15개 슬라이스의
타입 이름이 충돌하고(`Status`, `Summary` 류) FSD의 슬라이스 격리도 잃는다.

### 환경설정 — `runtime/config.ts`

```ts
export interface CoreConfig {
  readonly apiBaseUrl: string;
  readonly publicApiBaseUrl: string;
}

let config: CoreConfig | null = null;

export function configureCore(next: CoreConfig): void { config = Object.freeze({ ...next }); }

/** 부트스트랩 전 호출을 조용히 넘기지 않는다(R1.5). */
export function requireConfig(): CoreConfig {
  if (config === null) throw new Error("configureCore()를 먼저 호출해야 합니다.");
  return config;
}
```

웹의 `shared/config/env.ts`는 **그대로 남는다** — `siteUrl`·포트원 키 등 웹 전용 값이 있다.
그중 코어가 쓰는 두 개만 부트스트랩에서 넘긴다.

### 세션 저장소 — `runtime/session-store.ts`

```ts
export interface SessionStore {
  readAuthorizationHeader(): Readonly<Record<string, string>>;
  clear(): void;
}
```

동기 인터페이스인 이유: 현재 `apiRequest`가 매 요청마다 동기로 헤더를 만든다. 앱의
SecureStore는 비동기이므로 **부트스트랩에서 1회 읽어 메모리에 캐시**하고, 로그인·로그아웃 때
캐시와 SecureStore를 함께 갱신한다. 인터페이스를 비동기로 바꾸면 `apiRequest`와 그 호출부 전부가
전염되므로 그 비용을 코어가 아니라 앱 어댑터가 진다.

| 플랫폼 | 구현 |
|---|---|
| 웹 | `localStorage["gole.session"]` — 현행 `session-auth.ts`와 동일 |
| 앱 | `expo-secure-store` + 메모리 캐시 (R3.2) |

`clearStoredSession()`이 웹에서 발행하던 `gole:session-change` 이벤트는 **웹 어댑터에 남긴다**.
DOM 이벤트는 코어의 관심사가 아니다. 앱은 같은 자리에서 상태 스토어를 갱신한다.

### 업로드 어댑터 — `runtime/upload-client.ts`

```ts
/** 웹은 File, 앱은 { uri, name, type }. FormData가 둘 다 받는다. */
export type UploadableImage = File | { readonly uri: string; readonly name: string; readonly type: string };
```

`FormData`는 RN에도 전역으로 있고 위 객체 리터럴을 받는다. 따라서 분기는 **타입 수준에서만**
필요하고 런타임 코드는 한 벌이다.

### 결제 채널 짝짓기 — `lib/payment-channel.ts`

`shared/lib/portone.ts`에서 **SDK를 모르는 부분만** 떼어낸다.

```ts
export type PortOneMethod = "kakaopay" | "card";

/** 결제수단 하나가 채널 키와 payMethod를 함께 정한다. 서버가 이 짝을 검증한다. */
export function resolveChannel(
  method: PortOneMethod,
  keys: { readonly kakaopay: string; readonly card: string },
): { readonly channelKey: string; readonly payMethod: "EASY_PAY" | "CARD" };

export function requireCardCustomer(customer: PortOneCustomer | undefined): PortOneCustomer;
```

`buildPortOnePaymentRequest`(웹, iframe/redirect)와 앱의 요청 조립은 각자 남지만, **짝짓기와
구매자 정보 검증이라는 규칙은 한 곳**이다. 이게 어긋나면 결제가 전부 `PAYMENT_REVIEW`로 떨어지므로
공유 대상으로 삼을 값어치가 있다.

## 2. 웹 마이그레이션 — 파사드

상위 레이어의 `@entities/*` 참조가 **134건 / 75파일**이다. 이걸 전부 고치면 리뷰가 불가능해지고
회귀 위험만 커진다. 그래서 **엔티티 슬라이스를 코어 위의 얇은 파사드로 남긴다.**

```ts
// apps/web/src/entities/listing/index.ts
export * from "@gole/core/listing";          // 모델 · API (코어)
export { ListingCard } from "./ui/listing-card";   // 화면 (웹 전용, 그대로)
```

- 상위 75개 파일의 import는 **한 줄도 바뀌지 않는다.**
- `ui/`를 가진 2개 슬라이스(`lego-set`·`listing`)는 세그먼트가 남는다.
- 나머지 13개는 `index.ts`만 남아 **세그먼트 없는 슬라이스**가 된다 → steiger
  `fsd/no-segmentless-slices`가 걸린다. `src/app/**`에 이미 같은 예외가 있으므로
  `./src/entities/**`에도 **사유를 적어** 같은 방식으로 끈다.

`shared`도 같은 방식이다.

| 파일 | 처리 |
|---|---|
| `shared/api/index.ts` | 코어 재수출 + `server-session-headers`는 웹에 유지 |
| `shared/api/session-auth.ts` | localStorage `SessionStore` 구현으로 축소 |
| `shared/config/env.ts` | 유지. 부트스트랩에서 코어에 두 값 주입 |
| `shared/lib/index.ts` | `formatKrw`·`thumbnailUrl`·`paymentMethodLabel`을 코어에서 재수출 |
| `shared/lib/{class-names,seo}.ts` | 웹 유지 (Tailwind·next) |
| `shared/lib/portone.ts` | 유지하되 짝짓기·검증은 코어 호출로 교체 |

부트스트랩은 `shared/config/bootstrap.ts` 한 파일이고 `app/layout.tsx`와 최상위 클라이언트
프로바이더에서 호출한다. 설정을 호출 시점에 읽으므로(설계 원칙) import 순서 위험은 없다.

**eslint-plugin-boundaries는 손댈 필요가 없다.** `boundaries/include`가 `src/**/*`이고
`element-types`는 인식된 엘리먼트 사이만 본다 — 외부 패키지 import는 대상이 아니다.

## 3. `apps/mobile`

Expo + expo-router. React 버전은 웹(19.2.4)과 **워크스페이스에서 충돌하지 않는 조합**으로
맞춘다 — Expo SDK가 고정하는 React가 기준이고, 어긋나면 웹을 따라 올리는 게 아니라 앱을
`.npmrc` 수준에서 격리한다.

### 라우트

```
app/
  (tabs)/index          홈            ← views/home + following-feed
  (tabs)/search         검색          ← views/search (+ listing-filter)
  (tabs)/sell           판매          ← views/sell (내 매물 + 등록 진입)
  (tabs)/chat           채팅          ← views/chat-list
  (tabs)/me             내 정보       ← views/profile + collection
  listing/[id]          매물 상세     ← views/listing-detail
  set/[number]          세트·시세     ← views/set-detail
  prices                시세 탐색     ← views/prices
  seller/[id]           셀러샵        ← views/seller-shop
  community/            목록·글·작성  ← views/community(-post,-compose)
  order/[id]            주문 상세·결제 ← views/order-detail
  chat/[roomId]         채팅방
  notifications         알림함
  sign-in · sign-up · auth/callback/[provider]
```

**앱에 만들지 않는 웹 화면**: `admin`(범위 밖), `payment-return`·`oauth-callback`(웹 전용 복귀
경로), `account-security`·`password-reset`·`verify-email`(빈도가 낮고 폼이 무겁다 → 인앱
브라우저로 웹을 연다).

### 디자인 토큰

`globals.css`의 `@theme` 값을 `apps/mobile/src/shared/theme/tokens.ts`로 **값만** 옮긴다.
CSS 변수를 런타임에 읽을 수 없으므로 복제이지만, 출처가 하나라는 사실을 주석으로 고정하고
브랜드 색 변경 시 양쪽을 함께 고친다. 특히 `rise`(#f04452, 상승 빨강)·`fall`(#3182f6, 하락 파랑)은
`success`/`danger`와 **반대 문법**이므로 토큰 이름을 그대로 가져간다.

## 4. 소셜 로그인 (R4)

백엔드 변경 없음. `SocialAuthService`가 `redirectUri`를 state와 대조만 하므로 앱이 자기
redirect URI를 보내면 그대로 왕복한다.

```
앱 → GET  /api/v1/auth/social/{provider}/authorize?redirectUri=gole://auth/callback/{provider}
        ← authorizeUrl (서버가 state 발급·저장)
앱 → 인앱 브라우저로 authorizeUrl 열기
    ← gole://auth/callback/{provider}?code=…&state=…
앱 → POST /api/v1/auth/social/{provider}/callback  { code, redirectUri, state }
        ← session (Bearer 토큰)
```

provider 콘솔에 iOS·Android 앱 등록과 커스텀 스킴 redirect 추가가 필요하다(3 provider × 2
플랫폼). 백엔드의 `client-id`가 없는 provider는 웹과 같이 비활성으로 노출한다(R4.3).

## 5. 결제 (R7)

`@portone/browser-sdk`는 웹 전용이므로 앱은 포트원 RN SDK를 쓴다. 짝짓기·구매자 정보 검증은
코어 공유(§1). 검증은 그대로 **서버 원장 재조회**가 확정하고 앱은 성공을 선언하지 않는다.

운영은 포트원 공개 설정 없이 빌드돼 결제가 닫혀 있고 카드 채널은 이니시스 공용 테스트 MID다.
따라서 **앱 결제는 스텁·TEST에서 완성하고 LIVE 전환은 웹과 함께** 판단한다(범위 밖).

## 6. 푸시 알림 (R8)

백엔드에 인프라가 전무하다. `notification` 컨텍스트에 **헥사고날 한 세트를 새로 만든다** —
기존 coolsms 어댑터가 선례다.

```
notification/domain/model/DeviceToken.java
notification/application/port/in/RegisterDeviceTokenUseCase.java
notification/application/port/out/DeviceTokenRepositoryPort.java
notification/application/port/out/PushSenderPort.java
notification/application/service/DeviceTokenService.java
notification/adapter/out/persistence/…       # Mongo
notification/adapter/out/push/FcmPushSenderAdapter.java
notification/adapter/in/web/DeviceTokenController.java   # POST·DELETE /api/v1/notifications/devices
```

- FCM 하나로 Android·iOS(APNs 경유)를 모두 보낸다.
- 발송 실패는 삼킨다(R8.3). 알림 발행은 주문·채팅 트랜잭션에 딸린 부수 효과이지 성공 조건이 아니다.
- 설정 부재 시 no-op 어댑터로 기동한다(R8.5). `PaymentConfigurationGuard`처럼 기동을 막는
  대상이 아니다 — 푸시가 닫혀도 서비스는 정상이다.

## 7. CI / 릴리스

| 파일 | 변경 |
|---|---|
| `ci.yml` | `mobile` 잡 추가(typecheck·lint). `web` 잡에 코어 typecheck 포함 |
| `cd.yml` | **변경 없음.** pm2 배포와 앱 릴리스는 다른 경로다(R9.3) |
| (신규) `mobile-release.yml` | 수동 실행. 앱 빌드·제출 |

부작용: 워크스페이스에 RN 의존성이 들어오면 `pnpm install --frozen-lockfile`이 web·api 잡에서도
무거워진다. 감수한다.

## 리스크

**pnpm × Metro (최우선).** RN/Metro는 전통적으로 hoisted `node_modules`를 전제한다. 루트에
`node-linker=hoisted`를 켜면 웹까지 영향을 받으므로, **다른 작업을 시작하기 전에** 현재 Expo가
이 워크스페이스의 pnpm 링커에서 도는지 스파이크로 확정한다. 여기서 막히면 구조 자체를 다시 잡아야
한다 — 그래서 작업 1번이다.

**웹 회귀.** 마이그레이션은 화면을 바꾸지 않는다(R1.7). 파사드 덕에 상위 75파일이 그대로이므로
품질 게이트 5종 + 기존 Playwright 스위트 통과가 곧 완료 기준이다.

**토큰 복제.** 디자인 토큰이 두 곳에 존재한다. 자동 동기화 대신 주석으로 출처를 고정한다 —
빌드 파이프라인을 하나 더 만들 만큼 자주 바뀌는 값이 아니다.
