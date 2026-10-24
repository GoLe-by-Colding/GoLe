import * as SplashScreen from "expo-splash-screen";

/**
 * 네이티브 스플래시가 붙잡을 수 있는 최대 시간. 첫 웹 화면이 늦어도 이 뒤에는 내린다 — 이후는 WebView 로딩 표시가 잇는다.
 * (mobile-splash R3.2)
 */
export const SPLASH_MAX_MS = 3000;

let released = false;

/**
 * 네이티브 스플래시를 내린다. 첫 웹 화면이 그려졌거나, 실패 화면이 떴거나, 상한이 지났을 때 부른다.
 * 여러 탭 WebView와 상한 타이머가 겹쳐 불러도 처음 한 번만 효과가 있다. (mobile-splash R3)
 */
export function releaseSplash(): void {
  if (released) return;
  released = true;
  void SplashScreen.hideAsync().catch(() => undefined);
}
