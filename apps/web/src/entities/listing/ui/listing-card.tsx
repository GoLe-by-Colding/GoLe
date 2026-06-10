import { Badge, Card } from "@shared/ui";
import { thumbnailUrl } from "@shared/lib";
import type { Listing } from "../model/types";
import {
  completenessLabel,
  conditionLabel,
  formatPriceKrw,
  LISTING_CATEGORY_LABEL,
} from "../model/types";

export interface ListingCardProps {
  readonly listing: Listing;
}

export function ListingCard({ listing }: ListingCardProps) {
  const cover = listing.photoUrls[0];

  return (
    <Card interactive padded={false} className="flex flex-col" data-testid="listing-card">
      <div className="overflow-hidden">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          className="img-zoom w-full aspect-[4/3] object-cover bg-neutral-100"
          src={
            cover === undefined
              ? "https://placehold.co/600x400?text=LEGO"
              : thumbnailUrl(cover, 480)
          }
          alt={listing.title}
          loading="lazy"
        />
      </div>
      <div className="flex flex-col gap-2.5 p-4">
        <div className="flex items-center gap-1.5 flex-wrap">
          {listing.category !== "set" ? (
            <Badge tone="brand">{LISTING_CATEGORY_LABEL[listing.category]}</Badge>
          ) : null}
          <Badge tone="neutral">{conditionLabel(listing.condition)}</Badge>
          <Badge tone="brand">{completenessLabel(listing.completeness)}</Badge>
          {listing.hasMissingParts ? <Badge tone="warning">부품 누락</Badge> : null}
        </div>
        <span className="text-[15px] font-semibold leading-snug text-neutral-900 line-clamp-1">
          {listing.title}
        </span>
        <div className="flex items-baseline justify-between gap-2">
          <span className="text-xl font-extrabold tracking-tight text-neutral-900">
            {formatPriceKrw(listing.price)}
          </span>
          {listing.catalogSetNumber !== null ? (
            <span className="text-xs font-medium text-neutral-400">
              #{listing.catalogSetNumber}
            </span>
          ) : null}
        </div>
      </div>
    </Card>
  );
}
