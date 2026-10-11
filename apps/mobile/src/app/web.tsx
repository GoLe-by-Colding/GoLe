import { Stack, useLocalSearchParams } from "expo-router";
import { notificationWebPath } from "@/shared/lib";
import { WebScreen } from "@/views/web";

/** 전용 RN 라우트가 없는 알림도 웹의 원래 목적지와 뒤로 가기를 보존한다. */
export default function Screen() {
  const { path } = useLocalSearchParams<{ path?: string }>();
  return (
    <>
      <Stack.Screen options={{ headerShown: true, title: "GoLe", headerBackTitle: "뒤로" }} />
      <WebScreen path={notificationWebPath(path) ?? "/notifications"} nativeHeader />
    </>
  );
}
