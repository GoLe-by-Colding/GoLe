import type { Metadata } from "next";
import { OrderDetailPage } from "@views/order-detail";

export const metadata: Metadata = {
  title: "주문 상세",
  robots: { index: false, follow: false },
};

export default async function Page({
  params,
}: {
  readonly params: Promise<{ readonly id: string }>;
}) {
  const { id } = await params;
  return <OrderDetailPage orderId={id} />;
}
