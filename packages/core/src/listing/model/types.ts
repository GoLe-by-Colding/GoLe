/**
 * 리스팅 도메인 타입. 백엔드 ListingResponse와 1:1 대응.
 */
import { formatKrw } from "../../lib";

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

const LEGACY_ITEM_CONDITIONS: Readonly<Record<string, ItemCondition>> = {
  used_complete: "used_good",
  used_incomplete: "used_fair",
};

/**
 * URL·저장 링크에서 넘어온 상태 키를 현재 5단계 등급으로 정규화한다.
 * 백엔드가 흡수하는 레거시 3단계 키와 같은 규칙을 써야 오래된 검색 링크가
 * 필터 없이 전체 결과를 보여 주는 일을 막을 수 있다.
 */
export function parseItemCondition(value: string | undefined): ItemCondition | undefined {
  const normalized = value?.trim().toLowerCase();
  if (normalized === undefined || normalized.length === 0) {
    return undefined;
  }
  if (ITEM_CONDITIONS.includes(normalized as ItemCondition)) {
    return normalized as ItemCondition;
  }
  return LEGACY_ITEM_CONDITIONS[normalized];
}
export type Completeness = "full_box" | "no_box" | "bulk";
export type ListingStatus = "active" | "reserved" | "sold" | "deleted";
export type ListingCategory = "set" | "parts" | "minifig" | "moc";
export type ListingInterestTag =
  | "star-wars"
  | "technic"
  | "creator"
  | "architecture"
  | "city"
  | "ninjago"
  | "harry-potter"
  | "ideas"
  | "super-heroes"
  | "friends"
  | "duplo"
  | "icons"
  | "speed-champions"
  | "minecraft";

export const LISTING_CATEGORIES: ReadonlyArray<{
  readonly key: ListingCategory;
  readonly label: string;
}> = [
  { key: "set", label: "세트" },
  { key: "parts", label: "부품" },
  { key: "minifig", label: "미니피그" },
  { key: "moc", label: "창작품(MOC)" },
];

export const LISTING_INTEREST_TAGS: ReadonlyArray<{
  readonly key: ListingInterestTag;
  readonly label: string;
}> = [
  { key: "star-wars", label: "스타워즈" },
  { key: "technic", label: "테크닉" },
  { key: "creator", label: "크리에이터" },
  { key: "architecture", label: "아키텍처" },
  { key: "city", label: "시티" },
  { key: "ninjago", label: "닌자고" },
  { key: "harry-potter", label: "해리포터" },
  { key: "ideas", label: "아이디어" },
  { key: "super-heroes", label: "슈퍼히어로" },
  { key: "friends", label: "프렌즈" },
  { key: "duplo", label: "듀플로" },
  { key: "icons", label: "아이콘" },
  { key: "speed-champions", label: "스피드챔피언" },
  { key: "minecraft", label: "마인크래프트" },
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
  readonly interestTag: ListingInterestTag | null;
  readonly status: ListingStatus;
  readonly createdAt: string;
  /**
   * 노출 기준 시각 — 등록 또는 마지막 끌올. "최신순"이 이 값으로 정렬된다.
   *
   * <p>아래 필드는 매물 수정·끌올과 함께 추가됐다. 배포 순서가 어긋나 구 API가 응답하는 동안에도
   * 화면이 깨지지 않도록 모두 선택 필드로 읽는다.
   */
  readonly listedAt?: string;
  readonly bumpedAt?: string | null;
  /** 다음 끌올이 가능한 시각(`listedAt + 쿨다운`). */
  readonly bumpAvailableAt?: string;
  /** 직전 가격. 가격이 내려갔을 때만 채워지고, 다시 오르면 비워진다. */
  readonly previousPrice?: number | null;
  readonly priceChangedAt?: string | null;
  /** 수정 폼이 그대로 다시 제출할 저장 키. `photoUrls`와 같은 순서다. */
  readonly photoKeys?: readonly string[];
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
