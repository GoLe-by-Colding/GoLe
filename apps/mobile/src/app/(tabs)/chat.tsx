import { Screen, Text } from "@/shared/ui";

/** 채팅 탭. 웹 `views/chat-list`에 대응한다. (스펙 6단계에서 실제 화면으로 교체) */
export default function ChatScreen() {
  return (
    <Screen>
      <Text variant="title">채팅</Text>
      <Text muted>준비 중입니다.</Text>
    </Screen>
  );
}
