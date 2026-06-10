import type { Metadata } from "next";
import { AuthPage } from "@views/auth";

export const metadata: Metadata = {
  title: "회원가입",
  robots: { index: false, follow: false },
};

export default function Page() {
  return <AuthPage />;
}
