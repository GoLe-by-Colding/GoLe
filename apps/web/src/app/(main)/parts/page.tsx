import type { Metadata } from "next";
import { parsePartsBoardTab, PartsBoardPage } from "@views/parts-board";

export const metadata: Metadata = {
  title: "부품 요청",
  description:
    "조립하다 모자란 브릭 부품을 찾는 요청 게시판. 부품 번호·색상·수량을 올리면 그 세트를 가진 회원에게 알림이 가요.",
  alternates: { canonical: "/parts" },
  openGraph: {
    title: "부품 요청 · GoLe",
    description: "조립하다 모자란 브릭 부품을 찾고, 가진 세트로 다른 빌더를 도와주세요.",
    url: "/parts",
    type: "website",
  },
};

function firstParam(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

export default async function Page({
  searchParams,
}: {
  readonly searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const params = await searchParams;
  return (
    <PartsBoardPage
      initialSetNumber={firstParam(params["set"])}
      initialTab={parsePartsBoardTab(firstParam(params["status"]))}
    />
  );
}
