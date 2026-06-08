# Social Login (OAuth2) — Spec

> 구글/카카오/네이버 OAuth2 소셜 로그인. **client-id/secret 등 토큰은 env 플레이스홀더로 외부화**하여, 나중에 환경변수만 주입하면 즉시 동작한다. 토큰 미설정 provider는 비활성(앱은 정상 부팅).

## Requirements (EARS)
- S1 시스템은 Google·Kakao·Naver OAuth2 Authorization Code 플로우를 지원해야 한다.
- S2 IF provider의 client-id가 비어 있으면(env 미주입), 해당 provider는 비활성으로 간주하고 `GET /providers` 목록에서 제외하며, authorize-url/callback 요청은 400 `OAUTH_PROVIDER_NOT_CONFIGURED`로 거부해야 한다(앱 부팅은 영향 없음).
- S3 WHEN 프론트가 `GET /api/v1/auth/oauth/providers`를 호출하면, 시스템은 설정된(활성) provider 목록을 반환해야 한다.
- S4 WHEN `GET /api/v1/auth/oauth/{provider}/authorize-url?redirectUri=&state=`를 호출하면, 시스템은 provider 동의 화면 URL을 반환해야 한다.
- S5 WHEN `POST /api/v1/auth/oauth/{provider}/callback {code, redirectUri}`를 호출하면, 시스템은 code로 provider 토큰을 교환하고 프로필(email, providerId)을 조회해야 한다.
- S6 WHEN 프로필 이메일로 기존 계정이 있으면 그 계정으로, 없으면 새 계정(VERIFIED·USER·임의 비밀번호)을 생성하여 로그인 처리(find-or-create)하고, 기존과 동일한 불투명 세션 토큰을 발급해야 한다.
- S7 IF provider가 이메일을 제공하지 않으면, 시스템은 400 `OAUTH_EMAIL_UNAVAILABLE`로 거부해야 한다.
- S8 IF provider 식별자가 google/kakao/naver가 아니면, 400으로 거부해야 한다.
- S9 프론트는 활성 provider 버튼을 보여주고, 콜백 페이지에서 code/state를 받아 세션을 저장한 뒤 홈으로 이동해야 한다. CSRF 방지를 위해 state를 sessionStorage로 검증해야 한다.

## Design
- 백엔드(account 컨텍스트, 헥사고날):
  - domain `AuthProvider`(GOOGLE/KAKAO/NAVER).
  - port-in `SocialLoginUseCase`: `authorizeUrl(provider, redirectUri, state)`, `enabledProviders()`, `login(SocialLoginCommand{provider, code, redirectUri}) -> SocialLoginResult{accountId, sessionToken, role}`.
  - port-out `SocialIdentityProviderPort`: `isConfigured(provider)`, `authorizeUrl(...)`, `fetchProfile(provider, code, redirectUri) -> SocialProfile{provider, providerId, email}`.
  - service `SocialAuthService`: provider 포트로 프로필 취득 → `AccountRepositoryPort.findByEmail` or `Account.provisioned`(임의 비밀번호 해시) 생성·저장 → `SessionTokenPort.issue` + `SessionStorePort.store`(기존 로그인과 동일 TTL). **Account 애그리거트/암호 정책 무수정**.
  - adapter-out `OAuthProperties`(@ConfigurationProperties `oauth`) + `RestClientSocialIdentityProviderAdapter`(Spring `RestClient`로 token POST + userinfo GET, provider별 프로필 파싱).
  - adapter-in `SocialAuthController` `/api/v1/auth/oauth`.
- 프론트(FSD):
  - `entities/user`: `fetchSocialProviders()`, `fetchSocialAuthorizeUrl(provider, redirectUri, state)`, `socialCallback(provider, code, redirectUri) -> Session`.
  - `features/social-login`: 활성 provider 버튼 → state 생성·sessionStorage 저장 → authorize-url 받아 리다이렉트.
  - `views/oauth-callback` + app route `/auth/callback/[provider]`: code/state 검증 → `socialCallback` → `saveSession` → 홈 이동.
  - 로그인 화면(sign-in)에 버튼 노출.

### 설정(application.yml, env 플레이스홀더)
```
oauth:
  providers:
    google:  { client-id: ${GOOGLE_OAUTH_CLIENT_ID:},  client-secret: ${GOOGLE_OAUTH_CLIENT_SECRET:},  ... }
    kakao:   { client-id: ${KAKAO_OAUTH_CLIENT_ID:},   client-secret: ${KAKAO_OAUTH_CLIENT_SECRET:},   ... }
    naver:   { client-id: ${NAVER_OAUTH_CLIENT_ID:},   client-secret: ${NAVER_OAUTH_CLIENT_SECRET:},   ... }
```
authorization-uri/token-uri/user-info-uri/scope는 provider별 기본값을 두고 env로 덮어쓸 수 있다. **client-id/secret만 나중에 주입하면 동작.**

## Tasks
- [ ] B1 AuthProvider, SocialLoginUseCase, SocialIdentityProviderPort, SocialProfile
- [ ] B2 SocialAuthService (find-or-create + 세션 발급)
- [ ] B3 OAuthProperties + RestClientSocialIdentityProviderAdapter + application.yml
- [ ] B4 SocialAuthController (providers/authorize-url/callback)
- [ ] B5 SocialAuthServiceTest
- [ ] F1 entities/user social API
- [ ] F2 features/social-login 버튼
- [ ] F3 views/oauth-callback + app route + sign-in 연동
- [ ] D1 빌드·배포·스모크

## 보안/후속
- state는 프론트 sessionStorage로 검증(MVP). 후속: 서버측 state 저장/검증, PKCE, provider별 이메일 미인증 처리, 계정-소셜 연결(provider/providerId 영속) 분리.
- 이메일 기준 find-or-create는 동일 이메일=동일 사용자로 간주(소셜↔로컬 통합). 후속에 명시적 계정 연결 UX 가능.
