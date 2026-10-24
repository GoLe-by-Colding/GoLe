import {
  conditionLabel,
  LISTING_CATEGORIES,
  type ItemCondition,
  type ListingCategory,
  type ListingSort,
} from "@entities/listing";

export interface ListingFilterValues {
  readonly query: string;
  readonly condition: ItemCondition | "";
  readonly category: ListingCategory | "";
  readonly minPrice: string;
  readonly maxPrice: string;
  readonly sort: ListingSort;
}

export const LISTING_SORT_OPTIONS: ReadonlyArray<{ value: ListingSort; label: string }> = [
  { value: "newest", label: "최신순" },
  { value: "price_asc", label: "가격 낮은 순" },
  { value: "price_desc", label: "가격 높은 순" },
];

/** 원 단위 금액이다. 소수·지수 표기·정확하게 표현할 수 없는 수는 받지 않는다. */
export function parseFilterPrice(value: string | undefined): number | undefined {
  const text = value?.trim() ?? "";
  if (!/^\d+$/.test(text)) return undefined;
  const amount = Number(text);
  return Number.isSafeInteger(amount) ? amount : undefined;
}

export function filterPriceError(values: Pick<ListingFilterValues, "minPrice" | "maxPrice">): {
  field: "minPrice" | "maxPrice";
  message: string;
} | null {
  for (const field of ["minPrice", "maxPrice"] as const) {
    if (values[field].trim() && parseFilterPrice(values[field]) === undefined) {
      return { field, message: "가격은 0원 이상의 정수로 입력해 주세요." };
    }
  }
  const min = parseFilterPrice(values.minPrice);
  const max = parseFilterPrice(values.maxPrice);
  return min !== undefined && max !== undefined && min > max
    ? { field: "maxPrice", message: "최대가는 최소가보다 크거나 같아야 해요." }
    : null;
}

export function listingSearchHref(values: ListingFilterValues): string {
  const qs = new URLSearchParams();
  if (values.query.trim()) qs.set("query", values.query.trim());
  if (values.condition) qs.set("condition", values.condition);
  if (values.category) qs.set("category", values.category);
  for (const field of ["minPrice", "maxPrice"] as const) {
    const amount = parseFilterPrice(values[field]);
    if (amount !== undefined) qs.set(field, String(amount));
  }
  if (values.sort !== "newest") qs.set("sort", values.sort);
  const suffix = qs.toString();
  return suffix ? `/search?${suffix}` : "/search";
}

/** 각 링크는 주소에 적용된 조건 하나만 제거한다. 저장하지 않은 입력 초안과 섞지 않는다. */
export function appliedListingFilters(initial: ListingFilterValues): readonly {
  key: string;
  label: string;
  href: string;
}[] {
  const chips: Array<{ key: string; label: string; href: string }> = [];
  const without = (patch: Partial<ListingFilterValues>) =>
    listingSearchHref({ ...initial, ...patch });
  if (initial.category) {
    const label = LISTING_CATEGORIES.find((c) => c.key === initial.category)?.label;
    if (label) chips.push({ key: "category", label, href: without({ category: "" }) });
  }
  if (initial.condition) {
    chips.push({
      key: "condition",
      label: conditionLabel(initial.condition),
      href: without({ condition: "" }),
    });
  }
  for (const field of ["minPrice", "maxPrice"] as const) {
    const amount = parseFilterPrice(initial[field]);
    if (amount !== undefined) {
      chips.push({
        key: field,
        label: `${amount.toLocaleString("ko-KR")}원 ${field === "minPrice" ? "이상" : "이하"}`,
        href: without({ [field]: "" }),
      });
    }
  }
  if (initial.sort !== "newest") {
    const label = LISTING_SORT_OPTIONS.find((s) => s.value === initial.sort)?.label;
    if (label) chips.push({ key: "sort", label, href: without({ sort: "newest" }) });
  }
  return chips;
}
