import { Stack } from "expo-router";
import * as SplashScreen from "expo-splash-screen";
import { StatusBar } from "expo-status-bar";
import { useEffect } from "react";
import { SafeAreaProvider } from "react-native-safe-area-context";
import {
  configureForegroundNotifications,
  usePushRegistration,
} from "@/features/push-notifications";

void SplashScreen.preventAutoHideAsync();
configureForegroundNotifications();

/** 인증과 서비스 화면은 웹이 소유한다. 네이티브 세션을 웹에 주입하지 않는다. */
export default function RootLayout() {
  // 알림 탭 이동은 RN에 남긴다. 토큰 등록은 네이티브가 하지 않는다 — 세션은 웹이 가지므로
  // WebScreen 이 토큰을 웹에 건네고, 로그인한 웹이 자기 세션으로 등록한다.
  usePushRegistration(false);
  useEffect(() => {
    void SplashScreen.hideAsync();
  }, []);
  return (
    <SafeAreaProvider>
      <StatusBar style="dark" />
      <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: "white" } }} />
    </SafeAreaProvider>
  );
}
