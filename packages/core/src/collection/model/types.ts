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

/**
 * 컬렉션 자산 추이의 하루치 점. 백엔드 `value-history` 응답의 `points[]`와 대응. (collection-value-history H3)
 *
 * - `date`: Asia/Seoul 기준 날짜(`YYYY-MM-DD`).
 * - `ownedValue`: 그날 보유 세트 추정가 합(원).
 * - `pricedCount`가 `ownedCount`보다 작으면 시세가 없는 세트가 0원으로 들어간 값이다.
 */
export interface CollectionValuePoint {
  readonly date: string;
  readonly ownedValue: number;
  readonly ownedCount: number;
  readonly pricedCount: number;
}
