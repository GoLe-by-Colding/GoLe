# 카카오 연결 검증 — 2026-09-24

- GoLe 앱의 비즈 앱 등록, 대표 도메인, 128px 아이콘 저장을 카카오 콘솔에서 확인함.
- 카카오 로그인 ON, account_email 필수 동의·수집, REST API client secret ON을 확인함.
- 운영 apex 및 localhost 3000·3001·3010 콜백을 등록하고 저장된 목록을 재확인함.
- 로컬 `.env`에 KAKAO_OAUTH_CLIENT_ID, KAKAO_OAUTH_CLIENT_SECRET, KAKAO_OAUTH_SCOPE를 반영함. 개발 CORS·콜백 허용목록에 실제 웹 포트 3001을 포함함.
- API 재기동 후 providers가 kakao를 반환함. 로그인에서 미가입 정책 동의 안내를 확인하고, 필수 약관에 동의한 카카오 가입·인증 코드 교환·세션 발급·홈 복귀까지 Orca에서 실제 수행함.
- 인증 E2E 43건 통과함. 미가입 카카오를 Google 계정으로 잘못 부르던 안내를 소셜 계정으로 수정함.

## 운영 반영 조건

운영은 기존 prepared 배포 원장과 metadata migration pending이 남아 있음. 첫 full CD가 legacy Nginx healthcheck 부재를 장애로 오인해 실패했으며 VM을 다시 시작해 공개 health UP과 adopted runtime 검증을 확인함. 배포 게이트는 false로 복구함.

운영 키 저장·배포와 실제 운영 로그인을 로컬 성공과 혼동하지 않는다. 검토된 호스트 코드 배포와 원장 복구, full CD 이후 Secret Sync 결과까지 별도로 확인해야 한다. 구체적인 운영 결과는 팀 볼트 이슈 기록에 이어 적는다.
