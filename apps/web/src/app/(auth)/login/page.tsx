import type { Metadata } from "next";
import { SignInPage } from "@views/sign-in";

export const metadata: Metadata = {
  title: "로그인",
  robots: { index: false, follow: false },
};

export default function Page() {
  return <SignInPage />;
}
