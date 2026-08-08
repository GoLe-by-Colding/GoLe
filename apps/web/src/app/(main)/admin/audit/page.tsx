import type { Metadata } from "next";
import { AdminAuditView } from "@views/admin";

export const metadata: Metadata = { title: "감사 로그" };

export default function Page() {
  return <AdminAuditView />;
}
