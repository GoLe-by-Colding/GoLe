import { Stack } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { useColorScheme } from "react-native";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { themes } from "@/shared/theme";

/**
 * 루트 스택. 탭 그룹을 헤더 없이 싣고, 상세 화면들은 이 스택 위에 push된다.
 */
export default function RootLayout() {
  const scheme = useColorScheme();
  const colors = scheme === "dark" ? themes.dark : themes.light;

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
      </Stack>
    </SafeAreaProvider>
  );
}
