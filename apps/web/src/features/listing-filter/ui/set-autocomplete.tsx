"use client";

import { useEffect, useRef, useState } from "react";
import type { LegoSet } from "@entities/lego-set";
import { searchLegoSets } from "@entities/lego-set";

export interface SetAutocompleteProps {
  readonly value: string;
  readonly onChange: (value: string) => void;
  readonly onSelect?: (set: LegoSet) => void;
  readonly id?: string;
  readonly placeholder?: string;
}

/** 레고 세트 검색 자동완성 (debounce 300ms). listing-filter 전용. */
export function SetAutocomplete({
  value,
  onChange,
  onSelect,
  id,
  placeholder = "세트번호 또는 이름",
}: SetAutocompleteProps) {
  const [{ results, open }, setSearch] = useState<{ results: readonly LegoSet[]; open: boolean }>({
    results: [],
    open: false,
  });
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (value.trim().length < 1) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- 외부 검색 동기화(빈값 리셋)
      setSearch({ results: [], open: false });
      return;
    }
    let active = true;
    const t = setTimeout(() => {
      searchLegoSets(value.trim())
        .then((hits) => {
          if (active) setSearch({ results: hits.slice(0, 8), open: hits.length > 0 });
        })
        .catch(() => {
          if (active) setSearch({ results: [], open: false });
        });
    }, 300);
    return () => {
      active = false;
      clearTimeout(t);
    };
  }, [value]);

  useEffect(() => {
    function handleOut(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setSearch((s) => ({ ...s, open: false }));
      }
    }
    document.addEventListener("mousedown", handleOut);
    return () => document.removeEventListener("mousedown", handleOut);
  }, []);

  return (
    <div ref={containerRef} className="relative">
      <input
        id={id}
        type="text"
        value={value}
        placeholder={placeholder}
        autoComplete="off"
        onChange={(e) => {
          onChange(e.target.value);
          if (!e.target.value.trim()) setSearch({ results: [], open: false });
        }}
        onFocus={() => results.length > 0 && setSearch((s) => ({ ...s, open: true }))}
        className="h-10 w-full rounded-md border border-neutral-200 bg-white px-3 py-2 text-sm text-neutral-900 outline-none transition-colors focus-visible:border-brand-400 focus-visible:ring-2 focus-visible:ring-brand-100"
      />
      {open ? (
        <ul
          role="listbox"
          className="absolute left-0 right-0 top-full z-50 mt-1 overflow-hidden rounded-lg border border-neutral-200 bg-white shadow-lift"
        >
          {results.map((s) => (
            <li key={s.setNumber}>
              <button
                type="button"
                className="flex w-full items-center gap-3 px-3 py-2.5 text-left text-sm hover:bg-brand-50"
                onClick={() => {
                  onChange(s.name);
                  onSelect?.(s);
                  setSearch({ results: [], open: false });
                }}
              >
                <span className="font-mono text-xs text-neutral-400">#{s.setNumber}</span>
                <span className="truncate font-medium text-neutral-900">{s.name}</span>
                <span className="ml-auto shrink-0 text-xs text-neutral-400">{s.theme}</span>
              </button>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}
