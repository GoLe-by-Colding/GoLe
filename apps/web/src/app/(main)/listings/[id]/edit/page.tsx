import type { Metadata } from "next";
import { ListingEditPage } from "@views/listing-edit";

export const metadata: Metadata = {
  title: "매물 수정",
  robots: { index: false, follow: false },
};

export default async function Page({
  params,
}: {
  readonly params: Promise<{ readonly id: string }>;
}) {
  const { id } = await params;
  return <ListingEditPage listingId={id} />;
}
