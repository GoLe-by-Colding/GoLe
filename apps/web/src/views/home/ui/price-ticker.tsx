"use client";

import { useRef, useState, type FocusEvent } from "react";
import Link from "next/link";
import type { TrendingSet } from "@entities/pricing";
import { cn, formatKrw } from "@shared/lib";

/**
 * 트렌딩 세트의 평균 체결가를 연속해서 보여주는 시세 티커.
 *
 * 흐르는 띠는 멈출 수 있어야 한다(WCAG 2.2.2). 마우스를 올리면 그 자리에 멈추고, 멈춤 버튼을 누르거나
 * 키보드로 들어오면 흐름을 끊고 처음부터 손·키보드로 넘겨 보는 목록이 된다 — 흐르는 띠 위 링크에
 * 포커스하면 링크가 계속 움직여 잘린 영역 밖으로 나가기 때문이다. 터치 기기는 hover가 없으므로 버튼이
 * 유일한 정지 수단이다. 움직임 줄이기 설정이면 처음부터 넘겨 보는 목록이고 버튼도 두지 않는다.
 *
 * 키보드 판정은 `:focus-within`이 아니라 `:focus-visible`로 한다. 마우스로 링크를 누르는 순간에도
 * 포커스가 들어오므로, `:focus-within`이면 누르는 사이 띠가 처음으로 튀어 클릭이 다른 곳에 떨어진다.
 */
export function PriceTicker({ items }: { readonly items: readonly TrendingSet[] }) {
  const [stopped, setStopped] = useState(false);
  const scrollerRef = useRef<HTMLDivElement>(null);

  if (items.length === 0) return null;
  // 끊김 없는 순환을 위해 한 벌을 더 잇는다. 넘겨 보는 목록일 때는 두 번째 벌을 숨긴다.
  const doubled = [...items, ...items];

  function toggle() {
    if (stopped && scrollerRef.current !== null) {
      // 넘겨 본 위치가 남으면 다시 흐를 때 띠가 그만큼 밀린 채로 돈다.
      scrollerRef.current.scrollLeft = 0;
    }
    setStopped((value) => !value);
  }

  function handleFocus(event: FocusEvent<HTMLDivElement>) {
    const target = event.target;
    if (stopped || !target.matches(":focus-visible")) return;
    // 포커스가 들어오는 순간의 스크롤은 아직 흐르던 위치(transform)로 계산된다. 흐름이 풀린 뒤 다시 맞춘다.
    requestAnimationFrame(() => target.scrollIntoView({ block: "nearest", inline: "nearest" }));
  }

  function handleBlur(event: FocusEvent<HTMLDivElement>) {
    if (stopped || event.currentTarget.contains(event.relatedTarget as Node | null)) return;
    event.currentTarget.scrollLeft = 0;
  }

  return (
    <div
      role="region"
      aria-label="인기 세트 시세"
      className="flex items-stretch border-y border-neutral-200 bg-neutral-50"
    >
      <div
        ref={scrollerRef}
        onFocus={handleFocus}
        onBlur={handleBlur}
        className={cn(
          "group min-w-0 flex-1 py-3 [scrollbar-width:thin]",
          stopped
            ? "overflow-x-auto"
            : "overflow-hidden has-[:focus-visible]:overflow-x-auto motion-reduce:overflow-x-auto",
        )}
      >
        <div
          className={cn(
            "flex w-max items-center gap-10 pl-10",
            stopped
              ? "pr-10"
              : "animate-market-ticker group-hover:[animation-play-state:paused] group-has-[:focus-visible]:animate-none group-has-[:focus-visible]:pr-10 motion-reduce:animate-none motion-reduce:pr-10",
          )}
        >
          {doubled.map((set, index) => {
            const repeat = index >= items.length;
            return (
              <Link
                key={`${set.setNumber}-${index}`}
                href={`/prices?set=${encodeURIComponent(set.setNumber)}`}
                aria-hidden={repeat ? "true" : undefined}
                tabIndex={repeat ? -1 : undefined}
                className={cn(
                  "flex items-center gap-2.5 whitespace-nowrap text-sm hover:text-brand-700",
                  repeat &&
                    (stopped ? "hidden" : "group-has-[:focus-visible]:hidden motion-reduce:hidden"),
                )}
              >
                <span className="font-mono font-bold text-brand-700">#{set.setNumber}</span>
                <span className="max-w-[18ch] truncate text-neutral-600">{set.name}</span>
                <span className="font-semibold tabular-nums text-neutral-900">
                  <span className="mr-1 text-xs font-normal text-neutral-500">평균</span>
                  {formatKrw(set.averagePrice)}
                </span>
                <span className="text-xs text-neutral-500">
                  {set.tradeCount.toLocaleString("ko-KR")}건 체결
                </span>
              </Link>
            );
          })}
        </div>
      </div>
      <button
        type="button"
        onClick={toggle}
        aria-label={stopped ? "시세 티커 재생" : "시세 티커 멈춤"}
        className="flex shrink-0 items-center gap-1.5 border-l border-neutral-200 px-4 text-xs font-medium text-neutral-600 transition-colors hover:bg-neutral-100 hover:text-neutral-900 motion-reduce:hidden"
      >
        <svg aria-hidden="true" viewBox="0 0 12 12" className="h-3 w-3" fill="currentColor">
          {stopped ? (
            <path d="M3 1.5v9l7.5-4.5z" />
          ) : (
            <path d="M2.5 1.5h2.5v9H2.5zM7 1.5h2.5v9H7z" />
          )}
        </svg>
        {stopped ? "재생" : "멈춤"}
      </button>
    </div>
  );
}
