import { Stack } from "expo-router";
import * as SplashScreen from "expo-splash-screen";
import { StatusBar } from "expo-status-bar";
import { useEffect, useState } from "react";
import { useColorScheme } from "react-native";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { useSession } from "@/entities/user";
import { usePushRegistration } from "@/features/push-notifications";
import { bootstrapCore } from "@/shared/config";
import { themes } from "@/shared/theme";

// 부트스트랩이 끝나기 전에 화면을 보여주면 첫 요청이 인증 헤더 없이 나간다.
void SplashScreen.preventAutoHideAsync();

/**
 * 루트 스택. 탭 그룹을 헤더 없이 싣고, 인증 화면들은 이 스택 위에 push된다.
 *
 * 렌더 전에 코어 부트스트랩을 기다린다 — SecureStore 읽기가 비동기라 세션이 메모리에 올라오기
 * 전에는 보호 API가 전부 비로그인으로 나간다.
 */
export default function RootLayout() {
  const scheme = useColorScheme();
  const colors = scheme === "dark" ? themes.dark : themes.light;
  const [ready, setReady] = useState(false);
  const { session } = useSession();

  // 로그인 직후 등록되고, 로그아웃하면 조건이 풀린다.
  // `ready` 전에는 세션이 아직 메모리에 없어 항상 비로그인으로 보인다.
  usePushRegistration(ready && session !== null);

  useEffect(() => {
    let active = true;
    void bootstrapCore()
      .catch(() => undefined)
      .finally(() => {
        if (active) {
          setReady(true);
        }
        void SplashScreen.hideAsync();
      });
    return () => {
      active = false;
    };
  }, []);

  if (!ready) {
    return null;
  }

  return (
    <SafeAreaProvider>
      <StatusBar style="auto" />
      <Stack
        screenOptions={{
          headerStyle: { backgroundColor: colors.background },
          headerTintColor: colors.text,
          contentStyle: { backgroundColor: colors.background },
        }}
      >
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen name="sign-in" options={{ title: "로그인" }} />
        <Stack.Screen name="sign-up" options={{ title: "가입하기" }} />
        {/* 인증은 가입·미인증 로그인 양쪽에서 들어오므로 뒤로 가기로 되돌아갈 곳이 없다. */}
        <Stack.Screen name="verify-email" options={{ title: "이메일 인증" }} />
      </Stack>
    </SafeAreaProvider>
  );
}
