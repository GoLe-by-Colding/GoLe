# 네이버 연결 검증 — 2026-09-24

## 설정과 개발 실연동

- 기존 GoLe 앱에 동일한 128px 브릭 고래 아이콘과 대표 서비스 URL `https://gole.co.kr`를 저장함.
- 필수 제공 정보는 이메일만 유지하며 운영 `/auth/callback/naver`와 실제 개발 원점 `http://localhost:3001`의 같은 콜백을 등록함.
- `NAVER_OAUTH_CLIENT_ID`·`NAVER_OAUTH_CLIENT_SECRET`은 gitignore 로컬 환경 파일과 Control의 GoLe 운영 대상에만 저장함. 실제 값은 출력·문서·커밋에 남기지 않음.
- 개발 providers에서 kakao·naver를 확인한 뒤 실제 네이버 동의, 인증 코드 교환, 세션 발급, 홈 복귀와 로그아웃 후 재로그인을 확인함.
- 테스트 계정은 기존 이메일 계정과 연결됐으며 신규 계정이 생성됐다고 보고하지 않음. 소셜 가입 진행 시 이메일·비밀번호 입력란을 비워 두고 필수 정책 동의만 수행함.
- Control 최신 v8에 네이버 두 키만 병합한 v9를 저장하고 재조회 일치 및 production validator 통과를 확인함.

## 네이버 검수

- 2026-09-24 실제 개발 로그인 흐름 PDF와 마스킹한 이메일 활용 화면을 제출함.
- 검수 상태가 `검수요청`, 최종 요청일이 `2026.09.24`임을 새로고침 후에도 확인함.
- PDF에는 개발 환경과 기존 계정 연결 경로라는 점을 명시함. 기연동 계정의 동의 화면은 공식 `auth_type=reprompt`로 다시 표시해 실제 화면을 캡처함.
- 일반 사용자 공개는 네이버 검수 승인 이후임. 앱 등록자 계정으로 로그인한 사실과 전체 사용자 공개를 구분함.

근거: [네이버 사전 검수 가이드](https://developers.naver.com/docs/login/verify/verify.md),
[네이버 로그인 개발 가이드](https://developers.naver.com/docs/login/devguide/devguide.md).
제출 자료와 운영 적용 상태는 팀 볼트의 2026-09-24 개발로그·승찬 개발일지에 기록함.
