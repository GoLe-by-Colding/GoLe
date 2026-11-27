import type { ButtonHTMLAttributes, ReactNode } from "react";
import { cn } from "@shared/lib";

export interface ChipProps extends Omit<
  ButtonHTMLAttributes<HTMLButtonElement>,
  "aria-pressed" | "type"
> {
  /** 켜져 있는지. 보조기술에는 aria-pressed 로 전한다. */
  readonly selected: boolean;
  readonly size?: "sm" | "md";
  readonly children: ReactNode;
}

const CHIP_SIZE = {
  sm: "h-8 px-3 text-xs",
  md: "h-9 px-3.5 text-sm",
} as const;

/**
 * 여러 개 중에서 켜고 끄는 필터·태그 칩. 켜진 칩은 작은 브릭 턱(2px)을 갖고, 누르면 내려앉는다.
 * 하나만 고르는 정렬·기간 전환은 칩이 아니라 세그먼트로 만든다.
 */
export function Chip({ selected, size = "md", className, children, ...rest }: ChipProps) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      className={cn(
        "inline-flex shrink-0 items-center justify-center gap-1.5 rounded-full border font-semibold whitespace-nowrap transition-press disabled:cursor-not-allowed disabled:opacity-40 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-400",
        CHIP_SIZE[size],
        selected
          ? "brick border-brand-600 bg-brand-600 text-white [--brick-depth:2px] [--brick-edge:var(--color-brand-800)]"
          : "border-neutral-200 bg-white text-neutral-700 hover:border-neutral-300 hover:bg-neutral-50 transition-press motion-safe:not-disabled:active:scale-95",
        className,
      )}
      {...rest}
    >
      {children}
    </button>
  );
}
