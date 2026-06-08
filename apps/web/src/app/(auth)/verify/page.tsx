import type { Metadata } from "next";
import { VerifyEmailPage } from "@views/verify-email";

export const metadata: Metadata = {
  title: "이메일 인증",
  robots: { index: false, follow: false },
};

export default function Page() {
  return <VerifyEmailPage />;
}
