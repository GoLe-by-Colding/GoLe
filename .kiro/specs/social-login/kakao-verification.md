# 카카오 연결 검증 — 2026-09-24

- GoLe 앱의 비즈 앱 등록, 대표 도메인, 128px 아이콘 저장을 카카오 콘솔에서 확인함.
- 카카오 로그인 ON, account_email 필수 동의·수집, REST API client secret ON을 확인함.
- 운영 apex 및 localhost 3000·3001·3010 콜백을 등록하고 저장된 목록을 재확인함.
- 로컬 `.env`에 KAKAO_OAUTH_CLIENT_ID, KAKAO_OAUTH_CLIENT_SECRET, KAKAO_OAUTH_SCOPE를 반영함. 개발 CORS·콜백 허용목록에 실제 웹 포트 3001을 포함함.
- API 재기동 후 providers가 kakao를 반환함. 로그인에서 미가입 정책 동의 안내를 확인하고, 필수 약관에 동의한 카카오 가입·인증 코드 교환·세션 발급·홈 복귀까지 Orca에서 실제 수행함.
- 인증 E2E 43건 통과함. 미가입 카카오를 Google 계정으로 잘못 부르던 안내를 소셜 계정으로 수정함.

## 운영 실검증

- 릴리스 PR #160의 main `5cf0f18b`·main CI #35909805772 성공 후 검토된 공식 bootstrap을 설치함. [CD #35910838302](https://github.com/GoLe-by-Colding/GoLe/actions/runs/35910838302)이 성공함.
- 실제 배포 SHA와 env v9, 배포 원장·metadata migration marker 부재, 엄격한 호스트 검증 및 전체 서비스 healthy를 확인함. 공개 health는 200 UP, www는 apex로 301 이동함.
- 운영 providers가 kakao·naver를 반환하며 카카오 버튼으로 실제 인증 코드 교환을 수행함. 미가입 안내 → 필수 정책 3종 동의 → 카카오 가입 → 환영 화면 → 홈을 확인함. 선택적 제3자 제공 동의는 체크하지 않음.
- 같은 브라우저의 쿠키 인증으로 `GET /api/v1/accounts/me` 200을 확인함. UI 로그아웃 후 401과 세션 메타데이터 삭제, 카카오 재로그인 후 홈 복귀와 같은 API 200을 확인함.
- Control 최신 v9만 재시도해 [Secret Sync #35912576700](https://github.com/GoLe-by-Colding/GoLe/actions/runs/35912576700) 성공과 원장의 `배포 완료`를 확인함. 과거 v7·v8을 재배포하지 않음.

실제 계정·키·세션 토큰·인가 코드는 기록하지 않음. 복구 과정의 상세 근거는 팀 볼트의
2026-09-24 이슈 기록과 승찬 개발일지에 남김.
