import type { Metadata } from "next";
import type { ReactNode } from "react";
import "./globals.css";

export const metadata: Metadata = {
  title: "GoLe — 레고 중고거래 플랫폼",
  description:
    "레고 중고거래·시세·검수·컬렉션·커뮤니티를 한 곳에서. 안전하게 사고팔고 컬렉션을 관리하세요.",
};

export default function RootLayout({
  children,
}: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
