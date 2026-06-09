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
  elevation = "raised",
  interactive = false,
  padded = false,
  className,
  children,
  ...rest
}: CardProps) {
  return (
    <div
      className={cn(
        "group bg-white border border-neutral-200/60 rounded-2xl overflow-hidden",
        ELEVATION[elevation],
        interactive &&
          "cursor-pointer transition-all duration-300 ease-out hover:border-neutral-300/80 hover:shadow-lift hover:-translate-y-1",
        padded && "p-5",
        className,
      )}
      {...rest}
    >
      {children}
    </div>
  );
}
