import { useLocalSearchParams } from "expo-router";
import { WebScreen } from "@/views/web";

export default function Screen() {
  const { sellerId } = useLocalSearchParams<{ sellerId: string }>();
  return <WebScreen path={`/shops/${encodeURIComponent(sellerId ?? "")}`} />;
}
