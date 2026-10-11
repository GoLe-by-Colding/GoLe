# Mobile Splash Tasks

- [x] 스펙(requirements · design · tasks)
- [x] `build-brand-icons.mjs` splash 변형: 흰 배경용 정본 색 고래(폭 150dp) + 둥근 선 벡터 워드마크, 288dp 캔버스,
      1152px PNG, 94dp 원 실측(91.8dp). 재생성 시 스플래시 외 산출물(아이콘·어댑티브·알림·favicon·apple-icon·카카오)은 바이트 동일
- [x] `app.json` splash: `#FFFFFF`, `imageWidth 288`
- [x] `shared/lib/app-splash.ts`(`releaseSplash` 멱등, `SPLASH_MAX_MS` 3초) + RootLayout 상한 타이머 + WebScreen 첫 로드/실패에서 해제
- [x] mobile typecheck · lint · format:check, core typecheck(core 테스트 파일 없음), `expo export`(ios·android) 통과
- [x] iOS 시뮬레이터 Release 네이티브 빌드(`expo prebuild -p ios --clean` → `expo run:ios --configuration Release`, iPhone 17 Pro)
      — 콜드 실행 0.7~4.1초 흰 배경·큰 파란 고래·GoLe 스플래시, 4.5초에 웹 홈으로 바로 전환(중간 흰 화면·스피너 없음)
- [x] Android `expo prebuild -p android`(SDK 없음) 리소스 확인 — `splashscreen_background #FFFFFF`,
      `windowSplashScreenAnimatedIcon`, `splashscreen_logo` mdpi 288px ~ xxxhdpi 1152px
- [ ] 미검증: Android 12+ 실기기/에뮬레이터의 원형 마스크 실화면, 오프라인 첫 실행에서 실패 화면 즉시 노출(코드 경로만 확인),
      실기기 iOS. 네이티브 자산이라 반영에는 새 EAS 빌드(iOS build 7 · Android versionCode 3)가 필요하다
