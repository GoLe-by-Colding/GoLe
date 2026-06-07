import type { ReactNode } from "react";

// 인증 화면은 전체 화면 카드 레이아웃을 쓰므로 헤더 없이 children만 렌더한다.
export default function AuthLayout({ children }: { readonly children: ReactNode }) {
  return <>{children}</>;
}
