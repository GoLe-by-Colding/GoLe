import type { Metadata } from "next";
import { AdminMascotPage } from "@views/admin-mascot";

export const metadata: Metadata = { title: "마스코트" };

export default function Page() {
  return <AdminMascotPage />;
}
