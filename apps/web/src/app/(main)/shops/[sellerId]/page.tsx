import { SellerShopPage } from "@views/seller-shop";

export default async function Page({
  params,
}: {
  readonly params: Promise<{ readonly sellerId: string }>;
}) {
  const { sellerId } = await params;
  return <SellerShopPage sellerId={sellerId} />;
}
