import type { Metadata } from "next";
import { PricesPage } from "@views/prices";

export const metadata: Metadata = {
  title: "브릭 실시간 시세",
  description:
    "검증된 거래 근거가 있는 브릭 세트별 시세와 추이를 확인하세요. 데이터 범위를 함께 표시합니다.",
  alternates: { canonical: "/prices" },
  openGraph: {
    title: "브릭 실시간 시세 · GoLe",
    description:
      "검증된 거래 근거가 있는 브릭 세트별 시세와 추이를 확인하세요. 데이터 범위를 함께 표시합니다.",
    url: "/prices",
    type: "website",
  },
};

export const dynamic = "force-dynamic";

export default async function Page({
  searchParams,
}: {
  readonly searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const params = await searchParams;
  const requestedSet = params["set"];
  const initialSetNumber = Array.isArray(requestedSet) ? requestedSet[0] : requestedSet;

  return <PricesPage initialSetNumber={initialSetNumber} />;
}
