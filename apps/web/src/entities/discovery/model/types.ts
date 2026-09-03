export type WishlistTargetType = "listing" | "catalog_set";

export interface ListingSummary {
  readonly id: string;
  readonly sellerId: string;
  readonly title: string;
  readonly price: number;
  readonly condition: "new_sealed" | "like_new" | "used_good" | "used_fair" | "damaged";
  readonly catalogSetNumber: string | null;
  readonly category: "set" | "parts" | "minifig" | "moc";
  readonly status: "active" | "reserved" | "sold" | "deleted";
  readonly photoUrls: readonly string[];
  readonly createdAt: string;
}

export interface WishlistEntry {
  readonly targetType: WishlistTargetType;
  readonly targetId: string;
}
