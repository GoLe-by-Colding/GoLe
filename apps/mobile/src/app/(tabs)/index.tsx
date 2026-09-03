import { Screen, Text } from "@/shared/ui";

/** 홈 탭. 웹 `views/home`에 대응한다. (스펙 6단계에서 실제 화면으로 교체) */
export default function HomeScreen() {
  return (
    <Screen>
      <Text variant="title">홈</Text>
      <Text muted>준비 중입니다.</Text>
    </Screen>
  );
}
