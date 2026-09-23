import { useLocalSearchParams } from "expo-router";
import { WebScreen } from "@/views/web";

export default function Screen() {
  const { setNumber } = useLocalSearchParams<{ setNumber: string }>();
  return <WebScreen path={`/sets/${encodeURIComponent(setNumber ?? "")}`} />;
}
