"use client";

import type { ReactNode } from "react";
import { cn } from "@shared/lib";

export interface SegmentedOption<T extends string> {
  readonly value: T;
  readonly label: ReactNode;
}

export interface SegmentedProps<T extends string> {
  readonly options: readonly SegmentedOption<T>[];
  readonly value: T;
  readonly onChange: (value: T) => void;
  readonly "aria-label": string;
  /** "tabs"면 탭 목록(tablist·tab·aria-selected)으로, 기본은 누름 버튼 묶음(group·aria-pressed)으로 알린다. */
  readonly semantics?: "toggle" | "tabs";
  /** 부모 폭을 다 채울지. 기본은 내용 폭. */
  readonly fullWidth?: boolean;
  readonly className?: string;
}

/**
 * 하나만 고르는 정렬·기간·피드 전환. 칸은 같은 너비로 나누고, 흰 선택 표시가 스프링으로 미끄러져 옮겨 간다.
 * 여러 개를 켜고 끄는 필터는 `Chip`을 쓴다.
 */
export function Segmented<T extends string>({
  options,
  value,
  onChange,
  semantics = "toggle",
  fullWidth = false,
  className,
  "aria-label": ariaLabel,
}: SegmentedProps<T>) {
  const index = Math.max(
    0,
    options.findIndex((option) => option.value === value),
  );
  const tabs = semantics === "tabs";
  return (
    <div
      role={tabs ? "tablist" : "group"}
      aria-label={ariaLabel}
      className={cn(
        "relative isolate auto-cols-fr grid-flow-col rounded-lg bg-neutral-100 p-1",
        fullWidth ? "grid w-full" : "inline-grid",
        className,
      )}
    >
      <span
        aria-hidden="true"
        className="brick absolute inset-y-1 left-1 -z-10 rounded-md bg-white transition-press [--brick-depth:2px] [--brick-edge:var(--color-neutral-200)] motion-reduce:transition-none"
        style={{
          width: `calc((100% - 0.5rem) / ${options.length})`,
          translate: `${index * 100}% 0`,
        }}
      />
      {options.map((option) => {
        const selected = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            {...(tabs ? { role: "tab", "aria-selected": selected } : { "aria-pressed": selected })}
            onClick={() => onChange(option.value)}
            className={cn(
              "rounded-md px-3 py-1.5 text-sm font-semibold whitespace-nowrap transition-press motion-safe:active:scale-95 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-400",
              selected ? "text-neutral-900" : "text-neutral-500 hover:text-neutral-800",
            )}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}
