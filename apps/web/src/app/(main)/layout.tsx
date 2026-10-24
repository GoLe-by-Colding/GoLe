import type { ReactNode } from "react";
import { headers } from "next/headers";
import { SiteHeader } from "@widgets/site-header";
import { SiteFooter } from "@widgets/site-footer";
import { AdminBar } from "@widgets/admin-bar";
import { OnboardingBanner } from "@widgets/onboarding-banner";
import { MobileNavigation } from "@widgets/mobile-navigation";
import { AppPushRegistration } from "./app-push-registration";
import { AppTabLinks } from "./app-tab-links";

// 헤더가 있는 앱 셸. 홈/탐색/시세/커뮤니티 등 메인 화면에 적용.
export default async function MainLayout({ children }: { readonly children: ReactNode }) {
  const inApp = /\bGoLeApp\//.test((await headers()).get("user-agent") ?? "");
  return (
    <div
      className={
        inApp
          ? undefined
          : "pb-[var(--mobile-navigation-height)] [--mobile-navigation-height:calc(4rem+1px+env(safe-area-inset-bottom))] lg:[--mobile-navigation-height:0px] print:pb-0"
      }
    >
      {/* 기존 계정(legacyExempt)에만 뜨는 프로필 완성 안내 — 그 외에는 아무것도 렌더링하지 않는다. */}
      <OnboardingBanner />
      <SiteHeader />
      <main className="min-h-[60vh]">{children}</main>
      <SiteFooter />
      {/* 온사이트 어드민 모드 — ADMIN이 아니면 아무것도 렌더링하지 않는다. */}
      <AdminBar />
      {inApp ? null : <MobileNavigation />}
      {/* 앱(WebView) 안에서만 동작한다 — 네이티브가 건넨 푸시 토큰을 로그인 계정으로 등록한다. */}
      <AppPushRegistration />
      {/* 앱(WebView) 안에서만 동작한다 — 다른 탭의 화면으로 가는 링크를 그 탭으로 넘겨 선택 탭과 화면을 맞춘다. */}
      <AppTabLinks />
    </div>
  );
}
