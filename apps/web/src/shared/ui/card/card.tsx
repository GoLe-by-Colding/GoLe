import type { HTMLAttributes, ReactNode } from "react";
import { cn } from "@shared/lib";

export type CardElevation = "flat" | "raised";

const ELEVATION: Record<CardElevation, string> = {
  flat: "shadow-none",
  raised: "shadow-soft",
};

export interface CardProps extends HTMLAttributes<HTMLDivElement> {
  readonly elevation?: CardElevation;
  readonly interactive?: boolean;
  readonly padded?: boolean;
  readonly children: ReactNode;
}

export function Card({
  elevation = "flat",
  interactive = false,
  padded = false,
  className,
  children,
  ...rest
}: CardProps) {
  return (
    <div
      className={cn(
        "group overflow-hidden rounded-lg border border-neutral-200 bg-white",
        ELEVATION[elevation],
        interactive &&
          "cursor-pointer transition-colors duration-150 hover:border-brand-300 hover:bg-neutral-50/30",
        padded && "p-5",
        className,
      )}
      {...rest}
    >
      {children}
    </div>
  );
}
