import type { Metadata } from "next";
import { AdminReportsView } from "@views/admin";

export const metadata: Metadata = { title: "신고" };

export default function Page() {
  return <AdminReportsView />;
}
