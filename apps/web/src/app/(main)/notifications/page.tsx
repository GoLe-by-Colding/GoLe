import type { Metadata } from "next";
import { NotificationsPage } from "@views/notifications";

export const metadata: Metadata = {
  title: "알림",
  robots: { index: false, follow: false },
};

export default function Page() {
  return <NotificationsPage />;
}
