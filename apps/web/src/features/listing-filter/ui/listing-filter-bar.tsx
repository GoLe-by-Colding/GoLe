"use client";

import { type FormEvent, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  conditionLabel,
  type ItemCondition,
  type ListingCategory,
  type ListingSort,
  ITEM_CONDITIONS,
  LISTING_CATEGORIES,
} from "@entities/listing";
import { SetAutocomplete } from "./set-autocomplete";
import { Button, Select } from "@shared/ui";

export interface ListingFilterValues {
  readonly query: string;
  readonly condition: ItemCondition | "";
  readonly category: ListingCategory | "";
  readonly minPrice: string;
  readonly maxPrice: string;
  readonly sort: ListingSort;
}

export interface ListingFilterBarProps {
  readonly initial: ListingFilterValues;
}

const CONDITIONS = ITEM_CONDITIONS;

const SORTS: ReadonlyArray<{ readonly value: ListingSort; readonly label: string }> = [
  { value: "newest", label: "최신순" },
  { value: "price_asc", label: "가격 낮은순" },
  { value: "price_desc", label: "가격 높은순" },
];

/** 0 이상 숫자인 금액만 받는다. 주소에 손으로 넣은 "abc" 같은 값은 서버도 버리므로 칩·주소에서도 뺀다. */
function validPrice(value: string): number | null {
  const amount = Number(value);
  return value.trim() !== "" && Number.isFinite(amount) && amount >= 0 ? amount : null;
}

function priceText(value: string): string | null {
  const amount = validPrice(value);
  return amount === null ? null : `${amount.toLocaleString("ko-KR")}원`;
}

/** 필터 값으로 검색 주소를 만든다. 빈 값·잘못된 금액·기본 정렬은 주소에 싣지 않는다. */
function searchHref(values: ListingFilterValues): string {
  const qs = new URLSearchParams();
  if (values.query.trim()) qs.set("query", values.query.trim());
  if (values.condition) qs.set("condition", values.condition);
  if (values.category) qs.set("category", values.category);
  if (validPrice(values.minPrice) !== null) qs.set("minPrice", values.minPrice.trim());
  if (validPrice(values.maxPrice) !== null) qs.set("maxPrice", values.maxPrice.trim());
  if (values.sort !== "newest") qs.set("sort", values.sort);
  const suffix = qs.toString();
  return suffix ? `/search?${suffix}` : "/search";
}

/** 지금 주소에 적용된 필터(검색어 제외)를 칩으로. 각 칩은 그 필터만 뺀 주소를 가리킨다. */
function appliedFilters(
  initial: ListingFilterValues,
): ReadonlyArray<{ readonly key: string; readonly label: string; readonly href: string }> {
  const chips: Array<{ key: string; label: string; href: string }> = [];
  const without = (patch: Partial<ListingFilterValues>) => searchHref({ ...initial, ...patch });
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
  const min = priceText(initial.minPrice);
  if (min !== null)
    chips.push({ key: "minPrice", label: `${min} 이상`, href: without({ minPrice: "" }) });
  const max = priceText(initial.maxPrice);
  if (max !== null)
    chips.push({ key: "maxPrice", label: `${max} 이하`, href: without({ maxPrice: "" }) });
  if (initial.sort !== "newest") {
    const label = SORTS.find((s) => s.value === initial.sort)?.label;
    if (label) chips.push({ key: "sort", label, href: without({ sort: "newest" }) });
  }
  return chips;
}

export function ListingFilterBar({ initial }: ListingFilterBarProps) {
  const router = useRouter();
  const [values, setValues] = useState<ListingFilterValues>(initial);
  const [open, setOpen] = useState(false);

  function update<K extends keyof ListingFilterValues>(key: K, value: ListingFilterValues[K]) {
    setValues((prev) => ({ ...prev, [key]: value }));
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    router.push(searchHref(values));
    setOpen(false);
  }

  // 적용된 필터 수(검색어 제외)
  const activeCount = [
    values.condition,
    values.category,
    priceText(values.minPrice),
    priceText(values.maxPrice),
    values.sort !== "newest" ? values.sort : "",
  ].filter(Boolean).length;
  const chips = appliedFilters(initial);

  return (
    <div className="rounded-lg border border-neutral-200 bg-white">
      {/* 상단 바: 검색어 + 토글 버튼 (항상 노출) */}
      <form onSubmit={handleSubmit}>
        <div className="flex items-center gap-2 p-3">
          <div className="relative flex-1">
            <SetAutocomplete
              id="f-query"
              value={values.query}
              placeholder="검색어, 세트번호"
              onChange={(v) => update("query", v)}
              onSelect={(set) => {
                update("query", set.name);
                // 세트 선택 시 즉시 검색
                router.push(`/search?query=${encodeURIComponent(set.name)}`);
                setOpen(false);
              }}
            />
          </div>
          {/* 필터 토글 (모바일 전용) */}
          <button
            type="button"
            onClick={() => setOpen((v) => !v)}
            className="flex items-center gap-1.5 rounded-md border border-neutral-200 px-3 py-2 text-sm font-medium text-neutral-700 transition-colors hover:bg-neutral-50 sm:hidden"
          >
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden="true">
              <path
                d="M1 3h12M3 7h8M5 11h4"
                stroke="currentColor"
                strokeWidth="1.5"
                strokeLinecap="round"
              />
            </svg>
            {/* 320px급 폭에서는 16px 검색창 자리를 위해 아이콘만 보이고 이름은 화면 낭독기에 남긴다. */}
            <span className="max-[359px]:sr-only">필터</span>
            {activeCount > 0 ? (
              <span className="grid h-5 w-5 place-items-center rounded-full bg-brand-600 text-[11px] font-bold text-white">
                {activeCount}
              </span>
            ) : null}
          </button>
          {/* 검색 버튼 (모바일 항상, 데스크톱에서도) */}
          <Button type="submit" size="sm">
            검색
          </Button>
        </div>

        {/* 모바일은 상세 필터가 접혀 있어 무엇이 적용됐는지 보이지 않는다. 적용된 필터를 칩으로 보여 주고
            누르면 그 필터만 빼고 다시 찾는다(데스크톱은 필터가 늘 펼쳐져 있어 숨긴다). */}
        {chips.length > 0 && !open ? (
          <ul
            aria-label="적용된 필터"
            className="flex flex-wrap gap-1.5 border-t border-neutral-100 px-3 py-2 sm:hidden"
          >
            {chips.map((chip) => (
              <li key={chip.key}>
                <Link
                  href={chip.href}
                  aria-label={`${chip.label} 필터 빼기`}
                  className="inline-flex h-7 items-center gap-1 rounded-full bg-brand-50 px-2.5 text-xs font-semibold text-brand-700 transition-colors hover:bg-brand-100"
                >
                  {chip.label}
                  <span aria-hidden="true">×</span>
                </Link>
              </li>
            ))}
          </ul>
        ) : null}

        {/* 상세 필터: 모바일은 토글, 데스크톱은 항상 노출 */}
        <div
          className={`grid grid-cols-2 gap-2 border-t border-neutral-100 px-3 pb-3 pt-2 sm:flex sm:flex-wrap sm:items-end sm:gap-2 ${
            open ? "grid" : "hidden sm:flex"
          }`}
        >
          <div className="flex flex-col gap-1">
            <label className="text-xs font-medium text-neutral-500" htmlFor="f-category">
              카테고리
            </label>
            <Select
              id="f-category"
              value={values.category}
              onChange={(e) => update("category", e.target.value as ListingCategory | "")}
            >
              <option value="">전체</option>
              {LISTING_CATEGORIES.map((c) => (
                <option key={c.key} value={c.key}>
                  {c.label}
                </option>
              ))}
            </Select>
          </div>

          <div className="flex flex-col gap-1">
            <label className="text-xs font-medium text-neutral-500" htmlFor="f-condition">
              상태
            </label>
            <Select
              id="f-condition"
              value={values.condition}
              onChange={(e) => update("condition", e.target.value as ItemCondition | "")}
            >
              <option value="">전체</option>
              {CONDITIONS.map((c) => (
                <option key={c} value={c}>
                  {conditionLabel(c)}
                </option>
              ))}
            </Select>
          </div>

          <div className="flex flex-col gap-1">
            <label className="text-xs font-medium text-neutral-500" htmlFor="f-sort">
              정렬
            </label>
            <Select
              id="f-sort"
              value={values.sort}
              onChange={(e) => update("sort", e.target.value as ListingSort)}
            >
              {SORTS.map((s) => (
                <option key={s.value} value={s.value}>
                  {s.label}
                </option>
              ))}
            </Select>
          </div>

          <div className="flex gap-2">
            <div className="flex flex-1 flex-col gap-1">
              <label className="text-xs font-medium text-neutral-500" htmlFor="f-min">
                최소가
              </label>
              <input
                id="f-min"
                type="number"
                min={0}
                value={values.minPrice}
                onChange={(e) => update("minPrice", e.target.value)}
                placeholder="0"
                className="h-9 w-full rounded-md border border-neutral-200 bg-white px-2 text-sm text-neutral-900 outline-none transition-colors focus-visible:border-brand-400 focus-visible:ring-2 focus-visible:ring-brand-100"
              />
            </div>
            <div className="flex flex-1 flex-col gap-1">
              <label className="text-xs font-medium text-neutral-500" htmlFor="f-max">
                최대가
              </label>
              <input
                id="f-max"
                type="number"
                min={0}
                value={values.maxPrice}
                onChange={(e) => update("maxPrice", e.target.value)}
                placeholder="∞"
                className="h-9 w-full rounded-md border border-neutral-200 bg-white px-2 text-sm text-neutral-900 outline-none transition-colors focus-visible:border-brand-400 focus-visible:ring-2 focus-visible:ring-brand-100"
              />
            </div>
          </div>

          {/* 모바일에서만: 검색 버튼 (필터 영역 안) */}
          <div className="col-span-2 sm:hidden">
            <Button type="submit" size="sm" fullWidth>
              이 조건으로 검색
            </Button>
          </div>
        </div>
      </form>
    </div>
  );
}
