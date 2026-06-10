import { Suspense } from "react";
import type { Metadata } from "next";
import { AuthPage } from "@views/auth";

export const metadata: Metadata = {
  title: "로그인",
  robots: { index: false, follow: false },
};

export default function Page() {
  return (
    <Suspense fallback={null}>
      <AuthPage />
    </Suspense>
  );
}
