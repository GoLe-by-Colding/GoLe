import type { Metadata } from "next";
import { AdminAccountsView } from "@views/admin";

export const metadata: Metadata = { title: "회원" };

export default function Page() {
  return <AdminAccountsView />;
}
