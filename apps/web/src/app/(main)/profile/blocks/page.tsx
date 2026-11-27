import type { Metadata } from "next";
import { BlockedUsersPage } from "@views/blocked-users";

export const metadata: Metadata = {
  title: "차단 관리",
  robots: { index: false, follow: false },
};

export default function Page() {
  return <BlockedUsersPage />;
}
