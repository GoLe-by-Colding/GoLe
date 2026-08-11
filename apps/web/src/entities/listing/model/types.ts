/**
 * 리스팅 도메인 타입. 백엔드 ListingResponse와 1:1 대응.
 */
import { formatKrw } from "@shared/lib";

/**
 * 매물 상태 등급(고지 축). 백엔드 ItemCondition과 1:1.
 * 3단계 시절 값(used_complete/used_incomplete)은 백엔드가 읽기 시점에 흡수하므로
 * 프론트에는 새 등급만 존재한다.
 */
export type ItemCondition = "new_sealed" | "like_new" | "used_good" | "used_fair" | "damaged";

/** 등급 선택지. 좋은 상태 → 나쁜 상태 순서를 여기 한 곳에서만 정의한다. */
export const ITEM_CONDITIONS: readonly ItemCondition[] = [
  "new_sealed",
  "like_new",
  "used_good",
  "used_fair",
  "damaged",
];
export type Completeness = "full_box" | "no_box" | "bulk";
export type ListingStatus = "active" | "reserved" | "sold" | "deleted";
export type ListingCategory = "set" | "parts" | "minifig" | "moc";

export const LISTING_CATEGORIES: ReadonlyArray<{
  readonly key: ListingCategory;
  readonly label: string;
}> = [
  { key: "set", label: "세트" },
  { key: "parts", label: "부품" },
  { key: "minifig", label: "미니피그" },
  { key: "moc", label: "창작품(MOC)" },
];

export const LISTING_CATEGORY_LABEL: Record<ListingCategory, string> = {
  set: "세트",
  parts: "부품",
  minifig: "미니피그",
  moc: "창작품(MOC)",
};

export interface Listing {
  readonly id: string;
  readonly sellerId: string;
  readonly title: string;
  readonly description: string;
  readonly price: number;
  readonly condition: ItemCondition;
  readonly completeness: Completeness;
  readonly hasBox: boolean;
  readonly hasManual: boolean;
  readonly hasMissingParts: boolean;
  readonly missingPartsNote: string;
  readonly defectsNote: string;
  readonly photoUrls: readonly string[];
  readonly catalogSetNumber: string | null;
  readonly category: ListingCategory;
  readonly status: ListingStatus;
  readonly createdAt: string;
}

const CONDITION_LABEL: Record<ItemCondition, string> = {
  new_sealed: "미개봉",
  like_new: "거의 새것",
  used_good: "중고-양호",
  used_fair: "중고-사용감",
  damaged: "하자 있음",
};

const COMPLETENESS_LABEL: Record<Completeness, string> = {
  full_box: "풀박스",
  no_box: "박스 없음",
  bulk: "벌크(부품)",
};

export function conditionLabel(condition: ItemCondition): string {
  return CONDITION_LABEL[condition];
}

export function completenessLabel(completeness: Completeness): string {
  return COMPLETENESS_LABEL[completeness];
}

export function formatPriceKrw(price: number): string {
  return formatKrw(price);
}
