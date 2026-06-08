import type { Metadata } from "next";
import { AdminPage } from "@views/admin";

export const metadata: Metadata = {
  title: "관리자",
  robots: { index: false, follow: false },
};

export default function Page() {
  return <AdminPage />;
}
