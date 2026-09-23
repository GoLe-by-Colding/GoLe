import type { Metadata } from "next";
import { AdminPromotionMetricsView } from "@views/admin";

export const metadata: Metadata = { title: "홍보 지표" };

export default function Page() {
  return <AdminPromotionMetricsView />;
}
