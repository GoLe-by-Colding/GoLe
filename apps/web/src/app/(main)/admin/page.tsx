import type { Metadata } from "next";
import { AdminDashboardView } from "@views/admin";

export const metadata: Metadata = { title: "대시보드" };

export default function Page() {
  return <AdminDashboardView />;
}
