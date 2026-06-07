import { OrderDetailPage } from "@views/order-detail";

export default async function Page({
  params,
}: {
  readonly params: Promise<{ readonly id: string }>;
}) {
  const { id } = await params;
  return <OrderDetailPage orderId={id} />;
}
