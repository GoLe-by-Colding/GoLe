# 최초 로그인 온보딩 (onboarding)

## 왜 필요한가

`Account`에는 닉네임·전화번호·관심사·약관동의 필드가 전혀 없다. 회원가입 폼조차 이메일·
비밀번호만 받고, 개인정보 수집·마케팅 수신에 대한 동의를 받는 화면이 어디에도 없다.
소셜 로그인(구글/카카오/네이버) 신규가입은 더 심해서, 온보딩 없이 바로 `VERIFIED` 세션이
발급된다.

이 스펙은 가입 후 첫 로그인 시점에 닉네임 입력 → (현재 배포 정책에서 요구할 때만 전화번호
인증) → 관심 태그 선택 → 개인정보 동의(필수)/마케팅 동의(선택)를 받는 파이프라인을 정의한다.
`card-payment` 스펙이 이미 지적한 "계정에 이름 필드가 없다"는 결함도 닉네임 필드 추가로
함께 해소된다.

## 결정

### D1. 온보딩 완료 여부는 저장하지 않고 필드 유무로 파생시킨다

`AccountStatus`(UNVERIFIED/VERIFIED/SUSPENDED)는 이메일 인증·정지 여부만 표현하며 온보딩과
무관하다. 별도 `onboardingCompleted` 플래그도 두지 않는다. `tradeMode`가 launch 단계에서
파생되고 저장되지 않는 것과 같은 원칙이다.

```
required = !(nickname 있음
  && (!phoneVerificationRequired || phoneVerifiedAt 있음)
  && interestTags 비어있지 않음
  && privacyConsentedAt 있음)
```

각 단계는 성공할 때마다 즉시 `Account`에 영속화한다(끝에서 한 번에 저장하지 않음) — 사용자가
중간에 이탈해도 다음 로그인 때 완료되지 않은 단계부터 재개한다.

### D2. 전화번호 인증 코드는 이메일 인증코드와 분리한다

이메일 `VerificationCode`는 `UNVERIFIED` 상태·60초 재발급 제한·5회 시도 제한이 `Account`
애그리거트 안에 결합돼 있다. 전화번호 인증에 그대로 재사용하면 이메일 인증 상태와 충돌하므로,
Redis에 별도로 저장한다(`RedisOAuthStateStoreAdapter`의 TTL 패턴 재사용).

- `phone:otp:{accountId}` → `{phoneNumber, code, attempts}`, TTL 5분.
- `phone:otp:cooldown:{accountId}` → 존재하면 재발송 거부, TTL 60초(이메일과 동일 정책).
- `phone:otp:daily:{accountId}` → 발송 5회 초과 시 그날은 거부.
- 코드 대조 5회 초과 시 해당 OTP를 무효화하고 재요청을 요구한다.
- 코드 생성은 기존 `NumericVerificationCodeGeneratorAdapter`(6자리, `SecureRandom`)를 그대로
  쓴다.

### D3. OTP 발송 채널은 카카오 알림톡 전용(MVP)

기존 `AlimtalkSenderPort.send(SendAlimtalkCommand(to, templateId, variables))`를 그대로
호출한다 — 신규 발송 포트를 만들지 않는다. 신규 인증코드 템플릿만 카카오에 승인받으면 된다
(코드 밖 운영 과제, 리드타임 있음).

**알려진 제약**: `CoolsmsAlimtalkAdapter`는 SMS/LMS 대체발송이 꺼져 있어(`disableSms=true`,
`coolsms-alimtalk` 스펙 R1.4) 카카오톡 미가입자는 이번 스코프에서 전화번호 인증을 완료할 수
없다. 순수 SMS 경로 추가는 후속 과제로 남긴다.

CoolSMS 채널과 승인 템플릿이 준비되기 전 초기 공개는
`GOLE_ONBOARDING_PHONE_REQUIRED=false`로 전화 단계를 완료 조건에서 제외한다. 상태 API가
`phoneVerificationRequired`를 내려 웹·로그인 응답·일반 온보딩 가드가 같은 식을 사용한다. 다만
이 값이 false여도 신규 판매가 자동으로 열리지는 않는다. 신규 매물과 listing 기반 새 거래방은
별도 판매자 신원확인 래치와 실제 `phoneVerifiedAt`을 모두 요구한다(launch-stage D7). 공개 환경에서
전화 단계를 true로 켰는데 CoolSMS 또는 템플릿이 없으면 기동을 거부한다. 로깅 어댑터는
local/dev/test/e2e에서만 동작하며 OTP 원문은 별도 로컬 옵트인 없이는 기록하지 않는다.

### D4. 전화번호는 계정 간 유일해야 한다

이미 다른 계정에서 `phoneVerifiedAt`이 찍힌 번호는 재사용을 거부한다(`INVALID_PHONE_IN_USE`
등). 1인 다계정 어뷰징 방지 목적. `PhoneNumber` 값 객체는 `account` 컨텍스트에 신규로 둔다
(`order.PhoneNumber`의 정규화·마스킹 패턴을 참고하되 복제 — 컨텍스트 경계상 직접 참조하지
않음). 휴대폰 전용으로 좁혀 `01[016789]\d{7,8}`만 허용한다.

### D5. 서버측 게이팅은 부분 차단 — 매물등록·구매·채팅 시작만 막는다

홈·매물조회·시세 등 둘러보기는 온보딩 여부와 무관하게 항상 허용한다. 서버가 실제로 막는
것은 다음 세 액션뿐이다.

- 매물 등록 (`listing`)
- 주문 생성/구매 (`order`)
- 채팅 대화 시작 (`chat`)

이 저장소는 클라이언트만 믿고 게이트를 걸었다가 실제 우회 사고가 난 전례가 있다
([[07_VOC_Issues/2026-08-29_어드민_클라이언트_게이트_우회]]) — 그래서 위 세 액션은 **서버가**
차단한다. `UserAuthInterceptor`와 같은 레벨(HTTP 어댑터)에 `@RequiresOnboarding` 애노테이션 +
가드 인터셉터를 두고, 판정은 account 컨텍스트의 인바운드 포트(`GetOnboardingStatusUseCase`)를
호출해 수행한다 — "다른 컨텍스트의 인바운드 포트에만 의존" 규칙을 그대로 지킨다. 위반 시
403 `ONBOARDING_REQUIRED`.

### D6. 이 스펙 배포 이전에 이미 존재하던 계정은 강제하지 않는다(`legacyExempt`)

배포 시점에 이미 가입돼 있던 계정에는 1회성 마이그레이션으로 `legacyExempt=true`를 세팅한다
(운영 DB에 직접 실행하는 1회 스크립트 — `.kiro/steering/deploy.md`의 일반 배포 절차와는
별개로 문서화해야 한다). 이후 새로 생성되는 계정은 기본 `false`.

- `legacyExempt=true`인 계정은 D5의 세 액션이 필드 미충족이어도 절대 차단되지 않는다.
- 단, 이 면제는 일반 온보딩에만 적용된다. 신규 매물 등록과 그 매물의 새 거래 연결에 필요한
  판매자 전화번호 인증 및 운영 준비 래치는 면제하지 않는다.
- 대신 프론트가 "프로필을 완성해 보세요" 배너만 노출한다(닫기 가능, 강제 아님).
- 이 플래그는 파생값이 아니라 마이그레이션 시점에 저장하는 사실이다 — 이후 사용자가 배너를
  보고 자발적으로 일부 단계를 완료해도 값이 바뀌지 않는다(하드 게이트로 넘어가지 않음).

### D7. 소셜 로그인은 우선 구글 신규가입만 대상으로 한다

`SocialLoginResult`의 `onboardingRequired`는 `provider === GOOGLE`일 때만 실제 `Account`
상태를 기준으로 계산하고, 카카오·네이버는 항상 `false`를 반환한다. 카카오·네이버 신규가입은
이번 스코프에서 기존 동작(즉시 로그인, `newAccount` 플래그로 환영 화면만 노출)을 그대로
유지한다. 확장 여부는 후속 결정 사항.

### D8. 관심 태그는 신규 컬렉션 없이 정적 curated 목록으로 시작한다

`catalog.LegoSet.theme`는 세트당 자유 텍스트 단일 값이라 사용자가 고르는 다중 선택 태그로
바로 쓸 수 없다. MVP는 백엔드 설정값(10~15개 curated 레고 테마/카테고리)을 `GET
/api/v1/account/interest-tags`로 노출하고, 사용자는 그중 1~5개를 고른다. 실제 테마 인기도
연동은 후속 과제.

### D9. 닉네임 규칙

2~12자, 한글/영문/숫자만 허용(공백·특수문자 불가), 대소문자 구분 없이 유일해야 한다.

## 요구사항

- [x] R1. `Account`에 `nickname`, `phoneNumber`, `phoneVerifiedAt`, `interestTags`,
      `privacyConsentedAt`, `marketingConsentedAt`, `legacyExempt` 필드가 추가된다(전부
      nullable, `legacyExempt`만 boolean 기본값).
- [x] R2. `GET /api/v1/accounts/me/onboarding`이 단계별 완료 여부와 `required`,
      `legacyExempt`, `phoneVerificationRequired`를 반환한다(재개용).
- [x] R3. `PUT /api/v1/accounts/me/onboarding/nickname`이 D9 규칙으로 검증하고, 중복이면
      거부한다.
- [x] R4. `POST /api/v1/accounts/me/onboarding/phone/verification`이 D2/D4 규칙(형식·유일성·
      쿨다운·일일 한도)을 통과한 뒤 D3 경로로 OTP를 발송한다.
- [x] R5. `POST /api/v1/accounts/me/onboarding/phone/verification/confirm`이 코드를 대조하고
      성공 시 `phoneVerifiedAt`을 저장한다. 5회 오답 시 해당 OTP를 무효화한다.
- [x] R6. `PUT /api/v1/accounts/me/onboarding/interest-tags`가 D8 목록 대비 1~5개 범위를
      검증한 뒤 저장한다.
- [x] R7. `POST /api/v1/accounts/me/onboarding/consent`가 개인정보 동의(false면 거부)와
      마케팅 동의(선택)를 받아 각각의 타임스탬프를 저장한다.
- [x] R8. `AccountResponses.MeResponse`, `SignInResult`, `SocialLoginResult`에
      `onboardingRequired: boolean`이 추가된다. 소셜은 D7에 따라 구글만 실값, 나머지는 항상
      `false`. 실값은 D1의 현재 전화 인증 정책을 반영한다.
- [x] R9. 매물등록·주문생성·채팅시작 엔드포인트가 `@RequiresOnboarding` 가드를 통과해야
      한다. `legacyExempt=true`이거나 온보딩을 이미 마친 계정은 항상 통과한다. 위반 시 403
      `ONBOARDING_REQUIRED`.
- [x] R10. 배포 시점 기존 계정 전체에 `legacyExempt=true`를 세팅하는 1회성 마이그레이션
      스크립트가 문서화된다.
- [x] R11. 프론트 `views/onboarding`이 진입 시 R2 응답으로 정책상 필요 없는 전화 단계와 이미
      끝난 단계를 건너뛰고 전체 단계 수·진행률을 동적으로 계산해 재개한다.
- [x] R12. 이메일 로그인 성공(`onboardingRequired=true`)과 구글 신규가입(`newAccount=true`)
      시 `/onboarding`으로 리다이렉트된다. `legacyExempt` 계정은 리다이렉트 대신 닫을 수 있는
      배너만 본다.
- [x] R13. 거래성 액션 호출 중 403 `ONBOARDING_REQUIRED`를 받으면 프론트 공용 API 클라이언트가
      `/onboarding`으로 유도한다.

## TODO — 이번 범위 밖

- 카카오·네이버 신규가입에도 온보딩 적용(D7 후속 결정).
- 순수 SMS 발송 경로 추가(카카오톡 미가입자 커버리지, D3 후속).
- 관심 태그를 `catalog` 실데이터(테마 인기도)와 연동(D8 후속).
- 마케팅 동의 철회 UI, 약관 개정 시 재동의(동의 버전 관리).
- 개인 판매자 신원정보 항목·확인 수단·증빙 보존에 대한 법무 검토와 정식 본인확인 연동. 현재
  전화번호 OTP는 번호 소유 확인 근거일 뿐 실명·주소 확인을 포함하지 않는다. 이 과제가 끝나기
  전에는 운영 `GOLE_SELLER_IDENTITY_VERIFICATION_READY`를 false로 유지한다.

## 범위에서 뺀 것

- 실명 확인(PASS 등 CI/DI 기반 본인인증). 이번 스펙은 "번호 소유 확인(OTP)"만 다루며 법적
  본인인증이 아니다.
- 전화번호 변경 후 재인증 플로우(설정 화면 쪽 후속 과제).

## 관련

- `card-payment` — 계정 이름 필드 부재 문제, 이 스펙의 R1(nickname)이 함께 해소
- `coolsms-alimtalk` — D3가 재사용하는 알림톡 발송 포트
- `social-login` — D7이 확장하는 `SocialLoginResult`
