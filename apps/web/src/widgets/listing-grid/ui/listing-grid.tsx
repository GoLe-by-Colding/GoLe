import { ListingCard, type Listing } from "@entities/listing";
import { Text } from "@shared/ui";
import styles from "./listing-grid.module.css";

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
      <div className={styles.empty}>
        <Text tone="muted">{emptyMessage}</Text>
      </div>
    );
  }

  return (
    <div className={styles.grid} data-testid="listing-grid">
      {listings.map((listing) => (
        <ListingCard key={listing.id} listing={listing} />
      ))}
    </div>
  );
}
