"use client";

import { type FormEvent, useId, useState, useTransition } from "react";
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
import { Button, Input, Select } from "@shared/ui";
import {
  appliedListingFilters,
  filterPriceError,
  listingSearchHref,
  LISTING_SORT_OPTIONS,
  type ListingFilterValues,
} from "../model/search-filters";
import { SetAutocomplete } from "./set-autocomplete";

export interface ListingFilterBarProps {
  readonly initial: ListingFilterValues;
}

export function ListingFilterBar({ initial }: ListingFilterBarProps) {
  const router = useRouter();
  const id = useId();
  const [values, setValues] = useState(initial);
  const [open, setOpen] = useState(filterPriceError(initial) !== null);
  const [validated, setValidated] = useState(filterPriceError(initial) !== null);
  const [pending, startTransition] = useTransition();
  const error = validated ? filterPriceError(values) : null;
  const chips = appliedListingFilters(initial);
  const resetHref = listingSearchHref({
    ...initial,
    condition: "",
    category: "",
    minPrice: "",
    maxPrice: "",
    sort: "newest",
  });

  function update<K extends keyof ListingFilterValues>(key: K, value: ListingFilterValues[K]) {
    setValues((prev) => ({ ...prev, [key]: value }));
  }

  function search(next: ListingFilterValues) {
    setValidated(true);
    const problem = filterPriceError(next);
    if (problem !== null) {
      setOpen(true);
      requestAnimationFrame(() => document.getElementById(`${id}-${problem.field}`)?.focus());
      return;
    }
    startTransition(() => router.push(listingSearchHref(next)));
    setOpen(false);
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    search(values);
  }

  return (
    <form
      onSubmit={handleSubmit}
      noValidate
      aria-label="상품 검색 조건"
      aria-busy={pending}
      className="rounded-xl border border-neutral-200 bg-white"
    >
      <div className="flex items-end gap-2 p-3 sm:p-4">
        <div className="min-w-0 flex-1">
          <label htmlFor={`${id}-query`} className="sr-only">
            검색어, 세트 번호
          </label>
          <SetAutocomplete
            id={`${id}-query`}
            value={values.query}
            placeholder="세트 이름이나 번호로 검색"
            onChange={(value) => update("query", value)}
            onSelect={(set) => {
              const next = { ...values, query: set.name };
              setValues(next);
              search(next);
            }}
          />
        </div>
        <Button type="submit" loading={pending} loadingLabel="검색 중" className="px-4">
          검색
        </Button>
      </div>

      <div className="flex items-center justify-between border-t border-neutral-100 px-3 py-2 sm:hidden">
        <button
          type="button"
          onClick={() => setOpen((value) => !value)}
          aria-expanded={open}
          aria-controls={`${id}-details`}
          className="inline-flex min-h-11 items-center gap-2 rounded-lg px-2 text-sm font-semibold text-neutral-700 hover:bg-neutral-50"
        >
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <path
              d="M2 4h12M4 8h8M6 12h4"
              stroke="currentColor"
              strokeWidth="1.5"
              strokeLinecap="round"
            />
          </svg>
          필터
          {chips.length > 0 ? (
            <span className="grid size-5 place-items-center rounded-full bg-brand-50 text-xs text-brand-700">
              {chips.length}
            </span>
          ) : null}
          <span aria-hidden="true" className="text-neutral-500">
            {open ? "−" : "+"}
          </span>
        </button>
        <span className="text-xs text-neutral-600">상태부터 가격까지</span>
      </div>

      <div
        id={`${id}-details`}
        className={`${open ? "grid" : "hidden"} grid-cols-2 gap-3 border-t border-neutral-100 p-3 sm:grid sm:grid-cols-3 sm:p-4 xl:grid-cols-5`}
      >
        <div className="flex min-w-0 flex-col gap-1.5">
          <label className="text-xs font-semibold text-neutral-600" htmlFor={`${id}-category`}>
            카테고리
          </label>
          <Select
            id={`${id}-category`}
            value={values.category}
            onChange={(event) => update("category", event.target.value as ListingCategory | "")}
          >
            <option value="">전체</option>
            {LISTING_CATEGORIES.map((category) => (
              <option key={category.key} value={category.key}>
                {category.label}
              </option>
            ))}
          </Select>
        </div>
        <div className="flex min-w-0 flex-col gap-1.5">
          <label className="text-xs font-semibold text-neutral-600" htmlFor={`${id}-condition`}>
            상태
          </label>
          <Select
            id={`${id}-condition`}
            value={values.condition}
            onChange={(event) => update("condition", event.target.value as ItemCondition | "")}
          >
            <option value="">전체</option>
            {ITEM_CONDITIONS.map((condition) => (
              <option key={condition} value={condition}>
                {conditionLabel(condition)}
              </option>
            ))}
          </Select>
        </div>
        <div className="col-span-2 flex min-w-0 flex-col gap-1.5 sm:col-span-1">
          <label className="text-xs font-semibold text-neutral-600" htmlFor={`${id}-sort`}>
            정렬
          </label>
          <Select
            id={`${id}-sort`}
            value={values.sort}
            onChange={(event) => update("sort", event.target.value as ListingSort)}
          >
            {LISTING_SORT_OPTIONS.map((sort) => (
              <option key={sort.value} value={sort.value}>
                {sort.label}
              </option>
            ))}
          </Select>
        </div>
        <div className="col-span-2 grid min-w-0 grid-cols-2 gap-3 sm:col-span-3 xl:col-span-2">
          {(["minPrice", "maxPrice"] as const).map((field) => (
            <div key={field} className="flex min-w-0 flex-col gap-1.5">
              <label className="text-xs font-semibold text-neutral-600" htmlFor={`${id}-${field}`}>
                {field === "minPrice" ? "최소가" : "최대가"}{" "}
                <span className="font-normal">(원)</span>
              </label>
              <Input
                id={`${id}-${field}`}
                inputMode="numeric"
                value={values[field]}
                onChange={(event) => update(field, event.target.value)}
                placeholder={field === "minPrice" ? "0" : "제한 없음"}
                invalid={error?.field === field}
                aria-describedby={error?.field === field ? `${id}-price-error` : undefined}
              />
            </div>
          ))}
        </div>
        {error !== null ? (
          <p
            id={`${id}-price-error`}
            role="alert"
            className="col-span-full text-sm font-medium text-danger"
          >
            {error.message}
          </p>
        ) : null}
        <div className="col-span-full sm:hidden">
          <Button type="submit" fullWidth loading={pending} loadingLabel="검색 중">
            이 조건으로 검색
          </Button>
        </div>
      </div>

      {chips.length > 0 ? (
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1 border-t border-neutral-100 px-3 py-2 sm:px-4">
          <ul aria-label="적용된 필터" className="flex flex-1 flex-wrap gap-x-2">
            {chips.map((chip) => (
              <li key={chip.key}>
                <Link
                  href={chip.href}
                  aria-label={`${chip.label} 필터 빼기`}
                  className="inline-flex min-h-11 items-center gap-2 rounded-lg px-2 text-xs font-semibold text-brand-700 hover:bg-brand-50"
                >
                  <span>{chip.label}</span>
                  <span aria-hidden="true" className="text-base font-normal">
                    ×
                  </span>
                </Link>
              </li>
            ))}
          </ul>
          <Link
            href={resetHref}
            className="inline-flex min-h-11 items-center rounded-lg px-2 text-xs font-medium text-neutral-600 underline underline-offset-4 hover:text-neutral-900"
          >
            필터 초기화
          </Link>
        </div>
      ) : null}
      <span role="status" className="sr-only">
        {pending ? "검색 조건을 적용하고 있어요." : ""}
      </span>
    </form>
  );
}
