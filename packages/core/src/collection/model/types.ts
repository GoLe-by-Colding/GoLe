/**
 * 컬렉션 도메인 타입. 백엔드 CollectionDtos와 대응.
 */
export type OwnershipStatus = "owned" | "wanted" | "sold";

export interface CollectionItem {
  readonly id: string;
  readonly setNumber: string;
  readonly status: OwnershipStatus;
  readonly createdAt: string;
}

const STATUS_LABEL: Record<OwnershipStatus, string> = {
  owned: "보유",
  wanted: "위시",
  sold: "판매함",
};

export function ownershipLabel(status: OwnershipStatus): string {
  return STATUS_LABEL[status];
}
