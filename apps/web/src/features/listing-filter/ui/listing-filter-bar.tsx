"use client";

import { type FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import {
  conditionLabel,
  type ItemCondition,
  type ListingCategory,
  type ListingSort,
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

const CONDITIONS: readonly ItemCondition[] = ["new_sealed", "used_complete", "used_incomplete"];

const SORTS: ReadonlyArray<{ readonly value: ListingSort; readonly label: string }> = [
  { value: "newest", label: "최신순" },
  { value: "price_asc", label: "가격 낮은순" },
  { value: "price_desc", label: "가격 높은순" },
];

export function ListingFilterBar({ initial }: ListingFilterBarProps) {
  const router = useRouter();
  const [values, setValues] = useState<ListingFilterValues>(initial);
  const [open, setOpen] = useState(false);

  function update<K extends keyof ListingFilterValues>(key: K, value: ListingFilterValues[K]) {
    setValues((prev) => ({ ...prev, [key]: value }));
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const qs = new URLSearchParams();
    if (values.query.trim()) qs.set("query", values.query.trim());
    if (values.condition) qs.set("condition", values.condition);
    if (values.category) qs.set("category", values.category);
    if (values.minPrice.trim()) qs.set("minPrice", values.minPrice.trim());
    if (values.maxPrice.trim()) qs.set("maxPrice", values.maxPrice.trim());
    if (values.sort !== "newest") qs.set("sort", values.sort);
    const suffix = qs.toString() ? `?${qs.toString()}` : "";
    router.push(`/search${suffix}`);
    setOpen(false);
  }

  // 적용된 필터 수(검색어 제외)
  const activeCount = [
    values.condition,
    values.category,
    values.minPrice,
    values.maxPrice,
    values.sort !== "newest" ? values.sort : "",
  ].filter(Boolean).length;

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
            필터
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
