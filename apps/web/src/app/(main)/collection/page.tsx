import type { Metadata } from "next";
import { CollectionPage } from "@views/collection";

export const metadata: Metadata = {
  title: "내 레고 컬렉션",
  description: "보유한 레고 세트를 등록하고 가치를 추적하며 컬렉션을 관리하세요.",
  alternates: { canonical: "/collection" },
};

export default function Page() {
  return <CollectionPage />;
}
