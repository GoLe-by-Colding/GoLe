import type { Metadata } from "next";
import { PartRequestDetailPage } from "@views/part-request-detail";

/**
 * 요청은 수명이 짧은 글이라 색인하지 않는다.
 *
 * 본문은 브라우저에서 읽는다. 작성자 판정(마감·삭제 vs 도와줄게요)이 브라우저 세션으로만 가능하고,
 * 서버에서 먼저 읽으면 E2E가 응답을 가로챌 수 없어 실제 API 없이는 화면을 검증할 수 없다.
 */
export const metadata: Metadata = {
  title: "부품 요청",
  robots: { index: false, follow: true },
};

export default async function Page({
  params,
}: {
  readonly params: Promise<{ readonly id: string }>;
}) {
  const { id } = await params;
  return <PartRequestDetailPage requestId={id} />;
}
