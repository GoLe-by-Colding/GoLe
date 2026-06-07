import { ListingDetailPage } from "@views/listing-detail";

export const dynamic = "force-dynamic";

export default async function Page({
  params,
}: {
  readonly params: Promise<{ readonly id: string }>;
}) {
  const { id } = await params;
  return <ListingDetailPage listingId={id} />;
}
