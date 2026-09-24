# 구글 연결 검증 — 2026-09-24

## 기존 구현과 외부 설정

- 기존 Google OAuth 어댑터·가입 정책·온보딩 코드를 그대로 사용함. 작업 전 현재 로컬 환경, GCP 운영 환경과 보관된 백업, Secret Manager v1~v9에서 Google 자격증명이 없음을 확인함. 수민의 과거 개인 환경 파일이 어떤 상태였는지는 확인하지 않음.
- GoLe 운영 프로젝트 `project-72a52bf1-06aa-4519-b2c`의 Google Auth Platform을 구성하고 웹 애플리케이션 `GoLe Web`을 생성함. 프로젝트 소유 계정으로 작업함.
- 사용자 유형은 외부, 게시 상태는 프로덕션 단계임. 앱 이름 GoLe, 브릭 고래 로고, 실제 지원·개발 연락처, 홈페이지 `https://gole.co.kr`, 개인정보처리방침 `/privacy`, 약관 `/terms`, 승인된 도메인 `gole.co.kr`를 저장함.
- 자바스크립트 원점은 운영 apex와 개발 localhost 3000·3001·3010이며, 리다이렉트는 각 원점의 정확한 `/auth/callback/google` URI임.
- 데이터 액세스는 `openid`·`userinfo.email`만 선택함. 서버의 `GOOGLE_OAUTH_SCOPE=openid email`과 일치하며 Google 인증 센터가 민감·제한 범위를 요청하지 않아 데이터 액세스 인증이 필요하지 않다고 표시함.

## 개발 실검증

- 루트 `.env`의 Google 키와 `apps/web/.env.development.local`의 WEB 3001→API 8090 연결을 구성함. 두 파일 모두 gitignore·0600임. 카카오·네이버 키와 실행 포트는 보존함.
- 로컬 `GOLE_ONBOARDING_PHONE_REQUIRED=false`·`GOLE_ONBOARDING_LOG_VERIFICATION_CODES=false`를 운영 정책과 맞춤. 초기 로컬 설정 누락으로 기본 전화 필수 단계가 나타났으며 API 재기동 후 3단계로 전환됨. 문자 발송이나 인증 우회는 수행하지 않음.
- providers에 google·kakao·naver가 모두 나타남. Orca의 Google 버튼에서 실제 인증 코드 교환·이메일 조회 후 미가입 안내를 확인함.
- 회원가입 화면에서 필수 정책 3종만 동의하고 Google 가입 → 닉네임 → 관심 태그 → 필수 개인정보 수집·이용 동의 → 홈을 확인함. 선택적 제3자 제공·마케팅 동의는 하지 않음.
- `GET /api/v1/accounts/me` 200, 온보딩 `required=false`·`marketingConsented=false`를 확인함. UI 로그아웃 후 401·로컬 세션 메타데이터 삭제, Google 재로그인 후 홈·같은 API 200·온보딩 완료 유지까지 확인함.

## 운영 배포와 실검증

- Control 최신 v9의 `GOOGLE_OAUTH_CLIENT_ID`·`GOOGLE_OAUTH_CLIENT_SECRET`·`GOOGLE_OAUTH_SCOPE` 세 키만 추가한 v10을 백업 후 저장함. production validator 통과, 변경 키 집합 일치, 저장 후 전체 환경 재조회 일치를 확인함. 값은 문서·커밋에 남기지 않음.
- [Secret Sync #35965532646](https://github.com/GoLe-by-Colding/GoLe/actions/runs/35965532646)이 성공했고 Control의 v9→v10 원장 상태가 `배포 완료`임. 이미 배포된 main `5cf0f18b`를 유지한 런타임 설정 배포이며 새 코드 릴리스는 필요하지 않았음.
- 호스트의 실제 환경 버전 10·동일 배포 SHA·상시 서비스 8개 healthy·예외 없는 `gole-verify-host-bootstrap --require-deployment` 통과를 확인함. 공개 health는 200 UP, providers는 google·kakao·naver임.
- Orca 운영 Google 버튼에서 미가입 안내 → 필수 정책 3종 동의 → 실제 신규 계정 생성 → 3단계 온보딩 → 홈을 확인함. 개발과 마찬가지로 선택 동의는 하지 않음.
- 운영 계정 API 200·온보딩 `required=false`, UI 로그아웃 후 401·메타데이터 삭제, Google 재로그인 후 홈·계정 API 200·온보딩 완료 유지까지 확인함. 가짜 코드나 모의 provider로 검증하지 않음.

## 공개와 브랜드 인증의 구분

- 외부 사용자용 프로덕션 로그인 설정과 실제 계정 로그인을 확인함. 다른 일반 사용자 계정 전부의 로그인을 검증한 것은 아님.
- Google 브랜드 자동 검증은 홈페이지 도메인이 해당 계정에 등록되지 않았다는 이유로 미완료임. 앱 이름·로고의 사용자 표시를 승인 완료로 보고하지 않음.
- 프로젝트 소유 계정의 Search Console에 `gole.co.kr` 도메인 속성과 DNS TXT 확인 값을 준비함. 가비아에 도메인 소유 계정 로그인이 필요하여 TXT 저장·재검증은 아직 수행하지 않음. 기존 A 레코드는 변경하지 않음.
- [Google 도메인 인증 가이드](https://support.google.com/cloud/answer/13804266?hl=en)에 따라 DNS 방식의 도메인 속성을 확인해야 함. URL 접두어 파일 추가로 대체하지 않음. 이후 [브랜드 인증 절차](https://developers.google.com/identity/verification/authentication-verification)에 따라 재검증과 브랜딩 게시를 완료해야 함.
- 네이버 일반 공개 검수는 별도 승인 대기임. 네이버 앱 등록자 로그인 성공을 일반 사용자 전체 공개로 보고하지 않음.

구현 근거: `RestClientSocialIdentityProviderAdapter`, `SocialAuthService`, `OnboardingService`,
웹 `views/oauth-callback`·`views/onboarding`. 상태 인수인계와 외부 미결은 팀 볼트의
2026-09-24 승찬 개발일지·알려진 개선 과제에 함께 기록함.
