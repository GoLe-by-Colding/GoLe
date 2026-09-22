import { Stack } from "expo-router";
import * as SplashScreen from "expo-splash-screen";
import { StatusBar } from "expo-status-bar";
import { useEffect } from "react";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { usePushRegistration } from "@/features/push-notifications";

void SplashScreen.preventAutoHideAsync();

/** 인증과 서비스 화면은 웹이 소유한다. 네이티브 세션을 웹에 주입하지 않는다. */
export default function RootLayout() {
  // 알림 탭 이동은 RN에 남긴다. 웹 로그인 연결 전에는 옛 SecureStore 계정으로 토큰을 등록하지 않는다.
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
