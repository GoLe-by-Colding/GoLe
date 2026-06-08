import type { ReactNode } from "react";
import { SiteHeader } from "@widgets/site-header";
import { SiteFooter } from "@widgets/site-footer";

// 헤더가 있는 앱 셸. 홈/탐색/시세/커뮤니티 등 메인 화면에 적용.
export default function MainLayout({ children }: { readonly children: ReactNode }) {
  return (
    <>
      <SiteHeader />
      <main className="min-h-[60vh]">{children}</main>
      <SiteFooter />
    </>
  );
}
