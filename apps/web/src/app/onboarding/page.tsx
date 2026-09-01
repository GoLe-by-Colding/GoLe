import type { Metadata } from "next";
import { OnboardingPage } from "@views/onboarding";

export const metadata: Metadata = {
  title: "프로필 완성",
  robots: { index: false, follow: false },
};

export default function Page() {
  return <OnboardingPage />;
}
