import type { Metadata } from "next";
import { SignUpPage } from "@views/sign-up";

export const metadata: Metadata = {
  title: "회원가입",
  robots: { index: false, follow: false },
};

export default function Page() {
  return <SignUpPage />;
}
