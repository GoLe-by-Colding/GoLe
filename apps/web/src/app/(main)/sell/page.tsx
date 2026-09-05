import type { Metadata } from "next";
import { SellPage } from "@views/sell";

export const metadata: Metadata = {
  title: "브릭 판매하기",
  robots: { index: false, follow: false },
};

export default function Page() {
  return <SellPage />;
}
