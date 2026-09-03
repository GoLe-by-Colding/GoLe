import type { Metadata } from "next";
import { AccountSecurityPage } from "@views/account-security";

export const metadata: Metadata = {
  title: "계정 보안",
  robots: { index: false, follow: false },
};

export default function Page() {
  return <AccountSecurityPage />;
}
