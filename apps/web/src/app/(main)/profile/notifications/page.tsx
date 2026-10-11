import type { Metadata } from "next";
import { NotificationSettingsPage } from "@views/notification-settings";

export const metadata: Metadata = {
  title: "알림 설정",
  robots: { index: false, follow: false },
};

export default function Page() {
  return <NotificationSettingsPage />;
}
