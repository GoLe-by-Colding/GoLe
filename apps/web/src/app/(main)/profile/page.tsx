import type { Metadata } from "next";
import { ProfilePage } from "@views/profile";

export const metadata: Metadata = {
  title: "내 정보",
  robots: { index: false, follow: false },
};

export default function Page() {
  return <ProfilePage />;
}
