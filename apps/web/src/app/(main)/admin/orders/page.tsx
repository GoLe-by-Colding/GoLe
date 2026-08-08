import type { Metadata } from "next";
import { AdminOrdersView } from "@views/admin";

export const metadata: Metadata = { title: "주문" };

export default function Page() {
  return <AdminOrdersView />;
}
