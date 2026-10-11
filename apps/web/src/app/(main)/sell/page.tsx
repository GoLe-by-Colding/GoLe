import type { Metadata } from "next";
import { setNumberInSearchText } from "@entities/lego-set";
import { SellPage } from "@views/sell";

export const metadata: Metadata = {
  title: "브릭 판매하기",
  robots: { index: false, follow: false },
};

export default async function Page({
  searchParams,
}: {
  readonly searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const sp = await searchParams;
  const raw = Array.isArray(sp["setNumber"]) ? sp["setNumber"][0] : sp["setNumber"];
  // 세트 페이지의 "이 세트 팔기"가 넘긴 번호. 세트 번호 형태만 받고 그 밖은 빈 칸으로 시작한다.
  const initialSetNumber = raw === undefined ? null : setNumberInSearchText(raw);
  return <SellPage initialSetNumber={initialSetNumber} />;
}
