# 검증 — 2026-09-14

## 확인 완료

- Expo SDK의 bundledNativeModules 기준 react-native-webview 13.16.1 설치.
- mobile typecheck / lint / format:check 통과.
- web typecheck / lint / fsd:lint / format:check 통과.
- `node .kiro/specs/mobile-webview/navigation.test.cjs`: 원점·프로토콜·자격 증명·위장 호스트 23개 검사 통과.
- `node .kiro/specs/mobile-webview/placeholder.test.cjs`: 빈 src, 실사진 유지, alt, 텍스트 이스케이프, 3종 결정적 패턴 SSR 회귀 통과.
- git diff --check 통과.
- 기존 Metro에서 앱 연결이 없어 reload가 전달되지 않음을 확인한 뒤, 같은 Orca 터미널에 i를 보내 iPhone 17 Pro에서 새 번들을 열었다. iOS Bundled 1322 modules 확인.
- Orca AX와 Simulator 스크린샷으로 홈 웹 히어로, 상품 탐색 웹 카드, 네이티브 탭 5개, 프로필→웹 로그인 진입을 확인.
- 검색 첫 매물의 빈 사진에 새 브릭 SVG가 표시됐고 해당 카드를 누르면 매물 상세·설명·판매자 상태 고지로 이동함.
- 작업 후 홈 탭으로 복귀. 기존 API :8090 health UP, 웹 :3000 HTTP 200, Metro :8081 running을 유지함.

## 남은 검증 / 의도적으로 보류한 것

- 로그인 성공과 탭 간 세션 동기화, 실기기 카메라/영상 선택, FCM 실발송, OAuth 복귀 및 실결제는 이번에 실행하지 않음.
- 기존 네이티브 푸시 클릭 리스너는 유지하지만 토큰 등록은 웹 인증 브리지 구현 전까지 false로 보류. 과거 SecureStore의 다른 계정으로 등록하는 것을 피함.
- Android·배포 바이너리·오프라인 복구·이력 제스처 실동작은 미검증. iOS Expo Go 관찰을 배포 앱 검증으로 표현하지 않음.
- `.env.local` 값은 수정하지 않았고 Firebase/Sentry 등의 기존 미커밋 변경을 보존함. 설치 중 기존 react-dom/react peer 경고가 있었으며 SDK 전체 업그레이드는 하지 않음.
- 이번 변경은 아직 커밋·푸시하지 않음. package.json과 pnpm-lock.yaml에는 기존 타 작업도 함께 있으므로 전체를 임의 스테이징하지 않음.

## 팀 기록

GoLe-obsidian의 `04_프론트엔드/RN과 웹뷰 역할 분리.md`, 디자인 시스템·README와 `06_개발로그/2026-09-14_RN 네이티브 탭에 모바일 웹을 연결함.md`에 실제 구현과 남은 경계를 기록함.
