export type WishlistTargetType = "listing" | "catalog_set";

export interface ListingSummary {
  readonly id: string;
  readonly title: string;
  readonly price: number;
  readonly condition: string;
  readonly catalogSetNumber: string | null;
}

export interface WishlistEntry {
  readonly targetType: WishlistTargetType;
  readonly targetId: string;
}
