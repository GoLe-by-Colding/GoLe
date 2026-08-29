import type { Metadata } from "next";
import { AdminSupportView } from "@views/admin";

export const metadata: Metadata = { title: "운영 문의" };

export default function Page() {
  return <AdminSupportView />;
}
