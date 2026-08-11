"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { searchLegoSets, type LegoSet } from "@entities/lego-set";

export interface HeaderSearchProps {
  /** 모바일 메뉴 등에서 전체 너비로 쓸 때 */
  readonly fullWidth?: boolean;
  /** 검색 실행(제출/선택) 후 호출 — 모바일 메뉴 닫기용 */
  readonly onSubmitted?: () => void;
}

/**
 * 헤더 통합 검색 — 세트번호·이름 자동완성(debounce 250ms) 후 매물 검색으로 이동.
 * 입력 제출 시 /search?query= 로, 자동완성 선택 시 해당 세트명으로 검색한다.
 */
export function HeaderSearch({ fullWidth = false, onSubmitted }: HeaderSearchProps) {
  const router = useRouter();
  const [value, setValue] = useState("");
  const [{ results, open }, setSearch] = useState<{ results: readonly LegoSet[]; open: boolean }>({
    results: [],
    open: false,
  });
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (value.trim().length < 1) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- 빈값 리셋 동기화
      setSearch({ results: [], open: false });
      return;
    }
    let active = true;
    const t = setTimeout(() => {
      searchLegoSets(value.trim())
        .then((hits) => {
          if (active) setSearch({ results: hits.slice(0, 6), open: hits.length > 0 });
        })
        .catch(() => {
          if (active) setSearch({ results: [], open: false });
        });
    }, 250);
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

  function go(query: string) {
    const q = query.trim();
    if (!q) return;
    setSearch({ results: [], open: false });
    router.push(`/search?query=${encodeURIComponent(q)}`);
    onSubmitted?.();
  }

  return (
    <div ref={containerRef} className={`relative ${fullWidth ? "w-full" : "w-full max-w-xs"}`}>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          go(value);
        }}
        role="search"
      >
        <div className="relative">
          <span
            aria-hidden="true"
            className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-neutral-400"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor">
              <circle cx="11" cy="11" r="7" strokeWidth="2" />
              <path d="m20 20-3.2-3.2" strokeWidth="2" strokeLinecap="round" />
            </svg>
          </span>
          <input
            type="search"
            value={value}
            placeholder="세트번호·이름으로 검색"
            aria-label="레고 세트 검색"
            autoComplete="off"
            onChange={(e) => setValue(e.target.value)}
            onFocus={() => results.length > 0 && setSearch((s) => ({ ...s, open: true }))}
            className="h-10 w-full rounded-md border border-neutral-200 bg-neutral-50 pl-9 pr-3 text-sm text-neutral-900 outline-none transition-colors focus-visible:border-brand-400 focus-visible:bg-white focus-visible:ring-2 focus-visible:ring-brand-100"
          />
        </div>
      </form>
      {open ? (
        <ul
          role="listbox"
          className="absolute left-0 right-0 top-full z-50 mt-1.5 overflow-hidden rounded-lg border border-neutral-200 bg-white shadow-lift"
        >
          {results.map((s) => (
            <li key={s.setNumber}>
              <button
                type="button"
                className="flex w-full items-center gap-3 px-3 py-2.5 text-left text-sm hover:bg-brand-50"
                onClick={() => go(s.name)}
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
