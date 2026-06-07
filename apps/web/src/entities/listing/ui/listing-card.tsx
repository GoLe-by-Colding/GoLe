import { Badge, Card } from "@shared/ui";
import type { Listing } from "../model/types";
import { conditionLabel, formatPriceKrw } from "../model/types";

export interface ListingCardProps {
  readonly listing: Listing;
}

export function ListingCard({ listing }: ListingCardProps) {
  const cover = listing.photoUrls[0];

  return (
    <Card interactive padded={false} className="flex flex-col" data-testid="listing-card">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        className="w-full aspect-[4/3] object-cover bg-neutral-100"
        src={cover ?? "https://placehold.co/600x400?text=LEGO"}
        alt={listing.title}
        loading="lazy"
      />
      <div className="flex flex-col gap-2 p-4">
        <div className="flex items-center justify-between gap-2">
          <Badge tone="neutral">{conditionLabel(listing.condition)}</Badge>
          {listing.catalogSetNumber !== null ? (
            <span className="text-xs text-neutral-500">#{listing.catalogSetNumber}</span>
          ) : null}
        </div>
        <span className="text-base font-semibold leading-tight text-neutral-900 line-clamp-1">
          {listing.title}
        </span>
        <span className="text-lg font-bold text-neutral-900">
          {formatPriceKrw(listing.price)}
        </span>
      </div>
    </Card>
  );
}
