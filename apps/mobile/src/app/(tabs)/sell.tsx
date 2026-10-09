import { useLocalSearchParams } from "expo-router";
import { WebScreen } from "@/views/web";

export default function Screen() {
  // 다른 탭의 웹 링크가 이 탭으로 넘긴 목적지. WebScreen이 원점·탭·경로를 다시 검증한다.
  const { to, at } = useLocalSearchParams<{ to?: string; at?: string }>();
  return <WebScreen path="/sell" tab="sell" target={to} targetKey={at} />;
}
