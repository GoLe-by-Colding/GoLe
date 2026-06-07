import Link from "next/link";
import { ListingCard, type Listing } from "@entities/listing";
import { Text } from "@shared/ui";

export interface ListingGridProps {
  readonly listings: readonly Listing[];
  readonly emptyMessage?: string;
}

export function ListingGrid({
  listings,
  emptyMessage = "표시할 상품이 없습니다.",
}: ListingGridProps) {
  if (listings.length === 0) {
    return (
      <div className="p-12 text-center">
        <Text tone="muted">{emptyMessage}</Text>
      </div>
    );
  }

  return (
    <div
      className="grid gap-5 [grid-template-columns:repeat(auto-fill,minmax(200px,1fr))]"
      data-testid="listing-grid"
    >
      {listings.map((listing) => (
        <Link key={listing.id} href={`/listings/${listing.id}`}>
          <ListingCard listing={listing} />
        </Link>
      ))}
    </div>
  );
}
