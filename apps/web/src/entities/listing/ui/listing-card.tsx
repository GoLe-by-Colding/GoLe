import { Badge, Card } from "@shared/ui";
import type { Listing } from "../model/types";
import { conditionLabel, formatPriceKrw } from "../model/types";
import styles from "./listing-card.module.css";

export interface ListingCardProps {
  readonly listing: Listing;
}

export function ListingCard({ listing }: ListingCardProps) {
  const cover = listing.photoUrls[0];

  return (
    <Card interactive padded={false} className={styles.card} data-testid="listing-card">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        className={styles.thumb}
        src={cover ?? "https://placehold.co/600x400?text=LEGO"}
        alt={listing.title}
        loading="lazy"
      />
      <div className={styles.body}>
        <div className={styles.topRow}>
          <Badge tone="neutral">{conditionLabel(listing.condition)}</Badge>
          {listing.catalogSetNumber !== null ? (
            <span className={styles.meta}>#{listing.catalogSetNumber}</span>
          ) : null}
        </div>
        <span className={styles.title}>{listing.title}</span>
        <span className={styles.price}>{formatPriceKrw(listing.price)}</span>
      </div>
    </Card>
  );
}
