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
        "bg-white border border-neutral-200/70 rounded-xl overflow-hidden",
        ELEVATION[elevation],
        interactive &&
          "cursor-pointer transition duration-200 hover:border-neutral-300 hover:shadow-lift hover:-translate-y-0.5",
        padded && "p-5",
        className,
      )}
      {...rest}
    >
      {children}
    </div>
  );
}
