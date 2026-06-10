import { notFound } from "next/navigation";
import Link from "next/link";
import {
  completenessLabel,
  conditionLabel,
  fetchListingById,
  formatPriceKrw,
  ListingGallery,
  type Listing,
} from "@entities/listing";
import { ApiError } from "@shared/api";
import { Badge, Button, Container, Heading } from "@shared/ui";
import { PurchaseButton } from "@features/purchase";
import { WishlistButton } from "@features/wishlist-toggle";
import { SetPriceInsight } from "@widgets/set-price-insight";

async function loadListing(id: string): Promise<Listing> {
  try {
    return await fetchListingById(id);
  } catch (cause) {
    if (cause instanceof ApiError && cause.status === 404) {
      notFound();
    }
    throw cause;
  }
}

export interface ListingDetailPageProps {
  readonly listingId: string;
}

export async function ListingDetailPage({ listingId }: ListingDetailPageProps) {
  const listing = await loadListing(listingId);
  const isAvailable = listing.status === "active";

  return (
    <Container width="lg">
      <div className="grid grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)] gap-10 pt-8 max-[820px]:grid-cols-1 max-[820px]:gap-6">
        <ListingGallery photos={listing.photoUrls} alt={listing.title} />
        <div className="flex flex-col gap-4">
          <div className="flex flex-wrap gap-2">
            <Badge tone="neutral">{conditionLabel(listing.condition)}</Badge>
            <Badge tone="brand">{completenessLabel(listing.completeness)}</Badge>
            {listing.catalogSetNumber !== null ? (
              <Badge tone="neutral">#{listing.catalogSetNumber}</Badge>
            ) : null}
            {listing.hasMissingParts ? <Badge tone="warning">부품 누락</Badge> : null}
            {!isAvailable ? <Badge tone="danger">거래완료</Badge> : null}
          </div>
          <Heading level={1}>{listing.title}</Heading>
          <span className="text-3xl font-bold tracking-tight">
            {formatPriceKrw(listing.price)}
          </span>
          <div className="flex flex-col gap-2">
            <span className="text-sm font-semibold text-neutral-800">상품 설명</span>
            <p className="whitespace-pre-wrap leading-relaxed text-neutral-600">
              {listing.description}
            </p>
          </div>

          {/* 상태 고지: 구매자가 구매 전에 확인 */}
          <div className="flex flex-col gap-2 rounded-xl border border-neutral-200 bg-neutral-50 p-4 text-sm">
            <span className="font-semibold text-neutral-800">판매자 상태 고지</span>
            <div className="flex flex-wrap gap-x-4 gap-y-1 text-neutral-600">
              <span>구성: {completenessLabel(listing.completeness)}</span>
              <span>박스: {listing.hasBox ? "있음" : "없음"}</span>
              <span>설명서: {listing.hasManual ? "있음" : "없음"}</span>
              <span>누락 부품: {listing.hasMissingParts ? "있음" : "없음"}</span>
            </div>
            {listing.hasMissingParts && listing.missingPartsNote.length > 0 ? (
              <p className="text-warning">누락: {listing.missingPartsNote}</p>
            ) : null}
            {listing.defectsNote.length > 0 ? (
              <p className="text-neutral-600">하자/손상: {listing.defectsNote}</p>
            ) : null}
          </div>
          <div className="mt-2 flex gap-3">
            <PurchaseButton listingId={listing.id} available={isAvailable} />
            <Button size="lg" variant="secondary" disabled={!isAvailable}>
              채팅하기
            </Button>
          </div>
          {listing.catalogSetNumber !== null ? (
            <WishlistButton targetType="catalog_set" targetId={listing.catalogSetNumber} />
          ) : null}
          <div className="mt-1 flex flex-col gap-1 border-t border-neutral-200 pt-4">
            <Link
              href={`/shops/${listing.sellerId}`}
              className="text-sm text-neutral-500 hover:text-neutral-900"
            >
              판매자 {listing.sellerId.slice(0, 8)} 님의 샵 →
            </Link>
          </div>
        </div>
      </div>

      {listing.catalogSetNumber !== null ? (
        <section className="mt-12 flex flex-col gap-4 border-t border-neutral-200 pt-10 pb-16">
          <Heading level={2}>시세</Heading>
          <SetPriceInsight setNumber={listing.catalogSetNumber} highlight={listing.condition} />
        </section>
      ) : (
        <div className="pb-16" />
      )}
    </Container>
  );
}
