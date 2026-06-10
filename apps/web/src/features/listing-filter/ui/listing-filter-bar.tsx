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
import { Button, Input, Select } from "@shared/ui";

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

  function update<K extends keyof ListingFilterValues>(key: K, value: ListingFilterValues[K]) {
    setValues((prev) => ({ ...prev, [key]: value }));
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const qs = new URLSearchParams();
    if (values.query.trim()) {
      qs.set("query", values.query.trim());
    }
    if (values.condition) {
      qs.set("condition", values.condition);
    }
    if (values.category) {
      qs.set("category", values.category);
    }
    if (values.minPrice.trim()) {
      qs.set("minPrice", values.minPrice.trim());
    }
    if (values.maxPrice.trim()) {
      qs.set("maxPrice", values.maxPrice.trim());
    }
    if (values.sort !== "newest") {
      qs.set("sort", values.sort);
    }
    const suffix = qs.toString().length > 0 ? `?${qs.toString()}` : "";
    router.push(`/search${suffix}`);
  }

  return (
    <form
      className="flex flex-wrap items-end gap-3 rounded-lg border border-neutral-200 bg-white p-4"
      onSubmit={handleSubmit}
    >
      <div className="flex min-w-[200px] flex-1 flex-col gap-1">
        <label className="text-sm font-medium text-neutral-600" htmlFor="f-query">
          검색어
        </label>
        <Input
          id="f-query"
          value={values.query}
          placeholder="제목, 설명, 세트번호"
          onChange={(e) => update("query", e.target.value)}
        />
      </div>
      <div className="flex flex-col gap-1">
        <label className="text-sm font-medium text-neutral-600" htmlFor="f-category">
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
        <label className="text-sm font-medium text-neutral-600" htmlFor="f-condition">
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
      <div className="flex w-24 flex-col gap-1">
        <label className="text-sm font-medium text-neutral-600" htmlFor="f-min">
          최소가
        </label>
        <Input
          id="f-min"
          type="number"
          min={0}
          value={values.minPrice}
          onChange={(e) => update("minPrice", e.target.value)}
        />
      </div>
      <div className="flex w-24 flex-col gap-1">
        <label className="text-sm font-medium text-neutral-600" htmlFor="f-max">
          최대가
        </label>
        <Input
          id="f-max"
          type="number"
          min={0}
          value={values.maxPrice}
          onChange={(e) => update("maxPrice", e.target.value)}
        />
      </div>
      <div className="flex flex-col gap-1">
        <label className="text-sm font-medium text-neutral-600" htmlFor="f-sort">
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
      <Button type="submit">검색</Button>
    </form>
  );
}
