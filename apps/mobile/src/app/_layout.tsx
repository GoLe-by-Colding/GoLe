import { Stack } from "expo-router";
import * as SplashScreen from "expo-splash-screen";
import { StatusBar } from "expo-status-bar";
import { useEffect } from "react";
import { SafeAreaProvider } from "react-native-safe-area-context";
import {
  configureForegroundNotifications,
  usePushRegistration,
} from "@/features/push-notifications";
import { releaseSplash, SPLASH_MAX_MS } from "@/shared/lib";

void SplashScreen.preventAutoHideAsync();
configureForegroundNotifications();

/** 인증과 서비스 화면은 웹이 소유한다. 네이티브 세션을 웹에 주입하지 않는다. */
export default function RootLayout() {
  // 알림 탭 이동은 RN에 남긴다. 토큰 등록은 네이티브가 하지 않는다 — 세션은 웹이 가지므로
  // WebScreen 이 토큰을 웹에 건네고, 로그인한 웹이 자기 세션으로 등록한다.
  usePushRegistration(false);
  // 스플래시는 첫 웹 화면이 그려지거나 실패 화면이 뜰 때 WebScreen이 내린다. 웹이 아닌 첫 화면이거나
  // 웹이 늦어도 화면을 붙잡지 않게 상한 뒤에는 여기서 내린다(mobile-splash R3).
  useEffect(() => {
    const timer = setTimeout(releaseSplash, SPLASH_MAX_MS);
    return () => clearTimeout(timer);
  }, []);
  return (
    <SafeAreaProvider>
      <StatusBar style="dark" />
      <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: "white" } }} />
    </SafeAreaProvider>
  );
}
