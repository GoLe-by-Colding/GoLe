import type { Metadata } from "next";
import {
  conditionLabel,
  fetchListingById,
  formatPriceKrw,
  LISTING_CATEGORY_LABEL,
} from "@entities/listing";
import { ListingDetailPage } from "@views/listing-detail";

export const dynamic = "force-dynamic";

/** 매물별 동적 메타데이터 — 공유·SEO용 title/description/OG. */
export async function generateMetadata({
  params,
}: {
  readonly params: Promise<{ readonly id: string }>;
}): Promise<Metadata> {
  const { id } = await params;
  try {
    const listing = await fetchListingById(id);
    const title = `${listing.title} — ${formatPriceKrw(listing.price)}`;
    const description = `${LISTING_CATEGORY_LABEL[listing.category]} · ${conditionLabel(
      listing.condition,
    )} · ${formatPriceKrw(listing.price)}. ${listing.description.slice(0, 80)}`;
    const canonical = `/listings/${id}`;
    return {
      title,
      description,
      alternates: { canonical },
      openGraph: { title, description, url: canonical, type: "website" },
    };
  } catch {
    return {
      title: "매물",
      description: "GoLe에서 레고 중고 매물을 안전결제로 거래하세요.",
    };
  }
}

export default async function Page({
  params,
}: {
  readonly params: Promise<{ readonly id: string }>;
}) {
  const { id } = await params;
  return <ListingDetailPage listingId={id} />;
}
