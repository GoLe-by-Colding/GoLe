import { notFound } from "next/navigation";
import {
  conditionLabel,
  fetchListingById,
  formatPriceKrw,
  type Listing,
} from "@entities/listing";
import { ApiError } from "@shared/api";
import { Badge, Button, Container, Heading, Text } from "@shared/ui";

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
  const cover = listing.photoUrls[0] ?? "https://placehold.co/800x600?text=LEGO";
  const isAvailable = listing.status === "active";

  return (
    <Container width="lg">
      <div className="grid grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)] gap-10 pt-8 pb-16 max-[820px]:grid-cols-1 max-[820px]:gap-6">
        <div className="overflow-hidden rounded-lg border border-neutral-200 bg-neutral-100">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img className="w-full aspect-[4/3] object-cover" src={cover} alt={listing.title} />
        </div>
        <div className="flex flex-col gap-4">
          <div className="flex gap-2">
            <Badge tone="neutral">{conditionLabel(listing.condition)}</Badge>
            {listing.catalogSetNumber !== null ? (
              <Badge tone="brand">#{listing.catalogSetNumber}</Badge>
            ) : null}
            {!isAvailable ? <Badge tone="danger">거래완료</Badge> : null}
          </div>
          <Heading level={1}>{listing.title}</Heading>
          <span className="text-3xl font-bold tracking-tight">
            {formatPriceKrw(listing.price)}
          </span>
          <p className="whitespace-pre-wrap leading-relaxed text-neutral-600">
            {listing.description}
          </p>
          <div className="mt-2 flex gap-3">
            <Button size="lg" disabled={!isAvailable}>
              {isAvailable ? "구매하기" : "거래완료"}
            </Button>
            <Button size="lg" variant="secondary" disabled={!isAvailable}>
              채팅하기
            </Button>
          </div>
          <div className="mt-1 flex flex-col gap-1 border-t border-neutral-200 pt-4">
            <Text size="sm" tone="muted">
              판매자 {listing.sellerId}
            </Text>
          </div>
        </div>
      </div>
    </Container>
  );
}
