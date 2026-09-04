import type { Metadata } from "next";
import { CollectionPage } from "@views/collection";

export const metadata: Metadata = {
  title: "내 브릭 컬렉션",
  description: "보유한 브릭 세트를 등록하고 가치를 추적하며 컬렉션을 관리하세요.",
  alternates: { canonical: "/collection" },
  // 로그인 사용자 본인 데이터만 보이는 화면이라 색인 가치가 없다. (SEO 스펙 R4.3)
  robots: { index: false, follow: true },
};

export default function Page() {
  return <CollectionPage />;
}
