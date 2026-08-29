import type { Metadata } from "next";
import {
  conditionLabel,
  fetchListingById,
  formatPriceKrw,
  LISTING_CATEGORY_LABEL,
  type Listing,
} from "@entities/listing";
import { ListingDetailPage } from "@views/listing-detail";
import { env } from "@shared/config";
import { JsonLd } from "@shared/ui";
import {
  absoluteUrl,
  breadcrumbJsonLd,
  schemaAvailability,
  schemaItemCondition,
  type BreadcrumbItem,
} from "@shared/lib";

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
    // 판매자가 직접 올린 첫 사진을 OG 이미지로 쓴다(절대 URL이어야 크롤러가 해석한다).
    const cover = listing.photoUrls[0];
    return {
      title,
      description,
      alternates: { canonical },
      openGraph: {
        title,
        description,
        url: canonical,
        type: "website",
        ...(cover === undefined
          ? {}
          : { images: [{ url: absoluteUrl(cover, env.siteUrl), alt: listing.title }] }),
      },
    };
  } catch {
    return {
      title: "매물",
      description: "GoLe에서 레고 중고 매물을 찾고 판매자와 대화해 거래하세요.",
    };
  }
}

/** 매물 Product/Offer 구조화 데이터. 가격·재고는 실제 매물 값만 쓴다(R5.2). */
function listingJsonLd(listing: Listing): Record<string, unknown> {
  const cover = listing.photoUrls[0];
  return {
    "@context": "https://schema.org",
    "@type": "Product",
    "@id": `${env.siteUrl}/listings/${listing.id}#product`,
    name: listing.title,
    description: listing.description.slice(0, 300),
    category: LISTING_CATEGORY_LABEL[listing.category],
    ...(listing.catalogSetNumber === null ? {} : { sku: listing.catalogSetNumber }),
    ...(cover === undefined ? {} : { image: absoluteUrl(cover, env.siteUrl) }),
    offers: {
      "@type": "Offer",
      priceCurrency: "KRW",
      price: listing.price,
      availability: schemaAvailability(listing.status),
      itemCondition: schemaItemCondition(listing.condition),
      url: `${env.siteUrl}/listings/${listing.id}`,
      seller: { "@type": "Person", "@id": `${env.siteUrl}/shops/${listing.sellerId}#seller` },
    },
  };
}

export default async function Page({
  params,
}: {
  readonly params: Promise<{ readonly id: string }>;
}) {
  const { id } = await params;

  // 구조화 데이터용으로만 조회한다. 실패하면 화면은 그대로 두고 JSON-LD만 생략한다.
  let listing: Listing | null = null;
  try {
    listing = await fetchListingById(id);
  } catch {
    listing = null;
  }

  const crumbs: readonly BreadcrumbItem[] =
    listing === null
      ? []
      : listing.catalogSetNumber === null
        ? [
            { name: "홈", path: "/" },
            { name: listing.title, path: `/listings/${listing.id}` },
          ]
        : [
            { name: "홈", path: "/" },
            { name: `레고 ${listing.catalogSetNumber}`, path: `/sets/${listing.catalogSetNumber}` },
            { name: listing.title, path: `/listings/${listing.id}` },
          ];

  return (
    <>
      <ListingDetailPage listingId={id} />
      {listing === null ? null : (
        <>
          <JsonLd data={listingJsonLd(listing)} />
          <JsonLd data={breadcrumbJsonLd(crumbs, env.siteUrl)} />
        </>
      )}
    </>
  );
}
