import type { Metadata } from "next";
import type { ReactNode } from "react";
import { AdminShell } from "@widgets/admin-shell";

// 콘솔 전 경로 색인 차단. (admin-console 요구사항 1.5)
export const metadata: Metadata = {
  title: { default: "운영자 콘솔", template: "%s · 운영자 콘솔" },
  robots: { index: false, follow: false },
};

export default function AdminLayout({ children }: { readonly children: ReactNode }) {
  return <AdminShell>{children}</AdminShell>;
}
