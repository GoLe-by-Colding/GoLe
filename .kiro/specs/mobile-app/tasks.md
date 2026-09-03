# 모바일 앱 (React Native) — 작업

단계 0이 막히면 이후 전부가 흔들린다. **0을 끝내기 전에 1로 넘어가지 않는다.**
커밋은 레이어별로 나눈다(`core` → `frontend` → `mobile` → `backend`).

## 0. 스파이크 — pnpm × Metro

- [x] 0.1 `apps/mobile`에 Expo 스캐폴드 → `pnpm install` → `expo export` 번들 성공
- [x] 0.2 워크스페이스 패키지(심링크)를 import했을 때 Metro가 해석하는지 확인
- [x] 0.3 `node-linker` 조정이 필요하면 **웹·API 잡에 미치는 영향**까지 확인
- [x] 0.4 React 버전 충돌 여부 확인 (웹 19.2.4 vs Expo가 고정하는 버전)
- [x] 0.5 결과를 `design.md` 리스크 절에 반영 (스파이크 앱을 그대로 `apps/mobile`로 승격)

## 1. `packages/core` — 런타임

- [ ] 1.1 패키지 스캐폴드. tsconfig는 웹의 strict 풀세트와 동일 수준 (R1.8)
- [ ] 1.2 `runtime/config.ts` — `configureCore()` / `requireConfig()`, 미설정 시 throw (R1.4, R1.5)
- [ ] 1.3 `runtime/session-store.ts` — `SessionStore` 인터페이스 + 주입 (R1.3)
- [ ] 1.4 `runtime/http-client.ts` — `apiRequest`·`ApiError` 이동. 설정·세션을 **호출 시점**에 읽는다
- [ ] 1.5 `runtime/upload-client.ts` — `UploadableImage` 합타입으로 어댑터화 (R1.6)
- [ ] 1.6 `lib/{format,thumbnail,payment-method}.ts` 이동
- [ ] 1.7 `lib/payment-channel.ts` — `resolveChannel`·`requireCardCustomer` 분리 (R7.2, R7.3)
- [ ] 1.8 `package.json` exports 맵 — 슬라이스별 subpath

## 2. `packages/core` — 엔티티 15개

- [ ] 2.1 `entities/*/model/types.ts` 15개 이동 (R1.2)
- [ ] 2.2 `entities/*/api/*.ts` 15개 이동, import를 코어 내부 경로로 교체
- [ ] 2.3 코어에 `next`·`react`·`react-native`·DOM 전역 참조가 없는지 검사 (R1.1)
- [ ] 2.4 `tsc --noEmit` 통과

## 3. 웹 마이그레이션 — 화면 변경 없음

- [ ] 3.1 `shared/config/bootstrap.ts` 추가, `app/layout.tsx`·최상위 클라이언트에서 호출
- [ ] 3.2 `shared/api/session-auth.ts`를 localStorage `SessionStore` 구현으로 축소.
      `gole:session-change` 이벤트는 웹에 유지
- [ ] 3.3 `shared/api/index.ts`·`shared/lib/index.ts`를 코어 재수출 파사드로 교체
- [ ] 3.4 `shared/lib/portone.ts`가 코어의 짝짓기·검증을 호출하도록 교체
- [ ] 3.5 엔티티 15개를 파사드 `index.ts`로 축소, 이동한 `model/`·`api/` 삭제
- [ ] 3.6 `steiger.config.ts`에 `./src/entities/**` → `fsd/no-segmentless-slices` off, **사유 주석**
- [ ] 3.7 품질 게이트 5종 통과 — `format:check` → `lint` → `typecheck` → `fsd:lint` → `build` (R1.7)
- [ ] 3.8 기존 Playwright 스위트 통과 (인프라 + API 서버 + `pnpm e2e:seed` 선행)

## 4. `apps/mobile` — 셸

- [x] 4.1 Expo + expo-router 스캐폴드, `@gole/core` 워크스페이스 의존 (R2.1)
- [x] 4.2 `shared/theme/tokens.ts` — `globals.css` `@theme` 값 이식. `rise`/`fall` 포함 (R2.3)
- [ ] 4.3 부트스트랩 — `configureCore()` + SecureStore `SessionStore` 구현 (R3.2)
- [x] 4.4 하단 탭 5개 (R2.2)
- [ ] 4.5 공통 오류·로딩·빈 상태 컴포넌트 (R5.4)
- [ ] 4.6 iOS·Android 빌드 확인 (R2.4)

## 5. 인증

- [ ] 5.1 로그인·회원가입·로그아웃 (R3.1)
- [ ] 5.2 Bearer 헤더 경로 확인. 쿠키에 의존하지 않는다 (R3.3)
- [ ] 5.3 `401 INVALID_SESSION`만 세션 폐기 → 로그인 화면 (R3.4)
- [ ] 5.4 앱 재시작 후 세션 유지 (R3.5)

## 6. 읽기 화면

- [ ] 6.1 홈 (R5.1)
- [ ] 6.2 검색 + 필터, 페이지네이션·무한 스크롤 (R5.1, R5.2)
- [ ] 6.3 매물 상세 + 갤러리, `thumbnailUrl` 공유 (R5.1, R5.3)
- [ ] 6.4 세트 상세·시세, 시세 탐색 (R5.1)
- [ ] 6.5 셀러샵, 프로필, 컬렉션 (R5.1)
- [ ] 6.6 커뮤니티 목록·글 상세 (R5.1)
- [ ] 6.7 알림함, 주문 상세 (R5.1)

## 7. 쓰기 화면

- [ ] 7.1 매물 등록 — 카메라·앨범 선택 (R6.1)
- [ ] 7.2 다중 이미지 업로드 (`/media/images/batch`) (R6.2)
- [ ] 7.3 찜, 셀러 팔로우 (R6.3)
- [ ] 7.4 커뮤니티 글·댓글·좋아요, 신고, 리뷰 작성 (R6.3)
- [ ] 7.5 1:1 채팅 (R6.4)
- [ ] 7.6 운송장 등록, 분쟁 접수 (R6.5)

## 8. 소셜 로그인

- [ ] 8.1 provider 콘솔에 iOS·Android 앱 등록 + 커스텀 스킴 redirect (3 provider × 2 플랫폼) (R4.2)
- [ ] 8.2 인앱 브라우저 인증 흐름 — 서버 state 왕복 (R4.1, R4.4)
- [ ] 8.3 딥링크 콜백 처리 → 세션 저장
- [ ] 8.4 미설정 provider 비활성 노출 (R4.3)
- [ ] 8.5 사용자 취소를 오류와 구분 (R4.5)

## 9. 결제

- [ ] 9.1 포트원 RN SDK 연동. 채널 짝짓기는 코어 공유 (R7.1, R7.2)
- [ ] 9.2 카드 선택 시 이름·이메일·연락처 확인 단계 (R7.3)
- [ ] 9.3 설정 부재 시 결제 버튼 비활성 (R7.4)
- [ ] 9.4 결제 후 서버 원장 검증 경로 확인 (R7.5)
- [ ] 9.5 사용자 취소와 장애를 구분해 안내 (R7.6)

## 10. 푸시 알림 — 백엔드

- [ ] 10.1 `domain/model/DeviceToken` (R8.1)
- [ ] 10.2 `port/in/RegisterDeviceTokenUseCase`, `port/out/{DeviceTokenRepositoryPort,PushSenderPort}`
- [ ] 10.3 `application/service/DeviceTokenService`
- [ ] 10.4 `adapter/out/persistence` — Mongo. Document와 도메인 모델 분리
- [ ] 10.5 `adapter/out/push/FcmPushSenderAdapter` + 미설정 시 no-op (R8.2, R8.5)
- [ ] 10.6 `adapter/in/web/DeviceTokenController` — POST·DELETE `/api/v1/notifications/devices`
- [ ] 10.7 발송 실패를 삼키는지 테스트 (R8.3)
- [ ] 10.8 `./gradlew spotlessApply && ./gradlew cleanTest test`

## 11. 푸시 알림 — 앱

- [ ] 11.1 권한 요청 + 토큰 등록·해제 (R8.1)
- [ ] 11.2 푸시 탭 → 해당 화면 이동 (R8.4)

## 12. CI / 문서

- [ ] 12.1 `ci.yml`에 `mobile` 잡 추가 (R9.1)
- [ ] 12.2 코어를 web·mobile 잡 양쪽에서 검사 (R9.2)
- [ ] 12.3 `mobile-release.yml` — 수동 실행 (R9.3)
- [ ] 12.4 `AGENTS.md` — 프로젝트·명령어·아키텍처 절 갱신 (R9.4)
- [ ] 12.5 `.env.example`에 앱 관련 설정(FCM·소셜 네이티브 client ID) 문서화
