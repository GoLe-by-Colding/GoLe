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
  /** 노출 기준 시각(등록 또는 마지막 끌올). 구 API는 내려주지 않으므로 선택 필드다. */
  readonly listedAt?: string;
  /** 직전 가격. 가격이 내려갔을 때만 채워진다. */
  readonly previousPrice?: number | null;
}

export interface WishlistEntry {
  readonly targetType: WishlistTargetType;
  readonly targetId: string;
}
