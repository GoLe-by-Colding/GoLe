import type { ReactNode } from "react";
import { SiteHeader } from "@widgets/site-header";
import { SiteFooter } from "@widgets/site-footer";
import { AdminBar } from "@widgets/admin-bar";
import { OnboardingBanner } from "@widgets/onboarding-banner";

// 헤더가 있는 앱 셸. 홈/탐색/시세/커뮤니티 등 메인 화면에 적용.
export default function MainLayout({ children }: { readonly children: ReactNode }) {
  return (
    <>
      {/* 기존 계정(legacyExempt)에만 뜨는 프로필 완성 안내 — 그 외에는 아무것도 렌더링하지 않는다. */}
      <OnboardingBanner />
      <SiteHeader />
      <main className="min-h-[60vh]">{children}</main>
      <SiteFooter />
      {/* 온사이트 어드민 모드 — ADMIN이 아니면 아무것도 렌더링하지 않는다. */}
      <AdminBar />
    </>
  );
}
