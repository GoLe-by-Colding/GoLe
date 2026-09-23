import { useLocalSearchParams } from "expo-router";
import { WebScreen } from "@/views/web";

export default function Screen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  return <WebScreen path={`/listings/${encodeURIComponent(id ?? "")}`} />;
}
