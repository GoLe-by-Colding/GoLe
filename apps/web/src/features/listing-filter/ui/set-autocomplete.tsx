"use client";

import { useEffect, useId, useRef, useState } from "react";
import { searchLegoSets, type LegoSet } from "@entities/lego-set";
import { Input, SearchIcon } from "@shared/ui";

export interface SetAutocompleteProps {
  readonly value: string;
  readonly onChange: (value: string) => void;
  readonly onSelect?: (set: LegoSet) => void;
  readonly id?: string;
  readonly placeholder?: string;
}

/** 입력에 초점을 유지하는 combobox. 늦은 응답은 닫힌 목록을 다시 열지 않는다. */
export function SetAutocomplete({
  value,
  onChange,
  onSelect,
  id,
  placeholder = "세트 번호 또는 이름",
}: SetAutocompleteProps) {
  const listId = useId();
  const containerRef = useRef<HTMLDivElement>(null);
  const [focused, setFocused] = useState(false);
  const [dismissed, setDismissed] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);
  const [search, setSearch] = useState<{ query: string; results: readonly LegoSet[] }>({
    query: "",
    results: [],
  });
  const query = value.trim();
  const results = search.query === query ? search.results : [];
  const open = focused && !dismissed && query.length > 0 && results.length > 0;

  useEffect(() => {
    if (!focused || !query || dismissed) return;
    let active = true;
    const timer = setTimeout(() => {
      searchLegoSets(query)
        .then((hits) => {
          if (active) setSearch({ query, results: hits.slice(0, 8) });
        })
        .catch(() => {
          if (active) setSearch({ query, results: [] });
        });
    }, 300);
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [query, focused, dismissed]);

  function select(set: LegoSet) {
    setDismissed(true);
    setActiveIndex(-1);
    onChange(set.name);
    onSelect?.(set);
  }

  return (
    <div
      ref={containerRef}
      className="relative"
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget)) setFocused(false);
      }}
    >
      <SearchIcon className="pointer-events-none absolute left-3 top-3 size-5 text-neutral-500" />
      <Input
        id={id}
        role="combobox"
        aria-expanded={open}
        aria-controls={open ? listId : undefined}
        aria-autocomplete="list"
        aria-activedescendant={open && activeIndex >= 0 ? `${listId}-${activeIndex}` : undefined}
        type="search"
        value={value}
        placeholder={placeholder}
        autoComplete="off"
        onChange={(event) => {
          onChange(event.target.value);
          setDismissed(false);
          setActiveIndex(-1);
        }}
        onFocus={() => {
          setFocused(true);
          setDismissed(false);
        }}
        onKeyDown={(event) => {
          if (event.nativeEvent.isComposing) return;
          if (event.key === "Escape") {
            setDismissed(true);
            setActiveIndex(-1);
            event.preventDefault();
          } else if ((event.key === "ArrowDown" || event.key === "ArrowUp") && results.length > 0) {
            event.preventDefault();
            setDismissed(false);
            const next =
              event.key === "ArrowDown"
                ? (activeIndex + 1) % results.length
                : (activeIndex <= 0 ? results.length : activeIndex) - 1;
            setActiveIndex(next);
            requestAnimationFrame(() =>
              document.getElementById(`${listId}-${next}`)?.scrollIntoView({ block: "nearest" }),
            );
          } else if (event.key === "Enter" && open && results[activeIndex]) {
            event.preventDefault();
            select(results[activeIndex]);
          }
        }}
        className="pl-10"
      />
      {open ? (
        <ul
          id={listId}
          role="listbox"
          aria-label="추천 세트"
          className="absolute left-0 right-0 top-full z-30 mt-2 max-h-64 overflow-y-auto rounded-lg border border-neutral-200 bg-white p-1 shadow-md"
        >
          {results.map((set, index) => (
            <li
              key={set.setNumber}
              id={`${listId}-${index}`}
              role="option"
              aria-selected={activeIndex === index}
              onMouseDown={(event) => event.preventDefault()}
              onMouseMove={() => setActiveIndex(index)}
              onClick={() => select(set)}
              className={`flex min-h-11 cursor-pointer flex-wrap items-center gap-x-2 gap-y-0.5 rounded-md px-3 py-2 text-sm ${activeIndex === index ? "bg-brand-50 text-brand-700" : "text-neutral-900 hover:bg-neutral-50"}`}
            >
              <span className="shrink-0 text-xs font-semibold tabular-nums text-neutral-600">
                #{set.setNumber}
              </span>
              <span className="min-w-0 flex-1 break-words font-medium">{set.name}</span>
            </li>
          ))}
        </ul>
      ) : null}
      <span role="status" className="sr-only">
        {open ? `추천 세트 ${results.length}개. 위아래 방향키로 선택하세요.` : ""}
      </span>
    </div>
  );
}
