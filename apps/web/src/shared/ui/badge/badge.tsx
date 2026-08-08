import type { HTMLAttributes, ReactNode } from "react";
import { cn } from "@shared/lib";

export type BadgeTone = "neutral" | "brand" | "success" | "danger" | "warning";

const TONE: Record<BadgeTone, string> = {
  neutral: "bg-neutral-100 text-neutral-600",
  brand: "bg-brand-50 text-brand-700",
  success: "bg-success-soft text-success",
  danger: "bg-danger-soft text-danger",
  warning: "bg-warning-soft text-warning",
};

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  readonly tone?: BadgeTone;
  readonly children: ReactNode;
}

export function Badge({ tone = "neutral", className, children, ...rest }: BadgeProps) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 h-[22px] px-2 rounded-full text-xs font-semibold whitespace-nowrap",
        TONE[tone],
        className,
      )}
      {...rest}
    >
      {children}
    </span>
  );
}
