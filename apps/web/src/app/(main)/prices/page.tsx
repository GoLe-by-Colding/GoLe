import type { Metadata } from "next";
import { PricesPage } from "@views/prices";

export const metadata: Metadata = {
  title: "레고 실시간 시세",
  description:
    "체결가 기반 레고 세트별 실시간 시세와 추이를 확인하세요. 시세 데이터로 합리적으로 사고팔 수 있습니다.",
  alternates: { canonical: "/prices" },
};

export const dynamic = "force-dynamic";

export default function Page() {
  return <PricesPage />;
}
