import type { Metadata } from "next";
import { SellPage } from "@views/sell";

export const metadata: Metadata = {
  title: "레고 판매하기",
  robots: { index: false, follow: false },
};

export default function Page() {
  return <SellPage />;
}
