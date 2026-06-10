/**
 * 리스팅 도메인 타입. 백엔드 ListingResponse와 1:1 대응.
 */
import { formatKrw } from "@shared/lib";

export type ItemCondition = "new_sealed" | "used_complete" | "used_incomplete";
export type Completeness = "full_box" | "no_box" | "bulk";
export type ListingStatus = "active" | "reserved" | "sold" | "deleted";
export type ListingCategory = "set" | "parts" | "minifig" | "moc";

export const LISTING_CATEGORIES: ReadonlyArray<{ readonly key: ListingCategory; readonly label: string }> = [
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
  used_complete: "중고-완전",
  used_incomplete: "중고-부품일부",
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
